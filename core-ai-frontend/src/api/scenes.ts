import { ApiError } from './providers'
import type {
  AiScene,
  SceneConfiguration,
  SceneExecutionResult,
  SceneFilters,
  ScenePackage,
  SceneStatus,
  SceneTemplate,
  SceneVersion
} from '../types/scene'

const ADMIN_URL = '/api/v1/ai/admin/scenes'
const TEMPLATE_URL = '/api/v1/ai/admin/scene-templates'
const RUNTIME_URL = '/api/v1/ai/scenes'

async function request<T>(base: string, path = '', init?: RequestInit): Promise<T> {
  const response = await fetch(`${base}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers }
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

function queryString(filters: object) {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  })
  return params.size ? `?${params}` : ''
}

export function listScenes(filters: SceneFilters = {}) {
  return request<AiScene[]>(ADMIN_URL, queryString(filters))
}

export function getScene(id: string) {
  return request<AiScene>(ADMIN_URL, `/${id}`)
}

export function createScene(code: string, configuration: SceneConfiguration) {
  return request<AiScene>(ADMIN_URL, '', {
    method: 'POST',
    body: JSON.stringify({ code, ...configuration })
  })
}

export function updateScene(id: string, configuration: SceneConfiguration) {
  return request<AiScene>(ADMIN_URL, `/${id}`, {
    method: 'PUT',
    body: JSON.stringify(configuration)
  })
}

export function transitionScene(id: string, status: SceneStatus) {
  return request<AiScene>(ADMIN_URL, `/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  })
}

export function testScene(
  id: string,
  input: string,
  variables: Record<string, unknown> = {}
) {
  return request<SceneExecutionResult>(ADMIN_URL, `/${id}/test`, {
    method: 'POST',
    body: JSON.stringify({ input, variables })
  })
}

export function listSceneVersions(id: string) {
  return request<SceneVersion[]>(ADMIN_URL, `/${id}/versions`)
}

export function rollbackScene(id: string, version: number) {
  return request<AiScene>(ADMIN_URL, `/${id}/versions/${version}/rollback`, {
    method: 'POST'
  })
}

export function exportScene(id: string) {
  return request<ScenePackage>(ADMIN_URL, `/${id}/export`)
}

export function importScene(scenePackage: ScenePackage) {
  return request<AiScene>(ADMIN_URL, '/import', {
    method: 'POST',
    body: JSON.stringify(scenePackage)
  })
}

export function saveSceneTemplate(id: string, templateName: string, defaultCode: string) {
  return request<SceneTemplate>(ADMIN_URL, `/${id}/templates`, {
    method: 'POST',
    body: JSON.stringify({ templateName, defaultCode })
  })
}

export function getSceneAudit(id: string) {
  return request<Array<{
    id: string
    action: string
    result: string
    detail?: string
    traceId?: string
    createTime: string
    createUser: string
  }>>(ADMIN_URL, `/${id}/audit`)
}

export function listSceneTemplates() {
  return request<SceneTemplate[]>(TEMPLATE_URL)
}

export function instantiateSceneTemplate(id: string, code?: string, name?: string) {
  return request<AiScene>(TEMPLATE_URL, `/${id}/instantiate`, {
    method: 'POST',
    body: JSON.stringify({ code, name })
  })
}

export function deleteSceneTemplate(id: string) {
  return request<void>(TEMPLATE_URL, `/${id}`, { method: 'DELETE' })
}

export function listPublishedScenes() {
  return request<AiScene[]>(RUNTIME_URL)
}

export function executeScene(
  code: string,
  input: string,
  variables: Record<string, unknown> = {}
) {
  return request<SceneExecutionResult>(RUNTIME_URL, `/${encodeURIComponent(code)}/execute`, {
    method: 'POST',
    body: JSON.stringify({ input, variables })
  })
}
