<script setup lang="ts">
import RuntimeConsole from '../components/RuntimeConsole.vue'
import { t } from '../i18n'
import type { RuntimeAction } from '../types/runtime'

const payload = {
  code: 'ui-knowledge',
  name: 'UI Knowledge',
  description: 'Local keyword and hybrid retrieval',
  category: 'DOCUMENT',
  visibility: 'PUBLIC',
  permissions: ['EVERYONE:*'],
  policy: {
    topK: 5,
    strategy: 'HYBRID',
    scoreThreshold: 0,
    mmrLambda: 0.5,
    metadataFilter: {},
    timeWeight: 0,
    chunkStrategy: 'PARAGRAPH',
    chunkSize: 256,
    chunkOverlap: 32
  }
}

const actions: RuntimeAction[] = [
  { label: t('runtime.addDocument'), path: '/api/v1/ai/admin/knowledge/{id}/documents', payload: { title: 'Runtime Guide', content: 'core-ai provides a local Gateway, Tool, Memory, Knowledge, Agent and Analytics runtime.', language: 'en', mimeType: 'text/plain', metadata: {}, permissions: ['EVERYONE:*'] }, tone: 'emphasis' },
  { label: t('runtime.process'), path: '/api/v1/ai/admin/knowledge/{id}/process', tone: 'primary' },
  { label: t('runtime.publish'), path: '/api/v1/ai/admin/knowledge/{id}/publish', tone: 'primary' },
  { label: t('runtime.search'), path: '/api/v1/ai/knowledge/{code}/search', payload: { query: 'local Gateway runtime', topK: 5 }, tone: 'emphasis' }
]
</script>

<template>
  <RuntimeConsole
    :title="t('knowledge.title')"
    :subtitle="t('knowledge.subtitle')"
    list-url="/api/v1/ai/admin/knowledge"
    create-url="/api/v1/ai/admin/knowledge"
    :create-payload="payload"
    :actions="actions"
  />
</template>
