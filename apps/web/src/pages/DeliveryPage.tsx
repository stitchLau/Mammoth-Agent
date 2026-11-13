import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useSession } from '../app/session'
import { BackLink, EmptyState, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../components/ui'
import {
  acceptKnowledgeProposal,
  addDeviation,
  approveDelivery,
  createDelivery,
  createKnowledgeProposal,
  getHandoff,
  getHandoffPackage,
  getSolutionVersion,
  getWorkItem,
  listAcceptanceMatrix,
  listBaselines,
  listDeliveries,
  listExecutions,
  listKnowledgeProposals,
  listRepositories,
  recordExecution,
  rejectKnowledgeProposal,
  type AcceptanceAnalysis,
  type Baseline,
  type DeliverySnapshotView,
  type ExecutionDetailView,
  type HandoffView,
  type HandoffPackageView,
  type KnowledgeProposalView,
  type Repository,
  type RequirementDetail,
  type SolutionTaskItem,
} from '../lib/api'
import { workItemStatusLabel, solutionStatusLabel } from '../lib/format'
import { canApproveSolution, canDecideRequirement, canEditRequirement } from '../lib/permissions'
import { useProject } from './ProjectLayout'

const short = (value: string | null | undefined): string =>
  value === null || value === undefined ? '—' : value.slice(0, 8)

/** Delivery loop page (M3): handoff, execution evidence, matrix, As-built and knowledge proposals. */
export default function DeliveryPage() {
  const { workItemId = '' } = useParams()
  const { project, role } = useProject()
  const { state } = useSession()
  const userId = state.status === 'authenticated' ? state.user.id : ''

  const [item, setItem] = useState<RequirementDetail | null>(null)
  const [tasks, setTasks] = useState<SolutionTaskItem[]>([])
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [baselines, setBaselines] = useState<Baseline[]>([])
  const [executions, setExecutions] = useState<ExecutionDetailView>({
    records: [],
    changes: [],
    taskStates: [],
    deviations: [],
  })
  const [analysis, setAnalysis] = useState<AcceptanceAnalysis | null>(null)
  const [deliveries, setDeliveries] = useState<DeliverySnapshotView[]>([])
  const [proposals, setProposals] = useState<KnowledgeProposalView[]>([])
  const [handoff, setHandoff] = useState<HandoffView | null>(null)
  const [handoffPackage, setHandoffPackage] = useState<HandoffPackageView | null>(null)

  const [repoId, setRepoId] = useState('')
  const [baselineId, setBaselineId] = useState('')
  const [toCommit, setToCommit] = useState('')
  const [execSummary, setExecSummary] = useState('')
  const [testsNote, setTestsNote] = useState('')
  const [completedTasks, setCompletedTasks] = useState<string[]>([])
  const [deviationType, setDeviationType] = useState('extra')
  const [deviationDescription, setDeviationDescription] = useState('')
  const [deviationRecordId, setDeviationRecordId] = useState('')
  const [snapshotTitle, setSnapshotTitle] = useState('')
  const [proposalTitle, setProposalTitle] = useState('')
  const [handoffNote, setHandoffNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const [detail, repoList, baselineList, executionDetail, matrix, deliveryList, proposalList] =
      await Promise.all([
        getWorkItem(project.id, workItemId),
        listRepositories(project.id),
        listBaselines(project.id),
        listExecutions(project.id, workItemId),
        listAcceptanceMatrix(project.id, workItemId).catch(() => null),
        listDeliveries(project.id, workItemId),
        listKnowledgeProposals(project.id, workItemId),
      ])
    setItem(detail)
    setRepositories(repoList)
    setBaselines(baselineList)
    setExecutions(executionDetail)
    setAnalysis(matrix)
    setDeliveries(deliveryList)
    setProposals(proposalList)
    setRepoId((current) => current || repoList.find((entry) => entry.status === 'active')?.id || '')
    setBaselineId((current) => current || baselineList[0]?.id || '')
    setDeviationRecordId((current) => current || executionDetail.records[0]?.id || '')
    const approvedVersionId = detail.item.approvedSolutionVersionId
    if (approvedVersionId) {
      const solution = await getSolutionVersion(project.id, workItemId, approvedVersionId)
      setTasks(solution.tasks)
    } else {
      setTasks([])
    }
  }, [project.id, workItemId])

  useEffect(() => {
    setItem(null)
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

  if (!item) {
    return <LoadingState label={error ?? '正在加载交付数据…'} />
  }

  const workItem = item.item
  const writer = canEditRequirement(role)
  const decider = canDecideRequirement(role)
  const activeRepos = repositories.filter((repository) => repository.status === 'active')

  return (
    <>
      <BackLink to={`/projects/${project.id}/requirements/${workItem.id}`} label="返回需求" />
      <PageHeader
        title={`${workItem.itemNo} 交付闭环`}
        subtitle={`${project.code} · ${workItem.title}`}
        actions={<StatusBadge status={workItem.status} label={workItemStatusLabel(workItem.status)} />}
      />
      {error && <ErrorBanner message={error} onRetry={() => void reload()} />}

      <div className="actions block">
        <button
          type="button"
          disabled={busy || !workItem.approvedSolutionVersionId}
          title={workItem.approvedSolutionVersionId ? undefined : '需要先批准方案'}
          onClick={() =>
            void run(async () => {
              const generated = await getHandoff(project.id, workItem.id)
              setHandoff(generated)
              setHandoffNote(`方案 v${generated.solutionVersion} · 基线 ${short(generated.baselineId)}`)
            })
          }
        >
          生成开发交接包
        </button>
        <button
          type="button"
          className="secondary"
          disabled={busy || !workItem.approvedSolutionVersionId}
          title={workItem.approvedSolutionVersionId ? undefined : '需要先批准方案'}
          onClick={() =>
            void run(async () => {
              const generated = await getHandoffPackage(project.id, workItem.id)
              setHandoffPackage(generated)
              setHandoffNote(
                `交接包 ZIP · 方案 v${generated.solutionVersion} · 基线 ${short(generated.baselineId)}`,
              )
            })
          }
        >
          生成交接包 ZIP
        </button>
        {handoffNote && <span className="hint">{handoffNote}</span>}
      </div>

      {handoffPackage && (
        <p className="state notice">
          交接包已生成：
          <a href={handoffPackage.downloadUrl}>下载 {handoffPackage.originalName}</a>
          {' '}· SHA-256 <span className="mono">{handoffPackage.sha256.slice(0, 16)}…</span>
        </p>
      )}

      {handoff && (
        <details className="panel" open>
          <summary>开发交接包（Markdown）</summary>
          <pre className="delivery-preview">{handoff.markdown}</pre>
        </details>
      )}

      <h2>实施记录</h2>
      {writer && workItem.approvedSolutionVersionId && (
        <form
          className="knowledge-form"
          aria-label="记录实施"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await recordExecution(project.id, workItem.id, {
                repositoryId: repoId,
                baselineId,
                toCommit: toCommit.trim(),
                summary: execSummary.trim() === '' ? null : execSummary.trim(),
                testsNote: testsNote.trim() === '' ? null : testsNote.trim(),
                completedTaskIds: completedTasks,
              })
              setToCommit('')
              setExecSummary('')
              setTestsNote('')
              setCompletedTasks([])
              await reload()
            })
          }}
        >
          <h3>回收实施证据（只读 Git）</h3>
          <div className="row">
            <select value={repoId} onChange={(event) => setRepoId(event.target.value)} aria-label="仓库">
              {activeRepos.map((repository) => (
                <option key={repository.id} value={repository.id}>
                  {repository.label}
                </option>
              ))}
            </select>
            <select
              value={baselineId}
              onChange={(event) => setBaselineId(event.target.value)}
              aria-label="基线"
            >
              {baselines.map((baseline, index) => (
                <option key={baseline.id} value={baseline.id}>
                  #{baselines.length - index} {baseline.note ?? ''}（{baseline.capturedAt}）
                </option>
              ))}
            </select>
            <input
              value={toCommit}
              onChange={(event) => setToCommit(event.target.value)}
              placeholder="实施后的 commit SHA"
              aria-label="目标 commit"
            />
          </div>
          <div className="row">
            <input
              value={execSummary}
              onChange={(event) => setExecSummary(event.target.value)}
              placeholder="实施摘要"
              aria-label="实施摘要"
            />
            <input
              value={testsNote}
              onChange={(event) => setTestsNote(event.target.value)}
              placeholder="测试/验证说明"
              aria-label="测试说明"
            />
          </div>
          <fieldset>
            <legend>已完成的任务</legend>
            {tasks.length === 0 && <p className="hint">批准方案没有任务。</p>}
            {tasks.map((task) => (
              <label key={task.id} className="hint">
                <input
                  type="checkbox"
                  checked={completedTasks.includes(task.id)}
                  onChange={(event) =>
                    setCompletedTasks((current) =>
                      event.target.checked
                        ? [...current, task.id]
                        : current.filter((entry) => entry !== task.id),
                    )
                  }
                />{' '}
                {task.title}
              </label>
            ))}
          </fieldset>
          <button type="submit" disabled={busy || !repoId || !baselineId || toCommit.trim() === ''}>
            记录实施
          </button>
        </form>
      )}
      {executions.records.length === 0 && (
        <EmptyState title="还没有实施记录" hint="先捕获基线，实施后回收 commit 范围与变更清单。" />
      )}
      {executions.records.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>仓库</th>
              <th>范围</th>
              <th>变更</th>
              <th>diverged</th>
              <th>摘要</th>
            </tr>
          </thead>
          <tbody>
            {executions.records.map((record) => {
              const changes = executions.changes.filter(
                (change) => change.executionRecordId === record.id,
              )
              const repository = repositories.find((entry) => entry.id === record.repositoryId)
              return (
                <tr key={record.id}>
                  <td>{repository?.label ?? short(record.repositoryId)}</td>
                  <td className="mono">
                    {short(record.fromCommit)} → {short(record.toCommit)}
                  </td>
                  <td>{changes.length}</td>
                  <td>{record.diverged ? '是' : '否'}</td>
                  <td>{record.summary ?? '—'}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}

      {writer && executions.records.length > 0 && (
        <form
          className="knowledge-form"
          aria-label="记录偏差"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await addDeviation(project.id, workItem.id, deviationRecordId, {
                deviationType,
                targetType: null,
                targetId: null,
                description: deviationDescription.trim(),
              })
              setDeviationDescription('')
              await reload()
            })
          }}
        >
          <h3>偏差分类（人工）</h3>
          <div className="row">
            <select
              value={deviationRecordId}
              onChange={(event) => setDeviationRecordId(event.target.value)}
              aria-label="实施记录"
            >
              {executions.records.map((record) => (
                <option key={record.id} value={record.id}>
                  {short(record.fromCommit)} → {short(record.toCommit)}
                </option>
              ))}
            </select>
            <select
              value={deviationType}
              onChange={(event) => setDeviationType(event.target.value)}
              aria-label="偏差类型"
            >
              <option value="conform">符合设计</option>
              <option value="extra">额外实现</option>
              <option value="missing">未实现</option>
              <option value="changed">实现变更</option>
            </select>
            <input
              value={deviationDescription}
              onChange={(event) => setDeviationDescription(event.target.value)}
              placeholder="偏差说明"
              aria-label="偏差说明"
            />
            <button type="submit" disabled={busy || deviationDescription.trim() === ''}>
              记录偏差
            </button>
          </div>
        </form>
      )}
      {executions.deviations.length > 0 && (
        <ul className="refs">
          {executions.deviations.map((deviation) => (
            <li key={deviation.id}>
              <span className="code">{deviation.deviationType}</span> {deviation.description}
            </li>
          ))}
        </ul>
      )}

      <h2>验收矩阵</h2>
      {!workItem.approvedSolutionVersionId && (
        <p className="hint">需要先批准方案才能生成验收矩阵。</p>
      )}
      {analysis && (
        <>
          <p className="hint">
            已覆盖 {analysis.covered} · 部分 {analysis.partial} · 未验证 {analysis.unverified}
          </p>
          <table className="table">
            <thead>
              <tr>
                <th>验收标准</th>
                <th>覆盖</th>
                <th>任务</th>
              </tr>
            </thead>
            <tbody>
              {analysis.matrix.map((row) => (
                <tr key={row.criterionId}>
                  <td>{row.criterionText}</td>
                  <td>{row.coverage}</td>
                  <td>
                    {row.doneTasks}/{row.totalTasks}
                  </td>
                </tr>
              ))}
              {analysis.matrix.length === 0 && (
                <tr>
                  <td colSpan={3} className="hint">
                    批准方案里没有验收标准。
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          {analysis.missingTasks.length > 0 && (
            <p className="hint">
              未完成任务候选：{analysis.missingTasks.map((task) => task.title).join('；')}
            </p>
          )}
        </>
      )}

      <h2>交付快照（As-built）</h2>
      {writer && workItem.approvedSolutionVersionId && (
        <form
          className="knowledge-form"
          aria-label="创建交付快照"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await createDelivery(project.id, workItem.id, snapshotTitle.trim())
              setSnapshotTitle('')
              await reload()
            })
          }}
        >
          <div className="row">
            <input
              value={snapshotTitle}
              onChange={(event) => setSnapshotTitle(event.target.value)}
              placeholder="快照标题（如 As-built v1）"
              aria-label="快照标题"
            />
            <button type="submit" disabled={busy || snapshotTitle.trim() === ''}>
              生成快照草稿
            </button>
          </div>
        </form>
      )}
      {deliveries.length === 0 && <p className="hint">还没有交付快照。</p>}
      <ul className="documents">
        {deliveries.map((snapshot) => (
          <li key={snapshot.id} className="document">
            <div className="document-head">
              <span className="role">{solutionStatusLabel(snapshot.status)}</span>
              <strong>{snapshot.title}</strong>
              <span className="hint">{snapshot.createdAt}</span>
              {snapshot.status === 'draft' &&
                canApproveSolution('in_review', role, snapshot.createdBy === userId) && (
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() =>
                      void run(async () => {
                        await approveDelivery(project.id, workItem.id, snapshot.id)
                        await reload()
                      })
                    }
                  >
                    批准快照
                  </button>
                )}
            </div>
            <details>
              <summary>查看 As-built 内容</summary>
              <pre className="delivery-preview">{snapshot.bodyMarkdown}</pre>
            </details>
            {writer && snapshot.status === 'approved' && (
              <form
                className="row"
                aria-label="创建知识提议"
                onSubmit={(event) => {
                  event.preventDefault()
                  void run(async () => {
                    await createKnowledgeProposal(project.id, workItem.id, {
                      snapshotId: snapshot.id,
                      title: proposalTitle.trim(),
                    })
                    setProposalTitle('')
                    await reload()
                  })
                }}
              >
                <input
                  value={proposalTitle}
                  onChange={(event) => setProposalTitle(event.target.value)}
                  placeholder="知识提议标题"
                  aria-label="知识提议标题"
                />
                <button type="submit" disabled={busy || proposalTitle.trim() === ''}>
                  创建知识提议
                </button>
              </form>
            )}
          </li>
        ))}
      </ul>

      <h2>知识更新提议</h2>
      {proposals.length === 0 && <p className="hint">还没有知识更新提议。</p>}
      <ul className="documents">
        {proposals.map((proposal) => (
          <li key={proposal.id} className="document">
            <div className="document-head">
              <span className="role">{proposal.status}</span>
              <strong>{proposal.title}</strong>
              <span className="hint">{proposal.createdAt}</span>
              {proposal.status === 'draft' && decider && proposal.createdBy !== userId && (
                <span className="actions">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() =>
                      void run(async () => {
                        await acceptKnowledgeProposal(project.id, workItem.id, proposal.id)
                        await reload()
                      })
                    }
                  >
                    接受并生成知识草稿
                  </button>
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() =>
                      void run(async () => {
                        await rejectKnowledgeProposal(project.id, workItem.id, proposal.id)
                        await reload()
                      })
                    }
                  >
                    拒绝
                  </button>
                </span>
              )}
            </div>
            {proposal.knowledgeDocumentId && (
              <p className="hint">已生成知识草稿（待人工审核后发布）：{proposal.knowledgeDocumentId}</p>
            )}
          </li>
        ))}
      </ul>

      {item.item.approvedSolutionVersionId && (
        <p className="hint">
          代码仓库仅保存路径与 commit 元数据，源码不进入知识库或检索索引。
        </p>
      )}
    </>
  )
}
