<script setup lang="ts">
import { t } from '../i18n'
import type { AiScene } from '../types/scene'

defineProps<{
  scenes: AiScene[]
  selectedId?: string
  loading?: boolean
}>()

defineEmits<{
  select: [scene: AiScene]
  templates: []
}>()

function primary(scene: AiScene) {
  return scene.models.find((model) => model.enabled && !model.fallback)
}
</script>

<template>
  <aside class="scene-list">
    <div v-if="loading" class="list-message">{{ t('common.loading') }}</div>
    <div v-else-if="!scenes.length" class="empty-state">
      <div class="empty-icon">✦</div>
      <h2>{{ t('scenes.emptyTitle') }}</h2>
      <p>{{ t('scenes.emptyText') }}</p>
      <button class="button button-primary" type="button" @click="$emit('templates')">
        {{ t('scenes.browseTemplates') }}
      </button>
    </div>
    <button
      v-for="scene in scenes"
      :key="scene.id"
      class="scene-card"
      :class="{ active: selectedId === scene.id }"
      type="button"
      @click="$emit('select', scene)"
    >
      <span class="scene-icon">{{ scene.icon || '✦' }}</span>
      <div class="scene-main">
        <div class="scene-title">
          <strong>{{ scene.name }}</strong>
          <span v-if="scene.recommended" :title="t('scenes.recommended')">★</span>
        </div>
        <span>{{ scene.category }} · V{{ scene.version }}</span>
        <small>
          {{ primary(scene)?.modelDisplayName || primary(scene)?.modelAlias || t('scenes.unresolved') }}
        </small>
        <div class="badges">
          <span
            class="badge"
            :class="{
              'badge-success': scene.status === 'PUBLISHED',
              'badge-warning': scene.status === 'DRAFT' || scene.status === 'TESTING',
              'badge-danger': scene.status === 'ARCHIVED'
            }"
          >
            {{ scene.status }}
          </span>
          <span class="badge">{{ scene.costTier }}</span>
        </div>
      </div>
    </button>
  </aside>
</template>

<style scoped>
.scene-list {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 8px;
  overflow: auto;
  border-right: 1px solid var(--border);
  background: rgba(250, 250, 252, 0.72);
}

.scene-card {
  display: flex;
  gap: 10px;
  width: 100%;
  margin-bottom: 6px;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 11px;
  text-align: left;
  color: var(--text);
  background: transparent;
}

.scene-card:hover,
.scene-card.active {
  border-color: rgba(0, 113, 227, 0.16);
  background: var(--accent-bg);
}

.scene-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  border-radius: 11px;
  background: var(--surface-solid);
  font-size: 20px;
}

.scene-main {
  display: grid;
  flex: 1;
  gap: 4px;
  min-width: 0;
}

.scene-title,
.badges {
  display: flex;
  align-items: center;
  gap: 5px;
}

.scene-title strong {
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scene-main > span,
.scene-main small {
  color: var(--text-secondary);
  font-size: 11px;
}

.badges {
  flex-wrap: wrap;
  margin-top: 2px;
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

@media (max-width: 900px) {
  .scene-list {
    max-height: 310px;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }
}
</style>
