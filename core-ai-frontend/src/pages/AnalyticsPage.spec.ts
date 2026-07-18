import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AnalyticsPage from './AnalyticsPage.vue'

describe('AnalyticsPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('renders dashboard metrics and deterministic insight', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(response({
        requestCount: 12,
        successCount: 11,
        avgLatencyMs: 42,
        totalCost: 0,
        inputTokens: 100,
        outputTokens: 50,
        averageQuality: 4.7,
        rankings: [{ resourceType: 'GATEWAY', requests: 12 }],
        budgets: [],
        alerts: []
      }))
      .mockResolvedValueOnce(response({
        insight: 'Runtime is stable with deterministic local execution.'
      }))

    const wrapper = mount(AnalyticsPage)
    await flushPromises()

    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('150')
    expect(wrapper.text()).toContain('4.7')
    expect(wrapper.text()).toContain('Runtime is stable')
    expect(wrapper.text()).toContain('DETERMINISTIC')
  })
})

function response(value: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: async () => value
  } as Response
}
