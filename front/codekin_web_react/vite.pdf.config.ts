import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'
import { cpSync } from 'node:fs'

export default defineConfig({
  plugins: [react(), tailwindcss(), {
    name: 'pdf-font-assets',
    closeBundle() { cpSync('public/fonts', 'dist/pdf-renderer/fonts', { recursive: true }) },
  }],
  define: { 'process.env.NODE_ENV': JSON.stringify('production') },
  publicDir: false,
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: {
    outDir: 'dist/pdf-renderer',
    lib: { entry: 'src/export/renderer.tsx', name: 'CodekinResumePdf', formats: ['iife'], fileName: () => 'renderer.js', cssFileName: 'renderer' },
    rollupOptions: { output: { inlineDynamicImports: true } },
  },
})
