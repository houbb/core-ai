<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { t } from '../i18n'
import type { Provider, ProviderInput, ProviderPreset, ProviderType } from '../types/provider'

const props = defineProps<{
  open: boolean
  presets: ProviderPreset[]
  provider?: Provider
  saving?: boolean
}>()

const emit = defineEmits<{
  close: []
  save: [input: ProviderInput]
}>()

const selectedPreset = ref<ProviderPreset>()
const advancedOpen = ref(false)
const error = ref('')
const tagsText = ref('')
const headersText = ref('')
const parametersText = ref('{}')

const draft = reactive<ProviderInput>({
  code: '',
  name: '',
  description: '',
  type: 'OPENAI_COMPATIBLE',
  endpoint: '',
  priority: 100,
  weight: 100,
  timeoutSeconds: 15,
  retryCount: 0,
  apiKey: '',
  organization: '',
  proxy: '',
  tlsVerify: true,
  headers: {},
  customParameters: {},
  tags: []
})

const editing = computed(() => Boolean(props.provider))
const showForm = computed(() => editing.value || Boolean(selectedPreset.value))

watch(
  () => [props.open, props.provider] as const,
  () => {
    if (!props.open) return
    error.value = ''
    advancedOpen.value = false
    selectedPreset.value = undefined
    if (props.provider) {
      Object.assign(draft, {
        code: props.provider.code,
        name: props.provider.name,
        description: props.provider.description || '',
        type: props.provider.type,
        endpoint: props.provider.endpoint,
        priority: props.provider.priority,
        weight: props.provider.weight,
        timeoutSeconds: props.provider.timeoutSeconds,
        retryCount: props.provider.retryCount,
        apiKey: '',
        organization: props.provider.organization || '',
        proxy: props.provider.proxy || '',
        tlsVerify: props.provider.tlsVerify,
        headers: undefined,
        customParameters: props.provider.customParameters,
        tags: props.provider.tags
      })
      tagsText.value = props.provider.tags.join(', ')
      headersText.value = ''
      parametersText.value = JSON.stringify(props.provider.customParameters, null, 2)
    } else {
      resetDraft()
    }
  },
  { immediate: true }
)

function resetDraft() {
  Object.assign(draft, {
    code: '',
    name: '',
    description: '',
    type: 'OPENAI_COMPATIBLE' as ProviderType,
    endpoint: '',
    priority: 100,
    weight: 100,
    timeoutSeconds: 15,
    retryCount: 0,
    apiKey: '',
    organization: '',
    proxy: '',
    tlsVerify: true,
    headers: {},
    customParameters: {},
    tags: []
  })
  tagsText.value = ''
  headersText.value = '{}'
  parametersText.value = '{}'
}

function choosePreset(preset: ProviderPreset) {
  selectedPreset.value = preset
  Object.assign(draft, {
    code: preset.code,
    name: preset.name,
    type: preset.type,
    endpoint: preset.endpoint,
    customParameters: preset.customParameters,
    tags: [preset.location]
  })
  tagsText.value = preset.location
  parametersText.value = JSON.stringify(preset.customParameters, null, 2)
}

function parseObject(value: string, preserveWhenBlank: boolean): Record<string, string> | undefined {
  if (!value.trim() && preserveWhenBlank) return undefined
  const parsed: unknown = JSON.parse(value || '{}')
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error()
  return Object.fromEntries(Object.entries(parsed).map(([key, item]) => [key, String(item)]))
}

function submit() {
  try {
    error.value = ''
    const headers = parseObject(headersText.value, editing.value)
    const customParameters = parseObject(parametersText.value, false) || {}
    emit('save', {
      ...draft,
      headers,
      customParameters,
      tags: tagsText.value.split(',').map((tag) => tag.trim()).filter(Boolean),
      apiKey: draft.apiKey?.trim() || undefined
    })
  } catch {
    error.value = t('form.invalidJson')
  }
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" role="presentation" @mousedown.self="emit('close')">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="provider-form-title">
      <header>
        <div>
          <h2 id="provider-form-title">{{ editing ? t('providers.edit') : t('providers.new') }}</h2>
          <p>{{ showForm ? t('form.basic') : t('form.choosePreset') }}</p>
        </div>
        <button class="close-button" type="button" aria-label="Close" @click="emit('close')">×</button>
      </header>

      <div v-if="!showForm" class="preset-grid">
        <button
          v-for="preset in presets"
          :key="preset.code"
          class="preset-card"
          type="button"
          @click="choosePreset(preset)"
        >
          <span class="preset-icon">{{ preset.location === 'local' ? '⌂' : '☁' }}</span>
          <strong>{{ preset.name }}</strong>
          <small>{{ preset.type }}</small>
        </button>
      </div>

      <form v-else @submit.prevent="submit">
        <div class="form-grid">
          <div class="field"><label>{{ t('form.name') }}</label><input v-model="draft.name" class="input" required maxlength="200" /></div>
          <div class="field"><label>{{ t('form.code') }}</label><input v-model="draft.code" class="input" required pattern="[a-z0-9][a-z0-9._-]{1,99}" /></div>
          <div class="field field-wide"><label>{{ t('form.endpoint') }}</label><input v-model="draft.endpoint" class="input" required type="url" /></div>
          <div class="field field-wide"><label>{{ t('form.description') }}</label><textarea v-model="draft.description" class="input" maxlength="2000" /></div>
          <div class="field field-wide">
            <label>{{ t('form.apiKey') }} <span v-if="editing" class="muted">· {{ t('form.apiKeyKeep') }}</span></label>
            <input v-model="draft.apiKey" class="input" type="password" autocomplete="new-password" />
          </div>
          <div class="field"><label>{{ t('form.priority') }}</label><input v-model.number="draft.priority" class="input" type="number" min="0" max="10000" /></div>
          <div class="field"><label>{{ t('form.weight') }}</label><input v-model.number="draft.weight" class="input" type="number" min="1" max="1000" /></div>
          <div class="field field-wide"><label>{{ t('form.tags') }}</label><input v-model="tagsText" class="input" /></div>
        </div>

        <button class="advanced-toggle" type="button" @click="advancedOpen = !advancedOpen">
          <span>{{ t('form.advanced') }}</span><span>{{ advancedOpen ? '−' : '+' }}</span>
        </button>

        <div v-if="advancedOpen" class="form-grid advanced-grid">
          <div class="field"><label>{{ t('form.organization') }}</label><input v-model="draft.organization" class="input" /></div>
          <div class="field"><label>{{ t('form.proxy') }}</label><input v-model="draft.proxy" class="input" type="url" /></div>
          <div class="field"><label>{{ t('form.timeout') }}</label><input v-model.number="draft.timeoutSeconds" class="input" type="number" min="1" max="120" /></div>
          <div class="field"><label>{{ t('form.retry') }}</label><input v-model.number="draft.retryCount" class="input" type="number" min="0" max="5" /></div>
          <label class="checkbox-row field-wide"><input v-model="draft.tlsVerify" type="checkbox" />{{ t('form.tlsVerify') }}</label>
          <div class="field field-wide"><label>{{ t('form.headers') }}</label><textarea v-model="headersText" class="input code-input" spellcheck="false" /></div>
          <div class="field field-wide"><label>{{ t('form.parameters') }}</label><textarea v-model="parametersText" class="input code-input" spellcheck="false" /></div>
        </div>

        <p v-if="error" class="form-error">{{ error }}</p>
        <footer>
          <button v-if="!editing" class="button button-secondary" type="button" @click="selectedPreset = undefined">
            {{ t('form.back') }}
          </button>
          <span class="footer-spacer" />
          <button class="button button-secondary" type="button" @click="emit('close')">{{ t('form.cancel') }}</button>
          <button class="button button-primary" type="submit" :disabled="saving">{{ t('form.save') }}</button>
        </footer>
      </form>
    </section>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(8px);
}

.modal {
  width: min(760px, 100%);
  max-height: calc(100vh - 32px);
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--surface-solid);
  box-shadow: var(--shadow);
}

.modal > header,
form > footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
}

.modal > header {
  justify-content: space-between;
  border-bottom: 1px solid var(--border);
}

.modal h2,
.modal p {
  margin: 0;
}

.modal h2 {
  font-size: 17px;
}

.modal header p {
  margin-top: 3px;
  color: var(--text-secondary);
  font-size: 11px;
}

.close-button {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  color: var(--text-secondary);
  background: var(--bg-secondary);
  font-size: 19px;
}

.preset-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  padding: 16px;
}

.preset-card {
  display: grid;
  justify-items: start;
  gap: 6px;
  min-height: 120px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
  text-align: left;
  color: var(--text);
  background: var(--surface);
}

.preset-card:hover {
  border-color: rgba(0, 113, 227, 0.35);
  background: var(--accent-bg);
}

.preset-card small {
  color: var(--text-secondary);
  font-size: 10px;
}

.preset-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--accent);
  background: var(--accent-bg);
}

form {
  padding: 4px 16px 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 12px 0;
}

.field-wide {
  grid-column: 1 / -1;
}

.advanced-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 0;
  border: 0;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  color: var(--text);
  background: transparent;
  font-size: 13px;
  font-weight: 600;
}

.advanced-grid {
  background: rgba(245, 245, 247, 0.6);
}

.checkbox-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 13px;
}

.code-input {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
}

.form-error {
  color: var(--danger);
  font-size: 12px;
}

form > footer {
  margin: 0 -16px;
  border-top: 1px solid var(--border);
}

.footer-spacer {
  flex: 1;
}

@media (max-width: 680px) {
  .preset-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .field-wide {
    grid-column: auto;
  }
}
</style>
