import axios, { type AxiosInstance } from 'axios';
import type {
  Service,
  ServiceSummary,
  ServiceCheckResponse,
  CheckResult,
  ServiceHistoryItem,
  LogFile,
  LogEntryDetail,
  LogQueryResponse,
  LogUploadResponse,
  DashboardSummary,
  StartResult,
  StopResult,
  AutoDetectData,
  ApplyConfigItem,
  ApplyDetectedData,
  ApiResponse,
} from '../types';

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

// 统一响应解包：code=0 成功，否则抛错
async function unwrap<T>(p: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const { data } = await p;
  if (data.code !== 0) {
    throw new Error(data.message || '请求失败');
  }
  return data.data;
}

export interface LogQueryParams {
  level?: string;
  keyword?: string;
  start_time?: string;
  end_time?: string;
  has_stack?: boolean;
  page?: number;
  page_size?: number;
}

export const api = {
  // —— 服务管理 ——
  getServices: () =>
    unwrap<{ services: Service[]; summary: ServiceSummary }>(http.get('/services')),
  createService: (payload: Partial<Service>) =>
    unwrap<Service>(http.post('/services', payload)),
  updateService: (id: number, payload: Partial<Service>) =>
    unwrap<Service>(http.put(`/services/${id}`, payload)),
  deleteService: (id: number) => unwrap<{ id: number }>(http.delete(`/services/${id}`)),
  checkAll: () => unwrap<ServiceCheckResponse>(http.post('/services/check')),
  checkOne: (id: number) => unwrap<CheckResult>(http.post(`/services/${id}/check`)),
  getHistory: async (id: number, limit = 50) => {
    const data = await unwrap<{ history: ServiceHistoryItem[] }>(
      http.get(`/services/${id}/history?limit=${limit}`)
    );
    return data.history;
  },

  // —— 进程管理 ——
  startService: (id: number) =>
    unwrap<StartResult>(http.post(`/services/${id}/start`)),
  stopService: (id: number) =>
    unwrap<StopResult>(http.post(`/services/${id}/stop`)),

  // —— 启动命令自动检测 ——
  autoDetectCommands: (baseDir: string) =>
    unwrap<AutoDetectData>(http.post('/services/auto-detect', { base_dir: baseDir })),
  applyDetectedConfigs: (configs: ApplyConfigItem[]) =>
    unwrap<ApplyDetectedData>(http.post('/services/apply-detected', { configs })),

  // —— 日志管理 ——
  uploadLog: (file: File, serviceName?: string) => {
    const form = new FormData();
    form.append('file', file);
    if (serviceName) form.append('serviceName', serviceName);
    return unwrap<LogUploadResponse>(
      http.post('/logs/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    );
  },
  getLogFiles: async () => {
    const data = await unwrap<{ files: LogFile[] }>(http.get('/logs/files'));
    return data.files;
  },
  getLogEntries: (fileId: number, params: LogQueryParams) =>
    unwrap<LogQueryResponse>(http.get(`/logs/files/${fileId}/entries`, { params })),
  getLogEntry: async (fileId: number, entryId: number) => {
    const data = await unwrap<{ entry: LogEntryDetail }>(
      http.get(`/logs/files/${fileId}/entries/${entryId}`)
    );
    return data.entry;
  },
  deleteLogFile: (fileId: number) =>
    unwrap<{ id: number }>(http.delete(`/logs/files/${fileId}`)),

  // —— 仪表盘 ——
  getDashboard: () => unwrap<DashboardSummary>(http.get('/dashboard/summary')),
};

export default api;
