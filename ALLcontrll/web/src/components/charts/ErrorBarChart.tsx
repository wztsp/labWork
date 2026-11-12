import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';

interface Props {
  data: Array<{ service_name: string; error_count: number }>;
}

export default function ErrorBarChart({ data }: Props) {
  const option: EChartsOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: 140, right: 30, top: 20, bottom: 30 },
    xAxis: { type: 'value' },
    yAxis: {
      type: 'category',
      data: data.map((d) => d.service_name),
    },
    series: [
      {
        type: 'bar',
        data: data.map((d) => d.error_count),
        itemStyle: { color: '#d32f2f' },
      },
    ],
  };
  return <ReactECharts option={option} style={{ height: 320 }} />;
}
