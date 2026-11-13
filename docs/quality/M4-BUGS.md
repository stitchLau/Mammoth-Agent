# M4 缺陷台账

- **日期**：2026-11-13
- **范围**：M4 S1（组织/用户生命周期与权限收紧）
- **说明**：记录实现/验证中发现的问题、根因、修复与回归；S2–S6 继续追加。

## B-M4-001 前端清空邮箱被后端当成“保持不变”

| 项 | 内容 |
|---|---|
| 现象 | 设置页把空邮箱转成 `null` 提交；后端约定 `null`=保持不变，导致用户无法清空邮箱 |
| 根因 | 前端把“空字符串”与“未提供”混为一谈；API 语义未在调用处对齐 |
| 修复 | 设置页始终提交 `email.trim()`（空串=清空）；后端 `updateProfile` 对空串归一为 `null` 并清空；`AccountSettingsTest.emailCanBeSetClearedAndValidated` 覆盖 |
| 回归 | 后端 103 tests、前端 build + 25 tests、E2E 4 passed |
| 状态 | 已关闭 |

## B-M4-002 E2E 文本定位歧义

| 项 | 内容 |
|---|---|
| 现象 | M4 E2E 中 `getByText('组织成员管理')` 同时命中页面副标题与 `<h2>`；`getByText(username)` 同时命中用户名与邮箱 |
| 根因 | Playwright strict mode + 新页面文本重复 |
| 修复 | 改用 `getByRole('heading', { name: '组织成员管理' })` 与 `getByText(username, { exact: true })` |
| 回归 | E2E 4 passed（15.1s） |
| 状态 | 已关闭 |

## 无产品缺陷说明

S1 未发现权限绕过、会话失效或迁移破坏；负向测试覆盖非 admin 建用户/停用/重置/主数据、停用撤销会话、Agent 禁登、最后 org_admin 保护、临时口令会话白名单。
