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
