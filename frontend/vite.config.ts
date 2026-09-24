/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  base: './',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{test,spec}.ts', 'portal/**/*.{test,spec}.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      include: ['src/**/*.{ts,vue}', 'portal/**/*.{ts,vue}'],
      exclude: [
        'src/main.ts',
        'portal/main.ts',
        'src/App.vue',
        'src/**/*.d.ts',
        'src/components/workflow/BpmnViewer.vue',
        'src/assets/**',
      ],
      thresholds: {
        lines: 80,
        statements: 75,
        functions: 70,
        branches: 60,
      },
    },
  },
})
