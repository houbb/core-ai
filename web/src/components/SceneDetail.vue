<script setup lang="ts">
import { ref } from 'vue'
import { t } from '../i18n'
import type { AiScene, SceneStatus, SceneVersion } from '../types/scene'

defineProps<{
  scene: AiScene
  versions: SceneVersion[]
  audit: Array<{
    id: string
    action: string
    result: string
    createTime: string
    createUser: string
    traceId?: string
  }>
  busy?: boolean
}>()

defineEmits<{
  edit: []
  status: [status: SceneStatus]
  test: []
  export: []
  rollback: [version: number]
  saveTemplate: [templateName: string, defaultCode: string]
}>()

const tab = ref<'overview' | 'models' | 'versions' | 'audit'>('overview')
const templateName = ref('')
const templateCode = ref('')

function time(value?: string) {
  return value
    ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })
      .format(new Date(value))
    : '—'
}
</script>

<template>
  <section class="scene-detail">
    <header class="detail-header">
      <div class="identity">
        <span class="detail-icon">{{ scene.icon || '✦' }}</span>
        <div>
          <div class="title-line">
            <h1>{{ scene.name }}</h1>
            <span
              class="badge"
              :class="{
                'badge-success': scene.status === 'PUBLISHED',
                'badge-warning': scene.status === 'DRAFT' || scene.status === 'TESTING',
                'badge-danger': scene.status === 'ARCHIVED'
              }"
            >{{ scene.status }}</span>
            <span v-if="scene.recommended" class="badge badge-success">★ {{ t('scenes.recommended') }}</span>
          </div>
          <p>{{ scene.code }} · {{ scene.category }} · V{{ scene.version }}</p>
        </div>
      </div>
      <div class="detail-actions">
        <button class="button button-secondary" type="button" :disabled="busy" @click="$emit('export')">
          {{ t('scenes.export') }}
        </button>
        <button
          v-if="scene.status === 'DRAFT' || scene.status === 'TESTING'"
          class="button button-primary"
          type="button"
          :disabled="busy"
          @click="$emit('edit')"
        >
          {{ t('providers.edit') }}
        </button>
      </div>
    </header>

    <div class="lifecycle-actions">
      <button v-if="scene.status === 'DRAFT'" class="button button-emphasis" type="button" :disabled="busy" @click="$emit('status', 'TESTING')">{{ t('scenes.startTesting') }}</button>
      <button v-if="scene.status === 'TESTING'" class="button button-primary" type="button" :disabled="busy" @click="$emit('test')">{{ t('scenes.playground') }}</button>
      <button v-if="scene.status === 'PUBLISHED'" class="button button-primary" type="button" :disabled="busy" @click="$emit('test')">{{ t('scenes.run') }}</button>
      <button v-if="scene.status === 'TESTING'" class="button button-emphasis" type="button" :disabled="busy || scene.lastTestedVersion !== scene.version" @click="$emit('status', 'PUBLISHED')">{{ t('scenes.publish') }}</button>
      <button v-if="scene.status === 'TESTING'" class="button button-secondary" type="button" :disabled="busy" @click="$emit('status', 'DRAFT')">{{ t('scenes.backToDraft') }}</button>
      <button v-if="scene.status === 'PUBLISHED'" class="button button-secondary" type="button" :disabled="busy" @click="$emit('status', 'DISABLED')">{{ t('scenes.disable') }}</button>
      <button v-if="scene.status === 'DISABLED'" class="button button-emphasis" type="button" :disabled="busy" @click="$emit('status', 'DRAFT')">{{ t('scenes.newVersion') }}</button>
      <button v-if="scene.status === 'DRAFT' || scene.status === 'DISABLED'" class="button button-danger" type="button" :disabled="busy" @click="$emit('status', 'ARCHIVED')">{{ t('scenes.archive') }}</button>
      <span v-if="scene.lastTestedAt" class="tested">{{ t('scenes.lastTested') }} {{ time(scene.lastTestedAt) }}</span>
    </div>

    <nav class="tabs">
      <button :class="{ active: tab === 'overview' }" type="button" @click="tab = 'overview'">{{ t('providers.overview') }}</button>
      <button :class="{ active: tab === 'models' }" type="button" @click="tab = 'models'">{{ t('scenes.models') }}</button>
      <button :class="{ active: tab === 'versions' }" type="button" @click="tab = 'versions'">{{ t('scenes.versions') }}</button>
      <button :class="{ active: tab === 'audit' }" type="button" @click="tab = 'audit'">{{ t('providers.audit') }}</button>
    </nav>

    <div v-if="tab === 'overview'" class="detail-body">
      <div class="metric-grid">
        <article><span>{{ t('scenes.primaryModel') }}</span><strong>{{ scene.models.find((item) => !item.fallback)?.modelDisplayName || scene.models.find((item) => !item.fallback)?.modelAlias }}</strong><small>{{ scene.models.find((item) => !item.fallback)?.providerName || t('scenes.unresolved') }}</small></article>
        <article><span>{{ t('scenes.cost') }}</span><strong>{{ scene.costTier }}</strong><small>{{ t('scenes.estimateOnly') }}</small></article>
        <article><span>{{ t('scenes.prompt') }}</span><strong>{{ scene.prompt?.promptId || '—' }}</strong><small>V{{ scene.prompt?.promptVersion || '—' }}</small></article>
        <article><span>{{ t('scenes.permissions') }}</span><strong>{{ scene.permissions.length }}</strong><small>{{ scene.permissions.map((item) => item.type).join(', ') }}</small></article>
      </div>
      <div class="section-card">
        <h2>{{ t('scenes.description') }}</h2>
        <p>{{ scene.description || '—' }}</p>
      </div>
      <div class="section-card">
        <h2>{{ t('scenes.parameters') }}</h2>
        <dl>
          <div><dt>Temperature</dt><dd>{{ scene.parameters.temperature ?? '—' }}</dd></div>
          <div><dt>Top P</dt><dd>{{ scene.parameters.topP ?? '—' }}</dd></div>
          <div><dt>{{ t('models.maxOutput') }}</dt><dd>{{ scene.parameters.maxOutputTokens ?? '—' }}</dd></div>
          <div><dt>{{ t('models.reasoningEffort') }}</dt><dd>{{ scene.parameters.reasoningEffort || '—' }}</dd></div>
          <div><dt>JSON</dt><dd>{{ scene.parameters.jsonMode ? t('common.yes') : t('common.no') }}</dd></div>
          <div><dt>Streaming</dt><dd>{{ scene.parameters.streaming ? t('common.yes') : t('common.no') }}</dd></div>
        </dl>
      </div>
      <div class="section-card">
        <h2>{{ t('scenes.workflow') }}</h2>
        <div v-if="!scene.workflow.length" class="muted">—</div>
        <div v-else class="workflow-list">
          <span v-for="step in scene.workflow" :key="step.code" class="badge">
            {{ step.code }} · {{ step.type }} → {{ step.reference }}
          </span>
        </div>
      </div>
    </div>

    <div v-else-if="tab === 'models'" class="detail-body">
      <div class="model-list">
        <article v-for="model in scene.models" :key="model.id || model.modelAlias">
          <span class="badge" :class="{ 'badge-success': model.resolved, 'badge-danger': !model.resolved }">{{ model.resolved ? t('scenes.resolved') : t('scenes.unresolved') }}</span>
          <div><strong>{{ model.modelDisplayName || model.modelAlias }}</strong><small>{{ model.modelAlias }} · {{ model.providerName || '—' }}</small></div>
          <span>{{ model.fallback ? t('scenes.fallback') : t('scenes.primary') }}</span>
          <code>{{ model.priority }}</code>
        </article>
      </div>
    </div>

    <div v-else-if="tab === 'versions'" class="detail-body">
      <div v-if="!versions.length" class="empty-tab">{{ t('scenes.noVersions') }}</div>
      <div v-else class="version-list">
        <article v-for="version in versions" :key="version.id">
          <div><strong>V{{ version.version }}</strong><small>{{ version.createUser }} · {{ time(version.createTime) }}</small></div>
          <span>{{ version.configuration.models[0]?.modelAlias }}</span>
          <button class="button button-secondary" type="button" :disabled="busy || scene.status === 'PUBLISHED' || scene.status === 'ARCHIVED'" @click="$emit('rollback', version.version)">{{ t('scenes.rollback') }}</button>
        </article>
      </div>
      <form class="template-form" @submit.prevent="$emit('saveTemplate', templateName, templateCode)">
        <h2>{{ t('scenes.saveTemplate') }}</h2>
        <input v-model="templateName" class="input" :placeholder="t('scenes.templateName')" required />
        <input v-model="templateCode" class="input" :placeholder="t('scenes.defaultCode')" required pattern="[a-z0-9][a-z0-9._-]{1,99}" />
        <button class="button button-primary" type="submit" :disabled="busy">{{ t('scenes.saveTemplate') }}</button>
      </form>
    </div>

    <div v-else class="detail-body">
      <div v-if="!audit.length" class="empty-tab">{{ t('providers.noAudit') }}</div>
      <div v-else class="audit-list">
        <article v-for="entry in audit" :key="entry.id">
          <span class="badge badge-success">{{ entry.result }}</span>
          <div><strong>{{ entry.action }}</strong><small>{{ entry.createUser }} · {{ time(entry.createTime) }}</small></div>
          <code>{{ entry.traceId }}</code>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.scene-detail {
  min-width: 0;
}

.detail-header,
.identity,
.title-line,
.detail-actions,
.lifecycle-actions,
.workflow-list,
.model-list article,
.version-list article,
.audit-list article {
  display: flex;
  align-items: center;
}

.detail-header {
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
  border-bottom: 1px solid var(--border);
}

.identity,
.title-line,
.detail-actions,
.lifecycle-actions,
.workflow-list {
  gap: 8px;
}

.detail-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: var(--accent-bg);
  font-size: 24px;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  font-size: 17px;
}

.identity p,
.tested {
  color: var(--text-secondary);
  font-size: 11px;
}

.detail-actions,
.lifecycle-actions,
.workflow-list {
  flex-wrap: wrap;
}

.lifecycle-actions {
  padding: 8px 14px;
  border-bottom: 1px solid var(--border);
  background: rgba(245, 245, 247, 0.55);
}

.tested {
  margin-left: auto;
}

.tabs {
  display: flex;
  gap: 4px;
  padding: 8px 14px 0;
  border-bottom: 1px solid var(--border);
}

.tabs button {
  padding: 8px 10px;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--text-secondary);
  background: transparent;
  font-size: 12px;
  font-weight: 600;
}

.tabs button.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}

.detail-body {
  display: grid;
  gap: 12px;
  padding: 14px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.metric-grid article,
.section-card,
.model-list article,
.version-list article,
.audit-list article,
.template-form {
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 11px;
  background: var(--surface);
}

.metric-grid article {
  display: grid;
  gap: 4px;
}

.metric-grid span,
.metric-grid small,
dt,
.model-list small,
.version-list small,
.audit-list small {
  color: var(--text-secondary);
  font-size: 11px;
}

.metric-grid strong {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.section-card h2,
.template-form h2 {
  margin-bottom: 9px;
  font-size: 13px;
}

.section-card p {
  font-size: 13px;
  line-height: 1.6;
}

dl {
  display: grid;
  gap: 6px;
  margin: 0;
}

dl div {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
}

dd {
  margin: 0;
  font-size: 13px;
}

.model-list,
.version-list,
.audit-list {
  display: grid;
  gap: 7px;
}

.model-list article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  gap: 10px;
}

.model-list article div,
.version-list article div,
.audit-list article div {
  display: grid;
  gap: 2px;
}

.version-list article {
  justify-content: space-between;
  gap: 10px;
}

.audit-list article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
}

.template-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 8px;
}

.template-form h2 {
  grid-column: 1 / -1;
}

.empty-tab {
  padding: 50px 20px;
  color: var(--text-secondary);
  text-align: center;
  font-size: 13px;
}

@media (max-width: 980px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .detail-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid,
  .template-form {
    grid-template-columns: 1fr;
  }

  .model-list article,
  .audit-list article {
    grid-template-columns: 1fr;
  }
}
</style>
