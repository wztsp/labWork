<template>
  <div class="role-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="角色编码">
          <el-input v-model="searchForm.roleCode" placeholder="请输入" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="searchForm.roleName" placeholder="请输入" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增角色</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="roleCode" label="角色编码" width="160" />
        <el-table-column prop="roleName" label="角色名称" width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleAssignPerms(row)">分配权限</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑角色' : '新增角色'" width="520px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="editForm.roleCode" :disabled="!!editForm.id" placeholder="如 ROLE_ADMIN" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="editForm.roleName" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限弹窗 -->
    <el-dialog v-model="permDialogVisible" title="分配权限" width="560px">
      <div class="perm-assign-tip">为角色 <b>{{ currentRow?.roleName }}</b> 分配权限：</div>
      <div class="perm-tree-wrapper">
        <el-tree
          ref="permTreeRef"
          :data="permTreeData"
          :props="treeProps"
          node-key="id"
          show-checkbox
          check-strictly
          default-expand-all
        />
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permLoading" @click="handleSavePerms">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElTree, type FormInstance, type FormRules } from 'element-plus'
import {
  getRoleList,
  saveRole,
  deleteRole,
  assignPermissions,
  getPermissionTree,
  type SysRole,
  type SysPermission,
  type RoleQuery
} from '@/api/system'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref<SysRole[]>([])
const searchForm = reactive<RoleQuery>({
  roleCode: '',
  roleName: '',
  status: undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

// 新增/编辑
const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<SysRole>({
  id: 0,
  roleCode: '',
  roleName: '',
  description: '',
  status: 1
})
const formRules: FormRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

// 分配权限
const permDialogVisible = ref(false)
const permLoading = ref(false)
const permTreeRef = ref<InstanceType<typeof ElTree>>()
const permTreeData = ref<SysPermission[]>([])
const currentRow = ref<SysRole | null>(null)
const treeProps = { label: 'permName', children: 'children' }

async function fetchList() {
  loading.value = true
  try {
    const res = await getRoleList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      roleCode: searchForm.roleCode || undefined,
      roleName: searchForm.roleName || undefined,
      status: searchForm.status
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function fetchPermTree() {
  if (permTreeData.value.length) return
  try {
    permTreeData.value = await getPermissionTree()
  } catch (e) {
    // ignore
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.roleCode = ''
  searchForm.roleName = ''
  searchForm.status = undefined
  handleSearch()
}

function handleSizeChange(size: number) {
  pagination.pageSize = size
  pagination.pageNum = 1
  fetchList()
}

function handlePageChange(page: number) {
  pagination.pageNum = page
  fetchList()
}

function handleAdd() {
  Object.assign(editForm, { id: 0, roleCode: '', roleName: '', description: '', status: 1 })
  formDialogVisible.value = true
}

function handleEdit(row: SysRole) {
  Object.assign(editForm, row)
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveRole({ ...editForm })
      ElMessage.success('保存成功')
      formDialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      formLoading.value = false
    }
  })
}

async function handleDelete(row: SysRole) {
  try {
    await ElMessageBox.confirm(`确认删除角色 ${row.roleName} 吗?`, '提示', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

async function handleAssignPerms(row: SysRole) {
  currentRow.value = row
  await fetchPermTree()
  permDialogVisible.value = true
  // 等待树渲染后回显已选权限
  setTimeout(() => {
    permTreeRef.value?.setCheckedKeys(row.permissionIds || [], false)
  }, 50)
}

async function handleSavePerms() {
  if (!currentRow.value || !permTreeRef.value) return
  permLoading.value = true
  try {
    const checkedKeys = permTreeRef.value.getCheckedKeys(false) as number[]
    await assignPermissions({ roleId: currentRow.value.id, permissionIds: checkedKeys })
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    permLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.role-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .table-card {
    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .perm-assign-tip {
    margin-bottom: 12px;
    color: var(--el-text-color-secondary);
  }

  .perm-tree-wrapper {
    max-height: 420px;
    overflow-y: auto;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 4px;
    padding: 8px 12px;
  }
}
</style>
