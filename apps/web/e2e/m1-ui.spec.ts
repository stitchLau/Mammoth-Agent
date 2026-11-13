import { expect, test } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

/**
 * Browser acceptance for the product flows.
 *
 * The first test covers M1.5 (setup -> project -> artifact -> knowledge draft -> submit ->
 * four-eyes block -> reviewer verify -> verified search with citations). It leaves the created
 * credentials/project in `shared` so the second test can continue with M2, because the backend is
 * started with an empty data root for the whole run (see playwright.config.ts).
 */
const shared: {
  adminUser?: string
  adminPassword?: string
  reviewerUser?: string
  reviewerPassword?: string
  projectId?: string
  workItemId?: string
} = {}

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
    shared.adminUser = adminName
    shared.adminPassword = password
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
    shared.projectId = projectId
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
    shared.adminPassword = newPassword
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
    shared.reviewerUser = reviewerName
    shared.reviewerPassword = password
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

/**
 * M2 acceptance (docs/specs/M2-PHASE2-PLAN.md AC-2/AC-3/AC-4/AC-5): a fuzzy requirement is collected
 * with evidence, gated by the readiness check, turned into one solution model rendered as two
 * views, then approved by a second person while the approved version stays immutable.
 */
test('M2 requirement to approved solution', async ({ page }) => {
  test.skip(!shared.adminUser, 'requires the M1 flow first (shared backend data root)')
  const adminUser = shared.adminUser ?? ''
  const adminPassword = shared.adminPassword ?? ''
  const reviewerUser = shared.reviewerUser ?? ''
  const reviewerPassword = shared.reviewerPassword ?? ''
  const projectId = shared.projectId ?? ''
  const requirementTitle = 'E2E 收货扫码重复入库'
  const solutionTitle = 'E2E 收货扫码处理方案'

  await test.step('登录并创建需求', async () => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill(adminUser)
    await page.getByLabel('密码').fill(adminPassword)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/\/projects$/)

    await page.goto(`/projects/${projectId}/requirements`)
    await page.getByPlaceholder('需求标题').fill(requirementTitle)
    await page
      .getByPlaceholder('客户/业务方的原始描述（可以很模糊）')
      .fill('客户口头描述：同一托盘重复扫码会重复入库，要求当天闭环。')
    await page.getByRole('button', { name: '创建需求' }).click()
    await page.getByRole('link', { name: requirementTitle }).click()
    await expect(page).toHaveURL(/\/requirements\/[0-9a-f-]+$/)
    await expect(page.getByText('REQ-0001')).toBeVisible()
  })

  await test.step('追加证据并完成 11 维度就绪检查', async () => {
    await page.getByLabel('证据内容').fill('现场反馈：重复扫码两次都会入库，需要冻结库存再人工复核。')
    await page.getByRole('button', { name: '追加证据' }).click()
    await expect(page.getByText('批次 1')).toBeVisible()

    const dimensions: [string, string][] = [
      ['背景', 'background'],
      ['目标', 'goals'],
      ['当前流程', 'current_process'],
      ['目标流程', 'target_process'],
      ['业务规则', 'business_rules'],
      ['角色与范围', 'roles_and_scope'],
      ['异常场景', 'exceptions'],
      ['数据与口径', 'data_and_metrics'],
      ['外部集成', 'integrations'],
      ['验收方式', 'acceptance'],
      ['实施约束', 'constraints'],
    ]
    for (const [label, key] of dimensions) {
      const row = page.getByRole('row').filter({ has: page.getByLabel(`${label} 状态`) })
      const saved = page.waitForResponse(
        (response) =>
          response.url().includes(`/assessments/${key}`) && response.request().method() === 'PUT',
      )
      await row.getByLabel(`${label} 状态`).selectOption('confirmed')
      await row.getByRole('button', { name: '保存' }).click()
      expect((await saved).ok()).toBeTruthy()
    }
    await expect(page.getByText('关键维度已就绪，可提交负责人确认')).toBeVisible()
  })

  await test.step('确认就绪并新建方案', async () => {
    await page.getByRole('button', { name: '确认就绪并进入分析' }).click()
    await expect(page.getByText('待分析').first()).toBeVisible()

    await page.getByLabel('方案标题').fill(solutionTitle)
    await page.getByRole('button', { name: '新建草稿' }).click()
    await expect(page).toHaveURL(/\/solutions\/[0-9a-f-]+$/)
    await expect(page.getByText('一致性检查')).toBeVisible()
  })

  await test.step('构建单一方案模型并查看双视图', async () => {
    await page.getByLabel('设计项标题').fill('收货扫码流程')
    await page.getByLabel('设计项正文').fill('重复扫码时冻结库存并转人工复核。')
    await page.getByRole('button', { name: '添加设计项' }).click()
    await expect(page.getByRole('cell', { name: '收货扫码流程' })).toBeVisible()

    await page.getByLabel('设计项类型').selectOption('technical')
    await page.getByLabel('上级设计项').selectOption({ label: '收货扫码流程' })
    await page.getByLabel('设计项标题').fill('扫码服务改造')
    await page.getByLabel('设计项正文').fill('扫码接口增加幂等校验。')
    await page.getByRole('button', { name: '添加设计项' }).click()
    await expect(page.getByRole('cell', { name: '扫码服务改造' })).toBeVisible()

    await page.getByLabel('验收标准对应设计项').selectOption({ label: '收货扫码流程' })
    await page.getByLabel('验收标准文本').fill('重复扫码被拦截且当天闭环')
    await page.getByRole('button', { name: '添加验收标准' }).click()
    await expect(page.getByText('[收货扫码流程] 重复扫码被拦截且当天闭环').first()).toBeVisible()

    await page.getByLabel('任务设计项').selectOption({ label: '扫码服务改造' })
    await page.getByLabel('任务验收标准').selectOption({ label: '重复扫码被拦截且当天闭环' })
    await page.getByLabel('任务标题').fill('实现扫码重复校验')
    await page.getByLabel('任务完成条件').fill('重复扫码返回可读提示')
    await page.getByRole('button', { name: '添加任务' }).click()
    await expect(page.getByRole('cell', { name: '实现扫码重复校验' })).toBeVisible()

    await expect(page.getByText('结构完整，可提交评审')).toBeVisible()
    await expect(page.getByLabel('业务方案视图')).toContainText('收货扫码流程')
    await expect(page.getByLabel('技术方案视图')).toContainText('扫码服务改造')
    await expect(page.getByLabel('技术方案视图')).toContainText('开发任务')
  })

  let solutionUrl = ''
  await test.step('提交评审且作者不可自批', async () => {
    await page.getByRole('button', { name: '提交评审' }).click()
    await expect(page.getByText('评审中').first()).toBeVisible()
    await expect(page.getByText(/方案作者不能批准自己的方案/)).toBeVisible()
    solutionUrl = page.url()
    shared.workItemId = solutionUrl.split('/requirements/')[1]?.split('/')[0] ?? ''
  })

  await test.step('reviewer 批准方案并保留批准版本', async () => {
    await page.getByRole('button', { name: '退出' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await page.getByLabel('用户名').fill(reviewerUser)
    await page.getByLabel('密码').fill(reviewerPassword)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/\/projects$/)

    await page.goto(solutionUrl)
    await page.getByLabel('评审意见').fill('同意，纳入本期')
    await page.getByRole('button', { name: '批准方案' }).click()
    await expect(page.getByText('已批准').first()).toBeVisible()

    await page.goto(solutionUrl.split('/solutions/')[0])
    await expect(page.getByText('已批准').first()).toBeVisible()
    await expect(page.getByText('当前批准版本')).toBeVisible()
    await expect(page.getByText(`v1`).first()).toBeVisible()
  })
})

/**
 * M3 acceptance (ADR-0007/0008, M3 plan S2/S3/S4/S6): register a local repository, capture an
 * immutable baseline, generate the handoff package, recover an implementation commit into the
 * acceptance matrix, and approve an As-built snapshot with a second person.
 */
test('M3 baseline, handoff, execution and delivery approval', async ({ page }) => {
  test.skip(!shared.workItemId, 'requires the M2 flow first (shared backend data root)')
  const adminUser = shared.adminUser ?? ''
  const adminPassword = shared.adminPassword ?? ''
  const reviewerUser = shared.reviewerUser ?? ''
  const reviewerPassword = shared.reviewerPassword ?? ''
  const projectId = shared.projectId ?? ''
  const workItemId = shared.workItemId ?? ''
  const repoDir = mkdtempSync(join(tmpdir(), 'mammoth-e2e-repo-'))
  const repo = join(repoDir, 'repo')
  const git = (...args: string[]): string =>
    execFileSync('git', args, { cwd: repo, encoding: 'utf8' }).trim()

  try {
    mkdirSync(join(repo, 'src'), { recursive: true })
    git('init', '-b', 'main')
    git('config', 'user.email', 'e2e@example.com')
    git('config', 'user.name', 'E2E')
    writeFileSync(join(repo, 'src', 'App.java'), 'class App {}\n')
    git('add', '-A')
    git('commit', '-m', 'initial')

    await test.step('注册仓库并捕获基线', async () => {
      await page.goto('/login')
      await page.getByLabel('用户名').fill(adminUser)
      await page.getByLabel('密码').fill(adminPassword)
      await page.getByRole('button', { name: '登录' }).click()
      await expect(page).toHaveURL(/\/projects$/)

      await page.goto(`/projects/${projectId}/baselines`)
      await page.getByLabel('仓库标识').fill('backend')
      await page.getByLabel('仓库路径').fill(repo)
      await page.getByRole('button', { name: '注册' }).click()
      await expect(page.getByRole('cell', { name: 'backend' })).toBeVisible()

      await page.getByLabel('基线说明').fill('E2E 实施前基线')
      const captured = page.waitForResponse(
        (response) => response.url().includes('/baselines') && response.request().method() === 'POST',
      )
      await page.getByRole('button', { name: '捕获基线' }).click()
      expect((await captured).ok()).toBeTruthy()
      await expect(page.getByText('E2E 实施前基线')).toBeVisible()
    })

    await test.step('生成交接包并回收实施 commit', async () => {
      await page.goto(`/projects/${projectId}/requirements/${workItemId}/delivery`)
      await page.getByRole('button', { name: '生成开发交接包' }).click()
      await expect(page.getByText('开发交接包（Markdown）')).toBeVisible()
      await expect(page.getByText(/只读约束/)).toBeVisible()

      writeFileSync(join(repo, 'src', 'App.java'), 'class App { int duplicateCheck; }\n')
      writeFileSync(join(repo, 'src', 'ScanValidator.java'), 'class ScanValidator {}\n')
      git('add', '-A')
      git('commit', '-m', 'implementation')
      const toCommit = git('rev-parse', 'HEAD')

      await page.getByLabel('目标 commit').fill(toCommit)
      await page.getByLabel('实施摘要').fill('E2E 实施完成')
      await page.getByRole('checkbox', { name: '实现扫码重复校验' }).check()
      await page.getByRole('button', { name: '记录实施' }).click()

      await expect(page.getByText(toCommit.slice(0, 8)).first()).toBeVisible()
      await expect(page.getByText('已覆盖 1')).toBeVisible()
    })

    await test.step('生成并四眼批准交付快照', async () => {
      await page.getByLabel('快照标题').fill('E2E As-built')
      await page.getByRole('button', { name: '生成快照草稿' }).click()
      await expect(page.getByText('E2E As-built')).toBeVisible()

      await page.getByRole('button', { name: '退出' }).click()
      await expect(page).toHaveURL(/\/login$/)
      await page.getByLabel('用户名').fill(reviewerUser)
      await page.getByLabel('密码').fill(reviewerPassword)
      await page.getByRole('button', { name: '登录' }).click()
      await expect(page).toHaveURL(/\/projects$/)

      await page.goto(`/projects/${projectId}/requirements/${workItemId}/delivery`)
      await page.getByRole('button', { name: '批准快照' }).click()
      await expect(page.getByText('已批准').first()).toBeVisible()
    })
  } finally {
    rmSync(repoDir, { recursive: true, force: true })
  }
})
