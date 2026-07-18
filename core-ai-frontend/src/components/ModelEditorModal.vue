<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { t } from '../i18n'
import type { Capability } from '../types/provider'
import type {
  AiModel,
  AliasInput,
  ModelAlias,
  ModelParameters,
  ModelUpdateInput,
  PricingInput
} from '../types/model'

const props = defineProps<{ open: boolean; model: AiModel; saving?: boolean }>()

const emit = defineEmits<{
  close: []
  basic: [input: ModelUpdateInput]
  capabilities: [overrides: Partial<Record<Capability, boolean>>]
  resetCapabilities: []
  parameters: [parameters: ModelParameters]
  pricing: [pricing: PricingInput]
  alias: [id: string | undefined, alias: AliasInput]
  deleteAlias: [alias: ModelAlias]
}>()

const capabilities: Capability[] = [
  'CHAT', 'REASONING', 'VISION', 'EMBEDDING', 'RERANK', 'IMAGE', 'VIDEO',
  'AUDIO', 'SPEECH', 'MODERATION', 'OCR', 'TOOL_CALL', 'JSON_MODE', 'STREAMING'
]

const tab = ref<'basic' | 'capability' | 'parameter' | 'pricing' | 'alias'>('basic')
const tagsText = ref('')
const aliasId = ref<string>()
const basic = reactive<ModelUpdateInput>({
  displayName: '',
  category: 'OTHER',
  description: '',
  contextManuallyOverridden: false,
  tags: []
})
const overrides = reactive<Partial<Record<Capability, boolean>>>({})
const parameters = reactive<ModelParameters>({})
const pricing = reactive<PricingInput>({
  currency: 'USD',
  effectiveTime: new Date().toISOString().slice(0, 16)
})
const aliasDraft = reactive<AliasInput>({
  alias: '',
  modelId: '',
  scene: '',
  priority: 100,
  enabled: true
})
const hasPricingValue = computed(() => [
  pricing.promptPrice,
  pricing.completionPrice,
  pricing.cacheReadPrice,
  pricing.cacheWritePrice
].some((value) => typeof value === 'number'))

watch(
  () => [props.open, props.model] as const,
  () => {
    if (!props.open) return
    tab.value = 'basic'
    Object.assign(basic, {
      displayName: props.model.displayName,
      category: props.model.category,
      description: props.model.description || '',
      maxContextTokens: props.model.maxContextTokens,
      maxInputTokens: props.model.maxInputTokens,
      maxOutputTokens: props.model.maxOutputTokens,
      defaultMaxTokens: props.model.defaultMaxTokens,
      contextManuallyOverridden: props.model.contextManuallyOverridden,
      tags: props.model.tags
    })
    tagsText.value = props.model.tags.join(', ')
    capabilities.forEach((capability) => {
      overrides[capability] = props.model.capabilities.includes(capability)
    })
    Object.assign(parameters, props.model.parameters)
    Object.assign(pricing, {
      currency: 'USD',
      promptPrice: undefined,
      completionPrice: undefined,
      cacheReadPrice: undefined,
      cacheWritePrice: undefined,
      effectiveTime: new Date().toISOString().slice(0, 16),
      notes: ''
    })
    resetAlias()
  },
  { immediate: true }
)

function saveBasic() {
  emit('basic', {
    ...basic,
    tags: tagsText.value.split(',').map((tag) => tag.trim()).filter(Boolean)
  })
}

function savePricing() {
  emit('pricing', {
    ...pricing,
    effectiveTime: new Date(pricing.effectiveTime).toISOString()
  })
}

function editAlias(alias: ModelAlias) {
  aliasId.value = alias.id
  Object.assign(aliasDraft, {
    alias: alias.alias,
    modelId: alias.modelId,
    scene: alias.scene || '',
    priority: alias.priority,
    enabled: alias.enabled
  })
}

function resetAlias() {
  aliasId.value = undefined
  Object.assign(aliasDraft, {
    alias: '',
    modelId: props.model.id,
    scene: '',
    priority: 100,
    enabled: true
  })
}

function saveAlias() {
  emit('alias', aliasId.value, { ...aliasDraft })
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" @mousedown.self="$emit('close')">
    <section class="modal" role="dialog" aria-modal="true">
      <header>
        <div><h2>{{ model.displayName }}</h2><p>{{ t('providers.edit') }} · {{ t('models.runtime') }}</p></div>
        <button class="close-button" type="button" :disabled="saving" @click="$emit('close')">×</button>
      </header>
      <nav class="editor-tabs">
        <button :class="{ active: tab === 'basic' }" type="button" @click="tab = 'basic'">{{ t('models.basic') }}</button>
        <button :class="{ active: tab === 'capability' }" type="button" @click="tab = 'capability'">{{ t('models.capabilities') }}</button>
        <button :class="{ active: tab === 'parameter' }" type="button" @click="tab = 'parameter'">{{ t('models.parameters') }}</button>
        <button :class="{ active: tab === 'pricing' }" type="button" @click="tab = 'pricing'">{{ t('models.pricing') }}</button>
        <button :class="{ active: tab === 'alias' }" type="button" @click="tab = 'alias'">{{ t('models.aliases') }}</button>
      </nav>

      <form v-if="tab === 'basic'" @submit.prevent="saveBasic">
        <div class="form-grid">
          <div class="field"><label>{{ t('models.displayName') }}</label><input v-model="basic.displayName" class="input" required /></div>
          <div class="field"><label>{{ t('models.category') }}</label><select v-model="basic.category" class="input"><option v-for="item in ['CHAT','REASONING','VISION','EMBEDDING','RERANK','IMAGE','VIDEO','AUDIO','SPEECH','MODERATION','OCR','OTHER']" :key="item">{{ item }}</option></select></div>
          <div class="field field-wide"><label>{{ t('models.description') }}</label><textarea v-model="basic.description" class="input" /></div>
          <div class="field"><label>{{ t('models.maxContext') }}</label><input v-model.number="basic.maxContextTokens" class="input" type="number" min="1" /></div>
          <div class="field"><label>{{ t('models.maxInput') }}</label><input v-model.number="basic.maxInputTokens" class="input" type="number" min="1" /></div>
          <div class="field"><label>{{ t('models.maxOutput') }}</label><input v-model.number="basic.maxOutputTokens" class="input" type="number" min="1" /></div>
          <div class="field"><label>{{ t('models.defaultMaxTokens') }}</label><input v-model.number="basic.defaultMaxTokens" class="input" type="number" min="1" /></div>
          <div class="field field-wide"><label>{{ t('models.tags') }}</label><input v-model="tagsText" class="input" /></div>
          <label class="checkbox-row field-wide"><input v-model="basic.contextManuallyOverridden" type="checkbox" /> {{ t('models.preserveContext') }}</label>
        </div>
        <footer><span /><button class="button button-primary" type="submit" :disabled="saving">{{ t('models.saveBasic') }}</button></footer>
      </form>

      <form v-else-if="tab === 'capability'" @submit.prevent="$emit('capabilities', { ...overrides })">
        <div class="capability-grid">
          <label v-for="capability in capabilities" :key="capability">
            <input v-model="overrides[capability]" type="checkbox" />
            <span>{{ capability }}</span>
          </label>
        </div>
        <footer>
          <button class="button button-secondary" type="button" :disabled="saving" @click="$emit('resetCapabilities')">{{ t('models.resetCapabilities') }}</button>
          <span />
          <button class="button button-primary" type="submit" :disabled="saving">{{ t('models.saveCapabilities') }}</button>
        </footer>
      </form>

      <form v-else-if="tab === 'parameter'" @submit.prevent="$emit('parameters', { ...parameters })">
        <div class="form-grid">
          <div class="field"><label>{{ t('models.temperature') }}</label><input v-model.number="parameters.temperature" class="input" type="number" min="0" max="2" step="0.01" /></div>
          <div class="field"><label>{{ t('models.topP') }}</label><input v-model.number="parameters.topP" class="input" type="number" min="0" max="1" step="0.01" /></div>
          <div class="field"><label>{{ t('models.frequencyPenalty') }}</label><input v-model.number="parameters.frequencyPenalty" class="input" type="number" min="-2" max="2" step="0.01" /></div>
          <div class="field"><label>{{ t('models.presencePenalty') }}</label><input v-model.number="parameters.presencePenalty" class="input" type="number" min="-2" max="2" step="0.01" /></div>
          <div class="field"><label>{{ t('models.maxOutput') }}</label><input v-model.number="parameters.maxOutputTokens" class="input" type="number" min="1" /></div>
          <div class="field"><label>{{ t('models.reasoningEffort') }}</label><select v-model="parameters.reasoningEffort" class="input"><option value="">—</option><option value="low">{{ t('models.low') }}</option><option value="medium">{{ t('models.medium') }}</option><option value="high">{{ t('models.high') }}</option></select></div>
          <div class="field"><label>{{ t('models.seed') }}</label><input v-model.number="parameters.seed" class="input" type="number" /></div>
        </div>
        <footer><span /><button class="button button-primary" type="submit" :disabled="saving">{{ t('models.saveParameters') }}</button></footer>
      </form>

      <form v-else-if="tab === 'pricing'" @submit.prevent="savePricing">
        <div class="form-grid">
          <div class="field"><label>{{ t('models.currency') }}</label><input v-model="pricing.currency" class="input" required /></div>
          <div class="field"><label>{{ t('models.effectiveTime') }}</label><input v-model="pricing.effectiveTime" class="input" type="datetime-local" required /></div>
          <div class="field"><label>{{ t('models.prompt') }} / 1M</label><input v-model.number="pricing.promptPrice" class="input" type="number" min="0" step="0.000001" /></div>
          <div class="field"><label>{{ t('models.completion') }} / 1M</label><input v-model.number="pricing.completionPrice" class="input" type="number" min="0" step="0.000001" /></div>
          <div class="field"><label>{{ t('models.cacheRead') }} / 1M</label><input v-model.number="pricing.cacheReadPrice" class="input" type="number" min="0" step="0.000001" /></div>
          <div class="field"><label>{{ t('models.cacheWrite') }} / 1M</label><input v-model.number="pricing.cacheWritePrice" class="input" type="number" min="0" step="0.000001" /></div>
          <div class="field field-wide"><label>{{ t('models.notes') }}</label><textarea v-model="pricing.notes" class="input" /></div>
        </div>
        <footer><span /><button class="button button-primary" type="submit" :disabled="saving || !hasPricingValue">{{ t('models.addPricing') }}</button></footer>
      </form>

      <div v-else class="alias-editor">
        <form @submit.prevent="saveAlias">
          <div class="form-grid">
            <div class="field"><label>{{ t('models.alias') }}</label><input v-model="aliasDraft.alias" class="input" required pattern="[a-z0-9][a-z0-9._-]{1,99}" /></div>
            <div class="field"><label>{{ t('models.scene') }}</label><input v-model="aliasDraft.scene" class="input" /></div>
            <div class="field"><label>{{ t('models.priority') }}</label><input v-model.number="aliasDraft.priority" class="input" type="number" min="0" max="10000" /></div>
            <label class="checkbox-row"><input v-model="aliasDraft.enabled" type="checkbox" /> {{ t('models.enabled') }}</label>
          </div>
          <footer><button v-if="aliasId" class="button button-secondary" type="button" :disabled="saving" @click="resetAlias">{{ t('common.new') }}</button><span /><button class="button button-primary" type="submit" :disabled="saving">{{ aliasId ? t('common.update') : t('models.addAlias') }}</button></footer>
        </form>
        <div class="alias-list">
          <article v-for="alias in model.aliases" :key="alias.id">
            <div><strong>{{ alias.alias }}</strong><small>{{ alias.scene || '—' }} · Priority {{ alias.priority }}</small></div>
            <button class="button button-secondary" type="button" :disabled="saving" @click="editAlias(alias)">{{ t('providers.edit') }}</button>
            <button class="button button-danger" type="button" :disabled="saving" @click="$emit('deleteAlias', alias)">{{ t('providers.delete') }}</button>
          </article>
        </div>
      </div>
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
  width: min(820px, 100%);
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
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px;
}

.modal > header {
  border-bottom: 1px solid var(--border);
}

h2,
p {
  margin: 0;
}

h2 {
  font-size: 17px;
}

p,
small {
  color: var(--text-secondary);
  font-size: 11px;
}

.close-button {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 50%;
  background: var(--bg-secondary);
  font-size: 19px;
}

.editor-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 12px 0;
  overflow: auto;
  border-bottom: 1px solid var(--border);
}

.editor-tabs button {
  padding: 8px 10px;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--text-secondary);
  background: transparent;
  font-size: 12px;
  font-weight: 600;
}

.editor-tabs button.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
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

.checkbox-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 13px;
}

form > footer {
  margin: 0 -16px;
  border-top: 1px solid var(--border);
}

form > footer span {
  flex: 1;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 16px 0;
}

.capability-grid label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: 9px;
  font-size: 12px;
}

.alias-editor {
  padding: 0 16px 16px;
}

.alias-editor form {
  padding: 4px 0 0;
}

.alias-editor form > footer {
  margin: 0;
  padding-inline: 0;
}

.alias-list {
  display: grid;
  gap: 7px;
  margin-top: 12px;
}

.alias-list article {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
}

.alias-list article div {
  display: grid;
  flex: 1;
  gap: 2px;
}

@media (max-width: 680px) {
  .form-grid,
  .capability-grid {
    grid-template-columns: 1fr;
  }

  .field-wide {
    grid-column: auto;
  }
}
</style>
