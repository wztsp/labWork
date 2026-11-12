import { defineConfig } from 'vitest/config';

/**
 * 后端单元测试配置。
 *
 * 说明：仅覆盖 `src/services/**` 等纯逻辑模块，
 * 不加载 routes / db。
 */
export default defineConfig({
  test: {
    environment: 'node',
    include: ['tests/**/*.test.ts'],
    globals: false,
    testTimeout: 10000,
    coverage: {
      provider: 'v8',
      include: ['src/services/**/*.ts'],
      reporter: ['text'],
    },
  },
});
