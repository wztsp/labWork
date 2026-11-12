<template>
  <div class="message-center-page">
    <el-card shadow="never">
      <!-- 查询 -->
      <el-form :inline="true" @submit.prevent="resetAndFetch">
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" placeholder="全部" clearable style="width: 140px">
            <el-option label="危急值" value="CRITICAL" />
            <el-option label="传染病" value="INFECTIOUS" />
            <el-option label="TAT超时" value="TAT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetAndFetch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleMarkAll">全部标为已读</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="tagType(row.messageType)" size="small">
              {{ typeLabel(row.messageType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" width="160" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isRead === 1 ? 'info' : 'danger'" size="small">
              {{ row.isRead === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.isRead === 0" type="primary" link size="small" @click="handleRead(row)">
              标为已读
            </el-button>
            <el-popconfirm title="确认删除该消息?" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getMessagePage,
  markMessageRead,
  markAllMessageRead,
  deleteMessage,
  MESSAGE_TYPE_MAP,
  type Message,
  type MessageQuery
} from '@/api/message'

const loading = ref(false)
const tableData = ref<Message[]>([])
const total = ref(0)
const queryParams = reactive<MessageQuery>({
  pageNum: 1,
  pageSize: 10,
  type: ''
})

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
  return t.replace('T', ' ').slice(0, 19)
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getMessagePage({ ...queryParams, type: queryParams.type || undefined })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function resetAndFetch() {
  queryParams.pageNum = 1
  fetchList()
}

function handleReset() {
  queryParams.type = ''
  queryParams.pageNum = 1
  fetchList()
}

function handleSizeChange(size: number) {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  fetchList()
}

function handlePageChange(page: number) {
  queryParams.pageNum = page
  fetchList()
}

async function handleRead(row: Message) {
  await markMessageRead(row.id!)
  row.isRead = 1
  ElMessage.success('已标为已读')
}

async function handleMarkAll() {
  await markAllMessageRead()
  ElMessage.success('已全部标为已读')
  fetchList()
}

async function handleDelete(row: Message) {
  try {
    await deleteMessage(row.id!)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // ignore
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.message-center-page {
  padding: 16px;

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>