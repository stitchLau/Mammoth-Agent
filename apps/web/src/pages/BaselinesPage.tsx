import { useCallback, useEffect, useState } from 'react'
import { EmptyState, ErrorBanner, LoadingState, PageHeader } from '../components/ui'
import {
  captureBaseline,
  deactivateRepository,
  listBaselines,
  listRepositories,
  registerRepository,
  type Baseline,
  type Repository,
} from '../lib/api'
import { useProject } from './ProjectLayout'

const short = (sha: string): string => sha.slice(0, 8)

/** Project code repositories and immutable baselines (ADR-0007, M3 S2). */
export default function BaselinesPage() {
  const { project, role } = useProject()
  const [repositories, setRepositories] = useState<Repository[] | null>(null)
  const [baselines, setBaselines] = useState<Baseline[] | null>(null)
  const [label, setLabel] = useState('')
  const [path, setPath] = useState('')
  const [branch, setBranch] = useState('')
  const [note, setNote] = useState('')
  const [allowDirty, setAllowDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const [repoList, baselineList] = await Promise.all([
      listRepositories(project.id),
      listBaselines(project.id),
    ])
    setRepositories(repoList)
    setBaselines(baselineList)
  }, [project.id])

  useEffect(() => {
    setRepositories(null)
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

  const isOwner = role === 'owner'
  const canCapture = role === 'owner' || role === 'editor'

  return (
    <>
      <PageHeader
        title="代码基线"
        subtitle="只读注册本地仓库并捕获不可变基线；Mammoth 不 clone/fetch/push，也不复制源码"
      />
      {error && <ErrorBanner message={error} onRetry={() => void reload()} />}

      <h2>已注册仓库</h2>
      {repositories === null && <LoadingState label="正在加载仓库…" />}
      {repositories !== null && repositories.length === 0 && (
        <EmptyState title="还没有注册代码仓库" hint="由项目负责人填写本地仓库绝对路径完成注册。" />
      )}
      {repositories !== null && repositories.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>标识</th>
              <th>路径</th>
              <th>远程（脱敏）</th>
              <th>状态</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {repositories.map((repository) => (
              <tr key={repository.id}>
                <td>{repository.label}</td>
                <td className="mono">{repository.absolutePath}</td>
                <td className="mono">{repository.remoteUrl ?? '—'}</td>
                <td>{repository.status}</td>
                <td>
                  {isOwner && repository.status === 'active' && (
                    <button
                      type="button"
                      className="secondary"
                      disabled={busy}
                      onClick={() =>
                        void run(async () => {
                          await deactivateRepository(project.id, repository.id)
                          await reload()
                        })
                      }
                    >
                      停用
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {isOwner && (
        <form
          className="knowledge-form"
          aria-label="注册仓库"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await registerRepository(project.id, {
                label: label.trim(),
                path: path.trim(),
                defaultBranch: branch.trim() === '' ? null : branch.trim(),
              })
              setLabel('')
              setPath('')
              setBranch('')
              await reload()
            })
          }}
        >
          <h3>注册本地仓库（owner）</h3>
          <div className="row">
            <input
              value={label}
              onChange={(event) => setLabel(event.target.value)}
              placeholder="仓库标识（如 backend）"
              aria-label="仓库标识"
            />
            <input
              value={path}
              onChange={(event) => setPath(event.target.value)}
              placeholder="本地绝对路径，如 E:\work\acme-mes"
              aria-label="仓库路径"
            />
            <input
              value={branch}
              onChange={(event) => setBranch(event.target.value)}
              placeholder="默认分支（可选）"
              aria-label="默认分支"
            />
            <button type="submit" disabled={busy || label.trim() === '' || path.trim() === ''}>
              注册
            </button>
          </div>
        </form>
      )}

      <h2>代码基线</h2>
      {canCapture && repositories !== null && repositories.length > 0 && (
        <form
          className="knowledge-form"
          aria-label="捕获基线"
          onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await captureBaseline(project.id, {
                note: note.trim() === '' ? null : note.trim(),
                allowDirty: allowDirty && isOwner,
              })
              setNote('')
              setAllowDirty(false)
              await reload()
            })
          }}
        >
          <h3>捕获当前基线</h3>
          <div className="row">
            <input
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="基线说明（可选）"
              aria-label="基线说明"
            />
            <button type="submit" disabled={busy}>
              捕获基线
            </button>
            {isOwner && (
              <label className="hint">
                <input
                  type="checkbox"
                  checked={allowDirty}
                  onChange={(event) => setAllowDirty(event.target.checked)}
                />{' '}
                允许脏工作树（不可复现，仅 owner）
              </label>
            )}
          </div>
        </form>
      )}
      {baselines === null && <LoadingState label="正在加载基线…" />}
      {baselines !== null && baselines.length === 0 && (
        <EmptyState title="还没有基线" hint="实施前捕获一次基线，实施回收时会以它为 from commit。" />
      )}
      {baselines !== null && baselines.length > 0 && (
        <ul className="documents">
          {baselines.map((baseline, index) => (
            <li key={baseline.id} className="document">
              <div className="document-head">
                <span className="code">#{baselines.length - index}</span>
                <strong>{baseline.note ?? '（无说明）'}</strong>
                <span className="hint">{baseline.capturedAt}</span>
                {index === 0 && <span className="notice">当前基线</span>}
              </div>
              <ul className="refs">
                {baseline.repos.map((entry) => {
                  const repository = repositories?.find((item) => item.id === entry.repositoryId)
                  return (
                    <li key={entry.id}>
                      <span className="mono">{repository?.label ?? entry.repositoryId.slice(0, 8)}</span> ·{' '}
                      <span className="mono">{short(entry.commitSha)}</span>
                      {entry.branch ? ` · ${entry.branch}` : ' · detached'}
                      {entry.dirty ? ' · 脏工作树' : ''}
                      {entry.worktree ? ' · worktree' : ''}
                    </li>
                  )
                })}
              </ul>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
