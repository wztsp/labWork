<template>
  <div class="comm-log-page">
    <!-- 手工验证 / 通讯测试 -->
    <el-card class="test-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>仪器通讯手工验证</span>
          <el-tag size="small" :type="'info'" effect="plain">向仪器数据接收接口发送模拟报文，验证通讯链路是否正常</el-tag>
        </div>
      </template>

      <el-form :inline="true" :model="testForm" @submit.prevent>
        <el-form-item label="仪器">
          <div class="instrument-row">
            <el-select
              v-model="testForm.instrumentId"
              placeholder="请选择仪器"
              filterable
              clearable
              style="width: 220px"
              @change="handleInstrumentChange"
            >
              <el-option
                v-for="item in instrumentList"
                :key="item.id"
                :label="item.instrumentName"
                :value="item.id"
              />
            </el-select>

            <!-- 模拟连接状态块：任何 connectType 的仪器都能在 LIS 内部模拟为"已接入" -->
            <div v-if="selectedInstrument" class="conn-block">
              <el-tooltip
                :content="
                  connectionStatus.online
                    ? ((connectionStatus.connectionSource === 'SIM' ? '[模拟接入] ' : '[真实接入] ') +
                       (connectionStatus.endpoint || connectionStatus.connectType || 'LIS'))
                    : ('未接入（' + (selectedInstrument.connectType || '未知方式') +
                      (connectionStatus.connectType === 'TCP'
                        ? '，仪器端地址：' + connectionStatus.ipAddress + ':' + connectionStatus.port
                        : connectionStatus.connectType === 'SERIAL'
                          ? '，串口：' + connectionStatus.serialPort + '@' + connectionStatus.baudRate
                          : '') + '）')
                "
              >
                <el-tag
                  :type="
                    !connectionStatus.online ? 'info'
                      : connectionStatus.connectionSource === 'SIM' ? '' : 'success'
                  "
                  :effect="connectionStatus.online ? 'dark' : 'plain'"
                  size="small"
                  class="conn-tag"
                >
                  <span class="dot" :class="{ on: connectionStatus.online }" />
                  {{
                    !connectionStatus.online ? '未接入' :
                    connectionStatus.connectionSource === 'SIM' ? '模拟已接入' : '真实已连接'
                  }}
                </el-tag>
              </el-tooltip>
              <el-button
                v-if="!connectionStatus.online"
                type="success"
                plain
                size="small"
                :loading="connecting"
                @click="handleSimulateConnect"
              >模拟连接到系统</el-button>
              <el-button
                v-else
                type="danger"
                plain
                size="small"
                :loading="connecting"
                @click="handleSimulateDisconnect"
              >断开</el-button>
              <el-button link type="primary" size="small" :loading="loadingStatus" @click="refreshConnStatus">
                刷新状态
              </el-button>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="品牌">
          <el-select v-model="testForm.brand" placeholder="请选择品牌" filterable allow-create style="width: 160px">
            <el-option v-for="b in brandList" :key="b" :label="b" :value="b" />
          </el-select>
        </el-form-item>

        <el-form-item label="检验项目组">
          <el-select v-model="testForm.panel" placeholder="请选择项目组" style="width: 140px">
            <el-option v-for="(p, name) in panels" :key="name" :label="name" :value="name" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="!isMicroMode" label="样本号/条码">
          <el-input
            v-model="testForm.sampleNo"
            placeholder="如 BC202608080001"
            clearable
            style="width: 200px"
          />
        </el-form-item>

        <el-form-item v-if="!isMicroMode">
          <el-checkbox v-model="testForm.random">随机值</el-checkbox>
        </el-form-item>

        <el-form-item v-if="!isMicroMode" label="质控模式">
          <el-checkbox v-model="testForm.qcMode" style="margin-right: 16px">
            <el-tooltip
              content="勾选后：样本号强制加 QC- 前缀；发送走 send-qc 质控专用接口，结果写入质控模块（lis_qc_record）"
              placement="top"
              :show-after="200"
            >
              质控模式
            </el-tooltip>
          </el-checkbox>
          <el-select
            v-if="testForm.qcMode"
            v-model="testForm.qcLotId"
            placeholder="质控品批号（可空=自动匹配）"
            filterable
            clearable
            style="width: 280px"
            :loading="qcLotListLoading"
          >
            <el-option
              v-for="lot in qcLotList"
              :key="lot.id"
              :label="`${lot.lotNo || '批号 ' + lot.id} · ${lot.itemName || ''}${lot.levels ? ' · ' + lot.levels : ''}`"
              :value="lot.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="!isMicroMode">
          <el-button
            type="primary"
            :loading="sending"
            @click="handleSend"
            :disabled="!connectionStatus.online"
          >
            {{ sending ? '发送中...' : (testForm.qcMode ? '发送质控数据' : '发送测试数据') }}
          </el-button>
          <el-button @click="handlePreview">预览报文</el-button>
          <el-tag v-if="connectionStatus.online" size="small" :type="testForm.qcMode ? 'warning' : 'success'" effect="plain">
            <template v-if="testForm.qcMode">质控模式 · 走 send-qc 接口</template>
            <template v-else>
              {{ connectionStatus.connectionSource === 'SIM'
                ? '将通过模拟链路送到解析器'
                : '将通过真实连接写入仪器' }}
            </template>
          </el-tag>
        </el-form-item>

        <el-form-item v-if="isMicroMode">
          <el-button type="primary" :loading="sending" @click="handleSendMicro">
            {{ sending ? '回传中...' : '模拟回传微生物数据' }}
          </el-button>
          <el-tag type="warning" size="small" effect="plain">微生物·药敏模式 · 走 send-micro 接口</el-tag>
        </el-form-item>
      </el-form>

      <!-- ============================ 微生物药敏模式：录入区 ============================ -->
      <div v-if="isMicroMode" class="micro-editor">
        <div class="micro-toolbar">
          <span class="micro-title">细菌鉴定 + 药敏结果（模拟通信程序回传）</span>
          <el-select
            v-model="microPreset"
            placeholder="选择预设测试案例"
            clearable
            style="width: 300px"
            @change="applyMicroPreset"
          >
            <el-option
              v-for="(preset) in microPresetList"
              :key="preset.key"
              :label="preset.label"
              :value="preset.key"
            />
          </el-select>
          <el-button type="success" size="small" plain @click="addOrganism">＋ 添加细菌</el-button>
        </div>

        <div class="micro-bar-codes">
          <el-form :inline="true">
            <el-form-item label="采集条码">
              <el-input v-model="microForm.sampleBarcode" placeholder="样本条码，如 SM-BC-001" clearable style="width: 220px" />
            </el-form-item>
            <el-form-item label="培养基条码">
              <el-input v-model="microForm.plateBarcode" placeholder="培养基条码，可空（按采集条码匹配）" clearable style="width: 240px" />
            </el-form-item>
            <el-tooltip content="至少填一项：优先按培养基条码匹配微生物报告，其次按采集条码匹配" placement="top">
              <el-tag size="small" type="info" effect="plain">目标微生物报告须处于未终审/可编辑状态</el-tag>
            </el-tooltip>
          </el-form>
        </div>

        <div v-for="(og, oi) in microForm.organisms" :key="oi" class="organism-block">
          <div class="og-header">
            <span class="og-idx">细菌 {{ oi + 1 }}</span>
            <el-input v-model="og.organismName" placeholder="细菌名称，如 金黄色葡萄球菌" style="width: 200px" />
            <el-input v-model="og.organismCode" placeholder="细菌编码，如 SAU" style="width: 130px" />
            <el-input v-model="og.quantity" placeholder="数量，如 大量" style="width: 120px" />
            <el-button type="primary" size="small" plain @click="addSensitivity(oi)">＋ 药敏</el-button>
            <el-button type="danger" size="small" plain @click="removeOrganism(oi)">删除细菌</el-button>
          </div>
          <el-table :data="og.sensitivities" border size="small" class="sens-table">
            <el-table-column label="抗生素名称" min-width="130">
              <template #default="{ row }">
                <el-input v-model="row.antibioticName" placeholder="如 苯唑西林" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="编码" min-width="100">
              <template #default="{ row }">
                <el-input v-model="row.antibioticCode" placeholder="如 OXA" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="MIC" min-width="110">
              <template #default="{ row }">
                <el-input v-model="row.micValue" placeholder="如 >2" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="结果" min-width="90" align="center">
              <template #default="{ row }">
                <el-select v-model="row.result" size="small" style="width: 80px">
                  <el-option label="敏感 S" value="S" />
                  <el-option label="中介 I" value="I" />
                  <el-option label="耐药 R" value="R" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="折点" min-width="110">
              <template #default="{ row }">
                <el-input v-model="row.breakpoint" placeholder="如 ≤2" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="药敏方法" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.method" placeholder="如 MIC/K-B" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="removeSensitivity(oi, row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="microResult" class="micro-result">
          <el-alert :type="microResult.type" :closable="false" :show-icon="true" :title="microResult.title">
            <template v-if="microResult.lines">
              <div v-for="(line, i) in microResult.lines" :key="i" class="micro-result-line">{{ line }}</div>
            </template>
          </el-alert>
        </div>
      </div>

      <!-- 报文预览 / 可编辑 -->
      <div v-if="previewData !== undefined" class="preview-section">
        <div class="preview-title">
          待发送报文 (ASTM)  —  支持直接修改后再发送
          <span class="title-actions">
            <el-button link type="primary" size="small" @click="resetPreviewTemplate">重置为模板</el-button>
            <el-button link type="primary" size="small" @click="clearPreview">清空</el-button>
            <el-button link type="primary" size="small" @click="previewData = undefined">收起</el-button>
          </span>
        </div>
        <el-input
          v-model="previewData"
          type="textarea"
          :autosize="{ minRows: 8, maxRows: 20 }"
          placeholder="在此编辑报文内容，将按文本原样发送"
          class="preview-editable"
        />
        <div class="preview-tip">
          提示：修改后的内容将作为最终报文发送，报文通过模拟链路送达系统解析器。
        </div>
      </div>
    </el-card>

    <!-- 顶部条件 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="仪器">
          <el-select
            v-model="searchForm.instrumentId"
            placeholder="全部仪器"
            filterable
            clearable
            style="width: 220px"
          >
            <el-option
              v-for="item in instrumentList"
              :key="item.id"
              :label="item.instrumentName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain @click="handleSearch">查询</el-button>
          <el-button @click="handleRefresh">刷新</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="时间" width="170" align="center">
          <template #default="{ row }">{{ formatDateTime(row.createTime || row.messageTime) }}</template>
        </el-table-column>
        <el-table-column label="仪器" width="150" align="center">
          <template #default="{ row }">{{ instrumentName(row.instrumentId) }}</template>
        </el-table-column>
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'RECEIVE' ? 'success' : 'primary'" size="small">
              {{ row.direction === 'RECEIVE' ? '接收' : '发送' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="消息内容" min-width="300">
          <template #default="{ row }">
            <div class="message-content" :title="row.rawMessage">{{ truncate(row.rawMessage, 80) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="解析结果" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.parseStatus === 'SUCCESS'" type="success" size="small">成功</el-tag>
            <el-tag v-else-if="row.parseStatus === 'FAIL'" type="danger" size="small">失败</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleViewDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="通讯报文详情" width="800px">
      <div v-if="currentLog" class="detail-content">
        <el-descriptions :column="2" border size="small" title="基本信息">
          <el-descriptions-item label="仪器">{{ instrumentName(currentLog.instrumentId) }}</el-descriptions-item>
          <el-descriptions-item label="方向">
            <el-tag :type="currentLog.direction === 'RECEIVE' ? 'success' : 'primary'" size="small">
              {{ currentLog.direction === 'RECEIVE' ? '接收' : '发送' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatDateTime(currentLog.createTime || currentLog.messageTime) }}</el-descriptions-item>
          <el-descriptions-item label="解析状态">
            <el-tag v-if="currentLog.parseStatus === 'SUCCESS'" type="success" size="small">成功</el-tag>
            <el-tag v-else-if="currentLog.parseStatus === 'FAIL'" type="danger" size="small">失败</el-tag>
            <span v-else class="muted">-</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 解析后的结构化数据 -->
        <div v-if="parsedData.length" class="parsed-section">
          <div class="raw-title">解析结果 ({{ parsedData.length }} 条检验结果)</div>
          <el-table :data="parsedData" border size="small" max-height="320">
            <el-table-column prop="sampleNo" label="样本号" width="140" />
            <el-table-column prop="testCode" label="检验代码" width="110" />
            <el-table-column prop="value" label="结果值" width="110" />
            <el-table-column prop="unit" label="单位" width="110" />
            <el-table-column prop="refRange" label="参考范围" width="130" />
            <el-table-column prop="abnormalFlag" label="异常标志" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.abnormalFlag === 'H'" type="danger" size="small">偏高</el-tag>
                <el-tag v-else-if="row.abnormalFlag === 'L'" type="warning" size="small">偏低</el-tag>
                <el-tag v-else type="info" size="small">正常</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 原始报文 -->
        <div class="raw-section">
          <div class="raw-title">原始报文</div>
          <pre class="raw-message">{{ currentLog.rawMessage || '-' }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  getCommLogList,
  getAllInstruments,
  getInstrumentBrands,
  sendInstrumentTestData,
  getInstrumentSingleStatus,
  sendInstrumentRawData,
  simulateInstrumentConnect,
  simulateInstrumentDisconnect,
  getInstrumentQcLots,
  sendInstrumentQcData,
  sendInstrumentMicroData,
  type CommLog,
  type CommLogQuery,
  type Instrument
} from '@/api/instrument'
import { formatDateTime } from '@/utils/format'

const route = useRoute()

/* ============================ 内置检验项目组 (与服务器协议一致) ============================ */
// 每个项目: [检验代码, 名称, 单位, 参考范围, 默认值]
const panels = {
  血常规: {
    code: 'CBP',
    items: [
      ['WBC', '白细胞计数', '10^9/L', '3.5-9.5', '6.5'],
      ['RBC', '红细胞计数', '10^12/L', '4.3-5.8', '4.8'],
      ['HGB', '血红蛋白', 'g/L', '130-175', '140'],
      ['HCT', '红细胞压积', '%', '40-50', '45'],
      ['MCV', '平均红细胞体积', 'fL', '82-100', '90'],
      ['MCH', '平均红细胞血红蛋白量', 'pg', '27-34', '30'],
      ['MCHC', '平均红细胞血红蛋白浓度', 'g/L', '316-354', '335'],
      ['RDW', '红细胞分布宽度', '%', '11-16', '13'],
      ['PLT', '血小板计数', '10^9/L', '125-350', '220'],
      ['PDW', '血小板分布宽度', 'fL', '9-17', '12'],
      ['MPV', '平均血小板体积', 'fL', '9-13', '10.5'],
      ['PCT', '血小板压积', '%', '0.108-0.282', '0.22'],
      ['NEUT', '中性粒细胞计数', '10^9/L', '1.8-6.3', '3.8'],
      ['LYMPH', '淋巴细胞计数', '10^9/L', '1.1-3.2', '2.0'],
      ['MONO', '单核细胞计数', '10^9/L', '0.1-0.6', '0.4'],
      ['EOS', '嗜酸性粒细胞计数', '10^9/L', '0.02-0.52', '0.15'],
      ['BASO', '嗜碱性粒细胞计数', '10^9/L', '0-0.06', '0.03'],
      ['NEUT_P', '中性粒细胞百分比', '%', '40-75', '58'],
      ['LYMPH_P', '淋巴细胞百分比', '%', '20-50', '31'],
      ['MONO_P', '单核细胞百分比', '%', '3-10', '6'],
      ['EOS_P', '嗜酸性粒细胞百分比', '%', '0.4-8', '2.3'],
      ['BASO_P', '嗜碱性粒细胞百分比', '%', '0-1', '0.5']
    ]
  },
  尿常规: {
    code: 'URINE',
    items: [
      ['U_COLOR', '尿颜色', '', '', '黄色'],
      ['U_CLARITY', '尿透明度', '', '', '透明'],
      ['U_GLU', '尿葡萄糖', '', '阴性', '阴性'],
      ['U_PRO', '尿蛋白', '', '阴性', '阴性'],
      ['U_BLD', '尿隐血', '', '阴性', '阴性'],
      ['U_LEU', '尿白细胞', '', '阴性', '阴性'],
      ['U_SG', '尿比重', '', '1.003-1.030', '1.020'],
      ['U_PH', '尿酸碱度', '', '4.6-8.0', '6.0'],
      ['U_M_RBC', '尿镜检红细胞', '/HP', '0-3', '1'],
      ['U_M_WBC', '尿镜检白细胞', '/HP', '0-5', '2'],
      ['U_M_EPI', '尿镜检上皮细胞', '/HP', '0-3', '1'],
      ['U_M_CAST', '尿镜检管型', '/HP', '阴性', '阴性'],
      ['U_M_BACT', '尿镜检细菌', '/HP', '阴性', '阴性']
    ]
  },
  大便常规: {
    code: 'STOOL',
    items: [
      ['S_APP', '大便外观', '', '', '软'],
      ['S_COLOR', '大便颜色', '', '', '黄褐色'],
      ['S_OB', '大便隐血', '', '阴性', '阴性'],
      ['S_WBC', '大便白细胞', '/HP', '0-3', '1'],
      ['S_RBC', '大便红细胞', '/HP', '0-3', '0'],
      ['S_PUS', '大便脓细胞', '/HP', '阴性', '阴性'],
      ['S_OVA', '大便虫卵', '', '阴性', '阴性'],
      ['S_MUC', '大便粘液', '', '阴性', '阴性']
    ]
  },
  生化: {
    code: 'CHEM',
    items: [
      ['GLU', '葡萄糖', 'mmol/L', '3.9-6.1', '5.2'],
      ['ALT', '谷丙转氨酶', 'U/L', '9-50', '25'],
      ['AST', '谷草转氨酶', 'U/L', '15-40', '22'],
      ['TP', '总蛋白', 'g/L', '65-85', '72'],
      ['ALB', '白蛋白', 'g/L', '40-55', '46'],
      ['TBIL', '总胆红素', 'umol/L', '3.4-17.1', '12'],
      ['DBIL', '直接胆红素', 'umol/L', '0-6.8', '3'],
      ['CRE', '肌酐', 'umol/L', '57-97', '75'],
      ['BUN', '尿素', 'mmol/L', '2.9-8.2', '5.5'],
      ['UA', '尿酸', 'umol/L', '208-428', '320']
    ]
  },
  // 微生物药敏模式：不走 ASTM 报文，直接调用 send-micro 接口回传细菌鉴定 + 药敏结果
  微生物药敏: {
    code: 'MICRO',
    microMode: true,
    items: []
  }
}

const loading = ref(false)
const tableData = ref<CommLog[]>([])
const instrumentList = ref<Instrument[]>([])
const brandList = ref<string[]>([])
const searchForm = reactive<CommLogQuery>({
  instrumentId: undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 50,
  total: 0
})

const detailDialogVisible = ref(false)
const currentLog = ref<CommLog | null>(null)

/* ============================ 手工验证 ============================ */
const testForm = reactive<{
  instrumentId?: number
  brand: string
  panel: string
  sampleNo: string
  random: boolean
  qcMode: boolean
  qcLotId?: number
}>({
  instrumentId: undefined,
  brand: 'MINDRAY',
  panel: '血常规',
  sampleNo: '',
  random: false,
  qcMode: false,
  qcLotId: undefined
})
const sending = ref(false)
const previewData = ref<string | undefined>(undefined)

/* ============================ 微生物药敏模式 ============================ */
/** 当前是否为「微生物药敏」项目组（走 send-micro 接口，跳过 ASTM 报文） */
const isMicroMode = computed(() => (panels as any)[testForm.panel]?.microMode === true)

/** 内置预设测试案例：覆盖常见耐药规则（与 08-expert-rules.sql 内置规则一一对应） */
interface MicroPreset {
  key: string
  label: string
  hint: string
  plateBarcode?: string
  sampleBarcode: string
  organisms: Array<{
    organismCode: string
    organismName: string
    quantity: string
    sensitivities: Array<{
      antibioticCode: string
      antibioticName: string
      micValue: string
      result: 'S' | 'I' | 'R' | string
      breakpoint: string
      method: string
    }>
  }>
}
const microPresetList: MicroPreset[] = [
  {
    key: 'MRSA',
    label: 'MRSA — 金黄色葡萄球菌 对苯唑西林/头孢西丁 耐药',
    hint: '应命中内置规则 MRSA（SAU → OXA/CFZ 任一耐药）',
    sampleBarcode: 'SM-ST-001',
    organisms: [
      {
        organismCode: 'SAU',
        organismName: '金黄色葡萄球菌',
        quantity: '大量',
        sensitivities: [
          { antibioticCode: 'OXA', antibioticName: '苯唑西林', micValue: '>4', result: 'R', breakpoint: '≤2', method: 'MIC' },
          { antibioticCode: 'CFZ', antibioticName: '头孢唑林', micValue: '>8', result: 'R', breakpoint: '≤2', method: 'MIC' },
          { antibioticCode: 'PEN', antibioticName: '青霉素', micValue: '>0.5', result: 'R', breakpoint: '≤0.12', method: 'MIC' },
          { antibioticCode: 'VAN', antibioticName: '万古霉素', micValue: '1.0', result: 'S', breakpoint: '≤2', method: 'MIC' }
        ]
      }
    ]
  },
  {
    key: 'CRE',
    label: 'CRE — 大肠埃希菌 对碳青霉烯类(亚胺培南/美罗培南) 耐药',
    hint: '应命中内置规则 CRE（ECO → IPM/MEM/ETP 任一耐药，危急值）',
    sampleBarcode: 'SM-BC-001',
    organisms: [
      {
        organismCode: 'ECO',
        organismName: '大肠埃希菌',
        quantity: '大量',
        sensitivities: [
          { antibioticCode: 'IPM', antibioticName: '亚胺培南', micValue: '>16', result: 'R', breakpoint: '≤1', method: 'MIC' },
          { antibioticCode: 'MEM', antibioticName: '美罗培南', micValue: '>16', result: 'R', breakpoint: '≤1', method: 'MIC' },
          { antibioticCode: 'CAZ', antibioticName: '头孢他啶', micValue: '>32', result: 'R', breakpoint: '≤4', method: 'MIC' },
          { antibioticCode: 'CIP', antibioticName: '环丙沙星', micValue: '>4', result: 'R', breakpoint: '≤0.5', method: 'MIC' }
        ]
      }
    ]
  },
  {
    key: 'VRE',
    label: 'VRE — 肠球菌 对万古霉素 耐药',
    hint: '应命中内置规则 VRE（EFA/EFM → VAN 耐药）',
    sampleBarcode: 'SM-ST-001',
    organisms: [
      {
        organismCode: 'EFA',
        organismName: '粪肠球菌',
        quantity: '中量',
        sensitivities: [
          { antibioticCode: 'VAN', antibioticName: '万古霉素', micValue: '>32', result: 'R', breakpoint: '≤4', method: 'MIC' },
          { antibioticCode: 'AMP', antibioticName: '氨苄西林', micValue: '2.0', result: 'S', breakpoint: '≤8', method: 'MIC' }
        ]
      }
    ]
  },
  {
    key: 'CRAB',
    label: 'CRAB — 鲍曼不动杆菌 对亚胺培南/美罗培南 耐药',
    hint: '应命中内置规则 CRAB（ABA → IPM/MEM 任一耐药）',
    sampleBarcode: 'SM-BC-001',
    organisms: [
      {
        organismCode: 'ABA',
        organismName: '鲍曼不动杆菌',
        quantity: '中量',
        sensitivities: [
          { antibioticCode: 'IPM', antibioticName: '亚胺培南', micValue: '>16', result: 'R', breakpoint: '≤4', method: 'MIC' },
          { antibioticCode: 'MEM', antibioticName: '美罗培南', micValue: '>16', result: 'R', breakpoint: '≤4', method: 'MIC' }
        ]
      }
    ]
  }
]

const microPreset = ref<string | undefined>(undefined)

interface MicroOrganismForm {
  organismCode: string
  organismName: string
  quantity: string
  sensitivities: Array<{
    antibioticCode?: string
    antibioticName: string
    micValue?: string
    result?: 'S' | 'I' | 'R' | string
    breakpoint?: string
    method?: string
  }>
}
const microForm = reactive<{
  plateBarcode: string
  sampleBarcode: string
  organisms: MicroOrganismForm[]
}>({
  plateBarcode: '',
  sampleBarcode: '',
  organisms: []
})

const microResult = ref<{
  type: 'success' | 'warning' | 'error'
  title: string
  lines?: string[]
} | undefined>(undefined)

function newEmptySensitivity() {
  return { antibioticName: '', antibioticCode: '', micValue: '', result: 'S', breakpoint: '', method: 'MIC' }
}
function newEmptyOrganism(): MicroOrganismForm {
  return { organismCode: '', organismName: '', quantity: '', sensitivities: [newEmptySensitivity()] }
}
function addOrganism() {
  microForm.organisms.push(newEmptyOrganism())
}
function removeOrganism(index: number) {
  microForm.organisms.splice(index, 1)
}
function addSensitivity(orgIndex: number) {
  microForm.organisms[orgIndex].sensitivities.push(newEmptySensitivity())
}
function removeSensitivity(orgIndex: number, row: MicroOrganismForm['sensitivities'][number]) {
  const list = microForm.organisms[orgIndex].sensitivities
  const idx = list.indexOf(row)
  if (idx > -1) list.splice(idx, 1)
}

/** 应用预设测试案例：填充条码 + 细菌 + 药敏 */
function applyMicroPreset(key: string | undefined) {
  if (!key) return
  const preset = microPresetList.find((p) => p.key === key)
  if (!preset) return
  microForm.plateBarcode = preset.plateBarcode ?? ''
  microForm.sampleBarcode = preset.sampleBarcode
  microForm.organisms = preset.organisms.map((o) => ({
    organismCode: o.organismCode,
    organismName: o.organismName,
    quantity: o.quantity,
    sensitivities: o.sensitivities.map((s) => ({ ...s }))
  }))
}

/** 模拟通信程序回传细菌鉴定 + 药敏结果 */
async function handleSendMicro() {
  if (!testForm.instrumentId) {
    ElMessage.warning('请选择仪器')
    return
  }
  if (!microForm.sampleBarcode && !microForm.plateBarcode) {
    ElMessage.warning('请填写采集条码或培养基条码（至少一项）')
    return
  }
  if (!microForm.organisms.length || microForm.organisms.some((o) => !o.organismName)) {
    ElMessage.warning('请至少填写一个细菌（名称必填）')
    return
  }
  sending.value = true
  microResult.value = undefined
  try {
    const res = await sendInstrumentMicroData({
      instrumentId: testForm.instrumentId!,
      plateBarcode: microForm.plateBarcode || undefined,
      sampleBarcode: microForm.sampleBarcode || undefined,
      organisms: microForm.organisms.map((o) => ({
        organismCode: o.organismCode || undefined,
        organismName: o.organismName,
        quantity: o.quantity || undefined,
        sensitivities: o.sensitivities
          .filter((s) => s.antibioticName)
          .map((s) => ({
            antibioticCode: s.antibioticCode || undefined,
            antibioticName: s.antibioticName,
            micValue: s.micValue || undefined,
            result: s.result || undefined,
            breakpoint: s.breakpoint || undefined,
            method: s.method || undefined
          }))
      }))
    })
    if (res?.parseStatus === 'FAIL') {
      microResult.value = {
        type: 'error',
        title: '微生物数据回传失败',
        lines: [res.parseMsg || '未知错误', `仪器 ${res.instrumentId ?? ''}`]
      }
    } else {
      const lines = [
        `已回传 细菌 ${res?.organismCount ?? microForm.organisms.length} 株 · 药敏 ${res?.sensitivityCount ?? 0} 项`,
        `目标报告：${res?.report ? '已匹配并写入' : '已送达（请在微生物报告/耐药告警中核对）'}`
      ]
      if (res?.parseMsg) lines.push(res.parseMsg)
      microResult.value = { type: 'success', title: '微生物数据已回传，耐药规则联动已触发', lines }
    }
    // 发送后刷新日志并定位到该仪器
    searchForm.instrumentId = testForm.instrumentId
    handleRefresh()
  } catch (e: any) {
    microResult.value = { type: 'error', title: '微生物数据回传失败', lines: [e?.message || '未知错误'] }
  } finally {
    sending.value = false
  }
}

/** 切到微生物模式时，预置一条空白细菌 */
watch(
  isMicroMode,
  (on) => {
    if (on && microForm.organisms.length === 0) {
      addOrganism()
    }
  },
  { immediate: true }
)

/* ========== 质控模式：质控品批号候选列表 ========== */
const qcLotList = ref<Array<{ id: number; lotNo?: string; levels?: string; expireDate?: string; testItemId?: number; itemName?: string; itemCode?: string }>>([])
const qcLotListLoading = ref(false)
async function loadQcLotList(instrumentId: number | undefined) {
  qcLotList.value = []
  if (!instrumentId) return
  qcLotListLoading.value = true
  try {
    qcLotList.value = await getInstrumentQcLots(instrumentId)
  } catch (e) {
    // 若接口不可用则静默（老版本后端未启用 send-qc 也可以正常发普通报文）
    qcLotList.value = []
  } finally {
    qcLotListLoading.value = false
  }
}

// 连接状态
const selectedInstrument = computed<Instrument | undefined>(() =>
  instrumentList.value.find((i) => i.id === testForm.instrumentId)
)
const connectionStatus = reactive<{
  online: boolean
  connectType?: string
  ipAddress?: string
  port?: number
  serialPort?: string
  baudRate?: number
  endpoint?: string
  lastCommTime?: string
  connectionSource?: 'REAL' | 'SIM' | 'NONE'
  connectTime?: string
}>({
  online: false
})
const connecting = ref(false)
const loadingStatus = ref(false)

// 切换仪器时自动刷新连接状态 & 切换品牌
async function refreshConnStatus() {
  if (!testForm.instrumentId) return
  loadingStatus.value = true
  try {
    const st = await getInstrumentSingleStatus(testForm.instrumentId)
    connectionStatus.online = !!st.online
    connectionStatus.connectType = st.connectType
    connectionStatus.ipAddress = st.ipAddress
    connectionStatus.port = st.port
    connectionStatus.serialPort = st.serialPort
    connectionStatus.baudRate = st.baudRate
    connectionStatus.endpoint = st.endpoint
    connectionStatus.lastCommTime = st.lastCommTime
    connectionStatus.connectionSource = st.connectionSource ?? (st.online ? 'REAL' : 'NONE')
    connectionStatus.connectTime = st.connectTime
  } catch (e: any) {
    // 状态接口不可用则回退：仅使用仪器表自身配置，online=false
    const ins = selectedInstrument.value
    if (ins) {
      connectionStatus.online = false
      connectionStatus.connectType = ins.connectType
      connectionStatus.ipAddress = ins.ipAddress
      connectionStatus.port = ins.port
      connectionStatus.serialPort = ins.serialPort
      connectionStatus.baudRate = ins.baudRate
      connectionStatus.endpoint = undefined
      connectionStatus.connectionSource = 'NONE'
    }
  } finally {
    loadingStatus.value = false
  }
}

/** 模拟连接到 LIS 系统（不拨号实体仪器，只在后端内存标记为"已接入"），
 *  之后发送报文会被当作"仪器送到系统的数据"送入解析/结果匹配流程 */
async function handleSimulateConnect() {
  const ins = selectedInstrument.value
  if (!ins) {
    ElMessage.warning('请先选择仪器')
    return
  }
  connecting.value = true
  try {
    const res = await simulateInstrumentConnect(ins.id!)
    if (res?.online) {
      ElMessage.success('已模拟接入到系统')
      await refreshConnStatus()
    } else {
      ElMessage.error('模拟接入失败')
    }
  } catch (e: any) {
    ElMessage.error('模拟接入失败：' + (e?.message || '未知错误'))
  } finally {
    connecting.value = false
  }
}

/** 模拟断开（清掉后端内存里的 mock 标记） */
async function handleSimulateDisconnect() {
  const ins = selectedInstrument.value
  if (!ins) return
  connecting.value = true
  try {
    await simulateInstrumentDisconnect(ins.id!)
    ElMessage.success('已断开')
    await refreshConnStatus()
  } catch (e: any) {
    ElMessage.error('断开失败：' + (e?.message || '未知错误'))
  } finally {
    connecting.value = false
  }
}

// 随机值生成
function rnd(min: number, max: number, digits = 1): number {
  return Number((min + Math.random() * (max - min)).toFixed(digits))
}

// 根据参考范围计算异常标志
function abnormalFlag(valueStr: string, refRange?: string): string {
  if (!refRange || !refRange.includes('-')) return 'N'
  const v = Number(valueStr)
  if (isNaN(v)) return 'N'
  const [lo, hi] = refRange.split('-').map(Number)
  if (!isNaN(hi) && v > hi) return 'H'
  if (!isNaN(lo) && v < lo) return 'L'
  return 'N'
}

// 构造 ASTM H/P/O/R/L 报文
function buildAstmMessage(): string {
  const panel = panels[testForm.panel as keyof typeof panels]
  const sampleNo = testForm.sampleNo || 'SIM' + Date.now().toString().slice(-8)
  const now = new Date()
  const dateStr = now.toISOString().slice(0, 10).replace(/-/g, '')
  const lines: string[] = []
  lines.push('H|\\^&|||SIMULATOR^^^^|||LIS||||||P|1|' + dateStr)
  lines.push('P|1|||SIM^Patient||19800101|M|||||^||')
  lines.push(
    'O|1|' + sampleNo + '^^^||' + testForm.panel + '|N|' +
    now.toISOString().slice(0, 14).replace(/[-T:]/g, '') + '||||||N||||||||||O'
  )
  let seq = 1
  for (const it of panel.items) {
    const [code, , unit, refRange, def] = it
    let value: string = def
    if (testForm.random && refRange && refRange.includes('-') && !isNaN(Number(refRange.split('-')[0]))) {
      const [lo, hi] = refRange.split('-').map(Number)
      value = String(rnd(lo, hi, 1))
    }
    const flag = abnormalFlag(value, refRange)
    lines.push(
      'R|' + (seq++) + '|^^^' + code + '|' + value + '|' + unit + '|' +
      (refRange || '') + '|' + flag + '||F|||||||||'
    )
  }
  lines.push('L|1|N')
  return lines.join('\n') + '\n'
}

function handleInstrumentChange(id?: number) {
  const ins = instrumentList.value.find((i) => i.id === id)
  if (ins?.brand) {
    testForm.brand = ins.brand.toUpperCase()
  }
  // 切仪器时清空连接态，再刷新一次状态
  Object.assign(connectionStatus, {
    online: false,
    connectType: undefined,
    ipAddress: undefined,
    port: undefined,
    serialPort: undefined,
    baudRate: undefined,
    endpoint: undefined,
    lastCommTime: undefined,
    connectionSource: 'NONE',
    connectTime: undefined
  })
  if (id) {
    refreshConnStatus()
  }
}

watch(
  () => testForm.instrumentId,
  (v) => {
    if (v != null) {
      refreshConnStatus()
      loadQcLotList(v)
    } else {
      qcLotList.value = []
      testForm.qcLotId = undefined
    }
  }
)
// 关闭质控模式时，清空已选质控批号
watch(
  () => testForm.qcMode,
  (on) => {
    if (!on) testForm.qcLotId = undefined
  }
)

function handlePreview() {
  if (!testForm.instrumentId) {
    ElMessage.warning('请先选择仪器')
    return
  }
  previewData.value = buildAstmMessage()
}

function resetPreviewTemplate() {
  if (!testForm.instrumentId) {
    ElMessage.warning('请先选择仪器后再重置模板')
    return
  }
  previewData.value = buildAstmMessage()
}

function clearPreview() {
  previewData.value = ''
}

async function handleSend() {
  if (!testForm.instrumentId) {
    ElMessage.warning('请选择仪器')
    return
  }
  // 统一发送规则：必须先"接入系统"(模拟或真实连接) 才能发数据，
  // 因为后端 InstrumentController.send 会根据接入来源自动路由：
  //   - 真实连接 → 通过 socket 写入仪器
  //   - 模拟连接 → 当作仪器入站数据送到 Handler → 解析器 → 结果匹配（核心需求）
  if (!connectionStatus.online) {
    ElMessage.warning('请先点击「模拟连接到系统」再发送数据')
    return
  }
  // 优先发送用户编辑后的报文；若预览区未显示或为空，则按当前表单值生成
  let rawData: string
  if (previewData.value !== undefined && previewData.value.trim() !== '') {
    rawData = previewData.value
  } else {
    rawData = buildAstmMessage()
    previewData.value = rawData
  }
  sending.value = true
  try {
    if (testForm.qcMode) {
      // ============ 质控模式 ============
      // 从当前表单（项目组+样本号+默认值/随机值）生成 forceParseMap，
      // 然后走 send-qc 专用接口（后端统一按 QC 前缀分流到质控落库）
      const panelCfg = (panels as any)[testForm.panel] as any
      const items: any[][] = (panelCfg?.items ?? []) as any[][]
      const sampleNo = normalizeQcSampleNo(testForm.sampleNo)
      const forceParseMap = items.map((it: any[]) => {
        const [code, , unit, ref, defVal] = it
        const v = testForm.random
          ? randomValueFor(defVal as string, ref as string)
          : (defVal as string)
        return { sampleNo, testCode: code as string, value: String(v), unit: unit ?? '', refRange: ref ?? '', abnormalFlag: '' }
      })
      const res = await sendInstrumentQcData({
        instrumentId: testForm.instrumentId!,
        rawData,
        qcLotId: testForm.qcLotId,
        forceParseMap
      })
      previewData.value = rawData
      if (res?.parseStatus === 'FAIL') {
        ElMessage.error('质控发送失败：' + (res?.parseMsg || '未知错误'))
      } else {
        const ok = res?.qcOk ?? (res?.qtyInput ?? forceParseMap.length)
        const fail = res?.qcFail ?? 0
        const msg = fail === 0
          ? `质控数据已送达：成功 ${ok} 条`
          : `质控发送完成：成功 ${ok} 条，失败 ${fail} 条`
        ElMessage.success(msg + ' — ' + (res?.parseMsg || ''))
      }
    } else {
      // ============ 普通模式 ============
      // 接入在线：统一走 sendInstrumentRawData，后端按 connectionSource 自动分流
      await sendInstrumentRawData(testForm.instrumentId!, rawData)
      previewData.value = rawData
      ElMessage.success(
        connectionStatus.connectionSource === 'SIM'
          ? '测试报文已送达系统解析，结果匹配流程已触发'
          : '测试数据已送达系统解析流程'
      )
    }
    // 发送后自动刷新日志并定位到该仪器
    searchForm.instrumentId = testForm.instrumentId
    handleRefresh()
    // 顺便刷新连接状态 (真实发送会更新 lastCommTime)
    await refreshConnStatus()
  } catch (e: any) {
    ElMessage.error('发送失败：' + (e?.message || '请检查后端服务或连接状态'))
  } finally {
    sending.value = false
  }
}

/** 若样本号不是 QC 前缀，自动补 QC-（用于质控模式发送） */
function normalizeQcSampleNo(s: string | undefined | null): string {
  const raw = (s ?? '').trim() || ('QC' + new Date().getTime().toString().slice(-6))
  if (/^(QC|QCK|QCL|QCT|LJ|L1|L2|L3|N1|N2|N3|Q1|Q2|Q3|LEV|LEVEL|CON|CTRL)/i.test(raw)) return raw
  return 'QC-' + raw
}

/* ============================ 日志查询 ============================ */

// 尝试解析 parseResult 为结构化结果数组
const parsedData = computed(() => {
  if (!currentLog.value?.parseResult) return []
  try {
    const obj = JSON.parse(currentLog.value.parseResult)
    if (Array.isArray(obj)) {
      return obj
    }
    if (obj && typeof obj === 'object') {
      return [obj]
    }
  } catch (e) {
    // 非 JSON 格式, 返回空数组
  }
  return []
})

// 根据 instrumentId 反查仪器名称
function instrumentName(id: number | undefined): string {
  if (id === undefined || id === null) return '-'
  const ins = instrumentList.value.find((i) => i.id === id)
  return ins?.instrumentName || `仪器(${id})`
}

function truncate(text: string | undefined, max: number): string {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '...' : text
}

async function loadInstruments() {
  try {
    instrumentList.value = await getAllInstruments()
  } catch (e) {
    // ignore
  }
}

async function loadBrands() {
  try {
    const list = await getInstrumentBrands()
    if (list && list.length) {
      brandList.value = list
    }
  } catch (e) {
    // ignore
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getCommLogList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      instrumentId: searchForm.instrumentId
    })
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

function handleRefresh() {
  fetchList()
}

function handleReset() {
  searchForm.instrumentId = undefined
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

function handleViewDetail(row: CommLog) {
  currentLog.value = row
  detailDialogVisible.value = true
}

onMounted(async () => {
  await loadInstruments()
  loadBrands()
  // 支持从仪器配置页跳转：?instrumentId=xxx 预选仪器并仅查看该仪器日志
  const qid = route.query.instrumentId
  if (qid) {
    const id = Number(qid)
    if (!Number.isNaN(id)) {
      testForm.instrumentId = id
      searchForm.instrumentId = id
    }
  }
  fetchList()
})
</script>

<style scoped lang="scss">
.comm-log-page {
  padding: 16px;

  .test-card {
    margin-bottom: 12px;
    border: 1px solid var(--el-color-primary-light-5);

    .card-header {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .instrument-row {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .conn-block {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 2px 8px;
      border-radius: 4px;
      background-color: var(--el-fill-color-light);
      border: 1px solid var(--el-border-color-lighter);
    }

    .conn-tag {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 0 8px;
    }
    .conn-tag .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: #a7b4c4;
      display: inline-block;
      transition: background-color .2s;
    }
    .conn-tag .dot.on {
      background-color: #67c23a;
      box-shadow: 0 0 0 2px rgba(103, 194, 58, .25);
    }

    .preview-section {
      margin-top: 8px;
      padding: 12px;
      background-color: var(--el-fill-color-light);
      border: 1px solid var(--el-border-color);
      border-radius: 4px;

      .preview-title {
        font-size: 14px;
        font-weight: bold;
        margin-bottom: 8px;
        color: var(--el-text-color-primary);
        display: flex;
        align-items: center;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: 8px;

        .title-actions {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          font-weight: normal;
        }
      }

      .preview-editable {
        :deep(.el-textarea__inner) {
          font-family: 'Consolas', 'Monaco', monospace;
          font-size: 12px;
          line-height: 1.6;
          white-space: pre;
          color: var(--el-text-color-primary);
          background-color: #ffffff;
        }
      }

      .preview-tip {
        margin-top: 8px;
        font-size: 12px;
        color: var(--el-color-info);
      }
    }

    .micro-editor {
      margin-top: 8px;
      padding: 12px;
      background-color: var(--el-fill-color-light);
      border: 1px solid var(--el-border-color);
      border-radius: 4px;

      .micro-toolbar {
        display: flex;
        align-items: center;
        gap: 12px;
        flex-wrap: wrap;
        margin-bottom: 8px;

        .micro-title {
          font-size: 14px;
          font-weight: bold;
          color: var(--el-text-color-primary);
        }
      }

      .micro-bar-codes {
        margin-bottom: 4px;
      }

      .organism-block {
        background-color: #ffffff;
        border: 1px solid var(--el-border-color-lighter);
        border-radius: 4px;
        padding: 10px;
        margin-bottom: 8px;

        .og-header {
          display: flex;
          align-items: center;
          gap: 8px;
          flex-wrap: wrap;
          margin-bottom: 8px;

          .og-idx {
            font-weight: bold;
            color: var(--el-color-primary);
          }
        }

        .sens-table {
          width: 100%;
        }
      }

      .micro-result {
        margin-top: 8px;

        .micro-result-line {
          line-height: 1.8;
        }
      }
    }
  }

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

  .message-content {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 12px;
    color: var(--el-text-color-regular);
    cursor: pointer;
  }

  .muted {
    color: var(--el-text-color-placeholder);
  }

  .detail-content {
    .raw-title {
      font-size: 14px;
      font-weight: bold;
      margin: 16px 0 8px 0;
      color: var(--el-text-color-primary);
    }

    .parsed-section {
      margin-top: 8px;
    }

    .raw-section {
      .raw-message {
        background-color: var(--el-fill-color-light);
        border: 1px solid var(--el-border-color);
        border-radius: 4px;
        padding: 12px;
        max-height: 320px;
        overflow: auto;
        font-family: 'Consolas', 'Monaco', monospace;
        font-size: 12px;
        line-height: 1.6;
        white-space: pre-wrap;
        word-break: break-all;
        margin: 0;
        color: var(--el-text-color-regular);
      }
    }
  }
}
</style>