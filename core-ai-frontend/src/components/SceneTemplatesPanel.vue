<script setup lang="ts">
import { reactive, watch } from 'vue'
import { t } from '../i18n'
import type { SceneTemplate } from '../types/scene'

const props = defineProps<{
  templates: SceneTemplate[]
  busy?: boolean
}>()

defineEmits<{
  close: []
  instantiate: [template: SceneTemplate, code: string]
  delete: [template: SceneTemplate]
}>()

const codes = reactive<Record<string, string>>({})

watch(
  () => props.templates,
  (templates) => templates.forEach((template) => {
    if (!codes[template.id]) codes[template.id] = template.defaultCode
  }),
  { immediate: true }
)
</script>

<template>
  <section class="template-panel">
    <header>
      <div><h2>{{ t('scenes.templates') }}</h2><p>{{ t('scenes.templatesHint') }}</p></div>
      <button class="button button-secondary" type="button" @click="$emit('close')">×</button>
    </header>
    <div class="template-grid">
      <article v-for="template in templates" :key="template.id">
        <span class="template-icon">{{ template.icon || '✦' }}</span>
        <div>
          <div class="title-line">
            <strong>{{ template.templateName }}</strong>
            <span v-if="template.recommended" class="badge badge-success">★</span>
            <span v-if="template.builtin" class="badge">{{ t('scenes.builtin') }}</span>
          </div>
          <p>{{ template.description }}</p>
          <small>{{ template.category }} · {{ template.configuration.models[0]?.modelAlias }}</small>
          <input
            v-model="codes[template.id]"
            class="input code-input"
            required
            pattern="[a-z0-9][a-z0-9._-]{1,99}"
            :aria-label="t('scenes.defaultCode')"
          />
        </div>
        <div class="actions">
          <button
            class="button button-primary"
            type="button"
            :disabled="busy"
            @click="$emit('instantiate', template, codes[template.id])"
          >
            {{ t('scenes.createFromTemplate') }}
          </button>
          <button
            v-if="!template.builtin"
            class="button button-danger"
            type="button"
            :disabled="busy"
            @click="$emit('delete', template)"
          >
            {{ t('providers.delete') }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.template-panel {
  margin-bottom: 12px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow);
}

header,
.title-line,
.actions {
  display: flex;
  align-items: center;
}

header {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

h2,
p {
  margin: 0;
}

h2 {
  font-size: 17px;
}

header p,
article p,
article small {
  color: var(--text-secondary);
  font-size: 11px;
}

header p {
  margin-top: 3px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}

article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 11px;
  background: var(--surface-solid);
}

.template-icon {
  font-size: 24px;
}

.title-line,
.actions {
  gap: 6px;
}

article p {
  margin: 4px 0;
}

.code-input {
  min-height: 32px;
  margin-top: 7px;
}

.actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

@media (max-width: 860px) {
  .template-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  article {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .actions {
    grid-column: 1 / -1;
  }
}
</style>
