// ADR-0008 operational drill: seed a data root, export, import into a new root, back up, restore,
// and verify the restored service end-to-end. Uses synthetic data and temporary directories only.
// Run: node scripts/admin-drill.mjs
import { spawn, spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, mkdtempSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const repoRoot = resolve(new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'))
const java = process.env.MAMMOTH_JAVA ?? 'java'
const jar = join(repoRoot, 'services', 'domain', 'target', 'mammoth-domain-service.jar')
const workRoot = mkdtempSync(join(tmpdir(), 'mammoth-adr8-drill-'))
const portBase = 18160
const password = 'Passw0rd!2026'
const importedPassword = 'ImportedPass!2026'
const steps = []

function record(name, ok, detail) {
  steps.push({ name, ok, detail })
  if (!ok) {
    console.error(`FAIL ${name}: ${detail}`)
  }
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex')
}

function cookieJar() {
  const jar = new Map()
  return {
    absorb(response) {
      const cookies = typeof response.headers.getSetCookie === 'function' ? response.headers.getSetCookie() : []
      for (const cookie of cookies) {
        const pair = cookie.split(';')[0]
        const index = pair.indexOf('=')
        if (index > 0) {
          jar.set(pair.slice(0, index), pair.slice(index + 1))
        }
      }
    },
    header() {
      return [...jar.entries()].map(([name, value]) => `${name}=${value}`).join('; ')
    },
    get(name) {
      return jar.get(name)
    },
  }
}

function client(base) {
  const jar = cookieJar()
  async function call(path, options = {}) {
    const headers = { ...(options.headers ?? {}) }
    const cookies = jar.header()
    if (cookies) headers.Cookie = cookies
    if (jar.get('XSRF-TOKEN') && !['GET', 'HEAD', 'OPTIONS'].includes((options.method ?? 'GET').toUpperCase())) {
      headers['X-XSRF-TOKEN'] = jar.get('XSRF-TOKEN')
    }
    const response = await fetch(base + path, { ...options, headers })
    jar.absorb(response)
    const text = await response.text()
    if (!response.ok) {
      const error = new Error(`${response.status} ${text.slice(0, 300)}`)
      error.status = response.status
      throw error
    }
    return text ? JSON.parse(text) : null
  }
  return { call, jar }
}

async function waitHealth(base, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(base + '/api/health')
      if (response.ok) return
    } catch {
      // not up yet
    }
    await new Promise((resolveDelay) => setTimeout(resolveDelay, 400))
  }
  throw new Error('server did not become healthy: ' + base)
}

function startServer(root, port) {
  const child = spawn(java, ['-jar', jar, `--server.port=${port}`, `--mammoth.data-root=${root}`], {
    stdio: 'ignore',
    windowsHide: true,
  })
  return child
}

function stopServer(child) {
  if (!child || child.exitCode !== null) return
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' })
  } else {
    child.kill('SIGTERM')
  }
}

function runAdmin(args, env = {}) {
  const result = spawnSync(java, ['-jar', jar, ...args], {
    encoding: 'utf8',
    env: { ...process.env, ...env },
    windowsHide: true,
  })
  const lines = (result.stdout ?? '').split(/\r?\n/).filter((line) => line.trim().startsWith('{'))
  let payload = null
  if (lines.length > 0) {
    try {
      payload = JSON.parse(lines[lines.length - 1])
    } catch {
      payload = null
    }
  }
  return { status: result.status, payload, stdout: result.stdout ?? '', stderr: result.stderr ?? '' }
}

const json = { 'Content-Type': 'application/json' }
const rootA = join(workRoot, 'data-a')
const rootB = join(workRoot, 'data-b')
const rootC = join(workRoot, 'data-c')
const exportZip = join(workRoot, 'mammoth-export.zip')
const backupParent = join(workRoot, 'backups')
let serverA
let serverB
let serverC

try {
  mkdirSync(rootA, { recursive: true })
  serverA = startServer(rootA, portBase)
  const baseA = `http://127.0.0.1:${portBase}`
  await waitHealth(baseA)
  const apiA = client(baseA)
  await apiA.call('/api/auth/csrf')
  const owner = await apiA.call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: 'drill-owner', displayName: 'Drill Owner', password }),
  })
  await apiA.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'drill-owner', password }),
  })
  const reviewer = await apiA.call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: 'drill-reviewer', displayName: 'Drill Reviewer', password }),
  })
  const customer = await apiA.call('/api/customers', {
    method: 'POST', headers: json, body: JSON.stringify({ name: 'ADR8 Customer' }),
  })
  const project = await apiA.call('/api/projects', {
    method: 'POST', headers: json,
    body: JSON.stringify({ customerId: customer.id, code: 'ADR8-1', name: 'ADR8 drill project' }),
  })
  await apiA.call(`/api/projects/${project.id}/members`, {
    method: 'POST', headers: json, body: JSON.stringify({ userId: reviewer.id, role: 'reviewer' }),
  })
  const content = new TextEncoder().encode('导出导入演练资料：条码重复时冻结库存。')
  const form = new FormData()
  form.append('file', new Blob([content], { type: 'text/markdown' }), 'drill-notes.md')
  const artifact = await apiA.call(`/api/projects/${project.id}/artifacts`, { method: 'POST', body: form })
  const draft = await apiA.call(`/api/projects/${project.id}/knowledge`, {
    method: 'POST', headers: json,
    body: JSON.stringify({
      docType: 'process', title: 'ADR8 演练知识', body: '条码重复时冻结库存并人工复核。',
      sourceRefs: [{ artifactId: artifact.id, locator: '全篇', note: 'drill' }],
    }),
  })
  await apiA.call(`/api/projects/${project.id}/knowledge/${draft.id}/submit-review`, { method: 'POST' })
  await apiA.call('/api/auth/logout', { method: 'POST' })
  await apiA.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'drill-reviewer', password }),
  })
  await apiA.call(`/api/projects/${project.id}/knowledge/${draft.id}/verify`, { method: 'POST' })
  record('seed-source-root', true, `project ${project.code}, verified knowledge`)
  stopServer(serverA)
  serverA = undefined

  const exportResult = runAdmin([
    `--admin-export=${exportZip}`, `--mammoth.data-root=${rootA}`, '--actor-user-id=' + owner.id,
  ])
  record('admin-export', exportResult.status === 0 && existsSync(exportZip),
    exportResult.status === 0 ? `${exportResult.payload?.result?.rows} rows` : exportResult.stderr.slice(0, 200))

  mkdirSync(rootB, { recursive: true })
  const importResult = runAdmin([
    `--admin-import=${exportZip}`, '--admin-username=drill-owner',
    `--mammoth.data-root=${rootB}`,
  ], { MAMMOTH_IMPORT_ADMIN_PASSWORD: importedPassword })
  record('admin-import', importResult.status === 0,
    importResult.status === 0 ? `${importResult.payload?.result?.rows} rows` : importResult.stderr.slice(0, 200))

  serverB = startServer(rootB, portBase + 1)
  const baseB = `http://127.0.0.1:${portBase + 1}`
  await waitHealth(baseB)
  const apiB = client(baseB)
  await apiB.call('/api/auth/csrf')
  await apiB.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'drill-owner', password: importedPassword }),
  })
  const projectsB = await apiB.call('/api/projects')
  const projectB = projectsB.find((entry) => entry.code === 'ADR8-1')
  const knowledgeB = await apiB.call(`/api/projects/${projectB.id}/knowledge`)
  const verifiedB = knowledgeB.find((entry) => entry.title === 'ADR8 演练知识')
  const artifactsB = await apiB.call(`/api/projects/${projectB.id}/artifacts`)
  const downloadB = await fetch(`${baseB}/api/projects/${projectB.id}/artifacts/${artifactsB[0].id}/content`, {
    headers: { Cookie: apiB.jar.header() },
  })
  const bytesB = new Uint8Array(await downloadB.arrayBuffer())
  record('import-verified', verifiedB?.status === 'verified'
    && artifactsB.length === 1 && sha256(bytesB) === artifact.contentHash,
    `project/knowledge/artifact restored`)
  const auditB = await apiB.call('/api/audit/me?limit=50')
  record('import-audited', auditB.some((entry) => entry.action === 'admin.import'),
    `${auditB.length} audit rows`)
  stopServer(serverB)
  serverB = undefined

  const backupResult = runAdmin([
    `--admin-backup=${backupParent}`, `--mammoth.data-root=${rootB}`,
  ])
  const backupDir = backupResult.payload?.result?.directory
  record('admin-backup', backupResult.status === 0 && backupDir && existsSync(join(backupDir, 'db', 'mammoth.db')),
    backupResult.status === 0 ? backupDir : backupResult.stderr.slice(0, 200))

  const restoreResult = runAdmin([`--admin-restore=${backupDir}`, `--restore-to=${rootC}`])
  record('admin-restore', restoreResult.status === 0 && existsSync(join(rootC, 'db', 'mammoth.db')),
    restoreResult.status === 0 ? rootC : restoreResult.stderr.slice(0, 200))

  serverC = startServer(rootC, portBase + 2)
  const baseC = `http://127.0.0.1:${portBase + 2}`
  await waitHealth(baseC)
  const apiC = client(baseC)
  await apiC.call('/api/auth/csrf')
  await apiC.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'drill-owner', password: importedPassword }),
  })
  const projectsC = await apiC.call('/api/projects')
  const knowledgeC = await apiC.call(`/api/projects/${projectsC[0].id}/knowledge`)
  record('restored-service-usable',
    projectsC.some((entry) => entry.code === 'ADR8-1')
      && knowledgeC.some((entry) => entry.title === 'ADR8 演练知识' && entry.status === 'verified'),
    'projects and verified knowledge after restore')
  stopServer(serverC)
  serverC = undefined

  const rejected = runAdmin([
    `--admin-import=${exportZip}`, '--admin-username=drill-owner', `--mammoth.data-root=${rootC}`,
  ], { MAMMOTH_IMPORT_ADMIN_PASSWORD: importedPassword })
  record('import-refuses-non-empty-root',
    rejected.status !== 0 && `${rejected.stderr}${rejected.stdout}`.includes('not empty'),
    `exit=${rejected.status}`)
} catch (error) {
  record('unexpected-error', false, error.message)
} finally {
  stopServer(serverA)
  stopServer(serverB)
  stopServer(serverC)
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
