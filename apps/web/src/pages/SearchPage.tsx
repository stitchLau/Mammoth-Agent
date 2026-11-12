import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ErrorBanner, PageHeader } from '../components/ui'
import { useProject } from './ProjectLayout'
import { searchKnowledge, type KnowledgeHit } from '../lib/api'

export default function SearchPage() {
  const { project } = useProject()
  const [query, setQuery] = useState('')
  const [hits, setHits] = useState<KnowledgeHit[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function search() {
    if (query.trim() === '') {
      return
    }
    setBusy(true)
    setError(null)
    try {
      setHits(await searchKnowledge(project.id, query.trim()))
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader
        title="已验证知识检索"
        subtitle="仅返回本项目已验证的知识；每条结果都带来源引用，可回到原始资料核对"
      />
      {error && <ErrorBanner message={error} onRetry={() => void search()} />}
      <div className="row">
        <input
          aria-label="检索问题"
          placeholder="例如：条码重复怎么处理"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              void search()
            }
          }}
        />
        <button type="button" disabled={busy || query.trim() === ''} onClick={() => void search()}>
          {busy ? '检索中…' : '检索'}
        </button>
      </div>
      {hits !== null && hits.length === 0 && <p className="state">没有命中已验证知识。</p>}
      {hits !== null && hits.length > 0 && (
        <ol className="hits">
          {hits.map((hit) => (
            <li key={hit.chunkId}>
              <Link to={`/projects/${project.id}/knowledge/${hit.documentId}`}>
                <strong>{hit.title}</strong>
              </Link>
              <span className="mono"> chunk#{hit.ordinal}</span>
              <p>{hit.text}</p>
              <p className="hint">引用 {hit.sourceRefCount} 项 · 文档 {hit.documentId.slice(0, 8)}…</p>
            </li>
          ))}
        </ol>
      )}
    </>
  )
}
