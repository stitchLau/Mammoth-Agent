import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useSession } from '../app/session'
import { BackLink, ErrorBanner, LoadingState, PageHeader } from '../components/ui'
import {
  addAcceptanceCriterion,
  addDesignItem,
  addSolutionTask,
  approveSolution,
  deleteAcceptanceCriterion,
  deleteDesignItem,
  deleteSolutionTask,
  getSolutionVersion,
  newSolutionVersion,
  renderSolution,
  requestSolutionChanges,
  submitSolution,
  type DesignItem,
  type RenderResult,
  type SolutionDetail,
} from '../lib/api'
import { solutionStatusLabel } from '../lib/format'
import {
  canApproveSolution,
  canEditRequirement,
  solutionApproveBlockedReason,
} from '../lib/permissions'
import { useProject } from './ProjectLayout'

const KIND_LABELS: Record<string, string> = {
  business: '业务',
  technical: '技术',
  shared: '共享',
}

const ITEM_TEXT = (items: DesignItem[], id: string | null): string =>
  id ? items.find((item) => item.id === id)?.title ?? id.slice(0, 8) : '—'

/** Solution version detail: structure editor, consistency gate and dual-view rendering (M2 S3–S5). */
export default function SolutionPage() {
  const { workItemId = '', versionId = '' } = useParams()
  const { project, role } = useProject()
  const { state } = useSession()
  const userId = state.status === 'authenticated' ? state.user.id : ''

  const [detail, setDetail] = useState<SolutionDetail | null>(null)
  const [business, setBusiness] = useState<RenderResult | null>(null)
  const [technical, setTechnical] = useState<RenderResult | null>(null)
  const [kind, setKind] = useState('business')
  const [parentId, setParentId] = useState('')
  const [itemTitle, setItemTitle] = useState('')
  const [itemBody, setItemBody] = useState('')
  const [criterionItem, setCriterionItem] = useState('')
  const [criterionText, setCriterionText] = useState('')
  const [taskItem, setTaskItem] = useState('')
  const [taskCriterion, setTaskCriterion] = useState('')
  const [taskTitle, setTaskTitle] = useState('')
  const [taskDoneWhen, setTaskDoneWhen] = useState('')
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const [loaded, businessView, technicalView] = await Promise.all([
      getSolutionVersion(project.id, workItemId, versionId),
      renderSolution(project.id, workItemId, versionId, 'business'),
      renderSolution(project.id, workItemId, versionId, 'technical'),
    ])
    setDetail(loaded)
    setBusiness(businessView)
    setTechnical(technicalView)
    setCriterionItem((current) => current || loaded.designItems[0]?.id || '')
    setTaskItem((current) => current || loaded.designItems[0]?.id || '')
  }, [project.id, workItemId, versionId])

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
    return <LoadingState label={error ?? '正在加载方案…'} />
  }

  const version = detail.version
  const editable = version.status === 'draft' && canEditRequirement(role)
  const isAuthor = version.createdBy === userId
  const blockedReason = solutionApproveBlockedReason(version.status, role, isAuthor)
  const items = detail.designItems
  const report = detail.report

  return (
    <>
      <BackLink to={`/projects/${project.id}/requirements/${workItemId}`} label="返回需求" />
      <PageHeader
        title={`方案 v${version.version}：${version.title}`}
        subtitle={`${solutionStatusLabel(version.status)}${version.changeSummary ? ` · ${version.changeSummary}` : ''}`}
        actions={
          <span className={`status status-${version.status}`} title={version.status}>
            {solutionStatusLabel(version.status)}
          </span>
        }
      />
      {error && <ErrorBanner message={error} onRetry={() => void reload()} />}

      <div className="actions block">
        {editable && (
          <button
            type="button"
            disabled={busy || !report.ok}
            title={report.ok ? undefined : '先修复一致性检查中的问题'}
            onClick={() =>
              void run(async () => {
                await submitSolution(project.id, workItemId, version.id)
                await reload()
              })
            }
          >
            提交评审
          </button>
        )}
        {version.status === 'in_review' && canApproveSolution(version.status, role, isAuthor) && (
          <button
            type="button"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await approveSolution(project.id, workItemId, version.id, comment.trim() === '' ? null : comment.trim())
                await reload()
              })
            }
          >
            批准方案
          </button>
        )}
        {version.status === 'in_review' && (
          <button
            type="button"
            className="secondary"
            disabled={busy || comment.trim() === ''}
            title={comment.trim() === '' ? '要求修改需要填写意见' : undefined}
            onClick={() =>
              void run(async () => {
                await requestSolutionChanges(project.id, workItemId, version.id, comment.trim())
                await reload()
              })
            }
          >
            要求修改
          </button>
        )}
        {['approved', 'superseded'].includes(version.status) && canEditRequirement(role) && (
          <button
            type="button"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await newSolutionVersion(project.id, workItemId, version.id, null)
                await reload()
              })
            }
          >
            基于此版本新建草稿
          </button>
        )}
        {version.status === 'in_review' && blockedReason && <span className="hint">{blockedReason}</span>}
        {version.status === 'in_review' && (
          <input
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            placeholder="评审意见（批准可选，要求修改必填）"
            aria-label="评审意见"
            className="comment-input"
          />
        )}
      </div>

      <h2>一致性检查</h2>
      <p className={report.ok ? 'notice' : 'state error'}>
        {report.ok ? '结构完整，可提交评审' : `存在 ${report.issues.length} 个阻断问题`}
      </p>
      <p className="hint">
        业务设计项 {report.counts.businessItems} · 技术设计项 {report.counts.technicalItems} · 共享{' '}
        {report.counts.sharedItems} · 验收标准 {report.counts.acceptanceCriteria} · 任务 {report.counts.tasks}
      </p>
      {report.issues.length > 0 && (
        <ul className="refs">
          {report.issues.map((issue, index) => (
            <li key={`${issue.code}-${index}`}>
              <span className="code">{issue.code}</span> {issue.message}
            </li>
          ))}
        </ul>
      )}

      {editable && (
        <>
          <h2>设计项（单一模型）</h2>
          <table className="table">
            <thead>
              <tr>
                <th>类型</th>
                <th>标题</th>
                <th>上级</th>
                <th>正文</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((entry) => (
                <tr key={entry.id}>
                  <td>{KIND_LABELS[entry.itemKind] ?? entry.itemKind}</td>
                  <td>{entry.title}</td>
                  <td>{ITEM_TEXT(items, entry.parentId)}</td>
                  <td>{entry.body.length > 60 ? `${entry.body.slice(0, 60)}…` : entry.body}</td>
                  <td>
                    <button
                      type="button"
                      className="secondary"
                      disabled={busy}
                      onClick={() =>
                        void run(async () => {
                          await deleteDesignItem(project.id, workItemId, version.id, entry.id)
                          await reload()
                        })
                      }
                    >
                      删除
                    </button>
                  </td>
                </tr>
              ))}
              {items.length === 0 && (
                <tr>
                  <td colSpan={5} className="hint">
                    还没有设计项。先建立业务目标，再补充技术设计项与共享口径。
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          <form
            className="knowledge-form"
            aria-label="新建设计项"
            onSubmit={(event) => {
              event.preventDefault()
              void run(async () => {
                await addDesignItem(project.id, workItemId, version.id, {
                  itemKind: kind,
                  parentId: parentId === '' ? null : parentId,
                  title: itemTitle.trim(),
                  body: itemBody,
                })
                setItemTitle('')
                setItemBody('')
                await reload()
              })
            }}
          >
            <div className="row">
              <select value={kind} onChange={(event) => setKind(event.target.value)} aria-label="设计项类型">
                <option value="business">业务</option>
                <option value="technical">技术</option>
                <option value="shared">共享</option>
              </select>
              <select value={parentId} onChange={(event) => setParentId(event.target.value)} aria-label="上级设计项">
                <option value="">（顶层）</option>
                {items.map((entry) => (
                  <option key={entry.id} value={entry.id}>
                    {entry.title}
                  </option>
                ))}
              </select>
              <input
                value={itemTitle}
                onChange={(event) => setItemTitle(event.target.value)}
                placeholder="设计项标题"
                aria-label="设计项标题"
              />
            </div>
            <textarea
              value={itemBody}
              onChange={(event) => setItemBody(event.target.value)}
              rows={2}
              placeholder="设计说明（流程/规则/影响）"
              aria-label="设计项正文"
            />
            <button type="submit" disabled={busy || itemTitle.trim() === ''}>
              添加设计项
            </button>
          </form>

          <h2>验收标准</h2>
          <ul className="refs">
            {detail.acceptanceCriteria.map((criterion) => (
              <li key={criterion.id}>
                [{ITEM_TEXT(items, criterion.designItemId)}] {criterion.text}
                <button
                  type="button"
                  className="secondary"
                  disabled={busy}
                  onClick={() =>
                    void run(async () => {
                      await deleteAcceptanceCriterion(project.id, workItemId, version.id, criterion.id)
                      await reload()
                    })
                  }
                >
                  删除
                </button>
              </li>
            ))}
            {detail.acceptanceCriteria.length === 0 && <li className="hint">还没有验收标准。</li>}
          </ul>
          <form
            className="knowledge-form"
            aria-label="新增验收标准"
            onSubmit={(event) => {
              event.preventDefault()
              void run(async () => {
                await addAcceptanceCriterion(project.id, workItemId, version.id, {
                  designItemId: criterionItem,
                  text: criterionText.trim(),
                })
                setCriterionText('')
                await reload()
              })
            }}
          >
            <div className="row">
              <select
                value={criterionItem}
                onChange={(event) => setCriterionItem(event.target.value)}
                aria-label="验收标准对应设计项"
              >
                {items.map((entry) => (
                  <option key={entry.id} value={entry.id}>
                    {entry.title}
                  </option>
                ))}
              </select>
              <input
                value={criterionText}
                onChange={(event) => setCriterionText(event.target.value)}
                placeholder="验收标准（可验证）"
                aria-label="验收标准文本"
              />
              <button type="submit" disabled={busy || criterionText.trim() === '' || items.length === 0}>
                添加验收标准
              </button>
            </div>
          </form>

          <h2>开发任务</h2>
          <table className="table">
            <thead>
              <tr>
                <th>任务</th>
                <th>追溯设计项</th>
                <th>完成条件</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {detail.tasks.map((task) => (
                <tr key={task.id}>
                  <td>{task.title}</td>
                  <td>{ITEM_TEXT(items, task.designItemId)}</td>
                  <td>{task.doneWhen ?? '—'}</td>
                  <td>
                    <button
                      type="button"
                      className="secondary"
                      disabled={busy}
                      onClick={() =>
                        void run(async () => {
                          await deleteSolutionTask(project.id, workItemId, version.id, task.id)
                          await reload()
                        })
                      }
                    >
                      删除
                    </button>
                  </td>
                </tr>
              ))}
              {detail.tasks.length === 0 && (
                <tr>
                  <td colSpan={4} className="hint">
                    还没有任务。任务必须追溯到设计项或验收标准。
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          <form
            className="knowledge-form"
            aria-label="新增开发任务"
            onSubmit={(event) => {
              event.preventDefault()
              void run(async () => {
                await addSolutionTask(project.id, workItemId, version.id, {
                  designItemId: taskItem === '' ? null : taskItem,
                  acceptanceCriterionId: taskCriterion === '' ? null : taskCriterion,
                  title: taskTitle.trim(),
                  description: null,
                  doneWhen: taskDoneWhen.trim() === '' ? null : taskDoneWhen.trim(),
                  dependsOnTaskId: null,
                })
                setTaskTitle('')
                setTaskDoneWhen('')
                await reload()
              })
            }}
          >
            <div className="row">
              <select value={taskItem} onChange={(event) => setTaskItem(event.target.value)} aria-label="任务设计项">
                <option value="">（不关联设计项）</option>
                {items.map((entry) => (
                  <option key={entry.id} value={entry.id}>
                    {entry.title}
                  </option>
                ))}
              </select>
              <select
                value={taskCriterion}
                onChange={(event) => setTaskCriterion(event.target.value)}
                aria-label="任务验收标准"
              >
                <option value="">（不关联验收标准）</option>
                {detail.acceptanceCriteria.map((criterion) => (
                  <option key={criterion.id} value={criterion.id}>
                    {criterion.text}
                  </option>
                ))}
              </select>
              <input
                value={taskTitle}
                onChange={(event) => setTaskTitle(event.target.value)}
                placeholder="任务标题"
                aria-label="任务标题"
              />
              <input
                value={taskDoneWhen}
                onChange={(event) => setTaskDoneWhen(event.target.value)}
                placeholder="完成条件"
                aria-label="任务完成条件"
              />
              <button
                type="submit"
                disabled={busy || taskTitle.trim() === '' || (taskItem === '' && taskCriterion === '')}
              >
                添加任务
              </button>
            </div>
          </form>
        </>
      )}

      <h2>双视图（同一模型渲染）</h2>
      <div className="dual-view">
        <section className="knowledge-body" aria-label="业务方案视图">
          <h3>业务方案</h3>
          <pre>{business?.markdown ?? '渲染中…'}</pre>
        </section>
        <section className="knowledge-body" aria-label="技术方案视图">
          <h3>技术方案</h3>
          <pre>{technical?.markdown ?? '渲染中…'}</pre>
        </section>
      </div>

      <h2>评审记录</h2>
      <ul className="refs">
        {detail.approvals.map((approval) => (
          <li key={approval.id}>
            {approval.at} · <span className="code">{approval.action}</span> ·{' '}
            {approval.actorUserId?.slice(0, 8) ?? '—'}
            {approval.comment ? ` · ${approval.comment}` : ''}
          </li>
        ))}
        {detail.approvals.length === 0 && <li className="hint">还没有评审记录。</li>}
      </ul>
    </>
  )
}
