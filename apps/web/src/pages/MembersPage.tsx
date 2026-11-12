import { useCallback, useEffect, useState } from 'react'
import { useSession } from '../app/session'
import { ErrorBanner, LoadingState, PageHeader, RoleBadge } from '../components/ui'
import { Pagination } from '../components/Pagination'
import { useProject } from './ProjectLayout'
import {
  addMember,
  changeMemberRole,
  createUser,
  listUsers,
  removeMember,
  type Member,
  type UserSummary,
} from '../lib/api'
import { clampPage, pageSlice } from '../lib/pagination'
import { canManageMembers, ROLES, roleLabel } from '../lib/permissions'

const PAGE_SIZE = 10

export default function MembersPage() {
  const { project, role, members, reloadMembers } = useProject()
  const { state } = useSession()
  const selfId = state.status === 'authenticated' ? state.user.id : ''
  const [users, setUsers] = useState<UserSummary[]>([])
  const [page, setPage] = useState(1)
  const [userId, setUserId] = useState('')
  const [newRole, setNewRole] = useState('viewer')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [newUser, setNewUser] = useState({ username: '', displayName: '', password: '', role: 'reviewer' })

  const reloadUsers = useCallback(async () => {
    setUsers(await listUsers())
  }, [])

  useEffect(() => {
    reloadUsers().catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
  }, [reloadUsers])

  const memberIds = new Set(members.map((member) => member.userId))
  const candidates = users.filter((user) => !memberIds.has(user.id))
  const canManage = canManageMembers(role)
  const ownerCount = members.filter((member) => member.role === 'owner').length
  const totalPages = clampPage(page, members.length, PAGE_SIZE)
  const visibleMembers = pageSlice(members, page, PAGE_SIZE)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  function addExisting(event: React.FormEvent) {
    event.preventDefault()
    void run(async () => {
      await addMember(project.id, userId, newRole)
      setUserId('')
      await reloadMembers()
    })
  }

  function createAndAdd(event: React.FormEvent) {
    event.preventDefault()
    void run(async () => {
      const target = await createUser(
        newUser.username.trim(),
        newUser.displayName.trim(),
        newUser.password,
      )
      await addMember(project.id, target.id, newUser.role)
      setNewUser({ username: '', displayName: '', password: '', role: 'reviewer' })
      setCreating(false)
      await Promise.all([reloadUsers(), reloadMembers()])
    })
  }

  return (
    <>
      <PageHeader
        title="成员与角色"
        subtitle={`${project.code} · owner 可添加/调整/移除成员；审核人必须是独立成员（不能自审）`}
      />
      {error && <ErrorBanner message={error} onRetry={() => void reloadMembers()} />}
      {members.length === 0 ? (
        <LoadingState label="正在加载成员…" />
      ) : (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>成员</th>
                <th>角色</th>
                <th>加入时间</th>
                {canManage && <th>操作</th>}
              </tr>
            </thead>
            <tbody>
              {visibleMembers.map((member: Member) => {
                const isSelf = member.userId === selfId
                const isLastOwner = member.role === 'owner' && ownerCount <= 1
                return (
                  <tr key={member.userId}>
                    <td>{nameOf(member.userId)}</td>
                    <td>
                      <RoleBadge role={member.role} />
                    </td>
                    <td className="mono">{member.createdAt}</td>
                    {canManage && (
                      <td className="actions">
                        <select
                          aria-label={`修改 ${nameOf(member.userId)} 的角色`}
                          value={member.role}
                          disabled={busy || (isSelf && isLastOwner)}
                          onChange={(event) =>
                            void run(async () => {
                              await changeMemberRole(project.id, member.userId, event.target.value)
                              await reloadMembers()
                            })
                          }
                        >
                          {ROLES.map((item) => (
                            <option key={item} value={item}>
                              {roleLabel(item)}
                            </option>
                          ))}
                        </select>
                        <button
                          type="button"
                          className="secondary"
                          disabled={busy || (isSelf && isLastOwner)}
                          onClick={() => {
                            if (window.confirm(`确认将 ${nameOf(member.userId)} 移出项目？`)) {
                              void run(async () => {
                                await removeMember(project.id, member.userId)
                                await reloadMembers()
                              })
                            }
                          }}
                        >
                          移除
                        </button>
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
          <Pagination page={totalPages} total={members.length} pageSize={PAGE_SIZE} onPageChange={setPage} />
        </>
      )}

      {canManage ? (
        <>
          <form className="knowledge-form" aria-label="添加成员" onSubmit={addExisting}>
            <h3>添加已有用户</h3>
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
              <button type="button" className="secondary" onClick={() => setCreating((current) => !current)}>
                {creating ? '收起' : '新建用户并加入'}
              </button>
            </div>
            {candidates.length === 0 && <p className="hint">所有用户都已是项目成员。</p>}
          </form>

          {creating && (
            <form className="knowledge-form" aria-label="新建用户" onSubmit={createAndAdd}>
              <h3>新建用户并加入项目</h3>
              <div className="row">
                <input
                  aria-label="新用户名"
                  value={newUser.username}
                  onChange={(e) => setNewUser({ ...newUser, username: e.target.value })}
                  placeholder="用户名（至少 2 位）"
                />
                <input
                  aria-label="新用户显示名"
                  value={newUser.displayName}
                  onChange={(e) => setNewUser({ ...newUser, displayName: e.target.value })}
                  placeholder="显示名"
                />
                <input
                  aria-label="新用户密码"
                  type="password"
                  value={newUser.password}
                  onChange={(e) => setNewUser({ ...newUser, password: e.target.value })}
                  placeholder="密码（至少 8 位）"
                />
                <select
                  aria-label="新用户角色"
                  value={newUser.role}
                  onChange={(e) => setNewUser({ ...newUser, role: e.target.value })}
                >
                  {ROLES.map((item) => (
                    <option key={item} value={item}>
                      {roleLabel(item)}
                    </option>
                  ))}
                </select>
                <button
                  type="submit"
                  disabled={busy || newUser.username.trim().length < 2 || newUser.password.length < 8}
                >
                  创建并加入
                </button>
              </div>
            </form>
          )}
        </>
      ) : (
        <p className="hint">只有项目负责人（owner）可以管理成员。</p>
      )}
    </>
  )

  function nameOf(memberId: string): string {
    const user = users.find((item) => item.id === memberId)
    if (user) {
      return `${user.displayName}（${user.username}）`
    }
    if (memberId === selfId && state.status === 'authenticated') {
      return `${state.user.displayName}（我）`
    }
    return memberId.slice(0, 8)
  }
}
