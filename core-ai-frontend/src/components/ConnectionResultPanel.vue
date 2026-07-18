<script setup lang="ts">
import { t } from '../i18n'
import type { ConnectionResult } from '../types/provider'

defineProps<{ result: ConnectionResult }>()
defineEmits<{ close: [] }>()
</script>

<template>
  <section class="connection-panel" :class="{ success: result.success, failure: !result.success }">
    <div class="result-heading">
      <div>
        <span class="result-icon">{{ result.success ? '✓' : '!' }}</span>
        <div>
          <h3>{{ result.success ? t('connection.success') : t('connection.failure') }}</h3>
          <p>{{ result.userMessage }}</p>
        </div>
      </div>
      <button class="button button-secondary" type="button" @click="$emit('close')">
        {{ t('connection.close') }}
      </button>
    </div>
    <div class="result-stats">
      <div><span>{{ t('providers.latency') }}</span><strong>{{ result.latencyMs }} ms</strong></div>
      <div><span>{{ t('providers.models') }}</span><strong>{{ result.modelCount }}</strong></div>
      <div><span>Status</span><strong>{{ result.status }}</strong></div>
    </div>
    <div class="check-list">
      <div v-for="check in result.checks" :key="check.name" class="check-row">
        <span>{{ check.success ? '✓' : '×' }}</span>
        <strong>{{ check.name }}</strong>
        <small>{{ check.detail }}</small>
      </div>
    </div>
    <div v-if="result.capabilities.length" class="capability-row">
      <span v-for="capability in result.capabilities" :key="capability" class="badge">
        {{ capability }}
      </span>
    </div>
    <code v-if="result.errorCode" class="error-code">{{ result.errorCode }}</code>
  </section>
</template>

<style scoped>
.connection-panel {
  margin: 12px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
}

.connection-panel.success {
  border-color: rgba(36, 138, 61, 0.24);
  background: var(--success-bg);
}

.connection-panel.failure {
  border-color: rgba(215, 0, 21, 0.2);
  background: var(--danger-bg);
}

.result-heading,
.result-heading > div,
.result-stats,
.check-row,
.capability-row {
  display: flex;
  align-items: center;
}

.result-heading {
  justify-content: space-between;
  gap: 12px;
}

.result-heading > div {
  gap: 10px;
}

.result-heading h3,
.result-heading p {
  margin: 0;
}

.result-heading h3 {
  font-size: 14px;
}

.result-heading p {
  margin-top: 3px;
  color: var(--text-secondary);
  font-size: 11px;
}

.result-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: white;
  background: var(--success);
  font-weight: 800;
}

.failure .result-icon {
  background: var(--danger);
}

.result-stats {
  gap: 8px;
  margin: 12px 0;
}

.result-stats > div {
  display: grid;
  flex: 1;
  gap: 3px;
  min-width: 0;
  padding: 8px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.55);
}

.result-stats span,
.check-row small {
  color: var(--text-secondary);
  font-size: 11px;
}

.result-stats strong,
.check-row strong {
  font-size: 12px;
}

.check-list {
  display: grid;
  gap: 5px;
}

.check-row {
  gap: 8px;
  min-height: 24px;
}

.check-row small {
  margin-left: auto;
}

.capability-row {
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 10px;
}

.error-code {
  display: inline-block;
  margin-top: 10px;
  color: var(--danger);
  font-size: 11px;
}
</style>
