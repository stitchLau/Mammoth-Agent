/** UI permission helpers mirroring ADR-0006; the server remains the authority. */
export const ROLES = ['owner', 'editor', 'reviewer', 'viewer'] as const
export type Role = (typeof ROLES)[number]

export const ROLE_LABELS: Record<string, string> = {
  owner: '负责人',
  editor: '编辑',
  reviewer: '审核人',
  viewer: '查看者',
}

export function roleLabel(role: string | null | undefined): string {
  return role ? ROLE_LABELS[role] ?? role : '—'
}

export function canWriteArtifacts(role: Role | null | undefined): boolean {
  return role === 'owner' || role === 'editor'
}

export function canEditKnowledge(role: Role | null | undefined, isCreator: boolean): boolean {
  return role === 'owner' || role === 'editor' || isCreator
}

export function canManageMembers(role: Role | null | undefined): boolean {
  return role === 'owner'
}

/** Submit is available for drafts only and to writers of that document. */
export function canSubmitForReview(
  status: string,
  role: Role | null | undefined,
  isCreator: boolean,
): boolean {
  return status === 'draft' && canEditKnowledge(role, isCreator)
}

/** Four-eyes: verifier must have owner/reviewer role and must not be the creator. */
export function canVerify(
  status: string,
  role: Role | null | undefined,
  isCreator: boolean,
): boolean {
  return status === 'needs_review' && (role === 'owner' || role === 'reviewer') && !isCreator
}

/** Explain why the verify action is unavailable (shown as disabled-button reason). */
export function verifyBlockedReason(
  status: string,
  role: Role | null | undefined,
  isCreator: boolean,
): string | null {
  if (canVerify(status, role, isCreator)) {
    return null
  }
  if (status === 'draft') {
    return '草稿需先提交审核'
  }
  if (status === 'verified') {
    return '该文档已验证'
  }
  if (isCreator) {
    return '审核人必须是其他项目成员（不能自审）'
  }
  if (role !== 'owner' && role !== 'reviewer') {
    return '需要项目负责人或审核人角色'
  }
  return '当前状态不可审核'
}
