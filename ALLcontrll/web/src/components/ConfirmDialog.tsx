import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  CircularProgress,
} from '@mui/material';
import type { ReactNode } from 'react';

interface Props {
  /** 是否打开。 */
  open: boolean;
  /** 弹窗标题。 */
  title: string;
  /** 弹窗内容（服务名、命令等）。 */
  children: ReactNode;
  /** 确认按钮文字，如"确认启动"。 */
  confirmLabel: string;
  /** 确认按钮颜色，默认 'primary'。 */
  confirmColor?: 'primary' | 'error' | 'success';
  /** 确认按钮 loading 态。 */
  loading?: boolean;
  /** 确认回调。 */
  onConfirm: () => void;
  /** 取消回调。 */
  onCancel: () => void;
}

/**
 * 通用操作确认弹窗。
 *
 * 用于启动/停止进程前的二次确认（P1-1），避免误操作影响线上服务。
 */
export default function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  confirmColor = 'primary',
  loading = false,
  onConfirm,
  onCancel,
}: Props) {
  return (
    <Dialog open={open} onClose={loading ? undefined : onCancel} fullWidth maxWidth="xs">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>{children}</DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={loading}>
          取消
        </Button>
        <Button
          variant="contained"
          color={confirmColor}
          onClick={onConfirm}
          disabled={loading}
          startIcon={loading ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
