import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PromptTestsPanel from './PromptTestsPanel.vue'

describe('PromptTestsPanel', () => {
  it('creates a deterministic render test and exposes pass state', async () => {
    const wrapper = mount(PromptTestsPanel, {
      props: {
        testCases: [{
          id: 'test',
          name: 'Translation',
          inputJson: '{"content":"Hello"}',
          enabled: true,
          lastPassed: true
        }],
        busy: false,
        canEdit: true,
        canRun: true
      }
    })

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('New render test')
    const textareas = wrapper.findAll('textarea')
    await textareas[0].setValue('{"content":"World"}')
    await wrapper.findAll('.button-emphasis')[0].trigger('click')

    expect(wrapper.text()).toContain('PASS')
    expect(wrapper.emitted('create')?.[0]?.[0]).toMatchObject({
      name: 'New render test',
      inputJson: '{"content":"World"}',
      enabled: true
    })
  })
})
