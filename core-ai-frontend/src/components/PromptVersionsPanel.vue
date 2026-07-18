<script setup lang="ts">
import { ref } from 'vue'
import { t } from '../i18n'
import type { PromptDiffLine, PromptVersion } from '../types/prompt'

const props = defineProps<{
  versions: PromptVersion[]
  diff: PromptDiffLine[]
  busy: boolean
}>()

const emit = defineEmits<{
  compare: [left: number, right: number]
  rollback: [version: number]
}>()

const left = ref<number>()
const right = ref<number>()

function compare() {
  if (left.value && right.value && left.value !== right.value) emit('compare', left.value, right.value)
}
</script>

<template>
  <section class="versions-panel">
    <header>
      <div><h3>{{ t('prompts.versions') }}</h3><p>{{ t('prompts.versionHint') }}</p></div>
      <div class="compare-controls">
        <select v-model="left" class="input"><option :value="undefined">V—</option><option v-for="item in versions" :key="`l${item.version}`" :value="item.version">V{{ item.version }}</option></select>
        <span>vs</span>
        <select v-model="right" class="input"><option :value="undefined">V—</option><option v-for="item in versions" :key="`r${item.version}`" :value="item.version">V{{ item.version }}</option></select>
        <button class="button button-emphasis" type="button" :disabled="busy" @click="compare">{{ t('prompts.compare') }}</button>
      </div>
    </header>
    <div class="version-list">
      <article v-for="item in versions" :key="item.id">
        <div>
          <strong>V{{ item.version }}</strong>
          <span v-if="item.publishedTime" class="badge badge-success">{{ t('prompts.published') }}</span>
          <span v-if="item.testsPassed" class="badge badge-success">TESTED</span>
        </div>
        <p>{{ item.changeLog || t('prompts.noChangeLog') }}</p>
        <small>{{ item.createUser }} · {{ new Date(item.createTime).toLocaleString() }}</small>
        <button class="button button-secondary" type="button" :disabled="busy" @click="$emit('rollback', item.version)">{{ t('prompts.rollback') }}</button>
      </article>
    </div>
    <div v-if="diff.length" class="diff-view">
      <div v-for="(line, index) in diff" :key="index" :class="line.type.toLowerCase()">
        <span>{{ line.section }}</span>
        <code>{{ line.type === 'ADDED' ? '+' : line.type === 'REMOVED' ? '-' : ' ' }} {{ line.text }}</code>
      </div>
    </div>
  </section>
</template>

<style scoped>
.versions-panel,
.version-list,
.diff-view {
  display: grid;
  gap: 10px;
}

header,
.compare-controls,
article > div {
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

header p,
article p,
article small {
  color: var(--text-secondary);
  font-size: 11px;
}

.compare-controls {
  gap: 6px;
}

.compare-controls .input {
  width: 82px;
}

.version-list {
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
}

article {
  display: grid;
  gap: 7px;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: 10px;
}

article > div {
  flex-wrap: wrap;
  gap: 5px;
}

article .button {
  justify-self: start;
}

.diff-view {
  max-height: 420px;
  overflow: auto;
  border-radius: 10px;
  background: #1c1c1e;
}

.diff-view div {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr);
  padding: 3px 8px;
  color: #d1d1d6;
  font: 11px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace;
}

.diff-view span {
  color: #8e8e93;
}

.diff-view .added {
  color: #30d158;
  background: rgba(48, 209, 88, 0.08);
}

.diff-view .removed {
  color: #ff453a;
  background: rgba(255, 69, 58, 0.08);
}
</style>
