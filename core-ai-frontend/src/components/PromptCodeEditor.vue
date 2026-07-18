<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { t } from '../i18n'

const props = withDefaults(defineProps<{
  modelValue: string
  label: string
  variables?: string[]
  readonly?: boolean
}>(), {
  variables: () => [],
  readonly: false
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const textarea = ref<HTMLTextAreaElement>()
const cursor = ref(0)

const lines = computed(() => Math.max(1, props.modelValue.split(/\r?\n/).length))
const showSuggestions = computed(() => {
  const before = props.modelValue.slice(0, cursor.value)
  return !props.readonly && props.variables.length > 0 && /\{\{[A-Za-z0-9_]*$/.test(before)
})

function update(event: Event) {
  const target = event.target as HTMLTextAreaElement
  cursor.value = target.selectionStart
  emit('update:modelValue', target.value)
}

function trackCursor(event: Event) {
  cursor.value = (event.target as HTMLTextAreaElement).selectionStart
}

async function insertVariable(name: string) {
  const start = cursor.value
  const before = props.modelValue.slice(0, start)
  const match = before.match(/\{\{[A-Za-z0-9_]*$/)
  const replaceStart = match ? start - match[0].length : start
  const value = `${props.modelValue.slice(0, replaceStart)}{{${name}}}${props.modelValue.slice(start)}`
  emit('update:modelValue', value)
  await nextTick()
  const next = replaceStart + name.length + 4
  textarea.value?.focus()
  textarea.value?.setSelectionRange(next, next)
  cursor.value = next
}

async function copy() {
  await navigator.clipboard?.writeText(props.modelValue)
}
</script>

<template>
  <section class="code-editor" data-testid="prompt-code-editor">
    <header>
      <span>{{ label }}</span>
      <span class="meta">{{ lines }} {{ t('prompts.lines') }}</span>
      <button class="copy" type="button" @click="copy">{{ t('prompts.copy') }}</button>
    </header>
    <div class="editor-body">
      <div class="gutter" aria-hidden="true">
        <span v-for="line in lines" :key="line">{{ line }}</span>
      </div>
      <textarea
        ref="textarea"
        :value="modelValue"
        :readonly="readonly"
        spellcheck="false"
        @input="update"
        @click="trackCursor"
        @keyup="trackCursor"
      />
    </div>
    <div v-if="showSuggestions" class="suggestions" data-testid="variable-suggestions">
      <button
        v-for="variable in variables"
        :key="variable"
        type="button"
        @click="insertVariable(variable)"
      >
        <code>{{ variable }}</code>
      </button>
    </div>
  </section>
</template>

<style scoped>
.code-editor {
  position: relative;
  overflow: hidden;
  border: 1px solid #2c2c2e;
  border-radius: 11px;
  background: #1c1c1e;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.14);
}

header {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
  padding: 6px 10px;
  color: #f2f2f7;
  border-bottom: 1px solid #3a3a3c;
  background: #2c2c2e;
  font-size: 11px;
  font-weight: 700;
}

.meta {
  margin-left: auto;
  color: #98989d;
  font-weight: 500;
}

.copy {
  padding: 3px 7px;
  border: 0;
  border-radius: 6px;
  color: #64d2ff;
  background: rgba(100, 210, 255, 0.1);
  font-size: 10px;
}

.editor-body {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  min-height: 150px;
  max-height: 340px;
  overflow: auto;
}

.gutter {
  display: grid;
  align-content: start;
  padding: 10px 8px;
  color: #636366;
  border-right: 1px solid #2c2c2e;
  font: 12px/1.55 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  text-align: right;
  user-select: none;
}

textarea {
  width: 100%;
  min-height: 150px;
  padding: 10px 12px;
  border: 0;
  outline: 0;
  resize: vertical;
  color: #f2f2f7;
  background: transparent;
  font: 13px/1.55 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  tab-size: 2;
}

.suggestions {
  position: absolute;
  z-index: 3;
  right: 12px;
  bottom: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  max-width: calc(100% - 66px);
  padding: 7px;
  border: 1px solid #48484a;
  border-radius: 9px;
  background: rgba(44, 44, 46, 0.96);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.3);
}

.suggestions button {
  padding: 4px 7px;
  border: 0;
  border-radius: 6px;
  color: #64d2ff;
  background: #3a3a3c;
}
</style>
