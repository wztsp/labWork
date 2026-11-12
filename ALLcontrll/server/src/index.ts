/**
 * 后端服务入口：加载环境变量 → 初始化数据库 → 写入种子数据 → 启动 HTTP 服务。
 */

import path from 'node:path';
import type { Server } from 'node:http';
import dotenv from 'dotenv';

// 环境变量必须在读取 config/defaults 之前加载。
dotenv.config({ path: path.resolve(__dirname, '../.env') });

/* eslint-disable import/first */
import type { Express } from 'express';
import { createApp } from './app';
import { DB_PATH, PORT } from './config/defaults';
import { closeDb } from './db/connection';
import { initDatabase } from './db/init';
import { seedDatabase } from './db/seed';
import { processManager } from './services/processManager';
/* eslint-enable import/first */

/** 启动服务。 */
function bootstrap(): void {
  initDatabase();
  const seeded: number = seedDatabase();

  // admin 重启后修复进程状态一致性：检测 DB 中 running 的 PID 是否仍存活
  processManager.reconcileOnStartup();

  const app: Express = createApp();
  const server: Server = app.listen(PORT, (): void => {
    // eslint-disable-next-line no-console
    console.log('==============================================');
    // eslint-disable-next-line no-console
    console.log('  LIS 运维后台管理系统 —— 后端服务已启动');
    // eslint-disable-next-line no-console
    console.log(`  监听地址 : http://localhost:${PORT}`);
    // eslint-disable-next-line no-console
    console.log(`  数据库   : ${DB_PATH}`);
    // eslint-disable-next-line no-console
    console.log(`  种子数据 : ${seeded > 0 ? `已写入 ${seeded} 条默认服务` : '已存在，跳过'}`);
    // eslint-disable-next-line no-console
    console.log('==============================================');
  });

  const shutdown = (signal: string): void => {
    // eslint-disable-next-line no-console
    console.log(`\n[${signal}] 正在关闭服务...`);
    server.close((): void => {
      closeDb();
      process.exit(0);
    });
    // 兜底：5 秒内未正常关闭则强制退出。
    setTimeout((): void => process.exit(0), 5000).unref();
  };

  process.on('SIGINT', (): void => shutdown('SIGINT'));
  process.on('SIGTERM', (): void => shutdown('SIGTERM'));
}

bootstrap();
