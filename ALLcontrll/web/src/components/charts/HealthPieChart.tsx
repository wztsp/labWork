import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';

interface Props {
  data: Array<{ name: string; value: number }>;
}

export default function HealthPieChart({ data }: Props) {
  const option: EChartsOption = {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        name: '服务健康率',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: true,
        data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0,0,0,0.2)',
          },
        },
      },
    ],
  };
  return <ReactECharts option={option} style={{ height: 300 }} />;
}
