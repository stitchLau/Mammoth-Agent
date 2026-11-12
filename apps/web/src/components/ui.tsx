import { Link } from 'react-router-dom'
import { knowledgeStatusLabel } from '../lib/format'
import { roleLabel } from '../lib/permissions'

export function StatusBadge(props: { status: string }) {
  return (
    <span className={`status status-${props.status}`} title={props.status}>
      {knowledgeStatusLabel(props.status)}
    </span>
  )
}

export function RoleBadge(props: { role: string }) {
  return <span className={`role role-${props.role}`}>{roleLabel(props.role)}</span>
}

export function LoadingState(props: { label?: string }) {
  return <p className="state muted">{props.label ?? '加载中…'}</p>
}

export function EmptyState(props: { title: string; hint?: string; action?: React.ReactNode }) {
  return (
    <div className="empty">
      <p>{props.title}</p>
      {props.hint && <p className="hint">{props.hint}</p>}
      {props.action}
    </div>
  )
}

export function ErrorBanner(props: { message: string; onRetry?: () => void }) {
  return (
    <div className="error-banner" role="alert">
      <span>{props.message}</span>
      {props.onRetry && (
        <button type="button" className="secondary" onClick={props.onRetry}>
          重试
        </button>
      )}
    </div>
  )
}

export function PageHeader(props: { title: string; subtitle?: string; actions?: React.ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1>{props.title}</h1>
        {props.subtitle && <p className="hint">{props.subtitle}</p>}
      </div>
      {props.actions && <div className="actions">{props.actions}</div>}
    </div>
  )
}

export function BackLink(props: { to: string; label: string }) {
  return (
    <Link className="back-link" to={props.to}>
      ← {props.label}
    </Link>
  )
}
