<template>
  <div class="user-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="searchForm.realName" placeholder="请输入" clearable style="width: 140px" @keyup.enter="handleSearch" />
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
          <el-button type="primary" @click="handleAdd">新增用户</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column prop="departmentName" label="科室" width="120">
          <template #default="{ row }">
            <span v-if="row.departmentName">{{ row.departmentName }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="140">
          <template #default="{ row }">
            <el-tag v-for="r in (row.roleNames || [])" :key="r" size="small" class="role-tag">{{ r }}</el-tag>
            <span v-if="!row.roleNames?.length" class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="(val: boolean) => handleToggleStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="160" align="center">
          <template #default="{ row }">{{ formatDateTime(row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleAssignRoles(row)">分配角色</el-button>
            <el-button type="warning" link size="small" @click="handleResetPwd(row)">重置密码</el-button>
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑用户' : '新增用户'" width="600px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="editForm.username" :disabled="!!editForm.id" placeholder="登录账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="editForm.realName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="editForm.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="editForm.email" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="科室">
              <el-select
                v-model="editForm.departmentId"
                placeholder="请选择科室"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="d in flatDepartments"
                  :key="d.id"
                  :label="d.label"
                  :value="d.id"
                  :disabled="d.status !== 1"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="480px">
      <div class="role-assign-tip">为用户 <b>{{ currentRow?.username }}</b> 分配角色：</div>
      <el-checkbox-group v-model="selectedRoleIds">
        <div v-for="r in allRoles" :key="r.id" class="role-checkbox">
          <el-checkbox :value="r.id" :label="r.roleName" />
          <span class="role-code">{{ r.roleCode }}</span>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleLoading" @click="handleSaveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getUserList,
  saveUser,
  deleteUser,
  resetPassword,
  assignRoles,
  toggleUserStatus,
  getAllRoles,
  getAllDepartments,
  type SysUser,
  type UserQuery,
  type RoleOption,
  type DepartmentOption
} from '@/api/system'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref<SysUser[]>([])
const allRoles = ref<RoleOption[]>([])
const allDepartments = ref<DepartmentOption[]>([])
const searchForm = reactive<UserQuery>({
  username: '',
  realName: '',
  status: undefined
})

interface FlatDept {
  id: number
  label: string
  status: number
}

/** 树形科室扁平化为下拉选项, 子级前面加缩进空格 */
function flattenDepts(list: DepartmentOption[] | undefined, prefix = '', out: FlatDept[] = []): FlatDept[] {
  if (!list) return out
  for (const d of list) {
    out.push({ id: d.id, label: prefix + d.deptName, status: d.status })
    if (d.children && d.children.length) {
      flattenDepts(d.children, prefix + '　', out)
    }
  }
  return out
}

const flatDepartments = computed<FlatDept[]>(() => flattenDepts(allDepartments.value))

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

// 新增/编辑
const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<SysUser>({
  id: 0,
  username: '',
  realName: '',
  phone: '',
  email: '',
  departmentId: undefined,
  departmentName: '',
  status: 1
})
const formRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

// 分配角色
const roleDialogVisible = ref(false)
const roleLoading = ref(false)
const currentRow = ref<SysUser | null>(null)
const selectedRoleIds = ref<number[]>([])

async function fetchList() {
  loading.value = true
  try {
    const res = await getUserList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      username: searchForm.username || undefined,
      realName: searchForm.realName || undefined,
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

async function fetchRoles() {
  try {
    allRoles.value = await getAllRoles()
  } catch (e) {
    // ignore
  }
}

async function fetchDepartments() {
  try {
    allDepartments.value = await getAllDepartments()
  } catch (e) {
    // ignore
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.username = ''
  searchForm.realName = ''
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
  Object.assign(editForm, {
    id: 0, username: '', realName: '', phone: '', email: '',
    departmentId: undefined, departmentName: '', status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: SysUser) {
  Object.assign(editForm, row)
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveUser({ ...editForm })
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

async function handleDelete(row: SysUser) {
  try {
    await ElMessageBox.confirm(`确认删除用户 ${row.username} 吗?`, '提示', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

async function handleToggleStatus(row: SysUser, val: boolean) {
  const newStatus = val ? 1 : 0
  try {
    await toggleUserStatus(row.id, newStatus)
    row.status = newStatus
    ElMessage.success('状态已更新')
  } catch (e) {
    // ignore
  }
}

async function handleResetPwd(row: SysUser) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入用户 ${row.username} 的新密码 (至少 6 位)`,
      '重置密码',
      {
        type: 'warning',
        inputType: 'password',
        inputValidator: (v: string) => (v && v.length >= 6) || '密码至少 6 位'
      }
    )
    await resetPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch (e) {
    // 取消或失败
  }
}

function handleAssignRoles(row: SysUser) {
  currentRow.value = row
  selectedRoleIds.value = [...(row.roleIds || [])]
  roleDialogVisible.value = true
}

async function handleSaveRoles() {
  if (!currentRow.value) return
  roleLoading.value = true
  try {
    await assignRoles({ userId: currentRow.value.id, roleIds: selectedRoleIds.value })
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    roleLoading.value = false
  }
}

onMounted(() => {
  fetchList()
  fetchRoles()
  fetchDepartments()
})
</script>

<style scoped lang="scss">
.user-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .table-card {
    .role-tag {
      margin-right: 4px;
      margin-bottom: 2px;
    }

    .text-muted {
      color: var(--el-text-color-placeholder);
    }

    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }

  .role-assign-tip {
    margin-bottom: 16px;
    color: var(--el-text-color-secondary);
  }

  .role-checkbox {
    display: flex;
    align-items: center;
    padding: 6px 0;

    .role-code {
      margin-left: 8px;
      font-size: 12px;
      color: var(--el-text-color-placeholder);
    }
  }
}
</style>
