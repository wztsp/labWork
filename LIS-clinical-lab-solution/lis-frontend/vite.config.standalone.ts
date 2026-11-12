import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import { resolve } from 'path'

/**
 * 试剂管理独立版构建配置
 * 与主前端共用源码与依赖，仅替换入口、路由与用户上下文。
 *
 * 用法：npm run build:standalone
 */
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ imports: ['vue', 'vue-router'] })
  ],
  resolve: {
    alias: [
      // 独立版用户上下文（不接入 LIS 统一认证，需在 @ 别名之前声明）
      { find: /^@\/stores\/user$/, replacement: resolve(__dirname, 'src/standalone/stores/user.ts') },
      { find: '@', replacement: resolve(__dirname, 'src') }
    ]
  },
  build: {
    outDir: 'dist-standalone',
    emptyOutDir: true,
    rollupOptions: {
      input: resolve(__dirname, 'index.standalone.html'),
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-vendor': ['element-plus']
        }
      }
    }
  }
})
