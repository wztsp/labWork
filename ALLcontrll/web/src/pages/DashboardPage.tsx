import { useState, useEffect } from 'react';
import { Box, Grid, Card, CardContent, Typography, CircularProgress, Alert } from '@mui/material';
import api from '../api/client';
import { useAppStore } from '../store/appStore';
import HealthPieChart from '../components/charts/HealthPieChart';
import ErrorBarChart from '../components/charts/ErrorBarChart';
import OverviewCards from '../components/OverviewCards';

function StatCard({
  label,
  value,
  color,
}: {
  label: string;
  value: string | number;
  color: string;
}) {
  return (
    <Card sx={{ borderTop: `4px solid ${color}`, height: '100%' }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h4" sx={{ color, fontWeight: 600 }}>
          {value}
        </Typography>
      </CardContent>
    </Card>
  );
}

export default function DashboardPage() {
  const dashboard = useAppStore((s) => s.dashboard);
  const setDashboard = useAppStore((s) => s.setDashboard);
  const summary = useAppStore((s) => s.summary);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    api
      .getDashboard()
      .then((d) => setDashboard(d))
      .catch((e) => setError((e as Error).message))
      .finally(() => setLoading(false));
  }, [setDashboard]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 2 }}>
        {error}
      </Alert>
    );
  }

  if (!dashboard) return null;

  const { log_summary, service_health_pie, service_error_counts } = dashboard;
  const healthRate = summary ? summary.health_rate : dashboard.service_status.health_rate;

  return (
    <Box>
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid item xs={6} md={3}>
          <StatCard label="在线率" value={`${healthRate}%`} color="#2e7d32" />
        </Grid>
        <Grid item xs={6} md={3}>
          <StatCard
            label="离线服务"
            value={dashboard.service_status.offline}
            color="#d32f2f"
          />
        </Grid>
        <Grid item xs={6} md={3}>
          <StatCard label="已上传日志" value={log_summary.total_files} color="#1976d2" />
        </Grid>
        <Grid item xs={6} md={3}>
          <StatCard label="ERROR 条数" value={log_summary.error_count} color="#ed6c02" />
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} md={5}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                服务健康率
              </Typography>
              {service_health_pie.length > 0 ? (
                <HealthPieChart data={service_health_pie} />
              ) : (
                <Typography color="text.secondary">暂无数据</Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={7}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                各服务异常计数
              </Typography>
              {service_error_counts.length > 0 ? (
                <ErrorBarChart data={service_error_counts} />
              ) : (
                <Typography color="text.secondary">暂无异常数据</Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Box sx={{ mt: 2 }}>
        <OverviewCards summary={dashboard.service_status} />
      </Box>
    </Box>
  );
}
