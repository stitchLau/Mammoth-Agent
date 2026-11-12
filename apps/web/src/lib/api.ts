/**
 * Minimal typed client for the M1 domain API.
 *
 * Identity is a temporary header (`X-Mammoth-User-Id`) for the pre-authentication stage; the login
 * session replaces it in the authentication task without changing these call sites' resource paths.
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

const USER_KEY = 'mammoth.userId'

export function readUserId(): string {
  try {
    return localStorage.getItem(USER_KEY) ?? ''
  } catch {
    return ''
  }
}

export function writeUserId(userId: string): void {
  try {
    localStorage.setItem(USER_KEY, userId)
  } catch {
    // storage may be unavailable (private mode); the in-memory value still works for this page
  }
}

async function request<T>(path: string, userId: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('X-Mammoth-User-Id', userId)
  const response = await fetch(path, { ...init, headers })
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
  return (await response.json()) as T
}

export function listProjects(userId: string): Promise<Project[]> {
  return request<Project[]>('/api/projects', userId)
}

export function listArtifacts(userId: string, projectId: string): Promise<Artifact[]> {
  return request<Artifact[]>(`/api/projects/${projectId}/artifacts`, userId)
}

export async function uploadArtifact(
  userId: string,
  projectId: string,
  file: File,
): Promise<Artifact> {
  const form = new FormData()
  form.append('file', file)
  return request<Artifact>(`/api/projects/${projectId}/artifacts`, userId, {
    method: 'POST',
    body: form,
  })
}
