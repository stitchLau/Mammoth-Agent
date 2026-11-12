import { describe, expect, it } from 'vitest'
import {
  canEditKnowledge,
  canManageMembers,
  canSubmitForReview,
  canVerify,
  canWriteArtifacts,
  roleLabel,
  verifyBlockedReason,
} from './permissions'

describe('role permissions', () => {
  it('only owner/editor may write artifacts and knowledge', () => {
    expect(canWriteArtifacts('owner')).toBe(true)
    expect(canWriteArtifacts('editor')).toBe(true)
    expect(canWriteArtifacts('reviewer')).toBe(false)
    expect(canWriteArtifacts('viewer')).toBe(false)
    expect(canEditKnowledge('viewer', true)).toBe(true)
    expect(canEditKnowledge('viewer', false)).toBe(false)
  })

  it('only owner manages members', () => {
    expect(canManageMembers('owner')).toBe(true)
    expect(canManageMembers('editor')).toBe(false)
    expect(canManageMembers(null)).toBe(false)
  })

  it('submit requires draft status and write access', () => {
    expect(canSubmitForReview('draft', 'editor', false)).toBe(true)
    expect(canSubmitForReview('needs_review', 'editor', false)).toBe(false)
    expect(canSubmitForReview('draft', 'viewer', false)).toBe(false)
  })

  it('verify is four-eyes: owner/reviewer and not the creator', () => {
    expect(canVerify('needs_review', 'reviewer', false)).toBe(true)
    expect(canVerify('needs_review', 'owner', false)).toBe(true)
    expect(canVerify('needs_review', 'reviewer', true)).toBe(false)
    expect(canVerify('needs_review', 'editor', false)).toBe(false)
    expect(canVerify('draft', 'reviewer', false)).toBe(false)
  })

  it('explains blocked verification', () => {
    expect(verifyBlockedReason('needs_review', 'reviewer', false)).toBeNull()
    expect(verifyBlockedReason('needs_review', 'reviewer', true)).toContain('不能自审')
    expect(verifyBlockedReason('needs_review', 'editor', false)).toContain('负责人或审核人')
    expect(verifyBlockedReason('draft', 'reviewer', false)).toContain('提交审核')
    expect(verifyBlockedReason('verified', 'reviewer', false)).toContain('已验证')
  })

  it('labels roles in Chinese', () => {
    expect(roleLabel('owner')).toBe('负责人')
    expect(roleLabel('reviewer')).toBe('审核人')
    expect(roleLabel(null)).toBe('—')
  })
})
