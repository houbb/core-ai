import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RuntimeConsole from './RuntimeConsole.vue'

describe('RuntimeConsole', () => {
  afterEach(() => vi.restoreAllMocks())

  it('loads resources and resolves action placeholders', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(response([{ id: 'tool-1', code: 'mock-tool', name: 'Mock Tool', status: 'PUBLISHED' }]))
      .mockResolvedValueOnce(response({ status: 'SUCCESS', mode: 'LOCAL' }))
      .mockResolvedValueOnce(response([{ id: 'tool-1', code: 'mock-tool', name: 'Mock Tool', status: 'PUBLISHED' }]))

    const wrapper = mount(RuntimeConsole, {
      props: {
        title: 'AI Tools',
        subtitle: 'Tool Runtime',
        listUrl: '/api/tools',
        actions: [{
          label: 'Execute',
          path: '/api/tools/{code}/execute',
          payload: { value: 1 },
          tone: 'primary'
        }]
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Mock Tool')
    expect(wrapper.text()).toContain('PUBLISHED')
    await wrapper.get('button.button-primary').trigger('click')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/tools/mock-tool/execute',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ value: 1 })
      })
    )
    expect(wrapper.text()).toContain('SUCCESS')
  })
})

function response(value: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: async () => value
  } as Response
}
