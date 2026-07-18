<script setup lang="ts">
import { reactive } from 'vue'
import { t } from '../i18n'
import type { PromptTestCase, PromptTestSuite } from '../types/prompt'

defineProps<{
  testCases: PromptTestCase[]
  suite?: PromptTestSuite
  busy: boolean
  canEdit: boolean
  canRun: boolean
}>()

const emit = defineEmits<{
  create: [data: { name: string; inputJson: string; expectedOutput?: string; enabled: boolean }]
  delete: [id: string]
  run: []
}>()

const form = reactive({
  name: '',
  inputJson: '{}',
  expectedOutput: '',
  enabled: true
})

function create() {
  if (!form.name.trim()) return
  emit('create', { ...form })
  form.name = ''
  form.inputJson = '{}'
  form.expectedOutput = ''
}
</script>

<template>
  <section class="tests-panel">
    <header>
      <div><h3>{{ t('prompts.testCases') }}</h3><p>{{ t('prompts.testHint') }}</p></div>
      <button class="button button-primary" type="button" :disabled="busy || !canRun" @click="$emit('run')">
        {{ t('prompts.runTests') }}
      </button>
    </header>
    <div v-if="canEdit" class="test-form">
      <input v-model="form.name" class="input" :placeholder="t('prompts.testName')" />
      <textarea v-model="form.inputJson" class="input code" :placeholder="t('prompts.variablesJson')" />
      <textarea v-model="form.expectedOutput" class="input code" :placeholder="t('prompts.expectedRender')" />
      <label><input v-model="form.enabled" type="checkbox" />{{ t('prompts.enabled') }}</label>
      <button class="button button-emphasis" type="button" :disabled="busy" @click="create">
        {{ t('prompts.addTest') }}
      </button>
    </div>
    <div class="test-list">
      <article v-for="testCase in testCases" :key="testCase.id">
        <div>
          <strong>{{ testCase.name }}</strong>
          <span class="badge" :class="{
            'badge-success': testCase.lastPassed === true,
            'badge-danger': testCase.lastPassed === false
          }">
            {{ testCase.lastPassed === undefined || testCase.lastPassed === null
              ? t('prompts.notRun')
              : testCase.lastPassed ? 'PASS' : 'FAIL' }}
          </span>
        </div>
        <pre>{{ testCase.inputJson }}</pre>
        <button v-if="canEdit" class="button button-danger" type="button" @click="$emit('delete', testCase.id)">
          {{ t('prompts.delete') }}
        </button>
      </article>
      <div v-if="!testCases.length" class="empty">{{ t('prompts.noTests') }}</div>
    </div>
    <div v-if="suite" class="suite" :class="{ passed: suite.passed, failed: !suite.passed }">
      <strong>{{ suite.passed ? t('prompts.testsPassed') : t('prompts.testsFailed') }}</strong>
      <span>{{ suite.mode }} · executed={{ suite.executed }}</span>
    </div>
  </section>
</template>

<style scoped>
.tests-panel,
.test-list {
  display: grid;
  gap: 10px;
}

header,
.test-form,
article > div,
.suite {
  display: flex;
  align-items: center;
}

header {
  justify-content: space-between;
  gap: 12px;
}

h3,
p {
  margin: 0;
}

h3 {
  font-size: 15px;
}

p,
.empty,
.suite span {
  color: var(--text-secondary);
  font-size: 11px;
}

.test-form {
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px;
  border-radius: 10px;
  background: var(--bg-secondary);
}

.test-form .input {
  width: auto;
  min-width: 180px;
  flex: 1;
}

.test-form label {
  display: flex;
  gap: 5px;
  padding: 9px 4px;
  font-size: 11px;
}

.code,
pre {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

article {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
}

article > div {
  justify-content: space-between;
}

pre {
  max-height: 120px;
  margin: 0;
  padding: 8px;
  overflow: auto;
  border-radius: 8px;
  background: var(--bg-secondary);
  font-size: 11px;
}

article .button {
  justify-self: end;
}

.suite {
  justify-content: space-between;
  padding: 9px 10px;
  border-radius: 10px;
}

.suite.passed {
  color: var(--success);
  background: var(--success-bg);
}

.suite.failed {
  color: var(--danger);
  background: var(--danger-bg);
}
</style>
