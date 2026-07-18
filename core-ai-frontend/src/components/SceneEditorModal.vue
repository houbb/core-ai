<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { t } from '../i18n'
import type {
  AiScene,
  SceneConfiguration,
  ScenePermission
} from '../types/scene'
import type { PromptSummary } from '../types/prompt'

const props = defineProps<{
  open: boolean
  scene?: AiScene
  aliases?: string[]
  prompts?: PromptSummary[]
  saving?: boolean
}>()

const emit = defineEmits<{
  close: []
  save: [code: string, configuration: SceneConfiguration]
}>()

const categories = [
  'CONVERSATION', 'WRITING', 'TRANSLATE', 'SUMMARIZE', 'CODING', 'CODE_REVIEW',
  'SQL', 'OCR', 'VISION', 'SPEECH', 'IMAGE', 'VIDEO', 'EMBEDDING', 'KNOWLEDGE',
  'RAG', 'REASONING', 'WORKFLOW', 'AGENT'
]
const tab = ref<'basic' | 'models' | 'parameters' | 'prompt' | 'permissions' | 'workflow'>('basic')
const code = ref('')
const draft = reactive<SceneConfiguration>({
  name: '',
  description: '',
  category: 'CONVERSATION',
  icon: '✦',
  recommended: false,
  models: [],
  parameters: { jsonMode: false, streaming: true },
  prompt: {},
  permissions: [],
  workflow: []
})

watch(
  () => [props.open, props.scene] as const,
  () => {
    if (!props.open) return
    tab.value = 'basic'
    code.value = props.scene?.code || ''
    Object.assign(draft, {
      name: props.scene?.name || '',
      description: props.scene?.description || '',
      category: props.scene?.category || 'CONVERSATION',
      icon: props.scene?.icon || '✦',
      recommended: props.scene?.recommended || false,
      parameters: { ...(props.scene?.parameters || { jsonMode: false, streaming: true }) },
      prompt: { ...(props.scene?.prompt || {}) }
    })
    draft.models = props.scene?.models.map(model => ({
      modelAlias: model.modelAlias,
      priority: model.priority,
      fallback: model.fallback,
      enabled: model.enabled
    })) || [{
      modelAlias: props.aliases?.includes('chat-default') ? 'chat-default' : (props.aliases?.[0] || 'chat-default'),
      priority: 10,
      fallback: false,
      enabled: true
    }]
    draft.permissions = props.scene?.permissions.map(item => ({ ...item }))
      || [{ type: 'EVERYONE', value: '*' }]
    draft.workflow = props.scene?.workflow.map(item => ({ ...item })) || []
  },
  { immediate: true }
)

function addModel() {
  draft.models.push({
    modelAlias: props.aliases?.[0] || 'chat-default',
    priority: (draft.models.length + 1) * 10,
    fallback: draft.models.length > 0,
    enabled: true
  })
}

function removeModel(index: number) {
  draft.models.splice(index, 1)
}

function selectPrimary(index: number) {
  if (draft.models[index].fallback) return
  draft.models.forEach((model, current) => {
    if (current !== index && model.enabled) model.fallback = true
  })
}

function addPermission() {
  draft.permissions.push({ type: 'ROLE', value: 'DEVELOPER' })
}

function removePermission(index: number) {
  draft.permissions.splice(index, 1)
}

function normalizePermission(permission: ScenePermission) {
  if (permission.type === 'EVERYONE') permission.value = '*'
  else if (permission.value === '*') permission.value = ''
}

function addWorkflow() {
  draft.workflow.push({
    code: `step-${draft.workflow.length + 1}`,
    type: 'MODEL_ALIAS',
    reference: props.aliases?.[0] || 'chat-default',
    optional: false
  })
}

function selectPrompt() {
  const selected = props.prompts?.find(item => item.code === draft.prompt.promptId)
  draft.prompt.promptVersion = selected?.publishedVersion
}

function removeWorkflow(index: number) {
  draft.workflow.splice(index, 1)
}

function save() {
  emit('save', code.value, {
    name: draft.name,
    description: draft.description,
    category: draft.category,
    icon: draft.icon,
    recommended: draft.recommended,
    models: draft.models.map(model => ({ ...model })),
    parameters: { ...draft.parameters },
    prompt: { ...draft.prompt },
    permissions: draft.permissions.map(permission => ({ ...permission })),
    workflow: draft.workflow.map(step => ({ ...step }))
  })
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" @mousedown.self="$emit('close')">
    <section class="modal" role="dialog" aria-modal="true">
      <header>
        <div><h2>{{ scene?.name || t('scenes.new') }}</h2><p>{{ t('scenes.editorHint') }}</p></div>
        <button class="close-button" type="button" :disabled="saving" @click="$emit('close')">×</button>
      </header>
      <nav class="editor-tabs">
        <button :class="{ active: tab === 'basic' }" type="button" @click="tab = 'basic'">{{ t('models.basic') }}</button>
        <button :class="{ active: tab === 'models' }" type="button" @click="tab = 'models'">{{ t('scenes.models') }}</button>
        <button :class="{ active: tab === 'parameters' }" type="button" @click="tab = 'parameters'">{{ t('scenes.parameters') }}</button>
        <button :class="{ active: tab === 'prompt' }" type="button" @click="tab = 'prompt'">{{ t('scenes.prompt') }}</button>
        <button :class="{ active: tab === 'permissions' }" type="button" @click="tab = 'permissions'">{{ t('scenes.permissions') }}</button>
        <button :class="{ active: tab === 'workflow' }" type="button" @click="tab = 'workflow'">{{ t('scenes.workflow') }}</button>
      </nav>

      <form @submit.prevent="save">
        <div v-if="tab === 'basic'" class="form-grid">
          <div class="field"><label>{{ t('scenes.code') }}</label><input v-model="code" class="input" :disabled="!!scene" required pattern="[a-z0-9][a-z0-9._-]{1,99}" /></div>
          <div class="field"><label>{{ t('scenes.name') }}</label><input v-model="draft.name" class="input" required /></div>
          <div class="field"><label>{{ t('scenes.category') }}</label><input v-model="draft.category" class="input" list="scene-categories" required /><datalist id="scene-categories"><option v-for="category in categories" :key="category" :value="category" /></datalist></div>
          <div class="field"><label>{{ t('scenes.icon') }}</label><input v-model="draft.icon" class="input" maxlength="40" /></div>
          <div class="field field-wide"><label>{{ t('scenes.description') }}</label><textarea v-model="draft.description" class="input" /></div>
          <label class="checkbox-row field-wide"><input v-model="draft.recommended" type="checkbox" /> {{ t('scenes.recommended') }}</label>
        </div>

        <div v-else-if="tab === 'models'" class="repeater">
          <article v-for="(model, index) in draft.models" :key="index" class="repeat-row model-row">
            <div class="field"><label>{{ t('scenes.modelAlias') }}</label><input v-model="model.modelAlias" class="input" list="model-aliases" required /><datalist id="model-aliases"><option v-for="alias in aliases" :key="alias" :value="alias" /></datalist></div>
            <div class="field"><label>{{ t('scenes.priority') }}</label><input v-model.number="model.priority" class="input" type="number" min="0" max="10000" /></div>
            <div class="field"><label>{{ t('scenes.routeRole') }}</label><select v-model="model.fallback" class="input" @change="selectPrimary(index)"><option :value="false">{{ t('scenes.primary') }}</option><option :value="true">{{ t('scenes.fallback') }}</option></select></div>
            <label class="checkbox-row"><input v-model="model.enabled" type="checkbox" /> {{ t('scenes.enabled') }}</label>
            <button class="button button-danger" type="button" :disabled="draft.models.length === 1" @click="removeModel(index)">×</button>
          </article>
          <button class="button button-secondary" type="button" @click="addModel">{{ t('scenes.addModel') }}</button>
        </div>

        <div v-else-if="tab === 'parameters'" class="form-grid">
          <div class="field"><label>Temperature</label><input v-model.number="draft.parameters.temperature" class="input" type="number" min="0" max="2" step="0.01" /></div>
          <div class="field"><label>Top P</label><input v-model.number="draft.parameters.topP" class="input" type="number" min="0" max="1" step="0.01" /></div>
          <div class="field"><label>{{ t('models.maxOutput') }}</label><input v-model.number="draft.parameters.maxOutputTokens" class="input" type="number" min="1" /></div>
          <div class="field"><label>{{ t('models.reasoningEffort') }}</label><select v-model="draft.parameters.reasoningEffort" class="input"><option value="">—</option><option value="low">{{ t('models.low') }}</option><option value="medium">{{ t('models.medium') }}</option><option value="high">{{ t('models.high') }}</option></select></div>
          <label class="checkbox-row"><input v-model="draft.parameters.jsonMode" type="checkbox" /> JSON Mode</label>
          <label class="checkbox-row"><input v-model="draft.parameters.streaming" type="checkbox" /> Streaming</label>
        </div>

        <div v-else-if="tab === 'prompt'" class="form-grid">
          <div class="field"><label>Prompt</label><select v-model="draft.prompt.promptId" class="input" @change="selectPrompt"><option :value="undefined">—</option><option v-for="prompt in prompts" :key="prompt.id" :value="prompt.code">{{ prompt.name }} · {{ prompt.code }} · V{{ prompt.publishedVersion }}</option></select></div>
          <div class="field"><label>{{ t('scenes.promptVersion') }}</label><input v-model.number="draft.prompt.promptVersion" class="input" type="number" min="1" /></div>
          <p class="field-wide info">{{ t('scenes.promptHint') }}</p>
        </div>

        <div v-else-if="tab === 'permissions'" class="repeater">
          <article v-for="(permission, index) in draft.permissions" :key="index" class="repeat-row permission-row">
            <select v-model="permission.type" class="input" @change="normalizePermission(permission)">
              <option>EVERYONE</option><option>ROLE</option><option>DEPARTMENT</option><option>USER_GROUP</option>
            </select>
            <input v-model="permission.value" class="input" :disabled="permission.type === 'EVERYONE'" required />
            <button class="button button-danger" type="button" @click="removePermission(index)">×</button>
          </article>
          <button class="button button-secondary" type="button" @click="addPermission">{{ t('scenes.addPermission') }}</button>
        </div>

        <div v-else class="repeater">
          <article v-for="(step, index) in draft.workflow" :key="index" class="repeat-row workflow-row">
            <input v-model="step.code" class="input" :placeholder="t('scenes.stepCode')" required />
            <select v-model="step.type" class="input"><option>MODEL_ALIAS</option><option>SCENE</option><option>EXTERNAL</option></select>
            <input v-model="step.reference" class="input" :placeholder="t('scenes.reference')" required />
            <label class="checkbox-row"><input v-model="step.optional" type="checkbox" /> {{ t('scenes.optional') }}</label>
            <button class="button button-danger" type="button" @click="removeWorkflow(index)">×</button>
          </article>
          <button class="button button-secondary" type="button" @click="addWorkflow">{{ t('scenes.addStep') }}</button>
        </div>

        <footer>
          <button class="button button-secondary" type="button" :disabled="saving" @click="$emit('close')">{{ t('form.cancel') }}</button>
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
  width: min(920px, 100%);
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

header p,
.info {
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

.repeater {
  display: grid;
  gap: 8px;
  padding: 14px 0;
}

.repeat-row {
  display: grid;
  align-items: end;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-secondary);
}

.model-row {
  grid-template-columns: minmax(190px, 2fr) 100px 130px auto auto;
}

.permission-row {
  grid-template-columns: 180px minmax(180px, 1fr) auto;
}

.workflow-row {
  grid-template-columns: 130px 150px minmax(180px, 1fr) auto auto;
}

form > footer {
  margin: 0 -16px;
  border-top: 1px solid var(--border);
}

@media (max-width: 760px) {
  .form-grid,
  .model-row,
  .permission-row,
  .workflow-row {
    grid-template-columns: 1fr;
  }

  .field-wide {
    grid-column: auto;
  }
}
</style>
