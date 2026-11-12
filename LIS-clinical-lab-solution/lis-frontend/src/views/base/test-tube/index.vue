<template>
  <div class="test-tube-page">
    <el-card shadow="never">
      <!-- 查询 -->
      <el-form :inline="true" @submit.prevent="resetAndFetch">
        <el-form-item label="关键字">
          <el-input
            v-model="queryParams.keyword"
            placeholder="编码/名称"
            clearable
            style="width: 200px"
            @keyup.enter="resetAndFetch"
          />
        </el-form-item>
        <el-form-item label="管类型">
          <el-select v-model="queryParams.tubeType" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in tubeTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="resetAndFetch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleAdd">新增检验试管</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="tubeCode" label="管编码" width="140" />
        <el-table-column prop="tubeName" label="管名称" width="140" />
        <el-table-column label="管颜色" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :color="colorMap[row.tubeColor]"
              size="small"
              style="color: #fff; border-color: transparent"
            >
              {{ row.tubeColor }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tubeType" label="管类型" width="100" align="center" />
        <el-table-column prop="volume" label="容量" width="100" align="center" />
        <el-table-column prop="additive" label="抗凝剂/添加剂" min-width="160" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该检验试管?" @confirm="handleDelete(row)">
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

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editForm.id ? '编辑检验试管' : '新增检验试管'"
      width="520px"
    >
      <el-form ref="formRef" :model="editForm" :rules="rules" label-width="100px">
        <el-form-item label="管编码" prop="tubeCode">
          <el-input v-model="editForm.tubeCode" placeholder="如: RED_TUBE" :disabled="!!editForm.id" />
        </el-form-item>
        <el-form-item label="管名称" prop="tubeName">
          <el-input v-model="editForm.tubeName" placeholder="如: 红头管" />
        </el-form-item>
        <el-form-item label="管颜色" prop="tubeColor">
          <el-select v-model="editForm.tubeColor" placeholder="请选择管颜色" style="width: 100%">
            <el-option v-for="c in colorOptions" :key="c" :label="c" :value="c">
              <span class="color-dot" :style="{ background: colorMap[c] }"></span>
              <span style="margin-left: 8px">{{ c }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="管类型" prop="tubeType">
          <el-select v-model="editForm.tubeType" placeholder="请选择管类型" style="width: 100%">
            <el-option v-for="t in tubeTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="容量">
          <el-input v-model="editForm.volume" placeholder="如: 5ml" />
        </el-form-item>
        <el-form-item label="抗凝剂">
          <el-input v-model="editForm.additive" placeholder="如: EDTA-K2" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import {
  getTestTubeList,
  saveTestTube,
  deleteTestTube,
  type TestTube,
  type TestTubeQuery
} from '@/api/base'

const tubeTypeOptions = ['采血管', '尿杯', '粪盒', '培养管']
const colorOptions = ['红', '紫', '蓝', '绿', '黑', '黄', '灰']
const colorMap: Record<string, string> = {
  '红': '#F56C6C',
  '紫': '#9C27B0',
  '蓝': '#1A73E8',
  '绿': '#67C23A',
  '黑': '#303133',
  '黄': '#E6A23C',
  '灰': '#909399'
}

const loading = ref(false)
const saving = ref(false)
const tableData = ref<TestTube[]>([])
const total = ref(0)
const queryParams = reactive<TestTubeQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  tubeType: '',
  status: undefined
})

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<TestTube>({
  id: 0,
  tubeCode: '',
  tubeName: '',
  tubeColor: '',
  tubeType: '',
  volume: '',
  additive: '',
  sortOrder: 0,
  status: 1,
  remark: ''
})

const rules = {
  tubeCode: [{ required: true, message: '请输入管编码', trigger: 'blur' }],
  tubeName: [{ required: true, message: '请输入管名称', trigger: 'blur' }],
  tubeColor: [{ required: true, message: '请选择管颜色', trigger: 'change' }],
  tubeType: [{ required: true, message: '请选择管类型', trigger: 'change' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getTestTubeList(queryParams)
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
  queryParams.keyword = ''
  queryParams.tubeType = ''
  queryParams.status = undefined
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

function handleAdd() {
  Object.assign(editForm, {
    id: 0,
    tubeCode: '',
    tubeName: '',
    tubeColor: '',
    tubeType: '',
    volume: '',
    additive: '',
    sortOrder: 0,
    status: 1,
    remark: ''
  })
  dialogVisible.value = true
}

function handleEdit(row: TestTube) {
  Object.assign(editForm, row)
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await saveTestTube({ ...editForm })
      ElMessage.success(editForm.id ? '更新成功' : '新增成功')
      dialogVisible.value = false
      fetchList()
    } catch (e) {
      // ignore
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row: TestTube) {
  try {
    await deleteTestTube(row.id)
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
.test-tube-page {
  padding: 16px;

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  .color-dot {
    display: inline-block;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    vertical-align: middle;
  }
}
</style>
