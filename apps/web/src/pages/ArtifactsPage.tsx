import { useCallback, useEffect, useState } from 'react'
import { EmptyState, ErrorBanner, LoadingState, PageHeader } from '../components/ui'
import { Pagination } from '../components/Pagination'
import { useProject } from './ProjectLayout'
import { listArtifacts, uploadArtifact, type Artifact } from '../lib/api'
import { formatBytes } from '../lib/format'
import { clampPage, pageSlice } from '../lib/pagination'
import { canWriteArtifacts } from '../lib/permissions'

const PAGE_SIZE = 10

export default function ArtifactsPage() {
  const { project, role } = useProject()
  const [artifacts, setArtifacts] = useState<Artifact[] | null>(null)
  const [page, setPage] = useState(1)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setArtifacts(await listArtifacts(project.id))
  }, [project.id])

  useEffect(() => {
    setArtifacts(null)
    reload().catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
  }, [reload])

  async function handleFiles(files: File[]) {
    if (files.length === 0) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      for (const file of files) {
        await uploadArtifact(project.id, file)
      }
      await reload()
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader
        title="项目资料"
        subtitle="原始资料不可变，按内容哈希去重；知识文档必须引用这里的资料"
      />
      {error && <ErrorBanner message={error} onRetry={() => void reload()} />}
      {canWriteArtifacts(role) && (
        <label className="upload">
          <span>{busy ? '上传中…' : '上传资料（可多选）'}</span>
          <input
            type="file"
            multiple
            disabled={busy}
            onChange={(event) => {
              // Copy before clearing: the FileList is live and becomes empty after value reset.
              const files = event.target.files ? Array.from(event.target.files) : []
              event.target.value = ''
              void handleFiles(files)
            }}
          />
        </label>
      )}
      {artifacts === null && <LoadingState label="正在加载资料…" />}
      {artifacts !== null && artifacts.length === 0 && (
        <EmptyState
          title="还没有资料"
          hint={canWriteArtifacts(role) ? '上传第一份原始文件作为知识来源。' : '请联系项目负责人或编辑上传资料。'}
        />
      )}
      {artifacts !== null && artifacts.length > 0 && (
        <>
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
              {pageSlice(artifacts, page, PAGE_SIZE).map((artifact) => (
                <tr key={artifact.id}>
                  <td>{artifact.originalName}</td>
                  <td>{formatBytes(artifact.sizeBytes)}</td>
                  <td className="mono">{artifact.contentHash.slice(0, 12)}</td>
                  <td className="mono">{artifact.createdAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination
            page={clampPage(page, artifacts.length, PAGE_SIZE)}
            total={artifacts.length}
            pageSize={PAGE_SIZE}
            onPageChange={setPage}
          />
        </>
      )}
    </>
  )
}
