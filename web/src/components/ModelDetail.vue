<script setup lang="ts">
import { ref } from 'vue'
import { t } from '../i18n'
import type { AiModel } from '../types/model'

defineProps<{
  model: AiModel
  audit: Array<{ id: string; action: string; result: string; createTime: string; createUser: string; traceId?: string }>
  busy?: boolean
}>()

defineEmits<{
  edit: []
  transition: [status: 'REGISTERED' | 'ENABLED' | 'DEPRECATED' | 'DISABLED']
  flags: [favorite: boolean, recommended: boolean]
  delete: []
}>()

const tab = ref<'overview' | 'pricing' | 'aliases' | 'audit'>('overview')

function time(value?: string) {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
}

function price(value?: number) {
  return value === undefined || value === null ? '—' : String(value)
}
</script>

<template>
  <section class="model-detail">
    <header class="detail-header">
      <div>
        <div class="title-line">
          <h1>{{ model.displayName }}</h1>
          <span class="badge" :class="{ 'badge-success': model.status === 'ENABLED', 'badge-warning': model.status === 'DISCOVERED' || model.status === 'REGISTERED' }">{{ model.status }}</span>
          <span v-if="model.recommended" class="badge badge-success">★ {{ t('models.recommended') }}</span>
          <span v-if="model.favorite" class="badge">♥ {{ t('models.favorite') }}</span>
        </div>
        <p>{{ model.providerName }} · {{ model.category }}</p>
      </div>
      <div class="detail-actions">
        <button class="button button-secondary" type="button" :disabled="busy" @click="$emit('flags', !model.favorite, model.recommended)">♥</button>
        <button class="button button-emphasis" type="button" :disabled="busy" @click="$emit('flags', model.favorite, !model.recommended)">★</button>
        <button class="button button-primary" type="button" :disabled="busy" @click="$emit('edit')">{{ t('providers.edit') }}</button>
      </div>
    </header>

    <div class="lifecycle-actions">
      <button v-if="model.status === 'DISCOVERED'" class="button button-emphasis" type="button" :disabled="busy" @click="$emit('transition', 'REGISTERED')">{{ t('models.register') }}</button>
      <button v-if="['REGISTERED', 'DISABLED', 'DEPRECATED'].includes(model.status)" class="button button-primary" type="button" :disabled="busy" @click="$emit('transition', 'ENABLED')">{{ t('models.enable') }}</button>
      <button v-if="model.status === 'ENABLED'" class="button button-emphasis" type="button" :disabled="busy" @click="$emit('transition', 'DEPRECATED')">{{ t('models.deprecate') }}</button>
      <button v-if="['REGISTERED', 'ENABLED', 'DEPRECATED'].includes(model.status)" class="button button-secondary" type="button" :disabled="busy" @click="$emit('transition', 'DISABLED')">{{ t('models.disable') }}</button>
    </div>

    <nav class="tabs">
      <button :class="{ active: tab === 'overview' }" type="button" @click="tab = 'overview'">{{ t('providers.overview') }}</button>
      <button :class="{ active: tab === 'pricing' }" type="button" @click="tab = 'pricing'">{{ t('models.pricing') }}</button>
      <button :class="{ active: tab === 'aliases' }" type="button" @click="tab = 'aliases'">{{ t('models.aliases') }}</button>
      <button :class="{ active: tab === 'audit' }" type="button" @click="tab = 'audit'">{{ t('providers.audit') }}</button>
    </nav>

    <div v-if="tab === 'overview'" class="detail-body">
      <div class="metric-grid">
        <article><span>{{ t('models.context') }}</span><strong>{{ model.maxContextTokens ?? '—' }}</strong><small>{{ t('models.input') }} {{ model.maxInputTokens ?? '—' }} · {{ t('models.output') }} {{ model.maxOutputTokens ?? '—' }}</small></article>
        <article><span>{{ t('providers.latency') }}</span><strong>{{ model.providerLatencyMs ?? '—' }} ms</strong><small>{{ model.providerName }}</small></article>
        <article><span>{{ t('models.pricing') }}</span><strong>{{ model.currentPricing?.currency ?? '—' }}</strong><small>{{ t('models.prompt') }} {{ price(model.currentPricing?.promptPrice) }} · {{ t('models.completion') }} {{ price(model.currentPricing?.completionPrice) }}</small></article>
        <article><span>{{ t('models.available') }}</span><strong>{{ model.availableFromProvider && model.providerEnabled ? t('common.yes') : t('common.no') }}</strong><small>{{ time(model.lastDiscoveredAt) }}</small></article>
      </div>

      <div class="section-card">
        <h2>{{ t('models.basic') }}</h2>
        <dl>
          <div><dt>{{ t('models.remoteId') }}</dt><dd><code>{{ model.remoteModelId }}</code></dd></div>
          <div><dt>{{ t('models.provider') }}</dt><dd>{{ model.providerName }} ({{ model.providerCode }})</dd></div>
          <div><dt>{{ t('models.description') }}</dt><dd>{{ model.description || '—' }}</dd></div>
          <div><dt>{{ t('models.defaultMaxTokens') }}</dt><dd>{{ model.defaultMaxTokens ?? '—' }}</dd></div>
        </dl>
      </div>

      <div class="section-card">
        <h2>{{ t('models.capabilities') }}</h2>
        <div class="pill-row">
          <span v-for="capability in model.capabilities" :key="capability" class="badge badge-success">{{ capability }}</span>
        </div>
      </div>

      <div class="section-card">
        <h2>{{ t('models.parameters') }}</h2>
        <dl>
          <div><dt>{{ t('models.temperature') }}</dt><dd>{{ model.parameters.temperature ?? '—' }}</dd></div>
          <div><dt>{{ t('models.topP') }}</dt><dd>{{ model.parameters.topP ?? '—' }}</dd></div>
          <div><dt>{{ t('models.reasoningEffort') }}</dt><dd>{{ model.parameters.reasoningEffort ?? '—' }}</dd></div>
          <div><dt>{{ t('models.seed') }}</dt><dd>{{ model.parameters.seed ?? '—' }}</dd></div>
        </dl>
      </div>

      <div class="section-card">
        <h2>{{ t('models.tags') }}</h2>
        <div class="pill-row"><span v-for="tag in model.tags" :key="tag" class="badge">{{ tag }}</span><span v-if="!model.tags.length">—</span></div>
      </div>

      <footer class="danger-zone">
        <button class="button button-danger" type="button" :disabled="busy || model.status === 'ENABLED'" @click="$emit('delete')">{{ t('providers.delete') }}</button>
      </footer>
    </div>

    <div v-else-if="tab === 'pricing'" class="detail-body">
      <div v-if="!model.pricingHistory.length" class="empty-tab">{{ t('models.noPricing') }}</div>
      <div v-else class="price-list">
        <article v-for="item in model.pricingHistory" :key="item.id">
          <div><strong>{{ item.currency }}</strong><small>{{ time(item.effectiveTime) }} · {{ item.source }}</small></div>
          <span>{{ t('models.prompt') }} {{ price(item.promptPrice) }}</span>
          <span>{{ t('models.completion') }} {{ price(item.completionPrice) }}</span>
          <span>{{ t('models.cache') }} {{ price(item.cacheReadPrice) }} / {{ price(item.cacheWritePrice) }}</span>
        </article>
      </div>
    </div>

    <div v-else-if="tab === 'aliases'" class="detail-body">
      <div v-if="!model.aliases.length" class="empty-tab">{{ t('models.noAliases') }}</div>
      <div v-else class="alias-list">
        <article v-for="alias in model.aliases" :key="alias.id">
          <strong>{{ alias.alias }}</strong><span class="badge">{{ alias.priority }}</span><span>{{ alias.scene || '—' }}</span><span class="badge" :class="{ 'badge-success': alias.enabled }">{{ alias.enabled ? t('models.enabled') : t('models.disabled') }}</span>
        </article>
      </div>
    </div>

    <div v-else class="detail-body">
      <div v-if="!audit.length" class="empty-tab">{{ t('providers.noAudit') }}</div>
      <div v-else class="audit-list">
        <article v-for="entry in audit" :key="entry.id"><span class="badge badge-success">{{ entry.result }}</span><div><strong>{{ entry.action }}</strong><small>{{ entry.createUser }} · {{ time(entry.createTime) }}</small></div><code>{{ entry.traceId }}</code></article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.model-detail {
  min-width: 0;
}

.detail-header,
.title-line,
.detail-actions,
.lifecycle-actions,
.pill-row,
.danger-zone,
.alias-list article,
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

.title-line,
.detail-actions,
.lifecycle-actions,
.pill-row,
.danger-zone {
  flex-wrap: wrap;
  gap: 8px;
}

h1 {
  margin: 0;
  font-size: 17px;
}

.detail-header p {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.lifecycle-actions {
  padding: 8px 14px;
  border-bottom: 1px solid var(--border);
  background: rgba(245, 245, 247, 0.55);
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
.price-list article,
.alias-list article,
.audit-list article {
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
.price-list small,
.audit-list small {
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
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 12px;
}

dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 13px;
}

.danger-zone {
  justify-content: flex-end;
}

.empty-tab {
  padding: 50px 20px;
  color: var(--text-secondary);
  text-align: center;
  font-size: 13px;
}

.price-list,
.alias-list,
.audit-list {
  display: grid;
  gap: 7px;
}

.price-list article {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) repeat(3, auto);
  align-items: center;
  gap: 12px;
  font-size: 12px;
}

.price-list div,
.audit-list div {
  display: grid;
  gap: 2px;
}

.alias-list article {
  gap: 10px;
  font-size: 12px;
}

.audit-list article {
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
}

.audit-list code {
  color: var(--text-secondary);
  font-size: 10px;
}

@media (max-width: 980px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .detail-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  dl div {
    grid-template-columns: 1fr;
  }

  .price-list article {
    grid-template-columns: 1fr;
  }
}
</style>
