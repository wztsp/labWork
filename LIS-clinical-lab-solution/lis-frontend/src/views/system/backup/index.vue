<template>
  <div class="backup-page">
    <!-- 顶部操作栏 -->
    <el-card class="action-card" shadow="never">
      <div class="action-bar">
        <div class="action-left">
          <el-button type="primary" :loading="backupLoading" @click="handleManualBackup">
            <el-icon><Upload /></el-icon>
            <span>立即备份</span>
          </el-button>
          <el-button :loading="loading" @click="fetchList">
            <el-icon><Refresh /></el-icon>
            <span>刷新</span>
          </el-button>
        </div>
        <div class="action-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>系统每日凌晨 2 点自动备份, 每周日凌晨 3 点全量备份, 备份文件保留 90 天</span>
        </div>
      </div>
    </el-card>

    <!-- 备份概览 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon stat-blue">
            <el-icon><Files /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">备份文件总数</div>
            <div class="stat-value">{{ backupList.length }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon stat-green">
            <el-icon><Coin /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">占用空间</div>
            <div class="stat-value">{{ formatSize(totalSize) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon stat-orange">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-label">最近备份时间</div>
            <div class="stat-value stat-time">{{ latestBackupTime || '-' }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 备份文件列表 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <span>备份文件列表</span>
      </template>
      <el-table v-loading="loading" :data="backupList" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="fileName" label="文件名" min-width="280" />
        <el-table-column label="文件大小" width="140" align="center">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="备份类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="backupTypeTag(row.fileName)" size="small">
              {{ backupTypeLabel(row.fileName) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastModified" label="备份时间" width="180" align="center" />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :loading="downloading === row.fileName" @click="handleDownload(row)">
              下载
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && backupList.length === 0" description="暂无备份文件, 点击「立即备份」生成第一份备份" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Refresh, InfoFilled, Files, Coin, Clock } from '@element-plus/icons-vue'
import {
  listBackups,
  manualBackup,
  deleteBackup,
  downloadBackup,
  type BackupFile
} from '@/api/system'

const loading = ref(false)
const backupLoading = ref(false)
const downloading = ref('')
const backupList = ref<BackupFile[]>([])

const totalSize = computed(() => backupList.value.reduce((sum, f) => sum + (f.size || 0), 0))

const latestBackupTime = computed(() => {
  if (backupList.value.length === 0) return ''
  // 列表已按时间倒序, 取第一个
  return backupList.value[0].lastModified || ''
})

function formatSize(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let idx = 0
  let size = bytes
  while (size >= 1024 && idx < units.length - 1) {
    size /= 1024
    idx++
  }
  return `${size.toFixed(idx === 0 ? 0 : 2)} ${units[idx]}`
}

function backupTypeLabel(fileName: string): string {
  if (fileName.includes('_weekly_')) return '每周备份'
  if (fileName.includes('_daily_')) return '每日备份'
  if (fileName.includes('_manual_')) return '手动备份'
  return '备份'
}

function backupTypeTag(fileName: string): 'primary' | 'success' | 'warning' {
  if (fileName.includes('_weekly_')) return 'warning'
  if (fileName.includes('_manual_')) return 'success'
  return 'primary'
}

async function fetchList() {
  loading.value = true
  try {
    backupList.value = (await listBackups()) || []
  } catch (e) {
    backupList.value = []
  } finally {
    loading.value = false
  }
}

async function handleManualBackup() {
  try {
    await ElMessageBox.confirm('确认立即执行全量数据库备份? 该操作可能需要数秒至数分钟', '确认备份', {
      type: 'info',
      confirmButtonText: '开始备份',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return // 用户取消
  }
  backupLoading.value = true
  try {
    const result = await manualBackup()
    if (result.success) {
      ElMessage.success(`备份成功: ${result.fileName}`)
    } else {
      ElMessage.warning(result.message || '备份未成功, 请检查 pg_dump 配置')
    }
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    backupLoading.value = false
  }
}

async function handleDownload(row: BackupFile) {
  downloading.value = row.fileName
  try {
    await downloadBackup(row.fileName)
    ElMessage.success('下载已开始')
  } catch (e: any) {
    ElMessage.error(e?.message || '下载失败')
  } finally {
    downloading.value = ''
  }
}

async function handleDelete(row: BackupFile) {
  try {
    await ElMessageBox.confirm(`确认删除备份文件 "${row.fileName}" 吗? 此操作不可恢复`, '危险操作', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
    await deleteBackup(row.fileName)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.backup-page {
  padding: 16px;

  .action-card {
    margin-bottom: 16px;

    .action-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 12px;

      .action-left {
        display: flex;
        gap: 12px;
      }

      .action-tip {
        display: flex;
        align-items: center;
        gap: 6px;
        color: var(--el-text-color-secondary);
        font-size: 13px;
      }
    }
  }

  .stats-row {
    margin-bottom: 16px;

    .stat-card {
      margin-bottom: 12px;

      :deep(.el-card__body) {
        display: flex;
        align-items: center;
        gap: 16px;
        padding: 20px;
      }

      .stat-icon {
        width: 56px;
        height: 56px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 28px;
        color: #fff;
        flex-shrink: 0;

        &.stat-blue { background: var(--el-color-primary); }
        &.stat-green { background: var(--el-color-success); }
        &.stat-orange { background: var(--el-color-warning); }
      }

      .stat-info {
        flex: 1;
        min-width: 0;

        .stat-label {
          font-size: 14px;
          color: var(--el-text-color-secondary);
          margin-bottom: 6px;
        }

        .stat-value {
          font-size: 26px;
          font-weight: bold;
          color: var(--el-text-color-primary);
          line-height: 1.2;

          &.stat-time {
            font-size: 16px;
          }
        }
      }
    }
  }
}
</style>
