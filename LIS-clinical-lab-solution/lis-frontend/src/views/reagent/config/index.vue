<template>
  <div class="reagent-config-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="参数">
          <el-input
            v-model="keyword"
            placeholder="参数键 / 名称 / 备注"
            clearable
            style="width: 240px"
            @keyup.enter="fetchList"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="fetchList">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="参数说明"
        description="采购订单审批、报损审批、高库存预警等开关均在此维护，修改后点击对应行“保存”即时生效。"
      />
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="filteredList" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="configKey" label="参数键" min-width="220" />
        <el-table-column prop="configName" label="参数名称" min-width="180">
          <template #default="{ row }">{{ row.configName || '-' }}</template>
        </el-table-column>
        <el-table-column label="参数值" min-width="260">
          <template #default="{ row }">
            <el-input v-model="editValues[row.configKey]" size="small" placeholder="请输入参数值" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              :loading="savingKeys.has(row.configKey)"
              @click="handleSave(row)"
            >保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getConfigList, updateConfigValue, type SysConfig } from '@/api/reagent'

const loading = ref(false)
const savingKeys = ref<Set<string>>(new Set())
const keyword = ref('')
const list = ref<SysConfig[]>([])
const editValues = reactive<Record<string, string>>({})

/** 关键字过滤（参数键 / 名称 / 备注） */
const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return list.value
  return list.value.filter((item) => {
    return [item.configKey, item.configName, item.remark]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(kw))
  })
})

function formatTime(time?: string): string {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

async function fetchList() {
  loading.value = true
  try {
    const rows = await getConfigList()
    list.value = rows || []
    Object.keys(editValues).forEach((key) => delete editValues[key])
    list.value.forEach((item) => {
      editValues[item.configKey] = item.configValue ?? ''
    })
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function handleReset() {
  keyword.value = ''
  fetchList()
}

async function handleSave(row: SysConfig) {
  if (!row.configKey) return
  const value = editValues[row.configKey] ?? ''
  savingKeys.value.add(row.configKey)
  try {
    await updateConfigValue(row.configKey, value)
    row.configValue = value
    ElMessage.success(`参数 ${row.configName || row.configKey} 已保存`)
  } catch (e) {
    // 错误提示由请求拦截器统一处理
  } finally {
    savingKeys.value.delete(row.configKey)
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.reagent-config-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 12px 16px;
    }

    :deep(.el-alert) {
      margin-top: 4px;
    }
  }
}
</style>
