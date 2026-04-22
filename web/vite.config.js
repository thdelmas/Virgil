import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Served at https://theophile.world/apps/virgil
export default defineConfig({
  plugins: [react()],
  base: '/apps/virgil/',
  build: {
    outDir: 'dist/apps/virgil',
    emptyOutDir: true,
    sourcemap: false,
  },
})
