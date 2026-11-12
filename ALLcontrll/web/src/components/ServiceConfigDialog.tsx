import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Stack,
  Alert,
} from '@mui/material';
import api from '../api/client';
import type { Service, ServiceType } from '../types';

interface Props {
  open: boolean;
  service: Service | null;
  onClose: () => void;
  onSaved: () => void;
}

interface ServiceForm {
  name: string;
  type: ServiceType;
  host: string;
  port: number;
  health_url: string;
  start_command: string;
  work_dir: string;
}

export default function ServiceConfigDialog({ open, service, onClose, onSaved }: Props) {
  const [form, setForm] = useState<ServiceForm>({
    name: '',
    type: 'backend',
    host: '127.0.0.1',
    port: 8080,
    health_url: '',
    start_command: '',
    work_dir: '',
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    if (service) {
      setForm({
        name: service.name,
        type: service.type,
        host: service.host,
        port: service.port,
        health_url: service.health_url ?? '',
        start_command: service.start_command ?? '',
        work_dir: service.work_dir ?? '',
      });
    } else {
      setForm({
        name: '',
        type: 'backend',
        host: '127.0.0.1',
        port: 8080,
        health_url: '',
        start_command: '',
        work_dir: '',
      });
    }
    setError(null);
  }, [open, service]);

  const save = async () => {
    setSaving(true);
    setError(null);
    const payload = {
      name: form.name,
      type: form.type,
      host: form.host,
      port: Number(form.port),
      health_url: form.health_url || null,
      start_command: form.start_command || null,
      work_dir: form.work_dir || null,
    };
    try {
      if (service) await api.updateService(service.id, payload);
      else await api.createService(payload);
      onSaved();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>服务配置</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="服务名"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            fullWidth
          />
          <FormControl fullWidth>
            <InputLabel>类型</InputLabel>
            <Select
              label="类型"
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value as ServiceType })}
            >
              <MenuItem value="backend">后端</MenuItem>
              <MenuItem value="frontend">前端</MenuItem>
            </Select>
          </FormControl>
          <TextField
            label="主机"
            value={form.host}
            onChange={(e) => setForm({ ...form, host: e.target.value })}
          />
          <TextField
            label="端口"
            type="number"
            value={form.port}
            onChange={(e) => setForm({ ...form, port: Number(e.target.value) })}
          />
          <TextField
            label="健康探测 URL（可选）"
            value={form.health_url}
            onChange={(e) => setForm({ ...form, health_url: e.target.value })}
            placeholder="留空则用 TCP 端口探测"
            fullWidth
          />
          <TextField
            label="启动命令（可选）"
            value={form.start_command}
            onChange={(e) => setForm({ ...form, start_command: e.target.value })}
            placeholder="java -jar D:\path\to\lis-gateway.jar"
            helperText="用于进程启动/停止功能；留空则该服务仅支持监控"
            multiline
            rows={2}
            fullWidth
          />
          <TextField
            label="工作目录（可选）"
            value={form.work_dir}
            onChange={(e) => setForm({ ...form, work_dir: e.target.value })}
            placeholder="D:\path\to\service"
            helperText="进程启动的工作目录，留空使用默认目录"
            fullWidth
          />
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>取消</Button>
        <Button variant="contained" onClick={save} disabled={saving || !form.name}>
          保存
        </Button>
      </DialogActions>
    </Dialog>
  );
}
