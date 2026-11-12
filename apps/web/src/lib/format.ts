/**
 * Default project code: uppercase customer + product + year, e.g. ACME + MES + 2026 -> ACME-MES-2026.
 * Kept dependency-free so the rule can be unit-tested before the domain API exists.
 */
export function formatProjectCode(customer: string, product: string, year: number): string {
  const normalized = (value: string): string =>
    value
      .trim()
      .replace(/\s+/g, '-')
      .toUpperCase()
  return [normalized(customer), normalized(product), String(year)].filter((part) => part.length > 0).join('-')
}

/** Human-readable byte size for the artifact list. */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return '-'
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${value.toFixed(1)} ${units[unit]}`
}

/** Chinese label for the M1 knowledge lifecycle states. */
export function knowledgeStatusLabel(status: string): string {
  switch (status) {
    case 'draft':
      return '草稿'
    case 'needs_review':
      return '待审核'
    case 'verified':
      return '已验证'
    default:
      return status
  }
}

/** Only drafts can be submitted for review (mirrors the server rule). */
export function canSubmitReview(status: string): boolean {
  return status === 'draft'
}

/** Only needs_review documents can be verified by a human reviewer. */
export function canVerify(status: string): boolean {
  return status === 'needs_review'
}

/** M2 requirement lifecycle labels (docs/07 §2, M2 plan §4.2). */
export function workItemStatusLabel(status: string): string {
  switch (status) {
    case 'draft':
      return '草稿'
    case 'collecting':
      return '收集中'
    case 'ready_for_analysis':
      return '待分析'
    case 'analyzing':
      return '分析中'
    case 'solution_review':
      return '方案评审'
    case 'approved':
      return '已批准'
    case 'on_hold':
      return '挂起'
    case 'blocked':
      return '阻塞'
    case 'withdrawn':
      return '已撤回'
    case 'rejected':
      return '已拒绝'
    default:
      return status
  }
}

export function workItemPriorityLabel(priority: string): string {
  switch (priority) {
    case 'low':
      return '低'
    case 'normal':
      return '普通'
    case 'high':
      return '高'
    case 'critical':
      return '紧急'
    default:
      return priority
  }
}

export function solutionStatusLabel(status: string): string {
  switch (status) {
    case 'draft':
      return '草稿'
    case 'in_review':
      return '评审中'
    case 'approved':
      return '已批准'
    case 'superseded':
      return '已被替代'
    default:
      return status
  }
}

export const READINESS_DIMENSIONS: { key: string; label: string; critical: boolean }[] = [
  { key: 'background', label: '背景', critical: true },
  { key: 'goals', label: '目标', critical: true },
  { key: 'current_process', label: '当前流程', critical: false },
  { key: 'target_process', label: '目标流程', critical: true },
  { key: 'business_rules', label: '业务规则', critical: false },
  { key: 'roles_and_scope', label: '角色与范围', critical: false },
  { key: 'exceptions', label: '异常场景', critical: false },
  { key: 'data_and_metrics', label: '数据与口径', critical: false },
  { key: 'integrations', label: '外部集成', critical: false },
  { key: 'acceptance', label: '验收方式', critical: true },
  { key: 'constraints', label: '实施约束', critical: false },
]

export function dimensionLabel(dimension: string): string {
  return READINESS_DIMENSIONS.find((entry) => entry.key === dimension)?.label ?? dimension
}

export function dimensionStateLabel(state: string): string {
  switch (state) {
    case 'missing':
      return '缺失'
    case 'partial':
      return '部分'
    case 'confirmed':
      return '已确认'
    case 'inferred':
      return '推断'
    case 'conflict':
      return '冲突'
    default:
      return state
  }
}

export function evidenceTypeLabel(type: string): string {
  switch (type) {
    case 'note':
      return '记录'
    case 'conversation':
      return '沟通'
    case 'meeting':
      return '会议'
    case 'email':
      return '邮件'
    case 'artifact':
      return '资料'
    default:
      return type
  }
}

export function readinessHeadline(ready: boolean): string {
  return ready ? '关键维度已就绪，可提交负责人确认' : '存在阻断项，暂不可确认就绪'
}
