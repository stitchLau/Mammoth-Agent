// M4 API demo: S3 Jira CSV import -> evidence mapping, and S5 in-app notification.
// Requires a running backend on a FRESH data root, e.g.:
//   java -jar services/domain/target/mammoth-domain-service.jar --server.port=8080 \
//     --mammoth.data-root=%TEMP%\mammoth-demo-m4
// Then: node scripts/demo-m4.mjs
// Synthetic data only; no real Jira/CSM/SMTP/WeCom calls.
const base = process.env.MAMMOTH_BASE ?? 'http://127.0.0.1:8080'
const password = 'Passw0rd!2026'
const steps = []

function record(name, ok, detail) {
  steps.push({ name, ok, detail })
  if (!ok) console.error(`FAIL ${name}: ${detail}`)
}

function client() {
  const jar = new Map()
  async function call(path, options = {}) {
    const headers = { ...(options.headers ?? {}) }
    if (jar.size) headers.Cookie = [...jar.entries()].map(([k, v]) => `${k}=${v}`).join('; ')
    if (jar.get('XSRF-TOKEN') && !['GET', 'HEAD', 'OPTIONS'].includes((options.method ?? 'GET').toUpperCase())) {
      headers['X-XSRF-TOKEN'] = jar.get('XSRF-TOKEN')
    }
    const response = await fetch(base + path, { ...options, headers })
    for (const cookie of typeof response.headers.getSetCookie === 'function' ? response.headers.getSetCookie() : []) {
      const pair = cookie.split(';')[0]
      const index = pair.indexOf('=')
      if (index > 0) jar.set(pair.slice(0, index), pair.slice(index + 1))
    }
    const text = await response.text()
    if (!response.ok) throw new Error(`${response.status} ${text.slice(0, 300)}`)
    return text ? JSON.parse(text) : null
  }
  return { call, jar }
}

const json = { 'Content-Type': 'application/json' }
const owner = client()
const reviewer = client()

try {
  const health = await fetch(base + '/api/health')
  record('health', health.ok, health.status)
  await owner.call('/api/auth/csrf')
  await owner.call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: 'm4-owner', displayName: 'M4 Owner', password }),
  })
  await owner.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'm4-owner', password }),
  })
  const reviewerUser = await owner.call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: 'm4-reviewer', displayName: 'M4 Reviewer', password }),
  })
  const customer = await owner.call('/api/customers', {
    method: 'POST', headers: json, body: JSON.stringify({ name: 'M4 Demo Customer' }),
  })
  const project = await owner.call('/api/projects', {
    method: 'POST', headers: json,
    body: JSON.stringify({ customerId: customer.id, code: 'M4-DEMO', name: 'M4 Demo' }),
  })
  await owner.call(`/api/projects/${project.id}/members`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ userId: reviewerUser.id, role: 'reviewer' }),
  })
  const item = await owner.call(`/api/projects/${project.id}/work-items`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ title: 'M4 Demo 导入需求', summary: '从 Jira CSV 导入证据', priority: 'normal', sourceNote: 'demo' }),
  })
  record('project-requirement', Boolean(project.id && item.id), `${project.code}/${item.itemNo}`)

  const source = await owner.call(`/api/projects/${project.id}/external-sources`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ systemType: 'jira', name: 'Demo Jira', baseUrl: 'http://jira.example' }),
  })
  const csv = [
    '问题类型,关键字,概要,描述,Issue URL,状态,经办人,报告人,创建日期,已更新',
    '缺陷,JIRA-DEMO-1,Demo 导入摘要,演示导入描述,http://jira.example/browse/JIRA-DEMO-1,挂起,张三,李四,2026/1/2 9:05,2026/1/3 10:06',
  ].join('\n')
  function form(fileName) {
    const data = new FormData()
    data.append('file', new Blob([csv], { type: 'text/csv' }), fileName)
    data.append('sourceId', source.id)
    data.append('mappingProfile', 'jira')
    return data
  }
  const preview = await owner.call(`/api/projects/${project.id}/imports/preview`, { method: 'POST', body: form('jira.csv') })
  record('jira-preview', preview.acceptedCount === 1 && preview.rejectedCount === 0,
    `${preview.rowCount} rows`)
  const applied = await owner.call(`/api/projects/${project.id}/imports`, { method: 'POST', body: form('jira.csv') })
  record('jira-apply', applied.accepted === 1 && applied.duplicate === false,
    `batch ${applied.batch.id.slice(0, 8)}`)
  const records = await owner.call(`/api/projects/${project.id}/external-records?status=unmapped`)
  record('jira-unmapped', records.length === 1 && records[0].externalKey === 'JIRA-DEMO-1', records.length)
  const mapped = await owner.call(
    `/api/projects/${project.id}/external-records/${records[0].id}/map`,
    { method: 'POST', headers: json, body: JSON.stringify({ workItemId: item.id, criterionId: null }) },
  )
  record('jira-map-evidence', mapped.status === 'mapped' && Boolean(mapped.mappedEvidenceId),
    mapped.mappedEvidenceId)

  const artifactForm = new FormData()
  artifactForm.append('file', new Blob(['M4 demo knowledge source'], { type: 'text/markdown' }), 'source.md')
  const artifact = await owner.call(`/api/projects/${project.id}/artifacts`, { method: 'POST', body: artifactForm })
  const document = await owner.call(`/api/projects/${project.id}/knowledge`, {
    method: 'POST', headers: json,
    body: JSON.stringify({
      docType: 'process', title: 'M4 Demo 知识', body: '演示通知与审核。',
      sourceRefs: [{ artifactId: artifact.id, locator: '全篇', note: 'demo' }],
    }),
  })
  await owner.call(`/api/projects/${project.id}/knowledge/${document.id}/submit-review`, { method: 'POST' })

  await reviewer.call('/api/auth/csrf')
  await reviewer.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'm4-reviewer', password }),
  })
  const notifications = await reviewer.call('/api/notifications')
  const reviewNotification = notifications.find((entry) => entry.type === 'knowledge.review_requested')
  record('notification-in-app', Boolean(reviewNotification), reviewNotification?.title)
  const read = await reviewer.call(`/api/notifications/${reviewNotification.id}/read`, { method: 'POST' })
  record('notification-read', read.status === 'read', read.id.slice(0, 8))
} catch (error) {
  record('unexpected-error', false, error.message)
}

const passed = steps.filter((step) => step.ok).length
console.log(JSON.stringify({ base, passed, failed: steps.length - passed, steps }, null, 2))
process.exit(passed === steps.length ? 0 : 1)
