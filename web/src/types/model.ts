import type { Capability } from './provider'

export type ModelStatus =
  | 'DISCOVERED'
  | 'REGISTERED'
  | 'ENABLED'
  | 'DEPRECATED'
  | 'DISABLED'
  | 'DELETED'

export type ModelCategory =
  | 'CHAT'
  | 'REASONING'
  | 'VISION'
  | 'EMBEDDING'
  | 'RERANK'
  | 'IMAGE'
  | 'VIDEO'
  | 'AUDIO'
  | 'SPEECH'
  | 'MODERATION'
  | 'OCR'
  | 'OTHER'

export interface ModelParameters {
  temperature?: number
  topP?: number
  frequencyPenalty?: number
  presencePenalty?: number
  maxOutputTokens?: number
  reasoningEffort?: string
  seed?: number
}

export interface ModelPricing {
  id: string
  modelId: string
  currency: string
  promptPrice?: number
  completionPrice?: number
  cacheReadPrice?: number
  cacheWritePrice?: number
  effectiveTime: string
  source: string
  notes?: string
  createTime: string
  createUser: string
}

export interface ModelAlias {
  id: string
  alias: string
  modelId: string
  modelDisplayName: string
  providerName: string
  scene?: string
  priority: number
  enabled: boolean
  createTime: string
  updateTime: string
  createUser: string
  updateUser: string
}

export interface AiModel {
  id: string
  providerId: string
  providerCode: string
  providerName: string
  providerEnabled: boolean
  providerLatencyMs?: number
  remoteModelId: string
  displayName: string
  category: ModelCategory
  description?: string
  status: ModelStatus
  enabled: boolean
  availableFromProvider: boolean
  recommended: boolean
  favorite: boolean
  maxContextTokens?: number
  maxInputTokens?: number
  maxOutputTokens?: number
  defaultMaxTokens?: number
  contextManuallyOverridden: boolean
  capabilities: Capability[]
  capabilityOverrides: Partial<Record<Capability, boolean>>
  parameters: ModelParameters
  currentPricing?: ModelPricing
  pricingHistory: ModelPricing[]
  aliases: ModelAlias[]
  tags: string[]
  lastDiscoveredAt?: string
  createTime: string
  updateTime: string
  createUser: string
  updateUser: string
}

export interface ModelFilters {
  query?: string
  providerId?: string
  category?: ModelCategory
  capability?: Capability
  status?: ModelStatus
  enabled?: boolean
  favorite?: boolean
  recommended?: boolean
  available?: boolean
  minimumContextTokens?: number
  tag?: string
}

export interface ModelUpdateInput {
  displayName: string
  category: ModelCategory
  description?: string
  maxContextTokens?: number
  maxInputTokens?: number
  maxOutputTokens?: number
  defaultMaxTokens?: number
  contextManuallyOverridden: boolean
  tags: string[]
}

export interface PricingInput {
  currency: string
  promptPrice?: number
  completionPrice?: number
  cacheReadPrice?: number
  cacheWritePrice?: number
  effectiveTime: string
  notes?: string
}

export interface AliasInput {
  alias: string
  modelId: string
  scene?: string
  priority: number
  enabled: boolean
}

export interface ModelRecommendation {
  model: AiModel
  score: number
  reason: string
}

export interface DefaultModel {
  alias: string
  model?: AiModel
}
