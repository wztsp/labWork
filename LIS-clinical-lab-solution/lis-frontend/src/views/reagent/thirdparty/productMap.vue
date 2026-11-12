<template>
  <div class="thirdparty-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="对接方">
          <el-select v-model="query.partnerCode" placeholder="请选择" style="width: 180px" @change="handleSearch">
            <el-option
              v-for="item in configList"
              :key="item.partnerCode"
              :label="item.partnerName"
              :value="item.partnerCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="对方编码/名称、本系统编码/名称"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="映射状态">
          <el-select v-model="query.mapped" placeholder="全部" clearable style="width: 130px">
            <el-option label="已映射" :value="1" />
            <el-option label="未映射" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" :loading="matching" @click="handleAutoMatch">自动匹配编码</el-button>
          <el-button type="primary" @click="handleAdd">新增映射</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="自动匹配按「对方商品编码 = 本系统试剂编码」建立映射；到货回传按此映射找到本地试剂，未映射的编码无法入库。"
      />
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="remoteCode" label="对方商品编码" width="150" />
        <el-table-column prop="remoteName" label="对方商品名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remoteSpec" label="对方规格" width="140" show-overflow-tooltip />
        <el-table-column prop="remoteUnit" label="单位" width="80" align="center" />
        <el-table-column prop="reagentCode" label="本系统试剂编码" width="140">
          <template #default="{ row }">{{ row.reagentCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="reagentName" label="本系统试剂名称" min-width="160">
          <template #default="{ row }">{{ row.reagentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="映射状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.reagentId ? 'success' : 'warning'" size="small">
              {{ row.reagentId ? '已映射' : '未映射' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑商品映射' : '新增商品映射'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="对接方" prop="partnerCode">
          <el-select v-model="form.partnerCode" style="width: 100%">
            <el-option
              v-for="item in configList"
              :key="item.partnerCode"
              :label="item.partnerName"
              :value="item.partnerCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="对方商品编码" prop="remoteCode">
          <el-input v-model="form.remoteCode" placeholder="卫材系统的商品编码" />
        </el-form-item>
        <el-form-item label="对方商品名称">
          <el-input v-model="form.remoteName" />
        </el-form-item>
        <el-form-item label="本系统试剂" prop="reagentId">
          <el-select
            v-model="form.reagentId"
            filterable
            remote
            clearable
            :remote-method="searchReagents"
            :loading="reagentLoading"
            placeholder="输入编码/名称搜索"
            style="width: 100%"
            @change="onReagentChange"
          >
            <el-option
              v-for="item in reagentOptions"
              :key="item.id"
              :label="`${item.reagentCode} ${item.reagentName}`"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
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
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getConfigList,
  getProductMapPage,
  saveProductMap,
  deleteProductMap,
  autoMatchProductMap,
  type ThirdPartyConfig,
  type ThirdPartyProductMap
} from '@/api/reagent/thirdparty'
import { getReagentPage, type Reagent } from '@/api/reagent'

const loading = ref(false)
const matching = ref(false)
const tableData = ref<ThirdPartyProductMap[]>([])
const configList = ref<ThirdPartyConfig[]>([])
const query = reactive({
  partnerCode: '',
  keyword: '',
  mapped: undefined as number | undefined
})
const pagination = reactive({ current: 1, size: 10, total: 0 })

async function fetchList() {
  loading.value = true
  try {
    const res = await getProductMapPage({
      current: pagination.current,
      size: pagination.size,
      partnerCode: query.partnerCode || undefined,
      keyword: query.keyword || undefined,
      mapped: query.mapped
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  fetchList()
}

function handleReset() {
  query.keyword = ''
  query.mapped = undefined
  handleSearch()
}

async function handleAutoMatch() {
  if (!query.partnerCode) {
    ElMessage.warning('请先选择对接方')
    return
  }
  matching.value = true
  try {
    const count = await autoMatchProductMap(query.partnerCode)
    ElMessage.success(`自动匹配完成，共匹配 ${count} 条`)
    fetchList()
  } finally {
    matching.value = false
  }
}

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ThirdPartyProductMap>(emptyForm())
const rules: FormRules = {
  partnerCode: [{ required: true, message: '请选择对接方', trigger: 'change' }],
  remoteCode: [{ required: true, message: '请输入对方商品编码', trigger: 'blur' }]
}

function emptyForm(): ThirdPartyProductMap {
  return { partnerCode: '', remoteCode: '', remoteName: '', reagentId: undefined, remark: '' }
}

function handleAdd() {
  Object.assign(form, emptyForm(), { id: undefined, partnerCode: query.partnerCode })
  dialogVisible.value = true
}

function handleEdit(row: ThirdPartyProductMap) {
  Object.assign(form, row)
  reagentOptions.value = row.reagentId
    ? [{ id: row.reagentId, reagentCode: row.reagentCode || '', reagentName: row.reagentName || '' } as Reagent]
    : []
  dialogVisible.value = true
}

async function handleDelete(row: ThirdPartyProductMap) {
  await ElMessageBox.confirm(`确定删除对方商品「${row.remoteCode}」的映射？`, '提示', { type: 'warning' })
  await deleteProductMap(row.id!)
  ElMessage.success('已删除')
  fetchList()
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return
    saving.value = true
    try {
      await saveProductMap({ ...form })
      ElMessage.success('保存成功')
      dialogVisible.value = false
      fetchList()
    } finally {
      saving.value = false
    }
  })
}

// 试剂远程搜索
const reagentOptions = ref<Reagent[]>([])
const reagentLoading = ref(false)

async function searchReagents(keyword: string) {
  reagentLoading.value = true
  try {
    const res = await getReagentPage({ pageNum: 1, pageSize: 20, reagentName: keyword || undefined })
    reagentOptions.value = res.records || []
  } finally {
    reagentLoading.value = false
  }
}

function onReagentChange(id?: number) {
  const reagent = reagentOptions.value.find(item => item.id === id)
  if (reagent) {
    form.reagentCode = reagent.reagentCode
    form.reagentName = reagent.reagentName
  } else {
    form.reagentCode = undefined
    form.reagentName = undefined
  }
}

onMounted(async () => {
  configList.value = await getConfigList()
  if (configList.value.length > 0) {
    query.partnerCode = configList.value[0].partnerCode
  }
  fetchList()
})
</script>

<style scoped>
.thirdparty-page {
  padding: 16px;
}
.search-card {
  margin-bottom: 12px;
}
.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
