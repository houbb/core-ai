<script setup lang="ts">
import { ref, watch } from 'vue'
import { t } from '../i18n'
import type { AiScene, SceneExecutionResult } from '../types/scene'

const props = defineProps<{
  open: boolean
  scene: AiScene
  result?: SceneExecutionResult
  busy?: boolean
}>()

const emit = defineEmits<{
  close: []
  run: [input: string]
}>()

const input = ref('')

watch(
  () => [props.open, props.scene.id] as const,
  () => {
    if (props.open) input.value = ''
  }
)
</script>

<template>
  <div v-if="open" class="modal-backdrop" @mousedown.self="$emit('close')">
    <section class="playground" role="dialog" aria-modal="true">
      <header>
        <div>
          <h2>{{ scene.icon }} {{ scene.name }}</h2>
          <p>{{ t('scenes.playground') }} · V{{ scene.version }}</p>
        </div>
        <button class="button button-secondary" type="button" :disabled="busy" @click="$emit('close')">×</button>
      </header>
      <div class="playground-grid">
        <div class="input-panel">
          <label>{{ t('scenes.input') }}</label>
          <textarea v-model="input" class="input" :placeholder="t('scenes.inputHint')" />
          <button
            class="button button-primary"
            type="button"
            :disabled="busy || !input.trim()"
            @click="emit('run', input)"
          >
            {{ busy ? t('common.loading') : t('scenes.run') }}
          </button>
        </div>
        <div class="result-panel">
          <div v-if="!result" class="empty-result">{{ t('scenes.runHint') }}</div>
          <template v-else>
            <div class="result-heading">
              <span class="badge badge-warning">{{ result.mode }}</span>
              <span>{{ result.modelDisplayName }} · {{ result.providerName }}</span>
              <span>{{ result.latencyMs }} ms</span>
            </div>
            <div class="output-card">
              <h3>{{ t('scenes.output') }}</h3>
              <p>{{ result.output }}</p>
              <small>
                {{ result.estimatedInputTokens ?? '—' }} tokens ·
                {{ result.currency || '' }} {{ result.estimatedCost ?? '—' }}
              </small>
            </div>
            <div class="trace">
              <h3>{{ t('scenes.trace') }}</h3>
              <article v-for="step in result.trace" :key="step.order">
                <span>{{ step.order }}</span>
                <div><strong>{{ step.stage }} · {{ step.name }}</strong><small>{{ step.detail }}</small></div>
                <em>{{ step.status }}</em>
              </article>
            </div>
          </template>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(8px);
}

.playground {
  width: min(1050px, 100%);
  max-height: calc(100vh - 32px);
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: var(--surface-solid);
  box-shadow: var(--shadow);
}

header,
.result-heading,
.trace article {
  display: flex;
  align-items: center;
}

header {
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

h2,
h3,
p {
  margin: 0;
}

h2 {
  font-size: 17px;
}

header p,
small {
  color: var(--text-secondary);
  font-size: 11px;
}

.playground-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.8fr) minmax(0, 1.5fr);
  min-height: 520px;
}

.input-panel,
.result-panel {
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 16px;
}

.input-panel {
  border-right: 1px solid var(--border);
  background: rgba(245, 245, 247, 0.55);
}

.input-panel label {
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 700;
}

.input-panel textarea {
  min-height: 330px;
}

.result-heading {
  flex-wrap: wrap;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 11px;
}

.output-card,
.trace {
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 11px;
}

h3 {
  margin-bottom: 8px;
  font-size: 13px;
}

.output-card p {
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.6;
}

.trace {
  display: grid;
  gap: 7px;
}

.trace article {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  gap: 8px;
  padding: 8px;
  border-radius: 9px;
  background: var(--bg-secondary);
}

.trace article > span {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: var(--accent);
  background: var(--accent-bg);
  font-size: 10px;
  font-weight: 700;
}

.trace article div {
  display: grid;
  gap: 2px;
}

.trace strong {
  font-size: 12px;
}

.trace em {
  color: var(--text-secondary);
  font-size: 10px;
  font-style: normal;
}

.empty-result {
  display: grid;
  min-height: 450px;
  place-items: center;
  color: var(--text-secondary);
  font-size: 13px;
}

@media (max-width: 760px) {
  .playground-grid {
    grid-template-columns: 1fr;
  }

  .input-panel {
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }
}
</style>
