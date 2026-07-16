import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ModelEditorModal from './ModelEditorModal.vue'
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
  maxContextTokens: 128000,
  maxOutputTokens: 16384,
  contextManuallyOverridden: false,
  capabilities: ['CHAT', 'STREAMING'],
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

describe('ModelEditorModal', () => {
  it('requires at least one price value before submitting a price version', async () => {
    const wrapper = mount(ModelEditorModal, {
      props: { open: true, model }
    })
    const pricingTab = wrapper.findAll('.editor-tabs button')
      .find((button) => button.text() === '价格')

    await pricingTab!.trigger('click')
    const form = wrapper.find('form')
    const submit = form.find('button[type="submit"]')
    expect(submit.attributes('disabled')).toBeDefined()

    await form.findAll('input[type="number"]')[0].setValue(0)
    expect(submit.attributes('disabled')).toBeUndefined()
  })

  it('keeps alias input intact until the parent confirms persistence', async () => {
    const wrapper = mount(ModelEditorModal, {
      props: { open: true, model }
    })
    const aliasTab = wrapper.findAll('.editor-tabs button')
      .find((button) => button.text() === '别名')

    await aliasTab!.trigger('click')
    const aliasInput = wrapper.find('input[pattern]')
    await aliasInput.setValue('chat-default')
    await wrapper.find('.alias-editor form').trigger('submit')

    expect(wrapper.emitted('alias')?.[0]).toEqual([
      undefined,
      {
        alias: 'chat-default',
        modelId: 'model-1',
        scene: '',
        priority: 100,
        enabled: true
      }
    ])
    expect((aliasInput.element as HTMLInputElement).value).toBe('chat-default')
  })
})
