<template>
  <div class="mobile-page mobile-hub">
    <header class="mobile-header">
      <h3>移动扫码作业</h3>
    </header>
    <div class="hub-grid">
      <router-link to="/reagent/mobile/scan-check" class="hub-card check">
        <div class="hub-icon">📋</div>
        <div class="hub-title">扫码盘点</div>
        <div class="hub-desc">扫描条码录入实盘数量</div>
      </router-link>
      <router-link to="/reagent/mobile/scan-pick" class="hub-card pick">
        <div class="hub-icon">📤</div>
        <div class="hub-title">扫码领用</div>
        <div class="hub-desc">扫描条码领用出库</div>
      </router-link>
      <router-link to="/reagent/mobile/stock-query" class="hub-card query">
        <div class="hub-icon">🔍</div>
        <div class="hub-title">库存查询</div>
        <div class="hub-desc">扫描条码查询库存</div>
      </router-link>
    </div>
    <el-alert type="info" :closable="false" show-icon class="tip">
      <template #title>
        提示：断网时提交的数据会暂存本地，恢复网络后可在对应页面一键上传。
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped lang="scss">
.mobile-page {
  max-width: 480px;
  margin: 0 auto;
  padding: 16px;
  min-height: 100vh;
  background: #f5f7fa;
}

.mobile-header {
  margin-bottom: 16px;

  h3 {
    margin: 0;
    font-size: 18px;
  }
}

.hub-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.hub-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  text-align: center;
  text-decoration: none;
  color: #303133;
  transition: transform 0.15s;

  &:active {
    transform: scale(0.97);
  }

  .hub-icon {
    font-size: 32px;
    margin-bottom: 8px;
  }

  .hub-title {
    font-size: 17px;
    font-weight: 600;
  }

  .hub-desc {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }

  &.check {
    border-top: 3px solid #1a73e8;
  }

  &.pick {
    border-top: 3px solid #67c23a;
  }

  &.query {
    border-top: 3px solid #e6a23c;
  }
}

.tip {
  margin-top: 8px;
}
</style>