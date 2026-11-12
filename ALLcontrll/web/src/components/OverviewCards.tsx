import { Grid, Card, CardContent, Typography } from '@mui/material';
import type { ServiceSummary } from '../types';

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

export default function OverviewCards({ summary }: { summary: ServiceSummary | null }) {
  if (!summary) return null;
  const healthy = summary.offline > 0 ? '#ed6c02' : '#2e7d32';
  return (
    <Grid container spacing={2} sx={{ mb: 2 }}>
      <Grid item xs={6} sm={4} md>
        <StatCard label="在线" value={summary.online} color="#2e7d32" />
      </Grid>
      <Grid item xs={6} sm={4} md>
        <StatCard label="离线" value={summary.offline} color="#d32f2f" />
      </Grid>
      <Grid item xs={6} sm={4} md>
        <StatCard label="已暂停" value={summary.paused} color="#757575" />
      </Grid>
      <Grid item xs={6} sm={4} md>
        <StatCard label="总数" value={summary.total} color="#1976d2" />
      </Grid>
      <Grid item xs={6} sm={4} md>
        <StatCard label="健康率" value={`${summary.health_rate}%`} color={healthy} />
      </Grid>
    </Grid>
  );
}
