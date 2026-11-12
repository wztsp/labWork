/**
 * 统一 API 响应封装。
 *
 * 约定：所有接口返回 `{ code, message, data }`，code=0 表示成功。
 * HTTP 状态码同时反映语义：200 / 400 / 404 / 413 / 500。
 */

import type { NextFunction, Request, RequestHandler, Response } from 'express';
import type { ApiResponse } from '../types';

/** 业务错误码枚举。 */
export const ErrorCode = {
  OK: 0,
  BAD_REQUEST: 400,
  NOT_FOUND: 404,
  PAYLOAD_TOO_LARGE: 413,
  INTERNAL_ERROR: 500,
} as const;

/** 构造成功响应体。 */
export function success<T>(data: T, message: string = 'ok'): ApiResponse<T> {
  return { code: ErrorCode.OK, message, data };
}

/** 构造错误响应体。 */
export function failure(code: number, message: string): ApiResponse<null> {
  return { code, message, data: null };
}

/** 发送成功响应（HTTP 200）。 */
export function sendSuccess<T>(res: Response, data: T, message: string = 'ok'): void {
  res.status(200).json(success(data, message));
}

/**
 * 发送错误响应。
 *
 * @param res Express 响应对象
 * @param httpStatus HTTP 状态码，同时作为默认业务错误码
 * @param message 错误描述
 * @param code 业务错误码，默认与 httpStatus 相同
 */
export function sendError(
  res: Response,
  httpStatus: number,
  message: string,
  code: number = httpStatus,
): void {
  res.status(httpStatus).json(failure(code, message));
}

/** 业务异常：携带 HTTP 状态码，由全局错误处理中间件统一转换为响应。 */
export class HttpError extends Error {
  public readonly status: number;
  public readonly code: number;

  constructor(status: number, message: string, code?: number) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.code = code ?? status;
  }
}

/** 生成 ISO 8601 格式的当前时间字符串。 */
export function nowIso(): string {
  return new Date().toISOString();
}

/**
 * 异步路由处理器包装：Express 4 不会自动捕获 async 函数抛出的异常，
 * 通过本包装统一转交给全局错误处理中间件。
 */
export function asyncHandler(
  handler: (req: Request, res: Response, next: NextFunction) => Promise<void>,
): RequestHandler {
  return (req: Request, res: Response, next: NextFunction): void => {
    handler(req, res, next).catch(next);
  };
}
