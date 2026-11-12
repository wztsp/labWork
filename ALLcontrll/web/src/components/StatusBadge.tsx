import { Chip, type ChipProps } from '@mui/material';
import type { ServiceStatus } from '../types';

const config: Record<ServiceStatus, { label: string; color: ChipProps['color'] }> = {
  online: { label: '在线', color: 'success' },
  offline: { label: '离线', color: 'error' },
  unknown: { label: '未知', color: 'default' },
};

interface Props {
  /** 服务监控状态。 */
  status: ServiceStatus;
  /** 是否启用监控，默认 true。false 时显示"已暂停"徽标。 */
  enabled?: boolean;
}

/**
 * 服务监控状态徽标。
 *
 * - enabled=false → 灰色 outlined "已暂停"
 * - enabled=true  → 按 status 显示在线/离线/未知
 */
export default function StatusBadge({ status, enabled = true }: Props) {
  if (!enabled) {
    return <Chip size="small" label="已暂停" color="default" variant="outlined" />;
  }
  const c = config[status];
  return (
    <Chip
      size="small"
      label={c.label}
      color={c.color}
      variant={status === 'unknown' ? 'outlined' : 'filled'}
    />
  );
}
