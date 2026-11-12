<template>
  <div class="thirdparty-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="对接方编码/名称"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.enabled" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增对接方</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="对接说明"
        description="对方接口标准不一致时，只需在「字段映射」中调整对方字段名，无需修改代码；入站接口鉴权使用配置中的入站 Token。"
      />
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="partnerCode" label="对接方编码" width="140" />
        <el-table-column prop="partnerName" label="对接方名称" min-width="150" />
        <el-table-column prop="baseUrl" label="接口地址" min-width="220" show-overflow-tooltip />
        <el-table-column label="鉴权方式" width="110" align="center">
          <template #default="{ row }">{{ AUTH_TYPE_MAP[row.authType || 0] }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入站接口" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.inboundEnabled === 1 ? 'success' : 'info'" size="small">
              {{ row.inboundEnabled === 1 ? '开放' : '关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleTest(row)">测试连接</el-button>
            <el-button type="warning" link size="small" @click="handleFieldMap(row)">字段映射</el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => handleMore(cmd, row)">
              <el-button type="info" link size="small">更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="products">拉取商品主数据</el-dropdown-item>
                  <el-dropdown-item command="suppliers">拉取供应商主数据</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除对接方</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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

    <!-- 配置编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑对接方' : '新增对接方'" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="对接方编码" prop="partnerCode">
              <el-input v-model="form.partnerCode" placeholder="如 WEICAI" :disabled="!!form.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="对接方名称" prop="partnerName">
              <el-input v-model="form.partnerName" placeholder="如 某卫材供应商" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接口地址" prop="baseUrl">
              <el-input v-model="form.baseUrl" placeholder="https://partner.example.com/api" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用状态">
              <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">鉴权与请求</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="鉴权方式">
              <el-select v-model="form.authType" style="width: 100%">
                <el-option v-for="(label, key) in AUTH_TYPE_MAP" :key="key" :label="label" :value="Number(key)" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="超时(毫秒)">
              <el-input-number v-model="form.timeoutMs" :min="1000" :max="120000" :step="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <template v-if="form.authType === 1">
            <el-col :span="12">
              <el-form-item label="Token 请求头">
                <el-input v-model="form.authHeader" placeholder="默认 Authorization" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="Token 值">
                <el-input v-model="form.authToken" placeholder="含前缀时一并填写，如 Bearer xxx" />
              </el-form-item>
            </el-col>
          </template>
          <template v-if="form.authType === 2">
            <el-col :span="12">
              <el-form-item label="AppKey">
                <el-input v-model="form.appKey" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="AppSecret">
                <el-input v-model="form.appSecret" show-password />
              </el-form-item>
            </el-col>
          </template>
          <template v-if="form.authType === 3">
            <el-col :span="12">
              <el-form-item label="用户名">
                <el-input v-model="form.basicUsername" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="密码">
                <el-input v-model="form.basicPassword" show-password />
              </el-form-item>
            </el-col>
          </template>
          <el-col :span="12">
            <el-form-item label="失败重试次数">
              <el-input-number v-model="form.retryTimes" :min="0" :max="5" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">出站接口路径</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="采购订单">
              <el-input v-model="form.orderPath" placeholder="/orders" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品主数据">
              <el-input v-model="form.productPath" placeholder="/products" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商主数据">
              <el-input v-model="form.supplierPath" placeholder="/suppliers" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存查询">
              <el-input v-model="form.stockPath" placeholder="/stock" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">入站接口（对方调我们）</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开放入站">
              <el-switch v-model="form.inboundEnabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入站 Token">
              <el-input v-model="form.inboundToken" placeholder="对方请求头 X-Third-Token" show-password />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="入站地址">
              <div class="tip-text">
                <div>到货回传：POST /api/reagent/open/thirdparty/{{ form.partnerCode || '{partnerCode}' }}/delivery</div>
                <div>订单状态：POST /api/reagent/open/thirdparty/{{ form.partnerCode || '{partnerCode}' }}/order-status</div>
                <div>订单拉取：GET /api/reagent/open/thirdparty/{{ form.partnerCode || '{partnerCode}' }}/orders</div>
                <div>试剂拉取：GET /api/reagent/open/thirdparty/{{ form.partnerCode || '{partnerCode}' }}/reagents</div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字段映射 -->
    <el-dialog v-model="mapVisible" title="字段映射" width="900px" destroy-on-close>
      <el-form :inline="true">
        <el-form-item label="业务类型">
          <el-select v-model="mapQuery.bizType" style="width: 160px" @change="loadFieldMaps">
            <el-option v-for="(label, key) in BIZ_TYPE_MAP" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="mapQuery.direction" style="width: 160px" @change="loadFieldMaps">
            <el-option label="出站（我们调对方）" :value="1" />
            <el-option label="入站（对方调我们）" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="addMapRow">新增映射</el-button>
          <el-button type="success" :loading="mapSaving" @click="saveMapRows">保存映射</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
        title="本地字段为系统标准字段，对方字段支持嵌套路径（如 data.orderNo）；明细字段本地用 items.xxx，对方用 items[].xxx。留空则不覆盖默认标准字段。"
      />
      <el-table :data="mapRows" border size="small" max-height="380">
        <el-table-column label="本系统字段" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.localField" placeholder="如 orderNo / items.reagentCode" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="对方字段" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.remoteField" placeholder="如 data.orderCode" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="缺省值" width="140">
          <template #default="{ row }">
            <el-input v-model="row.defaultValue" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="mapRows.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getConfigPage,
  createConfig,
  updateConfig,
  deleteConfig,
  testConfig,
  getFieldMaps,
  saveFieldMaps,
  pullThirdPartyProducts,
  pullThirdPartySuppliers,
  type ThirdPartyConfig,
  type ThirdPartyFieldMap
} from '@/api/reagent/thirdparty'

const AUTH_TYPE_MAP: Record<number, string> = { 0: '无需鉴权', 1: 'Token', 2: 'AK/SK 签名', 3: 'Basic' }
const BIZ_TYPE_MAP: Record<string, string> = {
  ORDER: '采购订单',
  PRODUCT: '商品主数据',
  SUPPLIER: '供应商主数据',
  STOCK: '库存效期',
  DELIVERY: '到货回单'
}

const loading = ref(false)
const tableData = ref<ThirdPartyConfig[]>([])
const searchForm = reactive({ keyword: '', enabled: undefined as number | undefined })
const pagination = reactive({ current: 1, size: 10, total: 0 })

async function fetchList() {
  loading.value = true
  try {
    const res = await getConfigPage({
      current: pagination.current,
      size: pagination.size,
      keyword: searchForm.keyword || undefined,
      enabled: searchForm.enabled
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
  searchForm.keyword = ''
  searchForm.enabled = undefined
  handleSearch()
}

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ThirdPartyConfig>(emptyForm())
const rules: FormRules = {
  partnerCode: [{ required: true, message: '请输入对接方编码', trigger: 'blur' }],
  partnerName: [{ required: true, message: '请输入对接方名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入接口地址', trigger: 'blur' }]
}

function emptyForm(): ThirdPartyConfig {
  return {
    partnerCode: '',
    partnerName: '',
    enabled: 1,
    baseUrl: '',
    authType: 0,
    authHeader: 'Authorization',
    authToken: '',
    appKey: '',
    appSecret: '',
    basicUsername: '',
    basicPassword: '',
    timeoutMs: 10000,
    retryTimes: 0,
    orderPath: '/orders',
    productPath: '/products',
    supplierPath: '/suppliers',
    stockPath: '/stock',
    inboundEnabled: 0,
    inboundToken: '',
    remark: ''
  }
}

function handleAdd() {
  Object.assign(form, emptyForm(), { id: undefined })
  dialogVisible.value = true
}

function handleEdit(row: ThirdPartyConfig) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) {
        await updateConfig({ ...form })
      } else {
        await createConfig({ ...form })
      }
      ElMessage.success('保存成功')
      dialogVisible.value = false
      fetchList()
    } finally {
      saving.value = false
    }
  })
}

async function handleTest(row: ThirdPartyConfig) {
  const res = await testConfig(row.id!)
  ElMessage.success(res || '连接成功')
}

async function handleMore(cmd: string, row: ThirdPartyConfig) {
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`确定删除对接方「${row.partnerName}」？`, '提示', { type: 'warning' })
    await deleteConfig(row.id!)
    ElMessage.success('已删除')
    fetchList()
    return
  }
  const action = cmd === 'products' ? pullThirdPartyProducts : pullThirdPartySuppliers
  const label = cmd === 'products' ? '商品' : '供应商'
  const res = await action(row.partnerCode)
  ElMessage.success(`已同步 ${res} 条${label}数据`)
}

const mapVisible = ref(false)
const mapSaving = ref(false)
const mapPartnerCode = ref('')
const mapQuery = reactive({ bizType: 'ORDER', direction: 1 })
const mapRows = ref<ThirdPartyFieldMap[]>([])

function handleFieldMap(row: ThirdPartyConfig) {
  mapPartnerCode.value = row.partnerCode
  mapQuery.bizType = 'ORDER'
  mapQuery.direction = 1
  mapRows.value = []
  mapVisible.value = true
  loadFieldMaps()
}

async function loadFieldMaps() {
  mapRows.value = await getFieldMaps({
    partnerCode: mapPartnerCode.value,
    bizType: mapQuery.bizType,
    direction: mapQuery.direction
  })
}

function addMapRow() {
  mapRows.value.push({ localField: '', remoteField: '', defaultValue: '', remark: '' })
}

async function saveMapRows() {
  mapSaving.value = true
  try {
    await saveFieldMaps({
      partnerCode: mapPartnerCode.value,
      bizType: mapQuery.bizType,
      direction: mapQuery.direction,
      maps: mapRows.value
    })
    ElMessage.success('映射已保存')
    loadFieldMaps()
  } finally {
    mapSaving.value = false
  }
}

onMounted(fetchList)
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
.tip-text {
  font-size: 12px;
  line-height: 20px;
  color: #909399;
  word-break: break-all;
}
</style>
