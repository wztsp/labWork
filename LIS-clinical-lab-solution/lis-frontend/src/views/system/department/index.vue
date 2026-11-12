<template>
  <div class="department-page">
    <!-- 顶部操作栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="科室名称">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd()">新增科室</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 树形表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        default-expand-all
        style="width: 100%"
      >
        <el-table-column prop="deptCode" label="科室编码" width="160" />
        <el-table-column prop="deptName" label="科室名称" min-width="180" />
        <el-table-column prop="sortOrder" label="排序" width="100" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleAdd(row)">新增子科室</el-button>
            <el-button type="warning" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑科室' : '新增科室'" width="560px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="科室编码" prop="deptCode">
              <el-input v-model="editForm.deptCode" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="科室名称" prop="deptName">
              <el-input v-model="editForm.deptName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="父科室">
              <el-tree-select
                v-model="editForm.parentId"
                :data="parentTreeData"
                :props="{ label: 'deptName', value: 'id', children: 'children' }"
                check-strictly
                clearable
                filterable
                placeholder="无(顶级科室)"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="editForm.sortOrder" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch
                v-model="editForm.status"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="停用"
              />
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
  getDepartmentTree,
  createDepartment,
  updateDepartment,
  deleteDepartment
} from '@/api/system/department'

interface Department {
  id: number
  parentId: number
  deptCode: string
  deptName: string
  sortOrder: number
  status: number
  children?: Department[]
  hasChildren?: boolean
}

const loading = ref(false)
const tableData = ref<Department[]>([])
const searchForm = reactive({ keyword: '' })

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<Department>({
  id: 0,
  parentId: 0,
  deptCode: '',
  deptName: '',
  sortOrder: 0,
  status: 1
})
const formRules: FormRules = {
  deptCode: [{ required: true, message: '请输入科室编码', trigger: 'blur' }],
  deptName: [{ required: true, message: '请输入科室名称', trigger: 'blur' }]
}

// 父科室下拉树数据(添加顶级虚拟节点)
const parentTreeData = computed(() => {
  return [
    {
      id: 0,
      deptName: '顶级科室',
      children: tableData.value
    }
  ]
})

// 过滤树(保留父子结构)
function filterTree(nodes: Department[], keyword: string): Department[] {
  if (!keyword) return nodes
  const result: Department[] = []
  for (const node of nodes) {
    const children = node.children ? filterTree(node.children, keyword) : []
    if (node.deptName.includes(keyword) || node.deptCode.includes(keyword) || children.length) {
      result.push({ ...node, children })
    }
  }
  return result
}

async function fetchTree() {
  loading.value = true
  try {
    const res: any = await getDepartmentTree()
    tableData.value = Array.isArray(res) ? res : []
  } catch (e) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  // 本地过滤(树形数据)
  fetchTree()
}

function handleReset() {
  searchForm.keyword = ''
  fetchTree()
}

function handleAdd(row?: Department) {
  Object.assign(editForm, {
    id: 0,
    parentId: row ? row.id : 0,
    deptCode: '',
    deptName: '',
    sortOrder: 0,
    status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: Department) {
  Object.assign(editForm, {
    id: row.id,
    parentId: row.parentId ?? 0,
    deptCode: row.deptCode,
    deptName: row.deptName,
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
      const data: any = { ...editForm }
      if (editForm.id) {
        await updateDepartment(editForm.id, data)
      } else {
        // 新增时不携带 id, 交由数据库自增主键生成, 避免 id=0 触发主键冲突
        delete data.id
        await createDepartment(data)
      }
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

async function handleDelete(row: Department) {
  try {
    await ElMessageBox.confirm(`确认删除科室 "${row.deptName}" 吗?`, '提示', { type: 'warning' })
    await deleteDepartment(row.id)
    ElMessage.success('删除成功')
    fetchTree()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchTree()
})
</script>

<style scoped lang="scss">
.department-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .table-card {
    // 表格样式继承全局
  }
}
</style>
