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
