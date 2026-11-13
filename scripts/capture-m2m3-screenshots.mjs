// Capture M2/M3 UI evidence screenshots against a disposable backend + desensitized data.
// Run: node scripts/capture-m2m3-screenshots.mjs
import { spawn, spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { createRequire } from 'node:module'
import { join, resolve } from 'node:path'

const repoRoot = resolve(new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'))
const java = process.env.MAMMOTH_JAVA ?? 'java'
const jar = join(repoRoot, 'services', 'domain', 'target', 'mammoth-domain-service.jar')
const workRoot = mkdtempSync(join(tmpdir(), 'mammoth-m2m3-shots-'))
const dataRoot = join(workRoot, 'data')
const repo = join(workRoot, 'repo')
const shots = join(repoRoot, 'docs', 'evidence', 'screenshots')
const requireWeb = createRequire(join(repoRoot, 'apps', 'web', 'package.json'))
const { chromium } = requireWeb('@playwright/test')
const port = 18173
const base = `http://127.0.0.1:${port}`
const password = 'Passw0rd!2026'
const json = { 'Content-Type': 'application/json' }

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', ...options })
  if (result.status !== 0) throw new Error(`${command} failed: ${result.stderr ?? ''}`)
  return result.stdout ?? ''
}

function client() {
  const cookies = new Map()
  async function call(path, options = {}) {
    const headers = { ...(options.headers ?? {}) }
    const cookie = [...cookies.entries()].map(([key, value]) => `${key}=${value}`).join('; ')
    if (cookie) headers.Cookie = cookie
    if (cookies.get('XSRF-TOKEN') && !['GET', 'HEAD', 'OPTIONS'].includes((options.method ?? 'GET').toUpperCase())) {
      headers['X-XSRF-TOKEN'] = cookies.get('XSRF-TOKEN')
    }
    const response = await fetch(base + path, { ...options, headers })
    for (const entry of response.headers.getSetCookie?.() ?? []) {
      const pair = entry.split(';')[0]
      const index = pair.indexOf('=')
      if (index > 0) cookies.set(pair.slice(0, index), pair.slice(index + 1))
    }
    const text = await response.text()
    if (!response.ok) throw new Error(`${response.status} ${text.slice(0, 200)}`)
    return text ? JSON.parse(text) : null
  }
  return { call }
}

async function waitForHealth() {
  for (let index = 0; index < 150; index++) {
    try {
      const response = await fetch(base + '/api/health')
      if (response.ok) return
    } catch {
      // not up yet
    }
    await new Promise((delay) => setTimeout(delay, 300))
  }
  throw new Error('backend did not become healthy')
}

let server
let browser
try {
  mkdirSync(join(repo, 'src', 'main', 'java'), { recursive: true })
  run('git', ['init', '-b', 'main'], { cwd: repo })
  run('git', ['config', 'user.email', 'shots@example.com'], { cwd: repo })
  run('git', ['config', 'user.name', 'Screenshot Bot'], { cwd: repo })
  writeFileSync(join(repo, 'src', 'main', 'java', 'ScanService.java'), 'class ScanService {}\n')
  writeFileSync(join(repo, 'README.md'), '# Desensitized project\n')
  run('git', ['add', '-A'], { cwd: repo })
  run('git', ['commit', '-m', 'initial'], { cwd: repo })
  const baselineCommit = run('git', ['rev-parse', 'HEAD'], { cwd: repo }).trim()

  mkdirSync(dataRoot, { recursive: true })
  server = spawn(java, ['-jar', jar, `--server.port=${port}`, `--mammoth.data-root=${dataRoot}`], {
    stdio: 'ignore', windowsHide: true,
  })
  await waitForHealth()

  const api = client()
  await api.call('/api/auth/csrf')
  const owner = await api.call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: 'shot-owner', displayName: '截图负责人', password }),
  })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'shot-owner', password }),
  })
  const reviewer = await api.call('/api/users', {
    method: 'POST', headers: json,
    body: JSON.stringify({ username: 'shot-reviewer', displayName: '截图评审人', password }),
  })
  const customer = await api.call('/api/customers', {
    method: 'POST', headers: json, body: JSON.stringify({ name: 'ACME（脱敏）' }),
  })
  const project = await api.call('/api/projects', {
    method: 'POST', headers: json,
    body: JSON.stringify({ customerId: customer.id, code: 'SHOT-MES', name: '脱敏 MES 演示项目' }),
  })
  await api.call(`/api/projects/${project.id}/members`, {
    method: 'POST', headers: json, body: JSON.stringify({ userId: reviewer.id, role: 'reviewer' }),
  })
  const content = new TextEncoder().encode('脱敏资料：收货扫码重复需冻结库存并人工复核。')
  const form = new FormData()
  form.append('file', new Blob([content], { type: 'text/markdown' }), 'notes.md')
  const artifact = await api.call(`/api/projects/${project.id}/artifacts`, { method: 'POST', body: form })

  // Two verified documents so the knowledge detail page can show the supersede/deprecate panel.
  const oldKnowledge = await api.call(`/api/projects/${project.id}/knowledge`, {
    method: 'POST', headers: json,
    body: JSON.stringify({
      docType: 'process', title: '收货扫码旧知识', body: '旧做法：收货扫码重复由人工核对。',
      sourceRefs: [{ artifactId: artifact.id, locator: '§1', note: null }],
    }),
  })
  const newKnowledge = await api.call(`/api/projects/${project.id}/knowledge`, {
    method: 'POST', headers: json,
    body: JSON.stringify({
      docType: 'process', title: '收货扫码新知识', body: '新做法：扫码重复冻结库存并人工复核。',
      sourceRefs: [{ artifactId: artifact.id, locator: '§2', note: null }],
    }),
  })
  for (const document of [oldKnowledge, newKnowledge]) {
    await api.call(`/api/projects/${project.id}/knowledge/${document.id}/submit-review`, { method: 'POST' })
  }
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'shot-reviewer', password }),
  })
  for (const document of [oldKnowledge, newKnowledge]) {
    await api.call(`/api/projects/${project.id}/knowledge/${document.id}/verify`, { method: 'POST' })
  }
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'shot-owner', password }),
  })

  const requirement = await api.call(`/api/projects/${project.id}/work-items`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ title: '收货扫码重复入库', summary: '现场口头描述，需要当天闭环', priority: 'high', sourceNote: '脱敏会议' }),
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
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'shot-reviewer', password }),
  })
  await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/solutions/${version.id}/approve`, {
    method: 'POST', headers: json, body: JSON.stringify({ comment: '同意' }),
  })
  await api.call('/api/auth/logout', { method: 'POST' })
  await api.call('/api/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ username: 'shot-owner', password }),
  })

  const repository = await api.call(`/api/projects/${project.id}/repositories`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ label: 'shot-mes', path: repo, defaultBranch: 'main' }),
  })
  const baseline = await api.call(`/api/projects/${project.id}/baselines`, {
    method: 'POST', headers: json, body: JSON.stringify({ note: 'approved design baseline', allowDirty: false }),
  })
  writeFileSync(join(repo, 'src', 'main', 'java', 'ScanService.java'),
    'class ScanService { boolean idempotent(String barcode) { return true; } }\n')
  run('git', ['add', '-A'], { cwd: repo })
  run('git', ['commit', '-m', 'feat: idempotent scan'], { cwd: repo })
  const toCommit = run('git', ['rev-parse', 'HEAD'], { cwd: repo }).trim()
  const execution = await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/executions`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ repositoryId: repository.id, baselineId: baseline.id, toCommit, summary: '实现扫码幂等校验', testsNote: 'mvn verify 通过', completedTaskIds: [task.id] }),
  })
  await api.call(`/api/projects/${project.id}/work-items/${requirement.id}/executions/${execution.id}/deviations`, {
    method: 'POST', headers: json,
    body: JSON.stringify({ deviationType: 'extra', targetType: null, targetId: null, description: '额外增加扫码日志' }),
  })

  browser = await chromium.launch()
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 }, locale: 'zh-CN' })
  const page = await context.newPage()
  await page.goto(base + '/login')
  await page.getByLabel('用户名').fill('shot-owner')
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL('**/projects')

  async function shot(name, path, waitFor) {
    await page.goto(base + path)
    await page.waitForLoadState('domcontentloaded')
    if (waitFor) {
      await page.getByText(waitFor, { exact: false }).first().waitFor({ state: 'visible', timeout: 15_000 })
    }
    await page.waitForTimeout(700)
    await page.screenshot({ path: join(shots, name), fullPage: true })
  }

  await shot('m2-requirements-list.png', `/projects/${project.id}/requirements`, '收货扫码')
  await shot('m2-requirement-readiness.png', `/projects/${project.id}/requirements/${requirement.id}`, '就绪')
  await shot('m2-solution-dual-view.png',
    `/projects/${project.id}/requirements/${requirement.id}/solutions/${version.id}`, '双视图（同一模型渲染）')
  await shot('m3-baselines.png', `/projects/${project.id}/baselines`, '代码基线')
  await shot('m3-delivery-closure.png', `/projects/${project.id}/requirements/${requirement.id}/delivery`, '验收矩阵')
  await shot('m3-knowledge-supersede.png', `/projects/${project.id}/knowledge/${oldKnowledge.id}`, '知识状态')
  console.log(JSON.stringify({
    screenshots: ['m2-requirements-list.png', 'm2-requirement-readiness.png', 'm2-solution-dual-view.png',
      'm3-baselines.png', 'm3-delivery-closure.png', 'm3-knowledge-supersede.png'],
    project: project.code,
  }))
} finally {
  if (browser) await browser.close()
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
