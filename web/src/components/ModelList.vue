<script setup lang="ts">
import { t } from '../i18n'
import type { AiModel } from '../types/model'

defineProps<{
  models: AiModel[]
  selectedId?: string
  loading?: boolean
  compareMode?: boolean
  compareIds?: string[]
}>()

defineEmits<{
  select: [model: AiModel]
  compare: [model: AiModel]
  sync: []
}>()

function contextLabel(value?: number) {
  if (!value) return '—'
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${Math.round(value / 1_000)}K`
  return String(value)
}
</script>

<template>
  <aside class="model-list">
    <div v-if="loading" class="list-message">{{ t('common.loading') }}</div>
    <div v-else-if="models.length === 0" class="empty-state">
      <div class="empty-icon">◫</div>
      <h2>{{ t('models.emptyTitle') }}</h2>
      <p>{{ t('models.emptyText') }}</p>
      <button class="button button-primary" type="button" @click="$emit('sync')">
        {{ t('models.sync') }}
      </button>
    </div>
    <button
      v-for="model in models"
      :key="model.id"
      class="model-card"
      :class="{ active: selectedId === model.id }"
      type="button"
      @click="$emit('select', model)"
    >
      <label v-if="compareMode" class="compare-check" @click.stop>
        <input
          type="checkbox"
          :checked="compareIds?.includes(model.id)"
          @change="$emit('compare', model)"
        />
      </label>
      <div class="card-title">
        <div>
          <strong>{{ model.displayName }}</strong>
          <span>{{ model.providerName }}</span>
        </div>
        <span v-if="model.favorite" :title="t('models.favorite')">♥</span>
        <span v-if="model.recommended" :title="t('models.recommended')">★</span>
      </div>
      <div class="card-badges">
        <span class="badge">{{ model.category }}</span>
        <span class="badge" :class="{ 'badge-success': model.status === 'ENABLED', 'badge-warning': model.status === 'DISCOVERED' || model.status === 'REGISTERED' }">
          {{ model.status }}
        </span>
        <span v-if="!model.availableFromProvider" class="badge badge-danger">{{ t('models.unavailable') }}</span>
      </div>
      <div class="card-meta">
        <span>{{ t('models.context') }} {{ contextLabel(model.maxContextTokens) }}</span>
        <span>{{ model.capabilities.length }} {{ t('models.capabilities') }}</span>
      </div>
    </button>
  </aside>
</template>

<style scoped>
.model-list {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 8px;
  overflow: auto;
  border-right: 1px solid var(--border);
  background: rgba(250, 250, 252, 0.72);
}

.model-card {
  position: relative;
  display: grid;
  gap: 9px;
  width: 100%;
  margin-bottom: 6px;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 11px;
  text-align: left;
  color: var(--text);
  background: transparent;
}

.model-card:hover,
.model-card.active {
  border-color: rgba(0, 113, 227, 0.16);
  background: var(--accent-bg);
}

.card-title,
.card-title > div,
.card-badges,
.card-meta {
  display: flex;
  align-items: center;
}

.card-title {
  gap: 6px;
}

.card-title > div {
  display: grid;
  flex: 1;
  gap: 2px;
  min-width: 0;
}

.card-title strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-title span,
.card-meta {
  color: var(--text-secondary);
  font-size: 11px;
}

.card-badges {
  flex-wrap: wrap;
  gap: 4px;
}

.card-meta {
  justify-content: space-between;
  gap: 8px;
}

.compare-check {
  position: absolute;
  top: 10px;
  right: 10px;
}

.compare-check + .card-title {
  padding-right: 24px;
}

.empty-state,
.list-message {
  padding: 48px 20px;
  text-align: center;
}

.empty-state {
  display: grid;
  justify-items: center;
}

.empty-state h2 {
  margin: 10px 0 4px;
  font-size: 17px;
}

.empty-state p,
.list-message {
  color: var(--text-secondary);
  font-size: 13px;
}

.empty-state p {
  margin: 0 0 16px;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  color: var(--accent);
  background: var(--accent-bg);
  font-size: 22px;
}

@media (max-width: 900px) {
  .model-list {
    max-height: 300px;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }
}
</style>
