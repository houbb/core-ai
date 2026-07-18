<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { runtimeRequest } from '../api/runtime'
import { ApiError } from '../api/providers'
import { t } from '../i18n'
import type { RuntimeAction } from '../types/runtime'

const props = defineProps<{
  title: string
  subtitle: string
  listUrl: string
  createUrl?: string
  createPayload?: unknown
  actions?: RuntimeAction[]
}>()

const items = ref<Record<string, unknown>[]>([])
const selected = ref<Record<string, unknown>>()
const result = ref<unknown>()
const editor = ref(JSON.stringify(props.createPayload || {}, null, 2))
const busy = ref(false)
const notice = ref<{ type: 'success' | 'error'; text: string }>()

const selectedId = computed(() => String(selected.value?.id || ''))
const selectedCode = computed(() => String(selected.value?.code || selected.value?.id || ''))

onMounted(load)

async function load() {
  busy.value = true
  try {
    const previousId = selectedId.value
    const response = await runtimeRequest<unknown>(props.listUrl)
    items.value = Array.isArray(response) ? response as Record<string, unknown>[] : [response as Record<string, unknown>]
    selected.value = items.value.find(item => String(item.id || '') === previousId) || items.value[0]
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function create() {
  if (!props.createUrl) return
  busy.value = true
  try {
    result.value = await runtimeRequest(props.createUrl, {
      method: 'POST',
      body: JSON.stringify(JSON.parse(editor.value || '{}'))
    })
    notice.value = { type: 'success', text: t('runtime.created') }
    await load()
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function run(action: RuntimeAction) {
  if ((action.path.includes('{id}') || action.path.includes('{code}')) && !selected.value) return
  busy.value = true
  try {
    const path = action.path
      .replace('{id}', encodeURIComponent(selectedId.value))
      .replace('{code}', encodeURIComponent(selectedCode.value))
    result.value = await runtimeRequest(path, {
      method: action.method || 'POST',
      body: action.payload === undefined ? undefined : JSON.stringify(action.payload)
    })
    notice.value = { type: 'success', text: `${action.label} · ${t('common.saved')}` }
    await load()
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

function label(item: Record<string, unknown>) {
  return String(item.name || item.title || item.code || item.id || t('common.unknown'))
}

function secondary(item: Record<string, unknown>) {
  return [item.code, item.status, item.category].filter(Boolean).join(' · ')
}

function showError(error: unknown) {
  notice.value = {
    type: 'error',
    text: error instanceof ApiError
      ? `${error.message}${error.errorCode ? ` (${error.errorCode})` : ''}`
      : error instanceof Error ? error.message : t('common.failed')
  }
}
</script>

<template>
  <section class="runtime-page">
    <header class="page-header">
      <div>
        <h1>{{ title }}</h1>
        <p>{{ subtitle }}</p>
      </div>
      <button class="button button-emphasis" type="button" :disabled="busy" @click="load">
        {{ t('runtime.refresh') }}
      </button>
    </header>

    <div v-if="notice" class="notice" :class="notice.type">
      <span>{{ notice.text }}</span>
      <button type="button" @click="notice = undefined">×</button>
    </div>

    <div class="runtime-grid">
      <aside class="runtime-list surface">
        <header>
          <strong>{{ t('runtime.resources') }}</strong>
          <span class="badge">{{ items.length }}</span>
        </header>
        <button
          v-for="item in items"
          :key="String(item.id || item.code)"
          class="runtime-item"
          :class="{ active: selected === item }"
          type="button"
          @click="selected = item"
        >
          <strong>{{ label(item) }}</strong>
          <span>{{ secondary(item) }}</span>
        </button>
        <p v-if="!items.length" class="empty">{{ busy ? t('common.loading') : t('runtime.empty') }}</p>
      </aside>

      <main class="runtime-main">
        <section v-if="selected" class="surface selected-card">
          <header>
            <div>
              <h2>{{ label(selected) }}</h2>
              <span class="badge badge-success">{{ selected.status || t('runtime.ready') }}</span>
            </div>
            <div class="actions">
              <button
                v-for="action in actions"
                :key="action.label"
                class="button"
                :class="`button-${action.tone || 'secondary'}`"
                type="button"
                :disabled="busy"
                @click="run(action)"
              >
                {{ action.label }}
              </button>
            </div>
          </header>
          <pre>{{ JSON.stringify(selected, null, 2) }}</pre>
        </section>

        <section v-if="createUrl" class="surface create-card">
          <header>
            <div>
              <h2>{{ t('runtime.quickCreate') }}</h2>
              <p>{{ t('runtime.quickCreateHint') }}</p>
            </div>
            <button class="button button-primary" type="button" :disabled="busy" @click="create">
              {{ t('common.new') }}
            </button>
          </header>
          <textarea v-model="editor" class="input json-editor" spellcheck="false" />
        </section>

        <section class="surface result-card">
          <header>
            <h2>{{ t('runtime.lastResult') }}</h2>
            <span class="badge">{{ result ? t('runtime.ready') : t('runtime.notRun') }}</span>
          </header>
          <pre>{{ result ? JSON.stringify(result, null, 2) : t('runtime.resultHint') }}</pre>
        </section>
      </main>
    </div>
  </section>
</template>

<style scoped>
.runtime-page {
  width: 100%;
}

.page-header,
.surface > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-header {
  margin-bottom: 16px;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  font-size: 24px;
}

h2 {
  font-size: 17px;
}

.page-header p,
.surface header p {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
}

.runtime-grid {
  display: grid;
  grid-template-columns: minmax(220px, 0.28fr) minmax(0, 1fr);
  gap: 14px;
}

.surface {
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.runtime-list {
  align-self: start;
  display: grid;
  gap: 8px;
}

.runtime-item {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  text-align: left;
  background: transparent;
}

.runtime-item:hover,
.runtime-item.active {
  border-color: rgba(0, 113, 227, 0.22);
  background: var(--accent-bg);
}

.runtime-item strong {
  font-size: 13px;
}

.runtime-item span,
.empty {
  color: var(--text-secondary);
  font-size: 11px;
}

.runtime-main {
  display: grid;
  gap: 14px;
}

.selected-card > header > div:first-child {
  display: flex;
  align-items: center;
  gap: 8px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

pre {
  max-height: 380px;
  margin: 12px 0 0;
  padding: 12px;
  overflow: auto;
  border-radius: 10px;
  color: #d7e3ff;
  background: #172033;
  font: 12px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: pre-wrap;
  word-break: break-word;
}

.json-editor {
  min-height: 360px;
  margin-top: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.notice {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
}

.notice.success {
  color: var(--success);
  background: var(--success-bg);
}

.notice.error {
  color: var(--danger);
  background: var(--danger-bg);
}

.notice button {
  border: 0;
  color: inherit;
  background: transparent;
}

@media (max-width: 820px) {
  .runtime-grid {
    grid-template-columns: 1fr;
  }
}
</style>
