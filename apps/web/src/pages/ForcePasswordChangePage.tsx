import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSession } from '../app/session'
import { changePassword, ensureCsrf } from '../lib/api'

/**
 * Blocking gate for admin-reset accounts (ADR-0006 M4 addendum). The server only allows
 * /api/auth/me, /api/auth/password, /api/auth/logout and CSRF for such sessions; this page mirrors
 * that restriction in the UI so the user is not left with broken pages.
 */
export default function ForcePasswordChangePage() {
  const { state, setNotice, signOut } = useSession()
  const navigate = useNavigate()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
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
      await ensureCsrf()
      await changePassword(currentPassword, newPassword)
      setNotice('密码已修改，请使用新密码重新登录。')
      await signOut()
      navigate('/login', { replace: true })
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
      setBusy(false)
    }
  }

  async function cancel() {
    await signOut()
    navigate('/login', { replace: true })
  }

  const username = state.status === 'authenticated' ? state.user.username : ''

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>需要修改密码</h1>
        <p className="hint">
          管理员已重置 <span className="mono">{username}</span> 的口令。请使用临时口令登录后设置新密码；
          在修改完成前，其他功能不可用。
        </p>
        <form className="stack" onSubmit={(event) => void submit(event)}>
          <label>
            当前密码（临时口令）
            <input
              type="password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              autoComplete="current-password"
              autoFocus
            />
          </label>
          <label>
            新密码（至少 8 位）
            <input
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              autoComplete="new-password"
            />
          </label>
          <label>
            确认新密码
            <input
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              autoComplete="new-password"
            />
          </label>
          {error && <p className="state error">{error}</p>}
          <button type="submit" disabled={busy || currentPassword === '' || newPassword === ''}>
            {busy ? '提交中…' : '修改密码并重新登录'}
          </button>
          <button type="button" className="secondary" onClick={() => void cancel()}>
            退出登录
          </button>
        </form>
      </div>
    </div>
  )
}
