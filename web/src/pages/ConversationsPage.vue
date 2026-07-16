<script setup lang="ts">
import RuntimeConsole from '../components/RuntimeConsole.vue'
import { t } from '../i18n'
import type { RuntimeAction } from '../types/runtime'

const actions: RuntimeAction[] = [
  { label: t('runtime.send'), path: '/api/v1/ai/conversations/{id}/messages', payload: { content: 'Explain the current runtime status.', contentType: 'TEXT', parameters: {} }, tone: 'primary' },
  { label: t('runtime.summary'), path: '/api/v1/ai/conversations/{id}/summary', tone: 'emphasis' },
  { label: t('runtime.export'), method: 'GET', path: '/api/v1/ai/conversations/{id}/export' },
  { label: t('runtime.replay'), path: '/api/v1/ai/conversations/{id}/replay', payload: {}, tone: 'emphasis' }
]
</script>

<template>
  <RuntimeConsole
    :title="t('conversations.title')"
    :subtitle="t('conversations.subtitle')"
    list-url="/api/v1/ai/conversations"
    create-url="/api/v1/ai/conversations"
    :create-payload="{ title: 'Runtime Conversation', sceneCode: 'chat', tags: ['console'] }"
    :actions="actions"
  />
</template>
