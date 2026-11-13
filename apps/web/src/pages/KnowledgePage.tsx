import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSession } from '../app/session'
import { EmptyState, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../components/ui'
import { Pagination } from '../components/Pagination'
import { useProject } from './ProjectLayout'
import {
  createKnowledge,
  listArtifacts,
  listKnowledge,
  submitKnowledgeReview,
  type Artifact,
  type KnowledgeDocument,
} from '../lib/api'
import { clampPage, pageSlice } from '../lib/pagination'
import { canEditKnowledge, canSubmitForReview } from '../lib/permissions'

const PAGE_SIZE = 10

const STATUS_FILTERS = [
  { value: '', label: '全部状态' },
  { value: 'draft', label: '草稿' },
  { value: 'needs_review', label: '待审核' },
  { value: 'verified', label: '已验证' },
  { value: 'superseded', label: '已被替代' },
  { value: 'deprecated', label: '已废弃' },
]

export default function KnowledgePage() {
  const { project, role } = useProject()
  const { state } = useSession()
  const userId = state.status === 'authenticated' ? state.user.id : ''
  const [documents, setDocuments] = useState<KnowledgeDocument[] | null>(null)
  const [artifacts, setArtifacts] = useState<Artifact[]>([])
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(1)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [artifactId, setArtifactId] = useState('')
  const [locator, setLocator] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const [documentList, artifactList] = await Promise.all([
      listKnowledge(project.id),
      listArtifacts(project.id),
    ])
    setDocuments(documentList)
    setArtifacts(artifactList)
    setArtifactId((current) => current || artifactList[0]?.id || '')
  }, [project.id])

  useEffect(() => {
    setDocuments(null)
    reload().catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
  }, [reload])

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

  const visible = documents?.filter((document) => status === '' || document.status === status) ?? null
  const writable = canEditKnowledge(role, true)
  const totalPages = clampPage(page, visible?.length ?? 0, PAGE_SIZE)

  return (
    <>
      <PageHeader
        title="知识文档"
        subtitle="draft → 待审核 → 已验证；已验证内容才会进入检索与助手回答"
        actions={
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value)
              setPage(1)
            }}
            aria-label="按状态筛选"
          >
            {STATUS_FILTERS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        }
      />
      {error && <ErrorBanner message={error} onRetry={() => void reload()} />}
      {documents === null && <LoadingState label="正在加载知识文档…" />}
      {visible !== null && visible.length === 0 && (
        <EmptyState
          title={status === '' ? '还没有知识文档' : '该状态下没有文档'}
          hint="基于已上传的资料创建知识草稿，经人工审核后才会被检索到。"
        />
      )}
      {visible !== null && visible.length > 0 && (
        <ul className="documents">
          {pageSlice(visible, totalPages, PAGE_SIZE).map((document) => (
            <li key={document.id} className="document">
              <div className="document-head">
                <StatusBadge status={document.status} />
                {document.stale && (
                  <span className="status status-superseded" title="该知识基于较早的代码基线">
                    可能过期
                  </span>
                )}
                <Link to={`/projects/${project.id}/knowledge/${document.id}`}>
                  <strong>{document.title}</strong>
                </Link>
                <span className="hint">来源 {document.sourceRefs.length} 项</span>
              </div>
              <p>{document.body.length > 120 ? `${document.body.slice(0, 120)}…` : document.body}</p>
              {canSubmitForReview(document.status, role, document.createdBy === userId) && (
                <div className="actions">
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
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      {visible !== null && visible.length > PAGE_SIZE && (
        <Pagination page={totalPages} total={visible.length} pageSize={PAGE_SIZE} onPageChange={setPage} />
      )}

      {writable && artifacts.length > 0 && (
        <form
          className="knowledge-form"
          aria-label="新建知识草稿"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await createKnowledge(project.id, {
                docType: 'note',
                title: title.trim(),
                body: body.trim(),
                sourceRefs: [{ artifactId, locator: locator.trim() === '' ? null : locator.trim(), note: null }],
              })
              setTitle('')
              setBody('')
              setLocator('')
              await reload()
            })
          }}
        >
          <h3>新建知识草稿（必须引用一份资料）</h3>
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="知识标题" />
          <textarea value={body} onChange={(e) => setBody(e.target.value)} rows={4} placeholder="知识正文" />
          <div className="row">
            <select value={artifactId} onChange={(e) => setArtifactId(e.target.value)} aria-label="来源资料">
              {artifacts.map((artifact) => (
                <option key={artifact.id} value={artifact.id}>
                  {artifact.originalName}
                </option>
              ))}
            </select>
            <input value={locator} onChange={(e) => setLocator(e.target.value)} placeholder="引用位置（可选）" />
            <button type="submit" disabled={busy || title.trim() === '' || body.trim() === '' || artifactId === ''}>
              创建草稿
            </button>
          </div>
        </form>
      )}
      {writable && artifacts.length === 0 && (
        <p className="hint">创建知识前，请先在「项目资料」上传至少一份资料。</p>
      )}
    </>
  )
}
