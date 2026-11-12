import { useCallback, useEffect, useState } from 'react'
import { useSession } from '../app/session'
import { ErrorBanner, LoadingState, PageHeader, RoleBadge } from '../components/ui'
import { useProject } from './ProjectLayout'
import { addMember, listUsers, type UserSummary } from '../lib/api'
import { canManageMembers, ROLES, roleLabel } from '../lib/permissions'

export default function MembersPage() {
  const { project, role, members, reloadMembers } = useProject()
  const { state } = useSession()
  const [users, setUsers] = useState<UserSummary[]>([])
  const [userId, setUserId] = useState('')
  const [newRole, setNewRole] = useState('viewer')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reloadUsers = useCallback(async () => {
    setUsers(await listUsers())
  }, [])

  useEffect(() => {
    reloadUsers().catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
  }, [reloadUsers])

  const memberIds = new Set(members.map((member) => member.userId))
  const candidates = users.filter((user) => !memberIds.has(user.id))
  const canManage = canManageMembers(role)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await addMember(project.id, userId, newRole)
      setUserId('')
      await reloadMembers()
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  const nameOf = (memberId: string) => {
    const user = users.find((item) => item.id === memberId)
    if (user) {
      return `${user.displayName}（${user.username}）`
    }
    return state.status === 'authenticated' && state.user.id === memberId
      ? `${state.user.displayName}（我）`
      : memberId.slice(0, 8)
  }

  return (
    <>
      <PageHeader
        title="成员与角色"
        subtitle={`${project.code} · owner 可添加成员；审核人必须是独立成员（不能自审）`}
      />
      {error && <ErrorBanner message={error} onRetry={() => void reloadMembers()} />}
      {members.length === 0 ? (
        <LoadingState label="正在加载成员…" />
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>成员</th>
              <th>角色</th>
              <th>加入时间</th>
            </tr>
          </thead>
          <tbody>
            {members.map((member) => (
              <tr key={member.userId}>
                <td>{nameOf(member.userId)}</td>
                <td>
                  <RoleBadge role={member.role} />
                </td>
                <td className="mono">{member.createdAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {canManage ? (
        <form className="knowledge-form" aria-label="添加成员" onSubmit={(event) => void submit(event)}>
          <h3>添加成员</h3>
          <div className="row">
            <select value={userId} onChange={(e) => setUserId(e.target.value)} aria-label="选择用户">
              <option value="">选择用户…</option>
              {candidates.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.displayName}（{user.username}）
                </option>
              ))}
            </select>
            <select value={newRole} onChange={(e) => setNewRole(e.target.value)} aria-label="选择角色">
              {ROLES.map((item) => (
                <option key={item} value={item}>
                  {roleLabel(item)}
                </option>
              ))}
            </select>
            <button type="submit" disabled={busy || userId === ''}>
              添加
            </button>
          </div>
          {candidates.length === 0 && <p className="hint">所有用户都已是项目成员。</p>}
        </form>
      ) : (
        <p className="hint">只有项目负责人（owner）可以管理成员。</p>
      )}
    </>
  )
}
