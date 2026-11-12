import { createRouter, createWebHistory } from 'vue-router'

/** 试剂管理独立版路由：仅包含 reagent 模块 22 个页面 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/reagent/dashboard' },
    { path: '/reagent/dashboard', name: 'ReagentDashboard', component: () => import('@/views/reagent/dashboard/index.vue'), meta: { title: '库存看板' } },
    { path: '/reagent/list', name: 'ReagentList', component: () => import('@/views/reagent/list/index.vue'), meta: { title: '试剂字典' } },
    { path: '/reagent/stock', name: 'ReagentStock', component: () => import('@/views/reagent/stock/index.vue'), meta: { title: '试剂库存' } },
    { path: '/reagent/record', name: 'ReagentRecord', component: () => import('@/views/reagent/record/index.vue'), meta: { title: '出入库记录' } },
    { path: '/reagent/inbound', name: 'ReagentInbound', component: () => import('@/views/reagent/inbound/index.vue'), meta: { title: '入库验收' } },
    { path: '/reagent/consumption', name: 'ReagentConsumption', component: () => import('@/views/reagent/consumption/index.vue'), meta: { title: '消耗登记' } },
    { path: '/reagent/check', name: 'ReagentCheck', component: () => import('@/views/reagent/check/index.vue'), meta: { title: '库存盘点' } },
    { path: '/reagent/cost', name: 'ReagentCost', component: () => import('@/views/reagent/cost/index.vue'), meta: { title: '成本核算' } },
    { path: '/reagent/purchase-apply', name: 'ReagentPurchaseApply', component: () => import('@/views/reagent/purchaseApply/index.vue'), meta: { title: '采购申请' } },
    { path: '/reagent/purchase-order', name: 'ReagentPurchaseOrder', component: () => import('@/views/reagent/purchaseOrder/index.vue'), meta: { title: '采购订单' } },
    { path: '/reagent/requisition', name: 'ReagentRequisition', component: () => import('@/views/reagent/requisition/index.vue'), meta: { title: '请领业务单' } },
    { path: '/reagent/supplier', name: 'ReagentSupplier', component: () => import('@/views/reagent/supplier/index.vue'), meta: { title: '供应商管理' } },
    { path: '/reagent/category', name: 'ReagentCategory', component: () => import('@/views/reagent/category/index.vue'), meta: { title: '分类管理' } },
    { path: '/reagent/warehouse', name: 'ReagentWarehouse', component: () => import('@/views/reagent/warehouse/index.vue'), meta: { title: '仓库管理' } },
    { path: '/reagent/budget', name: 'ReagentBudget', component: () => import('@/views/reagent/budget/index.vue'), meta: { title: '预算管理' } },
    { path: '/reagent/supplier-evaluate', name: 'ReagentSupplierEvaluate', component: () => import('@/views/reagent/supplierEvaluate/index.vue'), meta: { title: '供应商评价' } },
    { path: '/reagent/scrap-disposal', name: 'ReagentScrapDisposal', component: () => import('@/views/reagent/scrapDisposal/index.vue'), meta: { title: '环保处置台账' } },
    { path: '/reagent/report', name: 'ReagentReport', component: () => import('@/views/reagent/report/index.vue'), meta: { title: '试剂报表' } },
    { path: '/reagent/print-template', name: 'ReagentPrintTemplate', component: () => import('@/views/reagent/printTemplate/index.vue'), meta: { title: '打印模板管理' } },
    { path: '/reagent/thirdparty/config', name: 'ReagentThirdPartyConfig', component: () => import('@/views/reagent/thirdparty/config.vue'), meta: { title: '三方卫材对接' } },
    { path: '/reagent/thirdparty/product-map', name: 'ReagentThirdPartyProductMap', component: () => import('@/views/reagent/thirdparty/productMap.vue'), meta: { title: '卫材商品映射' } },
    { path: '/reagent/thirdparty/log', name: 'ReagentThirdPartyLog', component: () => import('@/views/reagent/thirdparty/log.vue'), meta: { title: '卫材接口日志' } },
    { path: '/reagent/config', name: 'ReagentConfig', component: () => import('@/views/reagent/config/index.vue'), meta: { title: '参数配置' } },
    { path: '/reagent/opening', name: 'ReagentOpening', component: () => import('@/views/reagent/opening/index.vue'), meta: { title: '开封管理' } },
    { path: '/reagent/batches', name: 'ReagentBatches', component: () => import('@/views/reagent/batches/index.vue'), meta: { title: '批次库存' } },
    { path: '/reagent/invoice', name: 'ReagentInvoice', component: () => import('@/views/reagent/invoice/index.vue'), meta: { title: '发票台账' } },
    { path: '/reagent/mobile', name: 'ReagentMobile', component: () => import('@/views/reagent/mobile/index.vue'), meta: { title: '移动扫码作业' } },
    { path: '/reagent/mobile/scan-check', name: 'ReagentMobileScanCheck', component: () => import('@/views/reagent/mobile/scan-check.vue'), meta: { title: '移动扫码盘点' } },
    { path: '/reagent/mobile/scan-pick', name: 'ReagentMobileScanPick', component: () => import('@/views/reagent/mobile/scan-pick.vue'), meta: { title: '移动扫码领用' } },
    { path: '/reagent/mobile/stock-query', name: 'ReagentMobileStockQuery', component: () => import('@/views/reagent/mobile/stock-query.vue'), meta: { title: '移动库存查询' } },
    { path: '/:pathMatch(.*)*', redirect: '/reagent/dashboard' }
  ]
})

router.afterEach((to) => {
  const title = (to.meta?.title as string) || '试剂管理系统'
  document.title = `${title} - 试剂管理系统`
})

export default router
