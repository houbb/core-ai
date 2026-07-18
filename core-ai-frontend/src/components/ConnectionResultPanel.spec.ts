import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ConnectionResultPanel from './ConnectionResultPanel.vue'

describe('ConnectionResultPanel', () => {
  it('renders successful connection assertions', () => {
    const wrapper = mount(ConnectionResultPanel, {
      props: {
        result: {
          success: true,
          status: 'AVAILABLE',
          latencyMs: 86,
          modelCount: 4,
          capabilities: ['CHAT', 'VISION'],
          checks: [
            { name: 'endpoint', success: true, detail: 'OK' },
            { name: 'authentication', success: true, detail: 'OK' }
          ],
          message: 'Connection successful',
          userMessage: '连接成功'
        }
      }
    })

    expect(wrapper.text()).toContain('连接成功')
    expect(wrapper.text()).toContain('86 ms')
    expect(wrapper.text()).toContain('VISION')
    expect(wrapper.findAll('.check-row')).toHaveLength(2)
  })

  it('renders actionable failure code', () => {
    const wrapper = mount(ConnectionResultPanel, {
      props: {
        result: {
          success: false,
          status: 'DRAFT',
          latencyMs: 10,
          modelCount: 0,
          capabilities: [],
          checks: [{ name: 'authentication', success: false, detail: 'Unauthorized' }],
          errorCode: 'PROVIDER_AUTHENTICATION_FAILED',
          message: 'Authentication failed',
          userMessage: '请检查 API Key'
        }
      }
    })

    expect(wrapper.text()).toContain('连接失败')
    expect(wrapper.text()).toContain('请检查 API Key')
    expect(wrapper.text()).toContain('PROVIDER_AUTHENTICATION_FAILED')
  })
})
