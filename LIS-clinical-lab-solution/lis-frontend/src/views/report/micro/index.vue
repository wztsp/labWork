<template>
  <div class="micro-report-page">
    <!-- 顶部过滤栏 -->
    <el-card class="top-card" shadow="never">
      <el-form :inline="true" :model="searchForm" size="small" @submit.prevent>
        <el-form-item label="患者">
          <el-input v-model="searchForm.patientName" placeholder="姓名" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="报告状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="初步报告" :value="1" />
            <el-option label="鉴定报告" :value="2" />
            <el-option label="最终报告" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
            clearable
          />
        </el-form-item>
        <el-form-item label="培养基条码核收">
          <el-input
            v-model="receiveBarcode"
            placeholder="扫描或输入培养基条码后回车核收"
            clearable
            style="width: 240px"
            @keyup.enter="handleReceiveByPlateBarcode"
          >
            <template #append>
              <el-button :icon="Search" type="primary" @click="handleReceiveByPlateBarcode">核收</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item>
          <span class="top-stats">
            <el-tag effect="plain">总数: <b>{{ pagination.total }}</b></el-tag>
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 主体: 左右布局 -->
    <div class="main-body">
      <!-- 左侧: 患者列表 -->
      <el-card
        class="left-panel"
        shadow="never"
        :style="{ width: leftPanelWidth + 'px' }"
        @dragstart.prevent
      >
        <template #header>
          <div class="panel-header">
            <span>患者列表</span>
            <el-tag size="small" type="info">{{ tableData.length }} 条</el-tag>
          </div>
        </template>
        <el-table
          v-loading="loading"
          :data="tableData"
          border
          stripe
          size="small"
          height="calc(100vh - 220px)"
          highlight-current-row
          @row-click="handleRowClick"
          :row-class-name="rowClassName"
        >
          <el-table-column prop="reportNo" label="报告编号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="patientName" label="患者" width="80" show-overflow-tooltip />
          <el-table-column prop="specimenType" label="标本类型" width="90" align="center" />
          <el-table-column label="培养条码" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="barcode-text">{{ (row as any).plateBarcode || (row as any).plateBarcodes || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small" effect="plain">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="阴性" width="60" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isNegative === 1" type="info" size="small" effect="dark">阴</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 1"
                type="danger"
                size="small"
                text
                @click.stop="handleCancelReceive(row)"
              >撤销核收</el-button>
              <span v-else style="color:#ccc">-</span>
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
            small
            background
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>

      <!-- 可拖拽分隔条 -->
      <div
        class="resizer"
        title="拖动调整列表/详情宽度"
        @mousedown.prevent="startResize"
      >
        <span class="resizer-grip"></span>
      </div>

      <!-- 右侧: 详情/编辑 -->
      <el-card class="right-panel" shadow="never" v-loading="detailLoading">
        <template #header>
          <div class="panel-header">
            <span v-if="currentReport">
              报告详情 - {{ currentReport.reportNo }}
              <el-tag :type="statusTagType(Number((currentReport as any).status ?? (currentReport as any).reportStatus))" size="small" style="margin-left: 8px">{{ statusText(Number((currentReport as any).status ?? (currentReport as any).reportStatus)) }}</el-tag>
              <el-tag v-if="Number((currentReport as any).isNegative) === 1" type="info" size="small" style="margin-left: 4px">阴性报告</el-tag>
            </span>
            <span v-else>请从左侧选择报告</span>
            <div v-if="currentReport" class="header-actions">
              <el-dropdown trigger="click" @command="handlePrintReport">
                <el-button type="primary" size="small" :icon="Printer">打印报告</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="printSupported && printSupported.identification" command="identification">鉴定报告</el-dropdown-item>
                    <el-dropdown-item v-if="printSupported && printSupported.negative" command="negative">最终阴性报告</el-dropdown-item>
                    <el-dropdown-item v-if="printSupported && printSupported.sensitivity" command="sensitivity">最终药敏报告</el-dropdown-item>
                    <el-dropdown-item v-if="printSupported && printSupported.blood" command="blood">血培养报告</el-dropdown-item>
                    <el-dropdown-item v-if="printSupported && !printSupported.identification && !printSupported.negative && !printSupported.sensitivity && !printSupported.blood" disabled>暂无可打印报告</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-popconfirm
                v-if="Number((currentReport as any).status ?? (currentReport as any).reportStatus) === 1"
                title="确认撤销该报告的核收吗？撤销后将删除本报告，标本可通过培养基条码重新核收。"
                @confirm="handleCancelReceive(currentReport)"
              >
                <template #reference>
                  <el-button type="danger" size="small" plain>撤销核收</el-button>
                </template>
              </el-popconfirm>
              <el-popconfirm
                v-if="Number((currentReport as any).status ?? (currentReport as any).reportStatus) === 3"
                title="确认取消最终报告吗？取消后将回退为鉴定报告，恢复可编辑、可重新发布状态。"
                confirm-button-text="取消最终报告"
                cancel-button-text="再想想"
                @confirm="handleRevokeFinal(currentReport)"
              >
                <template #reference>
                  <el-button type="danger" size="small" plain>取消最终报告</el-button>
                </template>
              </el-popconfirm>
              <el-button
                v-if="Number((currentReport as any).status ?? (currentReport as any).reportStatus) < 3"
                type="success"
                size="small"
                @click="handlePublish(currentReport)"
              >发布{{ statusText(Number((currentReport as any).status ?? (currentReport as any).reportStatus) + 1) }}</el-button>
            </div>
          </div>
        </template>

        <div v-if="!currentReport" class="empty-tip">
          <el-empty description="请从左侧选择一条报告" :image-size="80" />
        </div>

        <div v-else class="detail-content">
          <!-- 标本信息 -->
          <el-descriptions :column="3" border size="small" title="标本信息">
            <el-descriptions-item label="报告编号">{{ currentReport.reportNo }}</el-descriptions-item>
            <el-descriptions-item label="患者姓名">{{ currentReport.patientName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="标本类型">{{ currentReport.specimenType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="采集条码"><span class="barcode-text">{{ currentReport.sampleBarcode || '-' }}</span></el-descriptions-item>
            <el-descriptions-item label="培养条码"><span class="barcode-text">{{ currentReport.plateBarcode || currentReport.plateBarcodes || '-' }}</span></el-descriptions-item>
            <el-descriptions-item label="培养部位">{{ currentReport.cultureSite || '-' }}</el-descriptions-item>
            <el-descriptions-item label="标本号">{{ currentReport.sampleNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="采集时间">{{ formatDateTime(currentReport.collectTime) }}</el-descriptions-item>
            <el-descriptions-item label="采集人">{{ currentReport.collectorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="核收时间">{{ formatDateTime(currentReport.receiveTime) }}</el-descriptions-item>
            <el-descriptions-item label="申请科室">{{ currentReport.department || '-' }}</el-descriptions-item>
            <el-descriptions-item label="报告状态">{{ statusText(Number((currentReport as any).status ?? (currentReport as any).reportStatus)) }}</el-descriptions-item>
            <el-descriptions-item label="申请项目" :span="3"><span class="apply-items">{{ currentReport.applyItems || '-' }}</span></el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(currentReport.createTime) }}</el-descriptions-item>
          </el-descriptions>

          <!-- 专家/耐药规则命中标注 -->
          <div v-if="ruleHits.length > 0" class="rule-hits-panel">
            <div class="rule-hits-title">
              <el-icon><WarnTriangleFilled /></el-icon>
              <span>专家/耐药规则命中（预警可在「特殊耐药/危急值预警」页查看）</span>
            </div>
            <div class="rule-hits-list">
              <el-tag
                v-for="(hit, i) in ruleHits"
                :key="i"
                :type="hit.alertLevel === 'CRITICAL' ? 'danger' : 'warning'"
                effect="dark"
                class="rule-hit-tag"
              >
                {{ hit.alertType }} · {{ hit.organismName }}：{{ hit.message }}
              </el-tag>
            </div>
          </div>

          <!-- Tab: 革兰氏染色 / 阴性报告 / 细菌鉴定+药敏 -->
          <el-tabs v-model="activeTab" class="detail-tabs">
            <!-- 革兰氏染色 -->
            <el-tab-pane label="革兰氏染色" name="gram">
              <el-form :model="gramForm" label-width="100px" size="small" class="gram-form">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="染色结果">
                      <el-select v-model="gramForm.gramStainResult" placeholder="请选择或输入" filterable allow-create style="width: 100%">
                        <el-option label="未见细菌" value="未见细菌" />
                        <el-option label="革兰氏阳性球菌" value="革兰氏阳性球菌" />
                        <el-option label="革兰氏阴性杆菌" value="革兰氏阴性杆菌" />
                        <el-option label="革兰氏阳性杆菌" value="革兰氏阳性杆菌" />
                        <el-option label="革兰氏阴性球菌" value="革兰氏阴性球菌" />
                        <el-option label="革兰氏阴阳混合" value="革兰氏阴阳混合" />
                        <el-option label="真菌孢子" value="真菌孢子" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="操作人">
                      <el-select v-model="gramForm.gramStainOperator" placeholder="选择操作人" filterable allow-create clearable style="width: 100%" default-first-option>
                        <el-option v-for="o in operatorOptions" :key="o" :label="o" :value="o" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="24">
                    <el-form-item label="备注">
                      <el-input v-model="gramForm.gramStainRemark" type="textarea" :rows="3" placeholder="染色形态描述、量、分布等" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="24" v-if="currentReport.gramStainTime">
                    <el-form-item label="染色时间">
                      <span class="text-info">{{ formatDateTime(currentReport.gramStainTime) }}</span>
                    </el-form-item>
                  </el-col>
                </el-row>
                <div class="form-actions">
                  <el-button type="primary" size="small" :loading="gramLoading" :disabled="currentReport.status >= 3" @click="handleSaveGram">保存染色结果</el-button>
                </div>
              </el-form>
            </el-tab-pane>

            <!-- 阴性报告 -->
            <el-tab-pane label="阴性报告" name="negative">
              <el-form :model="negativeForm" label-width="100px" size="small" class="negative-form">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="是否阴性">
                      <el-switch v-model="negativeForm.isNegative" :active-value="1" :inactive-value="0" active-text="阴性" inactive-text="否" :disabled="currentReport.status >= 3" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="操作人">
                      <el-select v-model="negativeForm.negativeOperator" placeholder="选择操作人" filterable allow-create clearable style="width: 100%" default-first-option>
                        <el-option v-for="o in operatorOptions" :key="o" :label="o" :value="o" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="24">
                    <el-form-item label="报告结论">
                      <el-input v-model="negativeForm.negativeRemark" type="textarea" :rows="3" placeholder="如: 培养48小时, 未见致病菌生长" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="24" v-if="currentReport.negativeTime">
                    <el-form-item label="阴性报告时间">
                      <span class="text-info">{{ formatDateTime(currentReport.negativeTime) }}</span>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-alert v-if="negativeForm.isNegative === 1" type="warning" :closable="false" show-icon class="negative-tip">
                  标记为阴性报告后, 报告状态将自动置为「最终报告」, 不可再修改。
                </el-alert>
                <div class="form-actions">
                  <el-button type="primary" size="small" :loading="negativeLoading" :disabled="currentReport.status >= 3" @click="handleSaveNegative">保存阴性报告</el-button>
                </div>
              </el-form>
            </el-tab-pane>

            <!-- 血培养 (仅血液标本) -->
            <el-tab-pane v-if="currentReport?.specimenType === '血液'" label="血培养" name="blood">
              <!-- 血培养信息 -->
              <el-descriptions :column="3" border size="small" class="mb12">
                <el-descriptions-item label="培养瓶号">
                  <el-input v-model="bloodForm.bottleNo" size="small" placeholder="瓶号" :disabled="isReportFinal" />
                </el-descriptions-item>
                <el-descriptions-item label="瓶类型">
                  <el-select v-model="bloodForm.bottleType" size="small" placeholder="选择" :disabled="isReportFinal">
                    <el-option label="成人瓶" value="成人瓶" />
                    <el-option label="儿童瓶" value="儿童瓶" />
                    <el-option label="厌氧瓶" value="厌氧瓶" />
                    <el-option label="需氧瓶" value="需氧瓶" />
                  </el-select>
                </el-descriptions-item>
                <el-descriptions-item label="血量">
                  <el-input v-model="bloodForm.volume" size="small" placeholder="如 10ml" :disabled="isReportFinal" />
                </el-descriptions-item>
                <el-descriptions-item label="接种时间">
                  <el-date-picker v-model="bloodForm.inoculateTime" type="datetime" size="small" placeholder="选择" value-format="YYYY-MM-DDTHH:mm:ss" :disabled="isReportFinal" style="width:100%" />
                </el-descriptions-item>
                <el-descriptions-item label="报警时间">
                  <el-date-picker v-model="bloodForm.alarmTime" type="datetime" size="small" placeholder="选择" value-format="YYYY-MM-DDTHH:mm:ss" :disabled="isReportFinal" style="width:100%" />
                </el-descriptions-item>
                <el-descriptions-item label="TTP(小时)">
                  <el-input v-model="bloodForm.ttpHours" size="small" placeholder="自动计算" disabled />
                </el-descriptions-item>
                <el-descriptions-item label="血培养结果">
                  <el-select v-model="bloodForm.bloodCultureResult" size="small" placeholder="选择" :disabled="isReportFinal">
                    <el-option label="阳性" value="POSITIVE" />
                    <el-option label="阴性" value="NEGATIVE" />
                    <el-option label="培养中" value="PENDING" />
                  </el-select>
                </el-descriptions-item>
              </el-descriptions>
              <div style="text-align:right;margin-bottom:12px">
                <el-button type="primary" size="small" :disabled="isReportFinal" @click="saveBloodCulture">保存血培养信息</el-button>
              </div>

              <!-- 转种记录 -->
              <el-divider content-position="left">转种记录</el-divider>
              <el-table :data="subcultureList" border stripe size="small" max-height="200">
                <el-table-column type="index" label="序号" width="50" align="center" />
                <el-table-column prop="subcultureTime" label="转种时间" width="160">
                  <template #default="{ row }">{{ formatTime(row.subcultureTime) }}</template>
                </el-table-column>
                <el-table-column prop="medium" label="培养基" width="120" />
                <el-table-column prop="incubatorNo" label="培养箱号" width="100" />
                <el-table-column prop="result" label="结果" min-width="100" />
                <el-table-column prop="operator" label="操作人" width="80" />
                <el-table-column label="操作" width="60" align="center">
                  <template #default="{ row }">
                    <el-button type="danger" link size="small" :disabled="isReportFinal" @click="removeSubculture(row)">删除</el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <el-empty description="暂无转种记录" :image-size="40" />
                </template>
              </el-table>

              <!-- 添加转种记录表单 -->
              <el-form v-if="!isReportFinal" :model="subcultureForm" inline size="small" class="mt8">
                <el-form-item label="转种时间">
                  <el-date-picker v-model="subcultureForm.subcultureTime" type="datetime" placeholder="选择" value-format="YYYY-MM-DDTHH:mm:ss" style="width:180px" />
                </el-form-item>
                <el-form-item label="培养基">
                  <!-- 按培养基类型字典配置选择, 可关键字过滤 + 允许手动录入不在字典中的值 -->
                  <el-select
                    v-model="subcultureForm.medium"
                    placeholder="选择培养基"
                    filterable
                    allow-create
                    default-first-option
                    clearable
                    style="width:180px"
                  >
                    <el-option
                      v-for="m in mediumTypeOptions"
                      :key="m.id || m.mediumCode || m.mediumName"
                      :label="m.mediumName"
                      :value="m.mediumName"
                    >
                      <div style="display:flex; justify-content:space-between; gap:8px;">
                        <span>{{ m.mediumName }}</span>
                        <span v-if="m.mediumCode" style="color:#909399; font-size:12px;">{{ m.mediumCode }}</span>
                      </div>
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="培养箱">
                  <el-input v-model="subcultureForm.incubatorNo" placeholder="如 01" style="width:80px" />
                </el-form-item>
                <el-form-item label="结果">
                  <el-input v-model="subcultureForm.result" placeholder="转种结果" style="width:120px" />
                </el-form-item>
                <el-form-item label="操作人">
                  <el-select v-model="subcultureForm.operator" placeholder="操作人" filterable allow-create clearable style="width:80px" default-first-option>
                    <el-option v-for="o in operatorOptions" :key="o" :label="o" :value="o" />
                  </el-select>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" size="small" @click="addSubcultureRecord">添加转种</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 细菌鉴定 + 药敏 -->
            <el-tab-pane :label="`细菌鉴定与药敏 (${organisms.length})`" name="organism">
              <!-- 细菌鉴定 -->
              <div class="section-header">
                <span>细菌鉴定</span>
                <div class="section-actions">
                  <el-button type="primary" link size="small" :disabled="!currentReport.id" @click="runExpertRulesManually">
                    <WarnTriangleFilled />
                    <span style="margin-left:4px">运行耐药规则</span>
                  </el-button>
                  <el-button type="primary" link size="small" :disabled="currentReport.status >= 3" @click="openOrganismAddDialog">+ 添加细菌</el-button>
                </div>
              </div>
              <el-table :data="organisms" border stripe size="small" :expand-row-keys="expandedOrganismIds" row-key="id" :default-expand-all="true" @expand-change="onExpandOrganism">
                <el-table-column type="expand">
                  <template #default="{ row }">
                    <div class="sensitivity-expand">
                      <div class="section-header small">
                        <span>{{ row.organismName }} - 药敏结果</span>
                        <el-button type="primary" link size="small" :disabled="currentReport.status >= 3" @click="openSensitivityDialog(row)">+ 添加药敏</el-button>
                        <el-select
                          v-model="orgTemplateSel[row.id as number]"
                          placeholder="应用抗生素模板"
                          clearable
                          filterable
                          size="small"
                          :loading="!!orgTemplateLoading[row.id as number]"
                          :disabled="currentReport.status >= 3"
                          style="width: 180px; margin-left: 8px"
                          @visible-change="(v: boolean) => v && loadTemplatesForOrganism(row)"
                          @change="applyTemplate(row)"
                        >
                          <el-option
                            v-for="t in (orgTemplates[row.id as number] || [])"
                            :key="t.id"
                            :label="t.name"
                            :value="t.id"
                          />
                        </el-select>
                      </div>
                      <el-table :data="row.sensitivities || []" border size="small">
                        <el-table-column prop="antibioticName" label="抗生素" min-width="140" />
                        <el-table-column prop="micValue" label="MIC" width="100" align="center" />
                        <el-table-column label="结果" width="80" align="center">
                          <template #default="{ row: sub }">
                            <el-tag :type="sensitivityTagType(sub.result)" size="small">{{ sub.result }}</el-tag>
                          </template>
                        </el-table-column>
                        <el-table-column prop="breakpoint" label="判读界值" width="120" align="center" />
                        <el-table-column label="操作" width="130" align="center">
                          <template #default="{ row: sub }">
                            <el-button type="primary" link size="small" :disabled="currentReport.status >= 3" @click="handleEditSensitivity(row, sub)">修改</el-button>
                            <el-button type="danger" link size="small" :disabled="currentReport.status >= 3" @click="handleDeleteSensitivity(row, sub)">删除</el-button>
                          </template>
                        </el-table-column>
                      </el-table>
                      <el-empty v-if="!row.sensitivities || row.sensitivities.length === 0" description="暂无药敏结果" :image-size="40" />
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="organismName" label="细菌名称" min-width="160" />
                <el-table-column prop="organismCode" label="细菌编码" width="120" align="center" />
                <el-table-column prop="quantity" label="生长量" width="100" align="center" />
                <el-table-column prop="colonsMethod" label="培养方法" width="120" align="center" />
                <el-table-column label="药敏数" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" type="info">{{ (row.sensitivities || []).length }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="130" align="center">
                  <template #default="{ row }">
                    <el-button type="primary" link size="small" :disabled="currentReport.status >= 3" @click="handleEditOrganism(row)">修改</el-button>
                    <el-button type="danger" link size="small" :disabled="currentReport.status >= 3" @click="handleDeleteOrganism(row)">删除</el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <el-empty description="暂无细菌鉴定" :image-size="60" />
                </template>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-card>
    </div>

    <!-- 添加/修改细菌弹窗 -->
    <el-dialog v-model="organismDialogVisible" :title="organismEditMode ? '修改细菌鉴定' : '添加细菌鉴定'" width="480px" append-to-body>
      <el-form ref="organismFormRef" :model="organismForm" :rules="organismRules" label-width="90px">
        <el-form-item label="细菌名称" prop="organismName">
          <el-select
            v-model="organismForm.selectedBacteriaId"
            filterable
            remote
            reserve-keyword
            placeholder="搜索或选择细菌"
            :remote-method="remoteSearchBacteria"
            :loading="dictLoading"
            style="width: 100%"
            @change="onSelectBacteria"
          >
            <el-option
              v-for="b in bacteriaOptions"
              :key="b.id"
              :label="`${b.bacteriaName} (${b.bacteriaCode})`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="细菌编码">
          <el-input v-model="organismForm.organismCode" placeholder="选择细菌后自动回填（允许手工覆盖）" />
        </el-form-item>
        <el-form-item label="生长量">
          <el-input v-model="organismForm.quantity" placeholder="如: +、++、+++" />
        </el-form-item>
        <el-form-item label="培养方法">
          <el-input v-model="organismForm.colonsMethod" placeholder="如: 需氧培养" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="organismDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="organismLoading" @click="handleAddOrganism">{{ organismEditMode ? '保存' : '添加' }}</el-button>
      </template>
    </el-dialog>

    <!-- 添加/修改药敏弹窗 -->
    <el-dialog v-model="sensitivityDialogVisible" :title="sensitivityEditMode ? '修改药敏结果' : '添加药敏结果'" width="520px" append-to-body>
      <el-form ref="sensitivityFormRef" :model="sensitivityForm" :rules="sensitivityRules" label-width="90px">
        <el-form-item label="对应细菌">
          <el-input :model-value="currentOrganismName" disabled />
        </el-form-item>
        <el-form-item label="抗生素" prop="antibioticName">
          <el-select
            v-model="sensitivityForm.selectedAntibioticId"
            filterable
            remote
            reserve-keyword
            placeholder="搜索或选择抗生素"
            :remote-method="remoteSearchAntibiotics"
            :loading="dictLoading"
            style="width: 100%"
            @change="onSelectAntibiotic"
          >
            <el-option
              v-for="a in antibioticOptions"
              :key="a.id"
              :label="`${a.antibioticName} (${a.antibioticCode})${a.category ? ' · ' + a.category : ''}`"
              :value="a.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="MIC">
          <el-input v-model="sensitivityForm.micValue" placeholder="如: ≤2" />
        </el-form-item>
        <el-form-item label="结果" prop="result">
          <el-select v-model="sensitivityForm.result" placeholder="请选择" style="width: 100%">
            <el-option label="敏感(S)" value="S" />
            <el-option label="中介(I)" value="I" />
            <el-option label="耐药(R)" value="R" />
          </el-select>
        </el-form-item>
        <el-form-item label="判读界值">
          <el-input v-model="sensitivityForm.breakpoint" placeholder="选择抗生素后自动加载（支持手工调整）" />
          <div v-if="sensitivityForm.breakpointSource" class="text-info" style="font-size:12px;margin-top:4px">
            来源: {{ sensitivityForm.breakpointSource }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sensitivityDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sensitivityLoading" @click="handleAddSensitivity">{{ sensitivityEditMode ? '保存' : '添加' }}</el-button>
      </template>
    </el-dialog>

    <!-- 模板化打印预览 -->
    <el-dialog v-model="printVisible" title="打印预览" width="82%" top="4vh" append-to-body class="micro-print-dialog">
      <div ref="microPrintContainerRef" class="print-preview-container" v-loading="printLoading">
        <template v-if="microPrintTemplate">
          <ReportRenderer :data="microPrintData" :template="microPrintTemplate" :fit-to-page="true" />
        </template>
        <el-empty v-else description="模板加载中或为空" :image-size="80" />
      </div>
      <template #footer>
        <el-button @click="printVisible = false">关闭</el-button>
        <el-tooltip content="打印设置（纸张尺寸、缩放比例等）">
          <el-button :icon="Setting" @click="openPrintConfigDialog">打印设置</el-button>
        </el-tooltip>
        <el-button type="primary" :loading="printLoading" @click="doMicroPrint">打印</el-button>
      </template>
    </el-dialog>
    <PrintConfigDialog
      v-model:visible="printConfigVisible"
      scene="report-micro"
      @saved="onPrintConfigSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Printer, WarnTriangleFilled, Setting } from '@element-plus/icons-vue'
import {
  getAllBacteria,
  getAllAntibiotics,
  getBreakpointsByPair,
  getAntibioticTemplatesByBacteria,
  getAntibioticTemplateApply,
  getAllMediumTypes,
  type Bacteria,
  type Antibiotic,
  type AntibioticBreakpoint,
  type AntibioticTemplate,
  type MediumType
} from '@/api/micro'
import {
  getMicroReportPage,
  getMicroReportById,
  addOrganism,
  updateOrganism,
  deleteOrganism,
  addSensitivity,
  updateSensitivity,
  deleteSensitivity,
  publishMicroReport,
  saveGramStain,
  saveNegativeReport,
  saveBloodCulture as saveBloodCultureApi,
  addSubculture,
  deleteSubculture,
  receiveByPlateBarcode,
  cancelReceiveReport,
  revokeFinalReport,
  evaluateExpertRules,
  getMicroRuleHits,
  type MicroReport,
  type Organism,
  type Sensitivity,
  type Subculture,
  type MicroAlert
} from '@/api/report/micro'
import { formatDateTime, formatTime } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { getUserList } from '@/api/system'
import { escapeHtml, sanitizeHtml } from '@/utils/sanitize'
import { openPrintWindow } from '@/utils/print'
import ReportRenderer from '@/views/report/components/ReportRenderer.vue'
import { getBuiltInTemplate, type BuiltinTemplateType, type ReportTemplateConfig } from '@/api/report/template'
import { getTemplateByInstrument, getDefaultTemplateFromServer } from '@/api/report/template'
import {
  getPrintConfig,
  type PrintConfig,
} from '@/utils/printConfig'
import PrintConfigDialog from '@/components/PrintConfigDialog.vue'

const specimenTypes = ['血液', '尿液', '痰液', '分泌物', '脑脊液', '胸腹水', '粪便', '其他']

const route = useRoute()
let autoOpenMicroId: string | null = null
/** 尝试从路由 query.id 提取待打开的微生物报告ID (字符串, 避免雪花 Long JS 精度丢失) */
function readAutoOpenId(): string | null {
  const v = (route.query as any)?.id
  if (v == null || v === '') return null
  return String(v)
}

/** 返回 [近 N 天前 YYYY-MM-DD, 今日 YYYY-MM-DD] 数组 (给 el-date-picker value-format 消费) */
function buildLastNDaysRange(days = 30): [string, string] {
  function pad(n: number) { return n < 10 ? '0' + n : String(n) }
  const today = new Date()
  const endDate = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`
  const past = new Date(today.getTime() - (days - 1) * 24 * 60 * 60 * 1000)
  const beginDate = `${past.getFullYear()}-${pad(past.getMonth() + 1)}-${pad(past.getDate())}`
  return [beginDate, endDate]
}
const DEFAULT_DATE_RANGE = buildLastNDaysRange(30)

// ============ 左侧列表 ============
const loading = ref(false)
const tableData = ref<MicroReport[]>([])
const receiveBarcode = ref('')
const searchForm = reactive({
  patientName: '',
  status: undefined as number | undefined,
  // 进入页面默认按近 1 个月过滤, UI 上也能看到日期已选, 不再显示空白
  dateRange: [...DEFAULT_DATE_RANGE] as string[]
})
const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

// ============ 左右面板可拖拽调整宽度 ============
const COLS = {
  LEFT_MIN: 300,
  LEFT_MAX: 900,
  LEFT_DEFAULT: 380
}
const leftPanelWidth = ref(COLS.LEFT_DEFAULT)
function startResize(ev: MouseEvent) {
  ev.preventDefault()
  const startX = ev.clientX
  const startWidth = leftPanelWidth.value
  const onMove = (e: MouseEvent) => {
    const delta = e.clientX - startX
    const next = Math.min(COLS.LEFT_MAX, Math.max(COLS.LEFT_MIN, startWidth + delta))
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

// ============ 右侧详情 ============
const currentReport = ref<MicroReport | null>(null)
const detailLoading = ref(false)
const activeTab = ref('gram')
const organisms = ref<Organism[]>([])
const expandedOrganismIds = ref<string[]>([])
/** 专家/耐药规则命中项(鉴定报告标注) */
const ruleHits = ref<MicroAlert[]>([])

// 革兰氏染色表单
const gramLoading = ref(false)
const gramForm = reactive({
  gramStainResult: '',
  gramStainRemark: '',
  gramStainOperator: ''
})

// 阴性报告表单
const negativeLoading = ref(false)
const negativeForm = reactive({
  isNegative: 0,
  negativeRemark: '',
  negativeOperator: ''
})

// 血培养表单
const bloodForm = reactive({
  bottleNo: '',
  bottleType: '',
  volume: '',
  inoculateTime: '',
  alarmTime: '',
  ttpHours: undefined as number | undefined,
  bloodCultureResult: ''
})
const subcultureList = ref<Subculture[]>([])
const subcultureForm = reactive({
  subcultureTime: '',
  medium: '',
  incubatorNo: '',
  result: '',
  operator: ''
})
const isReportFinal = computed(() => currentReport.value?.status === 3)

// ============ 操作人 (可选, 默认当前登录人) ============
const userStore = useUserStore()
const operatorOptions = ref<string[]>([])
/** 当前登录人姓名 (默认操作人) */
const loginOperatorName = computed(() => userStore.realName || userStore.username || '')

// ============ 微生物字典(细菌/抗生素) ============
const bacteriaDict = ref<Bacteria[]>([])
const antibioticDict = ref<Antibiotic[]>([])
const dictLoading = ref(false)

// ============ 培养基类型字典 (用于"培养基"下拉选择) ============
const mediumTypeOptions = ref<MediumType[]>([])
async function loadMediumTypeOptions() {
  try {
    mediumTypeOptions.value = (await getAllMediumTypes()) || []
  } catch (_e) {
    mediumTypeOptions.value = []
  }
}

async function loadMicroDicts() {
  if (dictLoading.value) return
  dictLoading.value = true
  try {
    const [bs, as] = await Promise.all([
      getAllBacteria().catch(() => [] as Bacteria[]),
      getAllAntibiotics().catch(() => [] as Antibiotic[])
    ])
    bacteriaDict.value = bs || []
    antibioticDict.value = as || []
  } finally {
    dictLoading.value = false
  }
}

/** 根据细菌编码反查字典项（用于弹窗编辑回填） */
function findBacteriaByCode(code?: string): Bacteria | undefined {
  if (!code) return undefined
  return bacteriaDict.value.find((b) => b.bacteriaCode === code)
}
/** 根据抗生素编码反查字典项 */
function findAntibioticByCode(code?: string): Antibiotic | undefined {
  if (!code) return undefined
  return antibioticDict.value.find((a) => a.antibioticCode === code)
}
/** 根据 organismCode/细菌名称 查找细菌字典ID（用于联动折点查询） */
function resolveBacteriaId(organism: Organism | null | undefined): number | undefined {
  if (!organism) return undefined
  const hit =
    bacteriaDict.value.find((b) => b.bacteriaCode === (organism as any).organismCode) ||
    bacteriaDict.value.find((b) => b.bacteriaName === organism.organismName)
  return hit?.id
}

/** 加载可操作的检验人员列表 (系统用户), 默认当前登录人 */
async function loadOperators() {
  try {
    // getUserList 接口接受 current/size (分页参数名按后端约定)
    const res = await getUserList({ pageNum: 1, pageSize: 200, status: 1 } as any) || ({} as any)
    const names = new Set<string>()
    if (userStore.realName) names.add(userStore.realName)
    else if (userStore.username) names.add(userStore.username)
    ;(res?.records || []).forEach((u: any) => { if (u?.realName) names.add(u.realName) })
    operatorOptions.value = Array.from(names)
  } catch (e) {
    operatorOptions.value = userStore.realName ? [userStore.realName] : []
  }
  // 表单操作人默认当前登录人 (仅当尚未填写，且不覆盖已加载报告中的值)
  const operatorFallback = loginOperatorName.value || ''
  if (!gramForm.gramStainOperator) gramForm.gramStainOperator = operatorFallback
  if (!negativeForm.negativeOperator) negativeForm.negativeOperator = operatorFallback
  if (!subcultureForm.operator) subcultureForm.operator = operatorFallback
}

// ============ 报告打印 (鉴定/最终阴性/最终药敏/血培养) ============
const printSupported = computed(() => {
  const r = currentReport.value
  if (!r) return { identification: false, negative: false, sensitivity: false, blood: false }
  const orgs = Array.isArray(organisms.value) ? organisms.value : []
  const hasOrg = orgs.length > 0
  const hasSens = orgs.some((o) => Array.isArray(o?.sensitivities) && o.sensitivities.length > 0)
  const st: number = (r as any).status ?? (r as any).reportStatus ?? 0
  const specimenType: string = ((r as any).specimenType || '').toString()
  return {
    identification: hasOrg,
    negative: Number((r as any).isNegative) === 1,
    sensitivity: st >= 3 && hasSens,
    blood: specimenType === '血液'
  }
})

// ============ 报告打印 (使用「报告模块」设计的模板渲染) ============
// command -> 内置微生物模板类型 (仅微生物项目/报告可使用 reportType=3 的模板)
const PRINT_BUILTIN: Record<string, BuiltinTemplateType> = {
  identification: 'micro',      // 细菌鉴定
  sensitivity: 'micro',         // 最终药敏报告 (与鉴定共用 micro 模板, 含药敏表)
  negative: 'micro-negative',   // 最终阴性报告
  blood: 'micro-blood'          // 血培养报告
}

const printVisible = ref(false)
const printLoading = ref(false)
const microPrintTemplate = ref<ReportTemplateConfig | null>(null)
const microPrintData = ref<Record<string, any>>({})
const microPrintReportNo = ref('')
const microPrintContainerRef = ref<HTMLElement>()

// ========== 打印设置 ==========
const printConfigVisible = ref(false)
const currentPrintConfig = ref<PrintConfig>(getPrintConfig('report-micro'))
function openPrintConfigDialog() {
  printConfigVisible.value = true
}
function onPrintConfigSaved(cfg: PrintConfig) {
  currentPrintConfig.value = cfg
  ElMessage.success('打印设置已保存，下次打印时生效')
}

/** 加载微生物专用模板: 优先取服务端 reportType=3 的已存模板, 无则回退内置微生物模板 */
async function resolveMicroTemplate(builtin: BuiltinTemplateType): Promise<ReportTemplateConfig | null> {
  try {
    // 服务端 reportType=3 (微生物业务类型), 保证只命中微生物模板
    const typeTpl = await getDefaultTemplateFromServer(3)
    if (typeTpl) return typeTpl
  } catch { /* 忽略 */ }
  try {
    const insTpl = await getTemplateByInstrument(undefined, 3)
    if (insTpl) return insTpl
  } catch { /* 忽略 */ }
  return getBuiltInTemplate(builtin)
}

/** 报告打印统一入口: command = identification | negative | sensitivity | blood */
async function handlePrintReport(command: string) {
  const r = currentReport.value
  if (!r) return
  printLoading.value = true
  try {
    const builtin = PRINT_BUILTIN[command] || 'micro'
    const tpl = await resolveMicroTemplate(builtin)
    // 若命中的是服务端通用(非微生物)模板, 强制回退为微生物内置模板 (微生物模板只能微生物项目使用)
    const tt = (tpl as any)?.templateType as string | undefined
    microPrintTemplate.value = (tt && tt.includes('micro') ? tpl : getBuiltInTemplate(builtin))
    microPrintReportNo.value = r.reportNo || ''
    microPrintData.value = buildPrintData(r)
    printVisible.value = true
  } catch (e) {
    ElMessage.error('加载打印模板失败: ' + (e as any).message)
  } finally {
    printLoading.value = false
  }
}

/** 将微生物报告数据映射为 ReportRenderer 所需结构 */
function buildPrintData(r: MicroReport): Record<string, any> {
  const orgs: any[] = (organisms.value || []).map((o) => ({
    id: o.id, organismName: o.organismName, organismCode: o.organismCode,
    quantity: o.quantity, colonsMethod: o.colonsMethod,
    sensitivities: (o.sensitivities || []).map((s) => ({
      id: s.id, antibioticName: s.antibioticName, micValue: s.micValue,
      result: s.result, breakpoint: s.breakpoint
    }))
  }))
  const operator = (r as any).negativeOperator || (r as any).gramStainOperator || loginOperatorName.value || ''
  return {
    patientName: (r as any).patientName,
    patientGender: (r as any).patientGender,
    patientAge: (r as any).patientAge,
    specimenType: (r as any).specimenType,
    cultureSite: (r as any).cultureSite,
    sampleBarcode: (r as any).sampleBarcode,
    plateBarcode: (r as any).plateBarcode || (r as any).plateBarcodes || '',
    plateBarcodes: (r as any).plateBarcodes || '',
    specimenNo: (r as any).sampleBarcode || '',
    reportNo: r.reportNo,
    // 阴性报告
    isNegative: (r as any).isNegative,
    negativeRemark: (r as any).negativeRemark,
    negativeOperator: operator,
    // 血培养
    bottleNo: (r as any).bottleNo,
    bottleType: (r as any).bottleType,
    volume: (r as any).volume,
    inoculateTime: (r as any).inoculateTime,
    alarmTime: (r as any).alarmTime,
    ttpHours: (r as any).ttpHours,
    bloodCultureResult: (r as any).bloodCultureResult,
    // 签名与时间
    testerName: operator,
    auditorName: operator,
    receiverName: operator,
    reportTime: (r as any).publishTime || (r as any).auditTime || (r as any).createTime,
    receiveTime: (r as any).receiveTime,
    createTime: (r as any).createTime,
    organisms: orgs,
    results: [],
    applyItemNames: (r as any).cultureSite || ''
  }
}

/** 打印: 抽取 ReportRenderer 渲染的完整 DOM + 其样式 */
function doMicroPrint() {
  const container = microPrintContainerRef.value
  if (!container) { ElMessage.warning('模板预览未就绪'); return }
  const renderer = container.querySelector('.report-renderer-wrapper') as HTMLElement | null
  if (!renderer) { ElMessage.warning('模板预览未就绪'); return }
  let allCss = ''
  document.querySelectorAll('style').forEach((st) => {
    const text = st.textContent || ''
    if (text.trim()) allCss += text + '\n'
  })
  const tpl = microPrintTemplate.value
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
  .report-paper { transform: none !important; box-shadow: none !important; padding: 0 !important; margin: 0 !important; width: auto !important; }
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

// ============ 新增报告(已移除) & 培养基条码核收 ============
async function handleReceiveByPlateBarcode() {
  const code = receiveBarcode.value.trim()
  if (!code) {
    ElMessage.warning('请输入或扫描培养基条码')
    return
  }
  try {
    const res = await receiveByPlateBarcode(code)
    ElMessage.success('核收成功，已自动定位对应微生物报告')
    receiveBarcode.value = ''
    await fetchList()
    if (res && res.id) {
      nextTick(() => {
        const match = tableData.value.find((r) => r.id === res.id)
        if (match) handleRowClick(match)
      })
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '核收失败，请确认条码正确')
  }
}

/**
 * 撤销核收：删除初步报告+回滚培养基核收状态
 */
async function handleCancelReceive(row: any) {
  if (!row?.id) return
  try {
    const tip = `确认撤销报告「${row.reportNo || row.id}」的核收？\n撤销后：该报告将删除，关联培养基条码恢复为未核收状态，可重新核收。`
    await ElMessageBox.confirm(tip, '撤销核收确认', { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '取消' })
  } catch { return }
  try {
    await cancelReceiveReport(String(row.id))
    ElMessage.success('撤销核收成功')
    currentReport.value = null as any
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '撤销核收失败')
  }
}

/**
 * 取消最终报告：将「最终报告」回退为「鉴定报告」，恢复编辑/重新发布
 */
async function handleRevokeFinal(row: any) {
  if (!row?.id) return
  try {
    const res = await revokeFinalReport(String(row.id))
    ElMessage.success('已取消最终报告，报告已回退为鉴定报告')
    currentReport.value = null as any
    if (res && res.id) {
      handleRowClick(res as MicroReport)
    }
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '取消最终报告失败')
  }
}

// ============ 添加/修改细菌 ============
const organismDialogVisible = ref(false)
const organismLoading = ref(false)
const organismFormRef = ref<FormInstance>()
const organismEditMode = ref(false)
const editingOrganismId = ref<number | undefined>(undefined)
const organismForm = reactive({
  organismName: '',
  organismCode: '',
  quantity: '',
  colonsMethod: '',
  selectedBacteriaId: undefined as number | undefined
})
const organismRules: FormRules = {
  organismName: [{ required: true, message: '请选择或输入细菌名称', trigger: 'change' }]
}
// 细菌下拉可选项 (默认全量字典, 远程搜索筛选)
const bacteriaOptions = computed<Bacteria[]>(() => {
  const kw = bacteriaKeyword.value.trim().toLowerCase()
  if (!kw) return bacteriaDict.value
  return bacteriaDict.value.filter((b) =>
    b.bacteriaName.toLowerCase().includes(kw) ||
    b.bacteriaCode.toLowerCase().includes(kw) ||
    (b.gramType && b.gramType.toLowerCase().includes(kw))
  )
})
const bacteriaKeyword = ref('')
function remoteSearchBacteria(kw: string) {
  bacteriaKeyword.value = kw || ''
}
function onSelectBacteria(id: number | undefined) {
  if (!id) return
  const hit = bacteriaDict.value.find((b) => b.id === id)
  if (hit) {
    organismForm.organismName = hit.bacteriaName
    organismForm.organismCode = hit.bacteriaCode
  }
}

// ============ 添加/修改药敏 ============
const sensitivityDialogVisible = ref(false)
const sensitivityLoading = ref(false)
const sensitivityFormRef = ref<FormInstance>()
const sensitivityEditMode = ref(false)
const editingSensitivityId = ref<number | undefined>(undefined)
const sensitivityForm = reactive({
  antibioticName: '',
  antibioticCode: '',
  micValue: '',
  result: '',
  breakpoint: '',
  selectedAntibioticId: undefined as number | undefined,
  breakpointSource: ''
})
const sensitivityRules: FormRules = {
  antibioticName: [{ required: true, message: '请选择或输入抗生素', trigger: 'change' }],
  result: [{ required: true, message: '请选择结果', trigger: 'change' }]
}
const currentOrganism = ref<Organism | null>(null)
const currentOrganismName = computed(() => currentOrganism.value?.organismName || '-')

const antibioticKeyword = ref('')
const antibioticOptions = computed<Antibiotic[]>(() => {
  const kw = antibioticKeyword.value.trim().toLowerCase()
  if (!kw) return antibioticDict.value
  return antibioticDict.value.filter((a) =>
    a.antibioticName.toLowerCase().includes(kw) ||
    a.antibioticCode.toLowerCase().includes(kw) ||
    (a.category && a.category.toLowerCase().includes(kw))
  )
})
function remoteSearchAntibiotics(kw: string) {
  antibioticKeyword.value = kw || ''
}
/** 选择抗生素后: 回填名称/编码 + 联动当前细菌加载折点 */
async function onSelectAntibiotic(id: number | undefined) {
  if (!id) return
  const ab = antibioticDict.value.find((a) => a.id === id)
  if (!ab) return
  sensitivityForm.antibioticName = ab.antibioticName
  sensitivityForm.antibioticCode = ab.antibioticCode

  const bacteriaId = resolveBacteriaId(currentOrganism.value)
  sensitivityForm.breakpoint = ''
  sensitivityForm.breakpointSource = ''

  // 优先: 按 (细菌 + 抗生素) 对查询专属折点
  let points: AntibioticBreakpoint[] = []
  if (bacteriaId) {
    try {
      points = (await getBreakpointsByPair(id, bacteriaId)) || []
    } catch {
      points = []
    }
  }
  if (points && points.length > 0) {
    const p = points[0]
    const parts: string[] = []
    if (p.sBreakpoint) parts.push(`敏感(S)≤${p.sBreakpoint}`)
    if (p.rBreakpoint) parts.push(`耐药(R)≥${p.rBreakpoint}`)
    if (p.iBreakpoint) parts.push(`中介(I)=${p.iBreakpoint}`)
    if (p.sDd) parts.push(`抑菌圈S≥${p.sDd}mm`)
    if (p.rDd) parts.push(`抑菌圈R≤${p.rDd}mm`)
    sensitivityForm.breakpoint = parts.join(' ; ')
    const ver = [p.standard, p.standardVersion, p.method].filter(Boolean).join(' ')
    sensitivityForm.breakpointSource = `专属折点表${ver ? ' (' + ver + ')' : ''}`
    return
  }

  // 回退: 抗生素字典自带折点
  const fallback: string[] = []
  if (ab.breakpointLow != null && ab.breakpointLow !== '') fallback.push(`≤${ab.breakpointLow}`)
  if (ab.breakpointHigh != null && ab.breakpointHigh !== '') fallback.push(`≥${ab.breakpointHigh}`)
  if (fallback.length > 0) {
    sensitivityForm.breakpoint = fallback.join(' ~ ')
    sensitivityForm.breakpointSource = `抗生素字典${ab.method ? ' · ' + ab.method : ''}`
    return
  }
  sensitivityForm.breakpointSource = '暂未配置折点，请手工录入或联系管理员补充折点表'
}

function statusText(status: number): string {
  const map: Record<number, string> = { 1: '初步报告', 2: '鉴定报告', 3: '最终报告' }
  return map[status] || '未知'
}

function statusTagType(status: number): string {
  const map: Record<number, string> = { 1: 'info', 2: 'warning', 3: 'success' }
  return map[status] || 'info'
}

function sensitivityTagType(result: string): string {
  const map: Record<string, string> = { S: 'success', I: 'warning', R: 'danger' }
  return map[result] || 'info'
}

function rowClassName({ row }: { row: MicroReport }): string {
  return currentReport.value && row.id === currentReport.value.id ? 'current-row' : ''
}

// ============ 列表 ============
async function fetchList() {
  loading.value = true
  try {
    const [beginDate, endDate] = Array.isArray(searchForm.dateRange) && searchForm.dateRange.length === 2 ? searchForm.dateRange : [undefined, undefined]
    const res = await getMicroReportPage({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      patientName: searchForm.patientName || undefined,
      status: searchForm.status,
      beginDate: beginDate as string | undefined,
      endDate: endDate as string | undefined
    })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0

    // 若指定了 autoOpenMicroId, 优先打开该报告 (无论是否在当前分页命中)
    if (autoOpenMicroId != null) {
      const openId = String(autoOpenMicroId)
      const match = tableData.value.find(r => String(r.id) === openId)
      if (match) {
        handleRowClick(match)
      } else {
        // 不在当前列表: 直接拉取详情并设置
        try {
          const detail = await getMicroReportById(openId)
          if (detail && (detail as any).id) {
            // 回填到 currentReport, 并追加到列表便于左侧 UI 高亮
            const row = detail as unknown as MicroReport
            const seen = new Set<string>()
            const list: MicroReport[] = []
            for (const x of [row, ...tableData.value]) {
              const key = String((x as any).id)
              if (seen.has(key)) continue
              seen.add(key)
              list.push(x)
            }
            tableData.value = list
            handleRowClick(row)
          } else if (tableData.value.length > 0 && !currentReport.value) {
            handleRowClick(tableData.value[0])
          }
        } catch (e) {
          console.warn('[micro] 自动打开报告失败:', e)
          if (tableData.value.length > 0 && !currentReport.value) {
            handleRowClick(tableData.value[0])
          }
        }
      }
      autoOpenMicroId = null
    } else if (tableData.value.length > 0 && !currentReport.value) {
      // 自动选中第一条
      handleRowClick(tableData.value[0])
    }
  } catch (e) {
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNum = 1
  fetchList()
}

function handleReset() {
  searchForm.patientName = ''
  searchForm.status = undefined
  // 重置按钮也回到"近 1 个月", 与页面首次进入的默认体验一致 (而不是清空为全量)
  searchForm.dateRange = [...DEFAULT_DATE_RANGE]
  receiveBarcode.value = ''
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

// ============ 详情 ============
async function handleRowClick(row: MicroReport) {
  if (!row?.id) return
  // 先使用行数据回填，避免详情加载失败时右侧直接展示空状态（用户感知：点击后右侧不显示）
  const rowStatus: number = (row as any).status ?? row.reportStatus ?? 1
  currentReport.value = { ...row, reportStatus: row.reportStatus ?? rowStatus, status: rowStatus }
  detailLoading.value = true
  try {
    const res = await getMicroReportById(row.id as any)
    if (res) {
      const reportStatus: number = (res as any).reportStatus ?? (res as any).status ?? rowStatus
      organisms.value = (res as any).organisms || []
      subcultureList.value = (res as any).subcultures || []
      // 最终设置详情，保证 status / reportStatus 都可被模板引用
      currentReport.value = { ...res, reportStatus, status: reportStatus, organisms: organisms.value, subcultures: subcultureList.value } as MicroReport
      // 默认展开所有细菌行
      expandedOrganismIds.value = organisms.value.map((o) => String(o.id))
      nextTick(() => {
        // 双重保障：再次同步 expand-row-keys
        expandedOrganismIds.value = (organisms.value || []).map((o) => String(o.id))
      })
      // 同步革兰氏染色表单
      gramForm.gramStainResult = (res as any).gramStainResult || ''
      gramForm.gramStainRemark = (res as any).gramStainRemark || ''
      gramForm.gramStainOperator = (res as any).gramStainOperator || loginOperatorName.value || ''
      // 同步阴性报告表单
      negativeForm.isNegative = (res as any).isNegative ?? 0
      negativeForm.negativeRemark = (res as any).negativeRemark || ''
      negativeForm.negativeOperator = (res as any).negativeOperator || loginOperatorName.value || ''
      // 同步血培养表单
      bloodForm.bottleNo = (res as any).bottleNo || ''
      bloodForm.bottleType = (res as any).bottleType || ''
      bloodForm.volume = (res as any).volume || ''
      bloodForm.inoculateTime = (res as any).inoculateTime || ''
      bloodForm.alarmTime = (res as any).alarmTime || ''
      bloodForm.ttpHours = (res as any).ttpHours
      bloodForm.bloodCultureResult = (res as any).bloodCultureResult || ''
      // 同步转种列表 (如前面未赋值)
      if (!subcultureList.value?.length) subcultureList.value = (res as any).subcultures || []
    }
  } catch (e) {
    // 异常不要清空 currentReport, 否则点击后右侧变成空状态让用户以为点不到
    // 这里只清理子列表，避免残留旧数据
    organisms.value = []
    subcultureList.value = []
  } finally {
    // 双重保险：如果最后 currentReport 仍为空，则回填行数据
    if (!currentReport.value) {
      const rowStatus2: number = (row as any).status ?? (row as any).reportStatus ?? 1
      currentReport.value = { ...row, reportStatus: (row as any).reportStatus ?? rowStatus2, status: rowStatus2 }
    }
    loadRuleHits(row.id as any)
    detailLoading.value = false
  }
}

/** 只读计算该报告的专家/耐药规则命中项(用于鉴定报告标注) */
async function loadRuleHits(id: number | string) {
  if (!id) { ruleHits.value = []; return }
  try {
    ruleHits.value = (await getMicroRuleHits(id as any)) || []
  } catch (e) {
    ruleHits.value = []
  }
}

/** 手动触发耐药规则评估并刷新结果显示 (用户可点击"运行耐药规则") */
async function runExpertRulesManually() {
  if (!currentReport.value?.id) { ElMessage.warning('请先选中一条微生物报告'); return }
  try {
    ElMessage.info('正在运行耐药规则判定...')
    const out = await evaluateExpertRules(currentReport.value.id)
    const n = Array.isArray(out) ? out.length : 0
    ElMessage.success(n > 0 ? `耐药规则判定完成, 命中 ${n} 条预警` : '耐药规则判定完成, 未命中预警')
    await loadRuleHits(currentReport.value.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '运行耐药规则失败')
  }
}

function onExpandOrganism(row: Organism, expanded: Organism[]) {
  expandedOrganismIds.value = expanded.map(r => String(r.id))
}

// ============ 发布 ============
async function handlePublish(row: MicroReport) {
  const nextStatus = (row.status || 0) + 1
  if (nextStatus > 3) return
  try {
    await ElMessageBox.confirm(`确认发布「${statusText(nextStatus)}」吗?`, '提示', { type: 'warning' })
    await publishMicroReport(row.id, nextStatus, 1)
    ElMessage.success('发布成功')
    // 重新加载详情
    await handleRowClick({ ...row, status: nextStatus })
    fetchList()
  } catch (e) {
    // 取消或失败
  }
}

// ============ 革兰氏染色 ============
async function handleSaveGram() {
  if (!currentReport.value) return
  if (!gramForm.gramStainResult) {
    ElMessage.warning('请选择染色结果')
    return
  }
  gramLoading.value = true
  try {
    const res = await saveGramStain(currentReport.value.id, { ...gramForm })
    ElMessage.success('保存成功')
    if (res) {
      currentReport.value = { ...res, status: res.reportStatus ?? currentReport.value.status }
    }
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    gramLoading.value = false
  }
}

// ============ 阴性报告 ============
async function handleSaveNegative() {
  if (!currentReport.value) return
  negativeLoading.value = true
  try {
    const res = await saveNegativeReport(currentReport.value.id, { ...negativeForm })
    ElMessage.success('保存成功')
    if (res) {
      currentReport.value = { ...res, status: res.reportStatus ?? currentReport.value.status }
    }
    fetchList()
  } catch (e) {
    // ignore
  } finally {
    negativeLoading.value = false
  }
}

// ============ 血培养 ============
async function saveBloodCulture() {
  if (!currentReport.value) return
  try {
    const res = await saveBloodCultureApi(currentReport.value.id, { ...bloodForm })
    ElMessage.success('保存成功')
    if (res) {
      currentReport.value = { ...res, status: res.reportStatus ?? currentReport.value.status }
    }
    fetchList()
  } catch (e) {
    // ignore
  }
}

async function addSubcultureRecord() {
  if (!currentReport.value) return
  try {
    await addSubculture(currentReport.value.id, { ...subcultureForm })
    ElMessage.success('添加成功')
    Object.assign(subcultureForm, { subcultureTime: '', medium: '', incubatorNo: '', result: '', operator: '' })
    // 刷新转种列表（重新加载详情）
    await handleRowClick(currentReport.value)
  } catch (e) {
    // ignore
  }
}

async function removeSubculture(row: Subculture) {
  if (!currentReport.value || !row.id) return
  try {
    await ElMessageBox.confirm('确认删除该转种记录吗?', '提示', { type: 'warning' })
    await deleteSubculture(currentReport.value.id, row.id)
    ElMessage.success('删除成功')
    await handleRowClick(currentReport.value)
  } catch (e) {
    // 取消或失败
  }
}

// ============ 添加/修改细菌 ============
function openOrganismAddDialog() {
  organismEditMode.value = false
  editingOrganismId.value = undefined
  Object.assign(organismForm, {
    organismName: '',
    organismCode: '',
    quantity: '',
    colonsMethod: '',
    selectedBacteriaId: undefined
  })
  bacteriaKeyword.value = ''
  loadMicroDicts().finally(() => { organismDialogVisible.value = true })
}

function handleEditOrganism(row: Organism) {
  organismEditMode.value = true
  editingOrganismId.value = row.id
  Object.assign(organismForm, {
    organismName: row.organismName || '',
    organismCode: row.organismCode || (row as any).organismCode || '',
    quantity: row.quantity || '',
    colonsMethod: row.colonsMethod || ''
  }) as any
  bacteriaKeyword.value = ''
  loadMicroDicts().finally(() => {
    const matched = findBacteriaByCode(organismForm.organismCode) ||
      bacteriaDict.value.find((b) => b.bacteriaName === organismForm.organismName)
    organismForm.selectedBacteriaId = matched?.id
    organismDialogVisible.value = true
  })
}

async function handleDeleteOrganism(row: Organism) {
  if (!currentReport.value || !row.id) return
  try {
    await ElMessageBox.confirm(`确认删除细菌 "${row.organismName}" 及其所有药敏结果吗?`, '提示', { type: 'warning' })
    await deleteOrganism(currentReport.value.id, row.id)
    ElMessage.success('删除成功')
    await handleRowClick(currentReport.value)
    if (currentReport.value?.id) {
      try { await evaluateExpertRules(currentReport.value.id) } catch { /* 忽略非致命 */ }
      try { await loadRuleHits(currentReport.value.id) } catch { /* 忽略非致命 */ }
    }
  } catch (e) {
    // 取消或失败
  }
}

async function handleAddOrganism() {
  if (!organismFormRef.value || !currentReport.value) return
  await organismFormRef.value.validate(async (valid) => {
    if (!valid) return
    organismLoading.value = true
    try {
      if (organismEditMode.value && editingOrganismId.value) {
        await updateOrganism(currentReport.value!.id, editingOrganismId.value, { ...organismForm })
        ElMessage.success('修改成功')
      } else {
        await addOrganism(currentReport.value!.id, { ...organismForm })
        ElMessage.success('添加成功')
      }
      organismDialogVisible.value = false
      // 细菌保存后, 立即重新加载报告数据 + 触发耐药规则评估 + 刷新 ruleHits
      await handleRowClick(currentReport.value!)
      if (currentReport.value?.id) {
        try { await evaluateExpertRules(currentReport.value.id) } catch { /* 忽略非致命 */ }
        try { await loadRuleHits(currentReport.value.id) } catch { /* 忽略非致命 */ }
      }
    } catch (e) {
      // ignore
    } finally {
      organismLoading.value = false
    }
  })
}

// ============ 添加/修改药敏 ============
function openSensitivityDialog(row: Organism) {
  currentOrganism.value = row
  sensitivityEditMode.value = false
  editingSensitivityId.value = undefined
  Object.assign(sensitivityForm, {
    antibioticName: '',
    antibioticCode: '',
    micValue: '',
    result: '',
    breakpoint: '',
    selectedAntibioticId: undefined,
    breakpointSource: ''
  })
  antibioticKeyword.value = ''
  loadMicroDicts().finally(() => { sensitivityDialogVisible.value = true })
}

function handleEditSensitivity(organismRow: Organism, sensitivity: Sensitivity) {
  currentOrganism.value = organismRow
  sensitivityEditMode.value = true
  editingSensitivityId.value = sensitivity.id
  Object.assign(sensitivityForm, {
    antibioticName: sensitivity.antibioticName || '',
    antibioticCode: (sensitivity as any).antibioticCode || '',
    micValue: sensitivity.micValue || '',
    result: sensitivity.result || '',
    breakpoint: sensitivity.breakpoint || '',
    selectedAntibioticId: undefined,
    breakpointSource: ''
  })
  antibioticKeyword.value = ''
  loadMicroDicts().finally(() => {
    const matched = findAntibioticByCode(sensitivityForm.antibioticCode) ||
      antibioticDict.value.find((a) => a.antibioticName === sensitivityForm.antibioticName)
    sensitivityForm.selectedAntibioticId = matched?.id
    sensitivityDialogVisible.value = true
  })
}

async function handleDeleteSensitivity(organismRow: Organism, sensitivity: Sensitivity) {
  if (!currentReport.value || !sensitivity.id) return
  try {
    await ElMessageBox.confirm(`确认删除药敏结果 "${sensitivity.antibioticName}" 吗?`, '提示', { type: 'warning' })
    await deleteSensitivity(currentReport.value.id, sensitivity.id)
    ElMessage.success('删除成功')
    await handleRowClick(currentReport.value)
    if (currentReport.value?.id) {
      try { await evaluateExpertRules(currentReport.value.id) } catch { /* 忽略非致命 */ }
      try { await loadRuleHits(currentReport.value.id) } catch { /* 忽略非致命 */ }
    }
  } catch (e) {
    // 取消或失败
  }
}

async function handleAddSensitivity() {
  if (!sensitivityFormRef.value || !currentReport.value || !currentOrganism.value) return
  await sensitivityFormRef.value.validate(async (valid) => {
    if (!valid) return
    sensitivityLoading.value = true
    try {
      if (sensitivityEditMode.value && editingSensitivityId.value) {
        await updateSensitivity(currentReport.value!.id, editingSensitivityId.value, {
          ...sensitivityForm,
          organismId: currentOrganism.value!.id
        })
        ElMessage.success('修改成功')
      } else {
        await addSensitivity(currentReport.value!.id, {
          ...sensitivityForm,
          organismId: currentOrganism.value!.id
        })
        ElMessage.success('添加成功')
      }
      sensitivityDialogVisible.value = false
      // 药敏保存后, 立即重新加载报告数据 + 触发耐药规则评估 + 刷新 ruleHits
      await handleRowClick(currentReport.value!)
      if (currentReport.value?.id) {
        try { await evaluateExpertRules(currentReport.value.id) } catch { /* 忽略非致命 */ }
        try { await loadRuleHits(currentReport.value.id) } catch { /* 忽略非致命 */ }
      }
    } catch (e) {
      // ignore
    } finally {
      sensitivityLoading.value = false
    }
  })
}

// ============ 应用抗生素模板 ============
const orgTemplates = ref<Record<number, AntibioticTemplate[]>>({})
const orgTemplateLoading = ref<Record<number, boolean>>({})
const orgTemplateSel = reactive<Record<number, number | undefined>>({})

async function loadTemplatesForOrganism(row: Organism) {
  const oid = row.id as number
  if (orgTemplates[oid]) return
  const bacteriaId = resolveBacteriaId(row)
  if (!bacteriaId) {
    orgTemplates[oid] = []
    ElMessage.warning(`该细菌「${row.organismName}」未关联细菌字典，无法匹配抗生素模板`)
    return
  }
  orgTemplateLoading[oid] = true
  try {
    orgTemplates[oid] = (await getAntibioticTemplatesByBacteria(bacteriaId)) || []
    if (orgTemplates[oid].length === 0) {
      ElMessage.info(`细菌「${row.organismName}」下暂无启用模板，请先在「抗生素模板」中配置`)
    }
  } catch {
    orgTemplates[oid] = []
  } finally {
    orgTemplateLoading[oid] = false
  }
}

async function applyTemplate(row: Organism) {
  const templateId = orgTemplateSel[row.id as number]
  if (!templateId || !currentReport.value) return
  const bacteriaId = resolveBacteriaId(row)
  if (!bacteriaId) {
    orgTemplateSel[row.id as number] = undefined
    ElMessage.warning('该细菌未关联细菌字典，无法应用模板')
    return
  }
  try {
    const apply = await getAntibioticTemplateApply(templateId, bacteriaId)
    if (!apply || !apply.items || apply.items.length === 0) {
      ElMessage.warning('该模板没有可用的抗生素')
      orgTemplateSel[row.id as number] = undefined
      return
    }
    const existing = (row.sensitivities || []).map((s) =>
      ((s as any).antibioticCode || s.antibioticName || '').toLowerCase())
    const toAdd = apply.items.filter((it) =>
      !existing.includes((it.antibioticCode || it.antibioticName || '').toLowerCase()))
    if (toAdd.length === 0) {
      ElMessage.info(`模板「${apply.templateName}」的抗生素在当前细菌均已存在，无需重复添加`)
      orgTemplateSel[row.id as number] = undefined
      return
    }
    await ElMessageBox.confirm(
      `将从模板「${apply.templateName}」为「${row.organismName}」批量添加 ${toAdd.length} 种抗生素？\n` +
        `（已有 ${apply.items.length - toAdd.length} 种将跳过，MIC/结果留空待填写）`,
      '应用抗生素模板',
      { type: 'warning', confirmButtonText: '应用', cancelButtonText: '取消' }
    )
    for (const it of toAdd) {
      await addSensitivity(currentReport.value!.id, {
        organismId: row.id,
        antibioticName: it.antibioticName,
        antibioticCode: it.antibioticCode,
        micValue: '',
        result: '',
        breakpoint: it.breakpoint || ''
      })
    }
    ElMessage.success(`已批量添加 ${toAdd.length} 种抗生素`)
    await handleRowClick(currentReport.value)
    if (currentReport.value?.id) {
      try { await evaluateExpertRules(currentReport.value.id) } catch { /* 忽略非致命 */ }
      try { await loadRuleHits(currentReport.value.id) } catch { /* 忽略非致命 */ }
    }
  } catch (e) {
    // 用户取消确认或单条写入失败：保持现状
  } finally {
    orgTemplateSel[row.id as number] = undefined
  }
}

onMounted(() => {
  autoOpenMicroId = readAutoOpenId()
  fetchList()
  loadOperators()
  // 预加载细菌/抗生素/培养基类型字典, 打开弹窗/选择下拉时更快
  loadMicroDicts()
  loadMediumTypeOptions()
})

// 当路由 query.id 变化时 (例如在报告查询页反复点击不同微生物行进入) 重新打开
watch(
  () => (route.query as any)?.id,
  (newId: any) => {
    if (newId == null || newId === '') return
    const idStr = String(newId).trim()
    if (!idStr || !/^\d+$/.test(idStr)) return
    autoOpenMicroId = idStr
    const match = tableData.value.find(r => String(r.id) === idStr)
    if (match) {
      handleRowClick(match)
      autoOpenMicroId = null
    } else {
      fetchList()
    }
  }
)
</script>

<style scoped lang="scss">
.micro-report-page {
  padding: 12px;
  height: calc(100vh - 100px);
  display: flex;
  flex-direction: column;

  .top-card {
    margin-bottom: 10px;

    :deep(.el-card__body) {
      padding: 12px 16px 0 16px;
    }

    .top-stats {
      margin-left: 8px;
    }
  }

  .main-body {
    flex: 1;
    display: flex;
    gap: 6px;
    min-height: 0;

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
        background: var(--el-color-primary-light-9);
      }

      .resizer-grip {
        width: 3px;
        height: 40px;
        border-radius: 2px;
        background: var(--el-color-primary-light-5);
      }
    }
  }

  .left-panel {
    width: 380px;
    flex-shrink: 0;
    overflow: hidden;

    :deep(.el-card__body) {
      padding: 8px;
      display: flex;
      flex-direction: column;
      height: calc(100% - 40px);
    }

    .pagination-wrapper {
      display: flex;
      justify-content: center;
      margin-top: 8px;
    }

    :deep(.el-table) {
      cursor: pointer;
    }

    :deep(.current-row) {
      background-color: var(--el-color-primary-light-9) !important;
    }
  }

  .right-panel {
    flex: 1;
    min-width: 0;

    :deep(.el-card__body) {
      padding: 12px 16px;
      max-height: calc(100vh - 180px);
      overflow-y: auto;
    }

    .empty-tip {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 400px;
    }
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-actions {
      display: flex;
      gap: 8px;
    }
  }

  .detail-tabs {
    margin-top: 12px;
  }

  .mb12 {
    margin-bottom: 12px;
  }

  .mt8 {
    margin-top: 8px;
  }

  .section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin: 12px 0 8px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);

    .section-actions { display: flex; align-items: center; gap: 8px; }

    &.small {
      margin: 8px 0 6px;
      font-size: 13px;
    }
  }

  .form-actions {
    margin-top: 12px;
    text-align: right;
  }

  .negative-tip {
    margin-top: 8px;
  }

  .sensitivity-expand {
    padding: 8px 12px;
    background: var(--el-fill-color-light);
  }

  .text-info {
    color: var(--el-text-color-secondary);
  }

  .detail-content {
    :deep(.el-descriptions) {
      margin-bottom: 8px;
    }

    .barcode-text {
      font-family: Consolas, Monaco, monospace;
    }

    .apply-items {
      word-break: break-all;
      line-height: 1.5;
    }
  }

  .rule-hits-panel {
    margin: 0 0 10px;
    padding: 10px 12px;
    border: 1px solid var(--el-color-warning-light-7);
    border-radius: 6px;
    background: var(--el-color-warning-light-9);

    .rule-hits-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 600;
      color: var(--el-color-warning-dark-2);
      margin-bottom: 8px;
    }

    .rule-hits-list {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .rule-hit-tag {
      height: auto;
      white-space: normal;
      line-height: 1.5;
      text-align: left;
    }
  }

  :deep(.micro-print-dialog) {
    .print-preview-container {
      max-height: calc(100vh - 220px);
      overflow: auto;
      background: #f5f7fa;
      border-radius: 4px;
      padding: 4px;
    }
  }
}
</style>
