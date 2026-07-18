<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { runtimeRequest } from '../api/runtime'
import { t } from '../i18n'

const dashboard = ref<Record<string, unknown>>({})
const insight = ref('')
const busy = ref(false)
const error = ref('')

onMounted(load)

async function load() {
  busy.value = true
  error.value = ''
  try {
    dashboard.value = await runtimeRequest('/api/v1/ai/admin/analytics/dashboard')
    const value = await runtimeRequest<{ insight: string }>('/api/v1/ai/admin/analytics/insight')
    insight.value = value.insight
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t('common.failed')
  } finally {
    busy.value = false
  }
}

function metric(key: string) {
  return String(dashboard.value[key] ?? 0)
}
</script>

<template>
  <section class="analytics-page">
    <header class="page-header">
      <div>
        <h1>{{ t('analytics.title') }}</h1>
        <p>{{ t('analytics.subtitle') }}</p>
      </div>
      <button class="button button-emphasis" type="button" :disabled="busy" @click="load">
        {{ t('runtime.refresh') }}
      </button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="metric-grid">
      <article><span>{{ t('analytics.requests') }}</span><strong>{{ metric('requestCount') }}</strong></article>
      <article><span>{{ t('analytics.success') }}</span><strong>{{ metric('successCount') }}</strong></article>
      <article><span>{{ t('analytics.latency') }}</span><strong>{{ metric('avgLatencyMs') }} ms</strong></article>
      <article><span>{{ t('analytics.cost') }}</span><strong>{{ metric('totalCost') }}</strong></article>
      <article><span>{{ t('analytics.tokens') }}</span><strong>{{ Number(dashboard.inputTokens || 0) + Number(dashboard.outputTokens || 0) }}</strong></article>
      <article><span>{{ t('analytics.quality') }}</span><strong>{{ metric('averageQuality') }}</strong></article>
    </div>

    <section class="surface insight">
      <header><h2>{{ t('analytics.insight') }}</h2><span class="badge">DETERMINISTIC</span></header>
      <p>{{ insight || t('common.loading') }}</p>
    </section>

    <div class="analytics-grid">
      <section class="surface">
        <h2>{{ t('analytics.rankings') }}</h2>
        <pre>{{ JSON.stringify(dashboard.rankings || [], null, 2) }}</pre>
      </section>
      <section class="surface">
        <h2>{{ t('analytics.governance') }}</h2>
        <pre>{{ JSON.stringify({ budgets: dashboard.budgets || [], alerts: dashboard.alerts || [] }, null, 2) }}</pre>
      </section>
    </div>
  </section>
</template>

<style scoped>
.analytics-page {
  width: 100%;
}

.page-header,
.surface header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  font-size: 24px;
}

h2 {
  font-size: 17px;
}

.page-header p {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 13px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(120px, 1fr));
  gap: 10px;
  margin: 16px 0;
}

.metric-grid article,
.surface {
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

.metric-grid article {
  display: grid;
  gap: 8px;
}

.metric-grid span {
  color: var(--text-secondary);
  font-size: 11px;
}

.metric-grid strong {
  font-size: 20px;
}

.insight p {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.7;
}

.analytics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 12px;
}

pre {
  max-height: 420px;
  margin: 12px 0 0;
  padding: 12px;
  overflow: auto;
  border-radius: 10px;
  color: #d7e3ff;
  background: #172033;
  font: 12px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace;
}

.error {
  padding: 10px 12px;
  border-radius: 10px;
  color: var(--danger);
  background: var(--danger-bg);
}

@media (max-width: 1000px) {
  .metric-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 680px) {
  .metric-grid,
  .analytics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
