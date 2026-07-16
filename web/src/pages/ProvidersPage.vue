<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import {
  ApiError,
  createProvider,
  deleteProvider,
  getPresets,
  getProvider,
  getProviderAudit,
  listProviders,
  refreshProviderModels,
  setProviderEnabled,
  testProvider,
  updateProvider
} from '../api/providers'
import ConnectionResultPanel from '../components/ConnectionResultPanel.vue'
import ProviderDetail from '../components/ProviderDetail.vue'
import ProviderFormModal from '../components/ProviderFormModal.vue'
import ProviderList from '../components/ProviderList.vue'
import { t } from '../i18n'
import type {
  AuditEntry,
  Capability,
  ConnectionResult,
  Provider,
  ProviderFilters,
  ProviderInput,
  ProviderPreset
} from '../types/provider'

const providers = ref<Provider[]>([])
const selected = ref<Provider>()
const presets = ref<ProviderPreset[]>([])
const audit = ref<AuditEntry[]>([])
const connectionResult = ref<ConnectionResult>()
const loading = ref(true)
const busy = ref(false)
const modalOpen = ref(false)
const editing = ref(false)
const notice = ref<{ type: 'success' | 'error'; text: string }>()
const filters = reactive<ProviderFilters>({ query: '', location: '', capability: undefined })
const enabledOnly = ref(false)
let searchTimer: number | undefined

const capabilities: Capability[] = [
  'CHAT',
  'VISION',
  'EMBEDDING',
  'IMAGE',
  'AUDIO',
  'SPEECH',
  'RERANK',
  'REASONING'
]

onMounted(async () => {
  await Promise.all([loadProviders(), loadPresets()])
})

watch(
  () => filters.query,
  () => {
    window.clearTimeout(searchTimer)
    searchTimer = window.setTimeout(loadProviders, 250)
  }
)

async function loadPresets() {
  try {
    presets.value = await getPresets()
  } catch (error) {
    showError(error)
  }
}

async function loadProviders(preferredId?: string) {
  loading.value = true
  try {
    providers.value = await listProviders({
      ...filters,
      enabled: enabledOnly.value ? true : undefined
    })
    const targetId = preferredId || selected.value?.id || providers.value[0]?.id
    if (targetId && providers.value.some((provider) => provider.id === targetId)) {
      await selectProvider(targetId)
    } else {
      selected.value = undefined
      audit.value = []
    }
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

async function selectProvider(providerOrId: Provider | string) {
  const id = typeof providerOrId === 'string' ? providerOrId : providerOrId.id
  try {
    const [detail, entries] = await Promise.all([getProvider(id), getProviderAudit(id)])
    selected.value = detail
    audit.value = entries
  } catch (error) {
    showError(error)
  }
}

function openCreate() {
  editing.value = false
  modalOpen.value = true
}

function openEdit() {
  editing.value = true
  modalOpen.value = true
}

async function save(input: ProviderInput) {
  busy.value = true
  try {
    const saved = editing.value && selected.value
      ? await updateProvider(selected.value.id, input)
      : await createProvider(input)
    modalOpen.value = false
    notice.value = { type: 'success', text: t('common.saved') }
    await loadProviders(saved.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function runConnection(action: 'test' | 'refresh') {
  if (!selected.value) return
  busy.value = true
  try {
    connectionResult.value = action === 'test'
      ? await testProvider(selected.value.id)
      : await refreshProviderModels(selected.value.id)
    await loadProviders(selected.value.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function toggleProvider() {
  if (!selected.value) return
  busy.value = true
  try {
    const saved = await setProviderEnabled(selected.value.id, !selected.value.enabled)
    await loadProviders(saved.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function removeProvider() {
  if (!selected.value || !window.confirm(t('providers.confirmDelete'))) return
  busy.value = true
  try {
    await deleteProvider(selected.value.id)
    selected.value = undefined
    await loadProviders()
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

function showError(error: unknown) {
  const message = error instanceof ApiError
    ? `${error.message}${error.errorCode ? ` (${error.errorCode})` : ''}`
    : error instanceof Error ? error.message : t('common.failed')
  notice.value = { type: 'error', text: message }
}
</script>

<template>
  <section class="providers-page">
    <header class="page-header">
      <div>
        <h1>{{ t('providers.title') }}</h1>
        <p>{{ t('providers.subtitle') }}</p>
      </div>
      <button class="button button-primary" type="button" @click="openCreate">
        + {{ t('providers.new') }}
      </button>
    </header>

    <div class="filterbar">
      <input v-model="filters.query" class="input search-input" :placeholder="t('providers.search')" />
      <select v-model="filters.location" class="input" @change="loadProviders()">
        <option value="">{{ t('providers.allLocations') }}</option>
        <option value="CLOUD">{{ t('providers.cloud') }}</option>
        <option value="LOCAL">{{ t('providers.local') }}</option>
      </select>
      <select v-model="filters.capability" class="input" @change="loadProviders()">
        <option :value="undefined">{{ t('providers.allCapabilities') }}</option>
        <option v-for="capability in capabilities" :key="capability" :value="capability">{{ capability }}</option>
      </select>
      <label class="enabled-filter">
        <input v-model="enabledOnly" type="checkbox" @change="loadProviders()" />
        {{ t('providers.enabledOnly') }}
      </label>
    </div>

    <div v-if="notice" class="notice" :class="notice.type">
      <span>{{ notice.text }}</span>
      <button type="button" @click="notice = undefined">×</button>
    </div>

    <ConnectionResultPanel
      v-if="connectionResult"
      :result="connectionResult"
      @close="connectionResult = undefined"
    />

    <div class="workspace">
      <ProviderList
        :providers="providers"
        :selected-id="selected?.id"
        :loading="loading"
        @select="selectProvider"
        @create="openCreate"
      />
      <ProviderDetail
        v-if="selected"
        :provider="selected"
        :audit="audit"
        :busy="busy"
        @edit="openEdit"
        @test="runConnection('test')"
        @refresh="runConnection('refresh')"
        @toggle="toggleProvider"
        @delete="removeProvider"
      />
      <div v-else-if="providers.length > 0" class="select-hint">Select a Provider</div>
    </div>

    <ProviderFormModal
      :open="modalOpen"
      :presets="presets"
      :provider="editing ? selected : undefined"
      :saving="busy"
      @close="modalOpen = false"
      @save="save"
    />
  </section>
</template>

<style scoped>
.providers-page {
  width: 100%;
}

.page-header,
.filterbar,
.enabled-filter,
.notice {
  display: flex;
  align-items: center;
}

.page-header {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.page-header h1 {
  margin: 0;
  font-size: 22px;
}

.page-header p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.filterbar {
  gap: 8px;
  margin-bottom: 12px;
}

.filterbar .input {
  width: auto;
  min-width: 150px;
}

.filterbar .search-input {
  flex: 1;
  min-width: 220px;
}

.enabled-filter {
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--surface-solid);
  font-size: 12px;
  white-space: nowrap;
}

.notice {
  justify-content: space-between;
  gap: 10px;
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

.notice button {
  border: 0;
  color: inherit;
  background: transparent;
  font-size: 18px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
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

@media (max-width: 860px) {
  .filterbar {
    flex-wrap: wrap;
  }

  .filterbar .input,
  .filterbar .search-input {
    flex: 1 1 180px;
    width: 100%;
  }

  .workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
