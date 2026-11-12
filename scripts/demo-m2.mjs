// Mammoth M2 end-to-end demo (synthetic data only): requirement -> evidence -> readiness gate ->
// unified solution -> dual views -> four-eyes approval -> immutable version.
// Run: node scripts/demo-m2.mjs http://127.0.0.1:8080
const base = process.argv[2] ?? 'http://127.0.0.1:8080'
const stamp = Date.now().toString().slice(-6)
const steps = []
const jar = new Map()

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
const password = 'Passw0rd!2026'
const DIMENSIONS = [
  'background', 'goals', 'current_process', 'target_process', 'business_rules',
  'roles_and_scope', 'exceptions', 'data_and_metrics', 'integrations', 'acceptance', 'constraints',
]

try {
  const health = await call('/api/health')
  record('health', health.status === 'up', health.status)
  const csrf = await call('/api/auth/csrf')
  record('csrf-token', typeof csrf.token === 'string' && csrf.token.length > 0, 'XSRF-TOKEN cookie issued')

  const ownerName = `m2-owner-${stamp}`
  const owner = await call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: ownerName, displayName: 'M2 Owner', password }),
  })
  record('bootstrap-first-user', owner.bootstrap === true, owner.username)

  const login = await call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: ownerName, password }),
  })
  record('login', login.username === ownerName, 'session cookie issued')

  const reviewerName = `m2-reviewer-${stamp}`
  const reviewer = await call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: reviewerName, displayName: 'M2 Reviewer', password }),
  })
  const customer = await call('/api/customers', {
    method: 'POST', headers: json, body: JSON.stringify({ name: `M2-ACME-${stamp}` }),
  })
  const project = await call('/api/projects', {
    method: 'POST', headers: json,
    body: JSON.stringify({ customerId: customer.id, code: `M2-${stamp}`, name: 'M2 演示项目' }),
  })
  await call(`/api/projects/${project.id}/members`, {
    method: 'POST', headers: json, body: JSON.stringify({ userId: reviewer.id, role: 'reviewer' }),
  })
  record('project-and-membership', Boolean(project.id && reviewer.id), `${project.code} + reviewer`)

  const form = new FormData()
  form.append('file', new Blob([new TextEncoder().encode('客户会议纪要：收货扫码重复时重复入库，需冻结库存并人工复核。')], {
    type: 'text/markdown',
  }), 'm2-notes.md')
  const artifact = await call(`/api/projects/${project.id}/artifacts`, { method: 'POST', body: form })
  record('artifact-upload', artifact.contentHash.length === 64, artifact.originalName)

  const requirement = await call(`/api/projects/${project.id}/work-items`, {
    method: 'POST', headers: json,
    body: JSON.stringify({
      title: '收货扫码重复入库',
      summary: '客户口头描述：同一托盘重复扫码会重复入库，要求当天闭环，但未给验收标准。',
      priority: 'high',
      sourceNote: '客户现场会议',
    }),
  })
  record('requirement-created', requirement.status === 'draft' && requirement.itemNo === 'REQ-0001',
    `${requirement.itemNo} ${requirement.status}`)

  const evidence1 = await call(`/api/projects/${project.id}/work-items/${requirement.id}/evidence`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ evidenceType: 'conversation', text: '现场反馈：重复扫码两次都会入库。', artifactId: null, locator: null, capturedAt: null }),
  })
  const evidence2 = await call(`/api/projects/${project.id}/work-items/${requirement.id}/evidence`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ evidenceType: 'artifact', text: '会议纪要第 2 节：需冻结库存并人工复核。', artifactId: artifact.id, locator: '§2', capturedAt: null }),
  })
  record('evidence-batches', evidence1.batchNo === 1 && evidence2.batchNo === 2, 'two append-only batches')

  const afterEvidence = await call(`/api/projects/${project.id}/work-items/${requirement.id}`)
  record('draft-to-collecting', afterEvidence.item.status === 'collecting' && afterEvidence.evidence.length === 2,
    `${afterEvidence.item.status}, ${afterEvidence.evidence.length} evidence`)

  const blocked = afterEvidence.readiness
  record('readiness-blocked', blocked.ready === false && blocked.blocking.length >= 4,
    `${blocked.blocking.length} blocking reasons`)

  for (const dimension of DIMENSIONS) {
    if (dimension === 'exceptions') {
      await call(`/api/projects/${project.id}/work-items/${requirement.id}/assessments/${dimension}`, {
        method: 'PUT', headers: json,
        body: JSON.stringify({ state: 'missing', note: '客户未提供异常清单', assumptionNote: '按通用异常处理', decisionOwner: owner.id }),
      })
    } else {
      await call(`/api/projects/${project.id}/work-items/${requirement.id}/assessments/${dimension}`, {
        method: 'PUT', headers: json,
        body: JSON.stringify({ state: 'confirmed', note: '已与客户确认', assumptionNote: null, decisionOwner: null }),
      })
    }
  }
  const ready = await call(`/api/projects/${project.id}/work-items/${requirement.id}/readiness`)
  record('readiness-accepts-documented-assumption', ready.ready === true && ready.blocking.length === 0,
    'exceptions accepted with assumption + decision owner')

  const confirmed = await call(`/api/projects/${project.id}/work-items/${requirement.id}/confirm-readiness`, { method: 'POST' })
  record('readiness-confirmed-with-snapshot', confirmed.ready === true && Boolean(confirmed.snapshotId),
    `snapshot ${String(confirmed.snapshotId).slice(0, 8)}…`)

  const version1 = await call(`/api/projects/${project.id}/work-items/${requirement.id}/solutions`, {
    method: 'POST', headers: json, body: JSON.stringify({ title: '收货扫码异常处理方案', changeSummary: '初版' }),
  })
  const afterVersion = await call(`/api/projects/${project.id}/work-items/${requirement.id}`)
  record('solution-starts-analysis', version1.version === 1 && afterVersion.item.status === 'analyzing',
    `v${version1.version}, item ${afterVersion.item.status}`)

  await expectStatus(() => call(`/api/projects/${project.id}/work-items/${requirement.id}/solutions/${version1.id}/submit`, { method: 'POST' }), 409, 'empty-solution-submit-blocked')

  const versionBase = `/api/projects/${project.id}/work-items/${requirement.id}/solutions/${version1.id}`
  const businessItem = await call(`${versionBase}/design-items`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ itemKind: 'business', parentId: null, title: '收货扫码流程', body: '重复扫码时冻结库存并转人工复核。' }),
  })
  const technicalItem = await call(`${versionBase}/design-items`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ itemKind: 'technical', parentId: businessItem.id, title: '扫码服务改造', body: '扫码接口增加幂等校验。' }),
  })
  await call(`${versionBase}/design-items`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ itemKind: 'shared', parentId: null, title: '数据口径', body: '条码 + 托盘号唯一。' }),
  })
  const criterion = await call(`${versionBase}/acceptance-criteria`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ designItemId: businessItem.id, text: '重复扫码被拦截且当天闭环' }),
  })
  await call(`${versionBase}/tasks`, {
    method: 'POST', headers: json,
    body: JSON.stringify({
      designItemId: technicalItem.id, acceptanceCriterionId: criterion.id,
      title: '实现扫码重复校验', description: '在扫码接口增加幂等判断',
      doneWhen: '重复扫码返回可读提示', dependsOnTaskId: null,
    }),
  })

  const structured = await call(versionBase)
  record('consistency-check-passes', structured.report.ok === true,
    `business ${structured.report.counts.businessItems}, technical ${structured.report.counts.technicalItems}, tasks ${structured.report.counts.tasks}`)

  const businessView = await call(`${versionBase}/render?view=business`)
  const technicalView = await call(`${versionBase}/render?view=technical`)
  record('dual-view-single-model',
    businessView.markdown.includes('业务方案') && !businessView.markdown.includes('技术设计')
      && technicalView.markdown.includes('技术设计') && technicalView.markdown.includes('开发任务')
      && businessView.traceability.length === 1,
    'business/technical rendered from one model, traceability present')

  const submitted = await call(`${versionBase}/submit`, { method: 'POST' })
  record('solution-submitted', submitted.status === 'in_review', submitted.status)

  await expectStatus(() => call(`${versionBase}/approve`, { method: 'POST', headers: json, body: JSON.stringify({ comment: '自批' }) }), 403, 'author-self-approval-blocked')

  await call('/api/auth/logout', { method: 'POST' })
  await call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: reviewerName, password }),
  })
  const approved = await call(`${versionBase}/approve`, {
    method: 'POST', headers: json, body: JSON.stringify({ comment: '同意，纳入本期' }),
  })
  const afterApproval = await call(`/api/projects/${project.id}/work-items/${requirement.id}`)
  record('reviewer-approves', approved.status === 'approved' && afterApproval.item.status === 'approved',
    `${approved.status}, approver ${String(approved.approverUserId).slice(0, 8)}…`)

  await call('/api/auth/logout', { method: 'POST' })
  await call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: ownerName, password }),
  })
  await expectStatus(() => call(`${versionBase}/design-items`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ itemKind: 'business', parentId: null, title: '批准后修改', body: '不应允许' }),
  }), 409, 'approved-version-immutable')

  const revision = await call(`${versionBase}/new-version`, {
    method: 'POST', headers: json, body: JSON.stringify({ changeSummary: '客户补充异常清单' }),
  })
  const afterRevision = await call(`/api/projects/${project.id}/work-items/${requirement.id}`)
  record('new-version-reopens-requirement', revision.version === 2 && afterRevision.item.status === 'analyzing',
    `v${revision.version}, item ${afterRevision.item.status}`)

  const ownerAudit = await call('/api/audit/me?limit=100')
  const ownerActions = new Set(ownerAudit.map((entry) => entry.action))
  record('audit-trail-owner',
    ownerActions.has('work_item.readiness_confirm') && ownerActions.has('solution.create'),
    `${ownerAudit.length} audit rows (owner)`)

  const revisionBase = `/api/projects/${project.id}/work-items/${requirement.id}/solutions/${revision.id}`
  await call(`${revisionBase}/submit`, { method: 'POST' })

  await call('/api/auth/logout', { method: 'POST' })
  await call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: reviewerName, password }),
  })
  await call(`${revisionBase}/approve`, { method: 'POST', headers: json, body: JSON.stringify({ comment: '同意修订' }) })
  const versions = await call(`/api/projects/${project.id}/work-items/${requirement.id}/solutions`)
  record('superseded-history-kept',
    versions.length === 2 && versions[0].status === 'superseded' && versions[1].status === 'approved',
    versions.map((entry) => `v${entry.version}:${entry.status}`).join(', '))

  const finalDetail = await call(`/api/projects/${project.id}/work-items/${requirement.id}`)
  record('snapshots-recorded', finalDetail.snapshots.length === 3 && Boolean(finalDetail.item.approvedSolutionVersionId),
    `${finalDetail.snapshots.length} snapshots (readiness + 2 submissions)`)

  const reviewerAudit = await call('/api/audit/me?limit=100')
  const reviewerActions = new Set(reviewerAudit.map((entry) => entry.action))
  record('audit-trail-reviewer', reviewerActions.has('solution.approve'),
    `${reviewerAudit.length} audit rows (reviewer)`)

  await call('/api/auth/logout', { method: 'POST' })
  await expectStatus(() => call('/api/projects'), 401, 'logout-revokes-session')
} catch (error) {
  record('unexpected-error', false, error.message)
}

const passed = steps.filter((step) => step.ok).length
const failed = steps.length - passed
console.log(JSON.stringify({ base, passed, failed, steps }, null, 2))
process.exitCode = failed === 0 ? 0 : 1
