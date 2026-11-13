import { useCallback, useEffect, useState } from 'react'
import { BackLink, EmptyState, ErrorBanner, LoadingState, PageHeader } from '../components/ui'
import {
  applyImport,
  createExternalSource,
  getSolutionVersion,
  listExternalRecords,
  listExternalSources,
  listImportBatches,
  listWorkItems,
  mapExternalRecord,
  previewImport,
  rejectExternalRecord,
  type AcceptanceCriterion,
  type ExternalRecordView,
  type ExternalSourceView,
  type ImportBatchView,
  type ImportPreviewView,
  type ImportResultView,
  type WorkItem,
} from '../lib/api'
import { canEditRequirement } from '../lib/permissions'
import { useProject } from './ProjectLayout'

/** M4 S3: read-only Jira/CSV import and explicit evidence mapping. */
export default function ImportsPage() {
  const { project, role } = useProject()
  const writer = canEditRequirement(role)
  const owner = role === 'owner'

  const [sources, setSources] = useState<ExternalSourceView[]>([])
  const [batches, setBatches] = useState<ImportBatchView[]>([])
  const [records, setRecords] = useState<ExternalRecordView[]>([])
  const [workItems, setWorkItems] = useState<WorkItem[]>([])
  const [criteria, setCriteria] = useState<AcceptanceCriterion[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [sourceType, setSourceType] = useState('jira')
  const [sourceName, setSourceName] = useState('Jira')
  const [sourceBaseUrl, setSourceBaseUrl] = useState('')
  const [sourceId, setSourceId] = useState('')
  const [mappingProfile, setMappingProfile] = useState('jira')
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreviewView | null>(null)
  const [result, setResult] = useState<ImportResultView | null>(null)

  const [selectedRecordId, setSelectedRecordId] = useState('')
  const [mapWorkItemId, setMapWorkItemId] = useState('')
  const [mapCriterionId, setMapCriterionId] = useState('')
  const [rejectNote, setRejectNote] = useState('')

  const reload = useCallback(async () => {
    const [sourceList, batchList, recordList, itemList] = await Promise.all([
      listExternalSources(project.id),
      listImportBatches(project.id),
      listExternalRecords(project.id, 'unmapped'),
      listWorkItems(project.id),
    ])
    setSources(sourceList)
    setBatches(batchList)
    setRecords(recordList)
    setWorkItems(itemList)
    setSourceId((current) => current || sourceList[0]?.id || '')
  }, [project.id])

  useEffect(() => {
    setLoading(true)
    reload()
      .catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
      .finally(() => setLoading(false))
  }, [reload])

  useEffect(() => {
    const item = workItems.find((entry) => entry.id === mapWorkItemId)
    if (!item?.approvedSolutionVersionId) {
      setCriteria([])
      setMapCriterionId('')
      return
    }
    let cancelled = false
    getSolutionVersion(project.id, mapWorkItemId, item.approvedSolutionVersionId)
      .then((solution) => {
        if (!cancelled) {
          setCriteria(solution.acceptanceCriteria)
        }
      })
      .catch(() => {
        if (!cancelled) setCriteria([])
      })
    return () => {
      cancelled = true
    }
  }, [mapWorkItemId, project.id, workItems])

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

  if (loading) return <LoadingState label="正在加载外部导入…" />

  return (
    <div>
      <BackLink to={`/projects/${project.id}/requirements`} label="返回需求与方案" />
      <PageHeader
        title="外部导入"
        subtitle="Jira/CSV 只读导入，人工确认后才成为需求证据；原始文件保留可追溯。"
      />
      {error && <ErrorBanner message={error} />}

      <section className="card">
        <h2>外部来源</h2>
        {owner && (
          <form
            className="knowledge-form"
            onSubmit={(event) => {
              event.preventDefault()
              void run(async () => {
                await createExternalSource(project.id, {
                  systemType: sourceType,
                  name: sourceName.trim(),
                  baseUrl: sourceBaseUrl.trim() || null,
                })
                setSourceBaseUrl('')
                await reload()
              })
            }}
          >
            <div className="row">
              <select
                value={sourceType}
                onChange={(event) => setSourceType(event.target.value)}
                aria-label="来源类型"
              >
                <option value="jira">Jira</option>
                <option value="csm">CSM</option>
                <option value="generic">通用</option>
              </select>
              <input
                value={sourceName}
                onChange={(event) => setSourceName(event.target.value)}
                placeholder="来源名称"
                aria-label="来源名称"
              />
              <input
                value={sourceBaseUrl}
                onChange={(event) => setSourceBaseUrl(event.target.value)}
                placeholder="基础 URL（可选）"
                aria-label="基础 URL"
              />
              <button type="submit" disabled={busy || sourceName.trim() === ''}>
                创建来源
              </button>
            </div>
            <p className="hint">不要在此填写账号、令牌、密码或 webhook。</p>
          </form>
        )}
        {sources.length === 0 ? (
          <EmptyState title="还没有外部来源" hint={owner ? '先创建一个来源。' : '请联系项目负责人创建来源。'} />
        ) : (
          <ul className="refs">
            {sources.map((source) => (
              <li key={source.id}>
                <span className="code">{source.systemType}</span> {source.name}
                {source.baseUrl && <span className="hint"> · {source.baseUrl}</span>}
              </li>
            ))}
          </ul>
        )}
      </section>

      {writer && (
        <section className="card">
          <h2>上传与预检</h2>
          <p className="hint">
            支持 Jira 中文 CSV（GBK/UTF-8）和 JSON；预检不落库、不生成文件，确认后再应用。
          </p>
          <div className="row">
            <select
              value={sourceId}
              onChange={(event) => setSourceId(event.target.value)}
              aria-label="导入来源"
            >
              <option value="">选择来源</option>
              {sources.map((source) => (
                <option key={source.id} value={source.id}>
                  {source.name}
                </option>
              ))}
            </select>
            <select
              value={mappingProfile}
              onChange={(event) => setMappingProfile(event.target.value)}
              aria-label="映射配置"
            >
              <option value="jira">Jira 中文导出</option>
              <option value="generic">通用字段</option>
            </select>
            <input
              type="file"
              accept=".csv,.json,text/csv,application/json"
              aria-label="导入文件"
              onChange={(event) => {
                setFile(event.target.files?.[0] ?? null)
                setPreview(null)
                setResult(null)
              }}
            />
          </div>
          <div className="row">
            <button
              type="button"
              className="secondary"
              disabled={busy || !file || !sourceId}
              onClick={() =>
                void run(async () => {
                  const report = await previewImport(project.id, file!, sourceId, mappingProfile)
                  setPreview(report)
                  setResult(null)
                })
              }
            >
              预检
            </button>
            <button
              type="button"
              disabled={busy || !file || !sourceId}
              onClick={() =>
                void run(async () => {
                  const applied = await applyImport(project.id, file!, sourceId, mappingProfile)
                  setResult(applied)
                  setPreview(null)
                  await reload()
                })
              }
            >
              应用导入
            </button>
          </div>

          {preview && (
            <div>
              <p>
                预检：{preview.rowCount} 行，接受 {preview.acceptedCount}，拒绝{' '}
                {preview.rejectedCount}
              </p>
              {preview.errors.length > 0 && (
                <ul className="refs">
                  {preview.errors.slice(0, 20).map((rowError) => (
                    <li key={`${rowError.row}-${rowError.message}`}>
                      第 {rowError.row} 行：{rowError.message}
                    </li>
                  ))}
                </ul>
              )}
              <table className="table">
                <thead>
                  <tr>
                    <th>外部 key</th>
                    <th>标题</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.sample.map((record) => (
                    <tr key={`${record.externalId}-${record.externalVersion}`}>
                      <td className="mono">{record.externalKey ?? record.externalId}</td>
                      <td>{record.title}</td>
                      <td>{record.status}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {result && (
            <p>
              已应用批次 {result.batch.id.slice(0, 8)}：接受 {result.accepted}，拒绝{' '}
              {result.rejected}
              {result.duplicate && '（同一文件已导入，复用原批次）'}
            </p>
          )}
        </section>
      )}

      <section className="card">
        <h2>批次历史</h2>
        {batches.length === 0 ? (
          <EmptyState title="还没有导入批次" />
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>批次</th>
                <th>映射</th>
                <th>行/接受/拒绝</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              {batches.map((batch) => (
                <tr key={batch.id}>
                  <td className="mono">{batch.id.slice(0, 8)}</td>
                  <td>{batch.mappingProfile}</td>
                  <td>
                    {batch.rowCount}/{batch.acceptedCount}/{batch.rejectedCount}
                  </td>
                  <td>{batch.createdAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="card">
        <h2>待映射记录</h2>
        {records.length === 0 ? (
          <EmptyState title="没有待映射记录" hint="可先在上方导入 Jira CSV。" />
        ) : (
          <>
            <table className="table">
              <thead>
                <tr>
                  <th>外部 key</th>
                  <th>标题</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((record) => (
                  <tr key={record.id}>
                    <td className="mono">{record.externalKey ?? record.externalId}</td>
                    <td>{record.title}</td>
                    <td>{record.status}</td>
                    <td>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => {
                          setSelectedRecordId(record.id)
                          setRejectNote('')
                        }}
                      >
                        选择
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {selectedRecordId && writer && (
              <form
                className="knowledge-form"
                onSubmit={(event) => {
                  event.preventDefault()
                  void run(async () => {
                    await mapExternalRecord(project.id, selectedRecordId, {
                      workItemId: mapWorkItemId,
                      criterionId: mapCriterionId || null,
                    })
                    setSelectedRecordId('')
                    setMapCriterionId('')
                    await reload()
                  })
                }}
              >
                <h3>映射到需求证据</h3>
                <div className="row">
                  <select
                    value={mapWorkItemId}
                    onChange={(event) => {
                      setMapWorkItemId(event.target.value)
                      setMapCriterionId('')
                    }}
                    aria-label="映射需求"
                  >
                    <option value="">选择需求</option>
                    {workItems.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.itemNo} {item.title}
                      </option>
                    ))}
                  </select>
                  <select
                    value={mapCriterionId}
                    onChange={(event) => setMapCriterionId(event.target.value)}
                    aria-label="映射验收标准"
                  >
                    <option value="">不绑定验收标准</option>
                    {criteria.map((criterion) => (
                      <option key={criterion.id} value={criterion.id}>
                        {criterion.text}
                      </option>
                    ))}
                  </select>
                  <button type="submit" disabled={busy || !mapWorkItemId}>
                    映射
                  </button>
                </div>
                <p className="hint">需求必须处于可编辑状态；冻结后不能新增证据。</p>
              </form>
            )}
            {selectedRecordId && writer && (
              <form
                className="knowledge-form"
                onSubmit={(event) => {
                  event.preventDefault()
                  void run(async () => {
                    await rejectExternalRecord(project.id, selectedRecordId, rejectNote.trim())
                    setSelectedRecordId('')
                    setRejectNote('')
                    await reload()
                  })
                }}
              >
                <div className="row">
                  <input
                    value={rejectNote}
                    onChange={(event) => setRejectNote(event.target.value)}
                    placeholder="拒绝原因"
                    aria-label="拒绝原因"
                  />
                  <button type="submit" className="secondary" disabled={busy}>
                    拒绝该记录
                  </button>
                </div>
              </form>
            )}
          </>
        )}
      </section>
    </div>
  )
}
