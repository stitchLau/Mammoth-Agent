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
