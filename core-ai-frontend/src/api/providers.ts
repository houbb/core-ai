import type {
  AuditEntry,
  ConnectionResult,
  Provider,
  ProviderFilters,
  ProviderInput,
  ProviderPreset
} from '../types/provider'

const BASE_URL = '/api/v1/ai/admin/providers'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly errorCode?: string,
    readonly traceId?: string
  ) {
    super(message)
  }
}

async function request<T>(path = '', init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
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
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export function listProviders(filters: ProviderFilters = {}): Promise<Provider[]> {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value))
    }
  })
  const query = params.size > 0 ? `?${params}` : ''
  return request<Provider[]>(query)
}

export function getProvider(id: string): Promise<Provider> {
  return request<Provider>(`/${id}`)
}

export function getPresets(): Promise<ProviderPreset[]> {
  return request<ProviderPreset[]>('/presets')
}

export function createProvider(input: ProviderInput): Promise<Provider> {
  return request<Provider>('', { method: 'POST', body: JSON.stringify(input) })
}

export function updateProvider(id: string, input: ProviderInput): Promise<Provider> {
  return request<Provider>(`/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function setProviderEnabled(id: string, enabled: boolean): Promise<Provider> {
  return request<Provider>(`/${id}/enabled`, {
    method: 'PATCH',
    body: JSON.stringify({ enabled })
  })
}

export function testProvider(id: string): Promise<ConnectionResult> {
  return request<ConnectionResult>(`/${id}/test`, { method: 'POST' })
}

export function refreshProviderModels(id: string): Promise<ConnectionResult> {
  return request<ConnectionResult>(`/${id}/models/refresh`, { method: 'POST' })
}

export function getProviderAudit(id: string): Promise<AuditEntry[]> {
  return request<AuditEntry[]>(`/${id}/audit`)
}

export function deleteProvider(id: string): Promise<void> {
  return request<void>(`/${id}`, { method: 'DELETE' })
}
