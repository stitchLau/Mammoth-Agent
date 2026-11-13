import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSession } from '../app/session'
import { ErrorBanner, PageHeader } from '../components/ui'
import {
  changePassword,
  createUser,
  disableUser,
  enableUser,
  listJobs,
  listUsers,
  notificationChannels,
  resetUserPassword,
  retryJob,
  setUserOrgAdmin,
  testNotificationChannel,
  updateProfile,
  type ChannelStatusView,
  type JobView,
  type UserSummary,
} from '../lib/api'

export default function SettingsPage() {
  const { state, signIn, signOut, setNotice } = useSession()
  const navigate = useNavigate()
  const currentUser = state.status === 'authenticated' ? state.user : null
  const [displayName, setDisplayName] = useState(currentUser?.displayName ?? '')
  const [email, setEmail] = useState(currentUser?.email ?? '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const [users, setUsers] = useState<UserSummary[]>([])
  const [userError, setUserError] = useState<string | null>(null)
  const [temporaryPassword, setTemporaryPassword] = useState<string | null>(null)
  const [channels, setChannels] = useState<Record<string, ChannelStatusView>>({})
  const [jobs, setJobs] = useState<JobView[]>([])
  const [adminMessage, setAdminMessage] = useState<string | null>(null)
  const [newUser, setNewUser] = useState({
    username: '',
    displayName: '',
    email: '',
    password: '',
    accountType: 'human',
  })

  const reloadUsers = useCallback(async () => {
    if (!currentUser?.orgAdmin) {
      setUsers([])
      setChannels({})
      setJobs([])
      return
    }
    const [userList, channelList, jobList] = await Promise.all([
      listUsers(),
      notificationChannels(),
      listJobs(),
    ])
    setUsers(userList)
    setChannels(channelList)
    setJobs(jobList)
  }, [currentUser?.orgAdmin])

  useEffect(() => {
    reloadUsers().catch((problem: unknown) =>
      setUserError(problem instanceof Error ? problem.message : String(problem)),
    )
  }, [reloadUsers])

  async function saveProfile(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const updated = await updateProfile(displayName.trim(), email.trim())
      signIn(updated)
      setMessage('显示名/邮箱已更新')
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  async function savePassword(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setMessage(null)
    if (newPassword.length < 8) {
      setError('新密码至少 8 位')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('两次输入的新密码不一致')
      return
    }
    setBusy(true)
    try {
      await changePassword(currentPassword, newPassword)
      setNotice('密码已修改，所有会话已注销，请重新登录。')
      await signOut()
      navigate('/login', { replace: true })
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
      setBusy(false)
    }
  }

  async function runUserAction(action: () => Promise<void>) {
    setBusy(true)
    setUserError(null)
    try {
      await action()
      await reloadUsers()
    } catch (problem: unknown) {
      setUserError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  function createAccount(event: React.FormEvent) {
    event.preventDefault()
    void runUserAction(async () => {
      await createUser(
        newUser.username.trim(),
        newUser.displayName.trim(),
        newUser.password,
        newUser.email.trim() === '' ? null : newUser.email.trim(),
        newUser.accountType,
      )
      setNewUser({ username: '', displayName: '', email: '', password: '', accountType: 'human' })
    })
  }

  return (
    <>
      <PageHeader title="账号与组织设置" subtitle="显示名/邮箱、密码与组织成员管理" />
      {error && <ErrorBanner message={error} />}
      {message && <p className="state notice">{message}</p>}

      <h2>显示名与邮箱</h2>
      <form className="knowledge-form" aria-label="修改资料" onSubmit={(event) => void saveProfile(event)}>
        <div className="row">
          <input
            aria-label="显示名"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder="显示名"
          />
          <input
            aria-label="邮箱（通知用）"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="邮箱（可选，用于通知）"
          />
          <button type="submit" disabled={busy || displayName.trim() === ''}>
            保存资料
          </button>
        </div>
        {currentUser && (
          <p className="hint">
            用户名：<span className="mono">{currentUser.username}</span> · 用户 ID：
            <span className="mono">{currentUser.id}</span>
            {currentUser.orgAdmin ? ' · 组织管理员' : ''}
          </p>
        )}
      </form>

      <h2>修改密码</h2>
      <form className="knowledge-form" aria-label="修改密码" onSubmit={(event) => void savePassword(event)}>
        <div className="row">
          <input
            aria-label="当前密码"
            type="password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
            autoComplete="current-password"
            placeholder="当前密码"
          />
          <input
            aria-label="新密码"
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            autoComplete="new-password"
            placeholder="新密码（至少 8 位）"
          />
          <input
            aria-label="确认新密码"
            type="password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            autoComplete="new-password"
            placeholder="确认新密码"
          />
          <button type="submit" disabled={busy || currentPassword === '' || newPassword === ''}>
            {busy ? '提交中…' : '修改密码'}
          </button>
        </div>
      </form>

      {currentUser?.orgAdmin && (
        <>
          <h2>组织成员管理</h2>
          {userError && <ErrorBanner message={userError} onRetry={() => void reloadUsers()} />}
          {temporaryPassword && (
            <p className="state notice">
              临时口令（仅显示一次，请转交用户并让其立即修改）：
              <span className="mono"> {temporaryPassword}</span>{' '}
              <button
                type="button"
                className="secondary"
                onClick={() => void navigator.clipboard?.writeText(temporaryPassword)}
              >
                复制
              </button>
            </p>
          )}

          <table className="table">
            <thead>
              <tr>
                <th>用户</th>
                <th>邮箱</th>
                <th>类型</th>
                <th>状态</th>
                <th>组织管理员</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => {
                const isSelf = user.id === currentUser.id
                return (
                  <tr key={user.id}>
                    <td>
                      {user.displayName}（<span className="mono">{user.username}</span>）
                    </td>
                    <td>{user.email ?? '—'}</td>
                    <td>{user.accountType === 'agent' ? 'Agent' : '人类'}</td>
                    <td>{user.status === 'active' ? '启用' : '停用'}</td>
                    <td>{user.orgAdmin ? '是' : '否'}</td>
                    <td className="actions">
                      <button
                        type="button"
                        className="secondary"
                        disabled={busy || isSelf}
                        onClick={() =>
                          void runUserAction(async () => {
                            await (user.status === 'active'
                              ? disableUser(user.id)
                              : enableUser(user.id))
                          })
                        }
                      >
                        {user.status === 'active' ? '停用' : '启用'}
                      </button>
                      {user.accountType !== 'agent' && (
                        <button
                          type="button"
                          className="secondary"
                          disabled={busy}
                          onClick={() => {
                            if (!window.confirm(`重置 ${user.displayName} 的口令？`)) return
                            void runUserAction(async () => {
                              const result = await resetUserPassword(user.id)
                              setTemporaryPassword(result.temporaryPassword)
                            })
                          }}
                        >
                          重置口令
                        </button>
                      )}
                      <button
                        type="button"
                        className="secondary"
                        disabled={busy || (isSelf && user.orgAdmin)}
                        onClick={() =>
                          void runUserAction(async () => {
                            await setUserOrgAdmin(user.id, !user.orgAdmin)
                          })
                        }
                      >
                        {user.orgAdmin ? '取消管理员' : '设为管理员'}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          <form className="knowledge-form" aria-label="创建用户" onSubmit={(event) => void createAccount(event)}>
            <h3>创建用户</h3>
            <div className="row">
              <input
                aria-label="新用户名"
                value={newUser.username}
                onChange={(event) => setNewUser({ ...newUser, username: event.target.value })}
                placeholder="用户名（至少 2 位）"
              />
              <input
                aria-label="新用户显示名"
                value={newUser.displayName}
                onChange={(event) => setNewUser({ ...newUser, displayName: event.target.value })}
                placeholder="显示名"
              />
              <input
                aria-label="新用户邮箱"
                type="email"
                value={newUser.email}
                onChange={(event) => setNewUser({ ...newUser, email: event.target.value })}
                placeholder="邮箱（可选）"
              />
              <input
                aria-label="新用户密码"
                type="password"
                value={newUser.password}
                onChange={(event) => setNewUser({ ...newUser, password: event.target.value })}
                placeholder="密码（至少 8 位）"
              />
              <select
                aria-label="新用户类型"
                value={newUser.accountType}
                onChange={(event) => setNewUser({ ...newUser, accountType: event.target.value })}
              >
                <option value="human">人类用户</option>
                <option value="agent">Agent 账号（不可登录）</option>
              </select>
              <button
                type="submit"
                disabled={
                  busy ||
                  newUser.username.trim().length < 2 ||
                  newUser.password.length < 8
                }
              >
                创建用户
              </button>
            </div>
          </form>
        </>
      )}
      {currentUser?.orgAdmin && (
        <section>
          <h2>通知渠道</h2>
          {adminMessage && <p className="state notice">{adminMessage}</p>}
          <p className="hint">邮件/企微默认关闭；启用需在服务端配置环境变量后重启。</p>
          <ul className="refs">
            {Object.entries(channels).map(([name, status]) => (
              <li key={name}>
                <span className="code">{name}</span>{' '}
                {status.enabled ? '已启用' : '未启用'}
                {name !== 'inApp' && (
                  <>
                    {' '}· {status.configured ? '已配置' : `缺少 ${status.missing ?? '配置'}`}
                    <button
                      type="button"
                      className="secondary"
                      disabled={busy || !status.enabled || !status.configured}
                      onClick={() =>
                        void runUserAction(async () => {
                          await testNotificationChannel(name)
                          setAdminMessage(`${name} 测试通知已发送`)
                        })
                      }
                    >
                      测试发送
                    </button>
                  </>
                )}
              </li>
            ))}
          </ul>

          <h2>后台任务</h2>
          {jobs.length === 0 ? (
            <p className="hint">没有后台任务。</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>类型</th>
                  <th>状态</th>
                  <th>尝试</th>
                  <th>错误</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.id}>
                    <td>{job.type}</td>
                    <td>{job.status}</td>
                    <td>{job.attempts}</td>
                    <td>{job.lastError ?? '—'}</td>
                    <td>
                      <button
                        type="button"
                        className="secondary"
                        disabled={busy || job.status === 'succeeded'}
                        onClick={() =>
                          void runUserAction(async () => {
                            await retryJob(job.id)
                          })
                        }
                      >
                        重试
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}
    </>
  )
}
