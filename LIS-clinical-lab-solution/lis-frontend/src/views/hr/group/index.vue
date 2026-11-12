<template>
  <div class="group-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="小组名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入小组名称/编码"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd(null)">新增小组</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="filteredTree"
        row-key="id"
        border
        stripe
        default-expand-all
        :tree-props="{ children: 'children' }"
        style="width: 100%"
      >
        <el-table-column prop="groupName" label="小组名称" min-width="200" />
        <el-table-column prop="groupCode" label="小组编码" width="160" align="center" />
        <el-table-column label="负责人" width="140" align="center">
          <template #default="{ row }">
            {{ row.leaderName || (row.leaderId ? `员工${row.leaderId}` : '-') }}
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" width="160" align="center">
          <template #default="{ row }">{{ row.location || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleAdd(row)">新增子级</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑小组' : '新增小组'" width="600px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="小组编码" prop="groupCode">
              <el-input v-model="editForm.groupCode" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="小组名称" prop="groupName">
              <el-input v-model="editForm.groupName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上级小组">
              <el-tree-select
                v-model="editForm.parentId"
                :data="parentOptions"
                :props="{ label: 'groupName', value: 'id', children: 'children' }"
                check-strictly
                clearable
                placeholder="顶级小组"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人">
              <el-select
                v-model="editForm.leaderId"
                filterable
                clearable
                placeholder="请选择员工"
                style="width: 100%"
                @change="onLeaderChange"
              >
                <el-option
                  v-for="s in staffOptions"
                  :key="s.id"
                  :label="`${s.name} (${s.staffNo})`"
                  :value="s.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="位置">
              <el-input v-model="editForm.location" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="editForm.sortOrder" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="editForm.status" style="width: 100%">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getGroupTree,
  saveGroup,
  deleteGroup,
  getAllActiveStaff,
  type LabGroup,
  type Staff
} from '@/api/hr'

const loading = ref(false)
const treeData = ref<LabGroup[]>([])
const staffOptions = ref<Staff[]>([])
const searchForm = reactive({ keyword: '' })

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<LabGroup>({
  id: 0,
  groupCode: '',
  groupName: '',
  parentId: undefined,
  leaderId: undefined,
  leaderName: '',
  location: '',
  sortOrder: 0,
  status: 1
})
const formRules: FormRules = {
  groupCode: [{ required: true, message: '请输入小组编码', trigger: 'blur' }],
  groupName: [{ required: true, message: '请输入小组名称', trigger: 'blur' }]
}

// 关键字过滤(对树状数据递归过滤)
const filteredTree = computed<LabGroup[]>(() => {
  const kw = searchForm.keyword.trim()
  if (!kw) return treeData.value
  const filter = (nodes: LabGroup[]): LabGroup[] => {
    const result: LabGroup[] = []
    for (const node of nodes) {
      const matched =
        (node.groupName || '').includes(kw) || (node.groupCode || '').includes(kw)
      const children = node.children ? filter(node.children) : []
      if (matched || children.length > 0) {
        result.push({ ...node, children })
      }
    }
    return result
  }
  return filter(treeData.value)
})

// 父级下拉数据 (排除自身及其子孙)
const parentOptions = computed<LabGroup[]>(() => {
  if (!editForm.id) return treeData.value
  const exclude = (nodes: LabGroup[]): LabGroup[] => {
    const result: LabGroup[] = []
    for (const node of nodes) {
      if (node.id === editForm.id) continue
      const children = node.children ? exclude(node.children) : []
      result.push({ ...node, children })
    }
    return result
  }
  return exclude(treeData.value)
})

async function fetchTree() {
  loading.value = true
  try {
    treeData.value = (await getGroupTree()) || []
  } catch (e) {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

async function loadStaffOptions() {
  try {
    staffOptions.value = await getAllActiveStaff()
  } catch (e) {
    staffOptions.value = []
  }
}

function handleSearch() {
  // 前端过滤, 无需重新请求
}

function handleReset() {
  searchForm.keyword = ''
}

function onLeaderChange(staffId: number | undefined) {
  const s = staffOptions.value.find((x) => x.id === staffId)
  editForm.leaderName = s?.name || ''
}

function handleAdd(parent: LabGroup | null) {
  Object.assign(editForm, {
    id: 0,
    groupCode: '',
    groupName: '',
    parentId: parent?.id || undefined,
    leaderId: undefined,
    leaderName: '',
    location: '',
    sortOrder: 0,
    status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: LabGroup) {
  Object.assign(editForm, {
    id: row.id,
    groupCode: row.groupCode || '',
    groupName: row.groupName || '',
    parentId: row.parentId || undefined,
    leaderId: row.leaderId || undefined,
    leaderName: row.leaderName || '',
    location: row.location || '',
    sortOrder: row.sortOrder ?? 0,
    status: row.status ?? 1
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveGroup({ ...editForm })
      ElMessage.success('保存成功')
      formDialogVisible.value = false
      fetchTree()
    } catch (e) {
      // ignore
    } finally {
      formLoading.value = false
    }
  })
}

async function handleDelete(row: LabGroup) {
  try {
    await ElMessageBox.confirm(`确认删除小组 "${row.groupName}" 吗?`, '提示', { type: 'warning' })
    await deleteGroup(row.id)
    ElMessage.success('删除成功')
    fetchTree()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  loadStaffOptions()
  fetchTree()
})
</script>

<style scoped lang="scss">
.group-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }
}
</style>
