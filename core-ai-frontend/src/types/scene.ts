export type SceneStatus = 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'DISABLED' | 'ARCHIVED'
export type ScenePermissionType = 'EVERYONE' | 'ROLE' | 'DEPARTMENT' | 'USER_GROUP'
export type SceneWorkflowStepType = 'MODEL_ALIAS' | 'SCENE' | 'EXTERNAL'

export interface SceneModelBinding {
  id?: string
  modelAlias: string
  priority: number
  fallback: boolean
  enabled: boolean
  resolved?: boolean
  modelId?: string
  modelDisplayName?: string
  providerName?: string
  latencyMs?: number
}

export interface SceneParameters {
  temperature?: number
  topP?: number
  maxOutputTokens?: number
  reasoningEffort?: string
  jsonMode: boolean
  streaming: boolean
}

export interface ScenePrompt {
  promptId?: string
  promptVersion?: number
}

export interface ScenePermission {
  type: ScenePermissionType
  value?: string
}

export interface SceneWorkflowStep {
  code: string
  type: SceneWorkflowStepType
  reference: string
  optional: boolean
}

export interface SceneConfiguration {
  name: string
  description?: string
  category: string
  icon?: string
  recommended: boolean
  models: SceneModelBinding[]
  parameters: SceneParameters
  prompt: ScenePrompt
  permissions: ScenePermission[]
  workflow: SceneWorkflowStep[]
}

export interface AiScene extends SceneConfiguration {
  id: string
  code: string
  status: SceneStatus
  enabled: boolean
  version: number
  lastTestedAt?: string
  lastTestedVersion?: number
  costTier: 'CHEAP' | 'STANDARD' | 'PREMIUM' | 'UNKNOWN'
  createTime: string
  updateTime: string
  createUser: string
  updateUser: string
}

export interface SceneTemplate {
  id: string
  defaultCode: string
  templateName: string
  description?: string
  category: string
  icon?: string
  builtin: boolean
  recommended: boolean
  configuration: SceneConfiguration
}

export interface SceneVersion {
  id: string
  version: number
  configuration: SceneConfiguration
  createTime: string
  createUser: string
}

export interface ScenePackage {
  formatVersion: number
  code: string
  version: number
  configuration: SceneConfiguration
}

export interface SceneExecutionResult {
  mode: string
  executed: boolean
  output: string
  sceneCode: string
  sceneVersion: number
  modelAlias: string
  modelId: string
  modelDisplayName: string
  providerName: string
  promptId?: string
  promptVersion?: number
  latencyMs: number
  estimatedInputTokens?: number
  estimatedCost?: number
  currency?: string
  trace: Array<{
    order: number
    stage: string
    name: string
    detail: string
    status: string
  }>
  executeTime: string
  traceId: string
}

export interface SceneFilters {
  query?: string
  category?: string
  status?: SceneStatus
  enabled?: boolean
  recommended?: boolean
}
