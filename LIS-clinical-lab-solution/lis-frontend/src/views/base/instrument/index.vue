<template>
  <div class="instrument-dict-page">
    <!-- 顶部搜索栏 -->
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

    <!-- 左右布局 -->
    <div class="split-layout">
      <!-- 左侧: 仪器信息列表 -->
      <el-card shadow="never" class="left-panel">
        <template #header>
          <div class="panel-header">
            <span>仪器列表</span>
            <el-tag size="small" type="info">共 {{ pagination.total }} 台</el-tag>
          </div>
        </template>
        <el-table
          v-loading="loading"
          :data="tableData"
          border
          stripe
          highlight-current-row
          style="width: 100%"
          height="calc(100vh - 260px)"
          @current-change="handleCurrentRowChange"
        >
          <el-table-column type="index" label="序号" width="50" align="center" />
          <el-table-column prop="instrumentCode" label="仪器编码" width="110" />
          <el-table-column prop="instrumentName" label="仪器名称" min-width="130" />
          <el-table-column prop="brand" label="品牌" width="90" />
          <el-table-column prop="model" label="型号" width="90" />
          <el-table-column prop="protocolType" label="协议" width="80" align="center" />
          <el-table-column label="连接" width="80" align="center">
            <template #default="{ row }">
              {{ row.connectType === 'SERIAL' ? '串口' : row.connectType === 'TCP' ? 'TCP' : row.connectType }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="warning" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.pageNum"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            :page-sizes="[20, 50, 100]"
            layout="total, prev, pager, next"
            background
            small
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>

      <!-- 右侧: 检验项目 + 检验指标 / 操作人员 / 标本类型 -->
      <el-card shadow="never" class="right-panel">
        <template #header>
          <div class="panel-header">
            <span>
              仪器配置
              <span v-if="currentInstrument" class="header-subtitle">
                — {{ currentInstrument.instrumentName }}
              </span>
            </span>
            <div v-if="currentInstrument" class="header-actions">
              <el-button v-if="activeTab === 'items'" type="success" size="small" :loading="itemMapSaving" @click="handleSaveItemMaps">保存配置</el-button>
              <el-button v-if="activeTab === 'staff'" type="success" size="small" :loading="staffMapSaving" @click="handleSaveStaffMaps">保存人员</el-button>
              <el-button v-if="activeTab === 'specimens'" type="success" size="small" :loading="specimenMapSaving" @click="handleSaveSpecimenMaps">保存标本类型</el-button>
            </div>
          </div>
        </template>

        <div v-if="!currentInstrument" class="empty-tip">
          <el-empty description="请选择左侧仪器" :image-size="80" />
        </div>

        <div v-else class="right-split">
          <el-tabs v-model="activeTab" class="config-tabs">
            <el-tab-pane label="检验项目 & 检验指标" name="items">
          <!-- 检验项目区 -->
          <div class="section-block">
            <div class="section-header">
              <span class="section-title">
                <el-tag type="primary" size="small" effect="dark">检验项目</el-tag>
                <span class="section-count">{{ panelMapList.length }} 项</span>
              </span>
              <div class="section-actions">
                <el-input
                  v-model="panelTableSearch"
                  placeholder="搜索项目名称/编码/通道"
                  clearable
                  size="small"
                  style="width: 220px; margin-right: 8px"
                  @input="panelTablePagination.pageNum = 1"
                />
                <el-button type="primary" size="small" @click="addPanelRow">添加项目</el-button>
                <el-button type="danger" size="small" :disabled="!selectedPanelMaps.length" @click="removeSelectedPanelMaps">删除选中</el-button>
              </div>
            </div>
            <el-table
              v-loading="itemMapLoading"
              :data="panelTableData"
              size="small"
              border
              stripe
              height="220"
              @selection-change="onPanelMapSelectionChange"
            >
              <el-table-column type="selection" width="40" />
              <el-table-column label="检验项目" min-width="220">
                <template #default="{ row, $index }">
                  <el-select
                    v-model="row.testItemId"
                    placeholder="选择检验项目(输入名称/编码搜索)"
                    filterable
                    remote
                    reserve-keyword
                    remote-show-suffix
                    size="small"
                    style="width: 100%"
                    :loading="panelOptionsLoading"
                    :remote-method="remoteSearchPanel"
                    @change="(val) => onTestItemSelected(row, val, $index)"
                    @focus="() => { if(!panelOptions.value.length) remoteSearchPanel('') }"
                  >
                    <el-option
                      v-for="t in panelOptions"
                      :key="t.id"
                      :label="`${t.itemCode} - ${t.itemName}`"
                      :value="t.id"
                      :disabled="isItemUsed(t.id, 'panel', row)"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="仪器项目编码" width="120">
                <template #default="{ row }">
                  <el-input v-model="row.instrumentItemCode" size="small" placeholder="如 CBC" />
                </template>
              </el-table-column>
              <el-table-column label="仪器项目名称" width="120">
                <template #default="{ row }">
                  <el-input v-model="row.instrumentItemName" size="small" placeholder="如 血常规" />
                </template>
              </el-table-column>
              <el-table-column label="通道码" width="100">
                <template #default="{ row }">
                  <el-input v-model="row.channelCode" size="small" placeholder="如 CH01" />
                </template>
              </el-table-column>
              <el-table-column label="标本类型" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.specimenType" size="small" clearable placeholder="全部" filterable style="width: 100%">
                    <el-option
                      v-for="t in specimenTypeOptions"
                      :key="t.typeCode"
                      :label="t.typeName"
                      :value="t.typeName"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="60" align="center" fixed="right">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="removePanelRowByTableIndex($index)">移除</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="点击「添加项目」开始配置检验项目" :image-size="60" />
              </template>
            </el-table>
            <div class="table-pagination-bar">
              <el-pagination
                v-model:current-page="panelTablePagination.pageNum"
                v-model:page-size="panelTablePagination.pageSize"
                :total="panelTablePagination.total"
                :page-sizes="[10, 20, 50]"
                layout="total, prev, pager, next"
                background
                small
              />
            </div>
          </div>

          <!-- 分隔线 -->
          <el-divider class="section-divider">
            <span class="divider-text">以下为检验指标</span>
          </el-divider>

          <!-- 检验指标区 -->
          <div class="section-block indicator-section">
            <div class="section-header">
              <span class="section-title">
                <el-tag type="warning" size="small" effect="dark">检验指标</el-tag>
                <span class="section-count">{{ indicatorMapList.length }} 项</span>
              </span>
              <div class="section-actions">
                <el-input
                  v-model="indicatorTableSearch"
                  placeholder="搜索指标名称/编码/通道"
                  clearable
                  size="small"
                  style="width: 220px; margin-right: 8px"
                  @input="indicatorTablePagination.pageNum = 1"
                />
                <el-button type="primary" size="small" @click="addIndicatorRow">添加指标</el-button>
                <el-button type="danger" size="small" :disabled="!selectedIndicatorMaps.length" @click="removeSelectedIndicatorMaps">删除选中</el-button>
              </div>
            </div>
            <el-table
              :data="indicatorTableData"
              size="small"
              border
              stripe
              class="indicator-table"
              max-height="100%"
              @selection-change="onIndicatorMapSelectionChange"
            >
              <el-table-column type="selection" width="40" />
              <el-table-column label="检验指标" min-width="220">
                <template #default="{ row, $index }">
                  <el-select
                    v-model="row.testItemId"
                    placeholder="选择检验指标(输入名称/编码搜索)"
                    filterable
                    remote
                    reserve-keyword
                    remote-show-suffix
                    size="small"
                    style="width: 100%"
                    :loading="indicatorOptionsLoading"
                    :remote-method="remoteSearchIndicator"
                    @change="(val) => onTestItemSelected(row, val, $index)"
                    @focus="() => { if(!indicatorOptions.value.length) remoteSearchIndicator('') }"
                  >
                    <el-option
                      v-for="t in indicatorOptions"
                      :key="t.id"
                      :label="`${t.itemCode} - ${t.itemName}`"
                      :value="t.id"
                      :disabled="isItemUsed(t.id, 'indicator', row)"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="仪器项目编码" width="120">
                <template #default="{ row }">
                  <el-input v-model="row.instrumentItemCode" size="small" placeholder="如 WBC" />
                </template>
              </el-table-column>
              <el-table-column label="仪器项目名称" width="120">
                <template #default="{ row }">
                  <el-input v-model="row.instrumentItemName" size="small" placeholder="如 白细胞" />
                </template>
              </el-table-column>
              <el-table-column label="通道码" width="100">
                <template #default="{ row }">
                  <el-input v-model="row.channelCode" size="small" placeholder="如 CH01" />
                </template>
              </el-table-column>
              <el-table-column label="标本类型" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.specimenType" size="small" clearable placeholder="全部" filterable style="width: 100%">
                    <el-option
                      v-for="t in specimenTypeOptions"
                      :key="t.typeCode"
                      :label="t.typeName"
                      :value="t.typeName"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="60" align="center" fixed="right">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="removeIndicatorRowByTableIndex($index)">移除</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="点击「添加指标」开始配置检验指标" :image-size="60" />
              </template>
            </el-table>
            <div class="table-pagination-bar">
              <el-pagination
                v-model:current-page="indicatorTablePagination.pageNum"
                v-model:page-size="indicatorTablePagination.pageSize"
                :total="indicatorTablePagination.total"
                :page-sizes="[20, 50, 100]"
                layout="total, sizes, prev, pager, next"
                background
                small
              />
            </div>
          </div>

          <div class="config-tip">
            <span class="tip-icon">i</span>
            上方配置该仪器可检测的检验项目，下方配置可检测的检验指标；检验项目(isPanel=1)为申请粒度，检验指标(isPanel=0)为结果粒度
          </div>
            </el-tab-pane>

            <!-- 操作人员 -->
            <el-tab-pane :label="`操作人员 (${staffMapList.length})`" name="staff">
              <div class="section-block">
                <div class="section-header">
                  <span class="section-title">
                    <el-tag type="success" size="small" effect="dark">操作人员</el-tag>
                    <span class="section-count">{{ staffMapList.length }} 人</span>
                  </span>
                  <div class="section-actions">
                    <el-button type="primary" size="small" @click="addStaffRow">添加人员</el-button>
                    <el-button type="danger" size="small" :disabled="!selectedStaffMaps.length" @click="removeSelectedStaffMaps">删除选中</el-button>
                  </div>
                </div>
                <el-table
                  :data="staffMapList"
                  size="small"
                  border
                  stripe
                  height="380"
                  @selection-change="onStaffMapSelectionChange"
                >
                  <el-table-column type="selection" width="40" />
                  <el-table-column label="操作人员" min-width="200">
                    <template #default="{ row, $index }">
                      <el-select
                        v-model="row.staffId"
                        placeholder="选择操作人员"
                        filterable
                        size="small"
                        style="width: 100%"
                        @change="(val) => onStaffSelected(row, val)"
                      >
                        <el-option
                          v-for="s in staffOptions"
                          :key="s.id"
                          :label="s.name + (s.staffNo ? `（${s.staffNo}）` : '')"
                          :value="s.id"
                          :disabled="isStaffUsed(s.id, $index)"
                        />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="姓名" width="120">
                    <template #default="{ row }">
                      <span>{{ row.staffName || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="60" align="center" fixed="right">
                    <template #default="{ $index }">
                      <el-button type="danger" link size="small" @click="staffMapList.splice($index, 1)">移除</el-button>
                    </template>
                  </el-table-column>
                  <template #empty>
                    <el-empty description="点击「添加人员」配置可操作该仪器的人员；未配置则所有登录人员可见" :image-size="60" />
                  </template>
                </el-table>
              </div>
            </el-tab-pane>

            <!-- 标本类型 -->
            <el-tab-pane :label="`标本类型 (${specimenMapList.length})`" name="specimens">
              <div class="section-block">
                <div class="section-header">
                  <span class="section-title">
                    <el-tag type="warning" size="small" effect="dark">标本类型</el-tag>
                    <span class="section-count">{{ specimenMapList.length }} 种</span>
                  </span>
                  <div class="section-actions">
                    <el-button type="primary" size="small" @click="addSpecimenRow">添加标本类型</el-button>
                    <el-button type="danger" size="small" :disabled="!selectedSpecimenMaps.length" @click="removeSelectedSpecimenMaps">删除选中</el-button>
                  </div>
                </div>
                <el-table
                  :data="specimenMapList"
                  size="small"
                  border
                  stripe
                  height="380"
                  @selection-change="onSpecimenMapSelectionChange"
                >
                  <el-table-column type="selection" width="40" />
                  <el-table-column label="标本类型" min-width="200">
                    <template #default="{ row, $index }">
                      <el-select
                        v-model="row.specimenTypeId"
                        placeholder="选择标本类型"
                        filterable
                        size="small"
                        style="width: 100%"
                        @change="(val) => onSpecimenSelected(row, val)"
                      >
                        <el-option
                          v-for="t in specimenTypeOptions"
                          :key="t.id"
                          :label="`${t.typeCode} - ${t.typeName}`"
                          :value="t.id"
                          :disabled="isSpecimenUsed(t.id, $index)"
                        />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="类型编码" width="140">
                    <template #default="{ row }">
                      <span>{{ row.specimenTypeCode || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="类型名称" width="140">
                    <template #default="{ row }">
                      <span>{{ row.specimenTypeName || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="60" align="center" fixed="right">
                    <template #default="{ $index }">
                      <el-button type="danger" link size="small" @click="specimenMapList.splice($index, 1)">移除</el-button>
                    </template>
                  </el-table-column>
                  <template #empty>
                    <el-empty description="点击「添加标本类型」配置该仪器可处理的标本类型" :image-size="60" />
                  </template>
                </el-table>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-card>
    </div>

    <!-- 新增/编辑仪器弹窗 -->
    <el-dialog
      v-model="formDialogVisible"
      :title="editForm.id ? '编辑仪器' : '新增仪器'"
      width="640px"
    >
      <el-form ref="formRef" :model="editForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="仪器编码" prop="instrumentCode">
              <el-input v-model="editForm.instrumentCode" :disabled="!!editForm.id" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仪器名称" prop="instrumentName">
              <el-input v-model="editForm.instrumentName" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-input v-model="editForm.brand" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="editForm.model" placeholder="请输入" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="协议类型">
              <el-select v-model="editForm.protocolType" style="width: 100%">
                <el-option label="ASTM" value="ASTM" />
                <el-option label="HL7" value="HL7" />
                <el-option label="LIS01" value="LIS01" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="连接方式">
              <el-select v-model="editForm.connectType" style="width: 100%">
                <el-option label="串口" value="SERIAL" />
                <el-option label="TCP" value="TCP" />
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
                <el-select v-model="editForm.baudRate" style="width: 100%">
                  <el-option :label="9600" :value="9600" />
                  <el-option :label="19200" :value="19200" />
                  <el-option :label="38400" :value="38400" />
                  <el-option :label="57600" :value="57600" />
                  <el-option :label="115200" :value="115200" />
                </el-select>
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
                <el-input-number
                  v-model="editForm.port"
                  :min="1"
                  :max="65535"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </template>
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
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  getInstrumentDictList,
  saveInstrumentDict,
  deleteInstrumentDict,
  getInstrumentItemMaps,
  setInstrumentItemMaps,
  getTestItemList,
  getAllSpecimenTypes,
  getInstrumentStaffMaps,
  setInstrumentStaffMaps,
  getInstrumentSpecimenMaps,
  setInstrumentSpecimenMaps,
  type InstrumentDict,
  type InstrumentDictQuery,
  type InstrumentItemMap,
  type TestItem,
  type SpecimenType,
  type InstrumentStaffMap,
  type InstrumentSpecimenMap
} from '@/api/base'
import { getAllActiveStaff, type Staff } from '@/api/hr'

const loading = ref(false)
const tableData = ref<InstrumentDict[]>([])
const searchForm = reactive<InstrumentDictQuery>({
  instrumentCode: '',
  instrumentName: '',
  protocolType: '',
  status: undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 50,
  total: 0
})

const formDialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive<InstrumentDict>({
  id: 0,
  instrumentCode: '',
  instrumentName: '',
  brand: '',
  model: '',
  protocolType: 'ASTM',
  connectType: 'TCP',
  serialPort: '',
  baudRate: 9600,
  ipAddress: '',
  port: 9100,
  status: 1
})
const formRules: FormRules = {
  instrumentCode: [{ required: true, message: '请输入仪器编码', trigger: 'blur' }],
  instrumentName: [{ required: true, message: '请输入仪器名称', trigger: 'blur' }]
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getInstrumentDictList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      instrumentCode: searchForm.instrumentCode || undefined,
      instrumentName: searchForm.instrumentName || undefined
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
    // 默认选中第一行
    if (tableData.value.length && !currentInstrument.value) {
      handleCurrentRowChange(tableData.value[0])
    } else if (currentInstrument.value) {
      const updated = tableData.value.find(t => t.id === currentInstrument.value!.id)
      if (updated) handleCurrentRowChange(updated)
    }
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

async function handleAdd() {
  Object.assign(editForm, {
    id: 0, instrumentCode: '', instrumentName: '', brand: '', model: '',
    protocolType: 'ASTM', connectType: 'TCP', serialPort: '', baudRate: 9600,
    ipAddress: '', port: 9100, status: 1
  })
  formDialogVisible.value = true
}

async function handleEdit(row: InstrumentDict) {
  Object.assign(editForm, {
    id: row.id,
    instrumentCode: row.instrumentCode,
    instrumentName: row.instrumentName,
    brand: row.brand ?? '',
    model: row.model ?? '',
    protocolType: row.protocolType ?? 'ASTM',
    connectType: row.connectType ?? 'TCP',
    serialPort: row.serialPort ?? '',
    baudRate: row.baudRate ?? 9600,
    ipAddress: row.ipAddress ?? '',
    port: row.port ?? 9100,
    status: row.status ?? 1,
    lastOnlineTime: row.lastOnlineTime,
    createTime: row.createTime
  })
  formDialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formLoading.value = true
    try {
      await saveInstrumentDict({ ...editForm })
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

async function handleDelete(row: InstrumentDict) {
  try {
    await ElMessageBox.confirm(`确认删除仪器 "${row.instrumentName}" 吗?`, '提示', { type: 'warning' })
    await deleteInstrumentDict(row.id)
    ElMessage.success('删除成功')
    if (currentInstrument.value?.id === row.id) {
      currentInstrument.value = null
      panelMapList.value = []
      indicatorMapList.value = []
      staffMapList.value = []
      specimenMapList.value = []
    }
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

// ============ 右侧: 配置(检验项目/检验指标/操作人员/标本类型) ============
const currentInstrument = ref<InstrumentDict | null>(null)
const activeTab = ref<'items' | 'staff' | 'specimens'>('items')
const itemMapLoading = ref(false)
const itemMapSaving = ref(false)
// 检验项目列表(isPanel=1)
const panelMapList = ref<InstrumentItemMap[]>([])
// 检验指标列表(isPanel=0)
const indicatorMapList = ref<InstrumentItemMap[]>([])
// 选中项(分两套)
const selectedPanelMaps = ref<InstrumentItemMap[]>([])
const selectedIndicatorMaps = ref<InstrumentItemMap[]>([])
// ============ 远程搜索(避免一次性加载全部检验项目/指标造成卡顿) ============
const panelOptions = ref<TestItem[]>([])
const panelOptionsLoading = ref(false)
const panelSearchKeyword = ref('')
const indicatorOptions = ref<TestItem[]>([])
const indicatorOptionsLoading = ref(false)
const indicatorSearchKeyword = ref('')
// 下拉框选过的项目缓存(用于回显, 避免远程搜索后已选项不在列表中)
const panelEchoCache = ref<Map<number, TestItem>>(new Map())
const indicatorEchoCache = ref<Map<number, TestItem>>(new Map())
// 用于下拉框 remote-method 防抖
let panelSearchTimer: ReturnType<typeof setTimeout> | null = null
let indicatorSearchTimer: ReturnType<typeof setTimeout> | null = null
// ============ 检验项目表格: 搜索 + 分页 ============
const panelTableSearch = ref('')
const panelTablePagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const panelTableData = computed(() => {
  const kw = panelTableSearch.value.trim().toLowerCase()
  const source = panelMapList.value
  const filtered = !kw ? source : source.filter(m => {
    return (m.testItemName || '').toLowerCase().includes(kw)
      || (m.instrumentItemCode || '').toLowerCase().includes(kw)
      || (m.instrumentItemName || '').toLowerCase().includes(kw)
      || (m.channelCode || '').toLowerCase().includes(kw)
  })
  panelTablePagination.total = filtered.length
  const start = (panelTablePagination.pageNum - 1) * panelTablePagination.pageSize
  return filtered.slice(start, start + panelTablePagination.pageSize)
})
// ============ 检验指标表格: 搜索 + 分页 ============
const indicatorTableSearch = ref('')
const indicatorTablePagination = reactive({ pageNum: 1, pageSize: 50, total: 0 })
const indicatorTableData = computed(() => {
  const kw = indicatorTableSearch.value.trim().toLowerCase()
  const source = indicatorMapList.value
  const filtered = !kw ? source : source.filter(m => {
    return (m.testItemName || '').toLowerCase().includes(kw)
      || (m.instrumentItemCode || '').toLowerCase().includes(kw)
      || (m.instrumentItemName || '').toLowerCase().includes(kw)
      || (m.channelCode || '').toLowerCase().includes(kw)
  })
  indicatorTablePagination.total = filtered.length
  const start = (indicatorTablePagination.pageNum - 1) * indicatorTablePagination.pageSize
  return filtered.slice(start, start + indicatorTablePagination.pageSize)
})
// ============ 其他选项 ============
const specimenTypeOptions = ref<SpecimenType[]>([])
const staffOptions = ref<Staff[]>([])

// 操作人员 tab
const staffMapList = ref<InstrumentStaffMap[]>([])
const selectedStaffMaps = ref<InstrumentStaffMap[]>([])
const staffMapSaving = ref(false)

// 标本类型 tab
const specimenMapList = ref<InstrumentSpecimenMap[]>([])
const selectedSpecimenMaps = ref<InstrumentSpecimenMap[]>([])
const specimenMapSaving = ref(false)

function onPanelMapSelectionChange(rows: InstrumentItemMap[]) {
  selectedPanelMaps.value = rows
}

function onIndicatorMapSelectionChange(rows: InstrumentItemMap[]) {
  selectedIndicatorMaps.value = rows
}

// ============ 远程搜索: 检验项目下拉框 ============
async function remoteSearchPanel(keyword: string) {
  if (panelSearchTimer) clearTimeout(panelSearchTimer)
  panelSearchKeyword.value = keyword
  panelSearchTimer = setTimeout(async () => {
    panelOptionsLoading.value = true
    try {
      const res = await getTestItemList({
        pageNum: 1,
        pageSize: 50,
        keyword: keyword || undefined,
        isPanel: 1,
        status: 1
      })
      const list = res.records || []
      // 合并缓存项(已选项目即使不匹配也放入列表, 用于回显)
      const merged = [...list]
      const existIds = new Set(list.map(t => t.id))
      for (const cached of panelEchoCache.value.values()) {
        if (!existIds.has(cached.id)) merged.push(cached)
      }
      panelOptions.value = merged
    } catch {
      // 错误兜底: 至少保留缓存项
      panelOptions.value = [...panelEchoCache.value.values()]
    } finally {
      panelOptionsLoading.value = false
    }
  }, 300)
}

// ============ 远程搜索: 检验指标下拉框 ============
async function remoteSearchIndicator(keyword: string) {
  if (indicatorSearchTimer) clearTimeout(indicatorSearchTimer)
  indicatorSearchKeyword.value = keyword
  indicatorSearchTimer = setTimeout(async () => {
    indicatorOptionsLoading.value = true
    try {
      const res = await getTestItemList({
        pageNum: 1,
        pageSize: 50,
        keyword: keyword || undefined,
        isPanel: 0,
        status: 1
      })
      const list = res.records || []
      // 合并缓存项
      const merged = [...list]
      const existIds = new Set(list.map(t => t.id))
      for (const cached of indicatorEchoCache.value.values()) {
        if (!existIds.has(cached.id)) merged.push(cached)
      }
      indicatorOptions.value = merged
    } catch {
      indicatorOptions.value = [...indicatorEchoCache.value.values()]
    } finally {
      indicatorOptionsLoading.value = false
    }
  }, 300)
}

/** 根据 id 取检验项目详情(优先查缓存, 否则调后端详情兜底) */
async function ensureTestItemInCache(testItemId: number, scope: 'panel' | 'indicator'): Promise<TestItem | undefined> {
  if (!testItemId) return undefined
  const cache = scope === 'panel' ? panelEchoCache.value : indicatorEchoCache.value
  // 先在缓存中按 String id 比较找已有
  for (const [, v] of cache.entries()) {
    if (String(v.id) === String(testItemId)) return v
  }
  // 用精确 id 搜索(通过分页接口的 keyword 精确匹配 itemCode 不靠谱, 改为 keyword = 空拉取后过滤)
  try {
    const res = await getTestItemList({
      pageNum: 1, pageSize: 100, isPanel: scope === 'panel' ? 1 : 0, status: 1, keyword: ''
    })
    const found = (res.records || []).find(t => String(t.id) === String(testItemId))
    if (found) {
      cache.set(found.id, found)
      return found
    }
  } catch { /* ignore */ }
  return undefined
}

/** 判断某检验项目/指标是否已被其他行选用(避免重复, 同一区域内去重) — row 为表格中的行对象引用 */
function isItemUsed(testItemId: number, scope: 'panel' | 'indicator', row: InstrumentItemMap): boolean {
  const list = scope === 'panel' ? panelMapList.value : indicatorMapList.value
  return list.some((m) => m !== row && String(m.testItemId) === String(testItemId))
}

/** 获取某检验项目下的所有检验指标(通过 parentId 关联) */
function getIndicatorsOfPanel(panelId: number): TestItem[] {
  if (!panelId) return []
  // 优先从 indicatorOptions + indicatorEchoCache 中汇总所有已知数据, 再通过 parentId 过滤
  const known = new Map<string, TestItem>()
  for (const t of indicatorOptions.value) known.set(String(t.id), t)
  for (const t of indicatorEchoCache.value.values()) known.set(String(t.id), t)
  return [...known.values()].filter(t => String(t.parentId) === String(panelId))
}

/** 通过后端接口拉取某检验项目下的所有检验指标(用于级联自动加载) */
async function fetchIndicatorsOfPanelFromBackend(panelId: number): Promise<TestItem[]> {
  if (!panelId) return []
  try {
    const res = await getTestItemList({
      pageNum: 1, pageSize: 1000, isPanel: 0, status: 1
    })
    const list = res.records || []
    // 全部放入缓存, 加快后续回显
    for (const t of list) indicatorEchoCache.value.set(t.id, t)
    return list.filter(t => t.parentId === panelId)
  } catch {
    return []
  }
}

/** 统计当前已勾选的所有检验项目下, 每个指标被引用的次数（用于级联删除判断） */
function buildIndicatorRefCountFromPanels(excludePanelIds?: Set<number>): Map<number, number> {
  const counter = new Map<number, number>()
  for (const pm of panelMapList.value) {
    if (!pm.testItemId) continue
    if (excludePanelIds && excludePanelIds.has(pm.testItemId)) continue
    for (const ind of getIndicatorsOfPanel(pm.testItemId)) {
      counter.set(ind.id, (counter.get(ind.id) || 0) + 1)
    }
  }
  return counter
}

/** 选中检验项目/指标后自动填充仪器项目编码/名称; 若为检验项目, 自动加载其下指标 */
function onTestItemSelected(row: InstrumentItemMap, val: number, _index: number) {
  const scope = row === panelMapList.value.find(r => r === row) ? 'panel' : 'indicator'
  const options = scope === 'panel' ? panelOptions.value : indicatorOptions.value
  const cache = scope === 'panel' ? panelEchoCache.value : indicatorEchoCache.value
  // 放宽 id 比较: 统一转字符串匹配, 避免后端返回 String 与本地 number 不一致
  let item = options.find(t => String(t.id) === String(val)) || [...cache.values()].find(t => String(t.id) === String(val))
  // 未命中则放入空结构, 等待后端回显
  if (!item && val) {
    item = { id: val, itemCode: '', itemName: '', status: 1 }
    cache.set(val, item)
    ensureTestItemInCache(val as any, scope as 'panel' | 'indicator').then(detail => {
      if (detail) {
        cache.set(detail.id, detail)
        row.testItemName = detail.itemName
        if (!row.instrumentItemCode) row.instrumentItemCode = detail.itemCode
        if (!row.instrumentItemName) row.instrumentItemName = detail.itemName
        // 详情拉到后同步并入选项, 保证下拉回显名称而不是ID
        if (scope === 'indicator') {
          if (!indicatorOptions.value.some(x => String(x.id) === String(detail.id))) {
            indicatorOptions.value = [detail, ...indicatorOptions.value]
          }
        } else {
          if (!panelOptions.value.some(x => String(x.id) === String(detail.id))) {
            panelOptions.value = [detail, ...panelOptions.value]
          }
        }
      }
    })
  }
  if (item) {
    cache.set(item.id, item)
    row.testItemName = item.itemName
    if (!row.instrumentItemCode) row.instrumentItemCode = item.itemCode
    if (!row.instrumentItemName) row.instrumentItemName = item.itemName
    // 若是检验项目(isPanel=1) 但从缓存中只拿到空壳, 仍尝试走后端拉其子指标
    const realIsPanel = (item as any).isPanel
    // 如果 scope === panel, 当作 isPanel=1 处理
    if (scope === 'panel' || realIsPanel === 1) {
      autoLoadPanelIndicators(val)
    }
  }
}

/** 自动加载检验项目下的检验指标到指标表 */
async function autoLoadPanelIndicators(panelId: number) {
  if (!panelId) return
  // 先从已知缓存取, 不命中再走后端
  let indicators = getIndicatorsOfPanel(panelId)
  if (indicators.length === 0) {
    indicators = await fetchIndicatorsOfPanelFromBackend(panelId)
  }
  if (!indicators.length) return
  // 统一字符串化 id 去重比较
  const existingIds = new Set(indicatorMapList.value.map(m => String(m.testItemId)).filter(Boolean))
  let addedCount = 0
  for (const ind of indicators) {
    if (existingIds.has(String(ind.id))) continue
    indicatorEchoCache.value.set(ind.id, ind)
    indicatorMapList.value.push({
      testItemId: ind.id,
      testItemName: ind.itemName,
      instrumentItemCode: ind.itemCode,
      instrumentItemName: ind.itemName,
      channelCode: '',
      specimenType: ind.specimenType || '',
      operatorId: undefined,
      operatorName: ''
    })
    addedCount++
  }
  // 同步塞回下拉选项池, 立刻显示名称, 不显示 raw id
  if (addedCount > 0) {
    const existedOptionIds = new Set(indicatorOptions.value.map(x => String(x.id)))
    for (const ind of indicators) {
      if (!existedOptionIds.has(String(ind.id))) indicatorOptions.value.push(ind)
    }
    ElMessage.success(`已自动加载该项目下 ${addedCount} 个检验指标`)
  }
}

/** 添加一行检验项目 */
function addPanelRow() {
  if (!currentInstrument.value) return
  panelMapList.value.push({
    testItemId: 0,
    testItemName: '',
    instrumentItemCode: '',
    instrumentItemName: '',
    channelCode: '',
    specimenType: '',
    operatorId: undefined,
    operatorName: ''
  })
}

/** 通过分页表格中的显示索引, 计算出在 panelMapList.value 中的真实索引并删除(带级联) */
function removePanelRowByTableIndex(tableIndex: number) {
  const start = (panelTablePagination.pageNum - 1) * panelTablePagination.pageSize
  const visibleRow = panelTableData.value[tableIndex]
  if (!visibleRow) return
  const realIndex = panelMapList.value.indexOf(visibleRow)
  if (realIndex >= 0) removePanelRowCascade(realIndex)
}

/** 添加一行检验指标 */
function addIndicatorRow() {
  if (!currentInstrument.value) return
  indicatorMapList.value.push({
    testItemId: 0,
    testItemName: '',
    instrumentItemCode: '',
    instrumentItemName: '',
    channelCode: '',
    specimenType: '',
    operatorId: undefined,
    operatorName: ''
  })
}

/** 通过分页表格中的显示索引, 计算出在 indicatorMapList.value 中的真实索引并删除 */
function removeIndicatorRowByTableIndex(tableIndex: number) {
  const visibleRow = indicatorTableData.value[tableIndex]
  if (!visibleRow) return
  const realIndex = indicatorMapList.value.indexOf(visibleRow)
  if (realIndex >= 0) indicatorMapList.value.splice(realIndex, 1)
}

/** 移除一个检验项目行, 并级联移除其下指标(仅当无其他保留项目引用时) */
function removePanelRowCascade(index: number) {
  const target = panelMapList.value[index]
  if (!target) return
  // 构建"除了要删除的项目外"其他已选项目对指标的引用次数
  const excludeIds = new Set<number>()
  if (target.testItemId) excludeIds.add(target.testItemId)
  const refCount = buildIndicatorRefCountFromPanels(excludeIds)
  // 先从 panel 列表中移除
  panelMapList.value.splice(index, 1)
  // 级联删除该项目下不被其他项目引用的指标
  if (target.testItemId) {
    const indicators = getIndicatorsOfPanel(target.testItemId)
    if (indicators.length) {
      const removeIds = new Set<number>()
      for (const ind of indicators) {
        if ((refCount.get(ind.id) || 0) === 0) {
          removeIds.add(ind.id)
        }
      }
      if (removeIds.size) {
        indicatorMapList.value = indicatorMapList.value.filter(m => !removeIds.has(m.testItemId))
        ElMessage.info(`已同步移除 ${removeIds.size} 个未被其他项目共享的检验指标`)
      }
    }
  }
}

/** 删除检验项目选中行(带级联删除) */
function removeSelectedPanelMaps() {
  if (!selectedPanelMaps.value.length) return
  const toRemove = selectedPanelMaps.value.slice()
  // 构建要排除的项目集合(全部要删除的项目), 一次性计算剩余引用次数
  const excludeIds = new Set<number>(toRemove.map(m => m.testItemId).filter(Boolean))
  const refCount = buildIndicatorRefCountFromPanels(excludeIds)
  // 先从 panel 列表中移除
  const set = new Set(toRemove)
  panelMapList.value = panelMapList.value.filter(m => !set.has(m))
  // 级联删除: 收集所有待删除项目拥有的指标, 只删除剩余引用次数=0的
  const cascadeRemoveIds = new Set<number>()
  for (const panel of toRemove) {
    if (!panel.testItemId) continue
    for (const ind of getIndicatorsOfPanel(panel.testItemId)) {
      if ((refCount.get(ind.id) || 0) === 0) {
        cascadeRemoveIds.add(ind.id)
      }
    }
  }
  if (cascadeRemoveIds.size) {
    indicatorMapList.value = indicatorMapList.value.filter(m => !cascadeRemoveIds.has(m.testItemId))
    ElMessage.info(`已同步移除 ${cascadeRemoveIds.size} 个未被其他项目共享的检验指标`)
  }
  selectedPanelMaps.value = []
}

/** 删除检验指标选中行 */
function removeSelectedIndicatorMaps() {
  const set = new Set(selectedIndicatorMaps.value)
  indicatorMapList.value = indicatorMapList.value.filter(m => !set.has(m))
  selectedIndicatorMaps.value = []
}

/** 选中仪器行: 加载右侧配置 */
async function handleCurrentRowChange(row: InstrumentDict | null) {
  currentInstrument.value = row
  if (!row) {
    panelMapList.value = []
    indicatorMapList.value = []
    staffMapList.value = []
    specimenMapList.value = []
    panelTablePagination.pageNum = 1
    indicatorTablePagination.pageNum = 1
    return
  }
  panelTablePagination.pageNum = 1
  indicatorTablePagination.pageNum = 1
  // 只拉一次空关键字的前50条作为初始展示, 避免一次性全量加载卡顿
  if (panelOptions.value.length === 0) remoteSearchPanel('')
  if (indicatorOptions.value.length === 0) remoteSearchIndicator('')
  if (!specimenTypeOptions.value.length) {
    try {
      specimenTypeOptions.value = await getAllSpecimenTypes()
    } catch {
      specimenTypeOptions.value = []
    }
  }
  if (!staffOptions.value.length) {
    try {
      staffOptions.value = await getAllActiveStaff()
    } catch {
      staffOptions.value = []
    }
  }
  // 加载已配置的映射, 按 testItemId 的 isPanel 属性分配到两个列表
  itemMapLoading.value = true
  try {
    const maps = await getInstrumentItemMaps(row.id)
    const all = (maps || []).map(m => ({
      id: m.id,
      instrumentId: m.instrumentId,
      testItemId: m.testItemId,
      testItemName: m.testItemName,
      instrumentItemCode: m.instrumentItemCode || '',
      instrumentItemName: m.instrumentItemName || '',
      channelCode: m.channelCode || '',
      specimenType: m.specimenType || '',
      operatorId: m.operatorId,
      operatorName: m.operatorName || ''
    }))
    // 通过后端分页接口拉取检验项目字典(id集合)用于分组, 只拉前1000条做分组判断
    let dictPanelIds = new Set<string>()
    try {
      const panelRes = await getTestItemList({ pageNum: 1, pageSize: 1000, isPanel: 1, status: 1 })
      dictPanelIds = new Set((panelRes.records || []).map(t => String(t.id)))
      for (const t of (panelRes.records || [])) panelEchoCache.value.set(t.id, t)
    } catch { /* ignore */ }
    // 填充回显缓存(已映射项目即使后端没返回, 也先用数据库保存的名称做占位)
    const panelIdsInMaps: number[] = []
    const indicatorIdsInMaps: number[] = []
    for (const m of all) {
      if (!m.testItemId) continue
      if (dictPanelIds.has(String(m.testItemId))) {
        panelIdsInMaps.push(m.testItemId)
        if (!panelEchoCache.value.has(m.testItemId)) {
          panelEchoCache.value.set(m.testItemId, {
            id: m.testItemId,
            itemCode: m.instrumentItemCode || '',
            itemName: m.testItemName || m.instrumentItemName || '',
            status: 1,
            isPanel: 1
          })
        }
      } else {
        indicatorIdsInMaps.push(m.testItemId)
        if (!indicatorEchoCache.value.has(m.testItemId)) {
          indicatorEchoCache.value.set(m.testItemId, {
            id: m.testItemId,
            itemCode: m.instrumentItemCode || '',
            itemName: m.testItemName || m.instrumentItemName || '',
            status: 1,
            isPanel: 0
          })
        }
      }
    }
    panelMapList.value = all.filter(m => dictPanelIds.has(String(m.testItemId)))
    indicatorMapList.value = all.filter(m => !dictPanelIds.has(String(m.testItemId)))
    // 拉一批检验指标字典(1000条)进缓存, 保证AU5800这种大量指标的仪器做回显和级联加载
    if (indicatorIdsInMaps.length > 0) {
      try {
        const indicatorRes = await getTestItemList({ pageNum: 1, pageSize: 1000, isPanel: 0, status: 1 })
        for (const t of (indicatorRes.records || [])) {
          indicatorEchoCache.value.set(t.id, t)
        }
      } catch { /* ignore */ }
    }
    // ----- 关键修复: 把缓存的指标/项目字典同步初始化到下拉选项列表,
    //       避免 el-select 初始渲染时选项为空, v-model 匹配不到选项就显示原始ID -----
    indicatorOptions.value = [...indicatorEchoCache.value.values()]
    panelOptions.value = [...panelEchoCache.value.values()]
  } catch {
    panelMapList.value = []
    indicatorMapList.value = []
  } finally {
    itemMapLoading.value = false
  }
  // 加载操作人员映射
  try {
    const staffMaps = await getInstrumentStaffMaps(row.id)
    staffMapList.value = (staffMaps || []).map(m => ({
      id: m.id,
      instrumentId: m.instrumentId,
      staffId: m.staffId,
      staffName: m.staffName || ''
    }))
  } catch {
    staffMapList.value = []
  }
  // 加载标本类型映射
  try {
    const specimenMaps = await getInstrumentSpecimenMaps(row.id)
    specimenMapList.value = (specimenMaps || []).map(m => ({
      id: m.id,
      instrumentId: m.instrumentId,
      specimenTypeId: m.specimenTypeId,
      specimenTypeName: m.specimenTypeName || '',
      specimenTypeCode: m.specimenTypeCode || ''
    }))
  } catch {
    specimenMapList.value = []
  }
}

/** 保存配置(合并两个列表) */
async function handleSaveItemMaps() {
  if (!currentInstrument.value) return
  const allList = [...panelMapList.value, ...indicatorMapList.value]
  const validMaps = allList.filter(m => m.testItemId)
  if (validMaps.length !== allList.length) {
    ElMessage.warning('请为每一行选择检验项目/指标，或移除空行')
    return
  }
  itemMapSaving.value = true
  try {
    await setInstrumentItemMaps(currentInstrument.value.id, validMaps)
    ElMessage.success('配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    itemMapSaving.value = false
  }
}

// ============ 操作人员 tab ============
function onStaffMapSelectionChange(rows: InstrumentStaffMap[]) {
  selectedStaffMaps.value = rows
}

/** 判断某员工是否已被其他行选用(去重) */
function isStaffUsed(staffId: number, currentIndex: number): boolean {
  // 需要在模板中传入 $index; 此函数依赖模板调用约定
  return staffMapList.value.some((m, idx) => idx !== currentIndex && m.staffId === staffId)
}

/** 选中操作人员后同步姓名 */
function onStaffSelected(row: InstrumentStaffMap, val: number | undefined) {
  if (val) {
    const staff = staffOptions.value.find(s => s.id === val)
    if (staff) row.staffName = staff.name
  } else {
    row.staffName = ''
  }
}

function addStaffRow() {
  if (!currentInstrument.value) return
  staffMapList.value.push({
    staffId: 0,
    staffName: ''
  })
}

function removeSelectedStaffMaps() {
  const set = new Set(selectedStaffMaps.value)
  staffMapList.value = staffMapList.value.filter(m => !set.has(m))
  selectedStaffMaps.value = []
}

async function handleSaveStaffMaps() {
  if (!currentInstrument.value) return
  const validMaps = staffMapList.value.filter(m => m.staffId)
  if (validMaps.length !== staffMapList.value.length) {
    ElMessage.warning('请为每一行选择操作人员，或移除空行')
    return
  }
  staffMapSaving.value = true
  try {
    await setInstrumentStaffMaps(currentInstrument.value.id, validMaps)
    ElMessage.success('操作人员已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    staffMapSaving.value = false
  }
}

// ============ 标本类型 tab ============
function onSpecimenMapSelectionChange(rows: InstrumentSpecimenMap[]) {
  selectedSpecimenMaps.value = rows
}

/** 判断某标本类型是否已被其他行选用(去重) */
function isSpecimenUsed(specimenTypeId: number, currentIndex: number): boolean {
  return specimenMapList.value.some((m, idx) => idx !== currentIndex && m.specimenTypeId === specimenTypeId)
}

/** 选中标本类型后同步名称/编码 */
function onSpecimenSelected(row: InstrumentSpecimenMap, val: number | undefined) {
  if (val) {
    const t = specimenTypeOptions.value.find(s => s.id === val)
    if (t) {
      row.specimenTypeName = t.typeName
      row.specimenTypeCode = t.typeCode
    }
  } else {
    row.specimenTypeName = ''
    row.specimenTypeCode = ''
  }
}

function addSpecimenRow() {
  if (!currentInstrument.value) return
  specimenMapList.value.push({
    specimenTypeId: 0,
    specimenTypeName: '',
    specimenTypeCode: ''
  })
}

function removeSelectedSpecimenMaps() {
  const set = new Set(selectedSpecimenMaps.value)
  specimenMapList.value = specimenMapList.value.filter(m => !set.has(m))
  selectedSpecimenMaps.value = []
}

async function handleSaveSpecimenMaps() {
  if (!currentInstrument.value) return
  const validMaps = specimenMapList.value.filter(m => m.specimenTypeId)
  if (validMaps.length !== specimenMapList.value.length) {
    ElMessage.warning('请为每一行选择标本类型，或移除空行')
    return
  }
  specimenMapSaving.value = true
  try {
    await setInstrumentSpecimenMaps(currentInstrument.value.id, validMaps)
    ElMessage.success('标本类型已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    specimenMapSaving.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.instrument-dict-page {
  padding: 16px;
  height: 100vh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;

  .search-card {
    margin-bottom: 12px;
    flex-shrink: 0;

    :deep(.el-card__body) {
      padding: 16px 16px 0 16px;
    }
  }

  .split-layout {
    display: flex;
    gap: 12px;
    align-items: stretch;
    flex: 1;
    min-height: 0;

    .left-panel {
      flex: 1.2;
      min-width: 0;
      display: flex;
      flex-direction: column;

      :deep(.el-card__body) {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
    }

    .right-panel {
      flex: 1.2;
      min-width: 0;
      display: flex;
      flex-direction: column;

      :deep(.el-card__body) {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
    }
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-subtitle {
      color: var(--el-color-primary);
      font-weight: 500;
      margin-left: 4px;
    }

    .header-actions {
      display: flex;
      gap: 8px;
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
    flex-shrink: 0;
  }

  .empty-tip {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: 1;
  }

  .right-split {
    display: flex;
    flex-direction: column;
    gap: 8px;
    flex: 1;
    min-height: 0;

    .config-tabs {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-height: 0;

      :deep(.el-tabs__content) {
        padding-top: 8px;
        flex: 1;
        overflow: auto;
        min-height: 0;
      }

      :deep(.el-tab-pane) {
        height: 100%;
        display: flex;
        flex-direction: column;
        min-height: 0;
      }
    }

    .section-block {
      display: flex;
      flex-direction: column;
      gap: 6px;

      &.indicator-section {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
      }
    }

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-shrink: 0;

      .section-title {
        display: flex;
        align-items: center;
        gap: 8px;

        .section-count {
          color: #909399;
          font-size: 12px;
        }
      }

      .section-actions {
        display: flex;
        gap: 8px;
        align-items: center;
      }
    }

    .section-divider {
      margin: 6px 0;
      flex-shrink: 0;

      .divider-text {
        font-size: 12px;
        color: #909399;
        font-weight: 500;
      }
    }

    :deep(.indicator-table) {
      flex: 1;
      min-height: 0;
    }
  }

  .table-pagination-bar {
    display: flex;
    justify-content: flex-end;
    margin-top: 6px;
    padding: 4px 0;
    flex-shrink: 0;
  }

  .config-tip {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 8px;
    color: #909399;
    font-size: 12px;
    flex-shrink: 0;

    .tip-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 14px;
      height: 14px;
      border-radius: 50%;
      background: var(--el-color-info);
      color: #fff;
      font-size: 10px;
      font-style: normal;
    }
  }
}
</style>
