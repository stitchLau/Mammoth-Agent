import { describe, expect, it } from 'vitest'
import {
  canSubmitReview,
  canVerify,
  dimensionLabel,
  dimensionStateLabel,
  evidenceTypeLabel,
  formatBytes,
  formatProjectCode,
  knowledgeStatusLabel,
  readinessHeadline,
  READINESS_DIMENSIONS,
  solutionStatusLabel,
  workItemPriorityLabel,
  workItemStatusLabel,
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

describe('M2 requirement and solution labels', () => {
  it('maps requirement statuses to Chinese labels', () => {
    expect(workItemStatusLabel('draft')).toBe('草稿')
    expect(workItemStatusLabel('collecting')).toBe('收集中')
    expect(workItemStatusLabel('ready_for_analysis')).toBe('待分析')
    expect(workItemStatusLabel('solution_review')).toBe('方案评审')
    expect(workItemStatusLabel('approved')).toBe('已批准')
    expect(workItemStatusLabel('blocked')).toBe('阻塞')
    expect(workItemStatusLabel('unknown')).toBe('unknown')
  })

  it('maps priorities and solution statuses', () => {
    expect(workItemPriorityLabel('critical')).toBe('紧急')
    expect(workItemPriorityLabel('normal')).toBe('普通')
    expect(solutionStatusLabel('in_review')).toBe('评审中')
    expect(solutionStatusLabel('superseded')).toBe('已被替代')
  })

  it('keeps exactly 11 readiness dimensions with 4 critical ones', () => {
    expect(READINESS_DIMENSIONS).toHaveLength(11)
    expect(READINESS_DIMENSIONS.filter((entry) => entry.critical).map((entry) => entry.key)).toEqual([
      'background',
      'goals',
      'target_process',
      'acceptance',
    ])
    expect(dimensionLabel('exceptions')).toBe('异常场景')
    expect(dimensionLabel('made_up')).toBe('made_up')
  })

  it('maps assessment states and evidence types', () => {
    expect(dimensionStateLabel('missing')).toBe('缺失')
    expect(dimensionStateLabel('conflict')).toBe('冲突')
    expect(evidenceTypeLabel('conversation')).toBe('沟通')
    expect(evidenceTypeLabel('artifact')).toBe('资料')
  })

  it('explains readiness in one line', () => {
    expect(readinessHeadline(true)).toContain('已就绪')
    expect(readinessHeadline(false)).toContain('阻断')
  })
})
