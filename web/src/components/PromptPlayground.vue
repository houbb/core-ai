<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { t } from '../i18n'
import type { PromptRenderResult, PromptVariable } from '../types/prompt'

const props = defineProps<{
  open: boolean
  variables: PromptVariable[]
  result?: PromptRenderResult
  busy: boolean
}>()

const emit = defineEmits<{
  close: []
  render: [variables: Record<string, unknown>]
}>()

const input = ref('{}')
const error = ref('')

watch(() => props.open, open => {
  if (!open) return
  const sample: Record<string, unknown> = {}
  props.variables.forEach(variable => {
    if (variable.defaultValue !== undefined && variable.defaultValue !== '') {
      if (variable.type === 'STRING') sample[variable.name] = variable.defaultValue
      else {
        try { sample[variable.name] = JSON.parse(variable.defaultValue) } catch { sample[variable.name] = variable.defaultValue }
      }
    } else if (variable.required) {
      sample[variable.name] = variable.type === 'BOOLEAN' ? false
        : variable.type === 'INTEGER' ? 0
          : variable.type === 'LIST' ? []
            : variable.type === 'OBJECT' || variable.type === 'JSON' ? {}
              : ''
    }
  })
  input.value = JSON.stringify(sample, null, 2)
  error.value = ''
})

const combined = computed(() => [
  props.result?.systemPrompt,
  props.result?.userPrompt,
  props.result?.assistantPrompt
].filter(Boolean).join('\n\n'))

function run() {
  try {
    const variables = JSON.parse(input.value)
    if (!variables || Array.isArray(variables) || typeof variables !== 'object') throw new Error()
    error.value = ''
    emit('render', variables)
  } catch {
    error.value = t('prompts.invalidVariables')
  }
}

async function copy(value: string) {
  await navigator.clipboard?.writeText(value)
}
</script>

<template>
  <div v-if="open" class="backdrop" @click.self="$emit('close')">
    <section class="playground" role="dialog" aria-modal="true">
      <header>
        <div>
          <h2>{{ t('prompts.playground') }}</h2>
          <p>{{ t('prompts.playgroundHint') }}</p>
        </div>
        <button class="close" type="button" @click="$emit('close')">×</button>
      </header>
      <div class="playground-grid">
        <div class="input-side">
          <label>{{ t('prompts.variablesJson') }}</label>
          <textarea v-model="input" class="input json-input" spellcheck="false" />
          <p v-if="error" class="error">{{ error }}</p>
          <button class="button button-primary" type="button" :disabled="busy" @click="run">
            {{ busy ? t('common.loading') : t('prompts.render') }}
          </button>
        </div>
        <div class="output-side">
          <div v-if="result" class="result-meta">
            <span class="badge badge-success">{{ result.mode }}</span>
            <span>V{{ result.version }}</span>
            <span>{{ result.estimatedTokens }} tokens</span>
            <span>{{ result.characterCount }} chars</span>
          </div>
          <pre v-if="result" data-testid="rendered-prompt">{{ combined }}</pre>
          <div v-else class="empty">{{ t('prompts.renderEmpty') }}</div>
          <div v-if="result" class="copy-actions">
            <button class="button button-secondary" type="button" @click="copy(combined)">{{ t('prompts.copyRender') }}</button>
            <button class="button button-secondary" type="button" @click="copy(JSON.stringify(result, null, 2))">{{ t('prompts.copyJson') }}</button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed;
  z-index: 60;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.34);
  backdrop-filter: blur(8px);
}

.playground {
  width: min(1100px, 100%);
  max-height: calc(100vh - 32px);
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--surface-solid);
  box-shadow: var(--shadow);
}

header,
.result-meta,
.copy-actions {
  display: flex;
  align-items: center;
}

header {
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

h2,
p {
  margin: 0;
}

h2 {
  font-size: 17px;
}

header p {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 11px;
}

.close {
  border: 0;
  color: var(--text-secondary);
  background: transparent;
  font-size: 24px;
}

.playground-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.8fr) minmax(0, 1.2fr);
}

.input-side,
.output-side {
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 480px;
  padding: 14px;
}

.input-side {
  border-right: 1px solid var(--border);
}

label {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 700;
}

.json-input {
  min-height: 360px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.result-meta,
.copy-actions {
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 11px;
}

pre {
  min-height: 380px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 10px;
  color: #f2f2f7;
  background: #1c1c1e;
  white-space: pre-wrap;
  font: 12px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace;
}

.empty {
  display: grid;
  min-height: 380px;
  place-items: center;
  color: var(--text-secondary);
  border: 1px dashed var(--border);
  border-radius: 10px;
  font-size: 12px;
}

.error {
  color: var(--danger);
  font-size: 11px;
}

@media (max-width: 760px) {
  .playground-grid {
    grid-template-columns: 1fr;
  }

  .input-side {
    min-height: auto;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }
}
</style>
