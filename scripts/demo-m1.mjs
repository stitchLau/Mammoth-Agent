// Mammoth M1 end-to-end demo (synthetic data only).
// Run: node scripts/demo-m1.mjs http://127.0.0.1:8080
const base = process.argv[2] ?? 'http://127.0.0.1:8080'
const stamp = Date.now().toString().slice(-6)
const steps = []

function record(name, ok, detail) {
  steps.push({ name, ok, detail })
  if (!ok) {
    console.error(`FAIL ${name}: ${detail}`)
  }
}

async function call(path, options = {}, userId) {
  const headers = { ...(options.headers ?? {}) }
  if (userId) headers['X-Mammoth-User-Id'] = userId
  const response = await fetch(base + path, { ...options, headers })
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

try {
  const health = await call('/api/health')
  record('health', health.status === 'up', health.status)

  const json = { 'Content-Type': 'application/json' }
  const owner = await call('/api/users', { method: 'POST', headers: json, body: JSON.stringify({ username: `demo-owner-${stamp}`, displayName: 'Demo Owner', password: 'Passw0rd!2026' }) })
  const reviewer = await call('/api/users', { method: 'POST', headers: json, body: JSON.stringify({ username: `demo-reviewer-${stamp}`, displayName: 'Demo Reviewer', password: 'Passw0rd!2026' }) })
  const outsider = await call('/api/users', { method: 'POST', headers: json, body: JSON.stringify({ username: `demo-outsider-${stamp}`, displayName: 'Demo Outsider', password: 'Passw0rd!2026' }) })
  record('users', Boolean(owner.id && reviewer.id && outsider.id), 'owner/reviewer/outsider created')

  const customer = await call('/api/customers', { method: 'POST', headers: json, body: JSON.stringify({ name: `DEMO-ACME-${stamp}` }) }, owner.id)
  const product = await call('/api/products', { method: 'POST', headers: json, body: JSON.stringify({ name: 'DEMO-MES' }) }, owner.id)
  const project = await call('/api/projects', { method: 'POST', headers: json, body: JSON.stringify({ customerId: customer.id, code: `DEMO-${stamp}`, name: 'M1 演示项目' }) }, owner.id)
  record('master-data', Boolean(customer.id && product.id && project.id), `project ${project.code}`)

  await call(`/api/projects/${project.id}/members`, { method: 'POST', headers: json, body: JSON.stringify({ userId: reviewer.id, role: 'reviewer' }) }, owner.id)
  record('membership', true, 'reviewer joined project')

  const form = new FormData()
  form.append('file', new Blob([new TextEncoder().encode('演示原始纪要：收货流程中条码重复时冻结库存并人工复核。')], { type: 'text/markdown' }), 'demo-notes.md')
  const artifact = await call(`/api/projects/${project.id}/artifacts`, { method: 'POST', body: form }, owner.id)
  const formDup = new FormData()
  formDup.append('file', new Blob([new TextEncoder().encode('演示原始纪要：收货流程中条码重复时冻结库存并人工复核。')], { type: 'text/markdown' }), 'demo-notes-copy.md')
  const artifactDup = await call(`/api/projects/${project.id}/artifacts`, { method: 'POST', body: formDup }, owner.id)
  record('artifact-upload', artifact.id === artifactDup.id && artifact.contentHash.length === 64, `dedupe same id, hash ${artifact.contentHash.slice(0, 12)}…`)

  const draft = await call(`/api/projects/${project.id}/knowledge`, {
    method: 'POST', headers: json, body: JSON.stringify({
      docType: 'process',
      title: '收货流程异常处理',
      body: '收货流程中条码重复时，先冻结库存再人工复核，异常需当天闭环。',
      sourceRefs: [{ artifactId: artifact.id, locator: '§2', note: '演示纪要' }],
    }),
  }, owner.id)
  record('knowledge-draft', draft.status === 'draft' && draft.sourceRefs.length === 1, draft.status)

  await expectStatus(() => call(`/api/projects/${project.id}/knowledge/${draft.id}/verify`, { method: 'POST' }, reviewer.id), 409, 'verify-before-review-blocked')
  const pending = await call(`/api/projects/${project.id}/knowledge/${draft.id}/submit-review`, { method: 'POST' }, owner.id)
  record('submit-review', pending.status === 'needs_review', pending.status)
  await expectStatus(() => call(`/api/projects/${project.id}/knowledge/${draft.id}/verify`, { method: 'POST' }, owner.id), 403, 'self-review-blocked')
  const verified = await call(`/api/projects/${project.id}/knowledge/${draft.id}/verify`, { method: 'POST' }, reviewer.id)
  record('human-review', verified.status === 'verified' && verified.reviewer === reviewer.id, `reviewer ${String(verified.reviewer).slice(0, 8)}…`)

  const hits = await call(`/api/projects/${project.id}/knowledge/search?q=${encodeURIComponent('条码重复怎么处理')}&topK=5`, {}, owner.id)
  const hitOfVerified = hits.some((hit) => hit.documentId === verified.id)
  record('search-with-citations', hits.length > 0 && hitOfVerified && hits.every((hit) => hit.sourceRefCount > 0), `${hits.length} hit(s), citations present`)
  await expectStatus(() => call(`/api/projects/${project.id}/knowledge/search?q=x`, {}, outsider.id), 403, 'outsider-search-blocked')

  const document = await call(`/api/projects/${project.id}/knowledge/${verified.id}`, {}, reviewer.id)
  record('read-document', document.sourceRefs.length === 1 && document.status === 'verified', 'document + source refs readable by member')

  await expectStatus(() => call(`/api/projects/${project.id}/artifacts`, { method: 'POST', body: formDup }, outsider.id), 403, 'outsider-upload-blocked')
} catch (error) {
  record('unexpected-error', false, error.message)
}

const passed = steps.filter((step) => step.ok).length
const failed = steps.length - passed
console.log(JSON.stringify({ base, passed, failed, steps }, null, 2))
process.exitCode = failed === 0 ? 0 : 1
