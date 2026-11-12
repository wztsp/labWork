import { defineConfig } from 'vitest/config';

/**
 * 前端单元测试配置。
 *
 * 当前被测目标 `src/utils/mergeCheckResults.ts` 为纯函数，
 * 不依赖 DOM，因此使用 node 环境即可（无需 jsdom）。
 */
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'tests/**/*.test.ts'],
    globals: false,
    coverage: {
      provider: 'v8',
      include: ['src/utils/**/*.ts'],
      reporter: ['text'],
    },
  },
});
