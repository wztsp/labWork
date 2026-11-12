import { createTheme } from '@mui/material/styles';

// 暗色主题，贴合运维监控场景
const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#1976d2' },
    secondary: { main: '#9c27b0' },
    success: { main: '#2e7d32' },
    error: { main: '#d32f2f' },
    warning: { main: '#ed6c02' },
    background: { default: '#f5f6fa', paper: '#ffffff' },
  },
  shape: { borderRadius: 8 },
  typography: {
    fontSize: 14,
  },
});

export default theme;
