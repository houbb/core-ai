import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SceneEditorModal from './SceneEditorModal.vue'

describe('SceneEditorModal', () => {
  it('creates a portable Scene configuration with one primary Alias', async () => {
    const wrapper = mount(SceneEditorModal, {
      props: {
        open: true,
        aliases: ['chat-default']
      }
    })

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('chat')
    await inputs[1].setValue('AI Chat')
    await wrapper.find('form').trigger('submit')

    const emitted = wrapper.emitted('save')?.[0]
    expect(emitted?.[0]).toBe('chat')
    expect(emitted?.[1]).toMatchObject({
      name: 'AI Chat',
      category: 'CONVERSATION',
      models: [{
        modelAlias: 'chat-default',
        priority: 10,
        fallback: false,
        enabled: true
      }],
      permissions: [{ type: 'EVERYONE', value: '*' }]
    })
  })
})
