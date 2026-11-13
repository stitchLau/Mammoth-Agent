import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useSession } from '../app/session'
import { BackLink, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../components/ui'
import { useProject } from './ProjectLayout'
import {
  getKnowledge,
  listArtifacts,
  submitKnowledgeReview,
  updateKnowledge,
  verifyKnowledge,
  type Artifact,
  type KnowledgeDocument,
} from '../lib/api'
import { canEditKnowledge, canSubmitForReview, verifyBlockedReason } from '../lib/permissions'

export default function KnowledgeDetailPage() {
  const { projectId = '', documentId = '' } = useParams()
  const { project, role } = useProject()
  const { state } = useSession()
  const userId = state.status === 'authenticated' ? state.user.id : ''
  const [document, setDocument] = useState<KnowledgeDocument | null>(null)
  const [artifacts, setArtifacts] = useState<Artifact[]>([])
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [artifactId, setArtifactId] = useState('')
  const [locator, setLocator] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const [loaded, artifactList] = await Promise.all([
      getKnowledge(projectId, documentId),
      listArtifacts(projectId),
    ])
    setDocument(loaded)
    setArtifacts(artifactList)
    setTitle(loaded.title)
    setBody(loaded.body)
    setArtifactId(loaded.sourceRefs[0]?.artifactId ?? artifactList[0]?.id ?? '')
    setLocator(loaded.sourceRefs[0]?.locator ?? '')
  }, [projectId, documentId])

  useEffect(() => {
    setDocument(null)
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

  if (!document) {
    return <LoadingState label={error ?? '正在加载知识文档…'} />
  }

  const isCreator = document.createdBy === userId
  const editable = document.status === 'draft' && canEditKnowledge(role, isCreator)
  const blockedReason = verifyBlockedReason(document.status, role, isCreator)
  const artifactName = (id: string) => artifacts.find((item) => item.id === id)?.originalName ?? id.slice(0, 8)

  return (
    <>
      <BackLink to={`/projects/${project.id}/knowledge`} label="返回知识文档" />
      <PageHeader
        title={document.title}
        subtitle={`${project.code} · 版本 ${document.version}${document.reviewer ? ` · 审核人 ${document.reviewer.slice(0, 8)}…` : ''}`}
        actions={
          <>
            <StatusBadge status={document.status} />
            {document.stale && (
              <span className="status status-superseded" title="该知识基于较早的代码基线">
                可能过期
              </span>
            )}
          </>
        }
      />
      {error && <ErrorBanner message={error} onRetry={() => void reload()} />}

      <div className="actions block">
        {canSubmitForReview(document.status, role, isCreator) && (
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
        <button
          type="button"
          disabled={busy || blockedReason !== null}
          title={blockedReason ?? undefined}
          onClick={() =>
            void run(async () => {
              await verifyKnowledge(project.id, document.id)
              await reload()
            })
          }
        >
          审核通过
        </button>
        {blockedReason && <span className="hint">{blockedReason}</span>}
        {editable && (
          <button type="button" className="secondary" onClick={() => setEditing((current) => !current)}>
            {editing ? '取消编辑' : '编辑草稿'}
          </button>
        )}
      </div>

      {editing && editable ? (
        <form
          className="knowledge-form"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await updateKnowledge(project.id, document.id, {
                docType: document.docType,
                title: title.trim(),
                body: body.trim(),
                sourceRefs: [
                  { artifactId, locator: locator.trim() === '' ? null : locator.trim(), note: null },
                ],
              })
              setEditing(false)
              await reload()
            })
          }}
        >
          <h3>编辑草稿</h3>
          <input value={title} onChange={(e) => setTitle(e.target.value)} />
          <textarea value={body} onChange={(e) => setBody(e.target.value)} rows={8} />
          <div className="row">
            <select value={artifactId} onChange={(e) => setArtifactId(e.target.value)} aria-label="来源资料">
              {artifacts.map((artifact) => (
                <option key={artifact.id} value={artifact.id}>
                  {artifact.originalName}
                </option>
              ))}
            </select>
            <input value={locator} onChange={(e) => setLocator(e.target.value)} placeholder="引用位置" />
            <button type="submit" disabled={busy || title.trim() === '' || body.trim() === ''}>
              保存草稿
            </button>
          </div>
        </form>
      ) : (
        <article className="knowledge-body">
          <pre>{document.body}</pre>
        </article>
      )}

      <h2>来源引用</h2>
      <ul className="refs">
        {document.sourceRefs.map((ref, index) => (
          <li key={`${ref.artifactId}-${index}`}>
            <span className="mono">{artifactName(ref.artifactId)}</span>
            {ref.locator ? ` · ${ref.locator}` : ''}
            {ref.note ? ` · ${ref.note}` : ''}
          </li>
        ))}
      </ul>
      <p className="hint">创建于 {document.createdAt} · 更新于 {document.updatedAt}</p>
    </>
  )
}
