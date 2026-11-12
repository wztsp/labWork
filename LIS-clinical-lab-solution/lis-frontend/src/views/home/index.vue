<template>
  <div class="home-dashboard">
    <!-- 欢迎区 -->
    <el-card shadow="never" class="welcome-card">
      <div class="welcome">
        <div>
          <div class="welcome-title">{{ greeting }}，{{ displayName }}</div>
          <div class="welcome-sub">欢迎使用临床实验室信息系统 (LIS)，今天是 {{ today }}</div>
        </div>
        <el-button type="primary" plain :loading="loading" @click="loadAll">
          <el-icon style="margin-right: 4px"><Refresh /></el-icon>
          刷新数据
        </el-button>
      </div>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="4" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover" class="stat-card" @click="card.path && router.push(card.path)">
          <div class="stat-content">
            <el-icon class="stat-icon" :style="{ color: card.color }">
              <component :is="card.icon" />
            </el-icon>
            <div class="stat-value">{{ card.display }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表 + 消息 -->
    <el-row :gutter="16" class="middle-row">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-title">
              <el-icon style="color: #1a73e8; margin-right: 6px"><TrendCharts /></el-icon>
              <span>近7日标本量趋势</span>
            </div>
          </template>
          <v-chart class="chart" :option="chartOption" autoresize />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-title">
              <el-icon style="color: #e6a23c; margin-right: 6px"><Bell /></el-icon>
              <span>最新消息</span>
              <el-button type="primary" link size="small" style="margin-left: auto" @click="router.push('/monitor/message')">
                更多
              </el-button>
            </div>
          </template>
          <div v-loading="msgLoading" class="msg-list">
            <div v-if="!msgLoading && messages.length === 0" class="msg-empty">暂无消息</div>
            <div v-for="msg in messages" :key="msg.id" class="msg-item" @click="router.push('/monitor/message')">
              <el-tag :type="tagType(msg.messageType)" size="small">{{ typeLabel(msg.messageType) }}</el-tag>
              <div class="msg-text">
                <div class="msg-title">{{ msg.title }}</div>
                <div class="msg-time">{{ formatTime(msg.createTime) }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷导航 -->
    <el-card shadow="never" class="nav-card">
      <template #header>
        <div class="card-title">
          <el-icon style="color: #67c23a; margin-right: 6px"><Grid /></el-icon>
          <span>快捷导航</span>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="6" v-for="group in navGroups" :key="group.title" class="nav-group">
          <div class="nav-group-title">
            <el-icon :style="{ color: group.color }"><component :is="group.icon" /></el-icon>
            <span>{{ group.title }}</span>
          </div>
          <div class="nav-links">
            <div
              v-for="link in group.links"
              :key="link.path"
              class="nav-link"
              @click="router.push(link.path)"
            >
              <span class="nav-link-label">{{ link.label }}</span>
              <el-icon class="nav-link-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  Refresh,
  TrendCharts,
  Bell,
  Grid,
  ArrowRight,
  Cpu,
  Document,
  DataAnalysis,
  Setting,
  Files,
  Monitor,
  Box,
  Connection,
  AlarmClock,
  User,
  Warning
} from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { getWorkloadStats, getWorkloadByDay, getCriticalStats } from '@/api/stats'
import { getTatMonitorStats } from '@/api/monitor'
import { getMessagePage, MESSAGE_TYPE_MAP, type Message } from '@/api/message'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent])

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const msgLoading = ref(false)
const messages = ref<Message[]>([])

const workload = reactive({ total: 0, today: 0, urgent: 0, reported: 0 })
const critical = reactive({ total: 0, confirmed: 0, confirmRate: 0 })
const tatStats = ref<Record<string, any>>({})
const unreadCount = ref(0)
const trendData = ref<{ day: string; count: number }[]>([])

const displayName = computed(() => userStore.realName || userStore.username || '用户')
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const statCards = computed(() => [
  { label: '今日标本量', display: workload.today, icon: Cpu, color: '#1a73e8', path: '/sample/barcode' },
  { label: '总标本量', display: workload.total, icon: Document, color: '#67c23a', path: '/sample/tracking' },
  { label: '已报告数', display: workload.reported, icon: DataAnalysis, color: '#909399', path: '/report/list' },
  { label: '危急值总数', display: critical.total, icon: Warning, color: '#f56c6c', path: '/report/critical' },
  { label: '危急值确认率', display: `${critical.confirmRate}%`, icon: TrendCharts, color: '#e6a23c', path: '/stats/critical' },
  { label: 'TAT超时', display: tatStats.value.overdue || 0, icon: AlarmClock, color: '#9c27b0', path: '/monitor/tat-monitor' }
])

const chartOption = computed(() => {
  const days = trendData.value.map(d => d.day)
  const counts = trendData.value.map(d => d.count)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['标本量'] },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: days },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '标本量',
        type: 'bar',
        data: counts,
        itemStyle: { color: '#1a73e8', borderRadius: [4, 4, 0, 0] },
        barWidth: 28
      }
    ]
  }
})

const navGroups = [
  {
    title: '标本管理',
    icon: Cpu,
    color: '#1a73e8',
    links: [
      { label: '标本采集', path: '/sample/barcode' },
      { label: '待采集申请', path: '/sample/pending' },
      { label: '标本送检', path: '/sample/deliver' },
      { label: '标本签收', path: '/sample/sign-off' },
      { label: '标本核收', path: '/sample/receive' },
      { label: '标本追踪', path: '/sample/tracking' }
    ]
  },
  {
    title: '报告管理',
    icon: Document,
    color: '#f56c6c',
    links: [
      { label: '技师工作站', path: '/report/technician' },
      { label: '报告审核', path: '/report/audit' },
      { label: '危急值处理', path: '/report/critical' },
      { label: '报告查询', path: '/report/query' },
      { label: '报告列表', path: '/report/list' },
      { label: '报告模板设计', path: '/report/designer' }
    ]
  },
  {
    title: '质量控制',
    icon: DataAnalysis,
    color: '#67c23a',
    links: [
      { label: '日常质控', path: '/qc/daily' },
      { label: '质控基础数据', path: '/qc/lot' },
      { label: '规则配置', path: '/qc/rule-config' },
      { label: '质控预警', path: '/qc/alert' },
      { label: '室间质控', path: '/qc/eqa' },
      { label: '质控月报', path: '/qc/report' }
    ]
  },
  {
    title: '基础数据',
    icon: Files,
    color: '#e6a23c',
    links: [
      { label: '检验项目', path: '/base/panel' },
      { label: '检验指标', path: '/base/test-item' },
      { label: '检验试管', path: '/base/test-tube' },
      { label: '标本类型', path: '/base/specimen-type' },
      { label: '采集方式', path: '/base/collection-method' },
      { label: '仪器字典', path: '/base/instrument' }
    ]
  },
  {
    title: '仪器管理',
    icon: Monitor,
    color: '#9c27b0',
    links: [
      { label: '仪器状态', path: '/instrument/status' },
      { label: '仪器配置', path: '/instrument/config' },
      { label: '校准记录', path: '/instrument/calibration' },
      { label: '维修记录', path: '/instrument/repair' },
      { label: '维护记录', path: '/instrument/maintenance' },
      { label: '通讯日志', path: '/instrument/log' }
    ]
  },
  {
    title: '试剂管理',
    icon: Box,
    color: '#3f51b5',
    links: [
      { label: '库存看板', path: '/reagent/dashboard' },
      { label: '试剂库存', path: '/reagent/stock' },
      { label: '入库验收', path: '/reagent/inbound' },
      { label: '消耗登记', path: '/reagent/consumption' },
      { label: '采购申请', path: '/reagent/purchase-apply' },
      { label: '库存盘点', path: '/reagent/check' }
    ]
  },
  {
    title: '监控告警',
    icon: AlarmClock,
    color: '#f56c6c',
    links: [
      { label: 'TAT规则设置', path: '/monitor/tat-rule' },
      { label: 'TAT业务监控', path: '/monitor/tat-monitor' },
      { label: '传染病项目配置', path: '/monitor/infectious' },
      { label: '消息中心', path: '/monitor/message' }
    ]
  },
  {
    title: '统计分析',
    icon: DataAnalysis,
    color: '#009688',
    links: [
      { label: '工作量统计', path: '/stats/workload' },
      { label: '危急值统计', path: '/stats/critical' },
      { label: '趋势分析', path: '/stats/trend' },
      { label: '阳性率统计', path: '/stats/positive-rate' },
      { label: '收费统计', path: '/stats/charge' }
    ]
  },
  {
    title: '人事管理',
    icon: User,
    color: '#795548',
    links: [
      { label: '员工档案', path: '/hr/staff' },
      { label: '排班管理', path: '/hr/schedule' },
      { label: '资质证书', path: '/hr/certificate' }
    ]
  },
  {
    title: '系统管理',
    icon: Setting,
    color: '#607d8b',
    links: [
      { label: '用户管理', path: '/system/user' },
      { label: '角色管理', path: '/system/role' },
      { label: '科室管理', path: '/system/department' },
      { label: '系统参数', path: '/system/config' },
      { label: '操作日志', path: '/system/log' }
    ]
  },
  {
    title: '微生物',
    icon: Connection,
    color: '#ff9800',
    links: [
      { label: '微生物报告', path: '/report/micro' },
      { label: '细菌字典', path: '/micro/bacteria' },
      { label: '抗生素字典', path: '/micro/antibiotic' },
      { label: '耐药预警', path: '/micro/alert' }
    ]
  }
]

function typeLabel(type?: string) {
  return MESSAGE_TYPE_MAP[type || ''] || '通知'
}

function tagType(type?: string): 'danger' | 'warning' | 'info' {
  if (type === 'CRITICAL') return 'danger'
  if (type === 'INFECTIOUS') return 'warning'
  return 'info'
}

function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

function lastNDays(n: number): string[] {
  const days: string[] = []
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    days.push(d.toISOString().slice(0, 10))
  }
  return days
}

async function loadTrend() {
  try {
    const days = lastNDays(7)
    const res = (await getWorkloadByDay({ startDate: days[0], endDate: days[6] })) || []
    const map = new Map<string, number>()
    res.forEach((r: any) => map.set(r.day, Number(r.count)))
    trendData.value = days.map(day => ({ day, count: map.get(day) || 0 }))
  } catch (e) {
    // ignore
  }
}

async function loadMessages() {
  msgLoading.value = true
  try {
    const res = await getMessagePage({ pageNum: 1, pageSize: 6 })
    messages.value = res.records || []
    unreadCount.value = res.records?.filter(m => m.isRead === 0).length || 0
  } catch (e) {
    // ignore
  } finally {
    msgLoading.value = false
  }
}

async function loadAll() {
  loading.value = true
  try {
    const [w, c, t] = await Promise.all([
      getWorkloadStats(),
      getCriticalStats(),
      getTatMonitorStats()
    ])
    Object.assign(workload, {
      total: Number(w?.total || 0),
      today: Number(w?.today || 0),
      urgent: Number(w?.urgent || 0),
      reported: Number(w?.reported || 0)
    })
    Object.assign(critical, {
      total: Number(c?.total || 0),
      confirmed: Number(c?.confirmed || 0),
      confirmRate: Number(c?.confirmRate || 0)
    })
    tatStats.value = t || {}
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

let timer: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  loadAll()
  loadTrend()
  loadMessages()
  timer = setInterval(loadAll, 120000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.home-dashboard {
  padding: 16px;

  .welcome-card {
    margin-bottom: 16px;

    .welcome {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .welcome-title {
        font-size: 20px;
        font-weight: 700;
        color: #303133;
      }

      .welcome-sub {
        font-size: 13px;
        color: #909399;
        margin-top: 4px;
      }
    }
  }

  .stat-row {
    margin-bottom: 16px;

    .stat-card {
      cursor: pointer;
      transition: transform 0.2s, box-shadow 0.2s;

      &:hover {
        transform: translateY(-3px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      }

      .stat-content {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 8px 0;

        .stat-icon {
          font-size: 30px;
          margin-bottom: 8px;
        }

        .stat-value {
          font-size: 24px;
          font-weight: 700;
          color: #303133;
          line-height: 1.2;
        }

        .stat-label {
          font-size: 13px;
          color: #909399;
          margin-top: 6px;
        }
      }
    }
  }

  .middle-row {
    margin-bottom: 16px;

    .chart {
      height: 300px;
    }

    .card-title {
      display: flex;
      align-items: center;
      font-size: 15px;
      font-weight: 600;
      color: #303133;
    }

    .msg-list {
      height: 300px;
      overflow-y: auto;

      .msg-empty {
        text-align: center;
        color: #909399;
        padding: 60px 0;
        font-size: 13px;
      }

      .msg-item {
        display: flex;
        align-items: flex-start;
        gap: 8px;
        padding: 10px 4px;
        border-bottom: 1px solid #f0f0f0;
        cursor: pointer;

        &:hover {
          background: #f5f7fa;
        }

        .msg-text {
          flex: 1;
          min-width: 0;

          .msg-title {
            font-size: 13px;
            color: #303133;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
          }

          .msg-time {
            font-size: 12px;
            color: #909399;
            margin-top: 2px;
          }
        }
      }
    }
  }

  .nav-card {
    .card-title {
      display: flex;
      align-items: center;
      font-size: 15px;
      font-weight: 600;
      color: #303133;
    }

    .nav-group {
      margin-bottom: 16px;

      .nav-group-title {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 14px;
        font-weight: 600;
        color: #303133;
        padding-bottom: 8px;
        border-bottom: 1px solid #f0f0f0;
        margin-bottom: 4px;
      }

      .nav-links {
        display: flex;
        flex-wrap: wrap;

        .nav-link {
          display: flex;
          align-items: center;
          justify-content: space-between;
          width: calc(50% - 8px);
          margin: 4px;
          padding: 8px 10px;
          border-radius: 6px;
          background: #f5f7fa;
          cursor: pointer;
          transition: background 0.2s, color 0.2s;

          &:hover {
            background: #ecf5ff;
            color: #1a73e8;

            .nav-link-arrow {
              color: #1a73e8;
            }
          }

          .nav-link-label {
            font-size: 13px;
          }

          .nav-link-arrow {
            font-size: 12px;
            color: #c0c4cc;
          }
        }
      }
    }
  }
}
</style>