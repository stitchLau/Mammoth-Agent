import { expect, test } from '@playwright/test'

/**
 * M1.5 browser acceptance (UI-SPEC §7/§12): setup -> project -> artifact -> knowledge draft ->
 * submit -> four-eyes block -> reviewer verify -> verified search with citations.
 * Requires a running backend with an empty data root (see playwright.config.ts).
 */
test('M1 UI end-to-end', async ({ page }) => {
  const stamp = Date.now().toString().slice(-6)
  const adminName = `admin-${stamp}`
  const reviewerName = `reviewer-${stamp}`
  const password = 'Passw0rd!2026'
  const projectCode = `E2E-${stamp}`
  const knowledgeTitle = `收货流程异常处理-${stamp}`

  await test.step('首次设置创建管理员并登录', async () => {
    await page.goto('/setup')
    await page.getByLabel('用户名').fill(adminName)
    await page.getByLabel('显示名').fill('E2E Admin')
    await page.getByLabel(/密码/).fill(password)
    await page.getByRole('button', { name: '创建并登录' }).click()
    await expect(page).toHaveURL(/\/projects$/)
  })

  await test.step('创建客户与项目', async () => {
    await page.getByPlaceholder('客户名称').fill(`E2E 客户 ${stamp}`)
    await page.getByRole('button', { name: '创建客户' }).click()
    await expect(page.getByText('1 个客户')).toBeVisible()

    await page.getByPlaceholder(/项目编码/).fill(projectCode)
    await page.getByPlaceholder('项目名称').fill('E2E 项目')
    await page.getByRole('button', { name: '创建项目' }).click()
    await page.getByRole('link', { name: new RegExp(projectCode) }).click()
    await expect(page).toHaveURL(/\/projects\/[0-9a-f-]+\/artifacts$/)
  })

  await test.step('上传资料', async () => {
    const uploadResponsePromise = page.waitForResponse(
      (response) => response.url().includes('/artifacts') && response.request().method() === 'POST',
      { timeout: 15_000 },
    )
    await page.setInputFiles('input[type="file"]', {
      name: 'notes.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from('收货流程：条码重复时冻结库存并人工复核。'),
    })
    const uploadResponse = await uploadResponsePromise
    expect(uploadResponse.status()).toBe(200)
    await expect(page.getByText('notes.md')).toBeVisible()
  })

  let projectId = ''
  let documentId = ''
  await test.step('创建知识草稿并提交审核（创建者看到不可自审）', async () => {
    projectId = page.url().match(/\/projects\/([0-9a-f-]+)\//)![1]
    await page.getByRole('link', { name: '知识文档' }).click()
    await page.getByPlaceholder('知识标题').fill(knowledgeTitle)
    await page.getByPlaceholder('知识正文').fill('收货流程中条码重复时，先冻结库存再人工复核，异常需当天闭环。')
    await page.getByRole('button', { name: '创建草稿' }).click()

    await page.getByRole('button', { name: '提交审核' }).first().click()
    await expect(page.getByText('待审核').first()).toBeVisible()

    await page.getByRole('link', { name: knowledgeTitle }).click()
    await expect(page).toHaveURL(/\/knowledge\/[0-9a-f-]+$/)
    documentId = page.url().split('/').pop() ?? ''
    await expect(page.getByRole('button', { name: '审核通过' })).toBeDisabled()
    await expect(page.getByText(/不能自审/)).toBeVisible()
  })

  await test.step('账号设置：修改显示名并轮换密码', async () => {
    const newPassword = 'NewPassw0rd!2026'
    await page.goto('/settings')
    await page.getByLabel('显示名', { exact: true }).fill('E2E Admin Renamed')
    await page.getByRole('button', { name: '保存显示名' }).click()
    await expect(page.getByText('显示名已更新')).toBeVisible()
    await expect(page.getByRole('link', { name: 'E2E Admin Renamed' })).toBeVisible()

    await page.getByLabel('当前密码').fill(password)
    await page.getByLabel('新密码', { exact: true }).fill(newPassword)
    await page.getByLabel('确认新密码').fill(newPassword)
    await page.getByRole('button', { name: '修改密码' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByText(/密码已修改/)).toBeVisible()

    await page.getByLabel('用户名').fill(adminName)
    await page.getByLabel('密码').fill(newPassword)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/\/projects$/)
  })

  await test.step('添加 reviewer 成员', async () => {
    const csrfResponse = await page.request.get('/api/auth/csrf')
    const { token } = (await csrfResponse.json()) as { token: string }
    const headers = { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token }
    const userResponse = await page.request.post('/api/users', {
      headers,
      data: { username: reviewerName, displayName: 'E2E Reviewer', password },
    })
    expect(userResponse.ok()).toBeTruthy()
    const reviewer = (await userResponse.json()) as { id: string }
    const memberResponse = await page.request.post(`/api/projects/${projectId}/members`, {
      headers,
      data: { userId: reviewer.id, role: 'reviewer' },
    })
    expect(memberResponse.ok()).toBeTruthy()
  })

  await test.step('切换 reviewer 审核通过', async () => {
    await page.getByRole('button', { name: '退出' }).click()
    await expect(page).toHaveURL(/\/login$/)

    await page.getByLabel('用户名').fill(reviewerName)
    await page.getByLabel('密码').fill(password)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/\/projects$/)

    await page.goto(`/projects/${projectId}/knowledge/${documentId}`)
    await page.getByRole('button', { name: '审核通过' }).click()
    await expect(page.getByText('已验证').first()).toBeVisible()
  })

  await test.step('检索并显示引用', async () => {
    await page.getByRole('link', { name: '知识检索' }).click()
    await page.getByLabel('检索问题').fill('条码重复怎么处理')
    await page.getByRole('button', { name: '检索' }).click()
    await expect(page.getByText(knowledgeTitle).first()).toBeVisible()
    await expect(page.getByText(/引用 \d+ 项/)).toBeVisible()
  })
})
