<template>
  <el-dialog
    :model-value="modelValue"
    title="无主标本管理（仪器结果认领）"
    width="980px"
    top="6vh"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="load"
    @closed="reset"
  >
    <div class="unmatched-filter">
      <el-radio-group v-model="status" size="small" @change="load">
        <el-radio-button :value="0">待处理</el-radio-button>
        <el-radio-button :value="1">已匹配</el-radio-button>
        <el-radio-button :value="2">已作废</el-radio-button>
        <el-radio-button :value="undefined">全部</el-radio-button>
      </el-radio-group>
      <div style="flex: 1"></div>
      <el-button size="small" :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="tableData" border size="small" style="width: 100%">
      <el-table-column type="index" label="序" width="46" align="center" />
      <el-table-column prop="instrumentName" label="仪器" width="120" show-overflow-tooltip />
      <el-table-column label="无主条码/样本" width="120">
        <template #default="{ row }">{{ row.sampleNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="指标" min-width="130">
        <template #default="{ row }">{{ row.testName || row.testCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="结果值" width="110">
        <template #default="{ row }">
          <el-input v-if="row.status === 0" v-model="row.resultValue" size="small" placeholder="可手改" @change="onValueChange(row)" />
          <span v-else>{{ row.resultValue }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" width="60" align="center" />
      <el-table-column prop="refRange" label="参考范围" width="110" show-overflow-tooltip />
      <el-table-column label="异常" width="60" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.abnormalFlag" size="small" type="danger">{{ row.abnormalFlag }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="84" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="接收时间" width="150" align="center">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button type="success" link size="small" @click="handleMatch(row)">匹配</el-button>
            <el-button type="danger" link size="small" @click="handleDiscard(row)">作废</el-button>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        small
        @size-change="load"
        @current-change="load"
      />
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  getUnmatchedPage,
  updateUnmatchedResult,
  matchUnmatchedResult,
  discardUnmatchedResult,
  type InstrumentUnmatchedResult
} from '@/api/instrument'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const status = ref<number | undefined>(0)
const loading = ref(false)
const tableData = ref<InstrumentUnmatchedResult[]>([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

function load() {
  loading.value = true
  getUnmatchedPage({ pageNum: pageNum.value, pageSize: pageSize.value, status: status.value })
    .then((res) => {
      tableData.value = res.records || []
      total.value = Number(res.total || 0)
      if (pageNum.value > 1 && tableData.value.length === 0) {
        pageNum.value = 1
        load()
      }
    })
    .catch(() => ElMessage.error('加载无主标本失败'))
    .finally(() => (loading.value = false))
}

function reset() {
  status.value = 0
  pageNum.value = 1
  tableData.value = []
  total.value = 0
}

function onValueChange(row: InstrumentUnmatchedResult) {
  updateUnmatchedResult(row.id, { resultValue: row.resultValue })
    .then(() => ElMessage.success('结果值已保存'))
    .catch(() => ElMessage.error('保存失败'))
}

async function handleMatch(row: InstrumentUnmatchedResult) {
  try {
    const { value } = await ElMessageBox.prompt('请输入要匹配的业务标本条码/样本号（将写回正式结果）', '匹配标本', {
      confirmButtonText: '匹配',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '条码不能为空'
    })
    await matchUnmatchedResult(row.id, value.trim())
    ElMessage.success('已匹配到业务标本并落库')
    load()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function handleDiscard(row: InstrumentUnmatchedResult) {
  try {
    await ElMessageBox.confirm('确认作废该无主标本结果？', '提示', { type: 'warning' })
    await discardUnmatchedResult(row.id)
    ElMessage.success('已作废')
    load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '作废失败')
  }
}

function statusText(s?: number): string {
  return s === 1 ? '已匹配' : s === 2 ? '已作废' : '待处理'
}
function statusType(s?: number): 'warning' | 'success' | 'info' {
  return s === 1 ? 'success' : s === 2 ? 'info' : 'warning'
}
function formatTime(t?: string): string {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
}
</script>

<style scoped lang="scss">
.unmatched-filter {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>