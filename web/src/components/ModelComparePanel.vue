<script setup lang="ts">
import { t } from '../i18n'
import type { AiModel } from '../types/model'

defineProps<{ models: AiModel[] }>()
defineEmits<{ close: [] }>()

function price(model: AiModel) {
  const value = (model.currentPricing?.promptPrice ?? 0) + (model.currentPricing?.completionPrice ?? 0)
  return model.currentPricing ? `${model.currentPricing.currency} ${value}` : '—'
}
</script>

<template>
  <section class="compare-panel">
    <header>
      <div><h2>{{ t('models.compare') }}</h2><p>{{ models.length }} {{ t('models.modelCount') }}</p></div>
      <button class="button button-secondary" type="button" @click="$emit('close')">×</button>
    </header>
    <div class="compare-scroll">
      <table>
        <thead><tr><th>{{ t('models.metric') }}</th><th v-for="model in models" :key="model.id">{{ model.displayName }}</th></tr></thead>
        <tbody>
          <tr><td>{{ t('models.provider') }}</td><td v-for="model in models" :key="model.id">{{ model.providerName }}</td></tr>
          <tr><td>{{ t('models.status') }}</td><td v-for="model in models" :key="model.id">{{ model.status }}</td></tr>
          <tr><td>{{ t('models.context') }}</td><td v-for="model in models" :key="model.id">{{ model.maxContextTokens ?? '—' }}</td></tr>
          <tr><td>{{ t('models.output') }}</td><td v-for="model in models" :key="model.id">{{ model.maxOutputTokens ?? '—' }}</td></tr>
          <tr><td>{{ t('models.pricePerMillion') }}</td><td v-for="model in models" :key="model.id">{{ price(model) }}</td></tr>
          <tr><td>{{ t('providers.latency') }}</td><td v-for="model in models" :key="model.id">{{ model.providerLatencyMs ?? '—' }} ms</td></tr>
          <tr>
            <td>{{ t('models.capabilities') }}</td>
            <td v-for="model in models" :key="model.id">
              <span v-for="capability in model.capabilities" :key="capability" class="badge">{{ capability }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.compare-panel {
  margin-bottom: 12px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

h2,
p {
  margin: 0;
}

h2 {
  font-size: 17px;
}

p {
  color: var(--text-secondary);
  font-size: 11px;
}

.compare-scroll {
  overflow: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

th,
td {
  min-width: 150px;
  padding: 9px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  vertical-align: top;
}

th:first-child,
td:first-child {
  min-width: 110px;
  color: var(--text-secondary);
  font-size: 11px;
}

.badge {
  margin: 2px;
}
</style>
