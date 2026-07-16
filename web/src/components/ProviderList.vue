<script setup lang="ts">
import { t } from '../i18n'
import type { Provider } from '../types/provider'

defineProps<{
  providers: Provider[]
  selectedId?: string
  loading?: boolean
}>()

const emit = defineEmits<{
  select: [provider: Provider]
  create: []
}>()

function statusClass(provider: Provider) {
  const lastError = provider.health.lastError ? Date.parse(provider.health.lastError) : 0
  const lastSuccess = provider.health.lastSuccess ? Date.parse(provider.health.lastSuccess) : 0
  if (lastError > lastSuccess) return 'badge-danger'
  if (provider.status === 'AVAILABLE') return 'badge-success'
  if (provider.status === 'DRAFT' || provider.status === 'TESTING') return 'badge-warning'
  return ''
}
</script>

<template>
  <aside class="provider-list">
    <div v-if="loading" class="list-message">{{ t('common.loading') }}</div>
    <div v-else-if="providers.length === 0" class="empty-state">
      <div class="empty-icon">⌁</div>
      <h2>{{ t('providers.emptyTitle') }}</h2>
      <p>{{ t('providers.emptyText') }}</p>
      <button class="button button-primary" type="button" @click="emit('create')">
        {{ t('providers.new') }}
      </button>
    </div>
    <button
      v-for="provider in providers"
      :key="provider.id"
      class="provider-card"
      :class="{ active: provider.id === selectedId }"
      type="button"
      @click="emit('select', provider)"
    >
      <div class="card-heading">
        <div>
          <strong>{{ provider.name }}</strong>
          <span>{{ provider.code }}</span>
        </div>
        <span class="badge" :class="statusClass(provider)">{{ provider.status }}</span>
      </div>
      <div class="card-meta">
        <span>{{ provider.modelCount }} {{ t('providers.models') }}</span>
        <span>{{ provider.health.latencyMs ?? '—' }} ms</span>
        <span>{{ provider.location === 'LOCAL' ? t('providers.local') : t('providers.cloud') }}</span>
      </div>
      <div v-if="provider.tags.length" class="tag-row">
        <span v-for="tag in provider.tags.slice(0, 3)" :key="tag" class="badge">{{ tag }}</span>
      </div>
    </button>
  </aside>
</template>

<style scoped>
.provider-list {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 8px;
  overflow: auto;
  border-right: 1px solid var(--border);
  background: rgba(250, 250, 252, 0.72);
}

.provider-card {
  display: grid;
  gap: 10px;
  width: 100%;
  margin: 0 0 6px;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 11px;
  text-align: left;
  color: var(--text);
  background: transparent;
}

.provider-card:hover,
.provider-card.active {
  border-color: rgba(0, 113, 227, 0.16);
  background: var(--accent-bg);
}

.card-heading,
.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-heading > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.card-heading strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-heading span,
.card-meta {
  color: var(--text-secondary);
  font-size: 11px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.empty-state {
  display: grid;
  justify-items: center;
  padding: 48px 20px;
  text-align: center;
}

.empty-state h2 {
  margin: 10px 0 4px;
  font-size: 17px;
}

.empty-state p {
  margin: 0 0 16px;
  color: var(--text-secondary);
  font-size: 13px;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  color: var(--accent);
  background: var(--accent-bg);
  font-size: 24px;
}

.list-message {
  padding: 20px;
  color: var(--text-secondary);
  text-align: center;
  font-size: 13px;
}

@media (max-width: 860px) {
  .provider-list {
    max-height: 280px;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }
}
</style>
