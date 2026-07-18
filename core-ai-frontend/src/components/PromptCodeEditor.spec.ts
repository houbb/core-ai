import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PromptCodeEditor from './PromptCodeEditor.vue'

describe('PromptCodeEditor', () => {
  it('shows variable completion and inserts the selected token', async () => {
    const wrapper = mount(PromptCodeEditor, {
      props: {
        modelValue: 'Translate {{',
        label: 'User Prompt',
        variables: ['content', 'language']
      }
    })

    const textarea = wrapper.get('textarea')
    ;(textarea.element as HTMLTextAreaElement).setSelectionRange(
      textarea.element.value.length,
      textarea.element.value.length
    )
    await textarea.trigger('keyup')

    expect(wrapper.get('[data-testid="variable-suggestions"]').text()).toContain('language')
    await wrapper.findAll('[data-testid="variable-suggestions"] button')[1].trigger('click')

    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['Translate {{language}}'])
  })
})
