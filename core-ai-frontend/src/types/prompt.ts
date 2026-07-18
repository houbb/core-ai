export type PromptStatus = 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'DEPRECATED' | 'ARCHIVED'
export type PromptVisibility = 'PUBLIC' | 'PROJECT' | 'DEPARTMENT' | 'PRIVATE'
export type PromptVariableType = 'STRING' | 'INTEGER' | 'BOOLEAN' | 'JSON' | 'LIST' | 'OBJECT'
export type PromptGuardrailType = 'SENSITIVE' | 'INJECTION' | 'ILLEGAL' | 'LENGTH' | 'JSON_VALIDATE'
export type PromptGuardrailPhase = 'INPUT' | 'OUTPUT'

export interface PromptSummary {
  id: string
  code: string
  name: string
  description?: string
  category: string
  sceneId?: string
  status: PromptStatus
  currentVersion: number
  publishedVersion?: number
  visibility: PromptVisibility
  projectCode?: string
  departmentCode?: string
  ownerUser: string
  updateTime: string
}

export interface PromptVariable {
  name: string
  type: PromptVariableType
  required: boolean
  defaultValue?: string
  description?: string
}

export interface PromptOutputSchema {
  schemaJson?: string
  strictMode: boolean
}

export interface PromptGuardrail {
  type: PromptGuardrailType
  phase: PromptGuardrailPhase
  configJson: string
  enabled: boolean
}

export interface PromptChainStep {
  reference: string
  version?: number
  optional: boolean
}

export interface PromptVersion {
  id: string
  version: number
  systemPrompt?: string
  userPrompt: string
  assistantPrompt?: string
  changeLog?: string
  variables: PromptVariable[]
  outputSchema: PromptOutputSchema
  guardrails: PromptGuardrail[]
  chain: PromptChainStep[]
  testsPassed: boolean
  lastTestedTime?: string
  publishedTime?: string
  createTime: string
  createUser: string
}

export interface PromptDetail {
  prompt: PromptSummary
  currentVersion: PromptVersion
  publishedVersion?: PromptVersion
}

export interface PromptConfiguration {
  name: string
  description?: string
  category: string
  sceneId?: string
  visibility: PromptVisibility
  projectCode?: string
  departmentCode?: string
  systemPrompt: string
  userPrompt: string
  assistantPrompt: string
  changeLog?: string
  variables: PromptVariable[]
  outputSchema: PromptOutputSchema
  guardrails: PromptGuardrail[]
  chain: PromptChainStep[]
}

export interface PromptFilters {
  query?: string
  category?: string
  status?: PromptStatus
  visibility?: PromptVisibility
}

export interface PromptRenderedStage {
  promptId: string
  promptCode: string
  version: number
  systemPrompt?: string
  userPrompt: string
  assistantPrompt?: string
  estimatedTokens: number
}

export interface PromptRenderResult {
  promptId: string
  promptCode: string
  version: number
  systemPrompt?: string
  userPrompt: string
  assistantPrompt?: string
  chain: PromptRenderedStage[]
  characterCount: number
  estimatedTokens: number
  outputSchema?: string
  strictSchema: boolean
  mode: string
}

export interface PromptTestCase {
  id: string
  name: string
  inputJson: string
  expectedOutput?: string
  enabled: boolean
  lastActualOutput?: string
  lastPassed?: boolean
  lastRunTime?: string
}

export interface PromptTestSuite {
  promptId: string
  version: number
  passed: boolean
  mode: string
  executed: boolean
  results: Array<{
    testCaseId: string
    testCaseName: string
    passed: boolean
    expectedOutput?: string
    actualOutput: string
    mode: string
    executed: boolean
    render: PromptRenderResult
  }>
}

export interface PromptDiffLine {
  section: string
  type: 'SAME' | 'ADDED' | 'REMOVED'
  leftLine: number
  rightLine: number
  text: string
}

export interface PromptAbTest {
  id: string
  promptId: string
  sceneId?: string
  name: string
  versionA: number
  versionB: number
  trafficRatio: number
  enabled: boolean
  sampleA: number
  sampleB: number
  successA: number
  successB: number
  averageLatencyA: number
  averageLatencyB: number
  costATotal: number
  costBTotal: number
  averageScoreA: number
  averageScoreB: number
}

export interface PromptAudit {
  id: string
  action: string
  result: string
  detail?: string
  traceId?: string
  createTime: string
  createUser: string
}

export interface PromptRenderLog {
  id: string
  promptVersionId: string
  variableNames: string
  contentStored: boolean
  contentHash: string
  estimatedTokens: number
  mode: string
  expireTime?: string
  createTime: string
  createUser: string
}
