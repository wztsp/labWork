import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  Chip,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  TableContainer,
  Paper,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Button,
  Pagination,
  Stack,
  Alert,
} from '@mui/material';
import VisibilityIcon from '@mui/icons-material/Visibility';
import api, { type LogQueryParams } from '../api/client';
import { useAppStore } from '../store/appStore';
import LogUploadZone from '../components/LogUploadZone';
import LogDetailDrawer from '../components/LogDetailDrawer';

const levels = ['ALL', 'ERROR', 'WARN', 'INFO'];

export default function LogsPage() {
  const logFiles = useAppStore((s) => s.logFiles);
  const setLogFiles = useAppStore((s) => s.setLogFiles);
  const currentFileId = useAppStore((s) => s.currentFileId);
  const setCurrentFile = useAppStore((s) => s.setCurrentFile);
  const logEntries = useAppStore((s) => s.logEntries);
  const logTotal = useAppStore((s) => s.logTotal);
  const setLogEntries = useAppStore((s) => s.setLogEntries);

  const [level, setLevel] = useState('ALL');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const pageSize = 20;
  const [hasStack, setHasStack] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detailId, setDetailId] = useState<number | null>(null);

  const refreshFiles = useCallback(async () => {
    try {
      const files = await api.getLogFiles();
      setLogFiles(files);
      if (currentFileId == null && files.length > 0) setCurrentFile(files[0].id);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [setLogFiles, setCurrentFile, currentFileId]);

  const loadEntries = useCallback(async () => {
    if (currentFileId == null) return;
    setLoading(true);
    setError(null);
    const params: LogQueryParams = {
      page,
      page_size: pageSize,
      has_stack: hasStack || undefined,
    };
    if (level !== 'ALL') params.level = level;
    if (keyword.trim()) params.keyword = keyword.trim();
    try {
      const res = await api.getLogEntries(currentFileId, params);
      setLogEntries(res.entries, res.total, res.level_counts);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [currentFileId, page, pageSize, level, keyword, hasStack, setLogEntries]);

  useEffect(() => {
    refreshFiles();
  }, [refreshFiles]);

  useEffect(() => {
    loadEntries();
  }, [loadEntries]);

  const onUploaded = (fileId: number) => {
    setCurrentFile(fileId);
    setPage(1);
    refreshFiles();
  };

  const deleteFile = async (id: number) => {
    await api.deleteLogFile(id);
    setCurrentFile(null);
    setLogEntries([], 0, {});
    refreshFiles();
  };

  return (
    <Box>
      <LogUploadZone onUploaded={onUploaded} />

      <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap', gap: 1 }}>
        {logFiles.map((f) => (
          <Chip
            key={f.id}
            label={`${f.filename} (${f.entry_count})`}
            clickable
            color={f.id === currentFileId ? 'primary' : 'default'}
            onClick={() => {
              setCurrentFile(f.id);
              setPage(1);
            }}
            onDelete={() => deleteFile(f.id)}
          />
        ))}
        {logFiles.length === 0 && (
          <Typography color="text.secondary">尚未上传日志文件</Typography>
        )}
      </Stack>

      <Stack
        direction="row"
        spacing={2}
        sx={{ mb: 2, flexWrap: 'wrap', gap: 2 }}
        alignItems="center"
      >
        <FormControl size="small" sx={{ minWidth: 120 }}>
          <InputLabel>级别</InputLabel>
          <Select
            label="级别"
            value={level}
            onChange={(e) => {
              setLevel(e.target.value);
              setPage(1);
            }}
          >
            {levels.map((l) => (
              <MenuItem key={l} value={l}>
                {l}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <TextField
          size="small"
          label="关键字"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') setPage(1);
          }}
          sx={{ minWidth: 220 }}
        />
        <Button variant="outlined" onClick={() => setPage(1)}>
          筛选
        </Button>
        <FormControl size="small" sx={{ minWidth: 140 }}>
          <InputLabel>堆栈</InputLabel>
          <Select
            label="堆栈"
            value={hasStack ? '1' : '0'}
            onChange={(e) => {
              setHasStack(e.target.value === '1');
              setPage(1);
            }}
          >
            <MenuItem value="0">全部</MenuItem>
            <MenuItem value="1">仅异常堆栈</MenuItem>
          </Select>
        </FormControl>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>时间</TableCell>
              <TableCell>级别</TableCell>
              <TableCell>Logger</TableCell>
              <TableCell>消息</TableCell>
              <TableCell align="right">操作</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {logEntries.map((e) => (
              <TableRow
                key={e.id}
                hover
                sx={
                  e.level === 'ERROR'
                    ? { backgroundColor: '#fdecea' }
                    : undefined
                }
                onClick={() => setDetailId(e.id)}
                style={{ cursor: 'pointer' }}
              >
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  {e.timestamp ? new Date(e.timestamp).toLocaleString('zh-CN') : '—'}
                </TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={e.level}
                    color={
                      e.level === 'ERROR'
                        ? 'error'
                        : e.level === 'WARN'
                          ? 'warning'
                          : 'default'
                    }
                  />
                </TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  {e.logger ?? '—'}
                </TableCell>
                <TableCell
                  sx={{
                    maxWidth: 520,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {e.message}
                </TableCell>
                <TableCell align="right">
                  <VisibilityIcon
                    fontSize="small"
                    color="action"
                    onClick={(ev) => {
                      ev.stopPropagation();
                      setDetailId(e.id);
                    }}
                  />
                </TableCell>
              </TableRow>
            ))}
            {logEntries.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  {loading ? '加载中…' : '暂无日志条目'}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
        <Pagination
          count={Math.max(1, Math.ceil(logTotal / pageSize))}
          page={page}
          onChange={(_, p) => setPage(p)}
          color="primary"
        />
      </Box>

      <LogDetailDrawer
        fileId={currentFileId}
        entryId={detailId}
        onClose={() => setDetailId(null)}
      />
    </Box>
  );
}
