import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        // Override with STECKER_API_URL to point at a backend on another port.
        target: process.env.STECKER_API_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
