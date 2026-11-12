import { useEffect, useState } from 'react'
import { formatProjectCode } from './lib/format'

interface HealthResponse {
  status: string
  service: string
  time: string
}

type HealthState =
  | { kind: 'loading' }
  | { kind: 'ok'; health: HealthResponse }
  | { kind: 'error'; message: string }

/**
 * M1 engineering-skeleton shell. Real project/knowledge screens arrive in later tasks; this page
 * only proves the SPA can reach the Domain Service and renders loading/empty/error states.
 */
export default function App() {
  const [health, setHealth] = useState<HealthState>({ kind: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/health', { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }
        return (await response.json()) as HealthResponse
      })
      .then((body) => setHealth({ kind: 'ok', health: body }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setHealth({ kind: 'error', message: error instanceof Error ? error.message : String(error) })
        }
      })
    return () => controller.abort()
  }, [])

  return (
    <main className="shell">
      <header>
        <h1>Mammoth Agent</h1>
        <p className="subtitle">项目知识底座 · M1 工程骨架</p>
      </header>

      <section aria-label="服务状态">
        <h2>Domain Service</h2>
        {health.kind === 'loading' && <p className="state">正在连接后端…</p>}
        {health.kind === 'error' && (
          <p className="state error">
            无法连接后端（{health.message}）。请确认 <code>services/domain</code> 已启动。
          </p>
        )}
        {health.kind === 'ok' && (
          <dl>
            <dt>状态</dt>
            <dd className="ok">{health.health.status}</dd>
            <dt>服务</dt>
            <dd>{health.health.service}</dd>
            <dt>时间</dt>
            <dd>{health.health.time}</dd>
          </dl>
        )}
      </section>

      <section aria-label="示例项目">
        <h2>示例项目编码</h2>
        <p className="code">{formatProjectCode('ACME', 'MES', 2026)}</p>
        <p className="hint">客户 + 产品 + 年份的默认编码规则；真实项目创建在后续任务中实现。</p>
      </section>
    </main>
  )
}
