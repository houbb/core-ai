<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  assignPromptAbTest,
  comparePromptVersions,
  createPrompt,
  createPromptAbTest,
  createPromptTestCase,
  deletePromptTestCase,
  getPrompt,
  getPromptAudit,
  getPromptRenderLogs,
  listPromptAbTests,
  listPrompts,
  listPromptTestCases,
  listPromptVersions,
  renderPrompt,
  rollbackPrompt,
  runPromptTests,
  transitionPrompt,
  updatePrompt
} from '../api/prompts'
import { ApiError } from '../api/providers'
import PromptAbPanel from '../components/PromptAbPanel.vue'
import PromptCodeEditor from '../components/PromptCodeEditor.vue'
import PromptList from '../components/PromptList.vue'
import PromptPlayground from '../components/PromptPlayground.vue'
import PromptTestsPanel from '../components/PromptTestsPanel.vue'
import PromptVersionsPanel from '../components/PromptVersionsPanel.vue'
import { t } from '../i18n'
import type {
  PromptAbTest,
  PromptAudit,
  PromptConfiguration,
  PromptDetail,
  PromptDiffLine,
  PromptFilters,
  PromptGuardrailPhase,
  PromptGuardrailType,
  PromptRenderLog,
  PromptRenderResult,
  PromptStatus,
  PromptSummary,
  PromptTestCase,
  PromptTestSuite,
  PromptVariableType,
  PromptVersion,
  PromptVisibility
} from '../types/prompt'

type Tab = 'EDITOR' | 'VARIABLES' | 'GOVERNANCE' | 'TESTS' | 'VERSIONS' | 'AB' | 'AUDIT'

const prompts = ref<PromptSummary[]>([])
const selected = ref<PromptDetail>()
const versions = ref<PromptVersion[]>([])
const testCases = ref<PromptTestCase[]>([])
const suite = ref<PromptTestSuite>()
const abTests = ref<PromptAbTest[]>([])
const audit = ref<PromptAudit[]>([])
const renderLogs = ref<PromptRenderLog[]>([])
const rendered = ref<PromptRenderResult>()
const diff = ref<PromptDiffLine[]>([])
const loading = ref(true)
const busy = ref(false)
const newMode = ref(false)
const code = ref('')
const tab = ref<Tab>('EDITOR')
const playgroundOpen = ref(false)
const previewInput = ref('{}')
const notice = ref<{ type: 'success' | 'error'; text: string }>()
const filters = reactive<PromptFilters>({ query: '', category: '', status: undefined, visibility: undefined })
let searchTimer: number | undefined

const categories = ['CONVERSATION', 'TRANSLATE', 'OCR', 'SQL', 'CODING', 'WRITING', 'KNOWLEDGE', 'AGENT', 'WORKFLOW']
const statuses: PromptStatus[] = ['DRAFT', 'TESTING', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED']
const visibilities: PromptVisibility[] = ['PUBLIC', 'PROJECT', 'DEPARTMENT', 'PRIVATE']
const variableTypes: PromptVariableType[] = ['STRING', 'INTEGER', 'BOOLEAN', 'JSON', 'LIST', 'OBJECT']
const guardrailTypes: PromptGuardrailType[] = ['SENSITIVE', 'INJECTION', 'ILLEGAL', 'LENGTH', 'JSON_VALIDATE']
const guardrailPhases: PromptGuardrailPhase[] = ['INPUT', 'OUTPUT']
const tabs: Tab[] = ['EDITOR', 'VARIABLES', 'GOVERNANCE', 'TESTS', 'VERSIONS', 'AB', 'AUDIT']
const visibleTabs = computed<Tab[]>(() => newMode.value
  ? ['EDITOR', 'VARIABLES', 'GOVERNANCE']
  : tabs)

const draft = reactive<PromptConfiguration>(emptyConfiguration())

const canEdit = computed(() => newMode.value || selected.value?.prompt.status !== 'ARCHIVED')
const canEditTests = computed(() => ['DRAFT', 'TESTING'].includes(selected.value?.prompt.status || ''))
const canRunTests = computed(() => selected.value?.prompt.status === 'TESTING')
const variableNames = computed(() => draft.variables.map(item => item.name).filter(Boolean))
const localPreview = computed(() => {
  let variables: Record<string, unknown> = {}
  try { variables = JSON.parse(previewInput.value || '{}') } catch { /* keep empty preview */ }
  const render = (template?: string) => (template || '').replace(/\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*}}/g, (_, name: string) => {
    const value = variables[name] ?? draft.variables.find(item => item.name === name)?.defaultValue ?? `{{${name}}}`
    return typeof value === 'object' ? JSON.stringify(value) : String(value)
  })
  const systemPrompt = render(draft.systemPrompt)
  const userPrompt = render(draft.userPrompt)
  const assistantPrompt = render(draft.assistantPrompt)
  const combined = [systemPrompt, userPrompt, assistantPrompt].filter(Boolean).join('\n\n')
  return { systemPrompt, userPrompt, assistantPrompt, combined, tokens: Math.max(1, Math.ceil(combined.length / 4)) }
})

onMounted(() => loadPrompts())

watch(() => filters.query, () => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => loadPrompts(), 250)
})

function emptyConfiguration(): PromptConfiguration {
  return {
    name: '',
    description: '',
    category: 'CONVERSATION',
    sceneId: '',
    visibility: 'PUBLIC',
    projectCode: '',
    departmentCode: '',
    systemPrompt: '',
    userPrompt: '{{content}}',
    assistantPrompt: '',
    changeLog: '',
    variables: [{
      name: 'content',
      type: 'STRING',
      required: true,
      defaultValue: 'Hello AI',
      description: 'Business input'
    }],
    outputSchema: { schemaJson: '', strictMode: false },
    guardrails: [],
    chain: []
  }
}

async function loadPrompts(preferredId?: string) {
  loading.value = true
  try {
    prompts.value = await listPrompts(filters)
    const id = preferredId || selected.value?.prompt.id || prompts.value[0]?.id
    if (id && prompts.value.some(item => item.id === id)) await selectPrompt(id)
    else if (!newMode.value) selected.value = undefined
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

async function selectPrompt(promptOrId: PromptSummary | string) {
  const id = typeof promptOrId === 'string' ? promptOrId : promptOrId.id
  busy.value = true
  try {
    const [detail, history, cases, experiments, entries, logs] = await Promise.all([
      getPrompt(id),
      listPromptVersions(id),
      listPromptTestCases(id),
      listPromptAbTests(id),
      getPromptAudit(id),
      getPromptRenderLogs(id)
    ])
    selected.value = detail
    versions.value = history
    testCases.value = cases
    abTests.value = experiments
    audit.value = entries
    renderLogs.value = logs
    newMode.value = false
    code.value = detail.prompt.code
    applyDetail(detail)
    suite.value = undefined
    rendered.value = undefined
    diff.value = []
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

function applyDetail(detail: PromptDetail) {
  const version = detail.currentVersion
  Object.assign(draft, {
    name: detail.prompt.name,
    description: detail.prompt.description || '',
    category: detail.prompt.category,
    sceneId: detail.prompt.sceneId || '',
    visibility: detail.prompt.visibility,
    projectCode: detail.prompt.projectCode || '',
    departmentCode: detail.prompt.departmentCode || '',
    systemPrompt: version.systemPrompt || '',
    userPrompt: version.userPrompt || '',
    assistantPrompt: version.assistantPrompt || '',
    changeLog: '',
    variables: version.variables.map(item => ({ ...item })),
    outputSchema: { ...version.outputSchema, schemaJson: version.outputSchema.schemaJson || '' },
    guardrails: version.guardrails.map(item => ({ ...item })),
    chain: version.chain.map(item => ({ ...item }))
  })
  previewInput.value = JSON.stringify(sampleVariables(), null, 2)
}

function openNew() {
  selected.value = undefined
  versions.value = []
  testCases.value = []
  abTests.value = []
  audit.value = []
  renderLogs.value = []
  Object.assign(draft, emptyConfiguration())
  code.value = ''
  newMode.value = true
  tab.value = 'EDITOR'
  previewInput.value = JSON.stringify(sampleVariables(), null, 2)
}

async function save() {
  if (!draft.name.trim() || !draft.userPrompt.trim() || (newMode.value && !code.value.trim())) return
  busy.value = true
  try {
    const result = newMode.value
      ? await createPrompt(code.value, normalizedDraft())
      : await updatePrompt(selected.value!.prompt.id, normalizedDraft())
    notice.value = { type: 'success', text: newMode.value ? t('prompts.created') : t('prompts.versionCreated') }
    await loadPrompts(result.prompt.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

function normalizedDraft(): PromptConfiguration {
  return {
    ...draft,
    description: draft.description || undefined,
    sceneId: draft.sceneId || undefined,
    projectCode: draft.projectCode || undefined,
    departmentCode: draft.departmentCode || undefined,
    systemPrompt: draft.systemPrompt,
    assistantPrompt: draft.assistantPrompt,
    changeLog: draft.changeLog || undefined,
    outputSchema: {
      schemaJson: draft.outputSchema.schemaJson || undefined,
      strictMode: draft.outputSchema.strictMode
    },
    variables: draft.variables.map(item => ({
      ...item,
      defaultValue: item.defaultValue || undefined,
      description: item.description || undefined
    })),
    guardrails: draft.guardrails,
    chain: draft.chain.map(item => ({ ...item, version: item.version || undefined }))
  }
}

async function transition(status: PromptStatus) {
  if (!selected.value) return
  busy.value = true
  try {
    await transitionPrompt(selected.value.prompt.id, status)
    notice.value = { type: 'success', text: t('common.saved') }
    await loadPrompts(selected.value.prompt.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function render(variables: Record<string, unknown>) {
  if (!selected.value) return
  busy.value = true
  try {
    rendered.value = await renderPrompt(selected.value.prompt.id, variables)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function createTest(data: { name: string; inputJson: string; expectedOutput?: string; enabled: boolean }) {
  if (!selected.value) return
  busy.value = true
  try {
    await createPromptTestCase(selected.value.prompt.id, data)
    testCases.value = await listPromptTestCases(selected.value.prompt.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function deleteTest(id: string) {
  if (!selected.value) return
  busy.value = true
  try {
    await deletePromptTestCase(selected.value.prompt.id, id)
    testCases.value = await listPromptTestCases(selected.value.prompt.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function runTests() {
  if (!selected.value) return
  busy.value = true
  try {
    const result = await runPromptTests(selected.value.prompt.id)
    await selectPrompt(selected.value.prompt.id)
    suite.value = result
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function rollback(version: number) {
  if (!selected.value || !window.confirm(`${t('prompts.confirmRollback')} V${version}?`)) return
  busy.value = true
  try {
    await rollbackPrompt(selected.value.prompt.id, version)
    await loadPrompts(selected.value.prompt.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function compare(left: number, right: number) {
  if (!selected.value) return
  busy.value = true
  try {
    diff.value = await comparePromptVersions(selected.value.prompt.id, left, right)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function createAb(data: { name: string; versionA: number; versionB: number; trafficRatio: number }) {
  if (!selected.value) return
  busy.value = true
  try {
    await createPromptAbTest(selected.value.prompt.id, data)
    abTests.value = await listPromptAbTests(selected.value.prompt.id)
  } catch (error) {
    showError(error)
  } finally {
    busy.value = false
  }
}

async function assignAb(id: string, subject: string) {
  if (!selected.value) return
  try {
    const assignment = await assignPromptAbTest(selected.value.prompt.id, id, subject)
    notice.value = { type: 'success', text: `${t('prompts.assigned')} ${assignment.variant} · V${assignment.version} · #${assignment.bucket}` }
  } catch (error) {
    showError(error)
  }
}

function addVariable() {
  draft.variables.push({ name: `variable_${draft.variables.length + 1}`, type: 'STRING', required: false, defaultValue: '', description: '' })
}

function addGuardrail() {
  draft.guardrails.push({ type: 'LENGTH', phase: 'INPUT', configJson: '{"maxChars":10000}', enabled: true })
}

function addChainStep() {
  draft.chain.push({ reference: '', version: undefined, optional: false })
}

function sampleVariables() {
  const values: Record<string, unknown> = {}
  draft.variables.forEach(variable => {
    if (variable.defaultValue) {
      if (variable.type === 'STRING') values[variable.name] = variable.defaultValue
      else {
        try { values[variable.name] = JSON.parse(variable.defaultValue) } catch { values[variable.name] = variable.defaultValue }
      }
    }
  })
  return values
}

function showError(error: unknown) {
  const text = error instanceof ApiError
    ? `${error.message}${error.errorCode ? ` (${error.errorCode})` : ''}`
    : error instanceof Error ? error.message : t('common.failed')
  notice.value = { type: 'error', text }
}
</script>

<template>
  <section class="prompts-page">
    <header class="page-header">
      <div><h1>{{ t('prompts.title') }}</h1><p>{{ t('prompts.subtitle') }}</p></div>
      <button class="button button-primary" type="button" :disabled="busy" @click="openNew">{{ t('prompts.new') }}</button>
    </header>

    <div class="filterbar">
      <input v-model="filters.query" class="input search" :placeholder="t('prompts.search')" />
      <select v-model="filters.category" class="input" @change="loadPrompts()"><option value="">{{ t('prompts.allCategories') }}</option><option v-for="item in categories" :key="item">{{ item }}</option></select>
      <select v-model="filters.status" class="input" @change="loadPrompts()"><option :value="undefined">{{ t('prompts.allStatuses') }}</option><option v-for="item in statuses" :key="item">{{ item }}</option></select>
      <select v-model="filters.visibility" class="input" @change="loadPrompts()"><option :value="undefined">{{ t('prompts.allVisibility') }}</option><option v-for="item in visibilities" :key="item">{{ item }}</option></select>
    </div>

    <div v-if="notice" class="notice" :class="notice.type"><span>{{ notice.text }}</span><button type="button" @click="notice = undefined">×</button></div>

    <div class="workspace">
      <PromptList :prompts="prompts" :selected-id="selected?.prompt.id" :loading="loading" @select="selectPrompt" @create="openNew" />

      <main v-if="selected || newMode" class="prompt-detail">
        <header class="detail-header">
          <div>
            <div class="title-row">
              <h2>{{ newMode ? t('prompts.new') : selected?.prompt.name }}</h2>
              <span v-if="selected" class="badge" :class="{ 'badge-success': selected.prompt.status === 'PUBLISHED', 'badge-warning': selected.prompt.status === 'TESTING' }">{{ selected.prompt.status }}</span>
              <span v-if="selected?.prompt.publishedVersion" class="badge badge-success">LIVE V{{ selected.prompt.publishedVersion }}</span>
            </div>
            <p>{{ newMode ? t('prompts.newHint') : `${selected?.prompt.code} · ${selected?.prompt.visibility}` }}</p>
          </div>
          <div class="actions">
            <button v-if="selected" class="button button-emphasis" type="button" :disabled="busy" @click="playgroundOpen = true">{{ t('prompts.playground') }}</button>
            <button class="button button-primary" type="button" :disabled="busy || !canEdit" @click="save">{{ newMode ? t('prompts.create') : t('prompts.saveVersion') }}</button>
          </div>
        </header>

        <div v-if="selected" class="lifecycle">
          <button v-if="selected.prompt.status === 'DRAFT'" class="button button-emphasis" type="button" @click="transition('TESTING')">{{ t('prompts.toTesting') }}</button>
          <button v-if="selected.prompt.status === 'TESTING'" class="button button-secondary" type="button" @click="transition('DRAFT')">{{ t('prompts.backDraft') }}</button>
          <button v-if="selected.prompt.status === 'TESTING'" class="button button-primary" type="button" :disabled="!selected.currentVersion.testsPassed" @click="transition('PUBLISHED')">{{ t('prompts.publish') }}</button>
          <button v-if="selected.prompt.status === 'PUBLISHED'" class="button button-danger" type="button" @click="transition('DEPRECATED')">{{ t('prompts.deprecate') }}</button>
          <button v-if="['DRAFT', 'DEPRECATED'].includes(selected.prompt.status)" class="button button-danger" type="button" @click="transition('ARCHIVED')">{{ t('prompts.archive') }}</button>
          <span v-if="selected.prompt.status === 'TESTING' && !selected.currentVersion.testsPassed" class="muted">{{ t('prompts.publishGate') }}</span>
        </div>

        <nav class="tabs">
          <button v-for="item in visibleTabs" :key="item" type="button" :class="{ active: tab === item }" @click="tab = item">{{ t(`prompts.tab.${item.toLowerCase()}`) }}</button>
        </nav>

        <section v-if="tab === 'EDITOR'" class="tab-content editor-tab">
          <div class="metadata-grid">
            <div v-if="newMode" class="field"><label>{{ t('prompts.code') }}</label><input v-model="code" class="input" /></div>
            <div class="field"><label>{{ t('prompts.name') }}</label><input v-model="draft.name" class="input" /></div>
            <div class="field"><label>{{ t('prompts.category') }}</label><input v-model="draft.category" class="input" list="prompt-categories" /><datalist id="prompt-categories"><option v-for="item in categories" :key="item" :value="item" /></datalist></div>
            <div class="field"><label>{{ t('prompts.scene') }}</label><input v-model="draft.sceneId" class="input" /></div>
            <div class="field wide"><label>{{ t('prompts.description') }}</label><input v-model="draft.description" class="input" /></div>
            <div class="field wide"><label>{{ t('prompts.changeLog') }}</label><input v-model="draft.changeLog" class="input" :placeholder="t('prompts.changeLogHint')" /></div>
          </div>
          <div class="editor-layout">
            <div class="editors">
              <PromptCodeEditor v-model="draft.systemPrompt" :label="t('prompts.systemPrompt')" :variables="variableNames" />
              <PromptCodeEditor v-model="draft.userPrompt" :label="t('prompts.userPrompt')" :variables="variableNames" />
              <PromptCodeEditor v-model="draft.assistantPrompt" :label="t('prompts.assistantPrompt')" :variables="variableNames" />
            </div>
            <aside class="live-preview">
              <header><strong>{{ t('prompts.livePreview') }}</strong><span class="badge">{{ localPreview.tokens }} tokens</span></header>
              <textarea v-model="previewInput" class="input preview-json" spellcheck="false" />
              <pre>{{ localPreview.combined }}</pre>
            </aside>
          </div>
        </section>

        <section v-else-if="tab === 'VARIABLES'" class="tab-content">
          <header class="section-header"><div><h3>{{ t('prompts.variables') }}</h3><p>{{ t('prompts.variablesHint') }}</p></div><button class="button button-emphasis" type="button" @click="addVariable">{{ t('prompts.addVariable') }}</button></header>
          <div class="rows">
            <div v-for="(variable, index) in draft.variables" :key="index" class="config-row variables-row">
              <input v-model="variable.name" class="input" :placeholder="t('prompts.variableName')" />
              <select v-model="variable.type" class="input"><option v-for="item in variableTypes" :key="item">{{ item }}</option></select>
              <input v-model="variable.defaultValue" class="input" :placeholder="t('prompts.defaultValue')" />
              <input v-model="variable.description" class="input" :placeholder="t('prompts.variableDescription')" />
              <label><input v-model="variable.required" type="checkbox" />{{ t('prompts.required') }}</label>
              <button class="button button-danger" type="button" @click="draft.variables.splice(index, 1)">×</button>
            </div>
          </div>
        </section>

        <section v-else-if="tab === 'GOVERNANCE'" class="tab-content governance">
          <div class="governance-card">
            <h3>{{ t('prompts.permission') }}</h3>
            <div class="metadata-grid">
              <div class="field"><label>{{ t('prompts.visibility') }}</label><select v-model="draft.visibility" class="input"><option v-for="item in visibilities" :key="item">{{ item }}</option></select></div>
              <div v-if="draft.visibility === 'PROJECT'" class="field"><label>{{ t('prompts.project') }}</label><input v-model="draft.projectCode" class="input" /></div>
              <div v-if="draft.visibility === 'DEPARTMENT'" class="field"><label>{{ t('prompts.department') }}</label><input v-model="draft.departmentCode" class="input" /></div>
            </div>
          </div>
          <div class="governance-card">
            <header class="section-header"><div><h3>{{ t('prompts.outputSchema') }}</h3><p>{{ t('prompts.schemaHint') }}</p></div><label><input v-model="draft.outputSchema.strictMode" type="checkbox" />{{ t('prompts.strict') }}</label></header>
            <textarea v-model="draft.outputSchema.schemaJson" class="input schema-editor" spellcheck="false" />
          </div>
          <div class="governance-card">
            <header class="section-header"><div><h3>{{ t('prompts.guardrails') }}</h3><p>{{ t('prompts.guardrailHint') }}</p></div><button class="button button-emphasis" type="button" @click="addGuardrail">{{ t('prompts.addGuardrail') }}</button></header>
            <div v-for="(guardrail, index) in draft.guardrails" :key="index" class="config-row">
              <select v-model="guardrail.type" class="input"><option v-for="item in guardrailTypes" :key="item">{{ item }}</option></select>
              <select v-model="guardrail.phase" class="input"><option v-for="item in guardrailPhases" :key="item">{{ item }}</option></select>
              <input v-model="guardrail.configJson" class="input code-input" />
              <label><input v-model="guardrail.enabled" type="checkbox" />{{ t('prompts.enabled') }}</label>
              <button class="button button-danger" type="button" @click="draft.guardrails.splice(index, 1)">×</button>
            </div>
          </div>
          <div class="governance-card">
            <header class="section-header"><div><h3>{{ t('prompts.chain') }}</h3><p>{{ t('prompts.chainHint') }}</p></div><button class="button button-emphasis" type="button" @click="addChainStep">{{ t('prompts.addStep') }}</button></header>
            <div v-for="(step, index) in draft.chain" :key="index" class="config-row">
              <input v-model="step.reference" class="input" :placeholder="t('prompts.promptReference')" />
              <input v-model.number="step.version" class="input small" type="number" min="1" placeholder="Version" />
              <label><input v-model="step.optional" type="checkbox" />{{ t('prompts.optional') }}</label>
              <button class="button button-danger" type="button" @click="draft.chain.splice(index, 1)">×</button>
            </div>
          </div>
        </section>

        <section v-else-if="tab === 'TESTS' && selected" class="tab-content">
          <PromptTestsPanel :test-cases="testCases" :suite="suite" :busy="busy" :can-edit="canEditTests" :can-run="canRunTests" @create="createTest" @delete="deleteTest" @run="runTests" />
        </section>

        <section v-else-if="tab === 'VERSIONS' && selected" class="tab-content">
          <PromptVersionsPanel :versions="versions" :diff="diff" :busy="busy" @compare="compare" @rollback="rollback" />
        </section>

        <section v-else-if="tab === 'AB' && selected" class="tab-content">
          <PromptAbPanel :tests="abTests" :versions="versions" :busy="busy" @create="createAb" @assign="assignAb" />
        </section>

        <section v-else-if="tab === 'AUDIT' && selected" class="tab-content audit-grid">
          <div><h3>{{ t('prompts.audit') }}</h3><article v-for="item in audit" :key="item.id"><strong>{{ item.action }}</strong><span>{{ item.createUser }} · {{ new Date(item.createTime).toLocaleString() }}</span><code>{{ item.detail }}</code></article></div>
          <div><h3>{{ t('prompts.renderLogs') }}</h3><article v-for="item in renderLogs" :key="item.id"><strong>{{ item.mode }} · {{ item.estimatedTokens }} tokens</strong><span>{{ item.contentStored ? t('prompts.contentStored') : t('prompts.contentRedacted') }}</span><code>{{ item.contentHash.slice(0, 16) }}…</code></article></div>
        </section>
      </main>

      <div v-else class="select-hint">{{ t('prompts.selectHint') }}</div>
    </div>

    <PromptPlayground v-if="selected" :open="playgroundOpen" :variables="selected.currentVersion.variables" :result="rendered" :busy="busy" @close="playgroundOpen = false" @render="render" />
  </section>
</template>

<style scoped>
.prompts-page {
  width: 100%;
}

.page-header,
.filterbar,
.notice,
.detail-header,
.title-row,
.actions,
.lifecycle,
.tabs,
.section-header {
  display: flex;
  align-items: center;
}

.page-header,
.detail-header,
.section-header {
  justify-content: space-between;
  gap: 14px;
}

.page-header {
  margin-bottom: 14px;
}

.page-header h1,
.page-header p,
.detail-header h2,
.detail-header p,
.section-header h3,
.section-header p,
.governance-card h3 {
  margin: 0;
}

.page-header h1 {
  font-size: 22px;
}

.page-header p,
.detail-header p,
.section-header p {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 11px;
}

.filterbar {
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.filterbar .input {
  width: auto;
  min-width: 160px;
}

.filterbar .search {
  flex: 1;
  min-width: 240px;
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
  grid-template-columns: minmax(250px, 330px) minmax(0, 1fr);
  min-height: calc(100vh - 190px);
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.prompt-detail {
  min-width: 0;
  overflow: auto;
}

.detail-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

.detail-header h2 {
  font-size: 17px;
}

.title-row,
.actions,
.lifecycle {
  flex-wrap: wrap;
  gap: 7px;
}

.lifecycle {
  min-height: 48px;
  padding: 7px 16px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-secondary);
}

.tabs {
  gap: 3px;
  padding: 8px 12px;
  overflow-x: auto;
  border-bottom: 1px solid var(--border);
}

.tabs button {
  padding: 6px 9px;
  border: 0;
  border-radius: 8px;
  color: var(--text-secondary);
  background: transparent;
  font-size: 11px;
  font-weight: 700;
}

.tabs button.active {
  color: var(--accent);
  background: var(--accent-bg);
}

.tab-content {
  padding: 14px 16px 24px;
}

.metadata-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(130px, 1fr));
  gap: 10px;
}

.metadata-grid .wide {
  grid-column: span 2;
}

.editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
  gap: 12px;
  margin-top: 12px;
}

.editors,
.live-preview,
.rows,
.governance,
.audit-grid > div {
  display: grid;
  align-content: start;
  gap: 10px;
}

.live-preview {
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 11px;
  background: var(--bg-secondary);
}

.live-preview header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-json {
  min-height: 110px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.live-preview pre {
  min-height: 330px;
  max-height: 520px;
  margin: 0;
  padding: 11px;
  overflow: auto;
  border-radius: 9px;
  color: #f2f2f7;
  background: #1c1c1e;
  white-space: pre-wrap;
  font: 12px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace;
}

.config-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  padding: 9px;
  border: 1px solid var(--border);
  border-radius: 10px;
}

.config-row .input {
  width: auto;
  min-width: 140px;
  flex: 1;
}

.config-row .small {
  max-width: 110px;
}

.config-row label,
.section-header label {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-secondary);
  font-size: 11px;
}

.variables-row {
  align-items: flex-start;
}

.governance-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 11px;
}

.governance-card h3,
.section-header h3,
.audit-grid h3 {
  font-size: 15px;
}

.schema-editor {
  min-height: 180px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.code-input {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.audit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.audit-grid article {
  display: grid;
  gap: 5px;
  padding: 9px;
  border: 1px solid var(--border);
  border-radius: 9px;
}

.audit-grid article span,
.audit-grid article code {
  color: var(--text-secondary);
  font-size: 10px;
}

.select-hint {
  display: grid;
  place-items: center;
  color: var(--text-secondary);
  font-size: 13px;
}

@media (max-width: 1050px) {
  .metadata-grid {
    grid-template-columns: repeat(2, minmax(130px, 1fr));
  }

  .editor-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .page-header,
  .detail-header {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 620px) {
  .metadata-grid,
  .audit-grid {
    grid-template-columns: 1fr;
  }

  .metadata-grid .wide {
    grid-column: auto;
  }
}
</style>
