<template>
  <div class="permission-page">
    <!-- 顶部操作栏 -->
    <el-card class="search-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="page-title">权限管理</span>
          <span class="page-desc">维护系统菜单/按钮/接口权限树</span>
        </div>
        <div class="toolbar-right">
          <el-input
            v-model="filterText"
            placeholder="搜索权限名称"
            clearable
            style="width: 200px"
            :prefix-icon="Search"
          />
          <el-button type="primary" plain :icon="Refresh" @click="fetchTree">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="handleAddRoot">新增根权限</el-button>
          <el-button :icon="Sort" @click="toggleExpandAll">{{ expandAll ? '全部折叠' : '全部展开' }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- 权限树表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-if="refreshTable"
        v-loading="loading"
        :data="treeData"
        :tree-props="treeProps"
        row-key="id"
        border
        stripe
        :default-expand-all="expandAll"
        style="width: 100%"
      >
        <el-table-column prop="permName" label="权限名称" min-width="200" />
        <el-table-column prop="permCode" label="权限编码" min-width="180" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="PERM_TYPE_MAP[row.permType]?.type || 'info'" size="small">
              {{ PERM_TYPE_MAP[row.permType]?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.path || '-' }}</template>
        </el-table-column>
        <el-table-column prop="component" label="组件" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.component || '-' }}</template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="90" align="center">
          <template #default="{ row }">{{ row.icon || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleAddChild(row)">新增子项</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑权限' : '新增权限'" width="600px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="上级权限">
              <el-tree-select
                v-model="editForm.parentId"
                :data="parentTreeOptions"
                :props="treeProps"
                node-key="id"
                check-strictly
                clearable
                placeholder="不选则为根权限"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权限类型" prop="permType">
              <el-select v-model="editForm.permType" style="width: 100%">
                <el-option v-for="(v, k) in PERM_TYPE_MAP" :key="k" :label="v.label" :value="Number(k)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权限名称" prop="permName">
              <el-input v-model="editForm.permName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权限编码" prop="permCode">
              <el-input v-model="editForm.permCode" :disabled="!!editForm.id" placeholder="如 system:user:list" />
            </el-form-item>
          </el-col>
          <el-col v-if="editForm.permType === 1" :span="12">
            <el-form-item label="路由路径">
              <el-input v-model="editForm.path" placeholder="如 /system/user" />
            </el-form-item>
          </el-col>
          <el-col v-if="editForm.permType === 1" :span="12">
            <el-form-item label="组件">
              <el-input v-model="editForm.component" placeholder="如 system/user/index" />
            </el-form-item>
          </el-col>
          <el-col v-if="editForm.permType === 1" :span="12">
            <el-form-item label="图标">
              <el-input v-model="editForm.icon" placeholder="Element Plus 图标名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="editForm.sortOrder" :min="0" controls-position="right" style="width: 100%" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Sort } from '@element-plus/icons-vue'
import {
  getPermissionTree,
  savePermission,
  deletePermission,
  PERM_TYPE_MAP,
  type SysPermission
} from '@/api/system'

const loading = ref(false)
const treeData = ref<SysPermission[]>([])
const treeProps = { label: 'permName', children: 'children' }
const expandAll = ref(true)
const refreshTable = ref(true)
const filterText = ref('')

// 新增/编辑
const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<SysPermission>({
  id: 0,
  parentId: 0,
  permCode: '',
  permName: '',
  permType: 1,
  path: '',
  component: '',
  icon: '',
  sortOrder: 0,
  status: 1
})
const formRules: FormRules = {
  permName: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  permCode: [{ required: true, message: '请输入权限编码', trigger: 'blur' }],
  permType: [{ required: true, message: '请选择权限类型', trigger: 'change' }]
}

// 父权限下拉数据(带根节点占位)
const parentTreeOptions = ref<SysPermission[]>([])

async function fetchTree() {
  loading.value = true
  try {
    const res = await getPermissionTree()
    treeData.value = res || []
    parentTreeOptions.value = [{ id: 0, parentId: -1, permCode: 'root', permName: '根权限', permType: 1, sortOrder: 0, status: 1, children: res || [] } as SysPermission]
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

async function toggleExpandAll() {
  expandAll.value = !expandAll.value
  // 通过重建表格触发 default-expand-all 更新
  refreshTable.value = false
  await nextTick()
  refreshTable.value = true
}

function handleAddRoot() {
  Object.assign(editForm, {
    id: 0, parentId: 0, permCode: '', permName: '', permType: 1,
    path: '', component: '', icon: '', sortOrder: 0, status: 1
  })
  formDialogVisible.value = true
}

function handleAddChild(row: SysPermission) {
  Object.assign(editForm, {
    id: 0, parentId: row.id, permCode: '', permName: '', permType: row.permType,
    path: '', component: '', icon: '', sortOrder: 0, status: 1
  })
  formDialogVisible.value = true
}

function handleEdit(row: SysPermission) {
  Object.assign(editForm, row)
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await savePermission({ ...editForm })
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

async function handleDelete(row: SysPermission) {
  try {
    await ElMessageBox.confirm(`确认删除权限 ${row.permName} 吗?删除后子权限将一并移除。`, '提示', { type: 'warning' })
    await deletePermission(row.id)
    ElMessage.success('删除成功')
    fetchTree()
  } catch (e) {
    // 取消或失败
  }
}

// 搜索过滤(前端过滤树)
watch(filterText, (val) => {
  if (!val) {
    fetchTree()
    return
  }
  // 简单按名称过滤: 保留匹配节点及其父链
  const keyword = val.toLowerCase()
  function filter(nodes: SysPermission[]): SysPermission[] {
    const result: SysPermission[] = []
    for (const node of nodes) {
      const children = node.children ? filter(node.children) : []
      if (node.permName.toLowerCase().includes(keyword) || children.length) {
        result.push({ ...node, children })
      }
    }
    return result
  }
  treeData.value = filter(treeData.value)
})

onMounted(() => {
  fetchTree()
})
</script>

<style scoped lang="scss">
.permission-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    .toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 12px;

      .toolbar-left {
        display: flex;
        align-items: baseline;
        gap: 12px;

        .page-title {
          font-size: 18px;
          font-weight: 600;
          color: var(--el-text-color-primary);
        }

        .page-desc {
          font-size: 13px;
          color: var(--el-text-color-secondary);
        }
      }

      .toolbar-right {
        display: flex;
        align-items: center;
        gap: 8px;
      }
    }
  }

  .table-card {
    .pagination-wrapper {
      display: flex;
      justify-content: flex-end;
      margin-top: 16px;
    }
  }
}
</style>
