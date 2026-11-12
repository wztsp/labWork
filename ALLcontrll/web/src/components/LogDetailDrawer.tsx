import { useState, useEffect } from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  Stack,
  Button,
  CircularProgress,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import api from '../api/client';
import type { LogEntry } from '../types';

interface Props {
  fileId: number | null;
  entryId: number | null;
  onClose: () => void;
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body1">{value}</Typography>
    </Box>
  );
}

export default function LogDetailDrawer({ fileId, entryId, onClose }: Props) {
  const [entry, setEntry] = useState<LogEntry | null>(null);
  const [loading, setLoading] = useState(false);
  const open = entryId != null;

  useEffect(() => {
    if (fileId == null || entryId == null) {
      setEntry(null);
      return;
    }
    setLoading(true);
    api
      .getLogEntry(fileId, entryId)
      .then(setEntry)
      .catch(() => setEntry(null))
      .finally(() => setLoading(false));
  }, [fileId, entryId]);

  const copyText = () => {
    if (!entry) return;
    const full =
      entry.raw_line ||
      `${entry.timestamp ?? ''} ${entry.level} ${entry.message}` +
        (entry.stack_trace ? '\n' + entry.stack_trace : '');
    navigator.clipboard?.writeText(full);
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{ sx: { width: 560 } }}
    >
      <Box sx={{ p: 2 }}>
        <Box
          sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
        >
          <Typography variant="h6">日志详情</Typography>
          <IconButton onClick={onClose}>
            <CloseIcon />
          </IconButton>
        </Box>
        <Divider sx={{ my: 1 }} />
        {loading ? (
          <CircularProgress size={24} />
        ) : (
          entry && (
            <Stack spacing={1}>
              <Info label="时间" value={entry.timestamp ?? '—'} />
              <Info label="级别" value={entry.level ?? '—'} />
              <Info label="Logger" value={entry.logger ?? '—'} />
              <Info label="线程" value={entry.thread ?? '—'} />
              <Typography variant="subtitle2">消息</Typography>
              <Box
                component="pre"
                sx={{
                  whiteSpace: 'pre-wrap',
                  bgcolor: 'grey.100',
                  p: 1,
                  borderRadius: 1,
                  fontSize: 13,
                  maxHeight: 200,
                  overflow: 'auto',
                }}
              >
                {entry.message}
              </Box>
              {entry.stack_trace && (
                <>
                  <Typography variant="subtitle2">异常堆栈</Typography>
                  <Box
                    component="pre"
                    sx={{
                      whiteSpace: 'pre-wrap',
                      bgcolor: '#fdecea',
                      p: 1,
                      borderRadius: 1,
                      fontSize: 13,
                      maxHeight: 300,
                      overflow: 'auto',
                    }}
                  >
                    {entry.stack_trace}
                  </Box>
                </>
              )}
              <Button
                variant="outlined"
                onClick={copyText}
                sx={{ alignSelf: 'flex-start' }}
              >
                复制全文
              </Button>
            </Stack>
          )
        )}
      </Box>
    </Drawer>
  );
}
