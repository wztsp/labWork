<template>
  <div class="category-page">
    <!-- 工具栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item>
          <el-button type="primary" @click="handleAdd()">新增顶级分类</el-button>
          <el-button type="success" plain @click="handleRefresh">刷新树</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 分类树表格 -->
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
        <el-table-column prop="categoryCode" label="分类编码" width="160" />
        <el-table-column prop="categoryName" label="分类名称" min-width="180" />
        <el-table-column label="上级分类" width="120" align="center">
          <template #default="{ row }">{{ parentNameOf(row) }}</template>
        </el-table-column>
        <el-table-column label="危险品" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hazardCategory === 1 ? 'danger' : 'info'" size="small">
              {{ row.hazardCategory === 1 ? '危险品类' : '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleAdd(row)">新增下级</el-button>
            <el-button type="warning" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑分类' : '新增分类'" width="560px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="110px">
        <el-form-item label="上级分类">
          <el-select v-model="editForm.parentId" clearable placeholder="留空为顶级" style="width: 100%">
            <el-option
              v-for="node in flatTree"
              :key="node.id!"
              :label="`${'　'.repeat(node.levelNo || 1)}${node.categoryName}`"
              :value="node.id!"
              :disabled="editForm.id === node.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分类编码" prop="categoryCode">
          <el-input v-model="editForm.categoryCode" placeholder="请输入唯一编码" />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="editForm.categoryName" placeholder="请输入" />
        </el-form-item>
        <el-form-item label="危险品标志">
          <el-radio-group v-model="editForm.hazardCategory">
            <el-radio :label="0">普通</el-radio>
            <el-radio :label="1">危险品类</el-radio>
          </el-radio-group>
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
  getCategoryTree,
  createCategory,
  updateCategory,
  deleteCategory,
  type ReagentCategory
} from '@/api/reagent'

const loading = ref(false)
const treeData = ref<ReagentCategory[]>([])

// 扁平化树（用于上级分类下拉）
const flatTree = computed<ReagentCategory[]>(() => {
  const result: ReagentCategory[] = []
  const walk = (nodes: ReagentCategory[]) => {
    nodes.forEach((n) => {
      result.push(n)
      if (n.children && n.children.length > 0) walk(n.children)
    })
  }
  walk(treeData.value)
  return result
})

// 上级分类名称：parentId=0 或无父级显示 '-'
function parentNameOf(row: ReagentCategory): string {
  if (!row.parentId || row.parentId === 0) return '-'
  return flatTree.value.find((n) => n.id === row.parentId)?.categoryName || '-'
}

async function fetchTree() {
  loading.value = true
  try {
    treeData.value = await getCategoryTree()
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
const editForm = reactive<ReagentCategory>({
  id: undefined,
  parentId: 0,
  categoryCode: '',
  categoryName: '',
  hazardCategory: 0,
  sortOrder: 0,
  status: 1,
  remark: ''
})
const formRules: FormRules = {
  categoryCode: [{ required: true, message: '请输入分类编码', trigger: 'blur' }],
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

function handleAdd(parent?: ReagentCategory) {
  Object.assign(editForm, {
    id: undefined,
    parentId: parent?.id ?? 0,
    categoryCode: '',
    categoryName: '',
    hazardCategory: parent?.hazardCategory ?? 0,
    sortOrder: 0,
    status: 1,
    remark: ''
  })
  formDialogVisible.value = true
}

function handleEdit(row: ReagentCategory) {
  Object.assign(editForm, {
    id: row.id,
    parentId: row.parentId ?? 0,
    categoryCode: row.categoryCode ?? '',
    categoryName: row.categoryName ?? '',
    hazardCategory: row.hazardCategory ?? 0,
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
        await updateCategory(payload)
      } else {
        await createCategory(payload)
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

async function handleDelete(row: ReagentCategory) {
  try {
    await ElMessageBox.confirm(`确认删除分类 "${row.categoryName}" 吗? 存在子分类或被试剂引用时将拒绝删除。`, '提示', { type: 'warning' })
    await deleteCategory(row.id!)
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
.category-page {
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
}
</style>
