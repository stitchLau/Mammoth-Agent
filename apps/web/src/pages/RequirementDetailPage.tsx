import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useSession } from '../app/session'
import { BackLink, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../components/ui'
import {
  addRequirementEvidence,
  assessDimension,
  confirmReadiness,
  createSolutionVersion,
  getWorkItem,
  listArtifacts,
  transitionWorkItem,
  updateWorkItem,
  type Artifact,
  type DimensionAssessment,
  type RequirementDetail,
  type RequirementEvidence,
} from '../lib/api'
import {
  dimensionLabel,
  dimensionStateLabel,
  evidenceTypeLabel,
  readinessHeadline,
  solutionStatusLabel,
  workItemPriorityLabel,
  workItemStatusLabel,
  READINESS_DIMENSIONS,
} from '../lib/format'
import {
  canDecideRequirement,
  canEditRequirement,
} from '../lib/permissions'
import { useProject } from './ProjectLayout'

interface DimensionDraft {
  state: string
  note: string
  assumption: string
  owner: string
}

const STATES = ['missing', 'partial', 'confirmed', 'inferred', 'conflict']

const ACTIVE_STATUSES = ['collecting', 'ready_for_analysis', 'analyzing', 'solution_review']

function draftsOf(detail: RequirementDetail): Record<string, DimensionDraft> {
  const result: Record<string, DimensionDraft> = {}
  for (const dimension of detail.readiness.dimensions) {
    result[dimension.dimension] = {
      state: dimension.state,
      note: dimension.note ?? '',
      assumption: dimension.assumptionNote ?? '',
      owner: dimension.decisionOwner ?? '',
    }
  }
  return result
}

const blank = (value: string): string | null => (value.trim() === '' ? null : value.trim())

/** Requirement detail: evidence batches, 11-dimension readiness gate and status actions (M2 S1/S2). */
export default function RequirementDetailPage() {
  const { workItemId = '' } = useParams()
  const { project, role } = useProject()
  const { state } = useSession()
  const userId = state.status === 'authenticated' ? state.user.id : ''
  const navigate = useNavigate()

  const [detail, setDetail] = useState<RequirementDetail | null>(null)
  const [artifacts, setArtifacts] = useState<Artifact[]>([])
  const [drafts, setDrafts] = useState<Record<string, DimensionDraft>>({})
  const [title, setTitle] = useState('')
  const [summary, setSummary] = useState('')
  const [priority, setPriority] = useState('normal')
  const [evidenceType, setEvidenceType] = useState('note')
  const [evidenceText, setEvidenceText] = useState('')
  const [evidenceArtifact, setEvidenceArtifact] = useState('')
  const [evidenceLocator, setEvidenceLocator] = useState('')
  const [comment, setComment] = useState('')
  const [solutionTitle, setSolutionTitle] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Seed the editable dimension drafts once; later reloads must not clobber unsaved edits.
  const draftsSeeded = useRef(false)

  const reload = useCallback(async () => {
    const [loaded, artifactList] = await Promise.all([
      getWorkItem(project.id, workItemId),
      listArtifacts(project.id),
    ])
    setDetail(loaded)
    setArtifacts(artifactList)
    if (!draftsSeeded.current) {
      setDrafts(draftsOf(loaded))
      draftsSeeded.current = true
    }
    setTitle(loaded.item.title)
    setSummary(loaded.item.summary ?? '')
    setPriority(loaded.item.priority)
  }, [project.id, workItemId])

  useEffect(() => {
    setDetail(null)
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

  if (!detail) {
    return <LoadingState label={error ?? '正在加载需求…'} />
  }

  const item = detail.item
  const writable = canEditRequirement(role)
  const decider = canDecideRequirement(role)
  const frozen = ['approved', 'rejected', 'withdrawn'].includes(item.status)
  const canWithdraw = item.createdBy === userId || role === 'owner'
  const artifactName = (id: string) => artifacts.find((entry) => entry.id === id)?.originalName ?? id.slice(0, 8)

  const setDraft = (dimension: string, patch: Partial<DimensionDraft>) =>
    setDrafts((current) => ({
      ...current,
      [dimension]: { ...(current[dimension] ?? { state: 'missing', note: '', assumption: '', owner: '' }), ...patch },
    }))

  return (
    <>
      <BackLink to={`/projects/${project.id}/requirements`} label="返回需求池" />
      <PageHeader
        title={`${item.itemNo} ${item.title}`}
        subtitle={`${project.code} · 优先级 ${workItemPriorityLabel(item.priority)}${item.readyConfirmedAt ? ` · 就绪确认于 ${item.readyConfirmedAt}` : ''}`}
        actions={<StatusBadge status={item.status} label={workItemStatusLabel(item.status)} />}
      />
      {error && <ErrorBanner message={error} onRetry={() => void reload()} />}

      <div className="actions block">
        {item.status === 'collecting' && decider && (
          <button
            type="button"
            disabled={busy || !detail.readiness.ready}
            title={detail.readiness.ready ? undefined : detail.readiness.blocking.join('；')}
            onClick={() =>
              void run(async () => {
                await confirmReadiness(project.id, item.id)
                await reload()
              })
            }
          >
            确认就绪并进入分析
          </button>
        )}
        {item.status === 'ready_for_analysis' && writable && (
          <button
            type="button"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await transitionWorkItem(project.id, item.id, 'analyzing', null)
                await reload()
              })
            }
          >
            开始方案分析
          </button>
        )}
        {ACTIVE_STATUSES.includes(item.status) && writable && (
          <>
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await transitionWorkItem(project.id, item.id, 'on_hold', blank(comment))
                  await reload()
                })
              }
            >
              挂起
            </button>
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await transitionWorkItem(project.id, item.id, 'blocked', blank(comment))
                  await reload()
                })
              }
            >
              标记阻塞
            </button>
          </>
        )}
        {['on_hold', 'blocked'].includes(item.status) && writable && (
          <button
            type="button"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await transitionWorkItem(project.id, item.id, item.priorStatus ?? 'collecting', null)
                await reload()
              })
            }
          >
            恢复（回到 {workItemStatusLabel(item.priorStatus ?? 'collecting')}）
          </button>
        )}
        {decider && ACTIVE_STATUSES.includes(item.status) && (
          <button
            type="button"
            className="secondary"
            disabled={busy || blank(comment) === null}
            title={blank(comment) === null ? '拒绝需要填写理由' : undefined}
            onClick={() =>
              void run(async () => {
                await transitionWorkItem(project.id, item.id, 'rejected', blank(comment))
                await reload()
              })
            }
          >
            拒绝需求
          </button>
        )}
        {canWithdraw && !frozen && (
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await transitionWorkItem(project.id, item.id, 'withdrawn', blank(comment))
                await reload()
              })
            }
          >
            撤回
          </button>
        )}
        {ACTIVE_STATUSES.includes(item.status) && (
          <input
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            placeholder="过渡说明/拒绝理由（可选，拒绝必填）"
            aria-label="过渡说明"
            className="comment-input"
          />
        )}
      </div>

      <h2>需求描述</h2>
      {!frozen && writable ? (
        <form
          className="knowledge-form"
          aria-label="编辑需求"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await updateWorkItem(project.id, item.id, {
                title: title.trim(),
                summary: blank(summary),
                priority,
                ownerUserId: item.ownerUserId,
              })
              await reload()
            })
          }}
        >
          <input value={title} onChange={(e) => setTitle(e.target.value)} aria-label="需求标题" />
          <textarea
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
            rows={4}
            aria-label="需求摘要"
            placeholder="需求背景/目标/范围的当前理解"
          />
          <div className="row">
            <select value={priority} onChange={(e) => setPriority(e.target.value)} aria-label="优先级">
              <option value="low">低</option>
              <option value="normal">普通</option>
              <option value="high">高</option>
              <option value="critical">紧急</option>
            </select>
            <button type="submit" disabled={busy || title.trim() === ''}>
              保存需求
            </button>
          </div>
        </form>
      ) : (
        <article className="knowledge-body">
          <pre>{item.summary ?? '（无描述）'}</pre>
          {item.sourceNote && <p className="hint">来源：{item.sourceNote}</p>}
        </article>
      )}

      <h2>证据（{detail.evidence.length} 批，只追加）</h2>
      <ul className="documents">
        {detail.evidence.map((evidence: RequirementEvidence) => (
          <li key={evidence.id} className="document">
            <div className="document-head">
              <span className="code">批次 {evidence.batchNo}</span>
              <span className="role">{evidenceTypeLabel(evidence.evidenceType)}</span>
              <span className="hint">{evidence.capturedAt}</span>
            </div>
            <p>{evidence.text}</p>
            {evidence.artifactId && (
              <p className="hint">
                引用资料：{artifactName(evidence.artifactId)}
                {evidence.locator ? ` · ${evidence.locator}` : ''}
              </p>
            )}
          </li>
        ))}
        {detail.evidence.length === 0 && <li className="hint">还没有证据，先追加第一批客户描述。</li>}
      </ul>
      {writable && !frozen && (
        <form
          className="knowledge-form"
          aria-label="追加证据"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await addRequirementEvidence(project.id, item.id, {
                evidenceType,
                text: evidenceText.trim(),
                artifactId: blank(evidenceArtifact),
                locator: blank(evidenceLocator),
                capturedAt: null,
              })
              setEvidenceText('')
              setEvidenceLocator('')
              await reload()
            })
          }}
        >
          <h3>追加证据批次</h3>
          <div className="row">
            <select value={evidenceType} onChange={(e) => setEvidenceType(e.target.value)} aria-label="证据类型">
              {['note', 'conversation', 'meeting', 'email', 'artifact'].map((type) => (
                <option key={type} value={type}>
                  {evidenceTypeLabel(type)}
                </option>
              ))}
            </select>
            <select
              value={evidenceArtifact}
              onChange={(e) => setEvidenceArtifact(e.target.value)}
              aria-label="引用资料"
            >
              <option value="">不引用资料</option>
              {artifacts.map((artifact) => (
                <option key={artifact.id} value={artifact.id}>
                  {artifact.originalName}
                </option>
              ))}
            </select>
            <input
              value={evidenceLocator}
              onChange={(e) => setEvidenceLocator(e.target.value)}
              placeholder="引用位置（可选）"
              aria-label="引用位置"
            />
          </div>
          <textarea
            value={evidenceText}
            onChange={(e) => setEvidenceText(e.target.value)}
            rows={3}
            placeholder="原文摘录或沟通记录"
            aria-label="证据内容"
          />
          <button type="submit" disabled={busy || evidenceText.trim() === ''}>
            追加证据
          </button>
        </form>
      )}

      <h2>需求就绪检查（11 维度）</h2>
      <p className={detail.readiness.ready ? 'notice' : 'state error'}>{readinessHeadline(detail.readiness.ready)}</p>
      {detail.readiness.blocking.length > 0 && (
        <ul className="refs">
          {detail.readiness.blocking.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
      )}
      <table className="table">
        <thead>
          <tr>
            <th>维度</th>
            <th>状态</th>
            <th>说明</th>
            <th>假设/影响</th>
            <th>决策责任人</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {READINESS_DIMENSIONS.map((definition) => {
            const draft = drafts[definition.key] ?? { state: 'missing', note: '', assumption: '', owner: '' }
            const assessment: DimensionAssessment | undefined = detail.readiness.dimensions.find(
              (entry) => entry.dimension === definition.key,
            )
            return (
              <tr key={definition.key}>
                <td>
                  {dimensionLabel(definition.key)}
                  {definition.critical && <span className="hint">（关键）</span>}
                </td>
                <td>
                  <select
                    value={draft.state}
                    aria-label={`${dimensionLabel(definition.key)} 状态`}
                    disabled={!writable || frozen}
                    onChange={(event) => setDraft(definition.key, { state: event.target.value })}
                  >
                    {STATES.map((stateValue) => (
                      <option key={stateValue} value={stateValue}>
                        {dimensionStateLabel(stateValue)}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <input
                    value={draft.note}
                    aria-label={`${dimensionLabel(definition.key)} 说明`}
                    disabled={!writable || frozen}
                    onChange={(event) => setDraft(definition.key, { note: event.target.value })}
                  />
                </td>
                <td>
                  <input
                    value={draft.assumption}
                    aria-label={`${dimensionLabel(definition.key)} 假设`}
                    disabled={!writable || frozen}
                    onChange={(event) => setDraft(definition.key, { assumption: event.target.value })}
                  />
                </td>
                <td>
                  <input
                    value={draft.owner}
                    aria-label={`${dimensionLabel(definition.key)} 责任人`}
                    disabled={!writable || frozen}
                    onChange={(event) => setDraft(definition.key, { owner: event.target.value })}
                  />
                </td>
                <td>
                  {writable && !frozen && (
                    <button
                      type="button"
                      className="secondary"
                      disabled={busy}
                      onClick={() =>
                        void run(async () => {
                          await assessDimension(project.id, item.id, definition.key, {
                            state: draft.state,
                            note: blank(draft.note),
                            assumptionNote: blank(draft.assumption),
                            decisionOwner: blank(draft.owner),
                          })
                          await reload()
                        })
                      }
                    >
                      保存
                    </button>
                  )}
                  {assessment?.updatedAt && <span className="hint">{assessment.updatedAt}</span>}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>

      <h2>方案版本（{detail.solutionVersions.length}）</h2>
      <ul className="documents">
        {detail.solutionVersions.map((version) => (
          <li key={version.id} className="document">
            <div className="document-head">
              <span className="code">v{version.version}</span>
              <span className="role">{solutionStatusLabel(version.status)}</span>
              <Link to={`/projects/${project.id}/requirements/${item.id}/solutions/${version.id}`}>
                <strong>{version.title}</strong>
              </Link>
              {item.approvedSolutionVersionId === version.id && <span className="notice">当前批准版本</span>}
            </div>
            <p className="hint">
              作者 {version.createdBy?.slice(0, 8) ?? '—'} · 创建 {version.createdAt}
              {version.approverUserId ? ` · 批准人 ${version.approverUserId.slice(0, 8)}` : ''}
            </p>
          </li>
        ))}
        {detail.solutionVersions.length === 0 && <li className="hint">还没有方案版本。</li>}
      </ul>
      {writable && ['ready_for_analysis', 'analyzing'].includes(item.status) && (
        <form
          className="knowledge-form"
          aria-label="新建方案版本"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              const created = await createSolutionVersion(project.id, item.id, {
                title: solutionTitle.trim(),
                changeSummary: null,
              })
              setSolutionTitle('')
              navigate(`/projects/${project.id}/requirements/${item.id}/solutions/${created.id}`)
            })
          }}
        >
          <h3>新建方案版本</h3>
          <div className="row">
            <input
              value={solutionTitle}
              onChange={(event) => setSolutionTitle(event.target.value)}
              placeholder="方案标题"
              aria-label="方案标题"
            />
            <button type="submit" disabled={busy || solutionTitle.trim() === ''}>
              新建草稿
            </button>
          </div>
        </form>
      )}
      <p className="hint">
        需求创建于 {item.createdAt} · 更新于 {item.updatedAt}
        {item.contextSnapshotId ? ` · 基线快照 ${item.contextSnapshotId.slice(0, 8)}…` : ''}
      </p>
    </>
  )
}
