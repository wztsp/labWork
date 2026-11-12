<template>
  <el-container class="rs-layout">
    <el-aside width="212px" class="rs-aside">
      <div class="rs-logo">
        <span class="rs-logo__mark">R</span>
        <span class="rs-logo__text">试剂管理系统</span>
      </div>
      <el-menu :default-active="activePath" router class="rs-menu" background-color="#0f172a" text-color="#cbd5e1" active-text-color="#38bdf8">
        <el-menu-item index="/reagent/dashboard">
          <el-icon><DataBoard /></el-icon><span>库存看板</span>
        </el-menu-item>

        <el-menu-item-group title="主数据">
          <el-menu-item index="/reagent/list"><el-icon><Box /></el-icon><span>试剂字典</span></el-menu-item>
          <el-menu-item index="/reagent/category"><el-icon><Menu /></el-icon><span>分类管理</span></el-menu-item>
          <el-menu-item index="/reagent/supplier"><el-icon><OfficeBuilding /></el-icon><span>供应商管理</span></el-menu-item>
          <el-menu-item index="/reagent/supplier-evaluate"><el-icon><Star /></el-icon><span>供应商评价</span></el-menu-item>
          <el-menu-item index="/reagent/warehouse"><el-icon><House /></el-icon><span>仓库管理</span></el-menu-item>
          <el-menu-item index="/reagent/budget"><el-icon><Wallet /></el-icon><span>预算管理</span></el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="采购与出入库">
          <el-menu-item index="/reagent/purchase-apply"><el-icon><Document /></el-icon><span>采购申请</span></el-menu-item>
          <el-menu-item index="/reagent/purchase-order"><el-icon><Tickets /></el-icon><span>采购订单</span></el-menu-item>
          <el-menu-item index="/reagent/inbound"><el-icon><Download /></el-icon><span>入库验收</span></el-menu-item>
          <el-menu-item index="/reagent/stock"><el-icon><Files /></el-icon><span>试剂库存</span></el-menu-item>
          <el-menu-item index="/reagent/batches"><el-icon><Grid /></el-icon><span>批次库存</span></el-menu-item>
          <el-menu-item index="/reagent/consumption"><el-icon><Minus /></el-icon><span>消耗登记</span></el-menu-item>
          <el-menu-item index="/reagent/requisition"><el-icon><Switch /></el-icon><span>请领业务单</span></el-menu-item>
          <el-menu-item index="/reagent/record"><el-icon><List /></el-icon><span>出入库记录</span></el-menu-item>
          <el-menu-item index="/reagent/check"><el-icon><Checked /></el-icon><span>库存盘点</span></el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="三方对接">
          <el-menu-item index="/reagent/thirdparty/config"><el-icon><Connection /></el-icon><span>三方卫材对接</span></el-menu-item>
          <el-menu-item index="/reagent/thirdparty/product-map"><el-icon><Link /></el-icon><span>卫材商品映射</span></el-menu-item>
          <el-menu-item index="/reagent/thirdparty/log"><el-icon><List /></el-icon><span>卫材接口日志</span></el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="使用">
          <el-menu-item index="/reagent/opening"><el-icon><Timer /></el-icon><span>开封管理</span></el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="分析与处置">
          <el-menu-item index="/reagent/report"><el-icon><TrendCharts /></el-icon><span>试剂报表</span></el-menu-item>
          <el-menu-item index="/reagent/cost"><el-icon><Money /></el-icon><span>成本核算</span></el-menu-item>
          <el-menu-item index="/reagent/invoice"><el-icon><DocumentCopy /></el-icon><span>发票台账</span></el-menu-item>
          <el-menu-item index="/reagent/scrap-disposal"><el-icon><Delete /></el-icon><span>环保处置台账</span></el-menu-item>
          <el-menu-item index="/reagent/mobile"><el-icon><Iphone /></el-icon><span>移动扫码作业</span></el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="系统维护">
          <el-menu-item index="/reagent/config"><el-icon><Setting /></el-icon><span>参数配置</span></el-menu-item>
          <el-menu-item index="/reagent/print-template"><el-icon><Printer /></el-icon><span>打印模板管理</span></el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="rs-header">
        <div class="rs-header__title">{{ pageTitle }}</div>
        <div class="rs-header__right">
          <el-tag type="success" effect="plain" size="small">独立部署</el-tag>
          <span class="rs-operator">操作员：{{ userStore.realName }}</span>
          <el-button link type="primary" size="small" @click="editOperator">修改</el-button>
        </div>
      </el-header>
      <el-main class="rs-main">
        <router-view v-slot="{ Component }">
          <keep-alive :max="6">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

const activePath = computed(() => route.path)
const pageTitle = computed(() => (route.meta?.title as string) || '试剂管理')

async function editOperator() {
  try {
    const { value } = await ElMessageBox.prompt('请输入当前操作员姓名（用于单据记录）', '操作员设置', {
      inputValue: userStore.realName,
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputPattern: /^.{1,20}$/,
      inputErrorMessage: '请输入 1-20 个字符'
    })
    userStore.setOperator(value)
    ElMessage.success('操作员已更新')
  } catch {
    /* 取消 */
  }
}
</script>

<style scoped>
.rs-layout { height: 100vh; background: #f1f5f9; }
.rs-aside { background: #0f172a; overflow-y: auto; display: flex; flex-direction: column; }
.rs-logo { display: flex; align-items: center; gap: 10px; padding: 18px 16px; border-bottom: 1px solid #1e293b; }
.rs-logo__mark {
  width: 28px; height: 28px; border-radius: 8px; background: linear-gradient(135deg, #38bdf8, #6366f1);
  color: #fff; font-weight: 700; display: flex; align-items: center; justify-content: center; font-size: 15px;
}
.rs-logo__text { color: #e2e8f0; font-size: 15px; font-weight: 600; letter-spacing: .5px; }
.rs-menu { border-right: none; flex: 1; }
.rs-menu :deep(.el-menu-item-group__title) { color: #64748b; font-size: 12px; padding-left: 18px; }
.rs-menu :deep(.el-menu-item) { height: 40px; line-height: 40px; font-size: 13px; }
.rs-header {
  background: #fff; border-bottom: 1px solid #e2e8f0; display: flex; align-items: center;
  justify-content: space-between; height: 52px;
}
.rs-header__title { font-size: 15px; font-weight: 600; color: #0f172a; }
.rs-header__right { display: flex; align-items: center; gap: 12px; }
.rs-operator { font-size: 13px; color: #475569; }
.rs-main { padding: 14px; overflow-y: auto; }
</style>
