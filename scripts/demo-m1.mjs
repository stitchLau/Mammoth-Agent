// Mammoth M1 end-to-end demo (synthetic data only, session-cookie authentication).
// Run: node scripts/demo-m1.mjs http://127.0.0.1:8080
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

async function callAnonymous(path) {
  const response = await fetch(base + path)
  const text = await response.text()
  return { status: response.status, body: text }
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

try {
  const health = await call('/api/health')
  record('health', health.status === 'up', health.status)
  // Force CSRF token creation (deferred until first protected mutation otherwise).
  const csrf = await call('/api/auth/csrf')
  record('csrf-token', typeof csrf.token === 'string' && csrf.token.length > 0, 'XSRF-TOKEN cookie issued')

  // Bootstrap the first account, then sign in (session cookie + CSRF cookie).
  const ownerUser = `demo-owner-${stamp}`
  const owner = await call('/api/users', { method: 'POST', headers: json, body: JSON.stringify({ username: ownerUser, displayName: 'Demo Owner', password: 'Passw0rd!2026' }) })
  record('bootstrap-first-user', owner.bootstrap === true, owner.username)

  const login = await call('/api/auth/login', { method: 'POST', headers: json, body: JSON.stringify({ username: ownerUser, password: 'Passw0rd!2026' }) })
  record('login', login.username === ownerUser, 'session cookie issued')

  const me = await call('/api/auth/me')
  record('session-auth', me.username === ownerUser, me.id.slice(0, 8) + '…')
  const anonymous = await callAnonymous('/api/projects')
  record('anonymous-access-blocked', anonymous.status === 401, `status ${anonymous.status}`)

  const reviewer = await call('/api/users', { method: 'POST', headers: json, body: JSON.stringify({ username: `demo-reviewer-${stamp}`, displayName: 'Demo Reviewer', password: 'Passw0rd!2026' }) })
  const outsider = await call('/api/users', { method: 'POST', headers: json, body: JSON.stringify({ username: `demo-outsider-${stamp}`, displayName: 'Demo Outsider', password: 'Passw0rd!2026' }) })
  record('users', Boolean(reviewer.id && outsider.id), 'authenticated user creation')

  const customer = await call('/api/customers', { method: 'POST', headers: json, body: JSON.stringify({ name: `DEMO-ACME-${stamp}` }) })
  const product = await call('/api/products', { method: 'POST', headers: json, body: JSON.stringify({ name: 'DEMO-MES' }) })
  const project = await call('/api/projects', { method: 'POST', headers: json, body: JSON.stringify({ customerId: customer.id, code: `DEMO-${stamp}`, name: 'M1 演示项目' }) })
  record('master-data', Boolean(customer.id && product.id && project.id), `project ${project.code}`)

  await call(`/api/projects/${project.id}/members`, { method: 'POST', headers: json, body: JSON.stringify({ userId: reviewer.id, role: 'reviewer' }) })
  record('membership', true, 'reviewer joined project')

  const content = new TextEncoder().encode('演示原始纪要：收货流程中条码重复时冻结库存并人工复核。')
  const upload = async (name) => {
    const form = new FormData()
    form.append('file', new Blob([content], { type: 'text/markdown' }), name)
    return call(`/api/projects/${project.id}/artifacts`, { method: 'POST', body: form })
  }
  const artifact = await upload('demo-notes.md')
  const artifactDup = await upload('demo-notes-copy.md')
  record('artifact-upload', artifact.id === artifactDup.id && artifact.contentHash.length === 64, `dedupe same id, hash ${artifact.contentHash.slice(0, 12)}…`)

  const draft = await call(`/api/projects/${project.id}/knowledge`, {
    method: 'POST', headers: json, body: JSON.stringify({
      docType: 'process',
      title: '收货流程异常处理',
      body: '收货流程中条码重复时，先冻结库存再人工复核，异常需当天闭环。',
      sourceRefs: [{ artifactId: artifact.id, locator: '§2', note: '演示纪要' }],
    }),
  })
  record('knowledge-draft', draft.status === 'draft' && draft.sourceRefs.length === 1, draft.status)

  await expectStatus(() => call(`/api/projects/${project.id}/knowledge/${draft.id}/verify`, { method: 'POST' }), 409, 'verify-before-review-blocked')
  const pending = await call(`/api/projects/${project.id}/knowledge/${draft.id}/submit-review`, { method: 'POST' })
  record('submit-review', pending.status === 'needs_review', pending.status)
  await expectStatus(() => call(`/api/projects/${project.id}/knowledge/${draft.id}/verify`, { method: 'POST' }), 403, 'self-review-blocked')

  // The reviewer must sign in separately to exercise four-eyes review.
  await call('/api/auth/logout', { method: 'POST' })
  await call('/api/auth/login', { method: 'POST', headers: json, body: JSON.stringify({ username: `demo-reviewer-${stamp}`, password: 'Passw0rd!2026' }) })
  const verified = await call(`/api/projects/${project.id}/knowledge/${draft.id}/verify`, { method: 'POST' })
  record('human-review', verified.status === 'verified' && verified.reviewer === reviewer.id, `reviewer ${String(verified.reviewer).slice(0, 8)}…`)

  const hits = await call(`/api/projects/${project.id}/knowledge/search?q=${encodeURIComponent('条码重复怎么处理')}&topK=5`)
  const hitOfVerified = hits.some((hit) => hit.documentId === verified.id)
  record('search-with-citations', hits.length > 0 && hitOfVerified && hits.every((hit) => hit.sourceRefCount > 0), `${hits.length} hit(s), citations present`)

  const document = await call(`/api/projects/${project.id}/knowledge/${verified.id}`)
  record('read-document', document.sourceRefs.length === 1 && document.status === 'verified', 'document + source refs readable by member')

  const myAudit = await call('/api/audit/me?limit=10')
  record('audit-query', Array.isArray(myAudit), `${myAudit.length} audit rows for reviewer`)

  await call('/api/auth/logout', { method: 'POST' })
  await expectStatus(() => call('/api/projects'), 401, 'logout-revokes-session')
} catch (error) {
  record('unexpected-error', false, error.message)
}

const passed = steps.filter((step) => step.ok).length
const failed = steps.length - passed
console.log(JSON.stringify({ base, passed, failed, steps }, null, 2))
process.exitCode = failed === 0 ? 0 : 1
