import { useCallback, useEffect, useState } from 'react'
import {
  currentUser,
  ensureCsrf,
  listArtifacts,
  listProjects,
  login,
  logout,
  searchKnowledge,
  uploadArtifact,
  type Artifact,
  type KnowledgeHit,
  type Project,
  type SessionUser,
} from './lib/api'
import { formatBytes, formatProjectCode } from './lib/format'

type SessionState = { kind: 'loading' } | { kind: 'anonymous' } | { kind: 'authenticated'; user: SessionUser }

/**
 * M1 shell: login (session cookie) -> project list -> artifact upload/list -> verified-knowledge search
 * with citations. Skeleton for the fuller knowledge/review screens in later tasks.
 */
export default function App() {
  const [session, setSession] = useState<SessionState>({ kind: 'loading' })
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [artifacts, setArtifacts] = useState<Artifact[] | null>(null)
  const [query, setQuery] = useState('')
  const [hits, setHits] = useState<KnowledgeHit[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    void (async () => {
      try {
        await ensureCsrf()
        setSession({ kind: 'authenticated', user: await currentUser() })
      } catch {
        setSession({ kind: 'anonymous' })
      }
    })()
  }, [])

  useEffect(() => {
    if (session.kind !== 'authenticated') {
      setProjects(null)
      return
    }
    setError(null)
    listProjects()
      .then((items) => {
        setProjects(items)
        setSelectedProjectId((current) => current ?? items[0]?.id ?? null)
      })
      .catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
  }, [session])

  useEffect(() => {
    if (session.kind !== 'authenticated' || !selectedProjectId) {
      setArtifacts(null)
      return
    }
    setArtifacts(null)
    setHits(null)
    listArtifacts(selectedProjectId)
      .then(setArtifacts)
      .catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
  }, [session, selectedProjectId])

  const handleLogout = useCallback(async () => {
    try {
      await logout()
    } finally {
      setSession({ kind: 'anonymous' })
      setSelectedProjectId(null)
      setQuery('')
      setHits(null)
    }
  }, [])

  async function handleUpload(file: File | null) {
    if (!file || !selectedProjectId) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await uploadArtifact(selectedProjectId, file)
      setArtifacts(await listArtifacts(selectedProjectId))
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  async function handleSearch() {
    if (!selectedProjectId || query.trim() === '') {
      return
    }
    setError(null)
    try {
      setHits(await searchKnowledge(selectedProjectId, query.trim()))
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    }
  }

  const selectedProject = projects?.find((project) => project.id === selectedProjectId) ?? null

  return (
    <main className="shell">
      <header>
        <h1>Mammoth Agent</h1>
        <p className="subtitle">项目知识底座 · M1</p>
        {session.kind === 'authenticated' && (
          <div className="account">
            <span>{session.user.displayName}</span>
            <button type="button" className="secondary" onClick={handleLogout}>
              退出登录
            </button>
          </div>
        )}
      </header>

      {session.kind === 'loading' && <p className="state">正在检查登录状态…</p>}
      {session.kind === 'anonymous' && <LoginPanel onSuccess={setSession} onError={setError} />}

      {session.kind === 'authenticated' && (
        <>
          <section aria-label="项目">
            <h2>我的项目</h2>
            {projects === null && <p className="state">正在加载项目…</p>}
            {projects !== null && projects.length === 0 && (
              <p className="state">没有可见项目。请先用 API 创建客户与项目（见 docs/dev-setup.md）。</p>
            )}
            {projects !== null && projects.length > 0 && (
              <ul className="list">
                {projects.map((project) => (
                  <li key={project.id}>
                    <button
                      type="button"
                      className={project.id === selectedProjectId ? 'chip active' : 'chip'}
                      onClick={() => setSelectedProjectId(project.id)}
                    >
                      <span className="code">{project.code}</span>
                      <span>{project.name}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section aria-label="资料">
            <h2>项目资料{selectedProject ? ` · ${selectedProject.code}` : ''}</h2>
            {!selectedProject && <p className="state">选择一个项目查看资料。</p>}
            {selectedProject && artifacts === null && <p className="state">正在加载资料…</p>}
            {selectedProject && artifacts !== null && artifacts.length === 0 && (
              <p className="state">尚无资料，可上传第一份原始文件。</p>
            )}
            {artifacts !== null && artifacts.length > 0 && (
              <table className="table">
                <thead>
                  <tr>
                    <th>文件名</th>
                    <th>大小</th>
                    <th>哈希（前 12 位）</th>
                    <th>上传时间</th>
                  </tr>
                </thead>
                <tbody>
                  {artifacts.map((artifact) => (
                    <tr key={artifact.id}>
                      <td>{artifact.originalName}</td>
                      <td>{formatBytes(artifact.sizeBytes)}</td>
                      <td className="mono">{artifact.contentHash.slice(0, 12)}</td>
                      <td className="mono">{artifact.createdAt}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {selectedProject && (
              <label className="upload">
                <span>{busy ? '上传中…' : '上传原始资料'}</span>
                <input
                  type="file"
                  disabled={busy}
                  onChange={(event) => {
                    void handleUpload(event.target.files?.[0] ?? null)
                    event.target.value = ''
                  }}
                />
              </label>
            )}
          </section>

          <section aria-label="知识检索">
            <h2>已验证知识检索</h2>
            {!selectedProject && <p className="state">选择项目后可检索其已验证知识。</p>}
            {selectedProject && (
              <div className="row">
                <input
                  aria-label="检索问题"
                  placeholder="例如：条码重复怎么处理"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      void handleSearch()
                    }
                  }}
                />
                <button type="button" onClick={() => void handleSearch()} disabled={query.trim() === ''}>
                  检索
                </button>
              </div>
            )}
            {hits !== null && hits.length === 0 && <p className="state">没有命中已验证知识。</p>}
            {hits !== null && hits.length > 0 && (
              <ol className="hits">
                {hits.map((hit) => (
                  <li key={hit.chunkId}>
                    <strong>{hit.title}</strong>
                    <span className="mono"> chunk#{hit.ordinal}</span>
                    <p>{hit.text}</p>
                    <p className="hint">引用数：{hit.sourceRefCount} · 文档 {hit.documentId.slice(0, 8)}…</p>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </>
      )}

      {error && (
        <p className="state error" role="alert">
          {error}
        </p>
      )}

      <footer className="hint">
        项目编码示例：{formatProjectCode('ACME', 'MES', 2026)}；接口说明见 docs/dev-setup.md。
      </footer>
    </main>
  )
}

function LoginPanel(props: {
  onSuccess: (state: SessionState) => void
  onError: (message: string | null) => void
}) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    props.onError(null)
    try {
      const user = await login(username.trim(), password)
      props.onSuccess({ kind: 'authenticated', user })
    } catch (problem: unknown) {
      props.onError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="登录">
      <h2>登录</h2>
      <form className="stack" onSubmit={(event) => void submit(event)}>
        <label>
          用户名
          <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
        </label>
        <label>
          密码
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
          />
        </label>
        <button type="submit" disabled={busy || username.trim() === '' || password === ''}>
          {busy ? '登录中…' : '登录'}
        </button>
      </form>
      <p className="hint">
        首次使用需先创建管理员账号（POST /api/users 仅在没有用户时开放，见 docs/dev-setup.md）。
      </p>
    </section>
  )
}
