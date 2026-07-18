import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PromptPlayground from './PromptPlayground.vue'

describe('PromptPlayground', () => {
  it('validates variables and emits server render request', async () => {
    const wrapper = mount(PromptPlayground, {
      props: {
        open: true,
        variables: [{
          name: 'content',
          type: 'STRING',
          required: true,
          defaultValue: 'Hello'
        }],
        busy: false
      }
    })

    await wrapper.get('textarea').setValue('{"content":"Enterprise"}')
    await wrapper.get('.button-primary').trigger('click')

    expect(wrapper.emitted('render')).toEqual([[{ content: 'Enterprise' }]])
  })

  it('renders prompt token evidence', () => {
    const wrapper = mount(PromptPlayground, {
      props: {
        open: true,
        variables: [],
        busy: false,
        result: {
          promptId: 'id',
          promptCode: 'translate',
          version: 2,
          systemPrompt: 'System',
          userPrompt: 'Hello',
          chain: [],
          characterCount: 13,
          estimatedTokens: 4,
          strictSchema: false,
          mode: 'PLAYGROUND'
        }
      }
    })

    expect(wrapper.get('[data-testid="rendered-prompt"]').text()).toContain('System')
    expect(wrapper.text()).toContain('4 tokens')
  })
})
