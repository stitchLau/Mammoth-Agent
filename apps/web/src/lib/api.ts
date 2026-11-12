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

/** Ensure the XSRF-TOKEN cookie exists before the first mutation of a page load. */
export async function ensureCsrf(): Promise<void> {
  await fetch('/api/auth/csrf', { credentials: 'include' })
}

export function login(username: string, password: string): Promise<SessionUser> {
  return request<SessionUser>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
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

export function listArtifacts(projectId: string): Promise<Artifact[]> {
  return request<Artifact[]>(`/api/projects/${projectId}/artifacts`)
}

export async function uploadArtifact(projectId: string, file: File): Promise<Artifact> {
  const form = new FormData()
  form.append('file', file)
  return request<Artifact>(`/api/projects/${projectId}/artifacts`, { method: 'POST', body: form })
}

export function searchKnowledge(projectId: string, query: string): Promise<KnowledgeHit[]> {
  return request<KnowledgeHit[]>(
    `/api/projects/${projectId}/knowledge/search?q=${encodeURIComponent(query)}&topK=5`,
  )
}
