import { useState, useEffect } from 'react';
import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  Stack,
  List,
  ListItem,
  ListItemText,
  CircularProgress,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import api from '../api/client';
import type { Service, ServiceHistoryItem } from '../types';
import { useAppStore } from '../store/appStore';
import StatusBadge from './StatusBadge';

interface Props {
  serviceId: number | null;
  onClose: () => void;
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body1">{value}</Typography>
    </Box>
  );
}

export default function ServiceDetailDrawer({ serviceId, onClose }: Props) {
  const services = useAppStore((s) => s.services);
  const [history, setHistory] = useState<ServiceHistoryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const open = serviceId != null;
  const service: Service | undefined = services.find((s) => s.id === serviceId);

  useEffect(() => {
    if (serviceId == null) return;
    setLoading(true);
    api
      .getHistory(serviceId, 50)
      .then((h) => setHistory(h))
      .catch(() => setHistory([]))
      .finally(() => setLoading(false));
  }, [serviceId]);

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{ sx: { width: 420 } }}
    >
      <Box sx={{ p: 2 }}>
        <Box
          sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
        >
          <Typography variant="h6">服务详情</Typography>
          <IconButton onClick={onClose}>
            <CloseIcon />
          </IconButton>
        </Box>
        <Divider sx={{ my: 1 }} />

        {service && (
          <Stack spacing={1}>
            <InfoRow label="服务名" value={service.name} />
            <InfoRow label="类型" value={service.type === 'frontend' ? '前端' : '后端'} />
            <InfoRow label="地址" value={`${service.host}:${service.port}`} />
            <InfoRow label="健康 URL" value={service.health_url ?? '（TCP 端口探测）'} />
            <Box>
              <Typography variant="body2" color="text.secondary">
                当前状态
              </Typography>
              <StatusBadge status={service.status} />
            </Box>
            <InfoRow
              label="最后检测"
              value={
                service.last_checked_at
                  ? new Date(service.last_checked_at).toLocaleString('zh-CN')
                  : '—'
              }
            />
            <InfoRow
              label="响应耗时"
              value={service.response_ms != null ? `${service.response_ms} ms` : '—'}
            />
          </Stack>
        )}

        <Divider sx={{ my: 2 }} />
        <Typography variant="subtitle2" gutterBottom>
          最近检测历史
        </Typography>
        {loading ? (
          <CircularProgress size={24} />
        ) : (
          <List dense>
            {history.map((h) => (
              <ListItem key={h.id} divider>
                <ListItemText
                  primary={<StatusBadge status={h.status} />}
                  secondary={`${new Date(h.checked_at).toLocaleString('zh-CN')}${
                    h.response_ms != null ? ` · ${h.response_ms}ms` : ''
                  }`}
                />
              </ListItem>
            ))}
            {history.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                暂无历史
              </Typography>
            )}
          </List>
        )}
      </Box>
    </Drawer>
  );
}
