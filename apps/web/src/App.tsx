import { useEffect, useState } from 'react'
import {
  listArtifacts,
  listProjects,
  readUserId,
  uploadArtifact,
  writeUserId,
  type Artifact,
  type Project,
} from './lib/api'
import { formatBytes, formatProjectCode } from './lib/format'

/**
 * M1 shell: identity (temporary header) -> project list -> artifact list + upload.
 * Real project/knowledge screens and the login session arrive in later tasks.
 */
export default function App() {
  const [userId, setUserId] = useState(() => readUserId())
  const [draftUserId, setDraftUserId] = useState(() => readUserId())
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [artifacts, setArtifacts] = useState<Artifact[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!userId) {
      setProjects(null)
      return
    }
    setError(null)
    listProjects(userId)
      .then((items) => {
        setProjects(items)
        setSelectedProjectId((current) => current ?? items[0]?.id ?? null)
      })
      .catch((problem: unknown) => {
        setProjects(null)
        setError(problem instanceof Error ? problem.message : String(problem))
      })
  }, [userId])

  useEffect(() => {
    if (!userId || !selectedProjectId) {
      setArtifacts(null)
      return
    }
    setArtifacts(null)
    listArtifacts(userId, selectedProjectId)
      .then(setArtifacts)
      .catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
  }, [userId, selectedProjectId])

  function applyIdentity() {
    const normalized = draftUserId.trim()
    writeUserId(normalized)
    setUserId(normalized)
    setSelectedProjectId(null)
  }

  async function handleUpload(file: File | null) {
    if (!file || !userId || !selectedProjectId) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await uploadArtifact(userId, selectedProjectId, file)
      setArtifacts(await listArtifacts(userId, selectedProjectId))
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  const selectedProject = projects?.find((project) => project.id === selectedProjectId) ?? null

  return (
    <main className="shell">
      <header>
        <h1>Mammoth Agent</h1>
        <p className="subtitle">项目知识底座 · M1 骨架</p>
      </header>

      <section aria-label="身份">
        <h2>当前用户（临时身份头）</h2>
        <div className="row">
          <input
            aria-label="用户 ID"
            placeholder="粘贴用户 ID（POST /api/users 返回的 id）"
            value={draftUserId}
            onChange={(event) => setDraftUserId(event.target.value)}
          />
          <button type="button" onClick={applyIdentity} disabled={draftUserId.trim() === userId}>
            使用该身份
          </button>
        </div>
        <p className="hint">
          这是认证实现前的临时方式；接入登录会话后由 Cookie 取代，界面无需改变数据流。
        </p>
      </section>

      <section aria-label="项目">
        <h2>我的项目</h2>
        {!userId && <p className="state">请先设置用户 ID。</p>}
        {userId && projects === null && !error && <p className="state">正在加载项目…</p>}
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

      {error && (
        <p className="state error" role="alert">
          {error}
        </p>
      )}

      <footer className="hint">
        项目编码示例：{formatProjectCode('ACME', 'MES', 2026)}；后端 API 说明见 docs/dev-setup.md。
      </footer>
    </main>
  )
}
