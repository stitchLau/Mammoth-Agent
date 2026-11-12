import { useEffect, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useSession } from '../app/session'
import { createFirstAdmin, login, setupStatus } from '../lib/api'

/** First-run wizard (UI-SPEC P02): only reachable while the organization has no users. */
export default function SetupPage() {
  const { signIn } = useSession()
  const navigate = useNavigate()
  const [initialized, setInitialized] = useState<boolean | null>(null)
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setupStatus()
      .then((status) => setInitialized(status.initialized))
      .catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
  }, [])

  if (initialized === true) {
    return <Navigate to="/login" replace />
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await createFirstAdmin(username.trim(), displayName.trim(), password)
      const user = await login(username.trim(), password)
      signIn(user)
      navigate('/projects', { replace: true })
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>首次设置</h1>
        <p className="hint">创建第一个管理员账号。该入口仅在系统没有任何用户时可用。</p>
        {initialized === null && <p className="state">正在检查系统状态…</p>}
        {initialized === false && (
          <form className="stack" onSubmit={(event) => void submit(event)}>
            <label>
              用户名
              <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus />
            </label>
            <label>
              显示名
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} placeholder="例如：管理员" />
            </label>
            <label>
              密码（至少 8 位）
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
              />
            </label>
            {error && <p className="state error">{error}</p>}
            <button
              type="submit"
              disabled={busy || username.trim() === '' || password.length < 8}
            >
              {busy ? '创建中…' : '创建并登录'}
            </button>
          </form>
        )}
        {error && initialized === null && <p className="state error">{error}</p>}
      </div>
    </div>
  )
}
