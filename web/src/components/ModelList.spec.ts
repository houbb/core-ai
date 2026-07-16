import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ModelList from './ModelList.vue'
import type { AiModel } from '../types/model'

const model: AiModel = {
  id: 'model-1',
  providerId: 'provider-1',
  providerCode: 'openai',
  providerName: 'OpenAI',
  providerEnabled: true,
  providerLatencyMs: 80,
  remoteModelId: 'gpt-4o-2026-preview',
  displayName: 'GPT-4o',
  category: 'CHAT',
  status: 'ENABLED',
  enabled: true,
  availableFromProvider: true,
  recommended: true,
  favorite: true,
  maxContextTokens: 128000,
  contextManuallyOverridden: true,
  capabilities: ['CHAT', 'VISION', 'STREAMING'],
  capabilityOverrides: {},
  parameters: {},
  pricingHistory: [],
  aliases: [],
  tags: ['production'],
  createTime: '2026-07-16T00:00:00Z',
  updateTime: '2026-07-16T00:00:00Z',
  createUser: 'test',
  updateUser: 'test'
}

describe('ModelList', () => {
  it('uses friendly model identity and exposes compare selection', async () => {
    const wrapper = mount(ModelList, {
      props: {
        models: [model],
        selectedId: model.id,
        compareMode: true,
        compareIds: []
      }
    })

    expect(wrapper.text()).toContain('GPT-4o')
    expect(wrapper.text()).toContain('OpenAI')
    expect(wrapper.text()).not.toContain('gpt-4o-2026-preview')
    expect(wrapper.text()).toContain('128K')

    await wrapper.find('input[type="checkbox"]').trigger('change')
    expect(wrapper.emitted('compare')?.[0]).toEqual([model])
  })

  it('shows sync onboarding when registry is empty', async () => {
    const wrapper = mount(ModelList, { props: { models: [] } })

    expect(wrapper.text()).toContain('尚无模型')
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('sync')).toHaveLength(1)
  })
})
