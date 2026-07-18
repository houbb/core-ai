import { ApiError } from './providers'

export async function runtimeRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers
    }
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(
      problem.detail || `Request failed: ${response.status}`,
      problem.errorCode,
      problem.traceId
    )
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
