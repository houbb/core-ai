<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import {
  addModelPricing,
  compareModels,
  createAlias,
  deleteAlias,
  deleteModel,
  getDefaultModels,
  getModel,
  getModelAudit,
  listModels,
  recommendModels,
  resetModelCapabilities,
  setModelFlags,
  syncModels,
  transitionModel,
  updateAlias,
  updateModel,
  updateModelCapabilities,
  updateModelParameters
} from '../api/models'
import { ApiError, listProviders } from '../api/providers'
import ModelComparePanel from '../components/ModelComparePanel.vue'
import ModelDetail from '../components/ModelDetail.vue'
import ModelEditorModal from '../components/ModelEditorModal.vue'
import ModelList from '../components/ModelList.vue'
import { t } from '../i18n'
import type { Capability, Provider } from '../types/provider'
import type {
  AiModel,
  AliasInput,
  DefaultModel,
  ModelAlias,
  ModelCategory,
  ModelFilters,
  ModelParameters,
  ModelPricing,
  ModelRecommendation,
  ModelStatus,
  ModelUpdateInput,
  PricingInput
} from '../types/model'

const models = ref<AiModel[]>([])
const selected = ref<AiModel>()
const providers = ref<Provider[]>([])
const audit = ref<Array<{ id: string; action: string; result: string; createTime: string; createUser: string; traceId?: string }>>([])
const defaults = ref<DefaultModel[]>([])
const recommendations = ref<ModelRecommendation[]>([])
const compared = ref<AiModel[]>([])
const compareIds = ref<string[]>([])
const compareMode = ref(false)
const editorOpen = ref(false)
const loading = ref(true)
const busy = ref(false)
const notice = ref<{ type: 'success' | 'error'; text: string }>()
const showRecommend = ref(false)
const showDefaults = ref(false)
const recommendCapability = ref<Capability>('CHAT')
const recommendMode = ref<'BEST' | 'CHEAPEST' | 'FASTEST' | 'LARGEST_CONTEXT'>('BEST')
const favoriteOnly = ref(false)
const recommendedOnly = ref(false)
const availableOnly = ref(false)
const filters = reactive<ModelFilters>({ query: '', providerId: '', category: undefined, status: undefined })
let timer: number | undefined

const categories: ModelCategory[] = ['CHAT','REASONING','VISION','EMBEDDING','RERANK','IMAGE','VIDEO','AUDIO','SPEECH','MODERATION','OCR','OTHER']
const statuses: ModelStatus[] = ['DISCOVERED','REGISTERED','ENABLED','DEPRECATED','DISABLED']
const capabilities: Capability[] = ['CHAT','REASONING','VISION','EMBEDDING','RERANK','IMAGE','VIDEO','AUDIO','SPEECH','MODERATION','OCR','TOOL_CALL','JSON_MODE','STREAMING']

onMounted(async () => {
  await Promise.all([loadModels(), loadProviders(), loadDefaults()])
})

watch(() => filters.query, () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => loadModels(), 250)
})

async function loadProviders() {
  try {
    providers.value = await listProviders()
  } catch (error) {
    showError(error)
  }
}

async function loadDefaults() {
  try {
    defaults.value = await getDefaultModels()
  } catch (error) {
    showError(error)
  }
}

async function loadModels(preferredId?: string) {
  loading.value = true
  try {
    models.value = await listModels({
      ...filters,
      favorite: favoriteOnly.value ? true : undefined,
      recommended: recommendedOnly.value ? true : undefined,
      available: availableOnly.value ? true : undefined
    })
    const id = preferredId || selected.value?.id || models.value[0]?.id
    if (id && models.value.some((model) => model.id === id)) await selectModel(id)
    else {
      selected.value = undefined
      audit.value = []
    }
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

async function selectModel(modelOrId: AiModel | string) {
  const id = typeof modelOrId === 'string' ? modelOrId : modelOrId.id
  try {
    const [detail, entries] = await Promise.all([getModel(id), getModelAudit(id)])
    selected.value = detail
    audit.value = entries
  } catch (error) {
    showError(error)
  }
}

async function synchronize() {
  busy.value = true
  try {
    const result = await syncModels(filters.providerId)
    notice.value = {
      type: 'success',
      text: `${result.synchronizedModels} ${t('models.syncSuccess')}`
    }
    await loadModels()
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function saveBasic(input: ModelUpdateInput) {
  if (!selected.value) return
  await runAndReload(() => updateModel(selected.value!.id, input), true)
}

async function saveCapabilities(overrides: Partial<Record<Capability, boolean>>) {
  if (!selected.value) return
  await runAndReload(() => updateModelCapabilities(selected.value!.id, overrides), true)
}

async function resetCapabilities() {
  if (!selected.value) return
  await runAndReload(() => resetModelCapabilities(selected.value!.id), true)
}

async function saveParameters(parameters: ModelParameters) {
  if (!selected.value) return
  await runAndReload(() => updateModelParameters(selected.value!.id, parameters), true)
}

async function addPricing(pricing: PricingInput) {
  if (!selected.value) return
  await runAndReload(() => addModelPricing(selected.value!.id, pricing), false)
}

async function saveAlias(id: string | undefined, input: AliasInput) {
  await runAndReload(() => id ? updateAlias(id, input) : createAlias(input), false)
  await loadDefaults()
}

async function removeAlias(alias: ModelAlias) {
  if (!window.confirm(`${t('models.confirmAliasDelete')} ${alias.alias}?`)) return
  await runAndReload(() => deleteAlias(alias.id), false)
  await loadDefaults()
}

async function transition(status: 'REGISTERED' | 'ENABLED' | 'DEPRECATED' | 'DISABLED') {
  if (!selected.value) return
  await runAndReload(() => transitionModel(selected.value!.id, status), false)
  await loadDefaults()
}

async function flags(favorite: boolean, recommended: boolean) {
  if (!selected.value) return
  await runAndReload(() => setModelFlags(selected.value!.id, favorite, recommended), false)
}

async function removeModel() {
  if (!selected.value
      || !window.confirm(`${t('models.confirmDelete')} ${selected.value.displayName}?`)) return
  busy.value = true
  try {
    await deleteModel(selected.value.id)
    selected.value = undefined
    await loadModels()
    await loadDefaults()
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function runRecommendation() {
  busy.value = true
  try {
    recommendations.value = await recommendModels(recommendCapability.value, recommendMode.value)
    showRecommend.value = true
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

function toggleCompare(model: AiModel) {
  compareIds.value = compareIds.value.includes(model.id)
    ? compareIds.value.filter((id) => id !== model.id)
    : compareIds.value.length < 5 ? [...compareIds.value, model.id] : compareIds.value
}

async function runCompare() {
  if (compareIds.value.length < 2) {
    notice.value = { type: 'error', text: t('models.compareHint') }
    return
  }
  busy.value = true
  try {
    compared.value = await compareModels(compareIds.value)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function runAndReload<T>(operation: () => Promise<T>, closeEditor: boolean) {
  if (!selected.value) return
  busy.value = true
  const id = selected.value.id
  try {
    await operation()
    if (closeEditor) editorOpen.value = false
    notice.value = { type: 'success', text: t('common.saved') }
    await loadModels(id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

function showError(error: unknown) {
  const text = error instanceof ApiError
    ? `${error.message}${error.errorCode ? ` (${error.errorCode})` : ''}`
    : error instanceof Error ? error.message : t('common.failed')
  notice.value = { type: 'error', text }
}
</script>

<template>
  <section class="models-page">
    <header class="page-header">
      <div><h1>{{ t('models.title') }}</h1><p>{{ t('models.subtitle') }}</p></div>
      <div class="toolbar-actions">
        <button class="button button-secondary" type="button" @click="showDefaults = !showDefaults">{{ t('models.defaults') }}</button>
        <button class="button button-emphasis" type="button" @click="showRecommend = !showRecommend">{{ t('models.recommend') }}</button>
        <button class="button button-emphasis" type="button" @click="compareMode = !compareMode; compared = []">{{ t('models.compare') }}</button>
        <button class="button button-primary" type="button" :disabled="busy" @click="synchronize">{{ t('models.sync') }}</button>
      </div>
    </header>

    <div class="filterbar">
      <input v-model="filters.query" class="input search-input" :placeholder="t('models.search')" />
      <select v-model="filters.providerId" class="input" @change="loadModels()"><option value="">{{ t('models.allProviders') }}</option><option v-for="provider in providers" :key="provider.id" :value="provider.id">{{ provider.name }}</option></select>
      <select v-model="filters.category" class="input" @change="loadModels()"><option :value="undefined">{{ t('models.allCategories') }}</option><option v-for="category in categories" :key="category" :value="category">{{ category }}</option></select>
      <select v-model="filters.capability" class="input" @change="loadModels()"><option :value="undefined">{{ t('providers.allCapabilities') }}</option><option v-for="capability in capabilities" :key="capability" :value="capability">{{ capability }}</option></select>
      <select v-model="filters.status" class="input" @change="loadModels()"><option :value="undefined">{{ t('models.allStatuses') }}</option><option v-for="status in statuses" :key="status" :value="status">{{ status }}</option></select>
      <input v-model.number="filters.minimumContextTokens" class="input context-filter" type="number" min="1" :placeholder="t('models.minimumContext')" @change="loadModels()" />
      <label class="filter-check"><input v-model="favoriteOnly" type="checkbox" @change="loadModels()" />{{ t('models.favoriteOnly') }}</label>
      <label class="filter-check"><input v-model="recommendedOnly" type="checkbox" @change="loadModels()" />{{ t('models.recommended') }}</label>
      <label class="filter-check"><input v-model="availableOnly" type="checkbox" @change="loadModels()" />{{ t('models.available') }}</label>
    </div>

    <div v-if="notice" class="notice" :class="notice.type"><span>{{ notice.text }}</span><button type="button" @click="notice = undefined">×</button></div>

    <section v-if="showDefaults" class="utility-panel">
      <div class="panel-heading"><h2>{{ t('models.defaults') }}</h2><button type="button" @click="showDefaults = false">×</button></div>
      <div class="default-grid"><article v-for="item in defaults" :key="item.alias"><span>{{ item.alias }}</span><strong>{{ item.model?.displayName || '—' }}</strong><small>{{ item.model?.providerName || '' }}</small></article></div>
    </section>

    <section v-if="showRecommend" class="utility-panel">
      <div class="panel-heading"><h2>{{ t('models.recommend') }}</h2><button type="button" @click="showRecommend = false">×</button></div>
      <div class="recommend-controls">
        <select v-model="recommendCapability" class="input"><option v-for="capability in capabilities" :key="capability">{{ capability }}</option></select>
        <select v-model="recommendMode" class="input">
          <option value="BEST">{{ t('models.modeBest') }}</option>
          <option value="CHEAPEST">{{ t('models.modeCheapest') }}</option>
          <option value="FASTEST">{{ t('models.modeFastest') }}</option>
          <option value="LARGEST_CONTEXT">{{ t('models.modeLargestContext') }}</option>
        </select>
        <button class="button button-primary" type="button" :disabled="busy" @click="runRecommendation">{{ t('models.recommend') }}</button>
      </div>
      <div class="recommend-list"><button v-for="item in recommendations" :key="item.model.id" type="button" @click="selectModel(item.model.id)"><strong>{{ item.model.displayName }}</strong><span>{{ item.reason }}</span><code>{{ item.score.toFixed(2) }}</code></button></div>
    </section>

    <div v-if="compareMode" class="compare-toolbar">
      <span>{{ t('models.compareHint') }} · {{ compareIds.length }}/5</span>
      <button class="button button-primary" type="button" :disabled="compareIds.length < 2 || busy" @click="runCompare">{{ t('models.compare') }}</button>
    </div>
    <ModelComparePanel v-if="compared.length" :models="compared" @close="compared = []; compareIds = []" />

    <div class="workspace">
      <ModelList :models="models" :selected-id="selected?.id" :loading="loading" :compare-mode="compareMode" :compare-ids="compareIds" @select="selectModel" @compare="toggleCompare" @sync="synchronize" />
      <ModelDetail v-if="selected" :model="selected" :audit="audit" :busy="busy" @edit="editorOpen = true" @transition="transition" @flags="flags" @delete="removeModel" />
      <div v-else-if="models.length" class="select-hint">{{ t('models.selectHint') }}</div>
    </div>

    <ModelEditorModal
      v-if="selected"
      :open="editorOpen"
      :model="selected"
      :saving="busy"
      @close="editorOpen = false"
      @basic="saveBasic"
      @capabilities="saveCapabilities"
      @reset-capabilities="resetCapabilities"
      @parameters="saveParameters"
      @pricing="addPricing"
      @alias="saveAlias"
      @delete-alias="removeAlias"
    />
  </section>
</template>

<style scoped>
.models-page {
  width: 100%;
}

.page-header,
.toolbar-actions,
.filterbar,
.filter-check,
.notice,
.panel-heading,
.recommend-controls,
.compare-toolbar {
  display: flex;
  align-items: center;
}

.page-header {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.page-header h1,
.panel-heading h2 {
  margin: 0;
}

.page-header h1 {
  font-size: 22px;
}

.page-header p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.toolbar-actions,
.filterbar,
.recommend-controls {
  flex-wrap: wrap;
  gap: 8px;
}

.filterbar {
  margin-bottom: 12px;
}

.filterbar .input,
.recommend-controls .input {
  width: auto;
  min-width: 145px;
}

.filterbar .search-input {
  flex: 1;
  min-width: 220px;
}

.filterbar .context-filter {
  min-width: 110px;
  max-width: 130px;
}

.filter-check {
  gap: 6px;
  min-height: 38px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: white;
  font-size: 12px;
}

.notice {
  justify-content: space-between;
  margin-bottom: 10px;
  padding: 9px 12px;
  border-radius: 10px;
  font-size: 12px;
}

.notice.success {
  color: var(--success);
  background: var(--success-bg);
}

.notice.error {
  color: var(--danger);
  background: var(--danger-bg);
}

.notice button,
.panel-heading button {
  border: 0;
  color: inherit;
  background: transparent;
  font-size: 18px;
}

.utility-panel {
  margin-bottom: 12px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
}

.panel-heading {
  justify-content: space-between;
  margin-bottom: 10px;
}

.panel-heading h2 {
  font-size: 15px;
}

.default-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.default-grid article {
  display: grid;
  gap: 3px;
  padding: 10px;
  border-radius: 10px;
  background: var(--bg-secondary);
}

.default-grid span,
.default-grid small,
.recommend-list span {
  color: var(--text-secondary);
  font-size: 11px;
}

.recommend-list {
  display: grid;
  gap: 6px;
  margin-top: 10px;
}

.recommend-list button {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(240px, 2fr) auto;
  align-items: center;
  gap: 10px;
  padding: 9px;
  border: 1px solid var(--border);
  border-radius: 9px;
  text-align: left;
  color: var(--text);
  background: white;
}

.compare-toolbar {
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 8px 12px;
  border-radius: 10px;
  color: var(--accent);
  background: var(--accent-bg);
  font-size: 12px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(250px, 330px) minmax(0, 1fr);
  min-height: calc(100vh - 190px);
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.select-hint {
  display: grid;
  place-items: center;
  color: var(--text-secondary);
  font-size: 13px;
}

@media (max-width: 900px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .workspace {
    grid-template-columns: 1fr;
  }

  .default-grid {
    grid-template-columns: 1fr;
  }
}
</style>
