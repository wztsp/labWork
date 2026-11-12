<template>
  <div class="sidebar-wrapper">
    <div class="logo">
      <span v-if="!collapsed" class="logo-text">LIS 实验室系统</span>
      <span v-else class="logo-mini">LIS</span>
    </div>
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#002140"
        text-color="#bfcbd9"
        active-text-color="#fff"
        router
      >
        <el-menu-item index="/home">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>

        <el-sub-menu index="sample">
          <template #title>
            <el-icon><Cpu /></el-icon>
            <span>标本管理</span>
          </template>
          <el-menu-item index="/sample/apply">新建检验申请</el-menu-item>
          <el-menu-item index="/sample/barcode">标本采集</el-menu-item>
          <el-menu-item index="/sample/pending">待采集申请</el-menu-item>
          <el-menu-item index="/sample/deliver">标本送检</el-menu-item>
          <el-menu-item index="/sample/sign-off">标本签收</el-menu-item>
          <el-menu-item index="/sample/tracking">标本追踪</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="report">
          <template #title>
            <el-icon><Document /></el-icon>
            <span>报告管理</span>
          </template>
          <el-menu-item index="/report/technician">技师工作站</el-menu-item>
          <el-menu-item index="/report/critical">危急值处理</el-menu-item>
          <el-menu-item index="/report/query">报告查询</el-menu-item>
          <el-menu-item index="/report/designer">报告模板设计</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="qc">
          <template #title>
            <el-icon><TrendCharts /></el-icon>
            <span>质量控制</span>
          </template>
          <el-menu-item index="/qc/daily">室内质控</el-menu-item>
          <el-menu-item index="/qc/occ">失控报告管理</el-menu-item>
          <el-menu-item index="/qc/lot">质控基础数据</el-menu-item>
          <el-menu-item index="/qc/rule-config">规则配置</el-menu-item>
          <el-menu-item index="/qc/eqa">室间质控</el-menu-item>
          <el-menu-item index="/qc/report">质控月报</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="instrument">
          <template #title>
            <el-icon><Monitor /></el-icon>
            <span>仪器管理</span>
          </template>
          <el-menu-item index="/instrument/status">仪器状态</el-menu-item>
          <el-menu-item index="/instrument/config">仪器配置</el-menu-item>
          <el-menu-item index="/instrument/calibration">校准记录</el-menu-item>
          <el-menu-item index="/instrument/repair">维修记录</el-menu-item>
          <el-menu-item index="/instrument/log">通讯日志</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="base">
          <template #title>
            <el-icon><Files /></el-icon>
            <span>基础数据</span>
          </template>
          <el-menu-item index="/base/panel">检验项目</el-menu-item>
          <el-menu-item index="/base/test-item">检验指标</el-menu-item>
          <el-menu-item index="/base/test-tube">检验试管</el-menu-item>
          <el-menu-item index="/base/specimen-type">标本类型</el-menu-item>
          <el-menu-item index="/base/collection-method">采集方式</el-menu-item>
          <el-menu-item index="/base/instrument">仪器字典</el-menu-item>
          <el-menu-item index="/base/ref-range">参考范围</el-menu-item>
          <el-menu-item index="/base/barcode-rule">条码规则</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="stats">
          <template #title>
            <el-icon><DataAnalysis /></el-icon>
            <span>统计分析</span>
          </template>
          <el-menu-item index="/stats/workload">工作量统计</el-menu-item>
          <el-menu-item index="/stats/critical">危急值统计</el-menu-item>
          <el-menu-item index="/stats/trend">趋势分析</el-menu-item>
          <el-menu-item index="/stats/positive-rate">阳性率统计</el-menu-item>
          <el-menu-item index="/stats/charge">收费统计</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="canSystem" index="system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/user">用户管理</el-menu-item>
          <el-menu-item index="/system/role">角色管理</el-menu-item>
          <el-menu-item index="/system/permission">权限管理</el-menu-item>
          <el-menu-item index="/system/log">操作日志</el-menu-item>
          <el-menu-item index="/system/his-log">HIS接口日志</el-menu-item>
          <el-menu-item index="/system/department">科室管理</el-menu-item>
          <el-menu-item index="/system/config">系统参数</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="hr">
          <template #title>
            <el-icon><User /></el-icon>
            <span>人事管理</span>
          </template>
          <el-menu-item index="/hr/staff">员工档案</el-menu-item>
          <el-menu-item index="/hr/schedule">排班管理</el-menu-item>
          <el-menu-item index="/hr/certificate">资质证书</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="reagent">
          <template #title>
            <el-icon><Box /></el-icon>
            <span>试剂管理</span>
          </template>
          <el-menu-item index="/reagent/dashboard">库存看板</el-menu-item>
          <el-menu-item index="/reagent/list">试剂字典</el-menu-item>
          <el-menu-item index="/reagent/stock">试剂库存</el-menu-item>
          <el-menu-item index="/reagent/inbound">入库验收</el-menu-item>
          <el-menu-item index="/reagent/purchase-apply">采购申请</el-menu-item>
          <el-menu-item index="/reagent/purchase-order">采购订单</el-menu-item>
          <el-menu-item index="/reagent/consumption">消耗登记</el-menu-item>
          <el-menu-item index="/reagent/requisition">请领业务单</el-menu-item>
          <el-menu-item index="/reagent/check">库存盘点</el-menu-item>
          <el-menu-item index="/reagent/cost">成本核算</el-menu-item>
          <el-menu-item index="/reagent/record">出入库记录</el-menu-item>
          <el-menu-item index="/reagent/supplier">供应商管理</el-menu-item>
          <el-menu-item index="/reagent/thirdparty/config">三方卫材对接</el-menu-item>
          <el-menu-item index="/reagent/thirdparty/product-map">卫材商品映射</el-menu-item>
          <el-menu-item index="/reagent/thirdparty/log">卫材接口日志</el-menu-item>
          <el-menu-item index="/reagent/supplier-evaluate">供应商评价</el-menu-item>
          <el-menu-item index="/reagent/category">分类管理</el-menu-item>
          <el-menu-item index="/reagent/warehouse">仓库管理</el-menu-item>
          <el-menu-item index="/reagent/budget">预算管理</el-menu-item>
          <el-menu-item index="/reagent/scrap-disposal">环保处置台账</el-menu-item>
          <el-menu-item index="/reagent/report">试剂报表</el-menu-item>
          <el-menu-item index="/reagent/print-template">打印模板管理</el-menu-item>
          <el-menu-item index="/reagent/mobile">移动扫码作业</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="micro">
          <template #title>
            <el-icon><Connection /></el-icon>
            <span>微生物</span>
          </template>
          <el-menu-item index="/report/micro">微生物报告</el-menu-item>
          <el-menu-item index="/micro/patient-data-by-barcode">培养基条码生成</el-menu-item>
          <el-menu-item index="/micro/bacteria">细菌字典</el-menu-item>
          <el-menu-item index="/micro/antibiotic">抗生素字典</el-menu-item>
          <el-menu-item index="/micro/antibiotic-template">抗生素模板</el-menu-item>
          <el-menu-item index="/micro/resistance">耐药规则</el-menu-item>
          <el-menu-item index="/micro/alert">耐药预警</el-menu-item>
          <el-menu-item index="/micro/instrument-import">仪器导入</el-menu-item>
          <el-menu-item index="/micro/epidemiology">流行病学统计</el-menu-item>
          <el-menu-item index="/micro/qc">微生物室内质控</el-menu-item>
          <el-menu-item index="/micro/medium-type">培养基类型</el-menu-item>
          <el-menu-item index="/micro/culture-plan">培养方案配置</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="monitor">
          <template #title>
            <el-icon><AlarmClock /></el-icon>
            <span>监控告警</span>
          </template>
          <el-menu-item index="/monitor/tat-rule">TAT规则设置</el-menu-item>
          <el-menu-item index="/monitor/tat-monitor">TAT业务监控</el-menu-item>
          <el-menu-item index="/monitor/infectious">传染病项目配置</el-menu-item>
          <el-menu-item index="/monitor/message">消息中心</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import {
  Cpu,
  Document,
  TrendCharts,
  Monitor,
  Files,
  DataAnalysis,
  Setting,
  User,
  Box,
  Connection,
  AlarmClock,
  HomeFilled
} from '@element-plus/icons-vue'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const collapsed = computed(() => appStore.sidebarCollapsed)
const activeMenu = computed(() => route.path)
// 仅拥有系统管理相关权限(或管理员)的用户才显示"系统管理"菜单, 避免无权限用户点击后 403
const canSystem = computed(() =>
  userStore.userId === 1 ||
  userStore.hasAnyPermission(['system', 'system:user', 'system:role', 'system:perm', 'system:log', 'system:dept', 'system:config', 'system:backup', 'system:dict'])
)
</script>

<style scoped lang="scss">
.sidebar-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #001120;
  color: #fff;
  font-weight: 600;
  border-bottom: 1px solid #001a33;

  .logo-text {
    font-size: 16px;
    white-space: nowrap;
  }

  .logo-mini {
    font-size: 18px;
    font-weight: 700;
  }
}

.el-scrollbar {
  flex: 1;
  background: #002140;
}

:deep(.el-menu) {
  border-right: none;
}

/* 激活菜单项医疗蓝高亮 */
:deep(.el-menu-item.is-active) {
  background: #1a73e8;
  color: #fff;
}
:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: #0a3158;
}
</style>
