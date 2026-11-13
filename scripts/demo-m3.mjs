// Mammoth M3 end-to-end demo (synthetic data only): repository baseline -> requirement/solution ->
// handoff -> execution recovery -> acceptance matrix -> As-built -> knowledge update + staleness.
// Run: node scripts/demo-m3.mjs http://127.0.0.1:8080
import { execFileSync } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync, mkdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const base = process.argv[2] ?? 'http://127.0.0.1:8080'
const stamp = Date.now().toString().slice(-6)
const steps = []
const jar = new Map()
const password = 'Passw0rd!2026'

function record(name, ok, detail) {
  steps.push({ name, ok, detail })
  if (!ok) {
    console.error(`FAIL ${name}: ${detail}`)
  }
}

function absorb(response) {
  const cookies = typeof response.headers.getSetCookie === 'function' ? response.headers.getSetCookie() : []
  for (const cookie of cookies) {
    const pair = cookie.split(';')[0]
    const index = pair.indexOf('=')
    if (index > 0) {
      jar.set(pair.slice(0, index), pair.slice(index + 1))
    }
  }
}

function cookieHeader() {
  return [...jar.entries()].map(([name, value]) => `${name}=${value}`).join('; ')
}

async function call(path, options = {}) {
  const headers = { ...(options.headers ?? {}) }
  const cookies = cookieHeader()
  if (cookies) {
    headers.Cookie = cookies
  }
  if (jar.has('XSRF-TOKEN') && !['GET', 'HEAD', 'OPTIONS'].includes((options.method ?? 'GET').toUpperCase())) {
    headers['X-XSRF-TOKEN'] = jar.get('XSRF-TOKEN')
  }
  const response = await fetch(base + path, { ...options, headers })
  absorb(response)
  const text = await response.text()
  if (!response.ok) {
    const error = new Error(`${response.status} ${text}`)
    error.status = response.status
    throw error
  }
  return text ? JSON.parse(text) : null
}

async function expectStatus(fn, expected, name) {
  try {
    await fn()
    record(name, false, `expected ${expected}, got success`)
  } catch (error) {
    record(name, error.status === expected, `expected ${expected}, got ${error.status ?? error.message}`)
  }
}

const json = { 'Content-Type': 'application/json' }
const DIMENSIONS = [
  'background', 'goals', 'current_process', 'target_process', 'business_rules',
  'roles_and_scope', 'exceptions', 'data_and_metrics', 'integrations', 'acceptance', 'constraints',
]

function git(cwd, ...args) {
  return execFileSync('git', args, { cwd, encoding: 'utf8' }).trim()
}

const repoRoot = mkdtempSync(join(tmpdir(), 'mammoth-m3-demo-'))
let workItemId = ''
let solutionVersionId = ''
let taskId = ''
let baselineId = ''

async function login(username) {
  try {
    await call('/api/auth/logout', { method: 'POST' })
  } catch {
    // no session yet
  }
  await call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username, password }),
  })
}

try {
  const health = await call('/api/health')
  record('health', health.status === 'up', health.status)
  const csrf = await call('/api/auth/csrf')
  record('csrf-token', typeof csrf.token === 'string' && csrf.token.length > 0, 'XSRF-TOKEN issued')

  const ownerName = `m3-owner-${stamp}`
  const reviewerName = `m3-reviewer-${stamp}`
  const owner = await call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: ownerName, displayName: 'M3 Owner', password }),
  })
  record('bootstrap-first-user', owner.bootstrap === true, owner.username)
  await login(ownerName)
  const reviewer = await call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: reviewerName, displayName: 'M3 Reviewer', password }),
  })
  const customer = await call('/api/customers', {
    method: 'POST', headers: json, body: JSON.stringify({ name: `M3-ACME-${stamp}` }),
  })
  const project = await call('/api/projects', {
    method: 'POST', headers: json,
    body: JSON.stringify({ customerId: customer.id, code: `M3-${stamp}`, name: 'M3 交付项目' }),
  })
  await call(`/api/projects/${project.id}/members`, {
    method: 'POST', headers: json, body: JSON.stringify({ userId: reviewer.id, role: 'reviewer' }),
  })
  record('project-and-reviewer', Boolean(project.id && reviewer.id), project.code)

  // Real disposable git repository (outside the Mammoth data root).
  const repo = join(repoRoot, 'repo')
  mkdirSync(join(repo, 'src'), { recursive: true })
  git(repo, 'init', '-b', 'main')
  git(repo, 'config', 'user.email', 'demo@example.com')
  git(repo, 'config', 'user.name', 'M3 Demo')
  writeFileSync(join(repo, 'src', 'App.java'), 'class App {}\n')
  git(repo, 'add', '-A')
  git(repo, 'commit', '-m', 'initial')
  git(repo, 'remote', 'add', 'origin', 'https://user:secret-token@example.com/team/repo.git')
  const baselineCommit = git(repo, 'rev-parse', 'HEAD')
  record('git-fixture', baselineCommit.length === 40, `repo ${repo}`)

  const repository = await call(`/api/projects/${project.id}/repositories`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ label: 'backend', path: repo, defaultBranch: 'main' }),
  })
  record('repository-registered', repository.absolutePath.endsWith('repo'),
    `remote ${repository.remoteUrl}`)
  record('remote-url-sanitized', !String(repository.remoteUrl).includes('secret-token'),
    repository.remoteUrl)

  const baseline = await call(`/api/projects/${project.id}/baselines`, {
    method: 'POST', headers: json, body: JSON.stringify({ note: 'before implementation', allowDirty: false }),
  })
  baselineId = baseline.id
  record('baseline-captured',
    baseline.complete === true && baseline.repos[0].commitSha === baselineCommit,
    `${baseline.repos[0].commitSha.slice(0, 8)} on ${baseline.repos[0].branch}`)

  const requirement = await call(`/api/projects/${project.id}/work-items`, {
    method: 'POST', headers: json,
    body: JSON.stringify({
      title: '收货扫码重复入库', summary: '客户口头描述，要求当天闭环', priority: 'high', sourceNote: '现场会议',
    }),
  })
  workItemId = requirement.id
  await call(`/api/projects/${project.id}/work-items/${workItemId}/evidence`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ evidenceType: 'conversation', text: '重复扫码两次都会入库。', artifactId: null, locator: null, capturedAt: null }),
  })
  for (const dimension of DIMENSIONS) {
    await call(`/api/projects/${project.id}/work-items/${workItemId}/assessments/${dimension}`, {
      method: 'PUT', headers: json,
      body: JSON.stringify({ state: 'confirmed', note: '已确认', assumptionNote: null, decisionOwner: null }),
    })
  }
  await call(`/api/projects/${project.id}/work-items/${workItemId}/confirm-readiness`, { method: 'POST' })
  record('requirement-ready', true, '11 dimensions confirmed, snapshot captured')

  const version = await call(`/api/projects/${project.id}/work-items/${workItemId}/solutions`, {
    method: 'POST', headers: json, body: JSON.stringify({ title: '收货扫码处理方案', changeSummary: '初版' }),
  })
  solutionVersionId = version.id
  const business = await call(
    `/api/projects/${project.id}/work-items/${workItemId}/solutions/${solutionVersionId}/design-items`,
    { method: 'POST', headers: json, body: JSON.stringify({ itemKind: 'business', parentId: null, title: '收货扫码流程', body: '重复扫码冻结库存并转人工复核。' }) },
  )
  const technical = await call(
    `/api/projects/${project.id}/work-items/${workItemId}/solutions/${solutionVersionId}/design-items`,
    { method: 'POST', headers: json, body: JSON.stringify({ itemKind: 'technical', parentId: business.id, title: '扫码服务改造', body: '扫码接口增加幂等校验。' }) },
  )
  const criterion = await call(
    `/api/projects/${project.id}/work-items/${workItemId}/solutions/${solutionVersionId}/acceptance-criteria`,
    { method: 'POST', headers: json, body: JSON.stringify({ designItemId: business.id, text: '重复扫码被拦截且当天闭环' }) },
  )
  taskId = (await call(
    `/api/projects/${project.id}/work-items/${workItemId}/solutions/${solutionVersionId}/tasks`,
    { method: 'POST', headers: json, body: JSON.stringify({ designItemId: technical.id, acceptanceCriterionId: criterion.id, title: '实现扫码重复校验', description: '幂等校验', doneWhen: '返回可读提示', dependsOnTaskId: null }) },
  )).id
  await call(`/api/projects/${project.id}/work-items/${workItemId}/solutions/${solutionVersionId}/submit`, { method: 'POST' })
  await expectStatus(
    () => call(`/api/projects/${project.id}/work-items/${workItemId}/solutions/${solutionVersionId}/approve`, { method: 'POST', headers: json, body: JSON.stringify({ comment: '自批' }) }),
    403, 'author-self-approval-blocked',
  )
  await login(reviewerName)
  await call(`/api/projects/${project.id}/work-items/${workItemId}/solutions/${solutionVersionId}/approve`, {
    method: 'POST', headers: json, body: JSON.stringify({ comment: '同意' }),
  })
  record('solution-approved', true, `v${version.version}`)

  const handoff = await call(`/api/projects/${project.id}/work-items/${workItemId}/handoff`)
  record('handoff-generated',
    handoff.markdown.includes('开发交接包') && handoff.markdown.includes(baselineId)
      && handoff.markdown.includes('实现扫码重复校验'),
    `baseline ${baselineId.slice(0, 8)}`)

  // Implement: second commit, then recover the range from the baseline.
  writeFileSync(join(repo, 'src', 'App.java'), 'class App { int duplicateCheck; }\n')
  writeFileSync(join(repo, 'src', 'ScanValidator.java'), 'class ScanValidator {}\n')
  git(repo, 'add', '-A')
  git(repo, 'commit', '-m', 'implementation')
  const toCommit = git(repo, 'rev-parse', 'HEAD')

  await login(ownerName)
  const execution = await call(`/api/projects/${project.id}/work-items/${workItemId}/executions`, {
    method: 'POST', headers: json,
    body: JSON.stringify({
      repositoryId: repository.id, baselineId, toCommit, summary: '实现完成', testsNote: 'mvn verify 通过',
      completedTaskIds: [taskId],
    }),
  })
  record('execution-recorded', execution.diverged === false && execution.fromCommit === baselineCommit,
    `${execution.fromCommit.slice(0, 8)} → ${execution.toCommit.slice(0, 8)}`)

  const matrix = await call(`/api/projects/${project.id}/work-items/${workItemId}/acceptance-matrix`)
  record('acceptance-matrix-covered',
    matrix.covered === 1 && matrix.matrix[0].coverage === 'covered' && matrix.missingTasks.length === 0,
    `covered ${matrix.covered}/${matrix.matrix.length}`)

  await call(`/api/projects/${project.id}/work-items/${workItemId}/executions/${execution.id}/deviations`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ deviationType: 'extra', targetType: 'task', targetId: taskId, description: '额外增加了扫码日志' }),
  })
  record('deviation-recorded', true, 'extra implementation captured')

  const snapshot = await call(`/api/projects/${project.id}/work-items/${workItemId}/deliveries`, {
    method: 'POST', headers: json, body: JSON.stringify({ title: 'As-built v1' }),
  })
  record('delivery-snapshot-draft',
    snapshot.status === 'draft' && snapshot.bodyMarkdown.includes('验收矩阵'),
    snapshot.title)
  await expectStatus(
    () => call(`/api/projects/${project.id}/work-items/${workItemId}/deliveries/${snapshot.id}/approve`, { method: 'POST' }),
    403, 'snapshot-self-approval-blocked',
  )

  await login(reviewerName)
  const approvedSnapshot = await call(`/api/projects/${project.id}/work-items/${workItemId}/deliveries/${snapshot.id}/approve`, { method: 'POST' })
  record('snapshot-approved', approvedSnapshot.status === 'approved' && Boolean(approvedSnapshot.artifactId),
    `artifact ${String(approvedSnapshot.artifactId).slice(0, 8)}`)

  await login(ownerName)
  const proposal = await call(`/api/projects/${project.id}/work-items/${workItemId}/knowledge-proposals`, {
    method: 'POST', headers: json, body: JSON.stringify({ snapshotId: snapshot.id, title: '交付经验：扫码重复入库处理' }),
  })
  await expectStatus(
    () => call(`/api/projects/${project.id}/work-items/${workItemId}/knowledge-proposals/${proposal.id}/accept`, { method: 'POST' }),
    403, 'proposal-self-accept-blocked',
  )

  await login(reviewerName)
  const accepted = await call(`/api/projects/${project.id}/work-items/${workItemId}/knowledge-proposals/${proposal.id}/accept`, { method: 'POST' })
  record('proposal-accepted', accepted.status === 'accepted' && Boolean(accepted.knowledgeDocumentId),
    `knowledge draft ${String(accepted.knowledgeDocumentId).slice(0, 8)}`)

  const knowledgeBefore = await call(`/api/projects/${project.id}/knowledge`)
  const draft = knowledgeBefore.find((entry) => entry.id === accepted.knowledgeDocumentId)
  record('knowledge-draft-created',
    draft && draft.status === 'draft' && draft.applicableBaselineId === baselineId && draft.stale === false,
    `baseline ${String(draft?.applicableBaselineId).slice(0, 8)}, stale=${draft?.stale}`)

  await login(ownerName)
  const newer = await call(`/api/projects/${project.id}/baselines`, {
    method: 'POST', headers: json, body: JSON.stringify({ note: 'after implementation', allowDirty: false }),
  })
  record('newer-baseline-captured', newer.id !== baselineId, newer.id.slice(0, 8))

  const knowledgeAfter = await call(`/api/projects/${project.id}/knowledge`)
  const staleDraft = knowledgeAfter.find((entry) => entry.id === accepted.knowledgeDocumentId)
  record('knowledge-marked-stale', staleDraft?.stale === true && staleDraft?.status === 'draft',
    'advisory flag only; status unchanged')

  const ownerAudit = await call('/api/audit/me?limit=200')
  await login(reviewerName)
  const reviewerAudit = await call('/api/audit/me?limit=200')
  const actions = new Set([...ownerAudit, ...reviewerAudit].map((entry) => entry.action))
  record('audit-trail',
    actions.has('repository.register') && actions.has('baseline.capture')
      && actions.has('execution.record') && actions.has('delivery.snapshot_approve')
      && actions.has('knowledge_proposal.accept'),
    `${ownerAudit.length + reviewerAudit.length} audit rows (owner + reviewer)`)

  await call('/api/auth/logout', { method: 'POST' })
  await expectStatus(() => call('/api/projects'), 401, 'logout-revokes-session')
} catch (error) {
  record('unexpected-error', false, error.message)
} finally {
  try {
    rmSync(repoRoot, { recursive: true, force: true })
  } catch {
    // ignore cleanup failures
  }
}

const passed = steps.filter((step) => step.ok).length
const failed = steps.length - passed
console.log(JSON.stringify({ base, passed, failed, steps }, null, 2))
process.exitCode = failed === 0 ? 0 : 1
