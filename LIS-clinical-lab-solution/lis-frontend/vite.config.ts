import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import AutoImport from 'unplugin-auto-import/vite'

export default defineConfig({
  plugins: [
    vue(),
    // 代码中大量 .vue 文件依赖自动导入 Vue / vue-router API (computed/ref/useRouter 等)
    AutoImport({ imports: ['vue', 'vue-router'] })
  ],
  resolve: { alias: { '@': resolve(__dirname, 'src') } },
  server: {
    port: 3000,
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-vendor': ['element-plus'],
          'echarts-vendor': ['echarts', 'vue-echarts']
        }
      }
    }
  }
})