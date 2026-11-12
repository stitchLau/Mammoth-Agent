import { useNavigate } from 'react-router-dom'
import { useSession } from '../app/session'
import { PageHeader } from '../components/ui'

export default function SettingsPage() {
  const { state, signOut } = useSession()
  const navigate = useNavigate()

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <>
      <PageHeader title="账号设置" subtitle="M1.5 仅展示账号信息；改密与通知在后续阶段提供" />
      {state.status === 'authenticated' && (
        <table className="table">
          <tbody>
            <tr>
              <th>用户名</th>
              <td>{state.user.username}</td>
            </tr>
            <tr>
              <th>显示名</th>
              <td>{state.user.displayName}</td>
            </tr>
            <tr>
              <th>用户 ID</th>
              <td className="mono">{state.user.id}</td>
            </tr>
          </tbody>
        </table>
      )}
      <button type="button" className="secondary" onClick={() => void handleSignOut()}>
        退出登录
      </button>
    </>
  )
}
