<script setup lang="ts">
import { t } from '../i18n'
import type { PromptSummary } from '../types/prompt'

defineProps<{
  prompts: PromptSummary[]
  selectedId?: string
  loading: boolean
}>()

defineEmits<{
  select: [prompt: PromptSummary]
  create: []
}>()
</script>

<template>
  <aside class="prompt-list">
    <div v-if="loading" class="state">{{ t('common.loading') }}</div>
    <button
      v-for="prompt in prompts"
      :key="prompt.id"
      class="prompt-item"
      :class="{ active: selectedId === prompt.id }"
      type="button"
      @click="$emit('select', prompt)"
    >
      <span class="prompt-icon">{{ prompt.name.slice(0, 1).toUpperCase() }}</span>
      <span class="prompt-copy">
        <strong>{{ prompt.name }}</strong>
        <small>{{ prompt.code }} · V{{ prompt.currentVersion }}</small>
      </span>
      <span class="badge" :class="{
        'badge-success': prompt.status === 'PUBLISHED',
        'badge-warning': prompt.status === 'TESTING',
        'badge-danger': prompt.status === 'DEPRECATED'
      }">{{ prompt.status }}</span>
    </button>
    <div v-if="!loading && !prompts.length" class="empty">
      <strong>{{ t('prompts.emptyTitle') }}</strong>
      <span>{{ t('prompts.emptyText') }}</span>
      <button class="button button-primary" type="button" @click="$emit('create')">
        {{ t('prompts.new') }}
      </button>
    </div>
  </aside>
</template>

<style scoped>
.prompt-list {
  min-width: 0;
  overflow: auto;
  border-right: 1px solid var(--border);
  background: rgba(248, 248, 250, 0.82);
}

.prompt-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 11px 12px;
  border: 0;
  border-bottom: 1px solid var(--border);
  color: var(--text);
  background: transparent;
  text-align: left;
}

.prompt-item:hover,
.prompt-item.active {
  background: var(--accent-bg);
}

.prompt-item.active {
  box-shadow: inset 3px 0 var(--accent);
}

.prompt-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  color: var(--accent);
  background: white;
  font-size: 12px;
  font-weight: 800;
}

.prompt-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.prompt-copy strong,
.prompt-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prompt-copy strong {
  font-size: 13px;
}

.prompt-copy small,
.state,
.empty span {
  color: var(--text-secondary);
  font-size: 11px;
}

.state,
.empty {
  padding: 24px 14px;
}

.empty {
  display: grid;
  gap: 10px;
  justify-items: start;
}

@media (max-width: 900px) {
  .prompt-list {
    max-height: 280px;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }
}
</style>
