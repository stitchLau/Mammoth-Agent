import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState, ErrorBanner, LoadingState, PageHeader, StatusBadge } from '../components/ui'
import { Pagination } from '../components/Pagination'
import { createWorkItem, listWorkItems, type WorkItem } from '../lib/api'
import { workItemPriorityLabel, workItemStatusLabel } from '../lib/format'
import { clampPage, pageSlice } from '../lib/pagination'
import { canEditRequirement } from '../lib/permissions'
import { useProject } from './ProjectLayout'

const PAGE_SIZE = 10

const STATUS_FILTERS = [
  { value: '', label: '全部状态' },
  { value: 'draft', label: '草稿' },
  { value: 'collecting', label: '收集中' },
  { value: 'ready_for_analysis', label: '待分析' },
  { value: 'analyzing', label: '分析中' },
  { value: 'solution_review', label: '方案评审' },
  { value: 'approved', label: '已批准' },
  { value: 'on_hold', label: '挂起' },
  { value: 'blocked', label: '阻塞' },
]

/** Requirement pool of a project (M2 S1 UI). */
export default function RequirementsPage() {
  const { project, role } = useProject()
  const [items, setItems] = useState<WorkItem[] | null>(null)
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(1)
  const [title, setTitle] = useState('')
  const [summary, setSummary] = useState('')
  const [priority, setPriority] = useState('normal')
  const [sourceNote, setSourceNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setItems(await listWorkItems(project.id, status === '' ? undefined : status))
  }, [project.id, status])

  useEffect(() => {
    setItems(null)
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

  const writable = canEditRequirement(role)
  const totalPages = clampPage(page, items?.length ?? 0, PAGE_SIZE)

  return (
    <>
      <PageHeader
        title="需求与方案"
        subtitle="需求收集 → 就绪确认 → 方案分析 → 评审批准；证据只追加、方案版本不可覆盖"
        actions={
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value)
              setPage(1)
            }}
            aria-label="按需求状态筛选"
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
      {items === null && <LoadingState label="正在加载需求…" />}
      {items !== null && items.length === 0 && (
        <EmptyState
          title={status === '' ? '还没有需求工作项' : '该状态下没有需求'}
          hint="从一句客户描述开始建立需求，随后分批追加证据并做就绪检查。"
        />
      )}
      {items !== null && items.length > 0 && (
        <ul className="documents">
          {pageSlice(items, totalPages, PAGE_SIZE).map((item) => (
            <li key={item.id} className="document">
              <div className="document-head">
                <span className="code">{item.itemNo}</span>
                <StatusBadge status={item.status} label={workItemStatusLabel(item.status)} />
                <Link to={`/projects/${project.id}/requirements/${item.id}`}>
                  <strong>{item.title}</strong>
                </Link>
                <span className="hint">优先级 {workItemPriorityLabel(item.priority)}</span>
              </div>
              <p>{item.summary ?? '（无摘要）'}</p>
            </li>
          ))}
        </ul>
      )}
      {items !== null && items.length > PAGE_SIZE && (
        <Pagination page={totalPages} total={items.length} pageSize={PAGE_SIZE} onPageChange={setPage} />
      )}

      {writable && (
        <form
          className="knowledge-form"
          aria-label="新建需求"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await createWorkItem(project.id, {
                title: title.trim(),
                summary: summary.trim() === '' ? null : summary.trim(),
                priority,
                sourceNote: sourceNote.trim() === '' ? null : sourceNote.trim(),
              })
              setTitle('')
              setSummary('')
              setSourceNote('')
              setPriority('normal')
              await reload()
            })
          }}
        >
          <h3>新建需求</h3>
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="需求标题" />
          <textarea
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
            rows={3}
            placeholder="客户/业务方的原始描述（可以很模糊）"
          />
          <div className="row">
            <select value={priority} onChange={(e) => setPriority(e.target.value)} aria-label="优先级">
              <option value="low">低</option>
              <option value="normal">普通</option>
              <option value="high">高</option>
              <option value="critical">紧急</option>
            </select>
            <input
              value={sourceNote}
              onChange={(e) => setSourceNote(e.target.value)}
              placeholder="来源（会议/邮件/工单，可选）"
            />
            <button type="submit" disabled={busy || title.trim() === ''}>
              创建需求
            </button>
          </div>
        </form>
      )}
    </>
  )
}
