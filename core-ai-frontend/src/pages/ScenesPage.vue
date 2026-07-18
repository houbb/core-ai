<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { listAliases } from '../api/models'
import { listPublishedPrompts } from '../api/prompts'
import { ApiError } from '../api/providers'
import {
  createScene,
  deleteSceneTemplate,
  executeScene,
  exportScene,
  getScene,
  getSceneAudit,
  importScene,
  instantiateSceneTemplate,
  listScenes,
  listSceneTemplates,
  listSceneVersions,
  rollbackScene,
  saveSceneTemplate,
  testScene,
  transitionScene,
  updateScene
} from '../api/scenes'
import SceneDetail from '../components/SceneDetail.vue'
import SceneEditorModal from '../components/SceneEditorModal.vue'
import SceneList from '../components/SceneList.vue'
import ScenePlayground from '../components/ScenePlayground.vue'
import SceneTemplatesPanel from '../components/SceneTemplatesPanel.vue'
import { t } from '../i18n'
import type {
  AiScene,
  SceneConfiguration,
  SceneExecutionResult,
  SceneFilters,
  ScenePackage,
  SceneStatus,
  SceneTemplate,
  SceneVersion
} from '../types/scene'
import type { PromptSummary } from '../types/prompt'

const scenes = ref<AiScene[]>([])
const selected = ref<AiScene>()
const editing = ref<AiScene>()
const templates = ref<SceneTemplate[]>([])
const versions = ref<SceneVersion[]>([])
const audit = ref<Array<{
  id: string
  action: string
  result: string
  createTime: string
  createUser: string
  traceId?: string
}>>([])
const aliases = ref<string[]>([])
const publishedPrompts = ref<PromptSummary[]>([])
const execution = ref<SceneExecutionResult>()
const loading = ref(true)
const busy = ref(false)
const editorOpen = ref(false)
const templatesOpen = ref(false)
const playgroundOpen = ref(false)
const importInput = ref<HTMLInputElement>()
const notice = ref<{ type: 'success' | 'error'; text: string }>()
const filters = reactive<SceneFilters>({ query: '', category: '', status: undefined })
let timer: number | undefined

const categories = [
  'CONVERSATION', 'WRITING', 'TRANSLATE', 'SUMMARIZE', 'CODING', 'CODE_REVIEW',
  'SQL', 'OCR', 'VISION', 'SPEECH', 'IMAGE', 'VIDEO', 'EMBEDDING', 'KNOWLEDGE',
  'RAG', 'REASONING', 'WORKFLOW', 'AGENT'
]
const statuses: SceneStatus[] = ['DRAFT', 'TESTING', 'PUBLISHED', 'DISABLED', 'ARCHIVED']

onMounted(async () => {
  await Promise.all([loadScenes(), loadTemplates(), loadAliases(), loadPrompts()])
})

watch(() => filters.query, () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => loadScenes(), 250)
})

async function loadScenes(preferredId?: string) {
  loading.value = true
  try {
    scenes.value = await listScenes(filters)
    const id = preferredId || selected.value?.id || scenes.value[0]?.id
    if (id && scenes.value.some(scene => scene.id === id)) await selectScene(id)
    else {
      selected.value = undefined
      versions.value = []
      audit.value = []
    }
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

async function selectScene(sceneOrId: AiScene | string) {
  const id = typeof sceneOrId === 'string' ? sceneOrId : sceneOrId.id
  try {
    const [scene, sceneVersions, entries] = await Promise.all([
      getScene(id),
      listSceneVersions(id),
      getSceneAudit(id)
    ])
    selected.value = scene
    versions.value = sceneVersions
    audit.value = entries
  } catch (error) {
    showError(error)
  }
}

async function loadTemplates() {
  try {
    templates.value = await listSceneTemplates()
  } catch (error) {
    showError(error)
  }
}

async function loadAliases() {
  try {
    const items = await listAliases()
    aliases.value = [...new Set(items.map(item => item.alias))]
  } catch (error) {
    showError(error)
  }
}

async function loadPrompts() {
  try {
    publishedPrompts.value = await listPublishedPrompts()
  } catch (error) {
    showError(error)
  }
}

function openNew() {
  editing.value = undefined
  editorOpen.value = true
}

function openEdit() {
  editing.value = selected.value
  editorOpen.value = true
}

async function save(code: string, configuration: SceneConfiguration) {
  busy.value = true
  try {
    const result = editing.value
      ? await updateScene(editing.value.id, configuration)
      : await createScene(code, configuration)
    editorOpen.value = false
    notice.value = { type: 'success', text: t('common.saved') }
    await loadScenes(result.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function transition(status: SceneStatus) {
  if (!selected.value) return
  await runAndReload(() => transitionScene(selected.value!.id, status))
}

function openPlayground() {
  execution.value = undefined
  playgroundOpen.value = true
}

async function runTest(input: string) {
  if (!selected.value) return
  busy.value = true
  try {
    execution.value = selected.value.status === 'PUBLISHED'
      ? await executeScene(selected.value.code, input)
      : await testScene(selected.value.id, input)
    await selectScene(selected.value.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function rollback(version: number) {
  if (!selected.value
      || !window.confirm(`${t('scenes.confirmRollback')} V${version}?`)) return
  await runAndReload(() => rollbackScene(selected.value!.id, version))
}

async function downloadExport() {
  if (!selected.value) return
  busy.value = true
  try {
    const data = await exportScene(selected.value.id)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${data.code}-scene-v${data.version}.json`
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

function chooseImport() {
  importInput.value?.click()
}

async function handleImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  busy.value = true
  try {
    const data = JSON.parse(await file.text()) as ScenePackage
    const result = await importScene(data)
    notice.value = { type: 'success', text: t('scenes.imported') }
    await loadScenes(result.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function instantiate(template: SceneTemplate, code: string) {
  busy.value = true
  try {
    const result = await instantiateSceneTemplate(template.id, code)
    templatesOpen.value = false
    notice.value = { type: 'success', text: t('scenes.createdFromTemplate') }
    await loadScenes(result.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function removeTemplate(template: SceneTemplate) {
  if (!window.confirm(`${t('scenes.confirmTemplateDelete')} ${template.templateName}?`)) return
  busy.value = true
  try {
    await deleteSceneTemplate(template.id)
    await loadTemplates()
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function saveTemplate(templateName: string, defaultCode: string) {
  if (!selected.value) return
  busy.value = true
  try {
    await saveSceneTemplate(selected.value.id, templateName, defaultCode)
    notice.value = { type: 'success', text: t('scenes.templateSaved') }
    await loadTemplates()
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function runAndReload(operation: () => Promise<AiScene>) {
  if (!selected.value) return
  const id = selected.value.id
  busy.value = true
  try {
    await operation()
    notice.value = { type: 'success', text: t('common.saved') }
    await loadScenes(id)
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
  <section class="scenes-page">
    <header class="page-header">
      <div><h1>{{ t('scenes.title') }}</h1><p>{{ t('scenes.subtitle') }}</p></div>
      <div class="toolbar-actions">
        <button class="button button-secondary" type="button" :disabled="busy" @click="chooseImport">{{ t('scenes.import') }}</button>
        <input ref="importInput" hidden type="file" accept="application/json,.json" @change="handleImport" />
        <button class="button button-emphasis" type="button" :disabled="busy" @click="templatesOpen = !templatesOpen">{{ t('scenes.templates') }}</button>
        <button class="button button-primary" type="button" :disabled="busy" @click="openNew">{{ t('scenes.new') }}</button>
      </div>
    </header>

    <div class="filterbar">
      <input v-model="filters.query" class="input search-input" :placeholder="t('scenes.search')" />
      <select v-model="filters.category" class="input" @change="loadScenes()"><option value="">{{ t('scenes.allCategories') }}</option><option v-for="category in categories" :key="category">{{ category }}</option></select>
      <select v-model="filters.status" class="input" @change="loadScenes()"><option :value="undefined">{{ t('scenes.allStatuses') }}</option><option v-for="status in statuses" :key="status">{{ status }}</option></select>
      <label class="filter-check"><input v-model="filters.recommended" type="checkbox" :true-value="true" :false-value="undefined" @change="loadScenes()" />{{ t('scenes.recommendedOnly') }}</label>
    </div>

    <div v-if="notice" class="notice" :class="notice.type"><span>{{ notice.text }}</span><button type="button" @click="notice = undefined">×</button></div>

    <SceneTemplatesPanel
      v-if="templatesOpen"
      :templates="templates"
      :busy="busy"
      @close="templatesOpen = false"
      @instantiate="instantiate"
      @delete="removeTemplate"
    />

    <div class="workspace">
      <SceneList :scenes="scenes" :selected-id="selected?.id" :loading="loading" @select="selectScene" @templates="templatesOpen = true" />
      <SceneDetail
        v-if="selected"
        :scene="selected"
        :versions="versions"
        :audit="audit"
        :busy="busy"
        @edit="openEdit"
        @status="transition"
        @test="openPlayground"
        @export="downloadExport"
        @rollback="rollback"
        @save-template="saveTemplate"
      />
      <div v-else-if="scenes.length" class="select-hint">{{ t('scenes.selectHint') }}</div>
    </div>

    <SceneEditorModal
      :open="editorOpen"
      :scene="editing"
      :aliases="aliases"
      :prompts="publishedPrompts"
      :saving="busy"
      @close="editorOpen = false"
      @save="save"
    />

    <ScenePlayground
      v-if="selected"
      :open="playgroundOpen"
      :scene="selected"
      :result="execution"
      :busy="busy"
      @close="playgroundOpen = false"
      @run="runTest"
    />
  </section>
</template>

<style scoped>
.scenes-page {
  width: 100%;
}

.page-header,
.toolbar-actions,
.filterbar,
.filter-check,
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

.toolbar-actions,
.filterbar {
  flex-wrap: wrap;
  gap: 8px;
}

.filterbar {
  margin-bottom: 12px;
}

.filterbar .input {
  width: auto;
  min-width: 170px;
}

.filterbar .search-input {
  flex: 1;
  min-width: 240px;
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

.notice button {
  border: 0;
  color: inherit;
  background: transparent;
  font-size: 18px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(250px, 340px) minmax(0, 1fr);
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
}
</style>
