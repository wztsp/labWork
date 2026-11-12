import { create } from 'zustand';
import type {
  Service,
  ServiceSummary,
  LogFile,
  LogEntry,
  DashboardSummary,
} from '../types';

interface AppState {
  // 服务
  services: Service[];
  summary: ServiceSummary | null;
  checking: boolean;

  // 自动刷新
  autoRefreshEnabled: boolean;
  autoRefreshInterval: number; // 秒

  // 日志
  logFiles: LogFile[];
  currentFileId: number | null;
  logEntries: LogEntry[];
  logTotal: number;
  logLevelCounts: Record<string, number>;

  // 仪表盘
  dashboard: DashboardSummary | null;

  // actions
  setServices: (services: Service[], summary: ServiceSummary) => void;
  setChecking: (v: boolean) => void;
  setAutoRefresh: (enabled: boolean, interval?: number) => void;
  setLogFiles: (files: LogFile[]) => void;
  setCurrentFile: (id: number | null) => void;
  setLogEntries: (
    entries: LogEntry[],
    total: number,
    levelCounts: Record<string, number>
  ) => void;
  setDashboard: (d: DashboardSummary) => void;
}

export const useAppStore = create<AppState>((set) => ({
  services: [],
  summary: null,
  checking: false,
  autoRefreshEnabled: false,
  autoRefreshInterval: 30,
  logFiles: [],
  currentFileId: null,
  logEntries: [],
  logTotal: 0,
  logLevelCounts: {},
  dashboard: null,

  setServices: (services, summary) => set({ services, summary }),
  setChecking: (v) => set({ checking: v }),
  setAutoRefresh: (enabled, interval) =>
    set((s) => ({
      autoRefreshEnabled: enabled,
      autoRefreshInterval: interval ?? s.autoRefreshInterval,
    })),
  setLogFiles: (files) => set({ logFiles: files }),
  setCurrentFile: (id) => set({ currentFileId: id }),
  setLogEntries: (entries, total, levelCounts) =>
    set({ logEntries: entries, logTotal: total, logLevelCounts: levelCounts }),
  setDashboard: (d) => set({ dashboard: d }),
}));
