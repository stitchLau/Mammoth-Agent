import { describe, expect, it } from 'vitest'
import { formatProjectCode } from './format'

describe('formatProjectCode', () => {
  it('joins customer, product and year with hyphens', () => {
    expect(formatProjectCode('ACME', 'MES', 2026)).toBe('ACME-MES-2026')
  })

  it('normalizes whitespace and case', () => {
    expect(formatProjectCode('  acme  corp ', 'mes', 2026)).toBe('ACME-CORP-MES-2026')
  })

  it('skips empty segments', () => {
    expect(formatProjectCode('ACME', '', 2026)).toBe('ACME-2026')
  })
})
