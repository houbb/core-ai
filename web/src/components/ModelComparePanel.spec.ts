import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ModelComparePanel from './ModelComparePanel.vue'
import type { AiModel } from '../types/model'

function model(id: string, context: number): AiModel {
  return {
    id,
    providerId: 'provider',
    providerCode: 'provider',
    providerName: 'Provider',
    providerEnabled: true,
    remoteModelId: id,
    displayName: id.toUpperCase(),
    category: 'CHAT',
    status: 'ENABLED',
    enabled: true,
    availableFromProvider: true,
    recommended: false,
    favorite: false,
    maxContextTokens: context,
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
}

describe('ModelComparePanel', () => {
  it('renders exact cross-model comparison columns', () => {
    const wrapper = mount(ModelComparePanel, {
      props: { models: [model('alpha', 32000), model('beta', 128000)] }
    })

    expect(wrapper.text()).toContain('ALPHA')
    expect(wrapper.text()).toContain('BETA')
    expect(wrapper.text()).toContain('32000')
    expect(wrapper.text()).toContain('128000')
    expect(wrapper.findAll('thead th')).toHaveLength(3)
  })
})
