import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSession } from '../app/session'
import { ErrorBanner, PageHeader } from '../components/ui'
import { changePassword, updateProfile } from '../lib/api'

export default function SettingsPage() {
  const { state, signIn, signOut, setNotice } = useSession()
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState(
    state.status === 'authenticated' ? state.user.displayName : '',
  )
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  async function saveProfile(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const updated = await updateProfile(displayName.trim())
      signIn(updated)
      setMessage('显示名已更新')
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

  return (
    <>
      <PageHeader title="账号设置" subtitle="显示名与密码；修改密码会注销所有会话" />
      {error && <ErrorBanner message={error} />}
      {message && <p className="state notice">{message}</p>}

      <h2>显示名</h2>
      <form className="knowledge-form" aria-label="修改显示名" onSubmit={(event) => void saveProfile(event)}>
        <div className="row">
          <input
            aria-label="显示名"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            placeholder="显示名"
          />
          <button type="submit" disabled={busy || displayName.trim() === ''}>
            保存显示名
          </button>
        </div>
        {state.status === 'authenticated' && (
          <p className="hint">
            用户名：<span className="mono">{state.user.username}</span> · 用户 ID：
            <span className="mono">{state.user.id}</span>
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
    </>
  )
}
