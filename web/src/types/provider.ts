export type ProviderType =
  | 'OPENAI_COMPATIBLE'
  | 'ANTHROPIC'
  | 'GEMINI'
  | 'OLLAMA'
  | 'LM_STUDIO'
  | 'AZURE_OPENAI'
  | 'CUSTOM'

export type ProviderStatus = 'DRAFT' | 'TESTING' | 'AVAILABLE' | 'DISABLED' | 'DELETED'

export type Capability =
  | 'CHAT'
  | 'VISION'
  | 'EMBEDDING'
  | 'IMAGE'
  | 'VIDEO'
  | 'AUDIO'
  | 'SPEECH'
  | 'RERANK'
  | 'REASONING'
  | 'MODERATION'
  | 'OCR'
  | 'TOOL_CALL'
  | 'JSON_MODE'
  | 'STREAMING'

export interface ProviderHealth {
  latencyMs?: number
  availability: number
  rpm: number
  tpm: number
  lastSuccess?: string
  lastError?: string
  lastErrorMessage?: string
  lastStatusCode?: number
}

export interface ProviderModel {
  id: string
  modelId: string
  displayName: string
  capabilities: Capability[]
  contextLength?: number
  status: string
  lastSyncAt: string
}

export interface Provider {
  id: string
  code: string
  name: string
  description?: string
  type: ProviderType
  location: 'LOCAL' | 'CLOUD'
  endpoint: string
  enabled: boolean
  status: ProviderStatus
  priority: number
  weight: number
  timeoutSeconds: number
  retryCount: number
  tags: string[]
  apiKeyMasked?: string
  organization?: string
  proxy?: string
  tlsVerify: boolean
  headers: Record<string, string>
  customParameters: Record<string, string>
  capabilities: Capability[]
  health: ProviderHealth
  modelCount: number
  models?: ProviderModel[]
  createTime: string
  updateTime: string
  createUser: string
  updateUser: string
}

export interface ProviderPreset {
  code: string
  name: string
  type: ProviderType
  endpoint: string
  location: string
  apiKeyRequired: boolean
  customParameters: Record<string, string>
}

export interface ProviderInput {
  code: string
  name: string
  description?: string
  type: ProviderType
  endpoint: string
  priority: number
  weight: number
  timeoutSeconds: number
  retryCount: number
  apiKey?: string
  organization?: string
  proxy?: string
  tlsVerify: boolean
  headers?: Record<string, string>
  customParameters: Record<string, string>
  tags: string[]
}

export interface ConnectionCheck {
  name: string
  success: boolean
  detail: string
}

export interface ConnectionResult {
  success: boolean
  status: ProviderStatus
  latencyMs: number
  modelCount: number
  capabilities: Capability[]
  checks: ConnectionCheck[]
  errorCode?: string
  message: string
  userMessage: string
}

export interface AuditEntry {
  id: string
  action: string
  result: string
  detail?: string
  traceId?: string
  createTime: string
  createUser: string
}

export interface ProviderFilters {
  query?: string
  enabled?: boolean
  location?: string
  capability?: Capability
  tag?: string
}
