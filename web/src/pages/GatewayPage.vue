<script setup lang="ts">
import RuntimeConsole from '../components/RuntimeConsole.vue'
import { t } from '../i18n'
import type { RuntimeAction } from '../types/runtime'

const actions: RuntimeAction[] = [
  { label: t('runtime.invoke'), path: '/api/v1/ai/gateway/invoke', payload: { aliasCode: 'chat-default', sceneCode: 'console', input: 'Gateway console probe', parameters: {}, cacheable: true, streaming: false }, tone: 'primary' },
  { label: t('runtime.dashboard'), method: 'GET', path: '/api/v1/ai/admin/gateways/{id}/dashboard', tone: 'emphasis' },
  { label: t('runtime.traces'), method: 'GET', path: '/api/v1/ai/admin/gateways/{id}/traces?limit=20' }
]
</script>

<template>
  <RuntimeConsole
    :title="t('gateway.title')"
    :subtitle="t('gateway.subtitle')"
    list-url="/api/v1/ai/admin/gateways"
    :actions="actions"
  />
</template>
