<script setup lang="ts">
import { locale, setLocale, t } from './i18n'
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div>
        <span class="brand-mark">AI</span>
        <strong>core-ai</strong>
        <span class="topbar-subtitle">{{ t('app.subtitle') }}</span>
      </div>
      <nav class="main-nav">
        <RouterLink to="/analytics">{{ t('nav.analytics') }}</RouterLink>
        <RouterLink to="/agents">{{ t('nav.agents') }}</RouterLink>
        <RouterLink to="/knowledge">{{ t('nav.knowledge') }}</RouterLink>
        <RouterLink to="/conversations">{{ t('nav.conversations') }}</RouterLink>
        <RouterLink to="/gateway">{{ t('nav.gateway') }}</RouterLink>
        <RouterLink to="/tools">{{ t('nav.tools') }}</RouterLink>
        <RouterLink to="/prompts">{{ t('nav.prompts') }}</RouterLink>
        <RouterLink to="/scenes">{{ t('nav.scenes') }}</RouterLink>
        <RouterLink to="/providers">{{ t('nav.providers') }}</RouterLink>
        <RouterLink to="/models">{{ t('nav.models') }}</RouterLink>
      </nav>
      <div class="topbar-actions">
        <button
          class="button button-secondary language-button"
          type="button"
          @click="setLocale(locale === 'zh-CN' ? 'en-US' : 'zh-CN')"
        >
          {{ locale === 'zh-CN' ? 'EN' : '中文' }}
        </button>
      </div>
    </header>
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.main-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  margin-right: 12px;
  overflow-x: auto;
  white-space: nowrap;
}

.main-nav a {
  padding: 7px 10px;
  border-radius: 9px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
}

.main-nav a.router-link-active {
  color: var(--accent);
  background: var(--accent-bg);
}

@media (max-width: 560px) {
  .topbar {
    padding-inline: 10px;
  }

  .topbar strong,
  .topbar-subtitle {
    display: none;
  }

  .main-nav {
    margin-right: 4px;
  }
}
</style>
