<script setup lang="ts">
import { ref } from 'vue'
import { t } from '../i18n'
import type { AuditEntry, Provider } from '../types/provider'

defineProps<{
  provider: Provider
  audit: AuditEntry[]
  busy?: boolean
}>()

defineEmits<{
  edit: []
  test: []
  refresh: []
  toggle: []
  delete: []
}>()

const tab = ref<'overview' | 'models' | 'audit'>('overview')

function formatTime(value?: string) {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
}
</script>

<template>
  <section class="provider-detail">
    <header class="detail-header">
      <div>
        <div class="title-line">
          <h1>{{ provider.name }}</h1>
          <span class="badge" :class="{ 'badge-success': provider.status === 'AVAILABLE', 'badge-warning': provider.status === 'DRAFT' || provider.status === 'TESTING' }">
            {{ provider.status }}
          </span>
          <span class="badge">{{ provider.location }}</span>
        </div>
        <p>{{ provider.description || provider.endpoint }}</p>
      </div>
      <div class="detail-actions">
        <button class="button button-secondary" type="button" :disabled="busy" @click="$emit('edit')">
          {{ t('providers.edit') }}
        </button>
        <button class="button button-emphasis" type="button" :disabled="busy" @click="$emit('refresh')">
          {{ t('providers.refresh') }}
        </button>
        <button class="button button-primary" type="button" :disabled="busy" @click="$emit('test')">
          {{ t('providers.test') }}
        </button>
      </div>
    </header>

    <nav class="tabs" aria-label="Provider details">
      <button type="button" :class="{ active: tab === 'overview' }" @click="tab = 'overview'">
        {{ t('providers.overview') }}
      </button>
      <button type="button" :class="{ active: tab === 'models' }" @click="tab = 'models'">
        {{ t('providers.models') }} ({{ provider.modelCount }})
      </button>
      <button type="button" :class="{ active: tab === 'audit' }" @click="tab = 'audit'">
        {{ t('providers.audit') }}
      </button>
    </nav>

    <div v-if="tab === 'overview'" class="detail-body">
      <div class="metric-grid">
        <article>
          <span>{{ t('providers.health') }}</span>
          <strong>{{ provider.health.availability.toFixed(1) }}%</strong>
          <small>RPM {{ provider.health.rpm }} · TPM {{ provider.health.tpm }}</small>
        </article>
        <article>
          <span>{{ t('providers.latency') }}</span>
          <strong>{{ provider.health.latencyMs ?? '—' }} ms</strong>
          <small>{{ formatTime(provider.health.lastSuccess) }}</small>
        </article>
        <article>
          <span>{{ t('providers.models') }}</span>
          <strong>{{ provider.modelCount }}</strong>
          <small>{{ provider.capabilities.length }} capabilities</small>
        </article>
        <article>
          <span>Priority / Weight</span>
          <strong>{{ provider.priority }} / {{ provider.weight }}</strong>
          <small>{{ provider.timeoutSeconds }}s · {{ provider.retryCount }} retries</small>
        </article>
      </div>

      <div class="section-card">
        <h2>Connection</h2>
        <dl>
          <div><dt>Type</dt><dd>{{ provider.type }}</dd></div>
          <div><dt>Endpoint</dt><dd>{{ provider.endpoint }}</dd></div>
          <div><dt>API Key</dt><dd>{{ provider.apiKeyMasked || 'Not required' }}</dd></div>
          <div><dt>Organization</dt><dd>{{ provider.organization || '—' }}</dd></div>
          <div><dt>Proxy</dt><dd>{{ provider.proxy || '—' }}</dd></div>
          <div><dt>TLS</dt><dd>{{ provider.tlsVerify ? 'Verify' : 'Insecure' }}</dd></div>
        </dl>
      </div>

      <div class="section-card">
        <h2>Capabilities</h2>
        <div class="pill-row">
          <span v-for="capability in provider.capabilities" :key="capability" class="badge badge-success">
            {{ capability }}
          </span>
          <span v-if="provider.capabilities.length === 0" class="muted">{{ t('providers.testRequired') }}</span>
        </div>
      </div>

      <div class="section-card">
        <h2>Tags</h2>
        <div class="pill-row">
          <span v-for="tag in provider.tags" :key="tag" class="badge">{{ tag }}</span>
          <span v-if="provider.tags.length === 0" class="muted">—</span>
        </div>
      </div>

      <footer class="danger-zone">
        <button class="button button-emphasis" type="button" :disabled="busy" @click="$emit('toggle')">
          {{ provider.enabled ? t('providers.disable') : t('providers.enable') }}
        </button>
        <button class="button button-danger" type="button" :disabled="busy" @click="$emit('delete')">
          {{ t('providers.delete') }}
        </button>
      </footer>
    </div>

    <div v-else-if="tab === 'models'" class="detail-body">
      <div v-if="!provider.models?.length" class="empty-tab">{{ t('providers.noModels') }}</div>
      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr><th>Model</th><th>Capabilities</th><th>Context</th><th>Status</th></tr>
          </thead>
          <tbody>
            <tr v-for="model in provider.models" :key="model.id">
              <td><strong>{{ model.displayName }}</strong><small>{{ model.modelId }}</small></td>
              <td><span v-for="capability in model.capabilities" :key="capability" class="badge">{{ capability }}</span></td>
              <td>{{ model.contextLength ?? '—' }}</td>
              <td><span class="badge" :class="{ 'badge-success': model.status === 'ACTIVE' }">{{ model.status }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-else class="detail-body">
      <div v-if="audit.length === 0" class="empty-tab">{{ t('providers.noAudit') }}</div>
      <div v-else class="audit-list">
        <article v-for="entry in audit" :key="entry.id">
          <span class="badge" :class="{ 'badge-success': entry.result === 'SUCCESS', 'badge-danger': entry.result === 'FAILED' }">
            {{ entry.result }}
          </span>
          <div><strong>{{ entry.action }}</strong><small>{{ entry.createUser }} · {{ formatTime(entry.createTime) }}</small></div>
          <code>{{ entry.traceId }}</code>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.provider-detail {
  min-width: 0;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
  border-bottom: 1px solid var(--border);
}

.title-line,
.detail-actions,
.pill-row,
.danger-zone {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-header h1 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}

.detail-header p {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
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
.section-card {
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
dt {
  color: var(--text-secondary);
  font-size: 11px;
}

.metric-grid strong {
  font-size: 17px;
}

.section-card h2 {
  margin: 0 0 10px;
  font-size: 13px;
}

dl {
  display: grid;
  gap: 7px;
  margin: 0;
}

dl div {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 12px;
}

dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 13px;
}

.danger-zone {
  justify-content: flex-end;
  padding-top: 6px;
}

.empty-tab {
  padding: 60px 20px;
  color: var(--text-secondary);
  text-align: center;
  font-size: 13px;
}

.table-wrap {
  width: 100%;
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

th,
td {
  padding: 10px;
  border-bottom: 1px solid var(--border);
  text-align: left;
}

th {
  color: var(--text-secondary);
  font-size: 11px;
}

td small {
  display: block;
  margin-top: 3px;
  color: var(--text-secondary);
}

td .badge {
  margin: 2px;
}

.audit-list {
  display: grid;
  gap: 7px;
}

.audit-list article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
}

.audit-list div {
  display: grid;
  gap: 2px;
}

.audit-list small,
.audit-list code {
  color: var(--text-secondary);
  font-size: 11px;
}

@media (max-width: 980px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .detail-header {
    flex-direction: column;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  dl div {
    grid-template-columns: 1fr;
    gap: 3px;
  }
}
</style>
