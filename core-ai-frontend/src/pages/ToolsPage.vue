<script setup lang="ts">
import RuntimeConsole from '../components/RuntimeConsole.vue'
import { t } from '../i18n'
import type { RuntimeAction } from '../types/runtime'

const payload = {
  code: 'ui-mock-tool',
  name: 'UI Mock Tool',
  description: 'Deterministic local Tool',
  category: 'PLUGIN',
  toolType: 'PLUGIN',
  icon: 'hammer',
  schemaJson: '{"type":"object"}',
  outputSchemaJson: '{"type":"object"}',
  executorType: 'MOCK',
  executorConfig: { response: { ok: true, source: 'ui' } },
  parameters: [],
  chain: [],
  changeLog: 'Initial version',
  policy: {
    accessLevel: 'READ_ONLY',
    readonly: true,
    manualConfirm: false,
    approvalRequired: false,
    timeoutSeconds: 15,
    retryCount: 0,
    logContent: false,
    retentionDays: 7
  }
}

const actions: RuntimeAction[] = [
  { label: t('runtime.addTest'), path: '/api/v1/ai/admin/tools/{id}/test-cases', payload: { name: 'Smoke', inputJson: '{}', expectedResult: '{"ok":true,"source":"ui"}', enabled: true }, tone: 'emphasis' },
  { label: t('runtime.testing'), method: 'PATCH', path: '/api/v1/ai/admin/tools/{id}/status', payload: { status: 'TESTING' }, tone: 'emphasis' },
  { label: t('runtime.runTests'), path: '/api/v1/ai/admin/tools/{id}/tests/run', tone: 'primary' },
  { label: t('runtime.publish'), method: 'PATCH', path: '/api/v1/ai/admin/tools/{id}/status', payload: { status: 'PUBLISHED' }, tone: 'primary' },
  { label: t('runtime.execute'), path: '/api/v1/ai/tools/{code}/execute', payload: {}, tone: 'primary' }
]
</script>

<template>
  <RuntimeConsole
    :title="t('tools.title')"
    :subtitle="t('tools.subtitle')"
    list-url="/api/v1/ai/admin/tools"
    create-url="/api/v1/ai/admin/tools"
    :create-payload="payload"
    :actions="actions"
  />
</template>
