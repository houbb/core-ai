import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import ModelsPage from './pages/ModelsPage.vue'
import ProvidersPage from './pages/ProvidersPage.vue'
import ScenesPage from './pages/ScenesPage.vue'
import PromptsPage from './pages/PromptsPage.vue'
import ToolsPage from './pages/ToolsPage.vue'
import GatewayPage from './pages/GatewayPage.vue'
import ConversationsPage from './pages/ConversationsPage.vue'
import KnowledgePage from './pages/KnowledgePage.vue'
import AgentsPage from './pages/AgentsPage.vue'
import AnalyticsPage from './pages/AnalyticsPage.vue'
import './styles/tokens.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/analytics' },
    { path: '/analytics', component: AnalyticsPage },
    { path: '/agents', component: AgentsPage },
    { path: '/knowledge', component: KnowledgePage },
    { path: '/conversations', component: ConversationsPage },
    { path: '/gateway', component: GatewayPage },
    { path: '/tools', component: ToolsPage },
    { path: '/prompts', component: PromptsPage },
    { path: '/scenes', component: ScenesPage },
    { path: '/providers', component: ProvidersPage },
    { path: '/models', component: ModelsPage }
  ]
})

createApp(App).use(router).mount('#app')
