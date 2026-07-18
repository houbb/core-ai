import { ApiError } from './providers'
import type {
  PromptAbTest,
  PromptAudit,
  PromptConfiguration,
  PromptDetail,
  PromptDiffLine,
  PromptFilters,
  PromptRenderLog,
  PromptRenderResult,
  PromptStatus,
  PromptSummary,
  PromptTestCase,
  PromptTestSuite,
  PromptVersion
} from '../types/prompt'

const ADMIN_URL = '/api/v1/ai/admin/prompts'
const RUNTIME_URL = '/api/v1/ai/prompts'

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

export function listPrompts(filters: PromptFilters = {}) {
  return request<PromptSummary[]>(ADMIN_URL, queryString(filters))
}

export function getPrompt(id: string) {
  return request<PromptDetail>(ADMIN_URL, `/${id}`)
}

export function createPrompt(code: string, configuration: PromptConfiguration) {
  return request<PromptDetail>(ADMIN_URL, '', {
    method: 'POST',
    body: JSON.stringify({ code, ...configuration })
  })
}

export function updatePrompt(id: string, configuration: PromptConfiguration) {
  return request<PromptDetail>(ADMIN_URL, `/${id}`, {
    method: 'PUT',
    body: JSON.stringify(configuration)
  })
}

export function transitionPrompt(id: string, status: PromptStatus) {
  return request<PromptDetail>(ADMIN_URL, `/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  })
}

export function listPromptVersions(id: string) {
  return request<PromptVersion[]>(ADMIN_URL, `/${id}/versions`)
}

export function rollbackPrompt(id: string, version: number) {
  return request<PromptDetail>(ADMIN_URL, `/${id}/versions/${version}/rollback`, {
    method: 'POST'
  })
}

export function comparePromptVersions(id: string, left: number, right: number) {
  return request<PromptDiffLine[]>(ADMIN_URL, `/${id}/compare?left=${left}&right=${right}`)
}

export function renderPrompt(id: string, variables: Record<string, unknown>) {
  return request<PromptRenderResult>(ADMIN_URL, `/${id}/render`, {
    method: 'POST',
    body: JSON.stringify({ variables })
  })
}

export function validatePromptOutput(id: string, version: number | undefined, output: string) {
  return request<void>(ADMIN_URL, `/${id}/validate-output`, {
    method: 'POST',
    body: JSON.stringify({ version, output })
  })
}

export function listPromptTestCases(id: string) {
  return request<PromptTestCase[]>(ADMIN_URL, `/${id}/test-cases`)
}

export function createPromptTestCase(
  id: string,
  data: Pick<PromptTestCase, 'name' | 'inputJson' | 'expectedOutput' | 'enabled'>
) {
  return request<PromptTestCase>(ADMIN_URL, `/${id}/test-cases`, {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function updatePromptTestCase(
  id: string,
  testCaseId: string,
  data: Pick<PromptTestCase, 'name' | 'inputJson' | 'expectedOutput' | 'enabled'>
) {
  return request<PromptTestCase>(ADMIN_URL, `/${id}/test-cases/${testCaseId}`, {
    method: 'PUT',
    body: JSON.stringify(data)
  })
}

export function deletePromptTestCase(id: string, testCaseId: string) {
  return request<void>(ADMIN_URL, `/${id}/test-cases/${testCaseId}`, { method: 'DELETE' })
}

export function runPromptTests(id: string) {
  return request<PromptTestSuite>(ADMIN_URL, `/${id}/tests/run`, { method: 'POST' })
}

export function listPromptAbTests(id: string) {
  return request<PromptAbTest[]>(ADMIN_URL, `/${id}/ab-tests`)
}

export function createPromptAbTest(
  id: string,
  data: { name: string; sceneId?: string; versionA: number; versionB: number; trafficRatio: number }
) {
  return request<PromptAbTest>(ADMIN_URL, `/${id}/ab-tests`, {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function assignPromptAbTest(id: string, abTestId: string, subjectKey: string) {
  return request<{ abTestId: string; variant: string; version: number; bucket: number }>(
    ADMIN_URL,
    `/${id}/ab-tests/${abTestId}/assign`,
    { method: 'POST', body: JSON.stringify({ subjectKey }) }
  )
}

export function recordPromptAbObservation(
  id: string,
  abTestId: string,
  data: { variant: 'A' | 'B'; success: boolean; latencyMs: number; cost: number; score: number }
) {
  return request<PromptAbTest>(ADMIN_URL, `/${id}/ab-tests/${abTestId}/observations`, {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function getPromptAudit(id: string) {
  return request<PromptAudit[]>(ADMIN_URL, `/${id}/audit`)
}

export function getPromptRenderLogs(id: string) {
  return request<PromptRenderLog[]>(ADMIN_URL, `/${id}/render-logs`)
}

export function listPublishedPrompts() {
  return request<PromptSummary[]>(RUNTIME_URL)
}

export function renderPublishedPrompt(
  code: string,
  variables: Record<string, unknown>,
  version?: number
) {
  return request<PromptRenderResult>(
    RUNTIME_URL,
    `/${encodeURIComponent(code)}/render${version ? `?version=${version}` : ''}`,
    { method: 'POST', body: JSON.stringify({ variables }) }
  )
}
