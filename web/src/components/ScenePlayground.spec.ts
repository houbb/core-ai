import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ScenePlayground from './ScenePlayground.vue'
import type { AiScene, SceneExecutionResult } from '../types/scene'

const scene: AiScene = {
  id: 'scene-1',
  code: 'chat',
  name: 'AI Chat',
  category: 'CONVERSATION',
  icon: '💬',
  status: 'TESTING',
  enabled: false,
  version: 1,
  recommended: true,
  models: [{ modelAlias: 'chat-default', priority: 10, fallback: false, enabled: true }],
  parameters: { jsonMode: false, streaming: true },
  prompt: {},
  permissions: [{ type: 'EVERYONE', value: '*' }],
  workflow: [],
  costTier: 'UNKNOWN',
  createTime: '2026-07-16T00:00:00Z',
  updateTime: '2026-07-16T00:00:00Z',
  createUser: 'test',
  updateUser: 'test'
}

const result: SceneExecutionResult = {
  mode: 'PREVIEW',
  executed: false,
  output: 'Scene configuration validated.',
  sceneCode: 'chat',
  sceneVersion: 1,
  modelAlias: 'chat-default',
  modelId: 'model',
  modelDisplayName: 'GPT-4o',
  providerName: 'OpenAI',
  latencyMs: 30,
  estimatedInputTokens: 10,
  estimatedCost: 0.00002,
  currency: 'USD',
  trace: [
    { order: 1, stage: 'INPUT', name: 'Input', detail: '20 characters', status: 'VALIDATED' },
    { order: 2, stage: 'MODEL', name: 'chat-default', detail: 'GPT-4o', status: 'PRIMARY_READY' }
  ],
  executeTime: '2026-07-16T00:00:00Z',
  traceId: 'trace'
}

describe('ScenePlayground', () => {
  it('renders explicit preview output, model metrics, and trace', () => {
    const wrapper = mount(ScenePlayground, {
      props: { open: true, scene, result }
    })

    expect(wrapper.text()).toContain('PREVIEW')
    expect(wrapper.text()).toContain('GPT-4o')
    expect(wrapper.text()).toContain('Scene configuration validated.')
    expect(wrapper.text()).toContain('INPUT')
    expect(wrapper.text()).toContain('PRIMARY_READY')
  })
})
