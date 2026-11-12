import { Chip, type ChipProps } from '@mui/material';
import type { ProcessStatus } from '../types';

interface Props {
  /** 进程状态。 */
  processStatus: ProcessStatus;
  /** 是否配置了启动命令（影响 unknown 的展示文案）。 */
  hasStartCommand: boolean;
}

interface BadgeConfig {
  label: string;
  color: ChipProps['color'];
  variant: ChipProps['variant'];
}

/**
 * 进程状态徽标。
 *
 * 根据 process_status + hasStartCommand 展示：
 *   running  → 绿色 filled "运行中"
 *   stopped  → 灰色 filled "已停止"
 *   crashed  → 红色 filled "已崩溃"
 *   unknown + 未配置 → 灰色 outlined dashed "未配置"
 *   unknown + 已配置 → 灰色 outlined "未启动"
 */
export default function ProcessStatusBadge({ processStatus, hasStartCommand }: Props) {
  let config: BadgeConfig;

  if (processStatus === 'running') {
    config = { label: '运行中', color: 'success', variant: 'filled' };
  } else if (processStatus === 'stopped') {
    config = { label: '已停止', color: 'default', variant: 'filled' };
  } else if (processStatus === 'crashed') {
    config = { label: '已崩溃', color: 'error', variant: 'filled' };
  } else if (hasStartCommand) {
    config = { label: '未启动', color: 'default', variant: 'outlined' };
  } else {
    config = { label: '未配置', color: 'default', variant: 'outlined' };
  }

  return (
    <Chip
      size="small"
      label={config.label}
      color={config.color}
      variant={config.variant}
      sx={
        !hasStartCommand && processStatus === 'unknown'
          ? { borderStyle: 'dashed' }
          : undefined
      }
    />
  );
}
