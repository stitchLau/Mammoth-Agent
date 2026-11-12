import { describe, expect, it } from 'vitest'
import {
  canSubmitReview,
  canVerify,
  formatBytes,
  formatProjectCode,
  knowledgeStatusLabel,
} from './format'

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

describe('formatBytes', () => {
  it('formats bytes, kilobytes and megabytes', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(1023)).toBe('1023 B')
    expect(formatBytes(2048)).toBe('2.0 KB')
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB')
  })

  it('handles invalid input defensively', () => {
    expect(formatBytes(-1)).toBe('-')
    expect(formatBytes(Number.NaN)).toBe('-')
  })
})

describe('knowledge lifecycle helpers', () => {
  it('maps statuses to Chinese labels', () => {
    expect(knowledgeStatusLabel('draft')).toBe('草稿')
    expect(knowledgeStatusLabel('needs_review')).toBe('待审核')
    expect(knowledgeStatusLabel('verified')).toBe('已验证')
    expect(knowledgeStatusLabel('unknown')).toBe('unknown')
  })

  it('only allows review transitions from the expected states', () => {
    expect(canSubmitReview('draft')).toBe(true)
    expect(canSubmitReview('needs_review')).toBe(false)
    expect(canVerify('needs_review')).toBe(true)
    expect(canVerify('draft')).toBe(false)
    expect(canVerify('verified')).toBe(false)
  })
})
