import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProviderList from './ProviderList.vue'
import type { Provider } from '../types/provider'

const provider: Provider = {
  id: 'provider-1',
  code: 'openai',
  name: 'OpenAI',
  type: 'OPENAI_COMPATIBLE',
  location: 'CLOUD',
  endpoint: 'https://api.openai.com/v1',
  enabled: true,
  status: 'AVAILABLE',
  priority: 1,
  weight: 100,
  timeoutSeconds: 15,
  retryCount: 0,
  tags: ['cloud', 'production'],
  tlsVerify: true,
  headers: {},
  customParameters: {},
  capabilities: ['CHAT', 'VISION'],
  health: { latencyMs: 86, availability: 99.9, rpm: 0, tpm: 0 },
  modelCount: 15,
  createTime: '2026-07-16T00:00:00Z',
  updateTime: '2026-07-16T00:00:00Z',
  createUser: 'test',
  updateUser: 'test'
}

describe('ProviderList', () => {
  it('shows provider health summary and emits selection', async () => {
    const wrapper = mount(ProviderList, {
      props: { providers: [provider], selectedId: provider.id }
    })

    expect(wrapper.text()).toContain('OpenAI')
    expect(wrapper.text()).toContain('15 模型')
    expect(wrapper.text()).toContain('86 ms')
    expect(wrapper.find('.provider-card').classes()).toContain('active')

    await wrapper.find('.provider-card').trigger('click')
    expect(wrapper.emitted('select')?.[0]).toEqual([provider])
  })

  it('shows onboarding action when empty', async () => {
    const wrapper = mount(ProviderList, { props: { providers: [] } })

    expect(wrapper.text()).toContain('连接第一个 AI Provider')
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('create')).toHaveLength(1)
  })
})
