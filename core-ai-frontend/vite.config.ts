import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8104',
      '/actuator': 'http://127.0.0.1:8104'
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    css: true
  }
})
