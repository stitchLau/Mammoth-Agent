import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useSession } from '../app/session'
import { login } from '../lib/api'

export default function LoginPage() {
  const { state, signIn } = useSession()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (state.status === 'authenticated') {
    return <Navigate to="/projects" replace />
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
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
        <h1>Mammoth Agent</h1>
        <p className="hint">项目知识底座 · 登录</p>
        <form className="stack" onSubmit={(event) => void submit(event)}>
          <label>
            用户名
            <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus />
          </label>
          <label>
            密码
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </label>
          {error && <p className="state error">{error}</p>}
          <button type="submit" disabled={busy || username.trim() === '' || password === ''}>
            {busy ? '登录中…' : '登录'}
          </button>
        </form>
        <p className="hint">
          首次使用请先 <Link to="/setup">创建管理员账号</Link>。
        </p>
      </div>
    </div>
  )
}
