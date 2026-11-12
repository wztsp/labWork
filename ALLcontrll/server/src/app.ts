/**
 * Express 应用装配：中间件、路由、静态托管与全局错误处理。
 */

import fs from 'node:fs';
import path from 'node:path';
import cors from 'cors';
import express, { type Express, type NextFunction, type Request, type Response } from 'express';
import { MulterError } from 'multer';
import { MAX_UPLOAD_SIZE_BYTES, STATIC_DIR } from './config/defaults';
import { createDashboardRouter } from './routes/dashboard';
import { createLogsRouter } from './routes/logs';
import { createServicesRouter } from './routes/services';
import { ErrorCode, HttpError, nowIso, sendError, sendSuccess } from './utils/response';

/** 判断静态目录是否可用（生产模式下托管前端产物）。 */
function staticDirExists(): boolean {
  return fs.existsSync(path.join(STATIC_DIR, 'index.html'));
}

/**
 * 创建并配置 Express 应用。
 *
 * @returns 已完成中间件与路由注册的 Express 实例
 */
export function createApp(): Express {
  const app: Express = express();

  // ---- 基础中间件 ----
  app.use(cors());
  app.use(express.json({ limit: '2mb' }));
  app.use(express.urlencoded({ extended: true }));

  // ---- 健康自检 ----
  app.get('/api/health', (_req: Request, res: Response): void => {
    sendSuccess(res, { status: 'ok', time: nowIso() });
  });

  // ---- 业务路由 ----
  app.use('/api/services', createServicesRouter());
  app.use('/api/logs', createLogsRouter());
  app.use('/api/dashboard', createDashboardRouter());

  // ---- 未匹配的 API 请求 ----
  app.use('/api', (req: Request, res: Response): void => {
    sendError(res, 404, `接口不存在: ${req.method} ${req.originalUrl}`);
  });

  // ---- 前端静态资源托管（生产模式）----
  if (staticDirExists()) {
    app.use(express.static(STATIC_DIR));
    app.get('*', (_req: Request, res: Response): void => {
      res.sendFile(path.join(STATIC_DIR, 'index.html'));
    });
  } else {
    app.get('/', (_req: Request, res: Response): void => {
      res
        .status(200)
        .type('text/plain; charset=utf-8')
        .send(
          'LIS 运维后台管理系统 API 已启动。\n' +
            '开发模式请访问前端 http://localhost:8901\n' +
            '生产模式请先执行 npm run build:web 生成 web/dist。\n',
        );
    });
  }

  // ---- 全局错误处理 ----
  app.use((err: unknown, _req: Request, res: Response, _next: NextFunction): void => {
    if (err instanceof MulterError) {
      if (err.code === 'LIMIT_FILE_SIZE') {
        const limitMb: number = Math.round(MAX_UPLOAD_SIZE_BYTES / 1024 / 1024);
        sendError(res, 413, `文件过大，单文件不得超过 ${limitMb}MB`, ErrorCode.PAYLOAD_TOO_LARGE);
        return;
      }
      sendError(res, 400, `文件上传失败: ${err.message}`);
      return;
    }

    if (err instanceof HttpError) {
      sendError(res, err.status, err.message, err.code);
      return;
    }

    const message: string = err instanceof Error ? err.message : String(err);
    // eslint-disable-next-line no-console
    console.error('[error]', message, err instanceof Error ? err.stack : '');
    sendError(res, 500, `服务器内部错误: ${message}`, ErrorCode.INTERNAL_ERROR);
  });

  return app;
}
