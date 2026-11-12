import { useCallback, useEffect, useState } from 'react'
import {
  canSubmitReview,
  canVerify,
  formatBytes,
  knowledgeStatusLabel,
} from './lib/format'
import {
  createCustomer,
  createKnowledge,
  createProduct,
  createProject,
  currentUser,
  ensureCsrf,
  listArtifacts,
  listCustomers,
  listKnowledge,
  listProducts,
  listProjects,
  login,
  logout,
  searchKnowledge,
  submitKnowledgeReview,
  uploadArtifact,
  verifyKnowledge,
  type Artifact,
  type Customer,
  type KnowledgeDocument,
  type KnowledgeHit,
  type Product,
  type Project,
  type SessionUser,
} from './lib/api'

type SessionState = { kind: 'loading' } | { kind: 'anonymous' } | { kind: 'authenticated'; user: SessionUser }

const errorMessage = (problem: unknown): string =>
  problem instanceof Error ? problem.message : String(problem)

/**
 * M1 UI: login -> master data & project setup -> artifacts -> knowledge creation/review -> verified
 * search with citations. The backend owns every rule; this view mirrors its state machine.
 */
export default function App() {
  const [session, setSession] = useState<SessionState>({ kind: 'loading' })
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const refreshProjects = useCallback(async (selectId?: string) => {
    const items = await listProjects()
    setProjects(items)
    setSelectedProjectId((current) => selectId ?? current ?? items[0]?.id ?? null)
  }, [])

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
    refreshProjects().catch((problem: unknown) => setError(errorMessage(problem)))
  }, [session, refreshProjects])

  const handleLogout = useCallback(async () => {
    try {
      await logout()
    } finally {
      setSession({ kind: 'anonymous' })
      setProjects(null)
      setSelectedProjectId(null)
    }
  }, [])

  const selectedProject = projects?.find((project) => project.id === selectedProjectId) ?? null

  return (
    <main className="shell">
      <header>
        <h1>Mammoth Agent</h1>
        <p className="subtitle">项目知识底座 · M1</p>
        {session.kind === 'authenticated' && (
          <div className="account">
            <span>{session.user.displayName}</span>
            <button type="button" className="secondary" onClick={() => void handleLogout()}>
              退出登录
            </button>
          </div>
        )}
      </header>

      {session.kind === 'loading' && <p className="state">正在检查登录状态…</p>}
      {session.kind === 'anonymous' && <LoginPanel onSuccess={setSession} onError={setError} />}

      {session.kind === 'authenticated' && (
        <>
          <SetupPanel
            onCreated={async (projectId) => {
              await refreshProjects(projectId)
            }}
            onError={setError}
          />

          <section aria-label="项目">
            <h2>我的项目</h2>
            {projects === null && <p className="state">正在加载项目…</p>}
            {projects !== null && projects.length === 0 && (
              <p className="state">还没有项目。请在上方「新建档案」中创建客户与项目。</p>
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

          {selectedProject && (
            <ProjectWorkspace
              key={selectedProject.id}
              project={selectedProject}
              onError={setError}
            />
          )}
        </>
      )}

      {error && (
        <p className="state error" role="alert">
          {error}
        </p>
      )}

      <footer className="hint">M1 · 项目知识底座；接口说明见 docs/dev-setup.md，验收见 docs/M1-ACCEPTANCE.md。</footer>
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
      props.onError(errorMessage(problem))
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
        首次使用需先创建管理员账号（`POST /api/users` 仅在没有任何用户时开放）。
      </p>
    </section>
  )
}

function SetupPanel(props: { onCreated: (projectId: string) => Promise<void>; onError: (message: string) => void }) {
  const { onError } = props
  const [customers, setCustomers] = useState<Customer[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [customerName, setCustomerName] = useState('')
  const [productName, setProductName] = useState('')
  const [projectCode, setProjectCode] = useState('')
  const [projectName, setProjectName] = useState('')
  const [customerId, setCustomerId] = useState('')
  const [busy, setBusy] = useState(false)

  const reload = useCallback(async () => {
    const [customerList, productList] = await Promise.all([listCustomers(), listProducts()])
    setCustomers(customerList)
    setProducts(productList)
    setCustomerId((current) => current || customerList[0]?.id || '')
  }, [])

  useEffect(() => {
    reload().catch((problem: unknown) => onError(errorMessage(problem)))
  }, [reload, onError])

  async function run(action: () => Promise<void>) {
    setBusy(true)
    try {
      await action()
    } catch (problem: unknown) {
      onError(errorMessage(problem))
    } finally {
      setBusy(false)
    }
  }

  return (
    <details className="panel" aria-label="新建档案">
      <summary>新建档案（客户 / 产品 / 项目）</summary>
      <div className="grid">
        <form
          className="field"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await createCustomer(customerName.trim())
              setCustomerName('')
              await reload()
            })
          }}
        >
          <h3>新建客户</h3>
          <input value={customerName} onChange={(e) => setCustomerName(e.target.value)} placeholder="客户名称" />
          <button type="submit" disabled={busy || customerName.trim() === ''}>
            创建客户
          </button>
          <p className="hint">{customers.length} 个客户</p>
        </form>

        <form
          className="field"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await createProduct(productName.trim())
              setProductName('')
              await reload()
            })
          }}
        >
          <h3>新建产品</h3>
          <input value={productName} onChange={(e) => setProductName(e.target.value)} placeholder="产品名称" />
          <button type="submit" disabled={busy || productName.trim() === ''}>
            创建产品
          </button>
          <p className="hint">{products.length} 个产品</p>
        </form>

        <form
          className="field"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              const project = await createProject(customerId, projectCode.trim(), projectName.trim())
              setProjectCode('')
              setProjectName('')
              await props.onCreated(project.id)
            })
          }}
        >
          <h3>新建项目</h3>
          <select value={customerId} onChange={(e) => setCustomerId(e.target.value)} aria-label="选择客户">
            {customers.length === 0 && <option value="">（先创建客户）</option>}
            {customers.map((customer) => (
              <option key={customer.id} value={customer.id}>
                {customer.name}
              </option>
            ))}
          </select>
          <input value={projectCode} onChange={(e) => setProjectCode(e.target.value)} placeholder="项目编码，如 ACME-MES-2026" />
          <input value={projectName} onChange={(e) => setProjectName(e.target.value)} placeholder="项目名称" />
          <button type="submit" disabled={busy || customerId === '' || projectCode.trim() === ''}>
            创建项目
          </button>
        </form>
      </div>
    </details>
  )
}

function ProjectWorkspace(props: { project: Project; onError: (message: string) => void }) {
  const { project, onError } = props
  const [artifacts, setArtifacts] = useState<Artifact[] | null>(null)
  const [knowledge, setKnowledge] = useState<KnowledgeDocument[] | null>(null)
  const [expandedDocId, setExpandedDocId] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [hits, setHits] = useState<KnowledgeHit[] | null>(null)
  const [busy, setBusy] = useState(false)

  const reload = useCallback(async () => {
    const [artifactList, documentList] = await Promise.all([
      listArtifacts(project.id),
      listKnowledge(project.id),
    ])
    setArtifacts(artifactList)
    setKnowledge(documentList)
  }, [project.id])

  useEffect(() => {
    setArtifacts(null)
    setKnowledge(null)
    reload().catch((problem: unknown) => onError(errorMessage(problem)))
  }, [reload, onError])

  async function run(action: () => Promise<void>) {
    setBusy(true)
    try {
      await action()
    } catch (problem: unknown) {
      onError(errorMessage(problem))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <section aria-label="资料">
        <h2>项目资料 · {project.code}</h2>
        {artifacts === null && <p className="state">正在加载资料…</p>}
        {artifacts !== null && artifacts.length === 0 && (
          <p className="state">尚无资料，可上传第一份原始文件（作为知识来源）。</p>
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
        <label className="upload">
          <span>{busy ? '处理中…' : '上传原始资料'}</span>
          <input
            type="file"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0] ?? null
              event.target.value = ''
              if (!file) {
                return
              }
              void run(async () => {
                await uploadArtifact(project.id, file)
                await reload()
              })
            }}
          />
        </label>
      </section>

      <section aria-label="知识文档">
        <h2>项目知识</h2>
        {knowledge === null && <p className="state">正在加载知识文档…</p>}
        {knowledge !== null && knowledge.length === 0 && (
          <p className="state">尚无知识文档。先用下方表单基于资料创建草稿。</p>
        )}
        {knowledge !== null && knowledge.length > 0 && (
          <ul className="documents">
            {knowledge.map((document) => (
              <li key={document.id} className="document">
                <div className="document-head">
                  <span className={`status status-${document.status}`}>
                    {knowledgeStatusLabel(document.status)}
                  </span>
                  <strong>{document.title}</strong>
                  <span className="hint">
                    来源 {document.sourceRefs.length} 项
                    {document.reviewer ? ` · 审核人 ${document.reviewer.slice(0, 8)}…` : ''}
                  </span>
                </div>
                <p>{document.body.length > 120 ? `${document.body.slice(0, 120)}…` : document.body}</p>
                <div className="actions">
                  {canSubmitReview(document.status) && (
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() =>
                        void run(async () => {
                          await submitKnowledgeReview(project.id, document.id)
                          await reload()
                        })
                      }
                    >
                      提交审核
                    </button>
                  )}
                  {canVerify(document.status) && (
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() =>
                        void run(async () => {
                          await verifyKnowledge(project.id, document.id)
                          await reload()
                        })
                      }
                    >
                      审核通过
                    </button>
                  )}
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => setExpandedDocId(expandedDocId === document.id ? null : document.id)}
                  >
                    {expandedDocId === document.id ? '收起引用' : '查看来源引用'}
                  </button>
                </div>
                {expandedDocId === document.id && (
                  <ul className="refs">
                    {document.sourceRefs.map((ref, index) => (
                      <li key={`${ref.artifactId}-${index}`}>
                        <span className="mono">artifact {ref.artifactId.slice(0, 8)}…</span>
                        {ref.locator ? ` · ${ref.locator}` : ''}
                        {ref.note ? ` · ${ref.note}` : ''}
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        )}

        {artifacts !== null && artifacts.length > 0 && (
          <CreateKnowledgeForm
            artifacts={artifacts}
            busy={busy}
            onCreate={async (input) => {
              await createKnowledge(project.id, input)
              await reload()
            }}
            onError={onError}
            onBusy={setBusy}
          />
        )}
      </section>

      <section aria-label="知识检索">
        <h2>已验证知识检索</h2>
        <div className="row">
          <input
            aria-label="检索问题"
            placeholder="例如：条码重复怎么处理"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && query.trim() !== '') {
                void run(async () => setHits(await searchKnowledge(project.id, query.trim())))
              }
            }}
          />
          <button
            type="button"
            disabled={busy || query.trim() === ''}
            onClick={() => void run(async () => setHits(await searchKnowledge(project.id, query.trim())))}
          >
            检索
          </button>
        </div>
        {hits !== null && hits.length === 0 && <p className="state">没有命中已验证知识。</p>}
        {hits !== null && hits.length > 0 && (
          <ol className="hits">
            {hits.map((hit) => (
              <li key={hit.chunkId}>
                <strong>{hit.title}</strong>
                <span className="mono"> chunk#{hit.ordinal}</span>
                <p>{hit.text}</p>
                <p className="hint">
                  引用 {hit.sourceRefCount} 项 · 文档 {hit.documentId.slice(0, 8)}…
                </p>
              </li>
            ))}
          </ol>
        )}
      </section>
    </>
  )
}

function CreateKnowledgeForm(props: {
  artifacts: Artifact[]
  busy: boolean
  onCreate: (input: {
    docType: string
    title: string
    body: string
    sourceRefs: { artifactId: string; locator: string | null; note: string | null }[]
  }) => Promise<void>
  onError: (message: string) => void
  onBusy: (busy: boolean) => void
}) {
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [artifactId, setArtifactId] = useState(props.artifacts[0]?.id ?? '')
  const [locator, setLocator] = useState('')

  return (
    <form
      className="knowledge-form"
      aria-label="新建知识文档"
      onSubmit={(event) => {
        event.preventDefault()
        props.onBusy(true)
        void props
          .onCreate({
            docType: 'note',
            title: title.trim(),
            body: body.trim(),
            sourceRefs: [
              { artifactId, locator: locator.trim() === '' ? null : locator.trim(), note: null },
            ],
          })
          .then(() => {
            setTitle('')
            setBody('')
            setLocator('')
          })
          .catch((problem: unknown) => props.onError(errorMessage(problem)))
          .finally(() => props.onBusy(false))
      }}
    >
      <h3>新建知识草稿（必须引用一份资料）</h3>
      <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="知识标题" />
      <textarea value={body} onChange={(e) => setBody(e.target.value)} placeholder="知识正文（整理后的可读内容）" rows={4} />
      <div className="row">
        <select value={artifactId} onChange={(e) => setArtifactId(e.target.value)} aria-label="来源资料">
          {props.artifacts.map((artifact) => (
            <option key={artifact.id} value={artifact.id}>
              {artifact.originalName}
            </option>
          ))}
        </select>
        <input value={locator} onChange={(e) => setLocator(e.target.value)} placeholder="引用位置（可选，如 §2 / 第 3 页）" />
        <button type="submit" disabled={props.busy || title.trim() === '' || body.trim() === '' || artifactId === ''}>
          创建草稿
        </button>
      </div>
    </form>
  )
}
