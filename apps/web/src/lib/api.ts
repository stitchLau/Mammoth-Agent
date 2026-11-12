/**
 * Typed client for the M1 domain API, using the server-side session cookie plus the XSRF-TOKEN
 * cookie/header pair required by Spring Security for mutations.
 */
export interface Project {
  id: string
  customerId: string
  code: string
  name: string
  status: string
  ownerUserId: string | null
  createdAt: string
}

export interface Customer {
  id: string
  name: string
  status: string
  createdAt: string
}

export interface Product {
  id: string
  name: string
  status: string
  createdAt: string
}

export interface Artifact {
  id: string
  projectId: string
  kind: string
  originalName: string
  contentHash: string
  sizeBytes: number
  mime: string | null
  sourceNote: string | null
  uploadedBy: string | null
  createdAt: string
}

export interface SessionUser {
  id: string
  username: string
  displayName: string
}

export interface KnowledgeHit {
  documentId: string
  title: string
  chunkId: string
  ordinal: number
  text: string
  score: number
  sourceRefCount: number
}

export interface SourceRef {
  artifactId: string
  locator: string | null
  note: string | null
}

export interface KnowledgeDocument {
  id: string
  projectId: string
  docType: string
  title: string
  body: string
  status: string
  version: number
  createdBy: string | null
  reviewer: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
  sourceRefs: SourceRef[]
}

function readCookie(name: string): string {
  const prefix = `${name}=`
  for (const part of document.cookie.split(';')) {
    const trimmed = part.trim()
    if (trimmed.startsWith(prefix)) {
      return decodeURIComponent(trimmed.slice(prefix.length))
    }
  }
  return ''
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  const method = (init?.method ?? 'GET').toUpperCase()
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = readCookie('XSRF-TOKEN')
    if (csrf) {
      headers.set('X-XSRF-TOKEN', csrf)
    }
  }
  const response = await fetch(path, { ...init, headers, credentials: 'include' })
  if (!response.ok) {
    let message = `HTTP ${response.status}`
    try {
      const problem = (await response.json()) as { detail?: string; title?: string }
      message = problem.detail ?? problem.title ?? message
    } catch {
      // keep the status-based message
    }
    throw new Error(message)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

function jsonBody(value: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(value),
  }
}

/** Ensure the XSRF-TOKEN cookie exists before the first mutation of a page load. */
export async function ensureCsrf(): Promise<void> {
  await fetch('/api/auth/csrf', { credentials: 'include' })
}

export function login(username: string, password: string): Promise<SessionUser> {
  return request<SessionUser>('/api/auth/login', jsonBody({ username, password }))
}

export function currentUser(): Promise<SessionUser> {
  return request<SessionUser>('/api/auth/me')
}

export function logout(): Promise<{ status: string }> {
  return request<{ status: string }>('/api/auth/logout', { method: 'POST' })
}

export function listProjects(): Promise<Project[]> {
  return request<Project[]>('/api/projects')
}

export function listCustomers(): Promise<Customer[]> {
  return request<Customer[]>('/api/customers')
}

export function createCustomer(name: string): Promise<Customer> {
  return request<Customer>('/api/customers', jsonBody({ name }))
}

export function listProducts(): Promise<Product[]> {
  return request<Product[]>('/api/products')
}

export function createProduct(name: string): Promise<Product> {
  return request<Product>('/api/products', jsonBody({ name }))
}

export function createProject(customerId: string, code: string, name: string): Promise<Project> {
  return request<Project>('/api/projects', jsonBody({ customerId, code, name }))
}

export function listArtifacts(projectId: string): Promise<Artifact[]> {
  return request<Artifact[]>(`/api/projects/${projectId}/artifacts`)
}

export async function uploadArtifact(projectId: string, file: File): Promise<Artifact> {
  const form = new FormData()
  form.append('file', file)
  return request<Artifact>(`/api/projects/${projectId}/artifacts`, { method: 'POST', body: form })
}

export function listKnowledge(projectId: string): Promise<KnowledgeDocument[]> {
  return request<KnowledgeDocument[]>(`/api/projects/${projectId}/knowledge`)
}

export function getKnowledge(projectId: string, documentId: string): Promise<KnowledgeDocument> {
  return request<KnowledgeDocument>(`/api/projects/${projectId}/knowledge/${documentId}`)
}

export interface CreateKnowledgeInput {
  docType: string
  title: string
  body: string
  sourceRefs: { artifactId: string; locator: string | null; note: string | null }[]
}

export function createKnowledge(projectId: string, input: CreateKnowledgeInput): Promise<KnowledgeDocument> {
  return request<KnowledgeDocument>(`/api/projects/${projectId}/knowledge`, jsonBody(input))
}

export function submitKnowledgeReview(projectId: string, documentId: string): Promise<KnowledgeDocument> {
  return request<KnowledgeDocument>(
    `/api/projects/${projectId}/knowledge/${documentId}/submit-review`,
    { method: 'POST' },
  )
}

export function verifyKnowledge(projectId: string, documentId: string): Promise<KnowledgeDocument> {
  return request<KnowledgeDocument>(`/api/projects/${projectId}/knowledge/${documentId}/verify`, {
    method: 'POST',
  })
}

export function searchKnowledge(projectId: string, query: string): Promise<KnowledgeHit[]> {
  return request<KnowledgeHit[]>(
    `/api/projects/${projectId}/knowledge/search?q=${encodeURIComponent(query)}&topK=5`,
  )
}

export interface SetupStatus {
  initialized: boolean
  userCount: number
}

export function setupStatus(): Promise<SetupStatus> {
  return request<SetupStatus>('/api/setup/status')
}

export interface UserSummary {
  id: string
  username: string
  displayName: string
  status: string
  createdAt: string
}

export function listUsers(): Promise<UserSummary[]> {
  return request<UserSummary[]>('/api/users')
}

/** First-run bootstrap: only succeeds while the organization has no users. */
export function createFirstAdmin(username: string, displayName: string, password: string): Promise<{ id: string }> {
  return request<{ id: string }>('/api/users', jsonBody({ username, displayName, password }))
}

export interface Member {
  projectId: string
  userId: string
  role: string
  createdAt: string
}

export function listMembers(projectId: string): Promise<Member[]> {
  return request<Member[]>(`/api/projects/${projectId}/members`)
}

export function addMember(projectId: string, userId: string, role: string): Promise<Member> {
  return request<Member>(`/api/projects/${projectId}/members`, jsonBody({ userId, role }))
}

export function updateKnowledge(
  projectId: string,
  documentId: string,
  input: CreateKnowledgeInput,
): Promise<KnowledgeDocument> {
  return request<KnowledgeDocument>(`/api/projects/${projectId}/knowledge/${documentId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export function updateProfile(displayName: string): Promise<SessionUser> {
  return request<SessionUser>('/api/auth/profile', { ...jsonBody({ displayName }), method: 'PUT' })
}

export function changePassword(currentPassword: string, newPassword: string): Promise<{ status: string }> {
  return request<{ status: string }>('/api/auth/password', {
    ...jsonBody({ currentPassword, newPassword }),
    method: 'PUT',
  })
}

export function createUser(username: string, displayName: string, password: string): Promise<UserSummary> {
  return request<UserSummary>('/api/users', jsonBody({ username, displayName, password }))
}

export function changeMemberRole(projectId: string, userId: string, role: string): Promise<Member> {
  return request<Member>(`/api/projects/${projectId}/members/${userId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ role }),
  })
}

export function removeMember(projectId: string, userId: string): Promise<void> {
  return request<void>(`/api/projects/${projectId}/members/${userId}`, { method: 'DELETE' })
}

/* ------------------------------------------------------------------ */
/* M2: requirements and solutions                                      */
/* ------------------------------------------------------------------ */

export interface WorkItem {
  id: string
  projectId: string
  itemType: string
  itemNo: string
  title: string
  summary: string | null
  status: string
  priority: string
  sourceNote: string | null
  ownerUserId: string | null
  createdBy: string | null
  priorStatus: string | null
  readyConfirmedBy: string | null
  readyConfirmedAt: string | null
  contextSnapshotId: string | null
  approvedSolutionVersionId: string | null
  createdAt: string
  updatedAt: string
}

export interface RequirementEvidence {
  id: string
  batchNo: number
  evidenceType: string
  text: string
  artifactId: string | null
  locator: string | null
  capturedAt: string
  addedBy: string | null
  createdAt: string
}

export interface DimensionAssessment {
  dimension: string
  critical: boolean
  state: string
  note: string | null
  assumptionNote: string | null
  decisionOwner: string | null
  updatedBy: string | null
  updatedAt: string | null
}

export interface Readiness {
  workItemId: string
  ready: boolean
  blocking: string[]
  warnings: string[]
  catalogVersion: number
  confirmedBy: string | null
  confirmedAt: string | null
  snapshotId: string | null
  dimensions: DimensionAssessment[]
}

export interface RequirementSnapshot {
  id: string
  version: number
  payloadJson: string
  createdBy: string | null
  createdAt: string
}

export interface SolutionVersion {
  id: string
  version: number
  status: string
  title: string
  changeSummary: string | null
  contextSnapshotId: string | null
  createdBy: string | null
  submittedAt: string | null
  approverUserId: string | null
  approvedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface RequirementDetail {
  item: WorkItem
  readiness: Readiness
  evidence: RequirementEvidence[]
  assessments: DimensionAssessment[]
  solutionVersions: SolutionVersion[]
  snapshots: RequirementSnapshot[]
}

export interface DesignItem {
  id: string
  itemKind: string
  parentId: string | null
  title: string
  body: string
  ordinal: number
}

export interface AcceptanceCriterion {
  id: string
  designItemId: string
  text: string
  ordinal: number
}

export interface SolutionTaskItem {
  id: string
  designItemId: string | null
  acceptanceCriterionId: string | null
  title: string
  description: string | null
  doneWhen: string | null
  dependsOnTaskId: string | null
  ordinal: number
}

export interface ConsistencyIssue {
  code: string
  message: string
  objectId: string | null
}

export interface ConsistencyReport {
  ok: boolean
  issues: ConsistencyIssue[]
  counts: {
    businessItems: number
    technicalItems: number
    sharedItems: number
    acceptanceCriteria: number
    tasks: number
    uncoveredGoals: number
  }
}

export interface ApprovalRecordView {
  id: string
  action: string
  actorUserId: string | null
  comment: string | null
  at: string
}

export interface SolutionDetail {
  version: SolutionVersion
  designItems: DesignItem[]
  acceptanceCriteria: AcceptanceCriterion[]
  tasks: SolutionTaskItem[]
  report: ConsistencyReport
  approvals: ApprovalRecordView[]
}

export interface TraceRow {
  goal: string
  designItems: string
  tasks: string
  criteria: string
}

export interface RenderResult {
  view: string
  markdown: string
  designItemCount: number
  taskCount: number
  criterionCount: number
  traceability: TraceRow[]
}

export function listWorkItems(projectId: string, status?: string): Promise<WorkItem[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return request<WorkItem[]>(`/api/projects/${projectId}/work-items${query}`)
}

export function createWorkItem(
  projectId: string,
  input: { title: string; summary: string | null; priority: string; sourceNote: string | null },
): Promise<WorkItem> {
  return request<WorkItem>(`/api/projects/${projectId}/work-items`, jsonBody(input))
}

export function getWorkItem(projectId: string, workItemId: string): Promise<RequirementDetail> {
  return request<RequirementDetail>(`/api/projects/${projectId}/work-items/${workItemId}`)
}

export function updateWorkItem(
  projectId: string,
  workItemId: string,
  input: { title: string; summary: string | null; priority: string; ownerUserId: string | null },
): Promise<WorkItem> {
  return request<WorkItem>(`/api/projects/${projectId}/work-items/${workItemId}`, {
    ...jsonBody(input),
    method: 'PUT',
  })
}

export function addRequirementEvidence(
  projectId: string,
  workItemId: string,
  input: {
    evidenceType: string
    text: string
    artifactId: string | null
    locator: string | null
    capturedAt: string | null
  },
): Promise<RequirementEvidence> {
  return request<RequirementEvidence>(`/api/projects/${projectId}/work-items/${workItemId}/evidence`, jsonBody(input))
}

export function assessDimension(
  projectId: string,
  workItemId: string,
  dimension: string,
  input: {
    state: string
    note: string | null
    assumptionNote: string | null
    decisionOwner: string | null
  },
): Promise<DimensionAssessment> {
  return request<DimensionAssessment>(
    `/api/projects/${projectId}/work-items/${workItemId}/assessments/${dimension}`,
    { ...jsonBody(input), method: 'PUT' },
  )
}

export function confirmReadiness(projectId: string, workItemId: string): Promise<Readiness> {
  return request<Readiness>(`/api/projects/${projectId}/work-items/${workItemId}/confirm-readiness`, {
    method: 'POST',
  })
}

export function transitionWorkItem(
  projectId: string,
  workItemId: string,
  target: string,
  comment: string | null,
): Promise<WorkItem> {
  return request<WorkItem>(`/api/projects/${projectId}/work-items/${workItemId}/transitions`, jsonBody({ target, comment }))
}

export function listSolutionVersions(projectId: string, workItemId: string): Promise<SolutionVersion[]> {
  return request<SolutionVersion[]>(`/api/projects/${projectId}/work-items/${workItemId}/solutions`)
}

export function createSolutionVersion(
  projectId: string,
  workItemId: string,
  input: { title: string; changeSummary: string | null },
): Promise<SolutionVersion> {
  return request<SolutionVersion>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions`,
    jsonBody(input),
  )
}

export function getSolutionVersion(
  projectId: string,
  workItemId: string,
  versionId: string,
): Promise<SolutionDetail> {
  return request<SolutionDetail>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}`,
  )
}

export function addDesignItem(
  projectId: string,
  workItemId: string,
  versionId: string,
  input: { itemKind: string; parentId: string | null; title: string; body: string },
): Promise<DesignItem> {
  return request<DesignItem>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/design-items`,
    jsonBody(input),
  )
}

export function addAcceptanceCriterion(
  projectId: string,
  workItemId: string,
  versionId: string,
  input: { designItemId: string; text: string },
): Promise<AcceptanceCriterion> {
  return request<AcceptanceCriterion>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/acceptance-criteria`,
    jsonBody(input),
  )
}

export function addSolutionTask(
  projectId: string,
  workItemId: string,
  versionId: string,
  input: {
    designItemId: string | null
    acceptanceCriterionId: string | null
    title: string
    description: string | null
    doneWhen: string | null
    dependsOnTaskId: string | null
  },
): Promise<SolutionTaskItem> {
  return request<SolutionTaskItem>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/tasks`,
    jsonBody(input),
  )
}

export function renderSolution(
  projectId: string,
  workItemId: string,
  versionId: string,
  view: 'business' | 'technical',
): Promise<RenderResult> {
  return request<RenderResult>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/render?view=${view}`,
  )
}

export function submitSolution(
  projectId: string,
  workItemId: string,
  versionId: string,
): Promise<SolutionVersion> {
  return request<SolutionVersion>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/submit`,
    { method: 'POST' },
  )
}

export function approveSolution(
  projectId: string,
  workItemId: string,
  versionId: string,
  comment: string | null,
): Promise<SolutionVersion> {
  return request<SolutionVersion>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/approve`,
    jsonBody({ comment }),
  )
}

export function requestSolutionChanges(
  projectId: string,
  workItemId: string,
  versionId: string,
  comment: string,
): Promise<SolutionVersion> {
  return request<SolutionVersion>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/request-changes`,
    jsonBody({ comment }),
  )
}

export function newSolutionVersion(
  projectId: string,
  workItemId: string,
  versionId: string,
  changeSummary: string | null,
): Promise<SolutionVersion> {
  return request<SolutionVersion>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/new-version`,
    jsonBody({ changeSummary }),
  )
}

export function deleteDesignItem(
  projectId: string,
  workItemId: string,
  versionId: string,
  itemId: string,
): Promise<void> {
  return request<void>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/design-items/${itemId}`,
    { method: 'DELETE' },
  )
}

export function deleteAcceptanceCriterion(
  projectId: string,
  workItemId: string,
  versionId: string,
  criterionId: string,
): Promise<void> {
  return request<void>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/acceptance-criteria/${criterionId}`,
    { method: 'DELETE' },
  )
}

export function deleteSolutionTask(
  projectId: string,
  workItemId: string,
  versionId: string,
  taskId: string,
): Promise<void> {
  return request<void>(
    `/api/projects/${projectId}/work-items/${workItemId}/solutions/${versionId}/tasks/${taskId}`,
    { method: 'DELETE' },
  )
}
