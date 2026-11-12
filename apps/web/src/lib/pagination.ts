/** Client-side pagination helpers (server-side pagination is tracked as a later-phase item). */
export function pageCount(total: number, pageSize: number): number {
  if (!Number.isFinite(total) || total <= 0 || pageSize <= 0) {
    return 1
  }
  return Math.max(1, Math.ceil(total / pageSize))
}

export function clampPage(page: number, total: number, pageSize: number): number {
  return Math.min(Math.max(1, page), pageCount(total, pageSize))
}

export function pageSlice<T>(items: T[], page: number, pageSize: number): T[] {
  const safePage = clampPage(page, items.length, pageSize)
  const start = (safePage - 1) * pageSize
  return items.slice(start, start + pageSize)
}
