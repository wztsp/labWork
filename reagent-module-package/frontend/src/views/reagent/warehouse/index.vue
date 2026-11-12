<template>
  <div class="warehouse-page">
    <!-- 工具栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item>
          <el-button type="primary" @click="handleAdd()">新增库房</el-button>
          <el-button type="success" plain @click="handleRefresh">刷新树</el-button>
        </el-form-item>
        <el-form-item>
          <span class="tip-text">注：默认库房为系统内置节点，不可删除。</span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 仓库树表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="treeData"
        border
        stripe
        style="width: 100%"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="whCode" label="编码" width="150" />
        <el-table-column prop="whName" label="名称" min-width="160" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="whTypeTag(row.whType)" size="small">{{ whTypeText(row.whType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="归属科室" width="120" />
        <el-table-column label="温度区间" width="140" align="center">
          <template #default="{ row }">
            {{ row.tempMin != null && row.tempMax != null ? `${row.tempMin}~${row.tempMax}℃` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="locationDesc" label="位置描述" min-width="140" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.id !== 0" type="primary" link size="small" @click="handleAdd(row)">新增下级</el-button>
            <el-button type="warning" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.id !== 0" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑仓库节点' : '新增仓库节点'" width="600px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-form-item label="上级节点">
          <el-select v-model="editForm.parentId" clearable placeholder="留空为顶级" style="width: 100%">
            <el-option
              v-for="node in flatTree"
              :key="node.id!"
              :label="`${'　'.repeat(node.levelNo || 1)}${node.whName}`"
              :value="node.id!"
              :disabled="editForm.id === node.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="节点编码" prop="whCode">
          <el-input v-model="editForm.whCode" placeholder="请输入唯一编码" />
        </el-form-item>
        <el-form-item label="节点名称" prop="whName">
          <el-input v-model="editForm.whName" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="节点类型">
          <el-select v-model="editForm.whType" style="width: 100%">
            <el-option v-for="(label, key) in WH_TYPE_MAP" :key="key" :label="label" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属科室">
          <el-input v-model="editForm.deptName" placeholder="请输入科室名称" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="温度下限(℃)">
              <el-input-number v-model="editForm.tempMin" :precision="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="温度上限(℃)">
              <el-input-number v-model="editForm.tempMax" :precision="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="位置描述">
          <el-input v-model="editForm.locationDesc" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="editForm.sortOrder" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
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
  getWarehouseTree,
  createWarehouse,
  updateWarehouse,
  deleteWarehouse,
  type ReagentWarehouse
} from '@/api/reagent'

// 仓库节点类型：1库房 2冰箱 3层架
const WH_TYPE_MAP: Record<number, string> = {
  1: '库房',
  2: '冰箱',
  3: '层架'
}

function whTypeText(t?: number): string {
  return t === undefined || t === null ? '-' : WH_TYPE_MAP[t] || `未知(${t})`
}
function whTypeTag(t?: number): 'info' | 'warning' | 'success' {
  if (t === 1) return 'warning'
  if (t === 2) return 'success'
  return 'info'
}

const loading = ref(false)
const treeData = ref<ReagentWarehouse[]>([])

const flatTree = computed<ReagentWarehouse[]>(() => {
  const result: ReagentWarehouse[] = []
  const walk = (nodes: ReagentWarehouse[]) => {
    nodes.forEach((n) => {
      result.push(n)
      if (n.children && n.children.length > 0) walk(n.children)
    })
  }
  walk(treeData.value)
  return result
})

async function fetchTree() {
  loading.value = true
  try {
    treeData.value = await getWarehouseTree()
  } catch (e) {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

function handleRefresh() {
  fetchTree()
}

// 新增/编辑
const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<ReagentWarehouse>({
  id: undefined,
  parentId: 0,
  whCode: '',
  whName: '',
  whType: 1,
  deptName: '',
  tempMin: undefined,
  tempMax: undefined,
  locationDesc: '',
  sortOrder: 0,
  status: 1,
  remark: ''
})
const formRules: FormRules = {
  whCode: [{ required: true, message: '请输入节点编码', trigger: 'blur' }],
  whName: [{ required: true, message: '请输入节点名称', trigger: 'blur' }]
}

function handleAdd(parent?: ReagentWarehouse) {
  Object.assign(editForm, {
    id: undefined,
    parentId: parent?.id ?? 0,
    whCode: '',
    whName: '',
    whType: parent?.whType === 1 ? 2 : parent?.whType === 2 ? 3 : 1,
    deptName: parent?.deptName ?? '',
    tempMin: undefined,
    tempMax: undefined,
    locationDesc: '',
    sortOrder: 0,
    status: 1,
    remark: ''
  })
  formDialogVisible.value = true
}

function handleEdit(row: ReagentWarehouse) {
  Object.assign(editForm, {
    id: row.id,
    parentId: row.parentId ?? 0,
    whCode: row.whCode ?? '',
    whName: row.whName ?? '',
    whType: row.whType ?? 1,
    deptName: row.deptName ?? '',
    tempMin: row.tempMin,
    tempMax: row.tempMax,
    locationDesc: row.locationDesc ?? '',
    sortOrder: row.sortOrder ?? 0,
    status: row.status ?? 1,
    remark: row.remark ?? ''
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      const payload = { ...editForm }
      if (editForm.id) {
        await updateWarehouse(payload)
      } else {
        await createWarehouse(payload)
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

async function handleDelete(row: ReagentWarehouse) {
  // 默认库房为系统内置哨兵节点，前端直接拦截并提示
  if (row.id === 0) {
    ElMessage.warning('默认库房为系统内置节点，不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除仓库节点 "${row.whName}" 吗? 存在子节点或余量库存时将拒绝删除。`, '提示', { type: 'warning' })
    await deleteWarehouse(row.id!)
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
.warehouse-page {
  padding: 16px;

  .search-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }

    .tip-text {
      font-size: 12px;
      color: #909399;
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
