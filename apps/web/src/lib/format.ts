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
