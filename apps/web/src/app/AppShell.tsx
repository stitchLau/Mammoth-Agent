import { useEffect, useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { unreadNotificationCount } from '../lib/api'
import { useSession } from '../app/session'

/** Global chrome: brand, project home link, notifications, current user and sign-out (UI-SPEC §3). */
export default function AppShell() {
  const { state, signOut } = useSession()
  const navigate = useNavigate()
  const [unread, setUnread] = useState(0)

  useEffect(() => {
    if (state.status !== 'authenticated') return
    let cancelled = false
    const refresh = () => {
      unreadNotificationCount()
        .then((result) => {
          if (!cancelled) setUnread(result.unread)
        })
        .catch(() => undefined)
    }
    refresh()
    const timer = window.setInterval(refresh, 30000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [state.status])

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app">
      <header className="topbar">
        <Link className="brand" to="/projects">
          Mammoth Agent
        </Link>
        <span className="hint">项目知识底座</span>
        <div className="topbar-right">
          {state.status === 'authenticated' && (
            <>
              <Link className="quiet-link notification-link" to="/notifications">
                通知{unread > 0 ? ` (${unread})` : ''}
              </Link>
              <Link className="quiet-link" to="/settings">
                {state.user.displayName}
              </Link>
              <button type="button" className="secondary" onClick={() => void handleSignOut()}>
                退出
              </button>
            </>
          )}
        </div>
      </header>
      <div className="app-body">
        <Outlet />
      </div>
    </div>
  )
}
