// M3 acceptance drill on a desensitized project: approved solution -> handoff -> implementation
// recovery -> acceptance matrix -> As-built -> knowledge publish (four-eyes) -> supersede old
// knowledge. Uses only synthetic data and a disposable Git repository.
// Run: node scripts/m3-acceptance-drill.mjs
import { spawn, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const repoRoot = resolve(new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'))
const java = process.env.MAMMOTH_JAVA ?? 'java'
const jar = join(repoRoot, 'services', 'domain', 'target', 'mammoth-domain-service.jar')
const workRoot = mkdtempSync(join(tmpdir(), 'mammoth-m3-acceptance-'))
const dataRoot = join(workRoot, 'data')
const repo = join(workRoot, 'project-repo')
const port = 18170
const password = 'Passw0rd!2026'
const steps = []

function record(name, ok, detail) {
  steps.push({ name, ok, detail })
  if (!ok) console.error(`FAIL ${name}: ${detail}`)
}

function git(...args) {
  return execFileSync('git', args, { cwd: repo, encoding: 'utf8' }).trim()
}

function execFileSync(command, args, options) {
  const result = spawnSync(command, args, options)
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} failed: ${result.stderr ?? ''}`)
  }
  return result.stdout ?? ''
}

function client(base) {
  const jarCookies = new Map()
  async function call(path, options = {}) {
    const headers = { ...(options.headers ?? {}) }
    const cookie = [...jarCookies.entries()].map(([key, value]) => `${key}=${value}`).join('; ')
    if (cookie) headers.Cookie = cookie
    if (jarCookies.get('XSRF-TOKEN') && !['GET', 'HEAD', 'OPTIONS'].includes((options.method ?? 'GET').toUpperCase())) {
      headers['X-XSRF-TOKEN'] = jarCookies.get('XSRF-TOKEN')
    }
    const response = await fetch(base + path, { ...options, headers })
    for (const entry of response.headers.getSetCookie?.() ?? []) {
      const pair = entry.split(';')[0]
      const index = pair.indexOf('=')
      if (index > 0) jarCookies.set(pair.slice(0, index), pair.slice(index + 1))
    }
    const text = await response.text()
    if (!response.ok) {
      const error = new Error(`${response.status} ${text.slice(0, 300)}`)
      error.status = response.status
      throw error
    }
    return text ? JSON.parse(text) : null
  }
  return { call }
}

const json = { 'Content-Type': 'application/json' }
let server
try {
  mkdirSync(join(repo, 'src', 'main', 'java', 'com', 'acme', 'mes'), { recursive: true })
  git('init', '-b', 'main')
  git('config', 'user.email', 'drill@example.com')
  git('config', 'user.name', 'M3 Drill')
  writeFileSync(join(repo, 'src', 'main', 'java', 'com', 'acme', 'mes', 'ScanService.java'), 'class ScanService {}\n')
  writeFileSync(join(repo, 'README.md'), '# Desensitized ACME-MES project\n')
  git('add', '-A')
  git('commit', '-m', 'initial')
  const baselineCommit = git('rev-parse', 'HEAD')
  record('project-workspace', baselineCommit.length === 40, 'desensitized ACME-MES repo')

  mkdirSync(dataRoot, { recursive: true })
  server = spawn(java, ['-jar', jar, `--server.port=${port}`, `--mammoth.data-root=${dataRoot}`], {
    stdio: 'ignore', windowsHide: true,
  })
  const base = `http://127.0.0.1:${port}`
  const deadline = Date.now() + 60_000
  for (;;) {
    try {
      const response = await fetch(base + '/api/health')
      if (response.ok) break
    } catch {
      // not up yet
    }
    if (Date.now() > deadline) throw new Error('server did not become healthy')
    await new Promise((delay) => setTimeout(delay, 400))
  }

  const api = client(base)
  await api.call('/api/auth/csrf')
  const owner = await api.call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: 'accept-owner', displayName: 'Acceptance Owner', password }),
  })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-owner', password }),
  })
  const reviewer = await api.call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: 'accept-reviewer', displayName: 'Acceptance Reviewer', password }),
  })
  const customer = await api.call('/api/customers', {
    method: 'POST', headers: json, body: JSON.stringify({ name: 'ACME（脱敏）' }),
  })
  const project = await api.call('/api/projects', {
    method: 'POST', headers: json,
    body: JSON.stringify({ customerId: customer.id, code: 'ACME-MES-2026', name: 'ACME MES 脱敏项目' }),
  })
  await api.call(`/api/projects/${project.id}/members`, {
    method: 'POST', headers: json, body: JSON.stringify({ userId: reviewer.id, role: 'reviewer' }),
  })
  const content = new TextEncoder().encode('脱敏纪要：收货扫码重复时需冻结库存并人工复核。')
  const form = new FormData()
  form.append('file', new Blob([content], { type: 'text/markdown' }), 'acceptance-notes.md')
  const artifact = await api.call(`/api/projects/${project.id}/artifacts`, { method: 'POST', body: form })
  record('project-seeded', Boolean(project.id && artifact.id), `${project.code} + artifact`)

  const repository = await api.call(`/api/projects/${project.id}/repositories`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ label: 'acme-mes', path: repo, defaultBranch: 'main' }),
  })
  const baseline = await api.call(`/api/projects/${project.id}/baselines`, {
    method: 'POST', headers: json, body: JSON.stringify({ note: 'approved design baseline', allowDirty: false }),
  })
  record('baseline', baseline.repos[0].commitSha === baselineCommit,
    `${baseline.repos[0].commitSha.slice(0, 8)} on ${baseline.repos[0].branch}`)

  const oldKnowledge = await api.call(`/api/projects/${project.id}/knowledge`, {
    method: 'POST', headers: json,
    body: JSON.stringify({
      docType: 'process', title: '收货扫码旧知识', body: '旧做法：收货扫码重复由人工核对处理。',
      sourceRefs: [{ artifactId: artifact.id, locator: '§1', note: null }],
    }),
  })
  await api.call(`/api/projects/${project.id}/knowledge/${oldKnowledge.id}/submit-review`, { method: 'POST' })
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-reviewer', password }),
  })
  await api.call(`/api/projects/${project.id}/knowledge/${oldKnowledge.id}/verify`, { method: 'POST' })
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-owner', password }),
  })
  record('old-knowledge-verified', true, oldKnowledge.title)

  const requirement = await api.call(`/api/projects/${project.id}/work-items`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ title: '收货扫码重复入库', summary: '客户现场口头描述', priority: 'high', sourceNote: '脱敏会议' }),
  })
  await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/evidence`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ evidenceType: 'conversation', text: '重复扫码会重复入库，要求当天闭环。', artifactId: null, locator: null, capturedAt: null }),
  })
  for (const dimension of ['background', 'goals', 'current_process', 'target_process', 'business_rules', 'roles_and_scope', 'exceptions', 'data_and_metrics', 'integrations', 'acceptance', 'constraints']) {
    await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/assessments/${dimension}`, {
      method: 'PUT', headers: json,
      body: JSON.stringify({ state: 'confirmed', note: '已确认', assumptionNote: null, decisionOwner: null }),
    })
  }
  await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/confirm-readiness`, { method: 'POST' })
  const version = await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/solutions`, {
    method: 'POST', headers: json, body: JSON.stringify({ title: '收货扫码异常处理方案', changeSummary: '初版' }),
  })
  const business = await api.call(
    `/api/projects/${project.id}/work-items/${requirement.id}/solutions/${version.id}/design-items`,
    { method: 'POST', headers: json, body: JSON.stringify({ itemKind: 'business', parentId: null, title: '收货扫码流程', body: '重复扫码冻结库存并转人工复核。' }) },
  )
  const technical = await api.call(
    `/api/projects/${project.id}/work-items/${requirement.id}/solutions/${version.id}/design-items`,
    { method: 'POST', headers: json, body: JSON.stringify({ itemKind: 'technical', parentId: business.id, title: '扫码服务改造', body: '扫码接口增加幂等校验。' }) },
  )
  const criterion = await api.call(
    `/api/projects/${project.id}/work-items/${requirement.id}/solutions/${version.id}/acceptance-criteria`,
    { method: 'POST', headers: json, body: JSON.stringify({ designItemId: business.id, text: '重复扫码被拦截且当天闭环' }) },
  )
  const task = await api.call(
    `/api/projects/${project.id}/work-items/${requirement.id}/solutions/${version.id}/tasks`,
    { method: 'POST', headers: json, body: JSON.stringify({ designItemId: technical.id, acceptanceCriterionId: criterion.id, title: '实现扫码重复校验', description: '幂等校验', doneWhen: '当天闭环', dependsOnTaskId: null }) },
  )
  await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/solutions/${version.id}/submit`, { method: 'POST' })
  let selfApproveStatus = 0
  try {
    await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/solutions/${version.id}/approve`, { method: 'POST', headers: json, body: JSON.stringify({ comment: 'self' }) })
  } catch (error) {
    selfApproveStatus = error.status
  }
  record('solution-self-approval-blocked', selfApproveStatus === 403, `status ${selfApproveStatus}`)
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-reviewer', password }),
  })
  await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/solutions/${version.id}/approve`, {
    method: 'POST', headers: json, body: JSON.stringify({ comment: '同意' }),
  })
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-owner', password }),
  })
  record('solution-approved', true, `v${version.version}`)

  const handoff = await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/handoff`)
  record('handoff-package',
    handoff.markdown.includes('开发交接包') && handoff.markdown.includes(baseline.id)
      && handoff.markdown.includes('实现扫码重复校验'),
    `baseline ${baseline.id.slice(0, 8)}`)

  writeFileSync(join(repo, 'src', 'main', 'java', 'com', 'acme', 'mes', 'ScanService.java'),
    'class ScanService { boolean idempotent(String barcode) { return true; } }\n')
  git('add', '-A')
  git('commit', '-m', 'feat: idempotent scan validation')
  const toCommit = git('rev-parse', 'HEAD')

  const execution = await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/executions`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ repositoryId: repository.id, baselineId: baseline.id, toCommit, summary: '实现扫码幂等校验', testsNote: 'mvn verify 通过', completedTaskIds: [task.id] }),
  })
  record('execution-recorded', execution.diverged === false, `${execution.fromCommit.slice(0, 8)} → ${execution.toCommit.slice(0, 8)}`)

  const matrix = await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/acceptance-matrix`)
  record('acceptance-matrix', matrix.covered === 1 && matrix.missingTasks.length === 0,
    `covered ${matrix.covered}/${matrix.matrix.length}`)

  await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/executions/${execution.id}/deviations`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ deviationType: 'extra', targetType: null, targetId: null, description: '额外增加扫码日志' }),
  })
  record('deviation-recorded', true, 'extra implementation captured')

  const snapshot = await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/deliveries`, {
    method: 'POST', headers: json, body: JSON.stringify({ title: 'As-built v1' }),
  })
  record('as-built-draft', snapshot.status === 'draft' && snapshot.bodyMarkdown.includes('验收矩阵'),
    snapshot.title)
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-reviewer', password }),
  })
  const approved = await api.call(
    `/api/projects/${project.id}/work-items/${requirement.id}/deliveries/${snapshot.id}/approve`,
    { method: 'POST' },
  )
  record('as-built-approved', approved.status === 'approved' && Boolean(approved.artifactId),
    `artifact ${String(approved.artifactId).slice(0, 8)}`)

  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-owner', password }),
  })
  const proposal = await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/knowledge-proposals`, {
    method: 'POST', headers: json, body: JSON.stringify({ snapshotId: snapshot.id, title: '交付经验：扫码重复入库处理' }),
  })
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-reviewer', password }),
  })
  const accepted = await api.call(
    `/api/projects/${project.id}/work-items/${requirement.id}/knowledge-proposals/${proposal.id}/accept`,
    { method: 'POST' },
  )
  record('knowledge-draft-created', Boolean(accepted.knowledgeDocumentId), 'proposal accepted')
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-owner', password }),
  })
  await api.call(`/api/projects/${project.id}/knowledge/${accepted.knowledgeDocumentId}/submit-review`, { method: 'POST' })

  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-reviewer', password }),
  })
  const verified = await api.call(`/api/projects/${project.id}/knowledge/${accepted.knowledgeDocumentId}/verify`, { method: 'POST' })
  record('knowledge-published', verified.status === 'verified' && verified.applicableBaselineId === baseline.id,
    'four-eyes review completed')

  const superseded = await api.call(
    `/api/projects/${project.id}/knowledge/${oldKnowledge.id}/supersede`,
    { method: 'POST', headers: json, body: JSON.stringify({ replacementDocumentId: verified.id }) },
  )
  record('knowledge-superseded',
    superseded.status === 'superseded' && superseded.supersededBy === verified.id,
    `old -> ${verified.id.slice(0, 8)}`)

  const hits = await api.call(`/api/projects/${project.id}/knowledge/search?q=${encodeURIComponent('收货扫码')}&topK=10`)
  record('search-uses-current-knowledge',
    hits.length > 0 && hits.every((hit) => hit.documentId === verified.id),
    `${hits.length} hit(s), superseded excluded`)

  const reviewerAudit = await api.call('/api/audit/me?limit=200')
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'accept-owner', password }),
  })
  const ownerAudit = await api.call('/api/audit/me?limit=200')
  const actions = new Set([...reviewerAudit, ...ownerAudit].map((entry) => entry.action))
  record('audit-trail',
    actions.has('handoff.generate') && actions.has('execution.record')
      && actions.has('delivery.snapshot_approve') && actions.has('knowledge.supersede'),
    `${reviewerAudit.length + ownerAudit.length} audit rows (owner + reviewer)`)

  await api.call('/api/auth/logout', { method: 'POST' })
  let anonymousStatus = 0
  try {
    await api.call('/api/projects')
  } catch (error) {
    anonymousStatus = error.status
  }
  record('logout-revokes-session', anonymousStatus === 401, `status ${anonymousStatus}`)
} catch (error) {
  record('unexpected-error', false, error.message)
} finally {
  if (server && server.exitCode === null) {
    if (process.platform === 'win32') {
      spawnSync('taskkill', ['/pid', String(server.pid), '/T', '/F'], { stdio: 'ignore' })
    } else {
      server.kill('SIGTERM')
    }
  }
  if (!process.env.MAMMOTH_KEEP_DRILL) {
    try {
      rmSync(workRoot, { recursive: true, force: true })
    } catch {
      // ignore cleanup failures
    }
  }
}

const passed = steps.filter((step) => step.ok).length
const failed = steps.length - passed
console.log(JSON.stringify({ passed, failed, steps }, null, 2))
process.exitCode = failed === 0 ? 0 : 1
