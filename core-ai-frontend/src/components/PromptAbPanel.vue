<script setup lang="ts">
import { reactive, ref } from 'vue'
import { t } from '../i18n'
import type { PromptAbTest, PromptVersion } from '../types/prompt'

defineProps<{
  tests: PromptAbTest[]
  versions: PromptVersion[]
  busy: boolean
}>()

const emit = defineEmits<{
  create: [data: { name: string; versionA: number; versionB: number; trafficRatio: number }]
  assign: [id: string, subject: string]
}>()

const form = reactive({ name: '', versionA: 1, versionB: 1, trafficRatio: 50 })
const subject = ref('demo-user')

function create() {
  if (!form.name || form.versionA === form.versionB) return
  emit('create', { ...form })
}
</script>

<template>
  <section class="ab-panel">
    <header><h3>{{ t('prompts.abTests') }}</h3><p>{{ t('prompts.abHint') }}</p></header>
    <div class="ab-form">
      <input v-model="form.name" class="input" :placeholder="t('prompts.abName')" />
      <select v-model="form.versionA" class="input"><option v-for="item in versions.filter(v => v.publishedTime)" :key="`a${item.version}`" :value="item.version">A · V{{ item.version }}</option></select>
      <select v-model="form.versionB" class="input"><option v-for="item in versions.filter(v => v.publishedTime)" :key="`b${item.version}`" :value="item.version">B · V{{ item.version }}</option></select>
      <input v-model.number="form.trafficRatio" class="input" type="number" min="1" max="99" />
      <button class="button button-emphasis" type="button" :disabled="busy" @click="create">{{ t('prompts.createAb') }}</button>
    </div>
    <article v-for="test in tests" :key="test.id">
      <div>
        <strong>{{ test.name }}</strong>
        <span class="badge">A {{ test.trafficRatio }}% / B {{ 100 - test.trafficRatio }}%</span>
      </div>
      <div class="metrics">
        <span>A: {{ test.successA }}/{{ test.sampleA }} · {{ test.averageLatencyA.toFixed(0) }}ms</span>
        <span>B: {{ test.successB }}/{{ test.sampleB }} · {{ test.averageLatencyB.toFixed(0) }}ms</span>
      </div>
      <div class="assign">
        <input v-model="subject" class="input" />
        <button class="button button-secondary" type="button" @click="$emit('assign', test.id, subject)">{{ t('prompts.assign') }}</button>
      </div>
    </article>
    <div v-if="!tests.length" class="empty">{{ t('prompts.noAbTests') }}</div>
  </section>
</template>

<style scoped>
.ab-panel,
article {
  display: grid;
  gap: 10px;
}

header h3,
header p {
  margin: 0;
}

header h3 {
  font-size: 15px;
}

header p,
.empty,
.metrics {
  color: var(--text-secondary);
  font-size: 11px;
}

.ab-form,
article > div,
.assign {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.ab-form {
  padding: 10px;
  border-radius: 10px;
  background: var(--bg-secondary);
}

.ab-form .input {
  width: auto;
  min-width: 110px;
  flex: 1;
}

article {
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
}

article > div:first-child {
  justify-content: space-between;
}

.assign .input {
  max-width: 260px;
}
</style>
