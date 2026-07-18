<script setup lang="ts">
import RuntimeConsole from '../components/RuntimeConsole.vue'
import { t } from '../i18n'
import type { RuntimeAction } from '../types/runtime'

const payload = {
  code: 'ui-agent',
  name: 'UI Runtime Agent',
  description: 'Deterministic Agent using the unified Gateway',
  sceneCode: 'agent',
  icon: 'sparkles',
  color: '#5e5ce6',
  tags: ['console'],
  profile: {
    role: 'Runtime operator',
    goal: 'Complete the requested goal safely',
    personality: 'Precise',
    style: 'Concise',
    language: 'zh-CN',
    constraints: 'Do not execute external side effects without approval.'
  },
  planner: { type: 'DETERMINISTIC', config: {}, maxSteps: 10, maxDepth: 3, retryCount: 1 },
  tasks: [
    { name: 'Gateway response', orderNo: 1, type: 'GATEWAY', referenceId: 'agent-default', executionMode: 'SERIAL', condition: {}, config: {}, enabled: true }
  ],
  tools: [],
  knowledge: [],
  memory: { policy: 'USER', ownerTypes: ['USER'], writeEnabled: false, maxItems: 20, config: {} }
}

const actions: RuntimeAction[] = [
  { label: t('runtime.runTests'), path: '/api/v1/ai/admin/agents/{id}/tests/run', tone: 'emphasis' },
  { label: t('runtime.testing'), method: 'PATCH', path: '/api/v1/ai/admin/agents/{id}/status', payload: { status: 'TESTING' }, tone: 'emphasis' },
  { label: t('runtime.publish'), method: 'PATCH', path: '/api/v1/ai/admin/agents/{id}/status', payload: { status: 'PUBLISHED' }, tone: 'primary' },
  { label: t('runtime.execute'), path: '/api/v1/ai/agents/{code}/execute', payload: { goal: 'Report core runtime readiness.' }, tone: 'primary' }
]
</script>

<template>
  <RuntimeConsole
    :title="t('agents.title')"
    :subtitle="t('agents.subtitle')"
    list-url="/api/v1/ai/admin/agents"
    create-url="/api/v1/ai/admin/agents"
    :create-payload="payload"
    :actions="actions"
  />
</template>
