import { ApiError } from './providers'
import type { Capability } from '../types/provider'
import type {
  AiModel,
  AliasInput,
  DefaultModel,
  ModelAlias,
  ModelFilters,
  ModelParameters,
  ModelPricing,
  ModelRecommendation,
  ModelStatus,
  ModelUpdateInput,
  PricingInput
} from '../types/model'

const MODEL_URL = '/api/v1/ai/admin/models'
const ALIAS_URL = '/api/v1/ai/admin/model-aliases'

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

export function listModels(filters: ModelFilters = {}) {
  return request<AiModel[]>(MODEL_URL, queryString(filters))
}

export function getModel(id: string) {
  return request<AiModel>(MODEL_URL, `/${id}`)
}

export function syncModels(providerId?: string) {
  return request<{ synchronizedModels: number }>(
    MODEL_URL,
    `/sync${queryString({ providerId })}`,
    { method: 'POST' }
  )
}

export function updateModel(id: string, input: ModelUpdateInput) {
  return request<AiModel>(MODEL_URL, `/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function transitionModel(id: string, status: ModelStatus) {
  return request<AiModel>(MODEL_URL, `/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  })
}

export function updateModelCapabilities(
  id: string,
  overrides: Partial<Record<Capability, boolean>>
) {
  return request<AiModel>(MODEL_URL, `/${id}/capabilities`, {
    method: 'PUT',
    body: JSON.stringify({ overrides })
  })
}

export function resetModelCapabilities(id: string) {
  return request<AiModel>(MODEL_URL, `/${id}/capabilities/reset`, { method: 'POST' })
}

export function updateModelParameters(id: string, parameters: ModelParameters) {
  return request<AiModel>(MODEL_URL, `/${id}/parameters`, {
    method: 'PUT',
    body: JSON.stringify(parameters)
  })
}

export function addModelPricing(id: string, pricing: PricingInput) {
  return request<ModelPricing>(MODEL_URL, `/${id}/pricing`, {
    method: 'POST',
    body: JSON.stringify(pricing)
  })
}

export function setModelFlags(id: string, favorite: boolean, recommended: boolean) {
  return request<AiModel>(MODEL_URL, `/${id}/flags`, {
    method: 'PATCH',
    body: JSON.stringify({ favorite, recommended })
  })
}

export function deleteModel(id: string) {
  return request<void>(MODEL_URL, `/${id}`, { method: 'DELETE' })
}

export function compareModels(ids: string[]) {
  return request<AiModel[]>(MODEL_URL, '/compare', {
    method: 'POST',
    body: JSON.stringify({ ids })
  })
}

export function recommendModels(
  capability: Capability,
  mode: 'BEST' | 'CHEAPEST' | 'FASTEST' | 'LARGEST_CONTEXT',
  limit = 5
) {
  return request<ModelRecommendation[]>(
    MODEL_URL,
    `/recommend${queryString({ capability, mode, limit })}`
  )
}

export function getDefaultModels() {
  return request<DefaultModel[]>(MODEL_URL, '/defaults')
}

export function getModelAudit(id: string) {
  return request<Array<{
    id: string
    action: string
    result: string
    detail?: string
    traceId?: string
    createTime: string
    createUser: string
  }>>(MODEL_URL, `/${id}/audit`)
}

export function listAliases(alias?: string) {
  return request<ModelAlias[]>(ALIAS_URL, queryString({ alias }))
}

export function createAlias(input: AliasInput) {
  return request<ModelAlias>(ALIAS_URL, '', { method: 'POST', body: JSON.stringify(input) })
}

export function updateAlias(id: string, input: AliasInput) {
  return request<ModelAlias>(ALIAS_URL, `/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function deleteAlias(id: string) {
  return request<void>(ALIAS_URL, `/${id}`, { method: 'DELETE' })
}

export function resolveAlias(alias: string) {
  return request<AiModel[]>(ALIAS_URL, `/${encodeURIComponent(alias)}/resolve`)
}
