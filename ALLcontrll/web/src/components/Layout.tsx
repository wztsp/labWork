import { useState, useEffect, type ReactNode } from 'react';
import {
  Box,
  AppBar,
  Toolbar,
  Typography,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Switch,
  FormControlLabel,
} from '@mui/material';
import DashboardIcon from '@mui/icons-material/Dashboard';
import ListAltIcon from '@mui/icons-material/ListAlt';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAppStore } from '../store/appStore';

const drawerWidth = 220;

export default function Layout({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [now, setNow] = useState(new Date());

  const autoRefreshEnabled = useAppStore((s) => s.autoRefreshEnabled);
  const autoRefreshInterval = useAppStore((s) => s.autoRefreshInterval);
  const setAutoRefresh = useAppStore((s) => s.setAutoRefresh);

  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(t);
  }, []);

  const navItems = [
    { path: '/', label: '服务状态总览', icon: <MonitorHeartIcon /> },
    { path: '/logs', label: '异常日志', icon: <ListAltIcon /> },
    { path: '/dashboard', label: '仪表盘', icon: <DashboardIcon /> },
  ];

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}
      >
        <Toolbar>
          <Typography variant="h6" noWrap sx={{ flexGrow: 1 }}>
            LIS 运维后台管理系统
          </Typography>
          <FormControlLabel
            control={
              <Switch
                checked={autoRefreshEnabled}
                onChange={(e) => setAutoRefresh(e.target.checked)}
              />
            }
            label="自动刷新"
          />
          <Typography variant="body2" sx={{ ml: 2, minWidth: 150, textAlign: 'right' }}>
            {now.toLocaleString('zh-CN')}（{autoRefreshInterval}s）
          </Typography>
        </Toolbar>
      </AppBar>

      <Box
        component="nav"
        sx={{ width: drawerWidth, flexShrink: 0 }}
      >
        <Box
          sx={{
            width: drawerWidth,
            position: 'fixed',
            top: 0,
            bottom: 0,
            left: 0,
            pt: 8,
            bgcolor: 'background.paper',
            borderRight: '1px solid #e0e0e0',
            overflowY: 'auto',
          }}
        >
          <List>
            {navItems.map((item) => (
              <ListItemButton
                key={item.path}
                selected={location.pathname === item.path}
                onClick={() => navigate(item.path)}
              >
                <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItemButton>
            ))}
          </List>
        </Box>
      </Box>

      <Box
        component="main"
        sx={{ flexGrow: 1, p: 3, bgcolor: 'background.default', ml: `${drawerWidth}px` }}
      >
        <Toolbar />
        {children}
      </Box>
    </Box>
  );
}
