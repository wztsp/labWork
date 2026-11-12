<template>
  <div class="instrument-config-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="仪器编码">
          <el-input
            v-model="searchForm.instrumentCode"
            placeholder="请输入"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="仪器名称">
          <el-input
            v-model="searchForm.instrumentName"
            placeholder="请输入"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleAdd">新增仪器</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="instrumentCode" label="仪器编码" min-width="120" />
        <el-table-column prop="instrumentName" label="仪器名称" min-width="140" />
        <el-table-column prop="brand" label="品牌" width="100" />
        <el-table-column prop="model" label="型号" width="100" />
        <el-table-column prop="protocolType" label="协议类型" width="100" align="center" />
        <el-table-column label="连接方式" width="100" align="center">
          <template #default="{ row }">
            {{ CONNECT_TYPE_MAP[row.connectType] || row.connectType }}
          </template>
        </el-table-column>
        <el-table-column label="IP/端口/串口" width="180">
          <template #default="{ row }">
            <span v-if="row.connectType === 'TCP'">{{ row.ipAddress }}:{{ row.port }}</span>
            <span v-else-if="row.connectType === 'SERIAL'">{{ row.serialPort }}@{{ row.baudRate }}</span>
            <span v-else-if="row.connectType === 'FILE'">{{ row.filePath }}</span>
            <span v-else-if="row.connectType === 'DB'">{{ row.dbHost }}:{{ row.dbPort }}/{{ row.dbTable }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="工作状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="WORK_STATUS_TAG_TYPE[row.workStatus] || 'info'"
              size="small"
              effect="dark"
            >
              {{ WORK_STATUS_MAP[row.workStatus] || '未设置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="使用科室" width="120" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleCommTest(row)">通讯测试</el-button>
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
    <el-dialog v-model="formDialogVisible" :title="editForm.id ? '编辑仪器' : '新增仪器'" width="760px">
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="仪器编码" prop="instrumentCode">
              <el-input v-model="editForm.instrumentCode" :disabled="!!editForm.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仪器名称" prop="instrumentName">
              <el-input v-model="editForm.instrumentName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-input v-model="editForm.brand" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="editForm.model" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协议类型" prop="protocolType">
              <el-select v-model="editForm.protocolType" style="width: 100%">
                <el-option v-for="(label, key) in PROTOCOL_TYPE_MAP" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="连接方式" prop="connectType">
              <el-select v-model="editForm.connectType" style="width: 100%">
                <el-option v-for="(label, key) in CONNECT_TYPE_MAP" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <template v-if="editForm.connectType === 'SERIAL'">
            <el-col :span="12">
              <el-form-item label="串口号">
                <el-input v-model="editForm.serialPort" placeholder="如 COM1" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="波特率">
                <el-input-number v-model="editForm.baudRate" :min="9600" :step="9600" style="width: 100%" />
              </el-form-item>
            </el-col>
          </template>
          <template v-if="editForm.connectType === 'TCP'">
            <el-col :span="12">
              <el-form-item label="IP地址">
                <el-input v-model="editForm.ipAddress" placeholder="如 192.168.1.100" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="端口">
                <el-input-number v-model="editForm.port" :min="1" :max="65535" style="width: 100%" />
              </el-form-item>
            </el-col>
          </template>
          <template v-if="editForm.connectType === 'FILE'">
            <el-col :span="24">
              <el-form-item label="文件目录" prop="filePath">
                <el-input v-model="editForm.filePath" placeholder="如 D:/instrument/results" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="文件格式">
                <el-select v-model="editForm.fileFormat" style="width: 100%">
                  <el-option v-for="(label, key) in FILE_FORMAT_MAP" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="轮询间隔(秒)">
                <el-input-number v-model="editForm.pollInterval" :min="5" :max="3600" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="文件名匹配">
                <el-input v-model="editForm.filePattern" placeholder="正则, 如 .*result.*\\.csv" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="归档目录">
                <el-input v-model="editForm.processedDir" placeholder="留空则处理成功后删除文件" />
              </el-form-item>
            </el-col>
          </template>
          <template v-if="editForm.connectType === 'DB'">
            <el-col :span="8">
              <el-form-item label="数据库类型" prop="dbType">
                <el-select v-model="editForm.dbType" style="width: 100%">
                  <el-option v-for="(label, key) in DB_TYPE_MAP" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="主机">
                <el-input v-model="editForm.dbHost" placeholder="IP 或主机名" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="端口">
                <el-input-number v-model="editForm.dbPort" :min="1" :max="65535" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="库名">
                <el-input v-model="editForm.dbName" placeholder="数据库名" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="用户名">
                <el-input v-model="editForm.dbUser" placeholder="数据库账号" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="密码">
                <el-input v-model="editForm.dbPassword" type="password" show-password placeholder="数据库密码" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="结果表名" prop="dbTable">
                <el-input v-model="editForm.dbTable" placeholder="如 result_data" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="水位线列">
                <el-input v-model="editForm.dbWatermarkCol" placeholder="增量列(自增ID或时间戳), 可空" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="列映射">
                <el-input v-model="editForm.dbColumns"
                  placeholder="sampleNo=sample_no,testCode=item_code,value=result_value,unit=unit,refRange=ref_range,abnormalFlag=flag" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="附加条件">
                <el-input v-model="editForm.dbWhere" placeholder="如 status='F'(不含 WHERE 关键字), 可空" />
              </el-form-item>
            </el-col>
          </template>
          <el-col :span="12">
            <el-form-item label="通道类型">
              <el-select v-model="editForm.channelType" placeholder="请选择" style="width: 100%">
                <el-option v-for="(label, key) in CHANNEL_TYPE_MAP" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="使用科室">
              <el-input v-model="editForm.department" placeholder="如 检验科" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="序列号">
              <el-input v-model="editForm.serialNo" placeholder="仪器序列号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安装日期">
              <el-date-picker
                v-model="editForm.installDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保修到期">
              <el-date-picker
                v-model="editForm.warrantyExpiry"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工作状态">
              <el-select v-model="editForm.workStatus" placeholder="请选择" style="width: 100%">
                <el-option
                  v-for="(label, key) in WORK_STATUS_MAP"
                  :key="key"
                  :label="label"
                  :value="Number(key)"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import dayjs from 'dayjs'
import {
  getInstrumentList,
  saveInstrument,
  deleteInstrument,
  PROTOCOL_TYPE_MAP,
  CONNECT_TYPE_MAP,
  FILE_FORMAT_MAP,
  DB_TYPE_MAP,
  CHANNEL_TYPE_MAP,
  WORK_STATUS_MAP,
  WORK_STATUS_TAG_TYPE,
  type Instrument,
  type InstrumentQuery
} from '@/api/instrument'

const router = useRouter()

const loading = ref(false)
const tableData = ref<Instrument[]>([])
const searchForm = reactive({
  instrumentCode: '',
  instrumentName: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

// 编辑弹窗
const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<Instrument>({
  id: 0,
  instrumentCode: '',
  instrumentName: '',
  brand: '',
  model: '',
  protocolType: 'ASTM',
  connectType: 'TCP',
  channelType: 'TCP',
  serialPort: '',
  baudRate: 9600,
  ipAddress: '',
  port: 9100,
  serialNo: '',
  installDate: '',
  warrantyExpiry: '',
  department: '',
  workStatus: 1,
  status: 1,
  filePath: '',
  fileFormat: 'CSV',
  filePattern: '',
  processedDir: '',
  pollInterval: 30,
  dbType: 'POSTGRESQL',
  dbHost: '',
  dbPort: 5432,
  dbName: '',
  dbUser: '',
  dbPassword: '',
  dbDriver: '',
  dbTable: '',
  dbColumns: '',
  dbWhere: '',
  dbWatermarkCol: ''
})
const formRules: FormRules = {
  instrumentCode: [{ required: true, message: '请输入仪器编码', trigger: 'blur' }],
  instrumentName: [{ required: true, message: '请输入仪器名称', trigger: 'blur' }],
  protocolType: [{ required: true, message: '请选择协议类型', trigger: 'change' }],
  connectType: [{ required: true, message: '请选择连接方式', trigger: 'change' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getInstrumentList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      instrumentCode: searchForm.instrumentCode || undefined,
      instrumentName: searchForm.instrumentName || undefined
    } as InstrumentQuery)
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.instrumentCode = ''
  searchForm.instrumentName = ''
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
    id: 0,
    instrumentCode: '',
    instrumentName: '',
    brand: '',
    model: '',
    protocolType: 'ASTM',
    connectType: 'TCP',
    channelType: 'TCP',
    serialPort: '',
    baudRate: 9600,
    ipAddress: '',
    port: 9100,
    serialNo: '',
    installDate: '',
    warrantyExpiry: '',
    department: '',
    workStatus: 1,
    status: 1,
    filePath: '',
    fileFormat: 'CSV',
    filePattern: '',
    processedDir: '',
    pollInterval: 30,
    dbType: 'POSTGRESQL',
    dbHost: '',
    dbPort: 5432,
    dbName: '',
    dbUser: '',
    dbPassword: '',
    dbDriver: '',
    dbTable: '',
    dbColumns: '',
    dbWhere: '',
    dbWatermarkCol: ''
  })
  formDialogVisible.value = true
}

/** 跳转到通讯日志页并预选该仪器，进行模拟发送 / 微生物回传测试 */
function handleCommTest(row: Instrument) {
  router.push({ path: '/instrument/log', query: { instrumentId: row.id ?? '' } })
}

function handleEdit(row: Instrument) {
  Object.assign(editForm, {
    id: row.id,
    instrumentCode: row.instrumentCode ?? '',
    instrumentName: row.instrumentName ?? '',
    brand: row.brand ?? '',
    model: row.model ?? '',
    protocolType: row.protocolType ?? 'ASTM',
    connectType: row.connectType ?? 'TCP',
    channelType: row.channelType ?? 'TCP',
    serialPort: row.serialPort ?? '',
    baudRate: row.baudRate ?? 9600,
    ipAddress: row.ipAddress ?? '',
    port: row.port ?? 9100,
    serialNo: row.serialNo ?? '',
    installDate: row.installDate ?? '',
    warrantyExpiry: row.warrantyExpiry ?? '',
    department: row.department ?? '',
    workStatus: row.workStatus ?? 1,
    status: row.status ?? 1,
    filePath: row.filePath ?? '',
    fileFormat: row.fileFormat ?? 'CSV',
    filePattern: row.filePattern ?? '',
    processedDir: row.processedDir ?? '',
    pollInterval: row.pollInterval ?? 30,
    dbType: row.dbType ?? 'POSTGRESQL',
    dbHost: row.dbHost ?? '',
    dbPort: row.dbPort ?? 5432,
    dbName: row.dbName ?? '',
    dbUser: row.dbUser ?? '',
    dbPassword: row.dbPassword ?? '',
    dbDriver: row.dbDriver ?? '',
    dbTable: row.dbTable ?? '',
    dbColumns: row.dbColumns ?? '',
    dbWhere: row.dbWhere ?? '',
    dbWatermarkCol: row.dbWatermarkCol ?? ''
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveInstrument({ ...editForm })
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

async function handleDelete(row: Instrument) {
  try {
    await ElMessageBox.confirm(`确认删除仪器 ${row.instrumentName} 吗?`, '提示', { type: 'warning' })
    await deleteInstrument(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.instrument-config-page {
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
