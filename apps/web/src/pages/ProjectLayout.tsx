import { useEffect, useState } from 'react'
import { Navigate, NavLink, Outlet, useNavigate, useOutletContext, useParams } from 'react-router-dom'
import { useSession } from '../app/session'
import { LoadingState } from '../components/ui'
import { listMembers, listProjects, type Member, type Project } from '../lib/api'
import { canManageMembers, ROLES, type Role } from '../lib/permissions'

export interface ProjectContext {
  project: Project
  role: Role | null
  members: Member[]
  reloadMembers: () => Promise<Member[]>
}

export function useProject(): ProjectContext {
  return useOutletContext<ProjectContext>()
}

const NAV = [
  { to: 'artifacts', label: '项目资料' },
  { to: 'knowledge', label: '知识文档' },
  { to: 'requirements', label: '需求与方案' },
  { to: 'search', label: '知识检索' },
  { to: 'members', label: '成员与角色' },
]

function asRole(value: string | null | undefined): Role | null {
  return value && (ROLES as readonly string[]).includes(value) ? (value as Role) : null
}

/** Project-scoped frame with sidebar navigation and a project switcher (UI-SPEC §3/§5). */
export default function ProjectLayout() {
  const { projectId = '' } = useParams()
  const navigate = useNavigate()
  const { state } = useSession()
  const userId = state.status === 'authenticated' ? state.user.id : ''
  const [project, setProject] = useState<Project | null | 'missing'>(null)
  const [members, setMembers] = useState<Member[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [error, setError] = useState<string | null>(null)

  const reloadMembers = async () => {
    const list = await listMembers(projectId)
    setMembers(list)
    return list
  }

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const [projectList, memberList] = await Promise.all([listProjects(), listMembers(projectId)])
        if (cancelled) return
        setProjects(projectList)
        setMembers(memberList)
        const found = projectList.find((item) => item.id === projectId) ?? null
        setProject(found ?? 'missing')
      } catch (problem: unknown) {
        if (!cancelled) {
          setError(problem instanceof Error ? problem.message : String(problem))
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [projectId])

  if (project === 'missing') {
    return <Navigate to="/404" replace />
  }
  if (!project) {
    return <LoadingState label={error ?? '正在加载项目…'} />
  }

  const role = asRole(members.find((member) => member.userId === userId)?.role)

  return (
    <div className="project-layout">
      <aside className="sidebar">
        <label className="project-switcher">
          当前项目
          <select
            value={projectId}
            onChange={(event) => navigate(`/projects/${event.target.value}/artifacts`)}
          >
            {projects.map((item) => (
              <option key={item.id} value={item.id}>
                {item.code}
              </option>
            ))}
          </select>
        </label>
        <div className="project-title">
          <strong>{project.name}</strong>
          <span className="code">{project.code}</span>
        </div>
        <nav className="nav">
          {NAV.filter((item) => item.to !== 'members' || canManageMembers(role)).map((item) => (
            <NavLink
              key={item.to}
              to={`/projects/${projectId}/${item.to}`}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <p className="hint sidebar-foot">角色：{role ?? '非成员'}</p>
      </aside>
      <section className="project-main">
        {error && <p className="state error">{error}</p>}
        <Outlet context={{ project, role, members, reloadMembers } satisfies ProjectContext} />
      </section>
    </div>
  )
}
