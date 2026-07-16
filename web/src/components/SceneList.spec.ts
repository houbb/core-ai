import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SceneList from './SceneList.vue'
import type { AiScene } from '../types/scene'

const scene: AiScene = {
  id: 'scene-1',
  code: 'chat',
  name: 'AI Chat',
  description: 'Chat scene',
  category: 'CONVERSATION',
  icon: '💬',
  status: 'PUBLISHED',
  enabled: true,
  version: 1,
  recommended: true,
  models: [{
    modelAlias: 'chat-default',
    priority: 10,
    fallback: false,
    enabled: true,
    resolved: true,
    modelId: 'internal-model-id',
    modelDisplayName: 'GPT-4o',
    providerName: 'OpenAI'
  }],
  parameters: { jsonMode: false, streaming: true },
  prompt: {},
  permissions: [{ type: 'EVERYONE', value: '*' }],
  workflow: [],
  costTier: 'STANDARD',
  createTime: '2026-07-16T00:00:00Z',
  updateTime: '2026-07-16T00:00:00Z',
  createUser: 'test',
  updateUser: 'test'
}

describe('SceneList', () => {
  it('shows business identity, resolved model, status, and cost without model ids', async () => {
    const wrapper = mount(SceneList, {
      props: { scenes: [scene], selectedId: scene.id }
    })

    expect(wrapper.text()).toContain('AI Chat')
    expect(wrapper.text()).toContain('GPT-4o')
    expect(wrapper.text()).toContain('PUBLISHED')
    expect(wrapper.text()).toContain('STANDARD')
    expect(wrapper.text()).not.toContain('internal-model-id')

    await wrapper.find('.scene-card').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([scene])
  })
})
