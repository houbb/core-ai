import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ModelDetail from './ModelDetail.vue'
import type { AiModel } from '../types/model'

const model: AiModel = {
  id: 'model-1',
  providerId: 'provider-1',
  providerCode: 'openai',
  providerName: 'OpenAI',
  providerEnabled: true,
  remoteModelId: 'gpt-4o',
  displayName: 'GPT-4o',
  category: 'CHAT',
  status: 'REGISTERED',
  enabled: false,
  availableFromProvider: true,
  recommended: false,
  favorite: false,
  contextManuallyOverridden: false,
  capabilities: ['CHAT'],
  capabilityOverrides: {},
  parameters: {},
  pricingHistory: [],
  aliases: [],
  tags: [],
  createTime: '2026-07-16T00:00:00Z',
  updateTime: '2026-07-16T00:00:00Z',
  createUser: 'test',
  updateUser: 'test'
}

describe('ModelDetail', () => {
  it('disables lifecycle mutations while another model operation is running', () => {
    const wrapper = mount(ModelDetail, {
      props: { model, audit: [], busy: true }
    })

    const lifecycleButtons = wrapper.findAll('.lifecycle-actions button')
    expect(lifecycleButtons).toHaveLength(2)
    expect(lifecycleButtons.every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })
})
