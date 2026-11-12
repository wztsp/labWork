import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useTagsStore, MAX_VIEWS } from '@/stores/tags'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'Login', component: () => import('@/views/login/index.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/home/index.vue'), meta: { title: '首页' } },
      { path: 'sample/barcode', name: 'SampleBarcode', component: () => import('@/views/sample/barcode/index.vue'), meta: { title: '标本采集' } },
      { path: 'sample/pending', name: 'SamplePending', component: () => import('@/views/sample/pending/index.vue'), meta: { title: '待采集申请' } },
      { path: 'sample/apply', name: 'SampleApply', component: () => import('@/views/sample/apply/index.vue'), meta: { title: '新建检验申请' } },
      { path: 'sample/deliver', name: 'SampleDeliver', component: () => import('@/views/sample/deliver/index.vue'), meta: { title: '标本送检' } },
      { path: 'sample/sign-off', name: 'SampleSignOff', component: () => import('@/views/sample/sign-off/index.vue'), meta: { title: '标本签收' } },
      { path: 'sample/tracking', name: 'SampleTracking', component: () => import('@/views/sample/tracking/index.vue'), meta: { title: '标本追踪' } },
      { path: 'report/technician', name: 'ReportTechnician', component: () => import('@/views/report/technician/index.vue'), meta: { title: '技师工作站' } },
      { path: 'report/critical', name: 'ReportCritical', component: () => import('@/views/report/critical/index.vue'), meta: { title: '危急值处理' } },
      { path: 'report/query', name: 'ReportQuery', component: () => import('@/views/report/query/index.vue'), meta: { title: '报告查询' } },
      { path: 'qc/daily', name: 'QCDaily', component: () => import('@/views/qc/daily/index.vue'), meta: { title: '室内质控' } },
      { path: 'qc/occ', name: 'QcOcc', component: () => import('@/views/qc/occ/index.vue'), meta: { title: '失控报告管理' } },
      { path: 'qc/lot', name: 'QCLot', component: () => import('@/views/qc/lot/index.vue'), meta: { title: '质控基础数据' } },
      { path: 'qc/chart', redirect: '/qc/daily' },
      { path: 'qc/rule-config', name: 'QCRuleConfig', component: () => import('@/views/qc/rule-config/index.vue'), meta: { title: '规则配置' } },
      { path: 'instrument/status', name: 'InstrumentStatus', component: () => import('@/views/instrument/status/index.vue'), meta: { title: '仪器状态' } },
      { path: 'instrument/config', name: 'InstrumentConfig', component: () => import('@/views/instrument/config/index.vue'), meta: { title: '仪器配置' } },
      { path: 'instrument/log', name: 'InstrumentLog', component: () => import('@/views/instrument/log/index.vue'), meta: { title: '通讯日志' } },
      { path: 'instrument/calibration', name: 'InstrumentCalibration', component: () => import('@/views/instrument/calibration/index.vue'), meta: { title: '校准记录' } },
      { path: 'instrument/repair', name: 'InstrumentRepair', component: () => import('@/views/instrument/repair/index.vue'), meta: { title: '维修记录' } },
      { path: 'base/panel', name: 'BasePanel', component: () => import('@/views/base/panel/index.vue'), meta: { title: '检验项目' } },
      { path: 'base/test-item', name: 'BaseTestItem', component: () => import('@/views/base/test-item/index.vue'), meta: { title: '检验指标' } },
      { path: 'base/test-tube', name: 'BaseTestTube', component: () => import('@/views/base/test-tube/index.vue'), meta: { title: '检验试管' } },
      { path: 'base/specimen-type', name: 'BaseSpecimenType', component: () => import('@/views/base/specimen-type/index.vue'), meta: { title: '标本类型' } },
      { path: 'base/collection-method', name: 'BaseCollectionMethod', component: () => import('@/views/base/collection-method/index.vue'), meta: { title: '采集方式' } },
      { path: 'base/instrument', name: 'BaseInstrument', component: () => import('@/views/base/instrument/index.vue'), meta: { title: '仪器字典' } },
      { path: 'base/ref-range', name: 'BaseRefRange', component: () => import('@/views/base/ref-range/index.vue'), meta: { title: '参考范围' } },
      { path: 'base/barcode-rule', name: 'BaseBarcodeRule', component: () => import('@/views/base/barcode-rule/index.vue'), meta: { title: '条码规则' } },
      { path: 'stats/workload', name: 'StatsWorkload', component: () => import('@/views/stats/workload/index.vue'), meta: { title: '工作量统计' } },
      { path: 'system/user', name: 'SystemUser', component: () => import('@/views/system/user/index.vue'), meta: { title: '用户管理' } },
      { path: 'system/role', name: 'SystemRole', component: () => import('@/views/system/role/index.vue'), meta: { title: '角色管理' } },
      { path: 'system/permission', name: 'SystemPermission', component: () => import('@/views/system/permission/index.vue'), meta: { title: '权限管理' } },
      { path: 'system/log', name: 'SystemLog', component: () => import('@/views/system/log/index.vue'), meta: { title: '操作日志' } },
      { path: 'system/his-log', name: 'SystemHisLog', component: () => import('@/views/system/his-log/index.vue'), meta: { title: 'HIS接口日志' } },
      { path: 'stats/critical', name: 'StatsCritical', component: () => import('@/views/stats/critical/index.vue'), meta: { title: '危急值统计' } },
      { path: 'stats/trend', name: 'StatsTrend', component: () => import('@/views/stats/trend/index.vue'), meta: { title: '趋势分析' } },
      { path: 'stats/positive-rate', name: 'StatsPositiveRate', component: () => import('@/views/stats/positive-rate/index.vue'), meta: { title: '阳性率统计' } },
      { path: 'stats/charge', name: 'StatsCharge', component: () => import('@/views/stats/charge/index.vue'), meta: { title: '收费统计' } },
      { path: 'system/department', name: 'SystemDepartment', component: () => import('@/views/system/department/index.vue'), meta: { title: '科室管理' } },
      { path: 'system/config', name: 'SystemConfig', component: () => import('@/views/system/config/index.vue'), meta: { title: '系统参数' } },
      { path: 'report/micro', name: 'ReportMicro', component: () => import('@/views/report/micro/index.vue'), meta: { title: '微生物报告' } },
      { path: 'report/designer', name: 'ReportDesigner', component: () => import('@/views/report/designer/index.vue'), meta: { title: '报告模板设计' } },
      { path: 'qc/eqa', name: 'QcEqa', component: () => import('@/views/qc/eqa/index.vue'), meta: { title: '室间质控' } },
      { path: 'qc/report', name: 'QcReport', component: () => import('@/views/qc/report/index.vue'), meta: { title: '质控月报' } },
      { path: 'hr/staff', name: 'HrStaff', component: () => import('@/views/hr/staff/index.vue'), meta: { title: '员工档案' } },
      { path: 'hr/schedule', name: 'HrSchedule', component: () => import('@/views/hr/schedule/index.vue'), meta: { title: '排班管理' } },
      { path: 'hr/certificate', name: 'HrCertificate', component: () => import('@/views/hr/certificate/index.vue'), meta: { title: '资质证书' } },
      { path: 'reagent/dashboard', name: 'ReagentDashboard', component: () => import('@/views/reagent/dashboard/index.vue'), meta: { title: '库存看板' } },
      { path: 'reagent/list', name: 'ReagentList', component: () => import('@/views/reagent/list/index.vue'), meta: { title: '试剂字典' } },
      { path: 'reagent/stock', name: 'ReagentStock', component: () => import('@/views/reagent/stock/index.vue'), meta: { title: '试剂库存' } },
      { path: 'reagent/record', name: 'ReagentRecord', component: () => import('@/views/reagent/record/index.vue'), meta: { title: '出入库记录' } },
      { path: 'reagent/inbound', name: 'ReagentInbound', component: () => import('@/views/reagent/inbound/index.vue'), meta: { title: '入库验收' } },
      { path: 'reagent/consumption', name: 'ReagentConsumption', component: () => import('@/views/reagent/consumption/index.vue'), meta: { title: '消耗登记' } },
      { path: 'micro/bacteria', name: 'MicroBacteria', component: () => import('@/views/micro/bacteria/index.vue'), meta: { title: '细菌字典' } },
      { path: 'micro/antibiotic', name: 'MicroAntibiotic', component: () => import('@/views/micro/antibiotic/index.vue'), meta: { title: '抗生素字典' } },
      { path: 'micro/antibiotic-template', name: 'MicroAntibioticTemplate', component: () => import('@/views/micro/antibiotic-template/index.vue'), meta: { title: '抗生素模板' } },
      { path: 'micro/resistance', name: 'MicroResistance', component: () => import('@/views/micro/resistance/index.vue'), meta: { title: '耐药规则' } },
      { path: 'micro/instrument-import', name: 'MicroInstrumentImport', component: () => import('@/views/micro/instrument-import/index.vue'), meta: { title: '仪器导入' } },
      { path: 'micro/expert-rule', name: 'MicroExpertRule', component: () => import('@/views/micro/expert-rule/index.vue'), meta: { title: '耐药规则说明' } },
      { path: 'micro/alert', name: 'MicroAlert', component: () => import('@/views/micro/alert/index.vue'), meta: { title: '耐药预警' } },
      { path: 'micro/epidemiology', name: 'MicroEpidemiology', component: () => import('@/views/micro/epidemiology/index.vue'), meta: { title: '流行病学统计' } },
      { path: 'micro/qc', name: 'MicroQc', component: () => import('@/views/micro/qc/index.vue'), meta: { title: '微生物室内质控' } },
      { path: 'micro/medium-type', name: 'MicroMediumType', component: () => import('@/views/micro/medium-type/index.vue'), meta: { title: '培养基类型' } },
      { path: 'micro/culture-plan', name: 'MicroCulturePlan', component: () => import('@/views/micro/culture-plan/index.vue'), meta: { title: '培养方案配置' } },
      { path: 'micro/patient-data-by-barcode', name: 'MicroPatientDataByBarcode', component: () => import('@/views/micro/patient-data-by-barcode/index.vue'), meta: { title: '培养基条码生成' } },
      { path: 'sample/receive', name: 'SampleReceive', component: () => import('@/views/sample/receive/index.vue'), meta: { title: '标本核收' } },
      { path: 'sample/batch-import', name: 'SampleBatchImport', component: () => import('@/views/sample/batch-import/index.vue'), meta: { title: '批量导入' } },
      { path: 'report/audit', name: 'ReportAudit', component: () => import('@/views/report/audit/index.vue'), meta: { title: '报告审核' } },
      { path: 'report/list', name: 'ReportList', component: () => import('@/views/report/list/index.vue'), meta: { title: '报告列表' } },
      { path: 'qc/alert', name: 'QCAlert', component: () => import('@/views/qc/alert/index.vue'), meta: { title: '质控预警' } },
      { path: 'instrument/maintenance', name: 'InstrumentMaintenance', component: () => import('@/views/instrument/maintenance/index.vue'), meta: { title: '维护记录' } },
      { path: 'instrument/plan', name: 'InstrumentPlan', component: () => import('@/views/instrument/plan/index.vue'), meta: { title: '保养计划' } },
      { path: 'reagent/check', name: 'ReagentCheck', component: () => import('@/views/reagent/check/index.vue'), meta: { title: '库存盘点' } },
      { path: 'reagent/cost', name: 'ReagentCost', component: () => import('@/views/reagent/cost/index.vue'), meta: { title: '成本核算' } },
      { path: 'reagent/purchase-apply', name: 'ReagentPurchaseApply', component: () => import('@/views/reagent/purchaseApply/index.vue'), meta: { title: '采购申请' } },
      { path: 'reagent/purchase-order', name: 'ReagentPurchaseOrder', component: () => import('@/views/reagent/purchaseOrder/index.vue'), meta: { title: '采购订单' } },
      { path: 'reagent/requisition', name: 'ReagentRequisition', component: () => import('@/views/reagent/requisition/index.vue'), meta: { title: '请领业务单' } },
      { path: 'reagent/supplier', name: 'ReagentSupplier', component: () => import('@/views/reagent/supplier/index.vue'), meta: { title: '供应商管理' } },
      { path: 'reagent/category', name: 'ReagentCategory', component: () => import('@/views/reagent/category/index.vue'), meta: { title: '分类管理' } },
      { path: 'reagent/warehouse', name: 'ReagentWarehouse', component: () => import('@/views/reagent/warehouse/index.vue'), meta: { title: '仓库管理' } },
      { path: 'reagent/budget', name: 'ReagentBudget', component: () => import('@/views/reagent/budget/index.vue'), meta: { title: '预算管理' } },
      { path: 'reagent/supplier-evaluate', name: 'ReagentSupplierEvaluate', component: () => import('@/views/reagent/supplierEvaluate/index.vue'), meta: { title: '供应商评价' } },
      { path: 'reagent/scrap-disposal', name: 'ReagentScrapDisposal', component: () => import('@/views/reagent/scrapDisposal/index.vue'), meta: { title: '环保处置台账' } },
      { path: 'reagent/report', name: 'ReagentReport', component: () => import('@/views/reagent/report/index.vue'), meta: { title: '试剂报表' } },
      { path: 'reagent/print-template', name: 'ReagentPrintTemplate', component: () => import('@/views/reagent/printTemplate/index.vue'), meta: { title: '打印模板管理' } },
      { path: 'reagent/thirdparty/config', name: 'ReagentThirdPartyConfig', component: () => import('@/views/reagent/thirdparty/config.vue'), meta: { title: '三方卫材对接' } },
      { path: 'reagent/thirdparty/product-map', name: 'ReagentThirdPartyProductMap', component: () => import('@/views/reagent/thirdparty/productMap.vue'), meta: { title: '卫材商品映射' } },
      { path: 'reagent/thirdparty/log', name: 'ReagentThirdPartyLog', component: () => import('@/views/reagent/thirdparty/log.vue'), meta: { title: '卫材接口日志' } },
      { path: 'reagent/mobile', name: 'ReagentMobile', component: () => import('@/views/reagent/mobile/index.vue'), meta: { title: '移动扫码作业' } },
      { path: 'reagent/mobile/scan-check', name: 'ReagentMobileScanCheck', component: () => import('@/views/reagent/mobile/scan-check.vue'), meta: { title: '移动扫码盘点' } },
      { path: 'reagent/mobile/scan-pick', name: 'ReagentMobileScanPick', component: () => import('@/views/reagent/mobile/scan-pick.vue'), meta: { title: '移动扫码领用' } },
      { path: 'reagent/mobile/stock-query', name: 'ReagentMobileStockQuery', component: () => import('@/views/reagent/mobile/stock-query.vue'), meta: { title: '移动库存查询' } },
      { path: 'hr/group', name: 'HrGroup', component: () => import('@/views/hr/group/index.vue'), meta: { title: '小组管理' } },
      { path: 'hr/title', name: 'HrTitle', component: () => import('@/views/hr/title/index.vue'), meta: { title: '职称管理' } },
      { path: 'hr/training', name: 'HrTraining', component: () => import('@/views/hr/training/index.vue'), meta: { title: '培训记录' } },
      { path: 'hr/authorization', name: 'HrAuthorization', component: () => import('@/views/hr/authorization/index.vue'), meta: { title: '授权管理' } },
      { path: 'hr/document', name: 'HrDocument', component: () => import('@/views/hr/document/index.vue'), meta: { title: '文档资料' } },
      { path: 'system/backup', name: 'SystemBackup', component: () => import('@/views/system/backup/index.vue'), meta: { title: '数据备份' } },
      { path: 'monitor/tat-rule', name: 'MonitorTatRule', component: () => import('@/views/monitor/TatRule/index.vue'), meta: { title: 'TAT规则设置' } },
      { path: 'monitor/tat-monitor', name: 'MonitorTatMonitor', component: () => import('@/views/monitor/TatMonitor/index.vue'), meta: { title: 'TAT业务监控' } },
      { path: 'monitor/infectious', name: 'MonitorInfectious', component: () => import('@/views/monitor/Infectious/index.vue'), meta: { title: '传染病项目配置' } },
      { path: 'monitor/message', name: 'MonitorMessage', component: () => import('@/views/monitor/Message/index.vue'), meta: { title: '消息中心' } },
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  if (to.path === '/login') {
    next()
  } else if (!userStore.token) {
    next('/login')
  } else {
    // 刷新/冷启动后加载用户信息与权限, 确保菜单等按权限正确渲染
    await userStore.ensureInfo()
    document.title = `${to.meta.title || ''} - LIS`
    // 标签页数量限制: 已达上限且目标是一个新的独立页面时, 需先关闭一个标签才能加载
    const tagsStore = useTagsStore()
    const path = to.path === '/' ? '/home' : to.path
    const isNewPage = path !== '/home' && !tagsStore.visitedViews.some((t) => t.path === path)
    if (isNewPage && tagsStore.visitedViews.length >= MAX_VIEWS) {
      ElMessage.warning(`最多同时打开 ${MAX_VIEWS} 个页面, 请先关闭一个标签页后再打开`)
      next(from.path || '/home')
      return
    }
    next()
  }
})

router.afterEach((to) => {
  const tagsStore = useTagsStore()
  tagsStore.addView(to)
})

export default router
