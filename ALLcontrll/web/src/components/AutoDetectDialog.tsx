import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Stack,
  Alert,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  TableContainer,
  Paper,
  Checkbox,
  Chip,
  Box,
  Typography,
  CircularProgress,
} from '@mui/material';
import ManageSearchIcon from '@mui/icons-material/ManageSearch';
import api from '../api/client';
import type { DetectResultItem, ApplyConfigItem } from '../types';

interface Props {
  /** 是否打开。 */
  open: boolean;
  /** 关闭回调。 */
  onClose: () => void;
  /** 应用成功后回调（通常刷新服务列表）。 */
  onApplied: () => void;
}

/** 匹配类型 → 中文标签。 */
function matchTypeLabel(type: DetectResultItem['match_type']): string {
  switch (type) {
    case 'exact':
      return '精确';
    case 'prefix':
      return '前缀';
    case 'contains':
      return '包含';
    default:
      return type;
  }
}

/** 匹配类型 → Chip 颜色。精确=绿/前缀=蓝/包含=灰。 */
function matchTypeColor(type: DetectResultItem['match_type']): 'success' | 'info' | 'default' {
  switch (type) {
    case 'exact':
      return 'success';
    case 'prefix':
      return 'info';
    case 'contains':
      return 'default';
    default:
      return 'default';
  }
}

/**
 * 启动命令自动配置弹窗。
 *
 * 用户输入根目录 → 系统扫描可执行文件 → 按服务名匹配 → 勾选应用。
 * 默认勾选置信度 high 和 medium 的结果。
 */
export default function AutoDetectDialog({ open, onClose, onApplied }: Props) {
  const [baseDir, setBaseDir] = useState('');
  const [scanning, setScanning] = useState(false);
  const [results, setResults] = useState<DetectResultItem[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [applying, setApplying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 弹窗关闭时重置扫描/应用状态
  useEffect(() => {
    if (!open) {
      setResults([]);
      setSelected(new Set());
      setError(null);
      setScanning(false);
      setApplying(false);
    }
  }, [open]);

  /** 执行目录扫描。 */
  const handleScan = async () => {
    const dir = baseDir.trim();
    if (!dir) {
      setError('请输入目录路径');
      return;
    }
    setScanning(true);
    setError(null);
    try {
      const data = await api.autoDetectCommands(dir);
      setResults(data.results);
      // 默认勾选 high 和 medium 置信度的结果
      const defaultSelected = new Set<number>(
        data.results
          .filter((r) => r.confidence === 'high' || r.confidence === 'medium')
          .map((r) => r.service_id),
      );
      setSelected(defaultSelected);
      if (data.results.length === 0) {
        setError(`未匹配到任何服务（共扫描 ${data.scanned_files} 个文件）`);
      }
    } catch (e) {
      setError((e as Error).message);
      setResults([]);
      setSelected(new Set());
    } finally {
      setScanning(false);
    }
  };

  /** 应用选中的配置。 */
  const handleApply = async () => {
    if (selected.size === 0) return;
    setApplying(true);
    setError(null);
    try {
      const configs: ApplyConfigItem[] = results
        .filter((r) => selected.has(r.service_id))
        .map((r) => ({
          id: r.service_id,
          start_command: r.detected_command,
          work_dir: r.detected_work_dir,
        }));
      await api.applyDetectedConfigs(configs);
      onApplied();
      setResults([]);
      setSelected(new Set());
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setApplying(false);
    }
  };

  /** 切换单个服务的选中状态。 */
  const toggleSelect = (serviceId: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(serviceId)) {
        next.delete(serviceId);
      } else {
        next.add(serviceId);
      }
      return next;
    });
  };

  /** 全选/全不选。 */
  const toggleAll = () => {
    if (selected.size === results.length) {
      setSelected(new Set());
    } else {
      setSelected(new Set(results.map((r) => r.service_id)));
    }
  };

  const allChecked = results.length > 0 && selected.size === results.length;
  const someChecked = selected.size > 0 && selected.size < results.length;

  return (
    <Dialog
      open={open}
      onClose={scanning || applying ? undefined : onClose}
      fullWidth
      maxWidth="lg"
    >
      <DialogTitle>启动命令自动配置</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {/* 目录输入 + 扫描按钮 */}
          <Stack direction="row" spacing={1} alignItems="center">
            <TextField
              label="根目录路径"
              value={baseDir}
              onChange={(e) => setBaseDir(e.target.value)}
              placeholder="D:\lis\deploy"
              fullWidth
              size="small"
              disabled={scanning || applying}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  handleScan();
                }
              }}
            />
            <Button
              variant="contained"
              startIcon={scanning ? <CircularProgress size={18} color="inherit" /> : <ManageSearchIcon />}
              onClick={handleScan}
              disabled={scanning || applying || !baseDir.trim()}
              sx={{ whiteSpace: 'nowrap' }}
            >
              {scanning ? '扫描中…' : '扫描'}
            </Button>
          </Stack>

          {error && <Alert severity="error">{error}</Alert>}

          {/* 扫描结果表格 */}
          {results.length > 0 && (
            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell padding="checkbox">
                      <Checkbox
                        checked={allChecked}
                        indeterminate={someChecked}
                        onChange={toggleAll}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>服务名</TableCell>
                    <TableCell>匹配文件</TableCell>
                    <TableCell>启动命令</TableCell>
                    <TableCell>工作目录</TableCell>
                    <TableCell>匹配类型</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {results.map((item) => (
                    <TableRow
                      key={item.service_id}
                      sx={
                        item.confidence === 'low'
                          ? { backgroundColor: '#fff9e6' }
                          : undefined
                      }
                    >
                      <TableCell padding="checkbox">
                        <Checkbox
                          checked={selected.has(item.service_id)}
                          onChange={() => toggleSelect(item.service_id)}
                          size="small"
                        />
                      </TableCell>
                      <TableCell>
                        <Typography fontWeight={600}>{item.service_name}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>
                          {item.matched_file}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ wordBreak: 'break-all' }} fontFamily="monospace">
                          {item.detected_command}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>
                          {item.detected_work_dir ?? '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={matchTypeLabel(item.match_type)}
                          color={matchTypeColor(item.match_type)}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}

          {/* 图例 */}
          {results.length > 0 && (
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
              <Chip size="small" label="精确" color="success" />
              <Chip size="small" label="前缀" color="info" />
              <Chip size="small" label="包含" color="default" />
              <Typography variant="caption" color="text.secondary">
                浅黄色行表示低置信度匹配，请人工确认
              </Typography>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Box sx={{ flexGrow: 1 }}>
          {selected.size > 0 && (
            <Typography variant="body2" color="text.secondary">
              已选 {selected.size} 项
            </Typography>
          )}
        </Box>
        <Button onClick={onClose} disabled={scanning || applying}>
          取消
        </Button>
        <Button
          variant="contained"
          onClick={handleApply}
          disabled={applying || scanning || selected.size === 0}
          startIcon={applying ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          {applying ? '应用中…' : '应用选中'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
