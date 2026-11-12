/**
 * Capture UI screenshots for docs/evidence/screenshots (synthetic data only).
 *
 * Requires a running backend with a FRESH data root and built SPA, e.g.:
 *   java -jar services/domain/target/mammoth-domain-service.jar --server.port=18132 \
 *     --mammoth.data-root=%TEMP%\mammoth-shots
 *   cd apps/web && node e2e/capture-screenshots.mjs
 */
import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'

const base = process.env.MAMMOTH_E2E_BASE ?? 'http://127.0.0.1:18132'
const outDir = new URL('../../../docs/evidence/screenshots/', import.meta.url)
const stamp = Date.now().toString().slice(-6)
const password = 'Passw0rd!2026'
const adminName = `shots-admin-${stamp}`
const reviewerName = `shots-reviewer-${stamp}`
const projectCode = `SHOT-${stamp}`
const knowledgeTitle = `收货流程异常处理-${stamp}`

await mkdir(outDir, { recursive: true })
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1280, height: 860 }, locale: 'zh-CN' })

const shot = async (name) => {
  await page.screenshot({ path: new URL(name, outDir).pathname.replace(/^\/([A-Za-z]:)/, '$1'), fullPage: true })
  console.log('captured', name)
}

try {
  await page.goto(`${base}/setup`)
  await page.getByLabel('用户名').waitFor()
  await shot('01-setup.png')

  await page.getByLabel('用户名').fill(adminName)
  await page.getByLabel('显示名').fill('截图管理员')
  await page.getByLabel(/密码/).fill(password)
  await page.getByRole('button', { name: '创建并登录' }).click()
  await page.getByRole('link', { name: 'Mammoth Agent' }).waitFor()

  await page.getByPlaceholder('客户名称').fill(`截图客户 ${stamp}`)
  await page.getByRole('button', { name: '创建客户' }).click()
  await page.getByPlaceholder(/项目编码/).fill(projectCode)
  await page.getByPlaceholder('项目名称').fill('截图项目')
  await page.getByRole('button', { name: '创建项目' }).click()
  await page.getByRole('link', { name: new RegExp(projectCode) }).waitFor()
  await shot('02-projects.png')

  await page.getByRole('link', { name: new RegExp(projectCode) }).click()
  await page.waitForURL(/\/artifacts$/)
  const uploadPromise = page.waitForResponse(
    (response) => response.url().includes('/artifacts') && response.request().method() === 'POST',
  )
  await page.setInputFiles('input[type="file"]', {
    name: '仓库作业纪要.md',
    mimeType: 'text/markdown',
    buffer: Buffer.from('收货流程：条码重复时冻结库存并人工复核，异常当天闭环。'),
  })
  await uploadPromise
  await page.getByText('仓库作业纪要.md').waitFor()
  await shot('03-artifacts.png')

  await page.getByRole('link', { name: '知识文档' }).click()
  await page.getByPlaceholder('知识标题').fill(knowledgeTitle)
  await page.getByPlaceholder('知识正文').fill('收货流程中条码重复时，先冻结库存再人工复核，异常需当天闭环。')
  await page.getByRole('button', { name: '创建草稿' }).click()
  await page.getByRole('button', { name: '提交审核' }).first().click()
  await page.getByRole('link', { name: knowledgeTitle }).click()
  await page.waitForURL(/\/knowledge\/[0-9a-f-]+$/)
  await page.getByText(/不能自审/).waitFor()
  await shot('04-knowledge-self-review-blocked.png')

  const projectId = page.url().match(/\/projects\/([0-9a-f-]+)\//)[1]
  const documentId = page.url().split('/').pop()
  const csrf = await (await page.request.get(`${base}/api/auth/csrf`)).json()
  const headers = { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf.token }
  const reviewerResponse = await page.request.post(`${base}/api/users`, {
    headers,
    data: { username: reviewerName, displayName: '截图审核人', password },
  })
  const reviewer = await reviewerResponse.json()
  await page.request.post(`${base}/api/projects/${projectId}/members`, {
    headers,
    data: { userId: reviewer.id, role: 'reviewer' },
  })

  await page.getByRole('button', { name: '退出' }).click()
  await page.getByLabel('用户名').waitFor()
  await page.getByLabel('用户名').fill(reviewerName)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(/\/projects$/)
  await page.goto(`${base}/projects/${projectId}/knowledge/${documentId}`)
  await page.getByRole('button', { name: '审核通过' }).click()
  await page.getByText('已验证').first().waitFor()

  await page.getByRole('link', { name: '知识检索' }).click()
  await page.getByLabel('检索问题').fill('条码重复怎么处理')
  await page.getByRole('button', { name: '检索' }).click()
  await page.getByText(knowledgeTitle).first().waitFor()
  await page.getByText(/引用 \d+ 项/).waitFor()
  await shot('05-search-citations.png')

  await page.getByRole('button', { name: '退出' }).click()
  await page.getByLabel('用户名').waitFor()
  await shot('00-login.png')
} finally {
  await browser.close()
}
