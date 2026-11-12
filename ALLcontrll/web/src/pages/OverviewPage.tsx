import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Button,
  Typography,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  TableContainer,
  Paper,
  IconButton,
  Select,
  MenuItem,
  FormControl,
  CircularProgress,
  Alert,
  Tooltip,
  Chip,
  Switch,
  Stack,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import AddIcon from '@mui/icons-material/Add';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import VisibilityIcon from '@mui/icons-material/Visibility';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import api from '../api/client';
import { useAppStore } from '../store/appStore';
import type { Service } from '../types';
import { applyCheckResults } from '../utils/mergeCheckResults';
import OverviewCards from '../components/OverviewCards';
import StatusBadge from '../components/StatusBadge';
import ProcessStatusBadge from '../components/ProcessStatusBadge';
import ServiceConfigDialog from '../components/ServiceConfigDialog';
import ServiceDetailDrawer from '../components/ServiceDetailDrawer';
import AutoDetectDialog from '../components/AutoDetectDialog';
import ConfirmDialog from '../components/ConfirmDialog';

const intervalOptions = [10, 30, 60];

function statusRank(s: Service): number {
  if (s.status === 'offline') return 0;
  if (s.status === 'unknown') return 1;
  return 2;
}

/** 进程确认操作上下文。 */
interface ConfirmAction {
  type: 'start' | 'stop';
  service: Service;
}

export default function OverviewPage() {
  const services = useAppStore((s) => s.services);
  const summary = useAppStore((s) => s.summary);
  const checking = useAppStore((s) => s.checking);
  const setServices = useAppStore((s) => s.setServices);
  const setChecking = useAppStore((s) => s.setChecking);
  const autoRefreshEnabled = useAppStore((s) => s.autoRefreshEnabled);
  const autoRefreshInterval = useAppStore((s) => s.autoRefreshInterval);
  const setAutoRefresh = useAppStore((s) => s.setAutoRefresh);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [configOpen, setConfigOpen] = useState(false);
  const [editing, setEditing] = useState<Service | null>(null);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [autoDetectOpen, setAutoDetectOpen] = useState(false);

  // 监控开关 loading 态（按服务 ID）
  const [switchLoading, setSwitchLoading] = useState<Record<number, boolean>>({});
  // 进程操作 loading 态（按服务 ID）
  const [actionLoading, setActionLoading] = useState<Record<number, boolean>>({});
  // 确认弹窗上下文
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.getServices();
      setServices(res.services, res.summary);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [setServices]);

  const checkAll = useCallback(async () => {
    setChecking(true);
    setError(null);
    try {
      const res = await api.checkAll();
      const nowIso = new Date().toISOString();
      setServices(applyCheckResults(services, res.results, nowIso), res.summary);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setChecking(false);
    }
  }, [services, setServices, setChecking]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!autoRefreshEnabled) return;
    const t = setInterval(() => {
      checkAll();
    }, autoRefreshInterval * 1000);
    return () => clearInterval(t);
  }, [autoRefreshEnabled, autoRefreshInterval, checkAll]);

  /** 局部更新单个服务的字段（不重新拉取全量列表）。 */
  const patchService = useCallback(
    (id: number, patch: Partial<Service>): void => {
      if (summary === null) return;
      const updated: Service[] = services.map((s: Service): Service =>
        s.id === id ? { ...s, ...patch } : s,
      );
      setServices(updated, summary);
    },
    [services, summary, setServices],
  );

  /** 切换监控开关。 */
  const handleToggleEnabled = async (s: Service) => {
    setSwitchLoading((prev) => ({ ...prev, [s.id]: true }));
    try {
      const updated = await api.updateService(s.id, { enabled: !s.enabled });
      patchService(s.id, { enabled: updated.enabled });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSwitchLoading((prev) => ({ ...prev, [s.id]: false }));
    }
  };

  /** 确认启动/停止进程。 */
  const handleConfirmAction = async () => {
    if (confirmAction === null) return;
    const { type, service } = confirmAction;
    setActionLoading((prev) => ({ ...prev, [service.id]: true }));
    try {
      if (type === 'start') {
        const result = await api.startService(service.id);
        patchService(service.id, {
          process_status: 'running',
          pid: result.pid,
          process_started_at: result.started_at,
        });
      } else {
        await api.stopService(service.id);
        patchService(service.id, {
          process_status: 'stopped',
          pid: null,
        });
      }
      setConfirmAction(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setActionLoading((prev) => ({ ...prev, [service.id]: false }));
    }
  };

  const sorted = [...services].sort((a, b) => {
    if (statusRank(a) !== statusRank(b)) return statusRank(a) - statusRank(b);
    return a.id - b.id;
  });

  return (
    <Box>
      <OverviewCards summary={summary} />

      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 2,
          mb: 2,
          flexWrap: 'wrap',
        }}
      >
        <Button
          variant="contained"
          startIcon={
            checking ? <CircularProgress size={18} color="inherit" /> : <RefreshIcon />
          }
          onClick={checkAll}
          disabled={checking}
        >
          {checking ? '检测中…' : '立即检测'}
        </Button>

        <FormControl size="small" sx={{ minWidth: 140 }}>
          <Select
            value={autoRefreshInterval}
            onChange={(e) => setAutoRefresh(true, Number(e.target.value))}
            displayEmpty
          >
            {intervalOptions.map((v) => (
              <MenuItem key={v} value={v}>
                自动刷新 {v}s
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <Box sx={{ flexGrow: 1 }} />

        <Button
          variant="outlined"
          startIcon={<AutoFixHighIcon />}
          onClick={() => setAutoDetectOpen(true)}
        >
          自动配置
        </Button>

        <Button
          variant="outlined"
          startIcon={<AddIcon />}
          onClick={() => {
            setEditing(null);
            setConfigOpen(true);
          }}
        >
          新增服务
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>服务名</TableCell>
              <TableCell>类型</TableCell>
              <TableCell>地址</TableCell>
              <TableCell>监控</TableCell>
              <TableCell>进程</TableCell>
              <TableCell>状态</TableCell>
              <TableCell>最后检测</TableCell>
              <TableCell>耗时</TableCell>
              <TableCell align="right">操作</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sorted.map((s) => (
              <TableRow
                key={s.id}
                hover
                sx={
                  s.status === 'offline' || s.process_status === 'crashed'
                    ? { backgroundColor: '#fdecea' }
                    : undefined
                }
              >
                <TableCell>
                  <Typography fontWeight={600}>{s.name}</Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={s.type === 'frontend' ? '前端' : '后端'}
                    variant="outlined"
                  />
                </TableCell>
                <TableCell>
                  {s.host}:{s.port}
                  {s.health_url && (
                    <Typography variant="caption" display="block" color="text.secondary">
                      {s.health_url}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>
                  {switchLoading[s.id] ? (
                    <CircularProgress size={20} />
                  ) : (
                    <Switch
                      size="small"
                      checked={s.enabled}
                      onChange={() => handleToggleEnabled(s)}
                    />
                  )}
                </TableCell>
                <TableCell>
                  <ProcessStatusBadge
                    processStatus={s.process_status}
                    hasStartCommand={!!s.start_command}
                  />
                </TableCell>
                <TableCell>
                  <StatusBadge status={s.status} enabled={s.enabled} />
                </TableCell>
                <TableCell>
                  {s.last_checked_at
                    ? new Date(s.last_checked_at).toLocaleString('zh-CN')
                    : '—'}
                </TableCell>
                <TableCell>
                  {s.response_ms != null ? `${s.response_ms} ms` : '—'}
                </TableCell>
                <TableCell align="right">
                  <Stack direction="row" spacing={0.5} justifyContent="flex-end" alignItems="center">
                    {actionLoading[s.id] ? (
                      <CircularProgress size={20} />
                    ) : (
                      <>
                        <Tooltip title={s.start_command ? '启动进程' : '未配置启动命令'}>
                          <span>
                            <IconButton
                              size="small"
                              color="success"
                              disabled={s.process_status === 'running' || !s.start_command}
                              onClick={() => setConfirmAction({ type: 'start', service: s })}
                            >
                              <PlayArrowIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="停止进程">
                          <span>
                            <IconButton
                              size="small"
                              color="error"
                              disabled={s.process_status !== 'running'}
                              onClick={() => setConfirmAction({ type: 'stop', service: s })}
                            >
                              <StopIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      </>
                    )}
                    <Tooltip title="详情 / 配置">
                      <IconButton
                        size="small"
                        onClick={() => {
                          setEditing(s);
                          setConfigOpen(true);
                        }}
                      >
                        <VisibilityIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="查看历史">
                      <IconButton size="small" onClick={() => setDetailId(s.id)}>
                        <VisibilityIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {sorted.length === 0 && (
              <TableRow>
                <TableCell colSpan={9} align="center">
                  {loading ? '加载中…' : '暂无服务'}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <ServiceConfigDialog
        open={configOpen}
        service={editing}
        onClose={() => setConfigOpen(false)}
        onSaved={() => {
          setConfigOpen(false);
          load();
        }}
      />

      <AutoDetectDialog
        open={autoDetectOpen}
        onClose={() => setAutoDetectOpen(false)}
        onApplied={() => {
          setAutoDetectOpen(false);
          load();
        }}
      />

      <ServiceDetailDrawer
        serviceId={detailId}
        onClose={() => setDetailId(null)}
      />

      <ConfirmDialog
        open={confirmAction !== null}
        title={confirmAction?.type === 'start' ? '确认启动服务' : '确认停止服务'}
        confirmLabel={confirmAction?.type === 'start' ? '确认启动' : '确认停止'}
        confirmColor={confirmAction?.type === 'start' ? 'success' : 'error'}
        loading={confirmAction !== null && actionLoading[confirmAction.service.id] === true}
        onConfirm={handleConfirmAction}
        onCancel={() => setConfirmAction(null)}
      >
        {confirmAction && (
          <Stack spacing={1}>
            <Typography>
              即将{confirmAction.type === 'start' ? '启动' : '停止'}服务：
              <strong>{confirmAction.service.name}</strong>
            </Typography>
            {confirmAction.type === 'start' && confirmAction.service.start_command && (
              <Typography variant="body2" color="text.secondary">
                启动命令：{confirmAction.service.start_command}
              </Typography>
            )}
            <Typography variant="body2">确认执行此操作？</Typography>
          </Stack>
        )}
      </ConfirmDialog>
    </Box>
  );
}
