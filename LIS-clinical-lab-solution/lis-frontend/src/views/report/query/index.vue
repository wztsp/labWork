<template>
  <div class="report-query-page">
    <!-- ============ 左侧面板 ============ -->
    <div
      class="left-panel"
      :style="{ width: leftPanelWidth + 'px' }"
      @dragstart.prevent
    >
      <!-- 顶部筛选区 -->
      <div class="filter-bar">
        <div class="filter-row">
          <el-dropdown trigger="click" @command="onQuickDate">
            <el-button size="small" round>
              <el-icon><Clock /></el-icon>
              <span>{{ dateLabel }}</span>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="today">今天</el-dropdown-item>
                <el-dropdown-item command="yesterday">昨天</el-dropdown-item>
                <el-dropdown-item command="week">近7天</el-dropdown-item>
                <el-dropdown-item command="month">近30天</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            size="small"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="flex:1;min-width:280px"
          />

          <el-select v-model="reportKind" size="small" style="width: 110px" @change="handleSearch">
            <el-option label="全部报告" value="all" />
            <el-option label="常规报告" value="regular" />
            <el-option label="微生物报告" value="micro" />
          </el-select>

          <el-input
            v-model="searchKeyword"
            placeholder="姓名/就诊号/标本号/条码/报告号"
            size="small"
            clearable
            style="flex:1;min-width:200px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />

          <el-button type="primary" size="small" :icon="Search" @click="handleSearch">查询</el-button>
        </div>
      </div>

      <!-- 报告列表 -->
      <div class="list-table-wrapper">
        <el-table
          v-loading="loading"
          :data="filteredList"
          border
          stripe
          size="small"
          highlight-current-row
          height="calc(100vh - 280px)"
          @row-click="handleRowClick"
        >
          <el-table-column type="selection" width="40" align="center" />
          <el-table-column prop="source" label="来源" width="50" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.__kind === 'micro'" type="danger" size="small" effect="plain">微</el-tag>
              <span v-else>{{ row.source || '门' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="72" align="center">
            <template #default="{ row }">
              <template v-if="row.__kind === 'micro'">
                <el-tag v-if="row.status === 3" type="success" size="small" effect="dark">最终</el-tag>
                <el-tag v-else-if="row.status === 2" type="warning" size="small">鉴定</el-tag>
                <el-tag v-else type="info" size="small">初步</el-tag>
              </template>
              <template v-else>
                <el-tag v-if="row.status === 3" type="success" size="small" effect="dark">已发布</el-tag>
                <el-tag v-else-if="row.status === 2" type="success" size="small" effect="plain">已审核</el-tag>
                <el-tag v-else-if="row.status === 1" type="warning" size="small">审核中</el-tag>
                <el-tag v-else-if="row.status === 0" type="info" size="small">待审核</el-tag>
                <el-tag v-else size="small">草稿</el-tag>
              </template>
            </template>
          </el-table-column>
          <el-table-column prop="patientName" label="姓名" width="62" show-overflow-tooltip />
          <el-table-column prop="patientGender" label="性别" width="40" align="center">
            <template #default="{ row }">{{ row.patientGender === 1 ? '男' : row.patientGender === 2 ? '女' : '-' }}</template>
          </el-table-column>
          <el-table-column prop="patientAge" label="年龄" width="42" align="center" />
          <el-table-column prop="sampleNo" label="标本号" width="70" show-overflow-tooltip />
          <el-table-column prop="sampleBarcode" label="条码号" width="120" show-overflow-tooltip />
          <el-table-column prop="visitNo" label="就诊号" width="110" show-overflow-tooltip />
          <el-table-column prop="testItems" label="项目" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <template v-if="row.__kind === 'micro'">
                <span v-if="row.applyItems">{{ row.applyItems }}</span>
                <span v-else>{{ formatMicroApplyFallback(row) }}</span>
                <el-tag v-if="row.specimenType" type="info" size="small" effect="plain" style="margin-left:4px;">{{ row.specimenType }}</el-tag>
                <span v-if="row.cultureSite" style="margin-left:4px; color:#909399; font-size:12px;">部位:{{ row.cultureSite }}</span>
              </template>
              <template v-else>
                <span>{{ row.testItems || '-' }}</span>
              </template>
            </template>
          </el-table-column>
          <el-table-column label="报告时间" width="118" align="center" sortable>
            <template #default="{ row }">
              {{ formatShortTime(row.__kind === 'micro' ? (row.auditTime || row.createTime) : (row.publishTime || row.createTime)) }}
            </template>
          </el-table-column>
          <el-table-column prop="reportDoctor" label="报告人" width="62" align="center" />
          <el-table-column label="打印" width="46" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" :icon="Printer" @click.stop="printReport(row)">打</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 患者其他标本 -->
      <div class="other-samples" v-if="reportDetail && reportDetail.__kind !== 'micro' && otherSamples.length > 0">
        <div class="other-samples-header">
          <el-icon><FolderOpened /></el-icon>
          <span>患者其他标本</span>
        </div>
        <el-table :data="otherSamples" border size="small" height="140">
          <el-table-column prop="statusText" label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 4 ? 'success' : 'info'" size="small">{{ row.statusText }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sampleNo" label="标本号" width="110" show-overflow-tooltip />
          <el-table-column prop="instrumentName" label="仪器名称" width="120" show-overflow-tooltip />
          <el-table-column prop="testItems" label="申请项目" min-width="160" show-overflow-tooltip />
          <el-table-column prop="sampleBarcode" label="标本条码" width="120" show-overflow-tooltip />
          <el-table-column prop="patientName" label="患者姓名" width="70" show-overflow-tooltip />
          <el-table-column prop="patientGender" label="性别" width="45" align="center">
            <template #default="{ row }">{{ row.patientGender === 1 ? '男' : row.patientGender === 2 ? '女' : '-' }}</template>
          </el-table-column>
          <el-table-column prop="patientAge" label="年龄" width="50" align="center" />
        </el-table>
      </div>
    </div>

    <!-- 可拖拽分隔条 -->
    <div class="resizer" title="拖动调整列表/详情宽度" @mousedown.prevent="startResize">
      <span class="resizer-grip"></span>
    </div>

    <!-- ============ 右侧面板 ============ -->
    <div class="right-panel">
      <!-- 操作按钮栏: 常规报告 & 微生物报告共用容器, 内容区分 -->
      <div class="action-bar" v-if="reportDetail">
        <template v-if="reportDetail.__kind === 'micro'">
          <el-button size="small" type="primary" :icon="Printer" @click="doPrintMicroReport">打印微生物报告</el-button>
          <el-tag :type="microStatusType(reportDetail.status)" effect="dark" size="small">{{ microStatusText(reportDetail.status) }}</el-tag>
          <span class="no-print" style="color:#909399; margin-left:8px; font-size:12px;">报告号: {{ reportDetail.reportNo || '-' }}  ·  患者: {{ reportDetail.patientName || '-' }}</span>
        </template>
        <template v-else>
          <el-button size="small" :icon="EditPen" @click="openPinyinDialog">调整姓名拼音</el-button>
          <el-button size="small" :icon="Document" @click="openMedicalRecordQuery">病案查询</el-button>
          <el-button size="small" type="primary" :icon="Printer" @click="printA4Report">打印A4报告</el-button>
          <el-button size="small" :icon="Printer" @click="printReportDirect">打印</el-button>
          <el-button size="small" :icon="Setting" @click="openPrintSettings">打印设置</el-button>
          <el-button size="small" :icon="Download" @click="generatePDF">生成PDF</el-button>
          <el-button size="small" :icon="Picture" @click="openSampleImageDialog">标本图</el-button>
          <el-button size="small" type="warning" :icon="EditPen" @click="openModifyDialog">修改</el-button>
          <el-button size="small" :icon="View" @click="openDisplaySettings">显示设置</el-button>
        </template>
      </div>

      <!-- 微生物报告内嵌详情面板 (报告单直接显示, 不跳页) -->
      <div v-if="reportDetail && reportDetail.__kind === 'micro'" ref="microViewerRef" class="micro-viewer">
        <el-card shadow="never" class="micro-card">
          <template #header>
            <div class="micro-card-header">
              <span class="micro-title">微生物检验报告单</span>
              <el-tag :type="microStatusType(reportDetail.status)" size="small" effect="dark" class="no-print">{{ microStatusText(reportDetail.status) }}</el-tag>
            </div>
          </template>

          <!-- 标本信息 -->
          <el-descriptions :column="3" border size="small" class="specimen-desc">
            <el-descriptions-item label="报告编号">{{ reportDetail.reportNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="患者姓名">{{ reportDetail.patientName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ microDetailRaw?.patientGender ?? (reportDetail.patientGender ?? '-') }}</el-descriptions-item>
            <el-descriptions-item label="年龄">{{ microDetailRaw?.patientAge ?? (reportDetail.patientAge ?? '-') }}</el-descriptions-item>
            <el-descriptions-item label="就诊号">{{ reportDetail.visitNo || microDetailRaw?.visitNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="住院号">{{ microDetailRaw?.inpatientNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="标本类型">{{ reportDetail.specimenType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="采集条码">{{ reportDetail.sampleBarcode || microDetailRaw?.sampleBarcode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="标本号">{{ reportDetail.sampleNo || microDetailRaw?.sampleNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="培养条码">{{ microDetailRaw?.cultureBarcode || reportDetail.sampleBarcode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="培养部位">{{ reportDetail.cultureSite || microDetailRaw?.cultureSite || '-' }}</el-descriptions-item>
            <el-descriptions-item label="采集人">{{ microDetailRaw?.collectorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="采集时间">{{ formatDateTime(microDetailRaw?.collectTime || microDetailRaw?.collectDateTime || reportDetail.receiveTime) }}</el-descriptions-item>
            <el-descriptions-item label="核收时间">{{ formatDateTime(reportDetail.receiveTime || microDetailRaw?.receiveTime) }}</el-descriptions-item>
            <el-descriptions-item label="申请科室">{{ reportDetail.department || microDetailRaw?.department || '-' }}</el-descriptions-item>
            <el-descriptions-item label="床号">{{ microDetailRaw?.bedNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="医师">{{ microDetailRaw?.doctorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="费用类别">{{ microDetailRaw?.feeTypeName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="申请项目" :span="3"><span class="apply-items">{{ reportDetail.applyItems || microDetailRaw?.applyItems || '-' }}</span></el-descriptions-item>
            <el-descriptions-item label="创建时间" :span="3">{{ formatDateTime(reportDetail.createTime || microDetailRaw?.createTime) }}</el-descriptions-item>
          </el-descriptions>

          <!-- 专家/耐药规则命中 (预留, 暂无数据则不显示) -->
          <div v-if="microRuleHits && microRuleHits.length > 0" class="rule-hits-panel">
            <div class="rule-hits-title">
              <el-icon><WarnTriangleFilled /></el-icon>
              <span>专家/耐药规则命中</span>
            </div>
            <div class="rule-hits-list">
              <el-tag
                v-for="(hit, i) in microRuleHits"
                :key="i"
                :type="hit.alertLevel === 'CRITICAL' ? 'danger' : 'warning'"
                effect="dark"
                class="rule-hit-tag"
              >
                {{ hit.alertType }} · {{ hit.organismName }}：{{ hit.message }}
              </el-tag>
            </div>
          </div>

          <!-- Tab: 革兰氏染色 / 阴性报告 / 血培养 / 细菌鉴定与药敏 (只读展示) -->
          <el-tabs v-model="microActiveTab" class="detail-tabs no-print">
            <!-- 革兰氏染色 -->
            <el-tab-pane label="革兰氏染色" name="gram">
              <el-descriptions :column="2" border size="small" class="section-desc">
                <el-descriptions-item label="染色结果">{{ microDetailRaw?.gramStainResult || '-' }}</el-descriptions-item>
                <el-descriptions-item label="操作人">{{ microDetailRaw?.gramStainOperator || '-' }}</el-descriptions-item>
                <el-descriptions-item label="染色时间" :span="2">{{ formatDateTime(microDetailRaw?.gramStainTime) }}</el-descriptions-item>
                <el-descriptions-item label="备注" :span="2">
                  <div class="multiline-text">{{ microDetailRaw?.gramStainRemark || '-' }}</div>
                </el-descriptions-item>
              </el-descriptions>
              <el-empty v-if="!microDetailRaw?.gramStainResult && !microDetailRaw?.gramStainRemark" description="暂无革兰氏染色记录" :image-size="60" />
            </el-tab-pane>

            <!-- 阴性报告 -->
            <el-tab-pane label="阴性报告" name="negative">
              <el-descriptions :column="2" border size="small" class="section-desc">
                <el-descriptions-item label="是否阴性">
                  <el-tag v-if="Number(microDetailRaw?.isNegative ?? 0) === 1" type="success" effect="plain">阴性</el-tag>
                  <span v-else>-</span>
                </el-descriptions-item>
                <el-descriptions-item label="操作人">{{ microDetailRaw?.negativeOperator || '-' }}</el-descriptions-item>
                <el-descriptions-item label="阴性报告时间" :span="2">{{ formatDateTime(microDetailRaw?.negativeTime) }}</el-descriptions-item>
                <el-descriptions-item label="报告结论" :span="2">
                  <div class="multiline-text">{{ microDetailRaw?.negativeRemark || '-' }}</div>
                </el-descriptions-item>
              </el-descriptions>
              <el-empty v-if="Number(microDetailRaw?.isNegative ?? 0) !== 1 && !microDetailRaw?.negativeRemark" description="暂无阴性报告记录" :image-size="60" />
            </el-tab-pane>

            <!-- 血培养 (仅血液标本) -->
            <el-tab-pane v-if="(reportDetail.specimenType || microDetailRaw?.specimenType) === '血液'" label="血培养" name="blood">
              <el-descriptions :column="3" border size="small" class="section-desc mb12">
                <el-descriptions-item label="培养瓶号">{{ microDetailRaw?.bottleNo || '-' }}</el-descriptions-item>
                <el-descriptions-item label="瓶类型">{{ microDetailRaw?.bottleType || '-' }}</el-descriptions-item>
                <el-descriptions-item label="血量">{{ microDetailRaw?.volume || '-' }}</el-descriptions-item>
                <el-descriptions-item label="接种时间">{{ formatDateTime(microDetailRaw?.inoculateTime) }}</el-descriptions-item>
                <el-descriptions-item label="报警时间">{{ formatDateTime(microDetailRaw?.alarmTime) }}</el-descriptions-item>
                <el-descriptions-item label="TTP(小时)">{{ microDetailRaw?.ttpHours ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="血培养结果" :span="3">
                  <el-tag v-if="microDetailRaw?.bloodCultureResult === 'POSITIVE'" type="danger" effect="plain">阳性</el-tag>
                  <el-tag v-else-if="microDetailRaw?.bloodCultureResult === 'NEGATIVE'" type="success" effect="plain">阴性</el-tag>
                  <el-tag v-else-if="microDetailRaw?.bloodCultureResult === 'PENDING'" type="info" effect="plain">培养中</el-tag>
                  <span v-else>-</span>
                </el-descriptions-item>
              </el-descriptions>

              <!-- 转种记录 -->
              <el-divider content-position="left">转种记录</el-divider>
              <el-table :data="microSubcultures" border stripe size="small" max-height="260">
                <el-table-column type="index" label="序号" width="50" align="center" />
                <el-table-column prop="subcultureTime" label="转种时间" width="160">
                  <template #default="{ row }">{{ formatMicroTime(row.subcultureTime) }}</template>
                </el-table-column>
                <el-table-column prop="medium" label="培养基" width="140" />
                <el-table-column prop="incubatorNo" label="培养箱号" width="90" align="center" />
                <el-table-column prop="result" label="结果" min-width="120" />
                <el-table-column prop="operator" label="操作人" width="80" align="center" />
                <template #empty>
                  <el-empty description="暂无转种记录" :image-size="40" />
                </template>
              </el-table>
            </el-tab-pane>

            <!-- 细菌鉴定与药敏 -->
            <el-tab-pane :label="`细菌鉴定与药敏 (${microOrganisms.length})`" name="organism">
              <el-table
                :data="microOrganisms"
                border
                stripe
                size="small"
                row-key="id"
                :expand-row-keys="microExpandedIds"
                :default-expand-all="true"
                @expand-change="(row, expanded) => { microExpandedIds = expanded.map((r:any) => String(r.id)) }"
              >
                <el-table-column type="expand">
                  <template #default="{ row }">
                    <div class="sensitivity-expand">
                      <div class="section-header small">
                        <span>{{ row.organismName }} - 药敏结果</span>
                        <span class="sens-count-tag" style="margin-left:8px;">共 {{ (row.sensitivities || []).length }} 项</span>
                      </div>
                      <el-table :data="row.sensitivities || []" border size="small">
                        <el-table-column prop="antibioticName" label="抗生素" min-width="140" />
                        <el-table-column prop="antibioticCode" label="编码" width="90" align="center" />
                        <el-table-column prop="micValue" label="MIC" width="90" align="center" />
                        <el-table-column prop="diskZone" label="抑菌圈(mm)" width="100" align="center" />
                        <el-table-column label="结果" width="80" align="center">
                          <template #default="{ row: sub }">
                            <el-tag :type="microSensTagType(sub.result)" size="small">{{ sub.result || '-' }}</el-tag>
                          </template>
                        </el-table-column>
                        <el-table-column prop="breakpoint" label="判读界值" width="120" align="center" />
                        <el-table-column prop="method" label="方法" width="80" align="center" />
                      </el-table>
                      <el-empty v-if="!row.sensitivities || row.sensitivities.length === 0" description="暂无药敏结果" :image-size="40" />
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="organismName" label="细菌名称" min-width="160" />
                <el-table-column prop="organismCode" label="细菌编码" width="120" align="center" />
                <el-table-column prop="gramStain" label="染色分类" width="110" align="center" />
                <el-table-column prop="quantity" label="生长量" width="90" align="center" />
                <el-table-column prop="colonsMethod" label="培养方法" width="120" align="center" />
                <el-table-column prop="source" label="来源" width="90" align="center" />
                <el-table-column label="药敏数" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" type="info">{{ (row.sensitivities || []).length }}</el-tag>
                  </template>
                </el-table-column>
                <template #empty>
                  <el-empty description="暂无细菌鉴定记录" :image-size="60" />
                </template>
              </el-table>
            </el-tab-pane>
          </el-tabs>

          <!-- 底部: 签发信息 -->
          <el-descriptions :column="4" border size="small" class="signature-desc no-print" style="margin-top:16px;">
            <el-descriptions-item label="审核人">{{ microDetailRaw?.auditorName || (reportDetail.reportDoctor && !/^\d+$/.test(reportDetail.reportDoctor) ? reportDetail.reportDoctor : '-') }}</el-descriptions-item>
            <el-descriptions-item label="审核时间">{{ formatDateTime(reportDetail.auditTime || microDetailRaw?.auditTime) }}</el-descriptions-item>
            <el-descriptions-item label="操作人">{{ microDetailRaw?.operatorName || microDetailRaw?.operator || '-' }}</el-descriptions-item>
            <el-descriptions-item label="打印时间">{{ formatDateTime(new Date()) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </div>

      <!-- Tab 切换 (非微生物报告时显示) -->
      <el-tabs v-if="!reportDetail || reportDetail.__kind !== 'micro'" v-model="rightTab" type="card" class="right-tabs">
        <!-- Tab 1: 结果明细 -->
        <el-tab-pane label="结果明细" name="detail">
          <div v-if="reportDetail" class="results-container">
            <!-- 按检验项目分组显示 -->
            <div v-for="group in groupedResults" :key="group.panelName" class="result-group">
              <div class="group-header" @click="toggleGroup(group.panelName)">
                <el-icon class="toggle-icon" :class="{ expanded: expandedGroups.has(group.panelName) }">
                  <ArrowRight />
                </el-icon>
                <span class="group-title">
                  <el-tag v-if="group.panelName" type="primary" size="small" effect="light">{{ group.panelName }}</el-tag>
                  <span class="group-summary" v-if="group.totalFee">
                    <span class="fee-info">{{ group.panelName }} (应收: ¥{{ group.totalFee.toFixed(2) }} 实收: ¥{{ group.totalFee.toFixed(2) }})</span>
                    共 {{ group.indicators.length }} 项
                  </span>
                </span>
              </div>

              <el-table
                v-show="expandedGroups.has(group.panelName)"
                :data="group.indicators"
                border
                stripe
                size="small"
                class="results-table"
                :class="{ 'highlight-abnormal': displaySettings.highlightAbnormal }"
                :row-class-name="resultRowClassName"
              >
                <el-table-column type="index" label="序号" width="50" align="center" />
                <el-table-column v-if="displaySettings.shownColumns.includes('englishName')" prop="englishName" label="英文名称" width="90" show-overflow-tooltip />
                <el-table-column v-if="displaySettings.shownColumns.includes('indicatorName')" prop="indicatorName" label="指标" width="100" show-overflow-tooltip />
                <el-table-column v-if="displaySettings.shownColumns.includes('resultValue')" label="结果" width="110" align="center">
                  <template #default="{ row }">
                    <span class="result-mark" :class="resultCellClass(row)">
                      <el-tooltip :content="resultTooltip(row)" placement="top">
                        <span>{{ row.resultValue }}
                          <span v-if="resultArrow(row)" class="result-arrow">{{ resultArrow(row) }}</span>
                        </span>
                      </el-tooltip>
                      <el-icon v-if="row.isCritical" color="#f5222d" class="critical-icon"><WarningFilled /></el-icon>
                    </span>
                  </template>
                </el-table-column>
                <el-table-column v-if="displaySettings.shownColumns.includes('flag')" label="标识" width="50" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.abnormalFlag === 'H'" type="danger" size="small" effect="plain">H</el-tag>
                    <el-tag v-else-if="row.abnormalFlag === 'L'" type="warning" size="small" effect="plain">L</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column v-if="displaySettings.shownColumns.includes('unit')" prop="unit" label="单位" width="70" align="center" />
                <el-table-column v-if="displaySettings.shownColumns.includes('lastResult')" prop="lastResult" label="上次结果" width="80" align="center">
                  <template #default="{ row }">{{ row.lastResult || '-' }}</template>
                </el-table-column>
                <el-table-column v-if="displaySettings.shownColumns.includes('lastFlag')" label="上次标识" width="65" align="center">
                  <template #default="{ row }">
                    <span v-if="row.lastFlag">{{ row.lastFlag }}</span>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column v-if="displaySettings.shownColumns.includes('lastTestTime')" prop="lastTestTime" label="上次时间" width="130" align="center">
                  <template #default="{ row }">{{ formatShortTime(row.lastTestTime) }}</template>
                </el-table-column>
                <el-table-column v-if="displaySettings.shownColumns.includes('refRange')" prop="refRange" label="参考区间" width="100" align="center" show-overflow-tooltip />
                <el-table-column v-if="displaySettings.shownColumns.includes('interpretation')" label="解释信息" min-width="80" align="center" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.interpretation || '-' }}</template>
                </el-table-column>
              </el-table>
            </div>

            <el-empty v-if="groupedResults.length === 0" description="暂无检验结果" />
          </div>

          <el-empty v-else description="请从左侧选择标本查看结果" />
        </el-tab-pane>

        <!-- Tab 2: 预览报告 -->
        <el-tab-pane label="预览报告" name="preview">
          <div v-if="reportDetail" ref="previewContainerRef" class="preview-container">
            <div ref="reportPaperRef" :style="previewScaleStyle" class="report-paper">
              <ReportRenderer :template="reportTemplate" :data="reportRenderData" />
            </div>
          </div>

          <el-empty v-else description="请从左侧选择标本预览报告" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 打印设置弹窗 -->
    <el-dialog v-model="printSettingsVisible" title="打印设置" width="400px" @closed="handlePrintSettingsClosed">
      <el-form label-width="80px" size="small">
        <el-form-item label="纸张大小">
          <el-select v-model="printSettingsDraft.paperSize">
            <el-option label="A4" value="A4" />
            <el-option label="A5" value="A5" />
            <el-option label="B5" value="B5" />
          </el-select>
        </el-form-item>
        <el-form-item label="打印方向">
          <el-select v-model="printSettingsDraft.orientation">
            <el-option label="纵向" value="portrait" />
            <el-option label="横向" value="landscape" />
          </el-select>
        </el-form-item>
        <el-form-item label="边距(mm)">
          <el-input-number v-model="printSettingsDraft.margin" :min="5" :max="30" />
        </el-form-item>
        <el-form-item label="页眉">
          <el-switch v-model="printSettingsDraft.showHeader" />
        </el-form-item>
        <el-form-item label="页脚">
          <el-switch v-model="printSettingsDraft.showFooter" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="cancelPrintSettings">取消</el-button>
        <el-button size="small" type="primary" @click="savePrintSettings">保存设置</el-button>
      </template>
    </el-dialog>

    <!-- 显示设置弹窗 -->
    <el-dialog v-model="displaySettingsVisible" title="显示设置" width="400px">
      <el-form label-width="100px" size="small">
        <el-form-item label="显示列">
          <el-checkbox-group v-model="displaySettingsDraft.shownColumns">
            <el-checkbox label="englishName">英文名称</el-checkbox>
            <el-checkbox label="indicatorName">指标名称</el-checkbox>
            <el-checkbox label="resultValue">结果</el-checkbox>
            <el-checkbox label="flag">标识</el-checkbox>
            <el-checkbox label="unit">单位</el-checkbox>
            <el-checkbox label="lastResult">上次结果</el-checkbox>
            <el-checkbox label="lastFlag">上次标识</el-checkbox>
            <el-checkbox label="lastTestTime">上次时间</el-checkbox>
            <el-checkbox label="refRange">参考区间</el-checkbox>
            <el-checkbox label="interpretation">解释信息</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="分组依据">
          <el-select v-model="displaySettingsDraft.groupBy" style="width:100%">
            <el-option label="按组合/面板" value="panel" />
            <el-option label="按分类" value="category" />
            <el-option label="不分组" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示报告编号">
          <el-switch v-model="displaySettingsDraft.showReportNo" />
        </el-form-item>
        <el-form-item label="高亮异常">
          <el-switch v-model="displaySettingsDraft.highlightAbnormal" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="displaySettingsVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveDisplaySettings">保存</el-button>
      </template>
    </el-dialog>

    <!-- 调整姓名拼音弹窗 -->
    <el-dialog v-model="pinyinVisible" title="调整姓名拼音" width="400px">
      <el-form label-width="90px" size="small">
        <el-form-item label="患者姓名">
          <el-input v-model="pinyinDraft.name" disabled />
        </el-form-item>
        <el-form-item label="姓名拼音" required>
          <el-input v-model="pinyinDraft.pinyin" placeholder="输入姓名拼音,如: zhang san" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="pinyinVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="savePinyin">保存</el-button>
      </template>
    </el-dialog>

    <!-- 病案查询弹窗 -->
    <el-dialog v-model="medicalQueryVisible" title="病案查询" width="680px" top="6vh">
      <div class="medical-query">
        <div class="medical-summary" v-if="reportDetail">
          <div class="m-row"><span>患者姓名:</span><b>{{ reportDetail.patientName }}</b></div>
          <div class="m-row"><span>性别/年龄:</span>{{ getGender(reportDetail.patientGender) }} / {{ reportDetail.patientAge || '-' }}</div>
          <div class="m-row"><span>科室:</span>{{ reportDetail.department || '-' }}</div>
          <div class="m-row"><span>临床诊断:</span>{{ reportDetail.clinicalDiag || '-' }}</div>
          <div class="m-row"><span>申请医生:</span>{{ reportDetail.doctor || '-' }}</div>
          <div class="m-row"><span>标本号:</span>{{ reportDetail.sampleNo || '-' }}</div>
          <div class="m-row"><span>标本条码:</span>{{ reportDetail.sampleBarcode || '-' }}</div>
          <div class="m-row"><span>核收时间:</span>{{ formatTime(reportDetail.receiveTime) }}</div>
        </div>
        <el-table :data="medicalHistory" border size="small" max-height="380">
          <el-table-column label="报告日期" width="140" align="center">
            <template #default="{ row }">{{ formatShortTime(row.publishTime || row.createTime) }}</template>
          </el-table-column>
          <el-table-column prop="reportNo" label="报告编号" width="140" show-overflow-tooltip />
          <el-table-column prop="testItems" label="申请项目" min-width="200" show-overflow-tooltip />
          <el-table-column prop="department" label="科室" width="120" show-overflow-tooltip />
          <el-table-column prop="statusText" label="状态" width="80" align="center" />
        </el-table>
      </div>
      <template #footer>
        <el-button size="small" @click="medicalQueryVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 标本图弹窗 -->
    <el-dialog v-model="sampleImageVisible" title="标本图" width="600px">
      <div class="sample-image-container">
        <el-empty v-if="sampleImages.length === 0" description="暂无标本图数据" />
        <el-row v-else :gutter="10">
          <el-col v-for="(img, idx) in sampleImages" :key="idx" :span="12">
            <div class="img-card">
              <img :src="img.url" :alt="img.title" @click="previewImage(img.url)" />
              <div class="img-title">{{ img.title }}</div>
              <div class="img-sub" v-if="img.remark">{{ img.remark }}</div>
            </div>
          </el-col>
        </el-row>
      </div>
      <template #footer>
        <el-button size="small" @click="sampleImageVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 修改报告弹窗 -->
    <el-dialog v-model="modifyVisible" title="修改报告结果" width="720px">
      <el-table :data="modifyRows" border size="small" max-height="420">
        <el-table-column prop="testItemName" label="项目名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="原结果" width="100" align="center">
          <template #default="{ row }">{{ row.originValue || '-' }}</template>
        </el-table-column>
        <el-table-column label="新结果" width="140" align="center">
          <template #default="{ row }">
            <el-input v-model="row.resultValue" size="small" placeholder="新结果值" />
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column label="修改原因" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.modifyReason" size="small" placeholder="请填写修改原因" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button size="small" @click="modifyVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="modifySaving" @click="submitModify">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 微生物报告打印预览(按模板设计渲染) -->
    <el-dialog v-model="microPrintVisible" title="微生物报告打印预览" width="82%" top="4vh" append-to-body class="micro-print-dialog" destroy-on-close>
      <div ref="microPrintContainerRef" class="print-preview-container" v-loading="microPrintLoading">
        <template v-if="microPrintTemplate">
          <ReportRenderer :data="microPrintData" :template="microPrintTemplate" />
        </template>
        <el-empty v-else description="模板加载中或为空" :image-size="80" />
      </div>
      <template #footer>
        <el-button size="small" @click="microPrintVisible = false">关闭</el-button>
        <el-button size="small" type="primary" :icon="Printer" @click="doMicroPrintByTemplate">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Printer, Clock, ArrowDown, ArrowRight, Grid,
  FolderOpened, EditPen, Document, Download, Picture, View, Setting, WarningFilled, WarnTriangleFilled
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { getReportPage, getReportById, modifyReport, type ReportVO, type ResultVO } from '@/api/report'
import { getMicroReportPage, getMicroReportById, getMicroRuleHits, type MicroReport } from '@/api/report/micro'
import { getTemplateByInstrument, getDefaultTemplate, getDefaultTemplateFromServer, loadLocalTemplate, getBuiltInTemplate, type BuiltinTemplateType, type ReportTemplate } from '@/api/report/template'
import { getAllActiveStaff } from '@/api/hr'
import ReportRenderer from '@/views/report/components/ReportRenderer.vue'
import { sanitizeHtml, escapeHtml } from '@/utils/sanitize'
import { openPrintWindow } from '@/utils/print'

const router = useRouter()

// ============ 数据 ============
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const searchKeyword = ref('')
const dateRange = ref<string[]>([])
const dateLabel = ref('今天')
const reportKind = ref<'all' | 'regular' | 'micro'>('all')

const reportDetail = ref<any>(null)
const otherSamples = ref<any[]>([])
const rightTab = ref('detail')

// ============ 左右面板可拖拽调整宽度 ============
const RESIZE_COLS = { LEFT_MIN: 360, LEFT_MAX: 1600, LEFT_DEFAULT: 1100 }
const leftPanelWidth = ref(RESIZE_COLS.LEFT_DEFAULT)
function startResize(ev: MouseEvent) {
  ev.preventDefault()
  const startX = ev.clientX
  const startWidth = leftPanelWidth.value
  const onMove = (e: MouseEvent) => {
    const next = Math.min(RESIZE_COLS.LEFT_MAX, Math.max(RESIZE_COLS.LEFT_MIN, startWidth + (e.clientX - startX)))
    leftPanelWidth.value = next
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

// ============ 微生物报告内嵌预览 ============
const microActiveTab = ref<string>('gram')
const microOrganisms = ref<any[]>([])
const microSubcultures = ref<any[]>([])
const microRuleHits = ref<any[]>([])
const microExpandedIds = ref<string[]>([])
const microViewerRef = ref<HTMLElement | null>(null)

// ============ 微生物报告按模板打印 (复用 ReportRenderer) ============
const microPrintVisible = ref(false)
const microPrintLoading = ref(false)
const microPrintTemplate = ref<any>(null)
const microPrintData = ref<Record<string, any>>({})
const microPrintReportNo = ref('')
const microPrintContainerRef = ref<HTMLElement | null>(null)

/** 微生物报告状态文字 */
function microStatusText(v: number | undefined | null): string {
  const n = Number(v ?? 1)
  if (n === 3) return '最终报告'
  if (n === 2) return '鉴定报告'
  return '初步报告'
}
/** 微生物报告状态 tag 类型 */
function microStatusType(v: number | undefined | null): string {
  const n = Number(v ?? 1)
  if (n === 3) return 'success'
  if (n === 2) return 'warning'
  return 'info'
}
/** 药敏结果 tag 颜色 */
function microSensTagType(r: string): string {
  if (!r) return 'info'
  if (r === 'S' || r === '敏感') return 'success'
  if (r === 'R' || r === '耐药') return 'danger'
  if (r === 'I' || r === '中介') return 'warning'
  return 'info'
}
function formatDateTime(v: any): string {
  if (!v) return '-'
  const d = dayjs(v)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : String(v)
}
function formatMicroTime(v: any): string {
  if (!v) return '-'
  const d = dayjs(v)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : String(v)
}
/** 当前选中微生物报告的原始详情 (getMicroReportById 返回) */
const microDetailRaw = computed<any>(() => reportDetail.value?._rawMicro ?? reportDetail.value?._microRow ?? null)

/**
 * 微生物申请项目兜底显示:
 * 优先使用 applyItems, 否则根据标本类型 / 培养部位给出常见的微生物检验申请项目文本
 */
function formatMicroApplyFallback(row: any): string {
  const type = (row?.specimenType || '') as string
  const site = (row?.cultureSite || '') as string
  // 1. 血液标本: 血培养
  if (type === '血液') return site ? '血培养+' + site : '血培养'
  // 2. 脑脊液: 脑脊液细菌培养+药敏
  if (type === '脑脊液') return '脑脊液细菌培养+药敏'
  // 3. 尿液: 尿培养+药敏(普通/清洁中段尿/穿刺)
  if (type === '尿液') {
    if (site === '膀胱穿刺') return '尿培养+药敏(膀胱穿刺)'
    return '尿培养+药敏'
  }
  // 4. 粪便: 粪便致病菌培养
  if (type === '粪便') return '粪便致病菌培养'
  // 5. 分泌物/痰
  if (type === '分泌物') return site ? `${site}分泌物细菌培养+药敏` : '分泌物细菌培养+药敏'
  if (type === '呼吸道分泌物' || type === '痰液' || type === '痰') {
    if (site === '下呼吸道') return '下呼吸道分泌物细菌培养+药敏'
    return '痰培养+药敏'
  }
  // 6. 其他: "标本类型 细菌培养+药敏"
  if (type) return `${type}细菌培养+药敏`
  return '细菌培养+药敏'
}

const expandedGroups = reactive(new Set<string>())
const previewContainerRef = ref<HTMLElement | null>(null)
const reportPaperRef = ref<HTMLElement | null>(null)
const previewScale = ref(1)
const contentHeight = ref(1123)

/** A4 报告实际像素尺寸 (96dpi) */
const REPORT_W = 794
const REPORT_H = 1123

/** 动态计算报告缩放:
 *  - 单页: 同时适配宽高, 确保完整可见
 *  - 多页: 仅适配宽度, 高度超出则滚动
 */
const previewScaleStyle = computed(() => ({
  transform: `scale(${previewScale.value})`,
  transformOrigin: 'top center',
  width: REPORT_W + 'px',
  marginBottom: -(contentHeight.value * (1 - previewScale.value)) + 'px'
}))

let resizeObserver: ResizeObserver | null = null
let contentObserver: ResizeObserver | null = null

function updatePreviewScale() {
  const container = previewContainerRef.value
  const paper = reportPaperRef.value
  if (!container) { previewScale.value = 1; return }

  // 测量报告实际内容高度 (多页时会超过单页A4高度)
  if (paper) {
    contentHeight.value = paper.scrollHeight || REPORT_H
  }

  const { clientWidth, clientHeight } = container
  const padding = 24
  const availW = clientWidth - padding
  const availH = clientHeight - padding

  // 基于宽度的缩放比
  const scaleByW = availW / REPORT_W
  // 基于实际内容高度的缩放比
  const scaleByH = availH / contentHeight.value

  // 单页判定: 内容高度 <= 1.3倍A4高度视为单页
  const isMultiPage = contentHeight.value > REPORT_H * 1.3

  if (isMultiPage) {
    // 多页: 仅适配宽度, 高度允许滚动
    previewScale.value = Math.min(scaleByW, 1)
  } else {
    // 单页: 同时适配宽高, 确保完整显示
    previewScale.value = Math.min(scaleByW, scaleByH, 1)
  }
}

/** 报告渲染模板 (按报告仪器加载, 默认使用本地模板) */
const reportTemplate = ref<ReportTemplate>(getDefaultTemplate())
/** 在职员工手写签名图片缓存: 姓名 -> signatureImage */
const staffSignatureMap = ref<Record<string, string>>({})
/** 在职员工姓名缓存: 员工ID -> 姓名 (用于按 auditorId/publisherId 解析姓名) */
const staffByIdMap = ref<Record<string, string>>({})

const printSettingsVisible = ref(false)
const defaultPrintSettings = {
  paperSize: 'A4',
  orientation: 'portrait',
  margin: 10,
  showHeader: true,
  showFooter: true
}
const printSettings = reactive({ ...defaultPrintSettings, ...loadPrintSettings() })
const printSettingsDraft = reactive({ ...defaultPrintSettings })

// 显示设置
const displaySettingsVisible = ref(false)
const defaultDisplaySettings = {
  shownColumns: ['englishName', 'indicatorName', 'resultValue', 'flag', 'unit', 'lastResult', 'lastFlag', 'lastTestTime', 'refRange', 'interpretation'],
  groupBy: 'panel',
  showReportNo: true,
  highlightAbnormal: true
}
const displaySettings = reactive({ ...defaultDisplaySettings, ...loadDisplaySettings() })
const displaySettingsDraft = reactive({ ...defaultDisplaySettings })

// 姓名拼音
const pinyinVisible = ref(false)
const pinyinDraft = reactive({ name: '', pinyin: '' })
const patientPinyinMap = reactive<Record<string, string>>(loadPatientPinyinMap())

// 病案查询
const medicalQueryVisible = ref(false)
const medicalHistory = ref<any[]>([])

// 标本图
const sampleImageVisible = ref(false)
const sampleImages = ref<{ url: string; title: string; remark?: string }[]>([])

// ============ 计算属性 ============
const filteredList = computed(() => tableData.value)

interface ResultGroup {
  panelName: string
  indicators: any[]
  totalFee?: number
}

const groupedResults = computed<ResultGroup[]>(() => {
  if (!reportDetail.value?.results) return []
  const results: ResultVO[] = reportDetail.value.results
  const map = new Map<string, ResultGroup>()
  for (const r of results) {
    let panelName = '检验项目'
    if (displaySettings.groupBy === 'panel') {
      panelName = (r as any).panelName || (r as any).category || '检验项目'
    } else if (displaySettings.groupBy === 'category') {
      panelName = (r as any).category || (r as any).panelName || '检验项目'
    }
    if (!map.has(panelName)) {
      map.set(panelName, { panelName, indicators: [], totalFee: 0 })
      expandedGroups.add(panelName)
    }
    // 转换字段名称以匹配显示列
    const item: any = {
      ...r,
      englishName: (r as any).englishName || r.testItemCode || '',
      indicatorName: r.testItemName || '',
      resultValue: r.resultValue || '',
      abnormalFlag: r.abnormalFlag || '',
      unit: r.unit || '',
      lastResult: (r as any).lastResult || '',
      lastFlag: (r as any).lastFlag || '',
      lastTestTime: (r as any).lastTestTime || '',
      refRange: r.refRange || '',
      interpretation: (r as any).interpretation || '',
      isCritical: r.isCritical === 1 || r.isCritical === '1'
    }
    map.get(panelName)!.indicators.push(item)
  }
  // 不分组: 合并为单组
  if (displaySettings.groupBy === 'none' && map.size > 0) {
    const all: ResultGroup = { panelName: '全部结果', indicators: [], totalFee: 0 }
    map.forEach(g => { all.indicators.push(...g.indicators) })
    return [all]
  }
  return Array.from(map.values())
})

/** 报告模板渲染数据 (整合报告详情 + 签名图片) */
const reportRenderData = computed(() => {
  const d = reportDetail.value || {} as any
  const sig = (name?: string) => ({ signatureImage: name ? (staffSignatureMap.value[name] || '') : '' })
  // 操作/审核人姓名: 报告记录的是员工ID(auditorId/publisherId), 通过ID解析姓名
  const operatorName = d.auditor
    || (d.auditorId ? staffByIdMap.value[d.auditorId] : '')
    || (d.publisherId ? staffByIdMap.value[d.publisherId] : '')
    || ''
  // 各签名位姓名: 报告记录的姓名(存在且有签名)优先, 否则回退到操作人, 保证审核/报告签名可见
  const checkerName = (d.checker && staffSignatureMap.value[d.checker]) ? d.checker : operatorName
  const auditorName = operatorName
  const reportDoctorName = (d.reportDoctor && d.reportDoctor !== '系统生成' && staffSignatureMap.value[d.reportDoctor]) ? d.reportDoctor : operatorName
  return {
    ...d,
    patientName: d.patientName,
    gender: d.patientGender,
    age: d.patientAge,
    visitNo: d.visitNo,
    department: d.department,
    doctor: d.doctor,
    clinicalDiag: d.clinicalDiag,
    sampleBarcode: d.sampleBarcode || d.sampleNo,
    sampleType: d.sampleType,
    receiveTime: d.receiveTime,
    reportTime: d.publishTime || d.createTime,
    auditTime: d.auditTime,
    checker: checkerName,
    auditor: auditorName,
    reportDoctor: reportDoctorName,
    results: (d.results || []).map((r: any) => ({
      ...r,
      testItemName: r.testItemName,
      resultValue: r.resultValue,
      unit: r.unit,
      refRange: r.refRange,
      abnormalFlag: r.abnormalFlag || '',
      isCritical: r.isCritical === 1 || r.isCritical === '1' ? 1 : 0
    })),
    sampleImages: (d.sampleImages || []).map((img: any) => ({
      imageUrl: img.imageUrl || img.url,
      imageName: img.imageName || img.title || ''
    })),
    signatures: {
      checker: sig(checkerName),
      auditor: sig(auditorName),
      reportDoctor: sig(reportDoctorName)
    }
  }
})

/** 按报告核收仪器加载对应模板 (无匹配或 elements 为空时回退到本地/默认模板) */
async function loadReportTemplate() {
  const d = reportDetail.value
  const insId = (d?.results || []).find((r: any) => r.instrumentId)?.instrumentId
  const reportType = d?.reportType
  
  // 根据报告类型选择对应模板
  // reportType: 1-常规, 2-急诊, 3-微生物
  let templateType: BuiltinTemplateType | undefined
  if (reportType === 3) {
    templateType = 'micro'
  } else if (reportType === 2) {
    templateType = 'b5'
  }
  
  // 先尝试从服务器加载对应模板（按仪器+类型）
  let t = await getTemplateByInstrument(insId as number | undefined, reportType as number | undefined)
  
  if (!t || !t.elements || t.elements.length === 0) {
    // 从服务器默认模板加载（按类型）
    t = await getDefaultTemplateFromServer(reportType as number | undefined)
  }
  
  if (t && t.elements && t.elements.length > 0) {
    reportTemplate.value = t
  } else if (templateType) {
    // 使用内置模板
    reportTemplate.value = getBuiltInTemplate(templateType)
  } else {
    // 回退到本地/默认模板
    const local = loadLocalTemplate()
    reportTemplate.value = (local.elements && local.elements.length > 0) ? local : getDefaultTemplate()
  }
}

/** 加载在职员工签名图片: 姓名 -> signatureImage (HR 服务不可用时忽略) */
async function loadStaffSignatures() {
  try {
    const staffList = await getAllActiveStaff()
    const map: Record<string, string> = {}
    const idMap: Record<string, string> = {}
    for (const s of staffList) {
      if (s.name && s.signatureImage) map[s.name] = s.signatureImage
      if (s.id != null && s.id !== 0 && s.name) idMap[String(s.id)] = s.name
    }
    staffSignatureMap.value = map
    staffByIdMap.value = idMap
  } catch { /* HR 服务不可用时忽略 */ }
}

// ============ 方法 ============
function formatTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function formatShortTime(time?: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

function getGender(g: any): string {
  if (g === 1 || g === '男' || g === 'M') return '男'
  if (g === 2 || g === '女' || g === 'F') return '女'
  return '-'
}

/** 异常结果多级视觉标记: 危急值 / 偏高 / 偏低 / 正常 */
function resultCellClass(row: any): string {
  if (row.isCritical) return 'cell-critical'
  if (row.abnormalFlag === 'H') return 'cell-high'
  if (row.abnormalFlag === 'L') return 'cell-low'
  return ''
}
function resultArrow(row: any): string {
  if (row.isCritical) return '↑↑'
  if (row.abnormalFlag === 'H') return '↑'
  if (row.abnormalFlag === 'L') return '↓'
  return ''
}
function resultTooltip(row: any): string {
  if (row.isCritical) return '危急值! 请立即处理'
  if (row.abnormalFlag === 'H') return '高于参考区间'
  if (row.abnormalFlag === 'L') return '低于参考区间'
  return '正常'
}

/** 结果表行样式: 异常行高亮背景 */
function resultRowClassName({ row }: { row: any }) {
  if (row.isCritical) return 'result-row-critical'
  if (row.abnormalFlag === 'H') return 'result-row-h'
  if (row.abnormalFlag === 'L') return 'result-row-l'
  return ''
}

function toggleGroup(name: string) {
  if (expandedGroups.has(name)) expandedGroups.delete(name)
  else expandedGroups.add(name)
}

function onQuickDate(cmd: string) {
  const now = dayjs()
  let start: dayjs.Dayjs, end: dayjs.Dayjs, label: string
  switch (cmd) {
    case 'today':
      start = now.startOf('day'); end = now.endOf('day'); label = '今天'; break
    case 'yesterday':
      start = now.subtract(1, 'day').startOf('day'); end = now.subtract(1, 'day').endOf('day'); label = '昨天'; break
    case 'week':
      start = now.subtract(6, 'day').startOf('day'); end = now.endOf('day'); label = '近7天'; break
    case 'month':
      start = now.subtract(29, 'day').startOf('day'); end = now.endOf('day'); label = '近30天'; break
    default: return
  }
  dateRange.value = [start.format('YYYY-MM-DD HH:mm:ss'), end.format('YYYY-MM-DD HH:mm:ss')]
  dateLabel.value = label
  handleSearch()
}

async function fetchList() {
  loading.value = true
  try {
    const params: any = { current: 1, size: 200 }
    if (dateRange.value?.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    if (searchKeyword.value?.trim()) {
      params.keyword = searchKeyword.value.trim()
    }

    const kind = reportKind.value
    const tasks: Promise<any>[] = []
    if (kind === 'all' || kind === 'regular') {
      tasks.push(getReportPage(params).then(r => ({ kind: 'regular', res: r })).catch(e => ({ kind: 'regular', err: e })))
    }
    if (kind === 'all' || kind === 'micro') {
      const microParams: any = { pageNum: 1, pageSize: 200 }
      if (dateRange.value?.length === 2) {
        microParams.beginDate = dateRange.value[0]
        microParams.endDate = dateRange.value[1]
      }
      if (searchKeyword.value?.trim()) {
        microParams.keyword = searchKeyword.value.trim()
      }
      tasks.push(getMicroReportPage(microParams).then(r => ({ kind: 'micro', res: r })).catch(e => ({ kind: 'micro', err: e })))
    }

    const results = await Promise.all(tasks)
    const merged: any[] = []
    let regTotal = 0
    let microTotal = 0
    for (const p of results) {
      if ((p as any).err) {
        console.warn(`[report-query] ${(p as any).kind} query failed:`, (p as any).err)
        continue
      }
      if ((p as any).kind === 'regular') {
        const records = ((p as any).res?.records) || []
        for (const r of records) {
          merged.push({ ...r, __kind: 'regular' })
        }
        regTotal = Number((p as any).res?.total ?? records.length)
      } else if ((p as any).kind === 'micro') {
        const records = ((p as any).res?.records as MicroReport[]) || []
        for (const r of records) {
          const rAny: any = r as any
          merged.push({
            __kind: 'micro',
            __microId: r.id,
            id: r.id,
            reportNo: r.reportNo || '',
            reportType: 3,
            status: (rAny.status ?? r.reportStatus) ?? 1,
            patientId: r.patientId,
            patientName: r.patientName || '',
            // 常规列表列填充: 性别/年龄 详情接口获取
            patientGender: undefined,
            patientAge: undefined,
            sampleNo: r.sampleNo || rAny.sampleNo || '',
            sampleBarcode: r.sampleBarcode || rAny.sampleBarcode || '',
            visitNo: rAny.visitNo || '',
            applyItems: r.applyItems || '',
            testItems: r.applyItems || formatMicroApplyFallback({ specimenType: r.specimenType, cultureSite: r.cultureSite }),
            specimenType: r.specimenType || '',
            cultureSite: r.cultureSite || '',
            department: r.department || '',
            reportDoctor: rAny.auditorId ? String(rAny.auditorId) : (rAny.operator || ''),
            createTime: r.createTime || rAny.receiveTime || '',
            auditTime: r.auditTime || rAny.receiveTime || '',
            publishTime: r.publishTime || r.auditTime || rAny.receiveTime || '',
            receiveTime: r.receiveTime || '',
            // 微生物报告详情接口通过 getMicroReportById 获取
            _microRow: r
          })
        }
        microTotal = Number((p as any).res?.total ?? records.length)
      }
    }
    // 统一按报告时间倒序
    merged.sort((a: any, b: any) => {
      const ta = a.__kind === 'micro' ? (a.auditTime || a.createTime) : (a.publishTime || a.createTime)
      const tb = b.__kind === 'micro' ? (b.auditTime || b.createTime) : (b.publishTime || b.createTime)
      const at = ta ? new Date(ta).getTime() : 0
      const bt = tb ? new Date(tb).getTime() : 0
      return bt - at
    })
    tableData.value = merged
    total.value = merged.length
    // 记录分类型总数 (供调试)
    ;(tableData as any).__regTotal = regTotal
    ;(tableData as any).__microTotal = microTotal
  } catch (e: any) {
    console.error('报告查询失败:', e)
    ElMessage.error(e?.message || '报告查询失败')
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchList()
}

// ============ 打印设置持久化 ============
const PRINT_SETTINGS_KEY = 'report.query.printSettings'
const DISPLAY_SETTINGS_KEY = 'report.query.displaySettings'
const PATIENT_PINYIN_KEY = 'report.query.patientPinyin'

function loadPrintSettings(): any {
  try { return JSON.parse(localStorage.getItem(PRINT_SETTINGS_KEY) || '{}') } catch { return {} }
}
function loadDisplaySettings(): any {
  try { return JSON.parse(localStorage.getItem(DISPLAY_SETTINGS_KEY) || '{}') } catch { return {} }
}
function loadPatientPinyinMap(): Record<string, string> {
  try { return JSON.parse(localStorage.getItem(PATIENT_PINYIN_KEY) || '{}') } catch { return {} }
}

function openPrintSettings() {
  Object.assign(printSettingsDraft, printSettings)
  printSettingsVisible.value = true
}
function cancelPrintSettings() {
  Object.assign(printSettingsDraft, printSettings)
  printSettingsVisible.value = false
}
function handlePrintSettingsClosed() {
  // 无
}
function savePrintSettings() {
  Object.assign(printSettings, printSettingsDraft)
  localStorage.setItem(PRINT_SETTINGS_KEY, JSON.stringify(printSettings))
  printSettingsVisible.value = false
  ElMessage.success('打印设置已保存')
}

// ============ 显示设置 ============
function openDisplaySettings() {
  Object.assign(displaySettingsDraft, displaySettings)
  displaySettingsVisible.value = true
}
function saveDisplaySettings() {
  Object.assign(displaySettings, displaySettingsDraft)
  localStorage.setItem(DISPLAY_SETTINGS_KEY, JSON.stringify(displaySettings))
  displaySettingsVisible.value = false
  ElMessage.success('显示设置已保存')
}

// ============ 姓名拼音 ============
function openPinyinDialog() {
  if (!reportDetail.value) { ElMessage.warning('请先选择标本'); return }
  const name = reportDetail.value.patientName || ''
  pinyinDraft.name = name
  pinyinDraft.pinyin = patientPinyinMap[name] || ''
  pinyinVisible.value = true
}
function savePinyin() {
  if (!pinyinDraft.name) return
  if (pinyinDraft.pinyin) {
    patientPinyinMap[pinyinDraft.name] = pinyinDraft.pinyin
  } else {
    delete patientPinyinMap[pinyinDraft.name]
  }
  localStorage.setItem(PATIENT_PINYIN_KEY, JSON.stringify(patientPinyinMap))
  pinyinVisible.value = false
  ElMessage.success('姓名拼音已更新')
}

// ============ 病案查询 (按患者展示历史报告) ============
async function openMedicalRecordQuery() {
  if (!reportDetail.value) { ElMessage.warning('请先选择标本'); return }
  medicalHistory.value = []
  medicalQueryVisible.value = true
  if (reportDetail.value.patientId) {
    try {
      const res = await getReportPage({ patientId: reportDetail.value.patientId, size: 100 })
      medicalHistory.value = (res.records || []).filter((r: any) => r.id !== reportDetail.value.id)
    } catch { medicalHistory.value = [] }
  }
}

// ============ 标本图 ============
function openSampleImageDialog() {
  if (!reportDetail.value) { ElMessage.warning('请先选择标本'); return }
  sampleImages.value = []
  // 优先使用报告详情中的标本图像 (lis_sample_image 表, 血常规/尿常规等形态图像)
  const imgs = reportDetail.value.sampleImages || []
  if (imgs.length > 0) {
    sampleImages.value = imgs.map((img: any, idx: number) => ({
      url: img.imageUrl,
      title: img.imageName || `标本图 ${idx + 1}`,
      remark: img.imageType || ''
    }))
  }
  sampleImageVisible.value = true
}
function previewImage(url: string) {
  window.open(url, '_blank')
}

/** 跳转到微生物报告页 (携带报告ID, 目标页会自动加载并选中) */
function goMicroPage(row: any) {
  if (!row || row.__kind !== 'micro') return
  const id = row.__microId ?? row.id
  router.push({ name: 'ReportMicro', query: id ? { id: String(id) } : {} })
}

async function handleRowClick(row: any) {
  if (!row) return
  // 微生物报告: 拉取详情, 直接在右侧内嵌面板展示完整报告单
  if (row.__kind === 'micro') {
    // 注意: 后端 Long 序列化为 String 返回前端, 禁止 Number(id)
    //       雪花 ID > MAX_SAFE_INTEGER 会精度丢失, 例如:
    //       2091785550701096989 -> Number(id) -> 2091785550701097000, 导致后端"微生物报告不存在"
    const id = (row.__microId != null ? row.__microId : row.id) != null
      ? String(row.__microId != null ? row.__microId : row.id)
      : null
    let microDetail: any = row._microRow || row
    try {
      if (id != null && id !== '') microDetail = (await getMicroReportById(id)) || microDetail
    } catch (e) {
      console.warn('[report-query] 加载微生物详情失败, 使用列表行数据', e)
    }
    // 加载专家/耐药规则命中
    try {
      if (id != null && id !== '') {
        microRuleHits.value = (await getMicroRuleHits(id)) || []
      } else {
        microRuleHits.value = []
      }
    } catch {
      microRuleHits.value = []
    }
    const st: number = (microDetail as any).reportStatus ?? (microDetail as any).status ?? row.status ?? 1
    reportDetail.value = {
      ...row,
      _rawMicro: microDetail,
      // 补充微生物行可能不存在的字段
      applyItems: microDetail.applyItems || row.applyItems || '',
      specimenType: microDetail.specimenType || row.specimenType || '',
      cultureSite: microDetail.cultureSite || row.cultureSite || '',
      auditTime: microDetail.auditTime || row.auditTime || '',
      reportNo: microDetail.reportNo || row.reportNo || '',
      reportStatus: st,
      status: st
    }
    // 同步内嵌预览面板的微生物数据
    microOrganisms.value = (microDetail as any).organisms || []
    microSubcultures.value = (microDetail as any).subcultures || []
    microExpandedIds.value = microOrganisms.value.map((o: any) => String(o.id))
    microActiveTab.value = 'gram'
    otherSamples.value = []
    rightTab.value = 'detail'
    return
  }
  // 常规报告
  if (!row.id) return
  try {
    const detail: any = await getReportById(row.id)
    reportDetail.value = detail
    // 加载报告模板(按仪器)与员工签名图片 (签名须加载完成后再打印, 否则打印预览缺签名)
    await loadReportTemplate()
    await loadStaffSignatures()
    // 查询同一患者的其他标本
    if (detail?.patientId) {
      try {
        const other: any = await getReportPage({ patientId: detail.patientId, size: 20 })
        otherSamples.value = (other.records || []).filter((r: any) => r.id !== row.id)
      } catch { otherSamples.value = [] }
    } else {
      otherSamples.value = []
    }
    // 默认展开第一个分组
    nextTick(() => {
      if (groupedResults.value.length > 0) {
        groupedResults.value.forEach(g => expandedGroups.add(g.panelName))
      }
    })
  } catch {
    reportDetail.value = null
    otherSamples.value = []
  }
}

// ============ 打印功能(报告模板设计) ============
// 微生物模板类型映射(用于自动选择内置模板)
const MICRO_PRINT_BUILTIN: Record<string, BuiltinTemplateType> = {
  identification: 'micro',
  sensitivity: 'micro',
  negative: 'micro-negative',
  blood: 'micro-blood'
}

/** 加载微生物模板: 优先从服务端 reportType=3 取已保存模板, 失败再回退内置模板 */
async function resolveMicroTemplate(builtin: BuiltinTemplateType) {
  try {
    const typeTpl = await getDefaultTemplateFromServer(3)
    if (typeTpl) return typeTpl
  } catch { /* ignore */ }
  try {
    const insTpl = await getTemplateByInstrument(undefined, 3)
    if (insTpl) return insTpl
  } catch { /* ignore */ }
  return getBuiltInTemplate(builtin)
}

/** 映射微生物报告数据 -> ReportRenderer 所需结构 (与 /report/micro 页面保持一致) */
function buildMicroPrintData(rd: any, raw: any, orgs: any[]): Record<string, any> {
  const organismList = (orgs || []).map((o: any) => ({
    id: o.id,
    organismName: o.organismName,
    organismCode: o.organismCode,
    quantity: o.quantity,
    colonsMethod: o.colonsMethod,
    sensitivities: (o.sensitivities || []).map((s: any) => ({
      id: s.id,
      antibioticName: s.antibioticName,
      micValue: s.micValue,
      result: s.result,
      breakpoint: s.breakpoint
    }))
  }))
  const operatorFallback = raw?.negativeOperator || raw?.gramStainOperator || raw?.auditorName || raw?.operatorName || raw?.operator || ''
  return {
    patientName: rd?.patientName ?? raw?.patientName,
    patientGender: rd?.patientGender ?? raw?.patientGender,
    patientAge: rd?.patientAge ?? raw?.patientAge,
    specimenType: rd?.specimenType ?? raw?.specimenType,
    cultureSite: rd?.cultureSite ?? raw?.cultureSite,
    sampleBarcode: rd?.sampleBarcode ?? raw?.sampleBarcode,
    plateBarcode: raw?.plateBarcode || raw?.plateBarcodes || '',
    plateBarcodes: raw?.plateBarcodes || '',
    specimenNo: rd?.sampleNo || raw?.sampleNo || raw?.sampleBarcode || '',
    inpatientNo: raw?.inpatientNo || '',
    department: rd?.department ?? raw?.department,
    visitNo: rd?.visitNo ?? raw?.visitNo,
    reportNo: rd?.reportNo ?? raw?.reportNo,
    // 阴性报告
    isNegative: Number(raw?.isNegative ?? 0),
    negativeRemark: raw?.negativeRemark,
    negativeOperator: operatorFallback,
    // 血培养
    bottleNo: raw?.bottleNo,
    bottleType: raw?.bottleType,
    volume: raw?.volume,
    inoculateTime: raw?.inoculateTime,
    alarmTime: raw?.alarmTime,
    ttpHours: raw?.ttpHours,
    bloodCultureResult: raw?.bloodCultureResult,
    // 签名与时间
    testerName: operatorFallback,
    auditorName: raw?.auditorName || (rd?.reportDoctor && !/^\d+$/.test(String(rd.reportDoctor)) ? rd.reportDoctor : operatorFallback) || operatorFallback,
    receiverName: raw?.receiverName || operatorFallback,
    reportDoctor: raw?.auditorName || rd?.reportDoctor || operatorFallback,
    reportTime: rd?.publishTime || rd?.auditTime || raw?.auditTime || raw?.publishTime || raw?.createTime,
    receiveTime: rd?.receiveTime || raw?.receiveTime,
    collectTime: raw?.collectTime || raw?.collectDateTime,
    createTime: rd?.createTime || raw?.createTime,
    organisms: organismList,
    results: [],
    applyItemNames: rd?.applyItems || raw?.applyItems || rd?.cultureSite || raw?.cultureSite || '',
    warmTips: Array.isArray(raw?.warmTips) ? raw.warmTips : undefined,
    remark: raw?.remark || rd?.remark || ''
  }
}

/**
 * 微生物报告打印入口:
 *  - 使用报告模块「模板设计」中微生物专用模板渲染 (micro / micro-negative / micro-blood)
 *  - 复用 ReportRenderer 组件 + openPrintWindow, 与常规报告打印路径保持一致
 *  - 自动根据标本类型 / 阴性结果 / 细菌鉴定情况选择内置模板
 */
async function doPrintMicroReport() {
  if (!reportDetail.value || reportDetail.value.__kind !== 'micro') {
    ElMessage.warning('当前不是微生物报告预览，请先点击左侧行选择报告')
    return
  }
  const rd = reportDetail.value
  const raw = microDetailRaw.value || rd._microRow || ({} as any)
  const orgs = microOrganisms.value || (raw as any).organisms || []
  const specimenType = String((rd.specimenType || raw.specimenType || ''))

  // 自动选择模板类型
  let builtin: BuiltinTemplateType = 'micro'
  const isBlood = specimenType === '血液'
  const isNegative = Number(raw?.isNegative ?? 0) === 1
  if (isBlood) builtin = 'micro-blood'
  else if (isNegative && (!Array.isArray(orgs) || orgs.length === 0)) builtin = 'micro-negative'
  else builtin = 'micro'

  microPrintLoading.value = true
  try {
    // 加载签名, 保证报告签名字段可显示(失败忽略)
    try { await loadStaffSignatures() } catch { /* ignore */ }

    const tpl: any = await resolveMicroTemplate(builtin)
    // 若命中的是服务端通用(非微生物)模板, 强制回退为微生物内置模板
    const tt = tpl?.templateType as string | undefined
    microPrintTemplate.value = (tt && tt.includes('micro') ? tpl : getBuiltInTemplate(builtin))
    microPrintReportNo.value = rd.reportNo || ''
    microPrintData.value = buildMicroPrintData(rd, raw, orgs)
    // 打开预览弹窗(用户确认后点击打印 -> doMicroPrintByTemplate)
    microPrintVisible.value = true
  } catch (e: any) {
    console.error('[report-query] 加载微生物打印模板失败:', e)
    ElMessage.error('加载打印模板失败: ' + (e?.message || String(e)))
  } finally {
    microPrintLoading.value = false
  }
}

/**
 * 从「微生物打印预览」中抽取 ReportRenderer 渲染的 DOM + 样式 -> 生成打印窗口
 * 与 report/micro/index.vue 中 doMicroPrint 完全一致, 保证「报告模板设计」模板打印效果一致
 */
function doMicroPrintByTemplate() {
  const container = microPrintContainerRef.value
  if (!container) { ElMessage.warning('模板预览未就绪'); return }
  const renderer = container.querySelector('.report-renderer-wrapper') as HTMLElement | null
  if (!renderer) { ElMessage.warning('模板预览未就绪'); return }
  let allCss = ''
  document.querySelectorAll('style').forEach((st) => {
    const text = st.textContent || ''
    if (text.trim()) allCss += text + '\n'
  })
  const tpl: any = microPrintTemplate.value
  const paperSize = tpl?.paperSize || 'B5'
  const orientation = tpl?.orientation || 'portrait'
  const margin = tpl?.margin ?? 10
  const safeHtml = sanitizeHtml(renderer.outerHTML)
  const html = `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>${escapeHtml(microPrintReportNo.value || '微生物检验报告')}</title>
<style>
${allCss}
  .report-paper { transform: none !important; box-shadow: none !important; padding: 0 !important; margin: 0 !important; width: auto !important; min-height: auto !important; }
  .report-renderer-wrapper { background: #fff !important; padding: 0 !important; overflow: visible !important; }
  .report-renderer { box-shadow: none !important; margin: 0 !important; }
  .report-page { page-break-after: always; page-break-inside: avoid; margin-bottom: 0 !important; }
  .report-page:last-child { page-break-after: auto; }
  @page { size: ${paperSize} ${orientation}; margin: ${margin}mm; }
</style>
</head>
<body>
  ${safeHtml}
  <script>window.onload = function(){ setTimeout(function(){ window.print(); }, 300); };<\/script>
</body>
</html>`
  openPrintWindow(html, { autoPrint: false })
}

function printReport(row: any) {
  // 微生物报告: 直接打印右侧内嵌预览面板
  if (row?.__kind === 'micro') {
    ElMessageBox.confirm(
      `确认打印 [${row.patientName || '患者'}] 的微生物报告？`,
      '微生物报告打印',
      { type: 'info', confirmButtonText: '打印', cancelButtonText: '取消' }
    ).then(() => {
      // Long 精度: 统一 String 比较, 防止 === 在 number / string 混合时不相等
      const rowMicroId = String(row.__microId != null ? row.__microId : row.id ?? '')
      const curMicroId = String(reportDetail.value?.__microId != null ? reportDetail.value.__microId : reportDetail.value?.id ?? '')
      if (curMicroId && rowMicroId && curMicroId === rowMicroId) {
        doPrintMicroReport()
      } else {
        handleRowClick(row).then(() => nextTick(() => doPrintMicroReport()))
      }
    }).catch(() => {})
    return
  }
  ElMessageBox.confirm(
    `确认打印报告 [${row.patientName}] 的报告？`,
    '打印确认',
    { type: 'info', confirmButtonText: '打印', cancelButtonText: '取消' }
  ).then(() => {
    // 简单打印: 只打印报告预览
    if (reportDetail.value && reportDetail.value.id === row.id) {
      doPrint()
    } else {
      // 先加载详情再打印
      handleRowClick(row).then(() => nextTick(() => doPrint()))
    }
  }).catch(() => {})
}

function printA4Report() {
  if (!reportDetail.value) { ElMessage.warning('请先选择标本'); return }
  doPrint()
}

function printReportDirect() {
  if (!reportDetail.value) { ElMessage.warning('请先选择标本'); return }
  doPrint()
}

function doPrint() {
  const container = previewContainerRef.value
  if (!container) { ElMessage.warning('报告预览未就绪'); return }

  // 从预览中提取 ReportRenderer 的完整 DOM (含所有分页)
  const renderer = container.querySelector('.report-renderer-wrapper') as HTMLElement | null
  if (!renderer) { ElMessage.warning('报告预览未就绪'); return }

  // 收集页面所有 <style> 标签的内容 (包含 ReportRenderer 的 scoped 样式)
  let allCss = ''
  document.querySelectorAll('style').forEach(st => {
    const text = st.textContent || ''
    if (text.trim()) allCss += text + '\n'
  })

  // 获取完整渲染 HTML
  const safeHtml = sanitizeHtml(renderer.outerHTML)

  const html = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>检验报告单</title>
  <style>
    ${allCss}
    /* 打印覆盖: 去除缩放/阴影/滚动等预览态样式 */
    .report-paper {
      transform: none !important;
      transform-origin: unset !important;
      box-shadow: none !important;
      padding: 0 !important;
      margin: 0 !important;
      width: auto !important;
      min-height: auto !important;
    }
    .report-renderer-wrapper {
      background: #fff !important;
      padding: 0 !important;
      overflow: visible !important;
    }
    .report-renderer {
      box-shadow: none !important;
      margin: 0 !important;
    }
    .report-page {
      page-break-after: always;
      page-break-inside: avoid;
      margin-bottom: 0 !important;
    }
    .report-page:last-child {
      page-break-after: auto;
    }
    @page {
      size: ${printSettings.paperSize} ${printSettings.orientation};
      margin: ${printSettings.margin}mm;
    }
  </style>
</head>
<body>
  ${safeHtml}
  <script>
    window.onload = function() { setTimeout(function() { window.print(); }, 300); };
  <\/script>
</body>
</html>`

  openPrintWindow(html, { autoPrint: false })
}

async function generatePDF() {
  if (!reportDetail.value) { ElMessage.warning('请先选择标本'); return }
  // 简化版: 使用打印另存为PDF
  ElMessage.info('即将打开打印窗口，请在打印对话框中选择"另存为PDF"')
  nextTick(() => doPrint())
}

// ============ 报告修改 ============
interface ModifyRow {
  resultId: number
  testItemName: string
  originValue: string
  resultValue: string
  unit: string
  modifyReason: string
}
const modifyVisible = ref(false)
const modifySaving = ref(false)
const modifyRows = ref<ModifyRow[]>([])

function openModifyDialog() {
  if (!reportDetail.value) { ElMessage.warning('请先选择标本'); return }
  const results: ResultVO[] = reportDetail.value.results || []
  modifyRows.value = results.map(r => ({
    resultId: r.id,
    testItemName: r.testItemName || r.testItemCode || '-',
    originValue: r.resultValue || '',
    resultValue: r.resultValue || '',
    unit: r.unit || '',
    modifyReason: ''
  }))
  modifyVisible.value = true
}

async function submitModify() {
  if (!reportDetail.value) return
  // 只提交有变更的项
  const changed = modifyRows.value.filter(row =>
    row.resultValue.trim() !== '' && row.resultValue !== row.originValue
  )
  if (changed.length === 0) {
    ElMessage.warning('没有修改任何结果')
    return
  }
  const missingReason = changed.find(row => !row.modifyReason.trim())
  if (missingReason) {
    ElMessage.warning('修改结果后必须填写修改原因')
    return
  }
  modifySaving.value = true
  try {
    await modifyReport({
      reportId: reportDetail.value.id,
      results: changed.map(row => ({
        resultId: row.resultId,
        resultValue: row.resultValue,
        modifyReason: row.modifyReason
      }))
    })
    ElMessage.success('报告修改成功')
    modifyVisible.value = false
    // 刷新详情
    await handleRowClick({ id: reportDetail.value.id })
  } catch (e: any) {
    ElMessage.error('修改失败: ' + (e?.message || '未知错误'))
  } finally {
    modifySaving.value = false
  }
}

onMounted(() => {
  const now = dayjs()
  dateRange.value = [
    now.startOf('day').format('YYYY-MM-DD HH:mm:ss'),
    now.endOf('day').format('YYYY-MM-DD HH:mm:ss')
  ]
  dateLabel.value = '今天'
  fetchList()

  nextTick(() => {
    if (previewContainerRef.value) {
      resizeObserver = new ResizeObserver(() => updatePreviewScale())
      resizeObserver.observe(previewContainerRef.value)
      updatePreviewScale()
    }
  })
})

function setupContentObserver() {
  if (contentObserver) contentObserver.disconnect()
  const paper = reportPaperRef.value
  if (paper) {
    contentObserver = new ResizeObserver(() => {
      contentHeight.value = paper.scrollHeight || REPORT_H
      updatePreviewScale()
    })
    contentObserver.observe(paper)
  }
}

watch(rightTab, async (val) => {
  if (val === 'preview') {
    await nextTick()
    updatePreviewScale()
    if (previewContainerRef.value && resizeObserver) {
      resizeObserver.disconnect()
      resizeObserver.observe(previewContainerRef.value)
    }
    setupContentObserver()
  }
})

watch(reportDetail, async () => {
  if (rightTab.value === 'preview') {
    await nextTick()
    // 等待报告内容渲染完成后再测量高度
    setTimeout(() => {
      contentHeight.value = (reportPaperRef.value?.scrollHeight) || REPORT_H
      updatePreviewScale()
      setupContentObserver()
    }, 50)
  }
})

onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect()
  if (contentObserver) contentObserver.disconnect()
})
</script>

<style scoped lang="scss">
.report-query-page {
  display: flex;
  gap: 6px;
  height: calc(100vh - 92px);
  padding: 8px;
  background: #f0f2f5;
  overflow: hidden;
}

/* ============ 可拖拽分隔条 ============ */
.resizer {
  flex-shrink: 0;
  width: 10px;
  margin: 0 -2px;
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  touch-action: none;
  user-select: none;

  &:hover,
  &:active {
    background: #e6ebf5;
  }

  .resizer-grip {
    width: 3px;
    height: 44px;
    border-radius: 2px;
    background: #b3c4e0;
  }
}

/* ============ 左侧 ============ */
.left-panel {
  width: 1100px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.filter-bar {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;

  .filter-row {
    display: flex;
    gap: 6px;
    margin-bottom: 6px;
    align-items: center;
    flex-wrap: wrap;
  }

  .search-row {
    display: flex;
    gap: 4px;
    align-items: center;
    margin-top: 6px;

    .el-input { flex: 1; }
  }
}

.list-table-wrapper {
  flex: 1;
  overflow: hidden;
  padding: 0 8px;
}

.other-samples {
  border-top: 2px solid #1a73e8;
  padding: 8px 12px;
  background: #fafbfc;
  flex-shrink: 0;

  .other-samples-header {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 6px;
    font-weight: 600;
    font-size: 13px;
    color: #303133;

    .el-icon { color: #1a73e8; }
  }
}

/* ============ 右侧 ============ */
.right-panel {
  flex: 1;
  background: #fff;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.action-bar {
  display: flex;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
  flex-wrap: wrap;
  background: #fafbfc;
}

.right-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;

  :deep(.el-tabs__content) {
    flex: 1;
    overflow: hidden;
    padding: 0;
  }

  :deep(.el-tab-pane) {
    height: 100%;
    overflow: auto;
  }
}

/* ---- 结果明细 ---- */
.results-container {
  padding: 4px 8px;
  height: 100%;
  overflow: auto;
}

.result-group {
  margin-bottom: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.group-header {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  background: #f5f7fa;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid #ebeef5;

  .toggle-icon {
    transition: transform 0.2s;
    margin-right: 6px;
    color: #606266;

    &.expanded { transform: rotate(90deg); }
  }

  .group-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;

    .group-summary {
      color: #606266;
      font-size: 12px;

      .fee-info { color: #e6a23c; font-weight: 500; margin-right: 6px; }
    }
  }
}

.results-table {
  :deep(.el-table__body td) { padding: 4px 0; }
  :deep(.el-table__body td .cell) { font-size: 12px; }
}

/* 异常结果多级视觉标记 */
.result-mark {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;

  .result-arrow { font-weight: 700; }
  .critical-icon { flex-shrink: 0; }

  &.cell-critical {
    background: #f5222d;
    color: #fff;
    animation: criticalBlink 1s infinite;
  }
  &.cell-high {
    background: #fff7e6;
    color: #e6a23c;
    border: 1px solid #ffe58f;
  }
  &.cell-low {
    background: #f0f5ff;
    color: #1a73e8;
    border: 1px solid #adc6ff;
  }
}

@keyframes criticalBlink {
  0%, 100% { background: #f5222d; }
  50% { background: #ff7875; }
}

/* ---- 预览报告 ---- */
.preview-container {
  padding: 12px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background: #f0f2f5;
  height: 100%;
  overflow: auto;
}

.report-paper {
  background: #fff;
  padding: 20mm;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  font-family: SimSun, '宋体', serif;
  color: #303133;
  font-size: 12px;
}

.report-header {
  text-align: center;
  border-bottom: 2px solid #333;
  padding-bottom: 10px;
  margin-bottom: 15px;

  .report-title {
    font-size: 22px;
    font-weight: bold;
    margin: 5px 0;
    letter-spacing: 4px;
  }

  .report-hospital {
    font-size: 13px;
    color: #606266;
  }
}

.report-info {
  margin-bottom: 12px;

  .info-section {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 0;
  }

  .info-row {
    display: flex;
    border-bottom: 1px solid #ebeef5;
    padding: 4px 0;
    font-size: 12px;

    .info-label {
      width: 85px;
      color: #606266;
      flex-shrink: 0;
    }

    .info-value {
      flex: 1;
      font-weight: 500;
    }
  }
}

.report-results {
  margin-bottom: 15px;
}

.report-group {
  margin-bottom: 10px;
}

.report-group-title {
  background: #f5f7fa;
  padding: 5px 10px;
  font-weight: bold;
  font-size: 13px;
  border-left: 3px solid #1a73e8;
}

.report-results-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;

  th, td {
    border: 1px solid #dcdfe6;
    padding: 4px 6px;
    text-align: center;
  }

  th {
    background: #f5f7fa;
    font-weight: 600;
  }

  .result-cell.critical {
    color: #f56c6c;
    font-weight: bold;
  }

  .flag-high {
    color: #f56c6c;
    margin-left: 2px;
  }

  .flag-low {
    color: #e6a23c;
    margin-left: 2px;
  }
}

.report-footer {
  margin-top: 20px;
  font-size: 12px;

  .sig-line {
    display: flex;
    justify-content: space-around;
    border-top: 1px solid #ebeef5;
    padding-top: 10px;
  }

  .report-note {
    text-align: center;
    color: #909399;
    font-size: 11px;
    margin-top: 8px;
  }
}

/* 打印样式优化 */
@media print {
  .report-query-page > *:not(.preview-container) { display: none !important; }
  .preview-container {
    position: absolute;
    left: 0; top: 0;
    width: 100%;
    background: #fff;
    padding: 0;
  }
  .report-paper {
    box-shadow: none;
    width: 100%;
    min-height: auto;
    padding: 10mm;
  }
}

/* 病案查询弹窗样式 */
.medical-query {
  .medical-summary {
    padding: 10px 12px;
    background: #fafbfc;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    margin-bottom: 12px;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 4px 16px;
    font-size: 12px;
    .m-row {
      display: flex;
      padding: 2px 0;
      span:first-child { width: 90px; color: #606266; flex-shrink: 0; }
      b { font-weight: 500; }
    }
  }
}

/* 标本图弹窗样式 */
.sample-image-container {
  .img-card {
    border: 1px solid #ebeef5;
    border-radius: 4px;
    overflow: hidden;
    margin-bottom: 12px;
    img { width: 100%; display: block; cursor: pointer; height: 220px; object-fit: cover; background:#f5f5f5; }
    .img-title { padding: 6px 10px; font-size: 12px; font-weight: 500; }
    .img-sub { padding: 0 10px 8px; font-size: 11px; color: #909399; }
  }
}

/* 高亮异常: 整行背景提示 (开启时) */
.results-table.highlight-abnormal {
  :deep(.el-table__row) {
    &.result-row-critical { background: #fff1f0; }
    &.result-row-h { background: #fffbe6; }
    &.result-row-l { background: #f0f5ff; }
  }
}

/* 微生物报告内嵌预览区 */
.micro-viewer {
  padding: 12px 8px 0 8px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}
.micro-card :deep(.el-card__header) { padding: 10px 16px; }
.micro-card-header {
  display: flex; align-items: center; justify-content: space-between;
}
.micro-title {
  font-size: 18px; font-weight: 600; color: #303133; letter-spacing: 1px;
}
.micro-card { margin-bottom: 16px; }
.micro-card .specimen-desc { margin-bottom: 16px; }
.micro-card .section-desc.mb12 { margin-bottom: 12px; }
.micro-card .apply-items {
  color: #1f2d3d; font-weight: 500;
}
.micro-card .multiline-text {
  white-space: pre-wrap; word-break: break-all; min-height: 24px; color: #606266;
}
.micro-card .detail-tabs { margin-top: 16px; }
.micro-card .rule-hits-panel {
  margin-bottom: 16px; padding: 10px 12px; border-radius: 6px;
  background: #fdf6ec; border: 1px solid #faecd8;
}
.micro-card .rule-hits-title {
  display: flex; align-items: center; gap: 6px;
  color: #e6a23c; font-weight: 500; margin-bottom: 8px;
}
.micro-card .rule-hits-list { display: flex; flex-wrap: wrap; gap: 6px; }
.micro-card .rule-hit-tag { max-width: 100%; }
.micro-card .sensitivity-expand { padding: 8px 16px 16px; background: #fafafa; }
.micro-card .sens-count-tag { color: #909399; }
.micro-card .section-header.small {
  display: flex; align-items: center; margin-bottom: 8px;
  font-size: 13px; font-weight: 500;
}
.micro-card .no-print { }

/* 打印模式: 微面板溢出隐藏; action-bar 内的 no-print 已隐藏 */
@media print {
  .micro-card { box-shadow: none !important; border: 1px solid #e4e7ed; }
}
</style>
