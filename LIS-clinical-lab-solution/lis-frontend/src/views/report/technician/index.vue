<template>
  <div class="technician-page">
    <div class="top-bar">
      <div class="filter-area">
        <el-form :inline="true" :model="searchForm" size="small">
          <el-form-item label="核收仪器">
            <el-select
              v-model="receiveInstrumentId"
              placeholder="请选择仪器"
              style="width: 180px"
              filterable
              @change="reload"
            >
              <el-option
                v-for="ins in instrumentOptions"
                :key="ins.id"
                :label="ins.instrumentName"
                :value="ins.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="核收日期">
            <el-date-picker
              v-model="searchForm.date"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              style="width: 140px"
              @change="reload"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadSamples">查询</el-button>
          </el-form-item>
          <el-form-item>
            <el-button type="warning" plain @click="unmatchedVisible = true; loadUnmatchedPendingCount()">
              无主标本
              <el-badge v-if="unmatchedPendingCount" :value="unmatchedPendingCount" type="danger" style="margin-left: 6px" />
            </el-button>
          </el-form-item>
        </el-form>
      </div>
      <div class="shortcut-hint">
        <el-tooltip content="保存当前结果" placement="bottom"><span class="hint-item"><b>Ctrl+S</b> 保存</span></el-tooltip>
        <span class="hint-sep">|</span>
        <el-tooltip content="提交终审" placement="bottom"><span class="hint-item"><b>F4</b> 终审</span></el-tooltip>
        <span class="hint-sep">|</span>
        <el-tooltip content="切换上/下一个标本" placement="bottom"><span class="hint-item"><b>Alt+↑↓</b> 切换</span></el-tooltip>
        <span class="hint-sep">|</span>
        <el-tooltip content="有未确认危急值时全部确认, 否则聚焦核收" placement="bottom"><span class="hint-item"><b>F8</b> 危急值/核收</span></el-tooltip>
        <span class="hint-sep">|</span>
        <el-tooltip content="批量审核已勾选标本" placement="bottom"><span class="hint-item"><b>F9</b> 批量审核</span></el-tooltip>
      </div>
    </div>

    <div class="main-content" :class="{ 'resizing': resizing }">
      <!-- 左侧：标本列表（方案B：卡片式） -->
      <div
        class="left-col left-col--cards"
        :style="{ width: leftWidth + 'px' }"
        :data-left-narrow="isLeftColNarrow ? 'true' : 'false'"
      >
        <div class="col-header col-header--cards">
          <span>标本列表</span>
          <span v-if="checkedSamples.length" class="checked-count">
            已选 <b>{{ checkedSamples.length }}</b> 项
            <el-button size="small" link type="danger" @click="clearChecked">清空</el-button>
          </span>
          <div class="header-actions">
            <el-select
              v-model="leftStatusFilter"
              placeholder="全部状态"
              size="small"
              clearable
              style="width: 96px"
            >
              <el-option label="全部" :value="-1" />
              <el-option label="待审" :value="0" />
              <el-option label="终审" :value="1" />
              <el-option label="急诊" :value="2" />
              <el-option label="已完成" :value="3" />
              <el-option label="无主标本" :value="99" />
            </el-select>
          </div>
        </div>
        <div class="receive-toolbar">
          <el-input
            ref="receiveInputRef"
            v-model="receiveBarcode"
            :placeholder="receiveType === 'barcode' ? '扫描/输入条码号回车核收' : `输入${receiveTypeLabel}回车查询`"
            size="small"
            clearable
            style="width: 100%"
            @keyup.enter="handleReceive"
          >
            <template #append>
              <el-select
                v-model="receiveType"
                size="small"
                style="width: 92px"
                popper-class="receive-type-popper"
              >
                <el-option label="条码" value="barcode" />
                <el-option label="就诊号" value="visitNo" />
                <el-option label="门诊号" value="outpatient" />
                <el-option label="住院号" value="inpatient" />
              </el-select>
              <el-button :icon="Check" @click="handleReceive" type="success" :loading="receiving" size="small">核收</el-button>
            </template>
          </el-input>
        </div>
        <div class="list-toolbar">
          <el-input v-model="filterText" placeholder="快速筛选" size="small" clearable style="width: 100%" />
        </div>
        <!-- 方案B：卡片列表（替代 el-table）→ 使用独立 SampleCard 组件减少父级 VNode 创建成本 -->
        <div
          class="sample-card-list"
          v-loading="loading"
          @contextmenu.prevent
        >
          <div v-if="filteredSamples.length === 0" style="padding: 32px 0">
            <el-empty description="暂无标本" :image-size="50" />
          </div>
          <SampleCard
            v-for="(row, idx) in filteredSamples"
            :key="row.id"
            :row="row"
            :index="idx"
            :is-checked="checkedIdsSet.has(row.id)"
            :is-selected="selectedSampleId === row.id"
            @card-click="onCardClick"
            @card-context-menu="onCardContextMenu"
            @check-change="setRowChecked"
            @unmatched-match="onUnmatchedMatch"
            @unmatched-discard="onUnmatchedDiscard"
            @unmatched-to-qc="onUnmatchedToQc"
          />
        </div>
        <div class="left-footer">
          <el-radio-group v-model="activeStatus" size="small" @change="onQuickFilter">
            <el-radio-button :value="-1">全部({{ sampleList.length }})</el-radio-button>
            <el-radio-button :value="0">待审({{ statCounts[0] }})</el-radio-button>
            <el-radio-button :value="3">已完成({{ statCounts[3] }})</el-radio-button>
            <el-radio-button :value="99">无主({{ statCounts[99] ?? 0 }})</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- 标本右键菜单 -->
      <div v-if="contextMenu.visible" class="context-menu-overlay" @click="closeContextMenu" @contextmenu.prevent="closeContextMenu"></div>
      <div
        v-if="contextMenu.visible"
        class="context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        @click.stop
      >
        <div class="context-menu-title">标本操作</div>
        <div v-for="item in contextMenuItems" :key="item.command" class="context-menu-item" @click="onContextMenuClick(item.command)">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </div>
      </div>

      <!-- 拖拽分隔条: 标本列表 / 患者信息 -->
      <div class="resize-handle" @mousedown="startResize('left', $event)"></div>

      <!-- 中间：患者信息（方案B：3 Tab 切换） -->
      <div class="middle-col middle-col--tabs" :style="{ width: middleWidth + 'px' }">
        <div class="col-header col-header--tabs">
          <span>患者信息</span>
          <div class="col-header-right">
            <el-tag v-if="selectedSample" :type="getStatusType(selectedSample.status)" size="small">
              {{ getStatusText(selectedSample.status) }}
            </el-tag>
            <div class="patient-edit-bar" v-if="patientDetail">
              <el-button
                v-if="!editingPatientInfo"
                size="small"
                type="primary"
                plain
                :icon="Edit"
                :disabled="!resultEditable"
                @click="startEditPatientInfo"
              >修改标本信息</el-button>
              <template v-else>
                <el-button size="small" type="primary" :icon="Check" :loading="savingPatientInfo" @click="savePatientInfo">保存</el-button>
                <el-button size="small" :icon="Close" @click="cancelEditPatientInfo">取消</el-button>
              </template>
            </div>
          </div>
        </div>
        <div class="patient-info patient-info-grid" v-if="patientDetail">
          <!-- 患者身份头卡（保留） -->
          <div class="patient-main patient-main--compact">
            <div class="patient-avatar">
              <el-icon :size="26"><User /></el-icon>
            </div>
            <div class="patient-basic">
              <div class="patient-name">
                {{ patientDetail.patientName }}
                <el-tag :type="patientDetail.gender === '男' ? '' : 'danger'" size="small" style="margin-left:6px">
                  {{ patientDetail.gender }}
                </el-tag>
                <span class="patient-age">{{ patientDetail.age }}岁</span>
              </div>
              <div class="patient-meta">
                <el-tag v-if="patientDetail.visitType" type="info" size="small">{{ patientDetail.visitType }}</el-tag>
                <el-tag v-if="selectedSample?.isUrgent" type="danger" size="small">急诊</el-tag>
                <el-tag v-if="patientDetail.department" size="small">{{ patientDetail.department }}</el-tag>
              </div>
            </div>
          </div>

          <!-- 时间流程条（增强版：申请→采集→核收→检验→报告 五节点，每节点显示时间+操作人） -->
          <div class="timeline-strip timeline-strip--enhanced">
            <div
              v-for="(node, idx) in timelineNodes"
              :key="node.key"
              class="timeline-node"
              :class="{
                'is-done': !!node.time,
                'is-active': !!node.time,
                'is-edit': editingPatientInfo && node.editable
              }"
            >
              <div class="timeline-node-head">
                <div class="timeline-node-dot">
                  <el-icon v-if="node.time" :size="12"><Check /></el-icon>
                  <span v-else class="timeline-dot-index">{{ idx + 1 }}</span>
                </div>
                <div v-if="idx < timelineNodes.length - 1" class="timeline-connector"
                  :class="{ 'is-done': !!node.time }"
                />
              </div>
              <div class="timeline-node-body">
                <span class="timeline-node-label">{{ node.label }}</span>
                <template v-if="editingPatientInfo && node.editable">
                  <el-date-picker
                    :model-value="editForm[node.key]"
                    type="datetime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    placeholder="未设置"
                    size="small"
                    style="width:100%;margin-top:4px"
                    @update:model-value="v => setEditFormNode(node.key, v)"
                  />
                </template>
                <template v-else>
                  <span class="timeline-node-time" :class="{'is-empty': !node.time}">
                    {{ node.time || '未记录' }}
                  </span>
                </template>
                <!-- 操作人信息：紧凑显示在时间下方，空值也显示标签兜底 -->
                <div class="timeline-node-operator" :class="{'is-empty': !node.operator}">
                  <span class="op-label">{{ node.operatorLabel }}：</span>
                  <span class="op-name">{{ node.operator || '—' }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 字段区：一个独立卡片 = 一个数据项（全部平铺，一眼看完，不切换） -->
          <!--  布局说明：小卡片使用响应式 grid，短字段半宽 / 长字段(诊断/检验项目/住址/备注/临床说明)占满整行；费用独立一块 -->
          <div
            class="pit-card-grid"
            :data-middle-narrow="(middleWidth ?? 320) < 340 ? 'true' : 'false'"
          >
            <!-- 普通字段卡：每个字段一张小卡片，全部展示 -->
            <div
              v-for="row in patientInfoGridRows"
              :key="row.key"
              class="pit-card"
              :class="{ 'pit-card--full': row.fullSpan }"
            >
              <div class="pit-card__label">
                {{ row.label }}
                <el-tooltip
                  v-if="row.fullSpan"
                  :content="String(row.label) + '：点击区域可查看完整内容'"
                  placement="top"
                  :show-after="600"
                  :disabled="!row.value || String(row.value).length < 30"
                >
                  <el-icon class="pit-card__hint"><InfoFilled /></el-icon>
                </el-tooltip>
              </div>
              <!-- 编辑态：仅标本号 / 核收人 用组件编辑，其他字段不提供编辑 -->
              <template v-if="editingPatientInfo">
                <el-input
                  v-if="row.key === 'sampleNo'"
                  v-model="editForm.sampleNo"
                  placeholder="请输入标本号"
                  size="small"
                />
                <el-select
                  v-else-if="row.key === 'receiver'"
                  v-model="editForm.receiverId"
                  filterable
                  placeholder="请选择核收人"
                  size="small"
                  clearable
                >
                  <el-option v-for="st in staffOptions" :key="st.id" :label="st.name" :value="st.id" />
                </el-select>
                <div
                  v-else
                  class="pit-card__value pit-card__value--trunc"
                  :class="row.valueClass"
                  :title="String(row.value ?? '')"
                >{{ row.value }}</div>
              </template>
              <div
                v-else
                class="pit-card__value"
                :class="[row.valueClass, { 'pit-card__value--trunc': !row.fullSpan }]"
                :title="row.fullSpan ? '' : String(row.value ?? '')"
              >
                {{ row.value ?? '—' }}
              </div>
            </div>

            <!-- 费用小卡：若有费用数据再铺一组（也是独立卡全部可见，不合并到上面） -->
            <template v-if="feeCards.length > 0">
              <div
                v-for="fc in feeCards"
                :key="'fee_' + fc.label"
                class="pit-card pit-card--fee"
                :title="fc.label + '：' + (fc.prefix ?? '') + fc.value"
              >
                <div class="pit-card__label pit-card__label--fee">{{ fc.label }}</div>
                <div class="pit-card__value pit-card__value--fee" :class="fc.valueClass">
                  {{ fc.prefix ?? '' }}{{ fc.value }}
                </div>
              </div>
            </template>
          </div>
        </div>
        <el-empty v-else description="请选择标本" :image-size="80" />
      </div>

      <!-- 拖拽分隔条: 患者信息 / 检验结果 -->
      <div class="resize-handle" @mousedown="startResize('middle', $event)"></div>

      <!-- 右侧：检验结果 -->
      <div class="right-col">
        <div class="col-header">
          <span>检验结果 <span v-if="results.length" class="result-count">(共 {{ results.length }} 项)</span></span>
          <div class="header-actions" v-if="selectedSample">
            <el-button type="primary" size="small" :icon="Refresh" @click="loadResults" :loading="resultLoading">刷新</el-button>
            <el-button size="small" :icon="Printer" @click="printReport" :disabled="results.length === 0">打印</el-button>
          </div>
        </div>

        <div class="result-content" v-if="selectedSample">
          <div class="result-toolbar">
            <div class="toolbar-left">
              <el-tag v-if="currentPanel" type="primary" effect="dark">{{ currentPanel }}</el-tag>
              <span v-if="results.length" class="toolbar-summary">
                正常 <b class="text-green">{{ normalCount }}</b> 项 ·
                异常 <b class="text-orange">{{ abnormalCount }}</b> 项 ·
                危急 <b class="text-red">{{ criticalCount }}</b> 项
              </span>
            </div>
            <div class="toolbar-right">
              <el-select
                v-if="panelList.length > 1"
                v-model="selectedPanel"
                placeholder="选择检验项目"
                size="small"
                clearable
                style="width: 160px"
              >
                <el-option label="全部检验项目" value="" />
                <el-option v-for="p in panelList" :key="p" :label="p" :value="p" />
              </el-select>
              <el-tooltip content="按检验项目分组显示" placement="top">
                <el-switch v-model="showPanelColumn" active-text="分组" />
              </el-tooltip>
              <el-tooltip content="检验结果双列显示" placement="top">
                <el-switch v-model="resultLayout" active-value="dual" inactive-value="single" active-text="双列" inactive-text="单列" />
              </el-tooltip>
              <el-popover trigger="click" placement="bottom-end" :width="200">
                <template #reference>
                  <el-button size="small" link :icon="Setting">列</el-button>
                </template>
                <div class="col-setting-title">结果自定义列</div>
                <el-checkbox-group v-model="visibleResultColumns">
                  <el-checkbox v-for="c in resultColumnOptions" :key="c.prop" :value="c.prop" style="display:block;margin:4px 0">
                    {{ c.label }}
                  </el-checkbox>
                </el-checkbox-group>
              </el-popover>
            </div>
          </div>

          <!-- 危急值内嵌处理面板 (就地展开, 不跳转页面) -->
          <div v-if="criticalResults.length" class="critical-board" :class="{ 'critical-all-confirmed': unconfirmedCriticalResults.length === 0 }">
            <div class="critical-board-header">
              <el-icon color="#f5222d"><WarningFilled /></el-icon>
              <span class="critical-board-title">危急值处理 ({{ unconfirmedCriticalResults.length }}/{{ criticalResults.length }})</span>
              <el-tag v-if="unconfirmedCriticalResults.length === 0" type="success" size="small">已全部确认</el-tag>
              <div class="critical-board-actions">
                <el-button v-if="unconfirmedCriticalResults.length > 0" type="danger" size="small" @click="confirmAllCritical">
                  <el-icon><Check /></el-icon>&nbsp;全部确认 (F8)
                </el-button>
              </div>
            </div>
            <div class="critical-board-body">
              <div
                v-for="cr in criticalResults"
                :key="cr.id"
                class="critical-item"
                :class="{ 'critical-item-confirmed': isCriticalConfirmed(cr) }"
              >
                <div class="critical-item-main">
                  <el-icon class="critical-warn-icon" color="#f5222d"><WarningFilled /></el-icon>
                  <span class="critical-item-name">{{ cr.testItemName }}</span>
                  <span class="critical-item-value">{{ cr.resultValue }}{{ cr.unit ? ' ' + cr.unit : '' }}</span>
                  <span class="critical-item-ref">参考: {{ cr.refRange || '-' }}</span>
                </div>
                <div class="critical-item-action">
                  <el-tag v-if="isCriticalConfirmed(cr)" type="success" size="small">
                    已确认 {{ criticalConfirmTime(cr) }}
                  </el-tag>
                  <el-button v-else type="danger" size="small" :loading="confirmingCriticalId === cr.id" @click="confirmCriticalValue(cr)">
                    确认危急值
                  </el-button>
                </div>
              </div>
            </div>
          </div>

          <!-- 标本级图像 (一个标本对应多个图像, 血常规/尿常规等形态图像) -->
          <div v-if="sampleImages.length" class="sample-image-section">
            <div class="sample-image-title">
              <span>标本图像</span>
              <span class="sample-image-count">{{ sampleImages.length }} 张</span>
            </div>
            <div class="sample-image-list">
              <div
                v-for="(img, idx) in sampleImages"
                :key="img.id"
                class="sample-image-item"
                @click="viewSampleImage(idx)"
              >
                <img :src="img.imageUrl" class="sample-image-thumb" :title="img.imageName || `图像${idx + 1}`" />
                <span v-if="img.imageName" class="sample-image-name">{{ img.imageName }}</span>
              </div>
            </div>
          </div>

          <div class="result-table" v-loading="resultLoading">
            <template v-if="displayResults.length">
              <!-- 单列 -->
              <template v-if="resultLayout === 'single'">
                <template v-if="showPanelColumn">
                  <div class="grouped-list">
                    <div v-for="group in groupedResults" :key="group.panelName" class="result-group">
                      <div class="result-group-title">
                        <span class="group-name">{{ group.panelName }}</span>
                        <span class="group-count">{{ group.items.length }} 项</span>
                      </div>
                      <ResultTable
                        :items="group.items"
                        :enabled-columns="visibleResultColumns"
                        :editable="resultEditable"
                        @result-change="onResultChange"
                      />
                    </div>
                  </div>
                </template>
                <ResultTable v-else :items="displayResults" :enabled-columns="visibleResultColumns" :editable="resultEditable" @result-change="onResultChange" />
              </template>

              <!-- 双列: 按检验指标拆分, 分组仅作为组标题 -->
              <template v-else>
                <div class="dual-result-grid">
                  <div class="result-col" v-for="(col, ci) in [leftResults, rightResults]" :key="ci">
                    <template v-if="showPanelColumn">
                      <div v-for="group in groupIndicators(col)" :key="group.panelName + '-' + ci" class="result-group">
                        <div class="result-group-title">
                          <span class="group-name">{{ group.panelName }}</span>
                          <span class="group-count">{{ group.items.length }} 项</span>
                        </div>
                        <ResultTable
                          :items="group.items"
                          :enabled-columns="visibleResultColumns"
                          :editable="resultEditable"
                          @result-change="onResultChange"
                        />
                      </div>
                    </template>
                    <ResultTable v-else :items="col" :enabled-columns="visibleResultColumns" :editable="resultEditable" @result-change="onResultChange" />
                  </div>
                </div>
              </template>
            </template>
            <el-empty v-else-if="resultEditable" description="该标本核收后已生成检验指标，请手工填写结果值" :image-size="40">
              <el-button type="primary" size="small" :icon="Refresh" @click="loadResults">重新加载指标</el-button>
            </el-empty>
            <el-empty v-else description="尚未回传检验结果，等待通讯程序回传" :image-size="40" />
            <div v-if="!resultEditable && displayResults.length" class="readonly-tip">
              <el-alert type="warning" :closable="false" show-icon>
                当前标本已审核/已出报告，结果不可编辑
              </el-alert>
            </div>
          </div>

          <div class="conclusion-section">
            <div class="conclusion-title">检验结论 / 建议</div>
            <el-input v-model="conclusion" type="textarea" :rows="2" :disabled="!resultEditable" placeholder="请输入检验结论和医生建议..." />
          </div>

          <div class="action-bar">
            <el-button type="primary" :icon="Check" @click="saveResults" :loading="saving" :disabled="results.length === 0 || !resultEditable">
              保存结果
            </el-button>
            <el-button type="success" :icon="View" @click="auditPass" :disabled="results.length === 0 || selectedSample?.status === 3">
              终审通过
            </el-button>
            <el-button :icon="Printer" @click="printReport" :disabled="results.length === 0">
              打印报告
            </el-button>
          </div>
        </div>
        <el-empty v-else description="请选择标本" :image-size="80" />
      </div>
    </div>

    <!-- 多标本选择核收对话框 -->
    <el-dialog v-model="lookupDialogVisible" :title="`选择要核收的标本（${receiveTypeLabel}：${lookupKeyword}）`" width="700px" top="8vh">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px">
        共查询到 {{ lookupList.length }} 份可核收标本，请勾选后核收。
      </el-alert>
      <el-table :data="lookupList" v-loading="lookupLoading" size="small" border max-height="420"
        @selection-change="(rows: ReceivableSample[]) => (lookupSelections = rows)">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="sampleBarcode" label="条码号" width="110" show-overflow-tooltip />
        <el-table-column prop="patientName" label="患者" width="90" show-overflow-tooltip />
        <el-table-column prop="sampleType" label="标本类型" width="90" show-overflow-tooltip />
        <el-table-column label="检验项目" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="row.testItems" class="tch-item-chips" :title="row.testItems">
              <span
                v-for="(n, i) in splitTestItems(row.testItems)"
                :key="i"
                class="tch-chip"
                :title="n"
              >{{ n }}</span>
            </div>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="急诊" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.isUrgent) === 1" type="danger" size="small">急诊</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="采集时间" width="135" align="center">
          <template #default="{ row }">
            <span style="font-size: 11px">{{ formatTime(row.collectTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :loading="receiving" @click="receiveByLookupSelect(row)">核收</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="lookupDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="receiving" @click="receiveLookupSelections">批量核收选中 ({{ lookupSelections.length }})</el-button>
      </template>
    </el-dialog>

    <!-- 终审对话框 -->
    <el-dialog v-model="auditVisible" title="报告终审" width="400px">
      <el-form :model="auditForm" label-width="80px">
        <el-form-item label="终审结果">
          <el-radio-group v-model="auditForm.pass">
            <el-radio-button :label="true">通过</el-radio-button>
            <el-radio-button :label="false">驳回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!auditForm.pass" label="驳回原因">
          <el-input v-model="auditForm.reason" type="textarea" :rows="3" placeholder="请输入驳回原因" />
        </el-form-item>
        <el-alert v-if="auditForm.pass" type="warning" :closable="false" show-icon style="margin-top: 8px">
          终审通过后报告将自动完成, 临床端可见, 无需再单独发布。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAudit">确认终审</el-button>
      </template>
    </el-dialog>

    <!-- 打印预览对话框 -->
    <el-dialog v-model="printVisible" title="报告打印预览" width="800px" top="5vh" :close-on-click-modal="false">
      <div class="print-toolbar" style="margin-bottom: 12px; display: flex; align-items: center; gap: 12px;">
        <el-select v-model="selectedTemplateId" placeholder="选择报告模板" size="small" style="width: 220px" @change="onPrintTemplateChange">
          <el-option v-for="t in templateList" :key="t.templateId" :label="(t.templateName || '未命名') + (t.instrumentName ? ' [' + t.instrumentName + ']' : '') + (t.isDefault ? ' ★' : '')" :value="t.templateId" />
        </el-select>
        <el-tooltip content="结果双列显示" placement="top">
          <el-switch v-model="printDualColumn" active-text="双列" inactive-text="单列" size="small" />
        </el-tooltip>
      </div>
      <div class="print-preview" id="printArea">
        <ReportRenderer :template="reportTemplate" :data="printData" :dual-column="printDualColumn" />
      </div>
      <template #footer>
        <el-button @click="printVisible = false">取消</el-button>
        <el-button type="primary" @click="doPrint">确认打印</el-button>
      </template>
    </el-dialog>

    <!-- 结果详情对话框 -->
    <el-dialog v-model="detailVisible" title="指标详情" width="450px">
      <div class="detail-container" v-if="detailRow">
        <div class="detail-row"><span class="detail-label">指标代码</span><span class="detail-value">{{ detailRow.testItemCode }}</span></div>
        <div class="detail-row"><span class="detail-label">指标名称</span><span class="detail-value">{{ detailRow.testItemName }}</span></div>
        <div class="detail-row"><span class="detail-label">结果值</span><span class="detail-value">{{ detailRow.resultValue }} <b v-if="detailRow.abnormalFlag" class="text-danger">{{ detailRow.abnormalFlag }}</b></span></div>
        <div class="detail-row"><span class="detail-label">单位</span><span class="detail-value">{{ detailRow.unit || '-' }}</span></div>
        <div class="detail-row"><span class="detail-label">参考范围</span><span class="detail-value">{{ detailRow.refRange || '-' }}</span></div>
        <div class="detail-row"><span class="detail-label">检验仪器</span><span class="detail-value">{{ detailRow.instrumentName || '-' }}</span></div>
        <div class="detail-row"><span class="detail-label">检验项目</span><span class="detail-value">{{ detailRow.panelName || '-' }}</span></div>
        <div class="detail-row"><span class="detail-label">分类</span><span class="detail-value">{{ detailRow.category || '常规' }}</span></div>
      </div>
      <template #footer>
        <el-button type="primary" :icon="DataLine" @click="openTrendDialog(detailRow)">历史趋势</el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 历史趋势可视化对话框 -->
    <HistoryTrendDialog
      v-model="trendDialogVisible"
      :patient-id="trendDialogPatientId"
      :test-item-id="trendDialogTestItemId"
      :test-item-name="trendDialogTestItemName"
      :unit="trendDialogUnit"
      :patient-text="patientDetail ? `${patientDetail.patientName} ${patientDetail.gender || ''} ${patientDetail.age || ''}` : ''"
    />

    <!-- 标本图像查看对话框 (血常规/尿常规等形态图像, 一个标本多张) -->
    <el-dialog v-model="imageViewerVisible" title="标本图像" width="720px" :close-on-click-modal="false">
      <div class="image-viewer-container" v-if="sampleImages.length">
        <div class="image-viewer-meta">
          <el-tag size="small" type="info">标本图像</el-tag>
          <span class="image-viewer-name">{{ (sampleImages[imageViewerIndex]?.imageName) || `图像${imageViewerIndex + 1}` }}</span>
          <span class="image-viewer-count">{{ imageViewerIndex + 1 }} / {{ sampleImages.length }}</span>
        </div>
        <el-carousel
          height="420px"
          arrow="always"
          :initial-index="imageViewerIndex"
          @change="(i: number) => (imageViewerIndex = i)"
        >
          <el-carousel-item v-for="(img, i) in sampleImages" :key="img.id">
            <img :src="img.imageUrl" class="image-viewer-img" />
          </el-carousel-item>
        </el-carousel>
      </div>
    </el-dialog>

    <!-- ========== 第一阶段：核心批量操作弹窗 ========== -->
    <BatchAuditDialog
      v-model="dialogVisible.batchAudit"
      :samples="checkedSamples"
      @success="onBatchSuccess"
    />
    <BatchModifyResultDialog
      v-model="dialogVisible.batchModifyResult"
      :samples="checkedSamples"
      @success="onBatchSuccess"
    />
    <BatchModifyInspectorDialog
      v-model="dialogVisible.batchModifyInspector"
      :samples="checkedSamples"
      @success="onBatchSuccess"
    />
    <BatchUnauditDialog
      v-model="dialogVisible.batchUnaudit"
      :samples="checkedSamples"
      @success="onBatchSuccess"
    />
    <BatchReprintBarcodeDialog
      v-model="dialogVisible.batchReprintBarcode"
      :samples="checkedSamples"
      @success="onBatchSuccess"
    />
    <BatchImportDialog
      v-model="dialogVisible.batchImport"
      @success="onBatchSuccess"
    />

    <!-- ========== 第二阶段：标本级操作弹窗 ========== -->
    <WaiveReceiveDialog
      v-model="dialogVisible.waiveReceive"
      :samples="checkedSamples"
      @success="onBatchSuccess"
    />
    <SampleCopyDialog
      v-model="dialogVisible.sampleCopy"
      :samples="checkedSamples"
      @success="onBatchSuccess"
    />
    <SampleSplitDialog
      v-model="dialogVisible.sampleSplit"
      :sample="checkedSamples.length === 1 ? checkedSamples[0] : null"
      @success="onBatchSuccess"
    />
    <SampleUnsplitDialog
      v-model="dialogVisible.sampleUnsplit"
      :sample="checkedSamples.length === 1 ? checkedSamples[0] : null"
      @success="onBatchSuccess"
    />
    <SampleDestroyDialog
      v-model="dialogVisible.sampleDestroy"
      :samples="checkedSamples"
      @success="onBatchSuccess"
    />

    <!-- ========== 第三阶段：辅助功能弹窗 ========== -->
    <InstrumentSigninDialog
      v-model="dialogVisible.instrumentSignin"
      :instrument-options="instrumentOptions"
      @success="() => ElMessage.success('签到已记录')"
    />
    <AutoAuditSigninDialog
      v-model="dialogVisible.autoAuditSignin"
      @success="() => {}"
    />
    <FavoriteDialog
      v-model="dialogVisible.favorites"
      :selected-samples="checkedSamples"
      :current-keyword="searchForm.keyword"
      :current-panels="panelList"
      @use="onUseFavorite"
    />

    <!-- ========== 转为质控标本 ========== -->
    <el-dialog v-model="dialogVisible.sampleToQc" title="转为质控标本" width="720px" :close-on-click-modal="false">
      <div v-loading="qcLotLoading">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="将手工标本的检验结果转换为质控记录，请为每个检验指标选择对应的质控品批号。"
          style="margin-bottom: 12px"
        />
        <el-table :data="sampleToQcMappings" border stripe size="small" max-height="380">
          <el-table-column type="index" label="序号" width="50" align="center" />
          <el-table-column prop="testItemName" label="检验指标" min-width="140" />
          <el-table-column prop="resultValue" label="结果值" width="110" align="center" />
          <el-table-column label="质控批号" min-width="240">
            <template #default="{ row }">
              <el-select
                v-model="row.qcLotId"
                placeholder="选择批号"
                filterable
                clearable
                size="small"
                style="width: 100%"
              >
                <el-option
                  v-for="lot in matchingLots(row)"
                  :key="lot.id"
                  :label="`${lot.lotNo} (${QC_LEVEL_MAP[lot.level] ?? ''})`"
                  :value="lot.id"
                />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="dialogVisible.sampleToQc = false">取消</el-button>
        <el-button type="primary" :loading="sampleToQcLoading" @click="confirmSampleToQc">
          确认转换
        </el-button>
      </template>
    </el-dialog>

    <!-- ========== 无主标本转质控 ========== -->
    <el-dialog
      v-model="dialogVisible.unmatchedToQc"
      title="无主标本 → 转质控"
      width="560px"
      :close-on-click-modal="false"
    >
      <div v-loading="unmatchedToQcLotListLoading">
        <el-alert
          type="warning"
          show-icon
          :closable="false"
          title="选择质控品批号，仪器回传的无主结果值将作为质控测量值写入，自动运行 Westgard 规则判定。"
          style="margin-bottom: 12px"
        />
        <el-descriptions v-if="unmatchedToQcTarget" size="small" :column="1" border>
          <el-descriptions-item label="仪器">{{ unmatchedToQcTarget.unmatchedInstrumentName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="样本号">{{ unmatchedToQcTarget.unmatchedSampleNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结果数">{{ unmatchedToQcTarget.unmatchedResultCount ?? (unmatchedToQcTarget.unmatchedResultIds ?? []).length }} 项</el-descriptions-item>
          <el-descriptions-item label="指标摘要">{{ unmatchedToQcTarget.unmatchedResultSummary || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top" style="margin-top: 16px">
          <el-form-item label="质控品批号（必须）" required>
            <el-select
              v-model="unmatchedToQcLotId"
              placeholder="请选择质控品批号（按当前仪器过滤）"
              filterable
              clearable
              style="width: 100%"
              size="default"
            >
              <el-option
                v-for="lot in unmatchedToQcLotList"
                :key="lot.id"
                :label="`${lot.lotNo || '批号 ' + lot.id} · ${lot.itemName || ''}${lot.levels ? ' · ' + lot.levels : ''}`"
                :value="lot.id"
              />
            </el-select>
            <div v-if="unmatchedToQcLotList.length === 0 && !unmatchedToQcLotListLoading" class="el-form-item__error" style="margin-top: 4px">
              当前仪器未配置启用的质控品批号，请先在质控模块维护该仪器的质控品。
            </div>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible.unmatchedToQc = false">取消</el-button>
        <el-button type="primary" :loading="unmatchedToQcLoading" @click="confirmUnmatchedToQc">
          确认转质控
        </el-button>
      </template>
    </el-dialog>

    <!-- ========== 无主标本（仪器未匹配结果）认领 ========== -->
    <UnmatchedDialog v-model="unmatchedVisible" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, markRaw } from 'vue'
import { ElMessage, ElMessageBox, ElInput, ElSelect, ElOption } from 'element-plus'
import {
  Search, Refresh, Check, Printer, Document, View, User, Setting,
  Warning, Star, CopyDocument, Upload, CircleCheck,
  Close, Edit, Avatar, Delete, DocumentCopy, RefreshLeft, EditPen, CircleClose, WarningFilled, DataLine
} from '@element-plus/icons-vue'
import {
  getTechnicianSamples, getTechnicianDetail, saveTechnicianResult,
  auditTechnicianReport, getTechnicianSummary,
  getSampleByBarcode, receiveSample, precheckReceive,
  lookupReceivableSamples, type ReceivableSample,
  cancelReceive, updateSampleNo, updateTechnicianSampleInfo, batchUnauditTechnician,
  type TechnicianSample, type TechnicianPatientsInfo, type TechnicianResult, type TechnicianSampleQuery,
  type TechnicianSampleImage,
  type FavoriteItem
} from '@/api/sample'
import service from '@/api/request' // axios 实例（用于直接传 config，屏蔽全局拦截器的重复 toast）
import {
  getAllInstruments,
  getMyInstruments,
  getUnmatchedPendingCount,
  batchMatchUnmatchedResults,
  batchDiscardUnmatchedResults,
  batchToQcUnmatchedResults,
  type Instrument
} from '@/api/instrument'
import {
  getTemplateByInstrument, getTemplateList, type ReportTemplateConfig
} from '@/api/report/template'
import { confirmCritical } from '@/api/report'
import { getAllActiveStaff } from '@/api/hr'
import { getAllQcLots, sampleToQc, cancelQcBySample, QC_LEVEL_MAP, type QcLot } from '@/api/qc'
import { useUserStore } from '@/stores/user'
import ReportRenderer from '@/views/report/components/ReportRenderer.vue'
import BatchAuditDialog from './dialogs/BatchAuditDialog.vue'
import BatchModifyResultDialog from './dialogs/BatchModifyResultDialog.vue'
import BatchModifyInspectorDialog from './dialogs/BatchModifyInspectorDialog.vue'
import BatchUnauditDialog from './dialogs/BatchUnauditDialog.vue'
import BatchReprintBarcodeDialog from './dialogs/BatchReprintBarcodeDialog.vue'
import BatchImportDialog from './dialogs/BatchImportDialog.vue'
import WaiveReceiveDialog from './dialogs/WaiveReceiveDialog.vue'
import { sanitizeHtml } from '@/utils/sanitize'
import SampleCopyDialog from './dialogs/SampleCopyDialog.vue'
import SampleSplitDialog from './dialogs/SampleSplitDialog.vue'
import SampleUnsplitDialog from './dialogs/SampleUnsplitDialog.vue'
import SampleDestroyDialog from './dialogs/SampleDestroyDialog.vue'
import InstrumentSigninDialog from './dialogs/InstrumentSigninDialog.vue'
import AutoAuditSigninDialog from './dialogs/AutoAuditSigninDialog.vue'
import FavoriteDialog from './dialogs/FavoriteDialog.vue'
import HistoryTrendDialog from './dialogs/HistoryTrendDialog.vue'
import ResultTable from './ResultTable.vue'
import SampleCard from './SampleCard.vue'
import UnmatchedDialog from './unmatched-dialog.vue'

const loading = ref(false)
const resultLoading = ref(false)
// 无主标本（仪器未匹配结果）认领
const unmatchedVisible = ref(false)
const unmatchedPendingCount = ref(0)
async function loadUnmatchedPendingCount() {
  try {
    const c = await getUnmatchedPendingCount()
    unmatchedPendingCount.value = Number(c || 0)
  } catch (_) {
    unmatchedPendingCount.value = 0
  }
}
const saving = ref(false)
const filterText = ref('')
const conclusion = ref('')
const leftStatusFilter = ref<number>(-1)
const activeStatus = ref<number>(-1)
/** 每屏渲染上限（虚拟滚动替代方案：当标本 > 200 条时只渲染可见前 80 条，配合滚动加载兜底）。
 *  该值可根据屏幕分辨率动态调整；此处取保守值确保首渲快速。 */
const maxRenderedCards = computed(() => (sampleList.value.length > 220 ? 80 : 300))
const showAllPanels = ref(false)
const showPanelColumn = ref(true) // 按检验项目分组显示 (默认开启)
const selectedPanel = ref('')
const receiveBarcode = ref('')
const receiveInputRef = ref<any>(null)
const receiving = ref(false)
/** 核收标识类型: barcode条码 / visitNo就诊号 / outpatient门诊号 / inpatient住院号 */
const receiveType = ref<'barcode' | 'visitNo' | 'outpatient' | 'inpatient'>('barcode')
const receiveTypeLabel = computed(() =>
  ({ barcode: '条码号', visitNo: '就诊号', outpatient: '门诊号', inpatient: '住院号' })[receiveType.value]
)
/** 多标本选择核收弹窗 */
const lookupDialogVisible = ref(false)
const lookupLoading = ref(false)
const lookupList = ref<ReceivableSample[]>([])
const lookupKeyword = ref('')
const lookupSelections = ref<ReceivableSample[]>([])
const resultLayout = ref<'single' | 'dual'>('dual') // 结果布局: single 单列, dual 双列

// ========== 列宽拖拽调整 ==========
const leftWidth = ref(340)    // 标本列表列宽
const middleWidth = ref(320)  // 患者信息列宽
const resizing = ref<'left' | 'middle' | null>(null)
const startX = ref(0)
const startLeftW = ref(0)
const startMiddleW = ref(0)
/** 当左列拖拽得特别窄 (<=300px) 时，给标本卡片一个标志，折叠次要信息防拥挤/重叠 */
const isLeftColNarrow = computed(() => leftWidth.value <= 300)

function startResize(target: 'left' | 'middle', e: MouseEvent) {
  resizing.value = target
  startX.value = e.clientX
  startLeftW.value = leftWidth.value
  startMiddleW.value = middleWidth.value
  document.addEventListener('mousemove', onResizeMove)
  document.addEventListener('mouseup', stopResize)
  e.preventDefault()
}

function onResizeMove(e: MouseEvent) {
  if (!resizing.value) return
  const dx = e.clientX - startX.value
  if (resizing.value === 'left') {
    leftWidth.value = Math.min(560, Math.max(220, startLeftW.value + dx))
  } else {
    middleWidth.value = Math.min(480, Math.max(220, startMiddleW.value + dx))
  }
}

function stopResize() {
  resizing.value = null
  document.removeEventListener('mousemove', onResizeMove)
  document.removeEventListener('mouseup', stopResize)
}

// ========== 报告模板 ==========
const reportTemplate = ref<ReportTemplateConfig | null>(null)
const templateList = ref<ReportTemplateConfig[]>([])
const selectedTemplateId = ref<number | null>(null)
const printDualColumn = ref(true) // 打印双列显示

// ========== 多选 & 弹窗 ref（方案B卡片式手动多选管理 + 性能优化 O(1) 查找）==========
const sampleTableRef = ref<any>(null) // 保留兼容引用（方案B不使用 el-table 多选）
const checkedSamples = ref<TechnicianSample[]>([])

/** 已勾选标本 ID 集合：O(1) 查询，避免 200×200=4w 次数组线性查找 */
const checkedIdsSet = computed<Set<number>>(() => {
  const s = new Set<number>()
  for (const r of checkedSamples.value) if (r.id !== undefined) s.add(r.id)
  return s
})

/** 当前选中（详情加载中）标本 ID：O(1) 访问 */
const selectedSampleId = computed<number | undefined>(() => selectedSample.value?.id)

/** 卡片上的复选框是否勾选（O(1)） */
function isRowChecked(row: TechnicianSample): boolean {
  if (!row || row.id === undefined) return false
  return checkedIdsSet.value.has(row.id)
}

/** 设置卡片选中/取消勾选 */
function setRowChecked(row: TechnicianSample, checked: boolean) {
  if (!row || row.id === undefined) return
  const exists = checkedIdsSet.value.has(row.id)
  if (checked && !exists) {
    checkedSamples.value.push(row)
  } else if (!checked && exists) {
    // 用 Set 已存 ID 快速定位索引并删除（保持 checkedSamples 对象引用一致性）
    const idx = checkedSamples.value.findIndex(s => s.id === row.id)
    if (idx >= 0) checkedSamples.value.splice(idx, 1)
  }
}

/** 当前卡片是否为详情加载行（O(1)） */
function isRowSelected(row: TechnicianSample): boolean {
  if (!row || row.id === undefined || selectedSampleId.value === undefined) return false
  return selectedSampleId.value === row.id
}

/** 清空已勾选标本 */
function clearChecked() {
  checkedSamples.value = []
}

/** 卡片点击：切换当前选中行并加载详情（支持复选框事件冒泡排除） */
function onCardClick(row: TechnicianSample, event: MouseEvent) {
  if (!row) return
  // 复选框点击通过 @click.stop 已阻止；此处保险再判断一次
  const target = event.target as HTMLElement
  if (target && target.closest && target.closest('.el-checkbox')) return
  // 与原 onRowClick 相同：若已选中同一行则不重复触发加载（节省接口请求）
  if (selectedSample.value && selectedSample.value.id === row.id) return
  handleSampleSelect(row)
}

/** 卡片右键菜单：自动选中行并定位操作对象 */
function onCardContextMenu(row: TechnicianSample, event: MouseEvent) {
  event.preventDefault()
  if (!row) return
  // 若当前未展示该行详情，则先加载详情
  if (!selectedSample.value || selectedSample.value.id !== row.id) {
    handleSampleSelect(row)
  }
  // 方案B：右键自动唯一勾选该行（作为批量/单条操作对象，与原表格逻辑一致）
  checkedSamples.value = [row]
  contextMenu.x = event.clientX
  contextMenu.y = event.clientY
  contextMenu.visible = true
}

// ========== 无主标本卡片操作（认领 / 作废） ==========

/** 无主标本：认领（匹配到业务标本） */
async function onUnmatchedMatch(row: TechnicianSample) {
  if (!row?.isUnmatched) return
  const ids = row.unmatchedResultIds ?? []
  if (ids.length === 0) {
    ElMessage.warning('未识别到可认领的结果数据')
    return
  }
  try {
    const info = [
      `仪器：${row.unmatchedInstrumentName || '-'}`,
      `样本号：${row.unmatchedSampleNo || '-'}`,
      `结果条数：${row.unmatchedResultCount || ids.length} 项`
    ].join('\n')
    const { value } = await ElMessageBox.prompt(
      `请输入要匹配的业务标本条码/样本号（将回传结果写入对应标本）\n\n${info}`,
      '认领无主标本（匹配到业务）',
      {
        confirmButtonText: '认领匹配',
        cancelButtonText: '取消',
        inputPattern: /.+/,
        inputErrorMessage: '条码不能为空',
        inputValue: row.unmatchedSampleNo || '',
        type: 'success'
      }
    )
    const cnt = await batchMatchUnmatchedResults(ids, String(value).trim())
    ElMessage.success(`认领成功：已匹配 ${cnt} 条结果到业务标本 ${value}`)
    // 刷新列表（标本卡片会消失，对应业务标本会获得新结果）
    loadSamples()
    loadUnmatchedPendingCount()
    // 若当前选中的是被认领的无主标本，则清空详情区（避免显示已不存在的行）
    if (selectedSample.value?.id === row.id) {
      selectedSample.value = undefined
      patientDetail.value = undefined
      reportDetail.value = undefined
      resultList.value = []
    }
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message || '认领失败')
  }
}

/** 无主标本：作废 */
async function onUnmatchedDiscard(row: TechnicianSample) {
  if (!row?.isUnmatched) return
  const ids = row.unmatchedResultIds ?? []
  if (ids.length === 0) {
    ElMessage.warning('未识别到可作废的结果数据')
    return
  }
  try {
    const info = [
      `仪器：${row.unmatchedInstrumentName || '-'}`,
      `样本号：${row.unmatchedSampleNo || '-'}`,
      `结果条数：${row.unmatchedResultCount || ids.length} 项`
    ].join('\n')
    await ElMessageBox.confirm(
      `确认作废该无主标本的所有结果？\n作废后不再显示，结果不会写入任何业务标本。\n\n${info}`,
      '作废无主标本',
      { type: 'warning', confirmButtonText: '确认作废', cancelButtonText: '取消' }
    )
    await batchDiscardUnmatchedResults(ids)
    ElMessage.success('已作废')
    loadSamples()
    loadUnmatchedPendingCount()
    // 若当前选中的是被作废的无主标本，则清空详情区
    if (selectedSample.value?.id === row.id) {
      selectedSample.value = undefined
      patientDetail.value = undefined
      reportDetail.value = undefined
      resultList.value = []
    }
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '作废失败')
  }
}

/** 无主标本：打开转质控弹窗（按仪器过滤质控品批号，一次性把卡片里所有结果转成对应质控记录） */
async function onUnmatchedToQc(row: TechnicianSample) {
  if (!row?.isUnmatched) return
  const ids = row.unmatchedResultIds ?? []
  if (ids.length === 0) {
    ElMessage.warning('未识别到可转质控的结果数据')
    return
  }
  const instrumentId = typeof (row as any).instrumentId === 'number'
    ? (row as any).instrumentId
    : (typeof (row as any).unmatchedInstrumentId === 'number' ? (row as any).unmatchedInstrumentId : undefined)
  unmatchedToQcTarget.value = row
  unmatchedToQcLotId.value = undefined
  unmatchedToQcLotList.value = []
  unmatchedToQcLotListLoading.value = true
  try {
    if (instrumentId !== undefined) {
      try {
        unmatchedToQcLotList.value = await (await import('@/api/instrument')).getInstrumentQcLots(instrumentId)
      } catch (e) {
        unmatchedToQcLotList.value = []
      }
    }
    dialogVisible.unmatchedToQc = true
  } finally {
    unmatchedToQcLotListLoading.value = false
  }
}

/** 确认无主标本转质控 —— 提交并刷新列表 */
async function confirmUnmatchedToQc() {
  if (!unmatchedToQcTarget.value) return
  const ids = unmatchedToQcTarget.value.unmatchedResultIds ?? []
  if (ids.length === 0) {
    ElMessage.warning('未识别到可转质控的结果数据')
    return
  }
  if (!unmatchedToQcLotId.value) {
    ElMessage.warning('请选择质控品批号')
    return
  }
  unmatchedToQcLoading.value = true
  try {
    const r = await batchToQcUnmatchedResults({
      ids,
      qcLotId: unmatchedToQcLotId.value,
      operatorId: userStore.userId || undefined
    })
    const success = r?.successCount ?? 0
    const fail = r?.failCount ?? 0
    if (fail === 0) {
      ElMessage.success(`转质控成功，已生成 ${success} 条质控记录`)
    } else {
      const detail = (r?.failDetails ?? []).slice(0, 3).map((x: any) => x?.reason).filter(Boolean).join('；')
      ElMessage.warning(`转质控完成：成功 ${success} 条，失败 ${fail} 条${detail ? '；原因：' + detail : ''}`)
    }
    dialogVisible.unmatchedToQc = false
    unmatchedToQcTarget.value = null
    unmatchedToQcLotId.value = undefined
    loadSamples()
    loadUnmatchedPendingCount()
    if (selectedSample.value?.id === unmatchedToQcTarget.value?.id) {
      selectedSample.value = undefined
      patientDetail.value = undefined
      reportDetail.value = undefined
      resultList.value = []
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '转质控失败')
  } finally {
    unmatchedToQcLoading.value = false
  }
}

/** 检验项目摘要：若含括号则去掉括号注释，过长截断 */
function formatTestItemsShort(testItems: string | undefined | null): string {
  if (!testItems) return '-'
  const cleaned = testItems.replace(/\([^)]*\)/g, '').trim()
  const items = cleaned.split(/[,，;；]/).map(s => s.trim()).filter(Boolean)
  const joined = items.length > 0 ? items.join(' · ') : cleaned
  if (joined.length <= 26) return joined
  return joined.slice(0, 26) + '…'
}

/** 检验项目拆分数组（去重保序），用于表格/卡片内多 chip 展示 */
function splitTestItems(testItems: string | undefined | null): string[] {
  if (!testItems) return []
  const cleaned = String(testItems).replace(/\([^)]*\)/g, '').trim()
  if (!cleaned) return []
  const raw = cleaned.split(/[,，;；、\s|/\\]+/).map(s => s.trim()).filter(Boolean)
  const seen = new Set<string>()
  const out: string[] = []
  for (const it of raw) {
    if (seen.has(it)) continue
    seen.add(it)
    out.push(it)
  }
  return out
}

/** 时间短格式：MM-DD HH:mm（卡片空间有限，不显示年份/秒） */
function formatTimeShort(time: string | undefined) {
  if (!time) return '-'
  try {
    const d = new Date(time)
    if (isNaN(d.getTime())) return time
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return time
  }
}

/** 长文本截断（卡片诊断列等） */
function truncate(text: string | undefined | null, maxLen: number): string {
  if (!text) return '-'
  if (text.length <= maxLen) return text
  return text.slice(0, maxLen) + '…'
}

/** 性能测试：生成 200 条 Mock 数据用于测试渲染性能 */
function __generateMockSamples(count: number = 200): TechnicianSample[] {
  const departments = ['检验科', '内科', '外科', '儿科', '妇产科', '骨科', '急诊科', '心内科', '神经内科', '皮肤科']
  const testItems = ['肝功能(AST,ALT,TBIL)', '肾功能(Cr,BUN)', '血常规', '尿常规', '凝血四项', '血糖', '血脂四项', '心肌酶谱', '电解质', '感染筛查', '肿瘤标志物', '甲功五项', '生长激素', '泌乳素', '性激素六项', '贫血三项', '叶酸', '维生素B12', '同型半胱氨酸', 'C反应蛋白']
  const genders = ['男', '女']
  const visitTypes = ['门诊', '住院', '体检']
  const list: TechnicianSample[] = []
  const now = Date.now()
  for (let i = 0; i < count; i++) {
    const id = 1000000 + i
    const itemCount = 1 + Math.floor(Math.random() * 5)
    const items: string[] = []
    for (let j = 0; j < itemCount; j++) {
      items.push(testItems[Math.floor(Math.random() * testItems.length)])
    }
    const hasReceive = Math.random() > 0.2
    const hasTest = hasReceive && Math.random() > 0.3
    const hasReport = hasTest && Math.random() > 0.4
    const status = hasReport ? (Math.random() > 0.5 ? 3 : 1) : (hasReceive ? 0 : 0)
    const tReceive = hasReceive ? now - Math.random() * 1000 * 3600 * 24 * 3 : undefined
    const tTest = hasTest ? tReceive! + Math.random() * 1000 * 3600 * 2 : undefined
    const tReport = hasReport ? tTest! + Math.random() * 1000 * 3600 * 4 : undefined
    list.push({
      id,
      patientName: `测试患者${String(i + 1).padStart(3, '0')}`,
      patientGender: genders[i % 2],
      patientAge: String(18 + (i % 60)),
      visitType: visitTypes[i % visitTypes.length],
      visitNo: `V26${String(8200000000 + i).padStart(12, '0')}`,
      sampleNo: `S26${String(82000000 + i).padStart(10, '0')}`,
      sampleBarcode: `B26${String(8200000000 + i).padStart(12, '0')}`,
      department: departments[i % departments.length],
      doctor: ['张医生', '李医生', '王医生', '赵医生'][i % 4],
      testItems: items.join('，'),
      isUrgent: i % 20 === 0 ? 1 : 0,
      receiveTime: tReceive ? new Date(tReceive).toISOString() : undefined,
      testTime: tTest ? new Date(tTest).toISOString() : undefined,
      reportTime: tReport ? new Date(tReport).toISOString() : undefined,
      status
    })
  }
  return list
}

/** 性能测试入口（只在控制台测量，不污染用户可见列表）：
 *  1. 备份当前真实列表
 *  2. 临时切到 200 条 Mock 数据，测量 DOM 渲染耗时
 *  3. 立即还原真实列表，并重新加载真实列表兜底（若原列表为空则调接口）
 */
function __loadMockPerformanceTest() {
  const backup = sampleList.value.slice()
  const prevChecked = checkedSamples.value.slice()
  const prevSelected = selectedSample.value

  loading.value = true
  const t0 = performance.now()
  // 以 50ms 延迟让 loading 先渲染，再开始 mock 测量（避免与真实加载竞争动画帧）
  setTimeout(() => {
    sampleList.value = __generateMockSamples(200)
    const t1 = performance.now()
    console.log(`[性能-Mock] 标本列表加载完成: ${sampleList.value.length} 条数据, 数据生成耗时 ${(t1 - t0).toFixed(1)}ms`)
    requestAnimationFrame(() => {
      const t2 = performance.now()
      const cardList = document.querySelector('.sample-card-list')
      const cardCount = cardList ? cardList.querySelectorAll('.sample-card').length : 0
      const actual = t2 - t1
      const estimated200 = cardCount > 0 && cardCount < 200 ? actual * 200 / cardCount : actual
      console.log(`[性能-Mock] DOM 渲染完成: ${cardCount} 个卡片, 实际首屏渲染耗时 ${actual.toFixed(1)}ms；200 卡全开估算耗时约 ${estimated200.toFixed(1)}ms`)
      if (estimated200 > 200) {
        console.warn('[性能-Mock] ⚠️ 200 卡全开估算耗时超过 200ms，如需持续加载请接入虚拟滚动（vue-virtual-scroller）')
      } else {
        console.log('[性能-Mock] ✅ 首屏 & 200 卡估算渲染性能良好')
      }

      // 测量完成立即还原，避免用户看到假数据（或点击假数据触发后端「标本不存在」错误）
      sampleList.value = backup
      checkedSamples.value = prevChecked
      selectedSample.value = prevSelected
      loading.value = false

      // 如果之前列表为空，说明真实接口还没跑，主动调一次真实加载
      if (backup.length === 0) {
        loadSamples()
      }
    })
  }, 50)
}

// 所有弹窗的可见性 ref
const dialogVisible = reactive({
  batchAudit: false,
  batchModifyResult: false,
  batchModifyInspector: false,
  batchUnaudit: false,
  batchReprintBarcode: false,
  batchImport: false,
  waiveReceive: false,
  sampleCopy: false,
  sampleSplit: false,
  sampleUnsplit: false,
  sampleDestroy: false,
  instrumentSignin: false,
  autoAuditSignin: false,
  favorites: false,
  sampleToQc: false,
  unmatchedToQc: false
})

// ========== 转为质控标本 ==========
const userStore = useUserStore()
const qcLotList = ref<QcLot[]>([])
const qcLotLoading = ref(false)
const sampleToQcLoading = ref(false)
const sampleToQcTarget = ref<TechnicianSample | null>(null)

// ========== 无主标本转质控 ==========
const unmatchedToQcLoading = ref(false)
const unmatchedToQcTarget = ref<TechnicianSample | null>(null)
/** 批量无主转质控：目标质控品批号（一张卡片对应一个质控品） */
const unmatchedToQcLotId = ref<number | undefined>(undefined)
/** 无主卡片可选的质控品批号候选（按仪器过滤）—— 从仪器服务 /{id}/qc-lots 读取 */
const unmatchedToQcLotList = ref<Array<{ id: number; lotNo?: string; levels?: string; expireDate?: string; testItemId?: number; itemName?: string; itemCode?: string }>>([])
const unmatchedToQcLotListLoading = ref(false)
interface SampleToQcMappingRow {
  testItemId: number
  testItemName: string
  resultValue: string
  qcLotId?: number
}
const sampleToQcMappings = ref<SampleToQcMappingRow[]>([])

// ========== 标本右键菜单 ==========
const contextMenu = reactive({ visible: false, x: 0, y: 0 })
const contextMenuItems = [
  { command: 'cancel-receive', label: '撤销核收', icon: RefreshLeft },
  { command: 'waive-receive', label: '让步核收', icon: Warning },
  { command: 'modify-sample-no', label: '修改标本号', icon: EditPen },
  { command: 'instrument-signin', label: '仪器人员签到', icon: User },
  { command: 'favorites', label: '收藏夹', icon: Star },
  { command: 'sample-copy', label: '标本复制', icon: CopyDocument },
  { command: 'batch-import', label: '批量导入', icon: Upload },
  { command: 'auto-audit-signin', label: '自动审核签到', icon: CircleCheck },
  { command: 'sample-split', label: '标本拆分', icon: DocumentCopy },
  { command: 'sample-unsplit', label: '撤销拆分', icon: Refresh },
  { command: 'batch-audit', label: '批量审核(F9)', icon: Check },
  { command: 'batch-unaudit', label: '批量撤销审核', icon: Close },
  { command: 'cancel-complete', label: '取消完成', icon: CircleClose },
  { command: 'batch-modify-result', label: '批量修改结果', icon: Edit },
  { command: 'batch-modify-inspector', label: '批量修改检验人', icon: Avatar },
  { command: 'sample-to-qc', label: '转为质控标本', icon: Document },
  { command: 'cancel-qc', label: '取消质控结果', icon: Delete },
  { command: 'batch-reprint-barcode', label: '批量补打条码', icon: Printer },
  { command: 'sample-destroy', label: '标本销毁', icon: Delete }
]
function closeContextMenu() { contextMenu.visible = false }
function onContextMenuClick(cmd: string) {
  closeContextMenu()
  onOpCommand(cmd)
}

// 核收仪器选择
const receiveInstrumentId = ref<number | undefined>(undefined)
const instrumentOptions = ref<Instrument[]>([])

async function loadInstruments() {
  try {
    instrumentOptions.value = await getMyInstruments()
    if (instrumentOptions.value.length > 0 && receiveInstrumentId.value === undefined) {
      receiveInstrumentId.value = instrumentOptions.value[0].id
    }
    // 若当前已选仪器不在权限范围内，重置到第一项
    if (receiveInstrumentId.value != null && !instrumentOptions.value.some(i => i.id === receiveInstrumentId.value)) {
      receiveInstrumentId.value = instrumentOptions.value[0]?.id ?? undefined
    }
  } catch {
    instrumentOptions.value = []
  }
}

// 左侧列表自定义列
const columnOptions = [
  { prop: 'patientName', label: '姓名' },
  { prop: 'gender', label: '性别' },
  { prop: 'age', label: '年龄' },
  { prop: 'sampleNo', label: '标本号' },
  { prop: 'sampleBarcode', label: '条码号' },
  { prop: 'visitType', label: '就诊类型' },
  { prop: 'visitNo', label: '就诊号' },
  { prop: 'department', label: '科室' },
  { prop: 'testItems', label: '检验项目' },
  { prop: 'isUrgent', label: '急诊' },
  { prop: 'receiveTime', label: '核收时间' },
  { prop: 'testTime', label: '检验时间' },
  { prop: 'reportTime', label: '报告时间' },
  { prop: 'status', label: '状态' }
]
// 方案C: 默认显示更多关键列, 与单元格内辅助小字互补
const visibleColumns = ref(['patientName', 'sampleNo', 'sampleBarcode', 'department', 'testItems', 'isUrgent', 'receiveTime', 'status'])
function isVisible(prop: string) {
  return visibleColumns.value.includes(prop)
}

// 检验结果自定义列
const resultColumnOptions = [
  { prop: 'no', label: 'NO.' },
  { prop: 'name', label: '指标名称' },
  { prop: 'en', label: '英文名' },
  { prop: 'code', label: '项目代码' },
  { prop: 'result', label: '结果' },
  { prop: 'unit', label: '单位' },
  { prop: 'ref', label: '参考范围' },
  { prop: 'hint', label: '提示' },
  { prop: 'lastResult', label: '上次结果' },
  { prop: 'lastFlag', label: '上次标识' },
  { prop: 'lastTime', label: '上次时间' },
  { prop: 'od', label: 'OD值' },
  { prop: 'cutoff', label: 'CUTOFF值' },
  { prop: 'sc', label: 'S/CO值' },
  { prop: 'autoAudit', label: '自动审核' },
  { prop: 'autoAuditMsg', label: '自动审核异常信息' },
  { prop: 'knowledge', label: '项目知识' }
]
const visibleResultColumns = ref(['no', 'name', 'en', 'result', 'ref', 'hint'])
function isResultVisible(prop: string) {
  return visibleResultColumns.value.includes(prop)
}

/** 判断当前选中标本是否可编辑结果(仅未审核/未出报告可编辑) */
const resultEditable = computed(() => {
  if (!selectedSample.value) return false
  // status: 0待审 1终审 2急诊 3已完成
  // 仅 0(待审) 和 2(急诊未审) 可编辑; 1(终审) 3(已完成) 不可编辑
  return selectedSample.value.status === 0 || selectedSample.value.status === 2
})

const sampleList = ref<TechnicianSample[]>([])
const selectedSample = ref<TechnicianSample | null>(null)
const patientDetail = ref<TechnicianPatientsInfo | null>(null)
const results = ref<TechnicianResult[]>([])
const sampleImages = ref<TechnicianSampleImage[]>([])

const summary = reactive({ total: 0, pending: 0, urgent: 0, audited: 0 })

/** 本地日期字符串(YYYY-MM-DD)。必须用本地时间而非 UTC, 否则中国时区凌晨 00:00-07:59 会取到前一天导致默认核收日期错误 */
function getLocalTodayStr() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
const todayStr = getLocalTodayStr()

const searchForm = reactive({
  keyword: '',
  visitType: undefined as string | undefined,
  status: undefined as number | undefined,
  date: todayStr
})

const auditVisible = ref(false)
const auditForm = reactive({ pass: true, reason: '' })

const printVisible = ref(false)
const reportNo = ref('')
const detailVisible = ref(false)
const detailRow = ref<TechnicianResult | null>(null)
const trendDialogVisible = ref(false)
const trendDialogPatientId = ref<number | null>(null)
const trendDialogTestItemId = ref<number | null>(null)
const trendDialogTestItemName = ref('')
const trendDialogUnit = ref('')

const statCounts = computed(() => {
  const counts: Record<number, number> = { 0: 0, 1: 0, 2: 0, 3: 0, 99: 0 }
  sampleList.value.forEach(s => {
    if (counts[s.status] !== undefined) counts[s.status]++
  })
  return counts
})

// ========== 快速筛选：按状态 + 关键字（filterText / leftStatusFilter / maxRenderedCards 已在上方变量区声明）==========

/**
 * 标本号比较器（升序）：用户要求「从最小顺序号到最大顺序号」
 *   - 无标本号的（含 isUnmatched 无主标本）统一放末尾
 *   - 优先按「前缀字母」字典序（如 S < X），再按「数字段」数值升序（如 416 < 418），最后按尾缀
 *   - 匹配不到数字段时，退化为完整字符串字典序，避免 NaN 造成的顺序不稳
 */
function compareSampleNo(a: TechnicianSample, b: TechnicianSample): number {
  const aNo = a.sampleNo
  const bNo = b.sampleNo
  if (!aNo && !bNo) return 0
  if (!aNo) return 1
  if (!bNo) return -1

  const mA = aNo.match(/^(\D*)(\d+)(.*)$/)
  const mB = bNo.match(/^(\D*)(\d+)(.*)$/)
  if (mA && mB) {
    const [, pA, nA, sA] = mA
    const [, pB, nB, sB] = mB
    if (pA !== pB) return pA.localeCompare(pB, 'zh-CN')
    const numA = Number(nA)
    const numB = Number(nB)
    if (numA !== numB) return numA - numB
    return sA.localeCompare(sB, 'zh-CN')
  }
  return aNo.localeCompare(bNo, 'zh-CN')
}

const filteredSamples = computed(() => {
  let list = sampleList.value
  if (leftStatusFilter.value !== -1) {
    list = list.filter(s => s.status === leftStatusFilter.value)
  }
  if (filterText.value) {
    const text = filterText.value.toLowerCase()
    list = list.filter(s =>
      s.patientName?.toLowerCase().includes(text) ||
      // 快速筛选支持：标本号 / 条码号 / 就诊号 任一命中
      s.sampleNo?.toLowerCase().includes(text) ||
      s.sampleBarcode?.toLowerCase().includes(text) ||
      s.visitNo?.toLowerCase().includes(text) ||
      // 无主标本：支持按样本号/仪器名搜索
      (s.isUnmatched && (
        s.unmatchedSampleNo?.toLowerCase().includes(text) ||
        s.unmatchedInstrumentName?.toLowerCase().includes(text) ||
        s.testItems?.toLowerCase().includes(text)
      ))
    )
  }
  // 按标本号升序排序（先拷贝、再 sort，避免污染原数组导致 Vue 不必要的重算）
  list = list.slice().sort(compareSampleNo)
  // 渲染上限截断：当数据量超过显示能力时截断，减少一次性 DOM 成本（首次加载最关键）
  if (list.length > maxRenderedCards.value) {
    return list.slice(0, maxRenderedCards.value)
  }
  return list
})

const currentPanel = computed(() => {
  if (!results.value.length) return ''
  // 若有选中的检验项目, 显示选中项; 否则显示全部检验项目数
  if (selectedPanel.value) return selectedPanel.value
  if (panelList.value.length > 1) return `共 ${panelList.value.length} 个检验项目`
  return panelList.value[0] || ''
})

const panelList = computed(() => {
  const panels = new Set<string>()
  results.value.forEach(r => {
    if (r.panelName) panels.add(r.panelName)
  })
  return Array.from(panels)
})

/** 按检验项目分组的结果 (用于显示分组标题) */
const groupedResults = computed(() => {
  const groups: { panelName: string; items: TechnicianResult[] }[] = []
  const panelOrder = panelList.value
  for (const panel of panelOrder) {
    const items = displayResults.value.filter(r => r.panelName === panel)
    if (items.length > 0) {
      groups.push({ panelName: panel, items })
    }
  }
  // 无 panelName 的归到"其他"
  const noPanel = displayResults.value.filter(r => !r.panelName)
  if (noPanel.length > 0) {
    groups.push({ panelName: '其他指标', items: noPanel })
  }
  return groups
})

const displayResults = computed(() => {
  let list = results.value
  if (selectedPanel.value) {
    list = list.filter(r => r.panelName === selectedPanel.value)
  }
  return list
})

/** 双列显示: 将结果拆分为左右两列 */
const leftResults = computed(() => {
  const list = displayResults.value
  const mid = Math.ceil(list.length / 2)
  return list.slice(0, mid)
})
const rightResults = computed(() => {
  const list = displayResults.value
  const mid = Math.ceil(list.length / 2)
  return list.slice(mid)
})

/**
 * 双列显示: 按检验指标(而非分组)将结果列表平分为左右两列。
 * 若开启分组, 列内再按检验项目连续分组, 仅作为组标题展示, 不影响按指标拆分的双列排列。
 */
function groupIndicators(list: TechnicianResult[]) {
  const groups: { panelName: string; items: TechnicianResult[] }[] = []
  for (const item of list) {
    const panel = item.panelName || '其他指标'
    const last = groups[groups.length - 1]
    if (last && last.panelName === panel) {
      last.items.push(item)
    } else {
      groups.push({ panelName: panel, items: [item] })
    }
  }
  return groups
}

// ========== 标本图像查看 ==========
const imageViewerVisible = ref(false)
const imageViewerIndex = ref(0)
function viewSampleImage(index: number) {
  imageViewerIndex.value = index
  imageViewerVisible.value = true
}

/** 员工手写签名图片缓存: 姓名 -> signatureImage */
const staffSignatureMap = ref<Record<string, string>>({})

/** 加载在职员工签名图片, 用于报告检验人/审核人/报告人签名匹配 */
async function loadStaffSignatures() {
  try {
    const staffList = await getAllActiveStaff()
    const map: Record<string, string> = {}
    for (const s of staffList) {
      if (s.name && s.signatureImage) map[s.name] = s.signatureImage
    }
    staffSignatureMap.value = map
  } catch {
    // HR 服务不可用时忽略, 签名显示为空白
  }
}

/* ============ 方案 C: 布局计算属性 ============ */

/** 概要信息瓦片: 有值才渲染，避免 "-" 占位
 *  去重复原则:
 *  - 姓名/性别/年龄/就诊类型/科室/急诊 → 已在头卡展示，不再进瓦片
 *  - 标本号/条码号 → 左侧标本列表有，但作为关键ID仍在瓦片展示（方便查看）
 */
const overviewTiles = computed<{ label: string; value: string }[]>(() => {
  const p = patientDetail.value
  if (!p) return []
  const instrumentName = (selectedSample.value && instrumentOptions.value.length > 0)
    ? (instrumentOptions.value.find(i => i.id === (selectedSample.value as any).instrumentId)?.instrumentName || '')
    : ''
  const rows: [string, string | number | undefined][] = [
    ['就诊号', p.visitNo],
    ['标本号', p.sampleNo || p.specimenSerialNo || undefined],
    ['条码号', p.specimenNo || p.sampleBarcode || undefined],
    ['开单医生', p.doctor],
    ['送检科室', p.department],
    ['临床诊断', p.diagnosis],
    ['申请时间', formatTime(p.applyTime)],
    ['标本类型', p.sampleType],
    ['核收仪器', instrumentName]
  ]
  return rows
    .filter(([, v]) => {
      if (v === undefined || v === null || v === '') return false
      if (typeof v === 'string' && v.trim() === '' || v === '—' || v === '-') return false
      return true
    })
    .map(([label, value]) => ({ label, value: String(value) }))
})

/** 时间流程条节点: 申请 → 采集 → 核收 → 检验 → 报告
 *  editable: 报告/申请时间通常不在这里手动改，故中间三个可编辑
 *  每个节点同时携带「操作人」字段用于下方显示（让时间轴信息更完整）
 */
type TlKey = 'applyTime' | 'collectTime' | 'receiveTime' | 'testTime' | 'reportTime'
const timelineNodes = computed<{
  key: TlKey
  label: string
  time: string
  operator: string
  operatorLabel: string
  editable: boolean
}[]>(() => {
  const p = patientDetail.value
  if (!p) return []
  return [
    { key: 'applyTime',   label: '申请', time: formatTimeMD(p.applyTime),   operator: p.doctor ?? '',    operatorLabel: '开单医生', editable: false },
    { key: 'collectTime', label: '采集', time: formatTimeMD(p.collectTime), operator: p.collector ?? '',  operatorLabel: '采集人',   editable: true  },
    { key: 'receiveTime', label: '核收', time: formatTimeMD(p.receiveTime), operator: p.receiver ?? '',   operatorLabel: '签收人',   editable: true  },
    { key: 'testTime',    label: '检验', time: formatTimeMD(p.testTime),    operator: p.inspector ?? '',  operatorLabel: '检验人',   editable: true  },
    { key: 'reportTime',  label: '报告', time: formatTimeMD(p.reportTime),  operator: p.auditor ?? '',    operatorLabel: '审核人',   editable: false }
  ]
})

/** 时间流程条: 编辑态下给某个时间节点设置值（规避模板中 TS 断言） */
function setEditFormNode(key: TlKey, value: string) {
  // 申请时间 / 报告时间不可编辑，这里兜底以防误设
  if (key === 'reportTime' || key === 'applyTime') return
  ;(editForm as Record<string, unknown>)[key] = value ?? ''
}

/** 空值判断工具：值为 null / '' / '-' / '—' / undefined 视为空 */
function isEmptyField(v: unknown): boolean {
  if (v === undefined || v === null) return true
  if (typeof v === 'string') {
    const s = v.trim()
    return s === '' || s === '-' || s === '—'
  }
  if (typeof v === 'number') return !isFinite(v)
  return false
}

type GridRow = {
  key: string
  label: string
  value: string
  fullSpan?: boolean
  editable?: boolean
  editor?: any
  editorProps?: Record<string, unknown>
  valueClass?: string
}

/** 患者信息扁平字段列表：CSS Grid 双列显示，空字段在 computed 里直接过滤掉 */
const patientInfoGridRows = computed<GridRow[]>(() => {
  const p = patientDetail.value
  if (!p) return []
  const rows: GridRow[] = []
  const push = (row: GridRow) => {
    if (!row.editable && isEmptyField(row.value)) return
    rows.push(row)
  }

  // — 基础 / 身份标识 —
  push({ key: 'patientNo',      label: '患者编号', value: p.patientNo })
  push({ key: 'visitNo',        label: '就诊号',   value: p.visitNo })
  push({ key: 'outpatientNo',   label: '门诊号',   value: p.outpatientNo })
  push({ key: 'inpatientNo',    label: '住院号',   value: p.inpatientNo })
  push({ key: 'bedNo',          label: '床号',     value: p.bedNo })
  push({
    key: 'sampleNo',
    label: '标本号',
    value: p.sampleNo || p.specimenSerialNo || '',
    editable: true,
    editor: markRaw(ElInput),
    editorProps: {
      modelValue: editForm.sampleNo,
      'onUpdate:modelValue': (v: string) => { editForm.sampleNo = v ?? '' },
      placeholder: '请输入标本号',
      size: 'small'
    }
  })
  push({ key: 'sampleBarcode',  label: '条码号',   value: p.specimenNo || p.sampleBarcode || '' })
  push({ key: 'idCard',         label: '身份证',   value: p.idCard })
  push({ key: 'phone',          label: '联系电话', value: p.phone })

  // — 申请信息 —
  push({ key: 'applyDepartment',label: '申请科室', value: p.applyDepartment || p.department || '' })
  push({ key: 'doctor',         label: '送检医生', value: p.doctor })
  push({ key: 'applyTime',      label: '申请时间', value: formatTime(p.applyTime) })
  push({ key: 'orderNo',        label: '申请单号', value: p.orderNo })

  // — 标本信息 —
  push({ key: 'sampleType',     label: '标本类型', value: p.sampleType })
  push({ key: 'container',      label: '容器',     value: p.container })
  push({ key: 'collectSite',    label: '采集部位', value: p.collectSite })
  push({ key: 'sampleVolume',   label: '样本量',   value: p.sampleVolume })
  push({ key: 'collector',      label: '采集人',   value: p.collector })
  push({ key: 'deliverPerson',  label: '送检人',   value: p.deliverPerson })
  push({
    key: 'receiver',
    label: '核收人',
    value: p.receiver || '',
    editable: true,
    editor: markRaw(ElSelect),
    editorProps: {
      modelValue: editForm.receiverId,
      'onUpdate:modelValue': (v: number | undefined) => { editForm.receiverId = v },
      filterable: true,
      placeholder: '请选择核收人',
      size: 'small',
      clearable: true
    },
    editorOptions: staffOptions.value.map(st => ({ label: st.name, value: st.id, key: 'el-option' }))
  })
  {
    const instrumentName = (selectedSample.value && instrumentOptions.value.length > 0)
      ? (instrumentOptions.value.find(i => i.id === (selectedSample.value as any).instrumentId)?.instrumentName || '')
      : (p.receiveInstrumentName || '')
    if (!isEmptyField(instrumentName)) push({ key: 'instrument', label: '核收仪器', value: instrumentName })
  }

  // — 人员记录 —
  push({ key: 'inspector',      label: '检验人',   value: p.inspector })
  push({ key: 'reporter',       label: '报告人',   value: p.reporter })
  push({ key: 'auditor',        label: '审核人',   value: p.auditor })

  // — 费用（纯展示，支付/医保也并排放）—
  push({ key: 'paymentMethod',  label: '支付方式', value: p.paymentMethod })
  push({ key: 'insuranceType',  label: '医保类型', value: p.insuranceType })

  // — 长文本：诊断、项目、备注，占满两列 —
  push({ key: 'diagnosis',      label: '临床诊断', value: p.diagnosis,      fullSpan: true })
  push({ key: 'testItems',      label: '检验项目', value: p.testItems,      fullSpan: true })
  push({ key: 'address',        label: '住址',     value: p.address,        fullSpan: true })
  push({ key: 'clinicalNotes',  label: '备注',     value: p.clinicalNotes,  fullSpan: true })

  return rows
})

type FeeCard = {
  label: string
  value: string
  prefix?: string
  valueClass?: string
}
const feeCards = computed<FeeCard[]>(() => {
  const p = patientDetail.value
  if (!p) return []
  const list: FeeCard[] = []
  const fr = Number(p.feeReceivable)
  const fa = Number(p.feeActual)
  if (!Number.isNaN(fr) && Number.isFinite(fr)) {
    list.push({ label: '应收费用', value: fr.toFixed(2), prefix: '¥' })
  }
  if (!Number.isNaN(fa) && Number.isFinite(fa)) {
    list.push({ label: '实收费用', value: fa.toFixed(2), prefix: '¥' })
  }
  if (!Number.isNaN(fr) && !Number.isNaN(fa) && Number.isFinite(fr) && Number.isFinite(fa)) {
    const diff = fr - fa
    list.push({
      label: '差额',
      value: diff.toFixed(2),
      prefix: '¥',
      valueClass: diff > 0 ? 'fee-card-compact__value--red' : 'fee-card-compact__value--green'
    })
  }
  return list
})

/* ============ 标本信息编辑 (标本号/核收时间/检验时间/采集时间/核收人) ============ */
const editingPatientInfo = ref(false)
const savingPatientInfo = ref(false)
const staffOptions = ref<{ id: number; name: string }[]>([])
const editForm = reactive({
  sampleNo: '',
  collectTime: '',
  receiveTime: '',
  testTime: '',
  receiverId: undefined as number | undefined
})
// 方案B患者信息 3 Tab 激活索引: 'overview' | 'applySample' | 'personFee'
const activePatientTab = ref<'overview' | 'applySample' | 'personFee'>('overview')

/** 加载在职员工列表, 供核收人下拉选择 */
async function loadStaffOptions() {
  try {
    const staffList = await getAllActiveStaff()
    staffOptions.value = staffList.map(s => ({ id: s.id, name: s.name }))
  } catch {
    staffOptions.value = []
  }
}

/** 开始编辑标本信息: 将当前患者信息填充到编辑表单 */
function startEditPatientInfo() {
  const p = patientDetail.value
  if (!p) return
  editForm.sampleNo = p.sampleNo || p.specimenSerialNo || ''
  editForm.collectTime = p.collectTime || ''
  editForm.receiveTime = p.receiveTime || ''
  editForm.testTime = p.testTime || ''
  // 核收人: 通过姓名在员工列表中反查其 id
  const matched = p.receiver ? staffOptions.value.find(s => s.name === p.receiver) : undefined
  editForm.receiverId = matched?.id
  editingPatientInfo.value = true
}

function cancelEditPatientInfo() {
  editingPatientInfo.value = false
}

/** 保存标本信息: 调用后端 updateSampleInfo (仅核收未审核可保存) */
async function savePatientInfo() {
  if (!selectedSample.value) return
  const receiver = editForm.receiverId !== undefined
    ? (staffOptions.value.find(s => s.id === editForm.receiverId)?.name || '')
    : ''
  savingPatientInfo.value = true
  try {
    const res = await updateTechnicianSampleInfo(selectedSample.value.id, {
      sampleNo: editForm.sampleNo?.trim() || undefined,
      collectTime: editForm.collectTime || undefined,
      receiveTime: editForm.receiveTime || undefined,
      testTime: editForm.testTime || undefined,
      receiver: receiver || undefined,
      receiverId: editForm.receiverId
    })
    if (!res || !res.success || res.success === 0) {
      const msg = (res as any)?.messages?.[0] || '标本不存在或非核收未审核状态, 无法修改'
      ElMessage.error(msg)
      return
    }
    ElMessage.success('标本信息已保存')
    editingPatientInfo.value = false
    // 同步更新选中标本与患者信息显示
    if (selectedSample.value) {
      selectedSample.value.sampleNo = editForm.sampleNo?.trim() ?? selectedSample.value.sampleNo
      selectedSample.value.receiveTime = editForm.receiveTime || selectedSample.value.receiveTime
      selectedSample.value.testTime = editForm.testTime || selectedSample.value.testTime
    }
    if (patientDetail.value) {
      patientDetail.value.sampleNo = editForm.sampleNo?.trim() ?? patientDetail.value.sampleNo
      patientDetail.value.receiveTime = editForm.receiveTime || patientDetail.value.receiveTime
      patientDetail.value.testTime = editForm.testTime || patientDetail.value.testTime
      patientDetail.value.receiver = receiver || patientDetail.value.receiver
    }
    loadSamples()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e?.message || '未知错误'))
  } finally {
    savingPatientInfo.value = false
  }
}

/** 报告预览数据 (整合患者信息 + 检验结果 + 签名图片) */
const printData = computed(() => {
  const p = patientDetail.value || {} as any
  const sig = (name?: string) => ({ signatureImage: name ? (staffSignatureMap.value[name] || '') : '' })
  return {
    ...p,
    patientName: p.patientName,
    age: p.age,
    visitNo: p.visitNo,
    department: p.department,
    doctor: p.doctor,
    clinicalDiag: p.diagnosis,
    sampleBarcode: p.specimenNo || p.sampleNo,
    sampleType: p.sampleType,
    collectTime: p.collectTime,
    receiveTime: p.receiveTime,
    testTime: p.testTime,
    reportTime: p.reportTime,
    checker: p.inspector,
    auditor: p.publisher || p.reporter,
    reportDoctor: p.reporter,
    collector: p.collector,
    receiver: p.receiver,
    results: results.value.map((r: any) => ({
      ...r,
      abnormalFlag: r.abnormalFlag || '',
      isCritical: r.isCritical === true || r.isCritical === 1 ? 1 : 0
    })),
    sampleImages: sampleImages.value.map((img: any) => ({
      imageUrl: img.imageUrl || img.url,
      imageName: img.imageName || img.title || ''
    })),
    signatures: {
      checker: sig(p.inspector),
      auditor: sig(p.publisher || p.reporter),
      reportDoctor: sig(p.reporter),
      collector: sig(p.collector),
      receiver: sig(p.receiver)
    }
  }
})

/** 加载报告模板列表 */
async function loadTemplateList() {
  templateList.value = await getTemplateList()
}

/** 打印模板选择变更 */
async function onPrintTemplateChange(id: number | null) {
  if (!id) return
  const t = templateList.value.find(x => x.templateId === id)
  if (t) {
    reportTemplate.value = t
    printDualColumn.value = t.dualColumn !== false
  }
}

/** 根据当前标本的仪器加载对应模板 */
async function loadTemplateForInstrument(instrumentId?: number) {
  const t = await getTemplateByInstrument(instrumentId)
  if (t) {
    reportTemplate.value = t
    selectedTemplateId.value = t.templateId || null
    printDualColumn.value = t.dualColumn !== false
  } else {
    printDualColumn.value = true // 默认双列
  }
}

/** 获取异常箭头标示: ↑偏高 ↓偏低 */
function getArrowFlag(row: TechnicianResult): string {
  if (row.isCritical) return '↑↓'
  if (row.abnormalFlag === 'H' || row.abnormalFlag === '高') return '↑'
  if (row.abnormalFlag === 'L' || row.abnormalFlag === '低') return '↓'
  // 通过参考范围自动判断
  if (row.resultValue && row.refRange) {
    const val = parseFloat(row.resultValue)
    if (!isNaN(val)) {
      // 解析 "3.5-5.5" 格式
      const m = String(row.refRange).match(/([\d.]+)\s*[-~]\s*([\d.]+)/)
      if (m) {
        const low = parseFloat(m[1])
        const high = parseFloat(m[2])
        if (val > high) return '↑'
        if (val < low) return '↓'
      }
    }
  }
  return ''
}

const normalCount = computed(() => displayResults.value.filter(r => !r.abnormalFlag && !r.isCritical).length)
const abnormalCount = computed(() => displayResults.value.filter(r => r.abnormalFlag === 'H' || r.abnormalFlag === 'L').length)
const criticalCount = computed(() => displayResults.value.filter(r => r.isCritical).length)

// ========== 危急值内嵌处理 (就地确认, 不跳转) ==========
const criticalResults = computed(() => displayResults.value.filter(r => r.isCritical))
/** 已确认的危急值 resultId 集合 */
const confirmedCriticalIds = ref<Set<number | string>>(new Set())
/** 确认时间记录 */
const criticalConfirmTimes = ref<Map<number | string, string>>(new Map())
const confirmingCriticalId = ref<number | string | null>(null)
const unconfirmedCriticalResults = computed(() => criticalResults.value.filter(r => !confirmedCriticalIds.value.has(String(r.id))))
function isCriticalConfirmed(row: TechnicianResult) { return confirmedCriticalIds.value.has(String(row.id)) }
function criticalConfirmTime(row: TechnicianResult) { return criticalConfirmTimes.value.get(String(row.id)) || '' }
/** 确认单个危急值 */
async function confirmCriticalValue(row: TechnicianResult) {
  if (row.id == null) return
  confirmingCriticalId.value = row.id
  try {
    await confirmCritical(row.id)
    confirmedCriticalIds.value.add(String(row.id))
    criticalConfirmTimes.value.set(String(row.id), new Date().toLocaleString('zh-CN', { hour12: false }))
    ElMessage.success(`已确认危急值: ${row.testItemName} ${row.resultValue}`)
  } catch (e: any) {
    // 失败回退本地确认, 不阻断工作流
    confirmedCriticalIds.value.add(String(row.id))
    criticalConfirmTimes.value.set(String(row.id), new Date().toLocaleString('zh-CN', { hour12: false }))
    ElMessage.warning('危急值确认提交失败, 已本地记录')
  } finally {
    confirmingCriticalId.value = null
  }
}
/** 一键确认全部未确认危急值 */
async function confirmAllCritical() {
  const unconfirmed = unconfirmedCriticalResults.value
  if (unconfirmed.length === 0) return
  for (const cr of unconfirmed) {
    if (cr.id == null) continue
    try { await confirmCritical(cr.id) } catch { /* 单条失败不阻断 */ }
    confirmedCriticalIds.value.add(String(cr.id))
    criticalConfirmTimes.value.set(String(cr.id), new Date().toLocaleString('zh-CN', { hour12: false }))
  }
  ElMessage.success(`已确认 ${unconfirmed.length} 个危急值`)
}

const currentDateTime = computed(() => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

function onQuickFilter(status: number) {
  leftStatusFilter.value = status
}

function loadSamples() {
  loading.value = true
  const params: TechnicianSampleQuery = {
    current: 1,
    size: 50000 // 不做条数限制: 当天有多少标本就显示多少(单日 ≤1000 条属正常, 后端最终软上限 50000 条保障前端安全)
  }
  if (searchForm.keyword) params.keyword = searchForm.keyword
  if (searchForm.visitType) params.visitType = searchForm.visitType
  if (searchForm.status !== undefined) params.status = searchForm.status
  if (searchForm.date) {
    params.startDate = searchForm.date
    params.endDate = searchForm.date
  }
  if (receiveInstrumentId.value !== undefined) params.instrumentId = receiveInstrumentId.value

  const t0 = performance.now()
  getTechnicianSamples(params).then(res => {
    sampleList.value = res.records || []
    const t1 = performance.now()
    console.log(`[性能] 标本列表加载完成: ${sampleList.value.length} 条数据, 接口耗时 ${(t1 - t0).toFixed(1)}ms`)
    // 下一帧计算 DOM 渲染耗时
    requestAnimationFrame(() => {
      const t2 = performance.now()
      const cardList = document.querySelector('.sample-card-list')
      const cardCount = cardList ? cardList.querySelectorAll('.sample-card').length : 0
      console.log(`[性能] DOM 渲染完成: ${cardCount} 个卡片, 渲染耗时 ${(t2 - t1).toFixed(1)}ms`)
    })

    // 加载完后：若还没有选中的详情行，自动选中第一行（保证患者信息/检验结果区不为空）
    if (sampleList.value.length > 0 && !selectedSample.value) {
      handleSampleSelect(sampleList.value[0])
    } else if (sampleList.value.length === 0) {
      // 空列表：清空详情区
      selectedSample.value = null
      patientDetail.value = null
      results.value = []
      sampleImages.value = []
    }
  }).catch(() => {
    sampleList.value = []
  }).finally(() => {
    loading.value = false
  })
}

/** 仪器/日期变化时自动刷新列表与统计 */
function reload() {
  loadSamples()
  loadSummary()
}

/** 核收入口: 按所选标识类型分发 (条码直接核收, 其他标识查询可核收标本列表) */
async function handleReceive() {
  if (receiveType.value === 'barcode') {
    await receiveByBarcode()
  } else {
    await lookupAndSelect()
  }
}

/** 按就诊/门诊/住院标识查询可核收标本, 多个则弹出选择框, 单个直接核收, 无则提示 */
async function lookupAndSelect() {
  const keyword = receiveBarcode.value.trim()
  if (!keyword) {
    ElMessage.warning(`请输入${receiveTypeLabel.value}`)
    return
  }
  if (!receiveInstrumentId.value) {
    ElMessage.warning('请先选择核收仪器')
    return
  }
  if (lookupLoading.value) return
  lookupLoading.value = true
  try {
    const list = await lookupReceivableSamples(receiveType.value, keyword)
    if (!list || list.length === 0) {
      ElMessage.warning(`未查询到${receiveTypeLabel.value}为「${keyword}」的可核收标本`)
      receiveBarcode.value = ''
      return
    }
    lookupKeyword.value = keyword
    lookupList.value = list
    lookupSelections.value = []
    if (list.length === 1) {
      // 仅一个标本, 直接核收
      await receiveByLookupSelect(list[0])
    } else {
      // 多个标本, 打开选择弹窗
      lookupDialogVisible.value = true
    }
  } catch (err: any) {
    ElMessage.error(err?.message || err?.msg || '查询可核收标本失败')
  } finally {
    lookupLoading.value = false
  }
}

/** 核收单个标本(核收成功后加入列表并选中) */
async function receiveByLookupSelect(target: ReceivableSample) {
  const barcode = String(target.sampleBarcode)
  // 1. 已在列表中则直接选中
  const existIdx = sampleList.value.findIndex(s => s.sampleBarcode === barcode)
  if (existIdx >= 0) {
    const exist = sampleList.value[existIdx]
    lookupDialogVisible.value = false
    receiveBarcode.value = ''
    ElMessage.info(`标本 ${barcode} 已在列表中`)
    handleSampleSelect(exist)
    return
  }
  // 2. 调用核收接口
  let sample: any
  try {
    sample = await receiveSample({
      sampleBarcode: barcode,
      instrumentId: receiveInstrumentId.value
    })
  } catch (err: any) {
    const msg = err?.message || err?.msg || '核收失败'
    ElMessage.error(`${barcode} 核收失败: ${msg}`)
    return
  }
  // 3. 转换为技师工作站列表项
  const instrName = instrumentOptions.value.find(i => i.id === sample.instrumentId)?.instrumentName
    || sample.instrumentName || target.instrumentName || ''
  let testItemsText = '检验项目'
  if (sample.orderItems && sample.orderItems.length > 0) {
    testItemsText = sample.orderItems.map((oi: any) => oi.testItemName).join('、')
  } else if (target.testItems) {
    testItemsText = target.testItems
  } else if (sample.sampleType) {
    testItemsText = sample.sampleType
  }
  const techSample: TechnicianSample = {
    id: sample.id,
    sampleBarcode: sample.sampleBarcode,
    sampleNo: sample.sampleNo || '',
    patientName: sample.patientName || '未知',
    patientGender: sample.patientGender || '',
    patientAge: sample.patientAge || '',
    visitNo: sample.visitNo || '',
    visitType: sample.visitType || '门诊',
    testItems: testItemsText,
    department: sample.department || '',
    doctor: sample.doctor || '',
    collectTime: sample.collectTime,
    receiveTime: sample.receiveTime,
    status: 0,
    isUrgent: sample.isUrgent || 0,
    abnormalCount: 0,
    criticalCount: 0
  }
  sampleList.value.unshift(techSample)
  receiveBarcode.value = ''
  const sampleNoText = sample.sampleNo ? `，标本号: ${sample.sampleNo}` : ''
  const instrText = instrName ? `，仪器: ${instrName}` : ''
  ElMessage.success(`标本 ${barcode} 核收成功${sampleNoText}${instrText}，已加载检验指标`)
  await handleSampleSelect(techSample)
}

/** 弹窗中批量核收选中的标本 */
async function receiveLookupSelections() {
  if (lookupSelections.value.length === 0) {
    ElMessage.warning('请先勾选要核收的标本')
    return
  }
  if (receiving.value) return
  receiving.value = true
  let successCount = 0
  let failCount = 0
  const failMsgs: string[] = []
  for (const target of lookupSelections.value) {
    try {
      const barcode = String(target.sampleBarcode)
      const existIdx = sampleList.value.findIndex(s => s.sampleBarcode === barcode)
      if (existIdx >= 0) {
        successCount++
        continue
      }
      const sample = await receiveSample({
        sampleBarcode: barcode,
        instrumentId: receiveInstrumentId.value
      })
      const instrName = instrumentOptions.value.find(i => i.id === sample.instrumentId)?.instrumentName
        || sample.instrumentName || target.instrumentName || ''
      let testItemsText = '检验项目'
      if (sample.orderItems && sample.orderItems.length > 0) {
        testItemsText = sample.orderItems.map((oi: any) => oi.testItemName).join('、')
      } else if (target.testItems) {
        testItemsText = target.testItems
      } else if (sample.sampleType) {
        testItemsText = sample.sampleType
      }
      const techSample: TechnicianSample = {
        id: sample.id,
        sampleBarcode: sample.sampleBarcode,
        sampleNo: sample.sampleNo || '',
        patientName: sample.patientName || '未知',
        patientGender: sample.patientGender || '',
        patientAge: sample.patientAge || '',
        visitNo: sample.visitNo || '',
        visitType: sample.visitType || '门诊',
        testItems: testItemsText,
        department: sample.department || '',
        doctor: sample.doctor || '',
        collectTime: sample.collectTime,
        receiveTime: sample.receiveTime,
        status: 0,
        isUrgent: sample.isUrgent || 0,
        abnormalCount: 0,
        criticalCount: 0
      }
      sampleList.value.unshift(techSample)
      successCount++
    } catch (err: any) {
      failCount++
      failMsgs.push(`${target.sampleBarcode}: ${err?.message || err?.msg || '核收失败'}`)
    }
  }
  receiving.value = false
  if (failCount === 0) {
    ElMessage.success(`成功核收 ${successCount} 份标本`)
    lookupDialogVisible.value = false
    receiveBarcode.value = ''
  } else {
    ElMessage.warning(`成功 ${successCount} 份，失败 ${failCount} 份`)
    if (failMsgs.length > 0) {
      console.warn('批量核收失败明细:', failMsgs)
    }
  }
}

/** 通过条码核收标本: 选择仪器 -> 仪器能力预检 -> 核收(生成标本号) -> 加入列表并加载检验指标 */
async function receiveByBarcode() {
  const barcode = receiveBarcode.value.trim()
  if (!barcode) {
    ElMessage.warning('请输入或扫描条码号')
    return
  }
  if (!receiveInstrumentId.value) {
    ElMessage.warning('请先选择核收仪器')
    return
  }
  if (receiving.value) return
  receiving.value = true

  // 1. 检查列表中是否已存在该条码
  const existIdx = sampleList.value.findIndex(s => s.sampleBarcode === barcode)
  if (existIdx >= 0) {
    // 已在列表中，直接选中并加载结果
    const exist = sampleList.value[existIdx]
    ElMessage.info(`标本 ${barcode} 已在列表中`)
    receiveBarcode.value = ''
    receiving.value = false
    handleSampleSelect(exist)
    return
  }

  try {
    // 2. 根据条码查询标本
    let sample: any
    try {
      sample = await getSampleByBarcode(barcode)
    } catch {
      ElMessage.error(`未查询到条码 ${barcode} 对应的标本`)
      receiveBarcode.value = ''
      return
    }

    // 3. 已核收的条码不可重复核收 (status >= 4 已核收/检测中/已报告)
    if (sample.status !== undefined && sample.status >= 4) {
      const instrName = instrumentOptions.value.find(i => i.id === sample.instrumentId)?.instrumentName
        || sample.instrumentName || ''
      ElMessage.error(
        `标本 ${barcode} 已核收，不可再次核收${instrName ? `（当前核收仪器: ${instrName}）` : ''}`
      )
      receiveBarcode.value = ''
      return
    }

    // 4. 如尚未核收, 调用核收接口(传仪器ID, 系统校验仪器能力并生成标本号)
    if (sample.status === undefined || sample.status < 4) {
      // 4.1 仪器能力预检: 返回不可检测的项目名称列表
      let unsupportedItems: string[] = []
      try {
        unsupportedItems = await precheckReceive(barcode, receiveInstrumentId.value)
      } catch (err: any) {
        // 预检失败(可能是未配置项目映射), 不阻断, 交由核收接口处理
        console.warn('预检接口调用失败:', err)
      }
      if (unsupportedItems.length > 0) {
        const instrName = instrumentOptions.value.find(i => i.id === receiveInstrumentId.value)?.instrumentName || ''
        ElMessageBox.alert(
          `仪器 [${instrName}] 不支持检测以下检验项目: \n${unsupportedItems.join('、')}\n\n请选择其他仪器或在仪器配置中添加对应项目映射。`,
          '仪器能力校验未通过',
          { confirmButtonText: '我知道了', type: 'error' }
        )
        receiveBarcode.value = ''
        return
      }
      // 4.2 调用核收接口
      try {
        const received = await receiveSample({
          sampleBarcode: barcode,
          instrumentId: receiveInstrumentId.value
        })
        // 用核收返回的完整信息覆盖原 sample
        Object.assign(sample, received)
        sample.status = 4
      } catch (err: any) {
        // 核收接口失败, 显示错误并终止
        const msg = err?.message || err?.msg || '核收失败'
        ElMessage.error(msg)
        receiveBarcode.value = ''
        return
      }
    }

    // 5. 转换为技师工作站列表项并加入列表头部
    const instrName = instrumentOptions.value.find(i => i.id === sample.instrumentId)?.instrumentName
      || sample.instrumentName || ''
    // 拼接检验项目名称 (一个条码可能有多个检验项目)
    let testItemsText = '检验项目'
    if (sample.orderItems && sample.orderItems.length > 0) {
      testItemsText = sample.orderItems.map((oi: any) => oi.testItemName).join('、')
    } else if (sample.sampleType) {
      testItemsText = sample.sampleType
    }
    const techSample: TechnicianSample = {
      id: sample.id,
      sampleBarcode: sample.sampleBarcode,
      sampleNo: sample.sampleNo || '',
      patientName: sample.patientName || '未知',
      patientGender: sample.patientGender || '',
      patientAge: sample.patientAge || '',
      visitNo: sample.visitNo || '',
      visitType: sample.visitType || '门诊',
      testItems: testItemsText,
      department: sample.department || '',
      doctor: sample.doctor || '',
      collectTime: sample.collectTime,
      receiveTime: sample.receiveTime,
      status: 0,
      isUrgent: sample.isUrgent || 0,
      abnormalCount: 0,
      criticalCount: 0
    }
    sampleList.value.unshift(techSample)

    // 5. 选中新核收的标本并加载其检验指标
    receiveBarcode.value = ''
    const sampleNoText = sample.sampleNo ? `，标本号: ${sample.sampleNo}` : ''
    const instrText = instrName ? `，仪器: ${instrName}` : ''
    ElMessage.success(`标本 ${barcode} 核收成功${sampleNoText}${instrText}，已加载检验指标`)
    await handleSampleSelect(techSample)
  } finally {
    receiving.value = false
  }
}

function generateMockSamples() {
  const list: TechnicianSample[] = []
  const visitTypes = ['门诊', '住院', '体检']
  const depts = ['检验科', '体检中心', '急诊科', '内科门诊', '外科门诊']
  const panels = ['血常规(五分类)', '生化全套', '尿常规+尿沉渣', '肝功能八项', '肾功能三项', '血脂四项']
  const doctors = ['张医生', '李医生', '王医生', '赵医生', '陈医生']
  for (let i = 0; i < 15; i++) {
    const statusVal = i < 5 ? 0 : i < 9 ? 1 : i < 12 ? 3 : 2
    list.push({
      id: i + 1,
      sampleBarcode: `260729000${80 + i}`,
      sampleNo: `S20260729${String(1000 + i).padStart(5, '0')}`,
      patientName: ['邱永霞', '练佳濠', '卓静足', '黄健', '何意玲', '刘华', '陈明'][i % 7],
      patientGender: i % 2 === 0 ? '女' : '男',
      patientAge: String(20 + (i % 60)),
      visitNo: `MZ20260729${String(100 + i).padStart(5, '0')}`,
      visitType: visitTypes[i % 3],
      testItems: panels[i % panels.length],
      department: depts[i % depts.length],
      doctor: doctors[i % doctors.length],
      collectTime: '2026-07-29 08:30:00',
      receiveTime: '2026-07-29 09:00:00',
      testTime: statusVal >= 1 ? '2026-07-29 09:30:00' : undefined,
      reportTime: statusVal >= 3 ? '2026-07-29 10:15:00' : undefined,
      status: statusVal,
      isUrgent: i % 4 === 0 ? 1 : 0,
      abnormalCount: i % 5 === 0 ? 1 : 0,
      criticalCount: i % 7 === 0 ? 1 : 0
    })
  }
  return list
}

function resetSearch() {
  searchForm.keyword = ''
  searchForm.visitType = undefined
  searchForm.status = undefined
  searchForm.date = todayStr
  loadSamples()
}

// 行点击事件: 补充 current-change, 确保点击行时一定加载详情
function onRowClick(row: TechnicianSample) {
  if (!row) return
  // 若已选中同一行, current-change 不会重复触发, 这里手动调用
  if (selectedSample.value && selectedSample.value.id === row.id) return
  handleSampleSelect(row)
}

async function handleSampleSelect(row: TechnicianSample | null) {
  if (!row) return
  selectedSample.value = row
  results.value = []
  sampleImages.value = []
  conclusion.value = ''
  selectedPanel.value = ''
  resultLoading.value = true
  showAllPanels.value = false

  // ===== 无主标本：不走业务标本详情接口，按卡片信息合成一个概览视图 =====
  if (row.isUnmatched) {
    resultLoading.value = false
    reportDetail.value = undefined
    patientDetail.value = {
      patientId: 0 as any,
      patientName: '无主标本（仪器回传未匹配）',
      gender: '-',
      age: '',
      visitType: '',
      department: row.unmatchedInstrumentName || '-',
      doctor: '-',
      sampleNo: row.unmatchedSampleNo || '-',
      sampleBarcode: row.unmatchedSampleNo || '',
      sampleType: '-',
      visitNo: '-',
      diagnosis: row.unmatchedResultSummary
        ? `仪器回传结果：${row.unmatchedResultSummary}`
        : '请在左侧卡片点击「认领」匹配到业务标本，或点击「作废」丢弃'
    } as TechnicianPatientsInfo
    return
  }

  try {
    // 走 axios 实例 + _silent=true：请求拦截器照旧加 token；但响应拦截器在 _silent 模式下
    // 不自动弹出 ElMessage.error，避免 1 条失败连续弹 2 条（全局 + 本地）。
    const res = await service.get<any>(`/report/technician/${row.id}`, {
      params: { _silent: true } as any
    } as any)
    const payload = (res && res.data !== undefined && res.data !== null) ? res.data : res
    const data = (payload && payload.code === 200) ? payload.data : payload
    const detail: {
      patient: TechnicianPatientsInfo
      results?: TechnicianResult[]
      images?: TechnicianSampleImage[]
    } = data
    patientDetail.value = detail.patient
    results.value = detail.results || []
    sampleImages.value = detail.images || []
    if (results.value.length > 0) {
      ElMessage.success(`已加载 ${results.value.length} 项检验指标`)
    } else if (resultEditable.value) {
      ElMessage.warning('该标本暂无检验指标，请确认已关联检验项目')
    } else {
      ElMessage.info('该标本尚未回传检验结果, 请等待通讯程序回传')
    }
  } catch (e: any) {
    const msgFromResp =
      e?.response?.data?.message ||
      e?.data?.message ||
      e?.message ||
      ''
    const msg = String(msgFromResp || (e?.response?.status === 404 ? '标本不存在' : '未知错误'))
    const isNotFound = /标本不存在|已删除|not\s*found|404|不存在/i.test(msg) || (e?.response?.status === 404)
    if (isNotFound) {
      // 前端列表中存在但后端查不到（可能列表数据缓存/历史遗留或接口临时不一致）时：
      // 1. 用卡片本身展示的最小患者信息填充中间患者信息；
      // 2. 结果区给出提示，只弹 1 条 warning；
      // 3. 并自动把这行从 list 里剔除，避免重复点击报错。
      patientDetail.value = generateMockPatient(row)
      patientDetail.value.diagnosis = (row as any).diagnosis || '（详情接口无返回，请稍后查询刷新）'
      patientDetail.value.department = row.department || patientDetail.value.department
      patientDetail.value.doctor = row.doctor || patientDetail.value.doctor
      patientDetail.value.sampleType = (row as any).sampleType || patientDetail.value.sampleType
      patientDetail.value.feeReceivable = undefined as any
      patientDetail.value.feeActual = undefined as any
      patientDetail.value.reporter = ''
      patientDetail.value.publisher = ''
      ElMessage.warning('标本详情暂时不可用，已按列表信息兜底展示，请稍后刷新查询')
      sampleList.value = sampleList.value.filter(s => s.id !== row.id)
      if (selectedSample.value?.id === row.id) selectedSample.value = null
    } else {
      ElMessage.error('加载标本详情失败: ' + msg)
      patientDetail.value = null
      results.value = []
    }
  } finally {
    resultLoading.value = false
  }
}

function generateMockPatient(row: TechnicianSample): TechnicianPatientsInfo {
  return {
    patientId: row.id,
    patientName: row.patientName,
    gender: row.patientGender || '女',
    age: row.patientAge || '32',
    visitNo: row.visitNo || '',
    visitType: row.visitType || '门诊',
    department: row.department || '',
    doctor: row.doctor || '',
    diagnosis: '健康查体',
    idCard: '370***********1234',
    phone: '138****5678',
    address: '山东省济南市',
    sampleType: '静脉血',
    collectTime: row.collectTime,
    receiveTime: row.receiveTime,
    testTime: row.testTime,
    reportTime: row.reportTime,
    specimenNo: row.sampleBarcode,
    sampleNo: row.sampleNo,
    specimenSerialNo: row.sampleNo,
    feeReceivable: 35.50,
    feeActual: 35.50,
    collector: '张采集',
    receiver: '王签收',
    inspector: '',
    reporter: '',
    publisher: '',
    applyTime: '2026-07-29 08:00:00'
  }
}

function generateMockResults(row: TechnicianSample): TechnicianResult[] {
  const panel = row.testItems || '血常规(五分类)'
  const allResults: TechnicianResult[] = []

  // 定义各检验项目下的指标
  const panelDefs: Record<string, { code: string; name: string; unit: string; ref: string; val: string }[]> = {
    '血常规(五分类)': [
      { code: 'WBC', name: '白细胞计数', unit: '10^9/L', ref: '4.0-10.0', val: '6.5' },
      { code: 'RBC', name: '红细胞计数', unit: '10^12/L', ref: '4.0-5.5', val: '4.8' },
      { code: 'HGB', name: '血红蛋白', unit: 'g/L', ref: '120-160', val: '145' },
      { code: 'PLT', name: '血小板计数', unit: '10^9/L', ref: '100-300', val: '220' },
      { code: 'NEUT', name: '中性粒细胞%', unit: '%', ref: '50-70', val: '65' },
      { code: 'LYMPH', name: '淋巴细胞%', unit: '%', ref: '20-40', val: '30' },
    ],
    '生化全套': [
      { code: 'ALT', name: '谷丙转氨酶', unit: 'U/L', ref: '0-40', val: '32' },
      { code: 'AST', name: '谷草转氨酶', unit: 'U/L', ref: '0-40', val: '28' },
      { code: 'TBIL', name: '总胆红素', unit: 'μmol/L', ref: '5-21', val: '12.5' },
      { code: 'CREA', name: '肌酐', unit: 'μmol/L', ref: '44-133', val: '85' },
      { code: 'GLU', name: '葡萄糖', unit: 'mmol/L', ref: '3.9-6.1', val: '5.2' },
      { code: 'BUN', name: '尿素氮', unit: 'mmol/L', ref: '3.0-7.5', val: '4.8' },
    ],
    '尿常规+尿沉渣': [
      { code: 'PRO', name: '尿蛋白', unit: '', ref: '阴性', val: '阴性' },
      { code: 'GLU', name: '尿糖', unit: '', ref: '阴性', val: '阴性' },
      { code: 'KET', name: '酮体', unit: '', ref: '阴性', val: '阴性' },
      { code: 'BIL', name: '胆红素', unit: '', ref: '阴性', val: '阴性' },
      { code: 'SG', name: '尿比重', unit: '', ref: '1.005-1.030', val: '1.020' },
      { code: 'PH', name: '酸碱度', unit: '', ref: '5.0-8.0', val: '6.0' },
    ],
    '肝功能八项': [
      { code: 'ALT', name: '谷丙转氨酶', unit: 'U/L', ref: '0-40', val: '25' },
      { code: 'AST', name: '谷草转氨酶', unit: 'U/L', ref: '0-40', val: '22' },
      { code: 'TBIL', name: '总胆红素', unit: 'μmol/L', ref: '5-21', val: '15' },
      { code: 'DBIL', name: '直接胆红素', unit: 'μmol/L', ref: '0-6', val: '4' },
      { code: 'ALB', name: '白蛋白', unit: 'g/L', ref: '35-55', val: '42' },
      { code: 'TP', name: '总蛋白', unit: 'g/L', ref: '60-80', val: '72' },
    ],
    '肾功能三项': [
      { code: 'CREA', name: '肌酐', unit: 'μmol/L', ref: '44-133', val: '85' },
      { code: 'BUN', name: '尿素氮', unit: 'mmol/L', ref: '3.0-7.5', val: '5.2' },
      { code: 'UA', name: '尿酸', unit: 'μmol/L', ref: '150-420', val: '320' },
    ],
    '血脂四项': [
      { code: 'TC', name: '总胆固醇', unit: 'mmol/L', ref: '<5.2', val: '4.8' },
      { code: 'TG', name: '甘油三酯', unit: 'mmol/L', ref: '<1.7', val: '1.5' },
      { code: 'HDL', name: '高密度脂蛋白', unit: 'mmol/L', ref: '>1.0', val: '1.2' },
      { code: 'LDL', name: '低密度脂蛋白', unit: 'mmol/L', ref: '<3.4', val: '2.8' },
    ],
  }

  // 根据标本决定包含哪些检验项目（部分标本包含多个检验项目）
  const panelsToUse: string[] = []
  if (panel.includes('血常规')) panelsToUse.push('血常规(五分类)')
  if (panel.includes('生化')) {
    panelsToUse.push('生化全套')
    // 生化标本有时也包含血脂
    if (row.id % 3 === 0) panelsToUse.push('血脂四项')
  }
  if (panel.includes('尿常规')) panelsToUse.push('尿常规+尿沉渣')
  if (panel.includes('肝功能')) panelsToUse.push('肝功能八项')
  if (panel.includes('肾功能')) {
    panelsToUse.push('肾功能三项')
    // 肾功能有时也包含尿常规
    if (row.id % 4 === 0) panelsToUse.push('尿常规+尿沉渣')
  }
  if (panel.includes('血脂')) panelsToUse.push('血脂四项')
  if (panelsToUse.length === 0) panelsToUse.push(panel)

  let idCounter = 1
  for (const pnl of panelsToUse) {
    const items = panelDefs[pnl] || panelDefs['血常规(五分类)']
    for (const it of items) {
      const refMatch = it.ref.match(/([\d.]+)\s*[-~到至]\s*([\d.]+)/) || it.ref.match(/<([\d.]+)/)
      let flag = ''
      let isCritical = false
      if (refMatch) {
        const v = parseFloat(it.val)
        if (!isNaN(v)) {
          if (it.ref.startsWith('<')) {
            const max = parseFloat(refMatch[1])
            if (v > max) flag = 'H'
          } else {
            const low = parseFloat(refMatch[1])
            const high = parseFloat(refMatch[2])
            if (v < low * 0.9) { flag = 'L' }
            else if (v > high * 1.1) { flag = 'H' }
          }
        }
      }
      const instr = pnl.includes('生化') || pnl.includes('肝') || pnl.includes('肾') || pnl.includes('血脂') ? '罗氏 cobas 8000' : '迈瑞 BC-7500'
      allResults.push({
        id: idCounter,
        testItemId: idCounter,
        testItemCode: it.code,
        testItemName: it.name,
        itemNameEn: it.code,
        resultValue: it.val,
        unit: it.unit,
        refRange: it.ref,
        abnormalFlag: flag,
        isCritical: isCritical,
        instrumentId: 1,
        instrumentName: instr,
        category: isCritical ? '危急' : flag ? '异常' : '常规',
        panelName: pnl,
        appliedToReport: true
      })
      idCounter++
    }
  }

  return allResults
}

function isAbnormal(row: TechnicianResult) {
  return row.abnormalFlag === 'H' || row.abnormalFlag === 'L'
}

function onResultChange(row: TechnicianResult) {
  if (!row.resultValue || !row.refRange) return
  const value = parseFloat(row.resultValue)
  if (isNaN(value)) return

  const rangeMatch = row.refRange.match(/([\d.]+)\s*[-~到至]\s*([\d.]+)/) || row.refRange.match(/<([\d.]+)/)
  if (rangeMatch) {
    if (row.refRange.startsWith('<')) {
      const max = parseFloat(rangeMatch[1])
      row.abnormalFlag = value > max ? 'H' : ''
    } else {
      const low = parseFloat(rangeMatch[1])
      const high = parseFloat(rangeMatch[2])
      if (value < low) row.abnormalFlag = 'L'
      else if (value > high) row.abnormalFlag = 'H'
      else row.abnormalFlag = ''
    }
  }
}

function panelSpanMethod({ row, columnIndex }: { row: TechnicianResult; columnIndex: number }) {
  if (!showPanelColumn.value || columnIndex !== 0) return {}
  const data = displayResults.value
  const idx = data.indexOf(row)
  if (idx === -1) return {}
  // 第一条同组记录，计算合并行数
  if (idx === 0 || data[idx - 1].panelName !== row.panelName) {
    let span = 1
    for (let i = idx + 1; i < data.length; i++) {
      if (data[i].panelName === row.panelName) span++
      else break
    }
    return { rowspan: span, colspan: 1 }
  }
  // 非首条同组记录，隐藏
  return { rowspan: 0, colspan: 0 }
}

function viewResultDetail(row: TechnicianResult) {
  detailRow.value = row
  detailVisible.value = true
}

/** 打开历史趋势: 展示该患者该项目近N次结果折线图 */
function openTrendDialog(row: TechnicianResult) {
  const patientId = patientDetail.value?.patientId
  if (patientId == null || !row.testItemId) {
    ElMessage.warning('缺少患者或检验指标信息, 无法查看趋势')
    return
  }
  trendDialogPatientId.value = patientId
  trendDialogTestItemId.value = row.testItemId
  trendDialogTestItemName.value = row.testItemName || ''
  trendDialogUnit.value = row.unit || ''
  trendDialogVisible.value = true
}

async function loadResults() {
  if (!selectedSample.value) return
  resultLoading.value = true
  // 切换标本时重置危急值确认状态
  confirmedCriticalIds.value = new Set()
  criticalConfirmTimes.value = new Map()
  try {
    const detail = await getTechnicianDetail(selectedSample.value.id)
    patientDetail.value = detail.patient
    results.value = detail.results || []
    sampleImages.value = detail.images || []
    if (results.value.length > 0) {
      ElMessage.success(`已加载 ${results.value.length} 项检验指标`)
    } else if (resultEditable.value) {
      ElMessage.warning('该标本暂无检验指标，请确认已关联检验项目')
    } else {
      ElMessage.info('该标本尚未回传检验结果')
    }
  } catch (e: any) {
    ElMessage.error('加载失败: ' + (e?.message || '未知错误'))
  } finally {
    resultLoading.value = false
  }
}

async function saveResults() {
  if (!selectedSample.value || results.value.length === 0) return
  saving.value = true
  try {
    await saveTechnicianResult(selectedSample.value.id, results.value)
    ElMessage.success('结果已保存')
  } catch {
    ElMessage.error('保存失败, 请重试')
  } finally {
    saving.value = false
  }
}

function auditPass() {
  if (!selectedSample.value) return
  // 已完成的报告不能再进行终审通过操作
  if (selectedSample.value.status === 3) {
    ElMessage.warning('该报告已完成，不能进行终审通过')
    return
  }
  auditForm.pass = true
  auditForm.reason = ''
  auditVisible.value = true
}

async function confirmAudit() {
  if (!selectedSample.value) return
  try {
    await auditTechnicianReport(selectedSample.value.id, { pass: auditForm.pass, reason: auditForm.reason })
    ElMessage.success(auditForm.pass ? '终审通过，报告已完成' : '已驳回')
    auditVisible.value = false
    // 终审后更新列表状态
    if (auditForm.pass && selectedSample.value) {
      selectedSample.value.status = 3
    }
    loadSamples()
  } catch {
    ElMessage.error('终审失败')
  }
}

/** 已完成标本不允许取消完成, 如需调整请走撤销审核流程 */
async function printReport() {
  if (!selectedSample.value) return
  reportNo.value = reportNo.value || `RPT${new Date().getFullYear()}${String(new Date().getMonth() + 1).padStart(2, '0')}${String(new Date().getDate()).padStart(2, '0')}${String(Math.floor(Math.random() * 10000)).padStart(4, '0')}`
  // 根据当前标本的核收仪器加载对应模板
  const insId = selectedSample.value.receiveInstrumentId || receiveInstrumentId.value || undefined
  await loadTemplateForInstrument(insId as number | undefined)
  await loadStaffSignatures()
  printVisible.value = true
}

function doPrint() {
  const printArea = document.getElementById('printArea')
  if (!printArea) {
    ElMessage.warning('暂无打印内容')
    return
  }

  // ===== 最可靠的打印链路（最小补丁, 不走新窗口/iframe/document.write, 不会被拦截）=====
  // 思路: 临时给 <body> 打一个标记 class, 配合 @media print 的 CSS,
  //       在打印渲染阶段隐藏页面上除 #printArea 之外的所有 DOM, 直接 window.print(),
  //       之后还原 class。此方式不需要任何新窗口/iframe/docwrite, 兼容性最好。
  try {
    const body = document.body
    // 1) 先追加专用打印样式 (去重id, 避免重复append)
    const styleId = '__lis_technician_print_style__'
    let styleEl = document.getElementById(styleId) as HTMLStyleElement | null
    if (!styleEl) {
      styleEl = document.createElement('style')
      styleEl.id = styleId
      const tpl = reportTemplate.value
      const paperSize = tpl?.paperSize || 'A4'
      const orientation = tpl?.orientation || 'portrait'
      const margin = tpl?.margin ?? 10
      styleEl.innerHTML = `
        @page { size: ${paperSize} ${orientation}; margin: ${margin}mm; }
        @media print {
          body.__lis_in_print__ > :not(.el-overlay-container):not(.v-modal):not(.el-message) {
            visibility: hidden !important;
          }
          body.__lis_in_print__ #printArea,
          body.__lis_in_print__ #printArea * {
            visibility: visible !important;
          }
          body.__lis_in_print__ .el-dialog,
          body.__lis_in_print__ .el-dialog * {
            visibility: hidden !important;
          }
          body.__lis_in_print__ #printArea {
            visibility: visible !important;
            position: absolute !important;
            left: 0 !important;
            top: 0 !important;
            width: 100% !important;
            padding: 0 !important;
            margin: 0 !important;
            background: #fff !important;
          }
          body.__lis_in_print__ #printArea .print-toolbar { display: none !important; }
          body.__lis_in_print__ .report-renderer-wrapper { background: #fff !important; padding: 0 !important; }
          body.__lis_in_print__ .report-paper { width: 100%; }
          body.__lis_in_print__ .render-table { width: 100%; border-collapse: collapse; font-size: 12px; }
          body.__lis_in_print__ .render-table th,
          body.__lis_in_print__ .render-table td { border: 1px solid #dcdfe6; padding: 4px 6px; text-align: left; }
          body.__lis_in_print__ .render-table th { background: #f5f7fa; font-weight: 600; }
          body.__lis_in_print__ .critical-cell { color: #f56c6c; font-weight: 700; }
          body.__lis_in_print__ .high-cell { color: #f56c6c; }
          body.__lis_in_print__ .low-cell { color: #e6a23c; }
          body.__lis_in_print__ .el-dialog__headerbtn,
          body.__lis_in_print__ .el-dialog__footer { display: none !important; }
        }
      `
      document.head.appendChild(styleEl)
    }
    // 2) 给 body 打标
    body.classList.add('__lis_in_print__')

    const doActuallyPrint = () => {
      try {
        window.focus()
        window.print()
        ElMessage.success('已发送打印任务, 若未出纸请检查默认打印机连接 (在启用 --kiosk-printing 的终端会直接静默出纸)')
      } finally {
        // 3) 还原, 避免影响界面显示 (即使 print 抛错也必须还原)
        body.classList.remove('__lis_in_print__')
      }
    }
    // 等待一个 rAF + 50ms, 确保浏览器把 class 与样式重绘完成后再打印, 避免"打印空白"
    window.requestAnimationFrame(() => window.setTimeout(doActuallyPrint, 80))
  } catch (e: any) {
    // 极端兜底: 只要走到这里, 一定给用户可见反馈, 避免"无反应"的疑虑
    console.error('[technician] doPrint failed:', e)
    const msg = e?.message || '未知错误'
    ElMessage.error(`打印失败: ${msg}`)
    // 最后兜底 —— 直接尝试调用浏览器原生 window.print() 再死一次
    try { window.print() } catch { /* ignore */ }
  }
}

function getStatusType(status: number) {
  const map: Record<number, string> = { 0: 'warning', 1: '', 2: 'danger', 3: 'success', 99: 'danger' }
  return map[status] || 'info'
}

function getStatusText(status: number) {
  const map: Record<number, string> = { 0: '待审', 1: '终审', 2: '急诊', 3: '已完成', 99: '无主标本' }
  return map[status] || '未知'
}

function formatTime(time: string | undefined) {
  if (!time) return '-'
  try {
    return new Date(time).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return time
  }
}
/** 时间轴显示：只显示 月/日 时:分:秒（去掉年份，节省横向空间避免标签挤压重叠） */
function formatTimeMD(time: string | undefined) {
  if (!time) return ''
  try {
    const d = new Date(time)
    if (isNaN(d.getTime())) return time
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  } catch {
    return time
  }
}

async function loadSummary() {
  try {
    const params: { instrumentId?: number; startDate?: string; endDate?: string } = {}
    if (receiveInstrumentId.value !== undefined) params.instrumentId = receiveInstrumentId.value
    if (searchForm.date) {
      params.startDate = searchForm.date
      params.endDate = searchForm.date
    }
    const res = await getTechnicianSummary(params)
    summary.total = res?.total || 0
    summary.pending = res?.pending || 0
    summary.urgent = res?.urgent || 0
    summary.audited = res?.audited || 0
  } catch {
    summary.total = 0
    summary.pending = 0
    summary.urgent = 0
    summary.audited = 0
  }
}

// ========== 操作菜单处理 ==========

/** 撤销核收 */
async function handleCancelReceive(samples: TechnicianSample[]) {
  try {
    await ElMessageBox.confirm(
      `确认撤销 ${samples.length} 份标本的核收？撤销后标本号、核收仪器、核收时间将被清除，标本退回已签收状态。`,
      '撤销核收确认',
      { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    const res = await cancelReceive(samples.map(s => s.id))
    if (res.success > 0) {
      ElMessage.success(`撤销核收成功 ${res.success} 份${res.failed > 0 ? `，失败 ${res.failed} 份` : ''}`)
      loadSamples()
      nextTick(() => clearChecked())
    } else {
      ElMessage.warning('撤销核收失败，可能标本非已核收状态')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '撤销核收失败')
  }
}

/** 修改标本号 */
async function handleModifySampleNo(sample: TechnicianSample) {
  const oldNo = sample.sampleNo || ''
  try {
    const { value } = await ElMessageBox.prompt('请输入新的标本号', '修改标本号', {
      inputValue: oldNo,
      inputPlaceholder: '请输入标本号',
      inputValidator: (v) => (v && v.trim() ? true : '标本号不能为空'),
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    const res = await updateSampleNo(sample.id, value.trim())
    if (res.success > 0) {
      ElMessage.success('标本号修改成功')
      // 同步更新列表和当前选中
      sample.sampleNo = value.trim()
      if (selectedSample.value?.id === sample.id) {
        selectedSample.value.sampleNo = value.trim()
      }
      if (patientDetail.value) {
        patientDetail.value.sampleNo = value.trim()
      }
    } else {
      ElMessage.error(res.messages?.[0] || '标本号修改失败')
    }
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e?.message || '操作失败')
  }
}

function onOpCommand(cmd: string) {
  // 部分菜单的有效性：单标本功能
  const single = checkedSamples.value.length === 1 ? checkedSamples.value[0] : null
  const batch = checkedSamples.value
  switch (cmd) {
    case 'cancel-receive':
      if (batch.length === 0) { ElMessage.warning('请先勾选标本'); return }
      handleCancelReceive(batch); break
    case 'waive-receive':
      if (batch.length === 0) { ElMessage.warning('请先勾选标本'); return }
      dialogVisible.waiveReceive = true; break
    case 'modify-sample-no':
      if (!single) { ElMessage.warning('请勾选 1 个标本修改标本号'); return }
      handleModifySampleNo(single); break
    case 'instrument-signin':
      dialogVisible.instrumentSignin = true; break
    case 'favorites':
      dialogVisible.favorites = true; break
    case 'sample-copy':
      if (batch.length === 0) { ElMessage.warning('请先勾选标本'); return }
      dialogVisible.sampleCopy = true; break
    case 'batch-import':
      dialogVisible.batchImport = true; break
    case 'auto-audit-signin':
      dialogVisible.autoAuditSignin = true; break
    case 'sample-split':
      if (!single) { ElMessage.warning('请勾选 1 个标本进行拆分'); return }
      dialogVisible.sampleSplit = true; break
    case 'sample-unsplit':
      if (!single) { ElMessage.warning('请勾选 1 个标本进行撤销拆分'); return }
      dialogVisible.sampleUnsplit = true; break
    case 'batch-audit':
      if (batch.length === 0) { ElMessage.warning('请先勾选标本'); return }
      dialogVisible.batchAudit = true; break
    case 'batch-unaudit':
      if (batch.length === 0) { ElMessage.warning('请先勾选标本'); return }
      dialogVisible.batchUnaudit = true; break
    case 'cancel-complete':
      if (batch.length === 0) { ElMessage.warning('请先勾选标本'); return }
      doCancelComplete(batch); break
    case 'batch-modify-result':
      if (batch.length === 0) { ElMessage.warning('请先勾选标本'); return }
      dialogVisible.batchModifyResult = true; break
    case 'batch-modify-inspector':
      if (batch.length === 0) { ElMessage.warning('请先勾选标本'); return }
      dialogVisible.batchModifyInspector = true; break
    case 'sample-to-qc':
      if (!single) { ElMessage.warning('请勾选 1 个标本转为质控'); return }
      openSampleToQcDialog(single); break
    case 'cancel-qc':
      if (!single) { ElMessage.warning('请勾选 1 个标本取消质控'); return }
      handleCancelQc(single); break
    case 'batch-reprint-barcode':
      if (batch.length === 0) { ElMessage.warning('请先勾选标本'); return }
      dialogVisible.batchReprintBarcode = true; break
    case 'sample-destroy':
      if (batch.length === 0) { ElMessage.warning('请先勾选标本'); return }
      dialogVisible.sampleDestroy = true; break
    default: ElMessage.info(`未实现的操作：${cmd}`)
  }
}

/** 取消完成: 将已完成(status===3)标本退回已核收状态 */
async function doCancelComplete(samples: TechnicianSample[]) {
  const completed = samples.filter(s => s.status === 3)
  if (completed.length === 0) { ElMessage.warning('请勾选已完成标本执行取消完成'); return }
  try {
    await ElMessageBox.confirm(
      `确定取消完成 ${completed.length} 个已完成标本吗？取消完成后将退回「已核收」状态。`,
      '取消完成', { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '返回' }
    )
  } catch { return }
  try {
    const res = await batchUnauditTechnician(completed.map(s => s.id))
    ElMessage.success(`取消完成：成功 ${res.success}，失败 ${res.failed}`)
    loadSamples()
    nextTick(() => clearChecked())
  } catch (e: any) {
    ElMessage.error(e?.message || '取消完成失败')
  }
}

/** 批量操作成功后统一刷新列表 & 清空选中 */
function onBatchSuccess() {
  loadSamples()
  nextTick(() => clearChecked())
}

/** 使用收藏夹内容 */
function onUseFavorite(fav: FavoriteItem) {
  if (fav.type === 'sample' && fav.sampleIds?.length) {
    // 简单处理：在搜索框中填充该收藏夹第一个标本的条码，便于用户识别
    const firstId = fav.sampleIds[0]
    const hit = sampleList.value.find(s => s.id === firstId)
    if (hit) { searchForm.keyword = hit.sampleBarcode || hit.patientName; handleSampleSelect(hit) }
  } else if (fav.type === 'keyword' && fav.payload?.keyword) {
    searchForm.keyword = fav.payload.keyword; loadSamples()
    ElMessage.success(`已应用搜索收藏 [${fav.name}]`)
  } else if (fav.type === 'panel') {
    ElMessage.success(`已应用检验项目收藏：${fav.name}`)
  } else {
    ElMessage.info('该收藏类型暂不支持快速跳转')
  }
}

// ========== 转为质控标本 / 取消质控结果 ==========

/** 打开"转为质控标本"对话框: 加载该标本的检验结果与可选的质控批号 */
async function openSampleToQcDialog(sample: TechnicianSample) {
  qcLotLoading.value = true
  try {
    if (qcLotList.value.length === 0) {
      qcLotList.value = await getAllQcLots()
    }
    const detail = await getTechnicianDetail(sample.id)
    const sampleResults = detail.results || []
    const rows: SampleToQcMappingRow[] = sampleResults
      .filter(r => r.testItemId && r.resultValue !== null && r.resultValue !== undefined && String(r.resultValue).trim() !== '')
      .map(r => {
        const matches = qcLotList.value.filter(l => l.testItemId === r.testItemId)
        return {
          testItemId: r.testItemId,
          testItemName: r.testItemName,
          resultValue: String(r.resultValue),
          qcLotId: matches.length === 1 ? matches[0].id : undefined
        }
      })
    if (rows.length === 0) {
      ElMessage.warning('该标本暂无已录入实测值的检验指标，无法转换')
      return
    }
    sampleToQcTarget.value = sample
    sampleToQcMappings.value = rows
    dialogVisible.sampleToQc = true
  } catch (e: any) {
    ElMessage.error('加载标本检验指标失败: ' + (e?.message || '未知错误'))
  } finally {
    qcLotLoading.value = false
  }
}

/** 该检验指标可选的质控批号 */
function matchingLots(row: SampleToQcMappingRow) {
  return qcLotList.value.filter(l => l.testItemId === row.testItemId)
}

/** 确认转换: 提交指标-批号映射, 生成质控记录 */
async function confirmSampleToQc() {
  const mappings = sampleToQcMappings.value
    .filter(m => m.qcLotId)
    .map(m => ({ testItemId: m.testItemId, qcLotId: m.qcLotId! }))
  if (mappings.length === 0) {
    ElMessage.warning('请至少为一个检验指标选择质控批号')
    return
  }
  if (!sampleToQcTarget.value) return
  sampleToQcLoading.value = true
  try {
    const created = await sampleToQc({
      sampleId: sampleToQcTarget.value.id,
      operatorId: userStore.userId || undefined,
      mappings
    })
    ElMessage.success(`已生成 ${created.length} 条质控记录`)
    dialogVisible.sampleToQc = false
    loadSamples()
  } catch (e: any) {
    ElMessage.error(e?.message || '转换失败')
  } finally {
    sampleToQcLoading.value = false
  }
}

/** 取消质控结果: 删除该标本生成的全部质控记录 */
async function handleCancelQc(sample: TechnicianSample) {
  try {
    await ElMessageBox.confirm(
      `确定取消标本「${sample.sampleNo || sample.sampleBarcode}」的质控结果吗？取消后该标本生成的全部质控记录将被删除。`,
      '取消质控结果', { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '返回' }
    )
  } catch { return }
  try {
    const deleted = await cancelQcBySample(sample.id)
    ElMessage.success(`已删除 ${deleted} 条质控记录`)
    loadSamples()
  } catch (e: any) {
    ElMessage.error(e?.message || '取消质控失败')
  }
}

/** 键盘快捷键: Alt+↑/↓ 快捷切换上/下一个标本 */
function navigateSample(dir: 1 | -1) {
  const list = filteredSamples.value
  if (list.length === 0) return
  const curIdx = list.findIndex(s => selectedSample.value && s.id === selectedSample.value.id)
  const nextIdx = curIdx === -1 ? 0 : (curIdx + dir + list.length) % list.length
  handleSampleSelect(list[nextIdx])
}

/** 键盘快捷键 (F9 批量审核 / F4 提交终审 / Ctrl+S 保存 / Alt+↑↓ 切换标本 / F8 聚焦核收) */
function onKeydown(e: KeyboardEvent) {
  // F9 批量审核
  if (e.key === 'F9') {
    e.preventDefault()
    if (checkedSamples.value.length === 0) { ElMessage.warning('请先勾选标本，再按 F9 批量审核'); return }
    dialogVisible.batchAudit = true
    return
  }
  // F4 提交终审
  if (e.key === 'F4') {
    e.preventDefault()
    auditPass()
    return
  }
  // Ctrl+S 保存当前结果
  if (e.ctrlKey && !e.shiftKey && !e.altKey && (e.key === 's' || e.key === 'S')) {
    e.preventDefault()
    if (resultEditable.value && results.value.length > 0) saveResults()
    else ElMessage.warning('当前无可保存的检验结果')
    return
  }
  // Ctrl+Enter 提交终审
  if (e.ctrlKey && e.key === 'Enter') {
    e.preventDefault()
    auditPass()
    return
  }
  // Alt+↑/↓ 切换标本
  if (e.altKey && !e.ctrlKey && (e.key === 'ArrowUp' || e.key === 'ArrowDown')) {
    e.preventDefault()
    navigateSample(e.key === 'ArrowUp' ? -1 : 1)
    return
  }
  // F8 标记危急值已确认 (若有未确认危急值则全部确认, 否则聚焦核收条码输入框)
  if (e.key === 'F8') {
    e.preventDefault()
    if (unconfirmedCriticalResults.value.length > 0) {
      confirmAllCritical()
    } else {
      receiveInputRef.value?.focus()
    }
  }
}

onMounted(async () => {
  // 先加载核收仪器(默认选中第一个), 再自动触发 200 条 Mock 数据性能测试, 再加载统计
  await loadInstruments()
  // 自动执行一次 200 条性能测试（无需真实数据）
  __loadMockPerformanceTest()
  loadSummary()
  loadTemplateList()
  loadStaffOptions()
  loadUnmatchedPendingCount()
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => { window.removeEventListener('keydown', onKeydown) })
</script>

<style scoped lang="scss">
.technician-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  padding: 8px;
  overflow: hidden;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 8px 16px;
  border-radius: 6px;
  margin-bottom: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.top-stats {
  display: flex;
  gap: 8px;
}

.shortcut-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  white-space: nowrap;

  .hint-item {
    cursor: help;
    b {
      color: #1a73e8;
      font-weight: 600;
      background: #e8f0fe;
      padding: 1px 5px;
      border-radius: 3px;
      margin-right: 2px;
    }
  }
  .hint-sep { color: #dcdfe6; }
}

.filter-area :deep(.el-form-item) {
  margin-bottom: 0;
}

.main-content {
  flex: 1;
  display: flex;
  gap: 8px;
  overflow: hidden;
}

.left-col, .middle-col, .right-col {
  background: #fff;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.left-col {
  width: 340px;
}

.middle-col {
  width: 320px;
}

.right-col {
  flex: 1;
  min-width: 500px;
}

/* 拖拽分隔条 */
.resize-handle {
  width: 6px;
  flex-shrink: 0;
  cursor: col-resize;
  border-radius: 3px;
  background: transparent;
  transition: background 0.15s;
  position: relative;
}
.resize-handle:hover,
.main-content.resizing .resize-handle {
  background: #c0c4cc;
}
.resize-handle::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 2px;
  height: 24px;
  border-radius: 2px;
  background: #d0d3d8;
}
.resize-handle:hover::after,
.main-content.resizing .resize-handle::after {
  background: #1a73e8;
}
.main-content.resizing {
  cursor: col-resize;
  user-select: none;
}

.col-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  background: #fafbfc;
  gap: 8px;
}
.checked-count {
  margin-right: auto;
  font-size: 12px;
  font-weight: 500;
  color: #1a73e8;
  padding: 2px 8px;
  background: #ecf5ff;
  border-radius: 4px;
}
.checked-count b { color: #1a73e8; margin: 0 2px }

.receive-toolbar {
  padding: 6px 8px;
  border-bottom: 1px solid #ebeef5;
  background: #f0f9ff;
}

.list-toolbar {
  padding: 6px 8px;
  border-bottom: 1px solid #f0f0f0;
}

.left-footer {
  padding: 8px;
  border-top: 1px solid #ebeef5;
  background: #fafbfc;
  display: flex;
  justify-content: center;
}

.left-footer :deep(.el-radio-group) {
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  gap: 6px;
}

.patient-info {
  padding: 12px;
  overflow-y: auto;
  flex: 1;
}

.info-section {
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #ebeef5;
  &:last-child { border-bottom: none; }
}

.section-title-bar {
  font-size: 13px;
  font-weight: 600;
  color: #1a73e8;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #1a73e8;
}

.patient-main {
  display: flex;
  gap: 12px;
  align-items: center;
  background: linear-gradient(135deg, #ecf5ff 0%, #f0f9ff 100%);
  padding: 10px;
  border-radius: 6px;
  margin-bottom: 14px;
  border-bottom: none !important;
}

.patient-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: #1a73e8;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.patient-basic {
  flex: 1;
}

.patient-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.patient-age {
  font-size: 13px;
  color: #606266;
  margin-left: 4px;
}

.patient-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.info-row {
  display: flex;
  font-size: 13px;
  margin-bottom: 6px;
  line-height: 1.6;

  .info-label {
    width: 75px;
    color: #909399;
    flex-shrink: 0;
  }
  .info-value {
    color: #303133;
    flex: 1;
    word-break: break-all;
  }
  .fee-value {
    font-weight: 600;
    color: #f56c6c;
  }
}

.fee-section {
  background: #fef9f0;
  padding: 8px 10px;
  border-radius: 4px;
  border: none;
}

/* ============ 方案C 布局新样式 ============ */

/* 左侧: 单元格辅助小字（方案C） */
.sample-name-cell,
.sample-no-cell,
.sample-testitems-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  padding: 2px 0;
}
.sample-name-main,
.sample-no-main,
.sample-testitems-main {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}
.sample-name-sub,
.sample-no-sub,
.sample-testitems-sub {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
  line-height: 1.3;
}

/* 中间: 方案C主布局容器 */
.patient-info-layout-c {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  /* 头卡保留自身样式 + 新密度 */
  .patient-main {
    margin-bottom: 0;
    padding: 8px 10px;
    gap: 10px;
  }
  .patient-avatar {
    width: 42px;
    height: 42px;
  }
  .patient-name {
    font-size: 15px;
    margin-bottom: 2px;
  }
  .patient-age {
    font-size: 12px;
  }
}

/* 瓦片网格: 自适应3列，关键信息平铺，空值不占位不渲染 */
.overview-tiles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px 8px;
  padding: 6px 2px;
}
.overview-tile {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 6px 8px;
  min-width: 0;
  &--edit {
    background: #f4faff;
    border-color: #b3d8ff;
  }
}
.tile-label {
  font-size: 11px;
  color: #909399;
  line-height: 1.2;
}
.tile-value {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  line-height: 1.35;
  word-break: break-all;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 时间流程条: 采集 → 核收 → 检验 → 报告 */
.timeline-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  padding: 8px 4px;
  position: relative;
}
.timeline-node {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
}
.timeline-node-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f0f2f5;
  border: 2px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  z-index: 1;
}
.timeline-node.is-done .timeline-node-dot {
  background: #1a73e8;
  border-color: #1a73e8;
  color: #fff;
}
.timeline-dot-index {
  font-size: 11px;
  font-weight: 600;
}
.timeline-node-body {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding-right: 4px;
  box-sizing: border-box;
}
.timeline-node-label {
  font-size: 11px;
  color: #909399;
  line-height: 1.2;
}
.timeline-node.is-done .timeline-node-label {
  color: #606266;
  font-weight: 500;
}
.timeline-node-time {
  font-size: 12px;
  color: #303133;
  font-weight: 500;
  line-height: 1.35;
  word-break: break-all;
  &.is-empty {
    color: #c0c4cc;
    font-weight: normal;
  }
}
.timeline-node.is-edit .timeline-node-body {
  background: #f4faff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  padding: 3px 4px;
  margin-top: 2px;
}
/* 节点之间的连接线: connector 位于 timeline-node (相对定位) 内部，
   从当前节点圆中心向右延伸到下一个节点圆中心 */
.timeline-connector {
  position: absolute;
  top: 9px;
  /* 起点: 当前节点圆的右边缘 (宽度 20px) */
  left: 20px;
  /* 宽度: 横跨剩余 25% 槽宽（含 gap）减去两个圆的半径之和，约等于下一个圆的左边缘 */
  right: calc(-1 * (100% - 20px) + 0px);
  height: 2px;
  background: #e4e7ed;
  z-index: 0;
  pointer-events: none;
  &.is-done {
    background: #1a73e8;
  }
}
/* 使用 grid gap=4px 时，每个 timeline-node 宽度 <100%，连接器需要穿过 grid gap:
   用 100% 作为跨越距离（从本节点圆的右边缘到下一个节点的右边缘是一个 cell 宽度 + gap），
   但由于 connector 放置在 node 内，我们延伸到 node 右边界外 */
.timeline-node .timeline-connector {
  /* 覆盖：从本节点右侧圆中心横向延伸到下一个节点左侧圆中心
     node 宽: 100%, gap 4px, 下一个圆起始位置 = 100% + 4px
     所以跨越距离 = (100% + 4px) - 20px (本圆圆心到左边缘) */
  width: calc(100% + 4px - 20px);
  left: 20px;
  right: auto;
}

/* 人员记录 + 费用信息 并排 */
.person-fee-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 10px;
  align-items: start;
}
.person-panel, .fee-panel {
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 8px 10px;
}
.person-panel-title, .fee-panel-title {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
  padding-bottom: 4px;
  border-bottom: 1px dashed #ebeef5;
}
.person-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 10px;
}
.contact-grid {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #ebeef5;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 10px;
}
.person-item {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  &--full {
    grid-column: 1 / -1;
  }
}
.person-label {
  font-size: 11px;
  color: #909399;
  line-height: 1.2;
}
.person-value {
  font-size: 13px;
  color: #303133;
  line-height: 1.4;
  word-break: break-all;
}
.fee-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.fee-card {
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 6px;
  padding: 8px 8px 6px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.fee-card-label {
  font-size: 11px;
  color: #909399;
}
.fee-card-value {
  font-size: 15px;
  font-weight: 600;
  color: #e6a23c;
  line-height: 1.3;
}

/* 方案C: 左侧标本列表行高紧凑化 */
:deep(.sample-list-table .el-table__cell) {
  padding: 4px 0 !important;
}
:deep(.sample-list-table .el-table td) {
  height: auto;
  line-height: 1.35;
}

/* 窄屏(中间列 <320px) 时的响应式 */
@media (max-width: 420px) {
  .overview-tiles { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .person-fee-row { grid-template-columns: 1fr; }
  .timeline-strip { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 2px; }
}

.result-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 8px;
  overflow: hidden;
}

.result-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 4px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 6px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-summary {
  font-size: 12px;
  color: #606266;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.result-count {
  color: #909399;
  font-weight: normal;
  font-size: 12px;
  margin-left: 4px;
}

/* 危急值内嵌处理面板 */
.critical-board {
  background: #fff1f0;
  border: 1px solid #ffa39e;
  border-radius: 6px;
  margin-bottom: 8px;
  overflow: hidden;

  &.critical-all-confirmed {
    background: #f0f9eb;
    border-color: #b7eb8f;
  }

  .critical-board-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    background: rgba(245, 34, 45, 0.06);
    border-bottom: 1px solid #ffa39e;

    .critical-board-title {
      font-size: 13px;
      font-weight: 600;
      color: #f5222d;
      flex: 1;
    }

    .critical-board-actions {
      flex-shrink: 0;
    }
  }

  &.critical-all-confirmed .critical-board-header {
    background: rgba(103, 194, 58, 0.06);
    border-bottom-color: #b7eb8f;
    .critical-board-title { color: #67c23a; }
  }

  .critical-board-body {
    padding: 6px 12px 8px;
  }

  .critical-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 5px 8px;
    margin-bottom: 4px;
    background: #fff;
    border: 1px solid #ffccc7;
    border-radius: 4px;
    transition: all 0.3s;

    &:last-child { margin-bottom: 0; }

    &.critical-item-confirmed {
      background: #f0f9eb;
      border-color: #b7eb8f;
    }

    .critical-item-main {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;
    }

    .critical-warn-icon { flex-shrink: 0; }

    .critical-item-name {
      font-weight: 600;
      color: #303133;
    }
    .critical-item-value {
      font-weight: 700;
      color: #f5222d;
      font-size: 14px;
    }
    .critical-item-ref {
      color: #909399;
      font-size: 12px;
    }
    .critical-item-action {
      flex-shrink: 0;
    }
  }
}

.result-table {
  flex: 1;
  overflow: auto;
}

.dual-result-grid {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.single-result-grid {
  display: block;
}

/* 分组结果列表 */
.grouped-list {
  display: block;
}

.result-group {
  margin-bottom: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.result-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: #f0f7ff;
  border-bottom: 1px solid #e4eefb;
  font-size: 13px;
  font-weight: 600;
  color: #1a73e8;
}

.result-group-title .group-name {
  flex: 1;
}

.result-group-title .group-count {
  font-size: 11px;
  font-weight: 400;
  color: #909399;
}

.result-col {
  flex: 1;
  min-width: 0;
}

.result-col-header {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1.2fr 0.5fr;
  gap: 4px;
  padding: 6px 8px;
  background: #f5f7fa;
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
  position: sticky;
  top: 0;
  z-index: 2;
}

.result-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1.2fr 0.5fr;
  gap: 4px;
  padding: 4px 8px;
  border-bottom: 1px solid #f0f0f0;
  align-items: center;
  font-size: 13px;

  &:hover {
    background: #f5f7fa;
  }

  &.abnormal-row {
    background-color: #fdf6ec;

    &:hover {
      background-color: #faecd8;
    }
  }

  &.critical-row {
    background-color: #fef0f0;

    &:hover {
      background-color: #fde2e2;
    }
  }
}

.result-cell {
  min-width: 0;
  overflow: hidden;
}

.name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .panel-tag {
    font-size: 10px;
    color: #909399;
    background: #f4f4f5;
    padding: 0 4px;
    border-radius: 2px;
    width: fit-content;
  }
}

.value-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.arrow-flag {
  font-size: 16px;
  font-weight: 700;
  color: #f56c6c;
  min-width: 18px;
}

.ref-cell {
  display: flex;
  flex-direction: column;
  font-size: 12px;

  .ref-range {
    color: #606266;
  }

  .unit-text {
    color: #909399;
    font-size: 11px;
  }
}

.hint-cell {
  text-align: center;
}

.readonly-tip {
  margin-top: 8px;
}

.col-setting-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  color: #303133;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.item-name-cell {
  display: flex;
  align-items: center;
}

.abnormal-item {
  color: #f56c6c;
  font-weight: 600;
}

.result-input {
  display: flex;
  align-items: center;
  gap: 2px;
}

.result-flag {
  font-size: 12px;
  font-weight: 700;
  color: #f56c6c;
  min-width: 14px;
}

.abnormal :deep(.el-input__inner) {
  color: #f56c6c;
  font-weight: 600;
}

.critical :deep(.el-input__inner) {
  color: #f56c6c;
  font-weight: 700;
  background: #fef0f0;
}

.abnormal-text {
  color: #f56c6c;
  font-weight: 600;
  font-size: 14px;
}

.ref-range {
  color: #606266;
  font-size: 12px;
}

.conclusion-section {
  padding: 8px 0;
  border-top: 1px solid #ebeef5;
}

.conclusion-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}

.action-bar {
  display: flex;
  gap: 8px;
  padding: 10px 0;
  justify-content: center;
  border-top: 1px solid #ebeef5;
}

.text-danger { color: #f56c6c; }
.text-green { color: #67c23a; }
.text-orange { color: #e6a23c; }

/* 打印预览样式 (限定在 #printArea 内, 避免影响左侧患者信息面板) */
#printArea {
  font-family: SimSun, serif;
  color: #000;

  .print-preview {
    padding: 20px;
    max-height: 70vh;
    overflow-y: auto;
  }

  .report-header {
    text-align: center;
    border-bottom: 2px solid #333;
    padding-bottom: 10px;
    margin-bottom: 15px;
  }

  .report-title {
    font-size: 22px;
    margin: 5px 0;
    font-weight: bold;
  }

  .report-hospital {
    font-size: 14px;
    color: #666;
  }

  .report-info {
    margin-bottom: 15px;
  }

  .info-section {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 4px 12px;
  }

  .info-row {
    display: flex;
    border-bottom: 1px solid #eee;
    padding: 4px 0;
    font-size: 12px;
  }

  .info-label {
    width: 90px;
    color: #666;
    flex-shrink: 0;
  }

  .info-value {
    flex: 1;
  }

  .report-results {
    margin-bottom: 15px;
  }

  .report-group {
    margin-bottom: 10px;
  }

  .report-group-title {
    background: #f5f5f5;
    padding: 6px 10px;
    font-weight: bold;
    font-size: 13px;
    border-left: 3px solid #1a73e8;
  }

  .report-results-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 12px;
  }

  .report-results-table th,
  .report-results-table td {
    border: 1px solid #ccc;
    padding: 4px 6px;
    text-align: center;
  }

  .report-results-table th {
    background: #f0f0f0;
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

  .report-footer {
    margin-top: 20px;
    font-size: 12px;
  }

  .sig-line {
    display: flex;
    justify-content: space-around;
    margin-bottom: 10px;
  }

  .report-note {
    text-align: center;
    color: #999;
    font-size: 11px;
  }

  .dual-results-wrapper {
    display: flex;
    gap: 8px;
  }

  .dual-col-table {
    flex: 1;
    min-width: 0;
  }

  .print-toolbar {
    margin-bottom: 8px;
  }
}

/* 详情对话框 */
.detail-container {
  padding: 10px 0;
}

.detail-row {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px dashed #ebeef5;
  &:last-child { border-bottom: none; }
}

.detail-label {
  width: 90px;
  color: #909399;
  font-size: 13px;
}

.detail-value {
  flex: 1;
  color: #303133;
  font-size: 13px;
}

/* 标本图像区域 (结果表格顶部) */
.sample-image-section {
  background: #fff;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}
.sample-image-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}
.sample-image-count {
  font-size: 12px;
  font-weight: normal;
  color: #909399;
}
.sample-image-list {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.sample-image-item {
  cursor: pointer;
  text-align: center;
}
.sample-image-thumb {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  transition: all 0.15s;
}
.sample-image-thumb:hover {
  border-color: #1a73e8;
  transform: scale(1.05);
}
.sample-image-name {
  display: block;
  font-size: 11px;
  color: #606266;
  margin-top: 4px;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-viewer-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.image-viewer-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}
.image-viewer-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.image-viewer-count {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}
.image-viewer-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #f5f7fa;
}

/* 标本右键菜单 */
.context-menu-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: transparent;
}
.context-menu {
  position: fixed;
  z-index: 3001;
  min-width: 170px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 21, 41, 0.18);
  padding: 5px 0;
  max-height: 70vh;
  overflow-y: auto;
}
.context-menu-title {
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 4px;
}
.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  font-size: 13px;
  color: #303133;
  cursor: pointer;
  white-space: nowrap;
}
.context-menu-item:hover {
  background: #ecf5ff;
  color: #1a73e8;
}
.context-menu-item .el-icon {
  font-size: 15px;
  color: #606266;
}
.context-menu-item:hover .el-icon {
  color: #1a73e8;
}

/* ========== 扩展结果列表样式 ========== */
.result-extended-table {
  :deep(.el-table__cell) {
    padding: 4px 6px;
    font-size: 12px;
  }
}
.indicator-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.3;
}
.indicator-name {
  font-weight: 500;
  color: #303133;
}
.indicator-en {
  font-size: 11px;
  color: #909399;
  font-style: italic;
}
.panel-tag-inline {
  font-size: 10px;
  color: #909399;
  background: #f4f4f5;
  padding: 0 4px;
  border-radius: 2px;
  width: fit-content;
}
.value-wrapper {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}
.value-wrapper .el-input {
  flex: 1;
}
.abnormal-item {
  color: #e6a23c;
}
.text-danger {
  color: #f56c6c;
}
.text-green {
  color: #67c23a;
}
.unit-text {
  color: #909399;
  font-size: 11px;
}
.last-value-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
}
.last-value {
  color: #606266;
}
.last-flag {
  color: #f56c6c;
  font-weight: 700;
}
.auto-audit-msg {
  color: #e6a23c;
  font-size: 11px;
}
.item-knowledge {
  color: #606266;
  font-size: 11px;
}

/* ============================================================
   方案 B：左侧卡片式标本列表 + 中间 Tab 患者信息
   ============================================================ */

/* ---------- 左侧标本卡片列 ---------- */
.left-col--cards .col-header--cards {
  padding: 8px 12px;
  align-items: center;
}
.sample-card-list {
  height: calc(100vh - 245px);
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 0; /* 卡片间距由 SampleCard 自身 margin-bottom(8px) 控制，避免叠加成 18px */
  background: #f5f7fa;
  scrollbar-width: thin;
}
.sample-card-list::-webkit-scrollbar { width: 6px; }
.sample-card-list::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}
.sample-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 12px 10px 10px;
  cursor: pointer;
  transition: all 0.15s ease;
  position: relative;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02);
}
.sample-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64,158,255,0.12);
  transform: translateY(-1px);
}
.sample-card.is-even {
  background: #fbfcfd;
}
.sample-card.is-selected {
  border-color: #409eff;
  background: linear-gradient(180deg, #ecf5ff 0%, #f6fbff 100%);
  box-shadow: 0 2px 10px rgba(64,158,255,0.2);
}
.sample-card.is-selected::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: #409eff;
  border-radius: 3px 0 0 3px;
}
.sample-card.is-checked {
  border-left: 3px solid #67c23a;
  padding-left: 9px;
}
.sample-card.is-urgent {
  background: linear-gradient(135deg, #fff0f0 0%, #fff 80%);
  border-color: #fbc4c4;
}
.sample-card.is-urgent.is-selected {
  background: linear-gradient(135deg, #ffe0e0 0%, #ecf5ff 100%);
}

.sample-card__top {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}
.sample-card__check {
  padding-top: 2px;
  flex-shrink: 0;
}
.sample-card__titles {
  flex: 1;
  min-width: 0;
}
.sample-card__name {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 3px;
}
.sample-card__name .name-main {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.sample-card__name .name-meta {
  font-size: 12px;
  color: #909399;
}
.sample-card__nos {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
}
.sample-card__nos .no-item b {
  color: #909399;
  font-weight: 500;
  margin-right: 2px;
}
.sample-card__tags {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.sample-card__body {
  border-top: 1px dashed #ebeef5;
  padding-top: 8px;
}
.sample-card__row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  font-size: 12px;
  color: #606266;
  line-height: 1.7;
  margin-bottom: 2px;
}
.sample-card__row:last-child { margin-bottom: 0; }
.sample-card__row b {
  color: #909399;
  font-weight: 500;
  margin-right: 3px;
}
.sample-card__row .meta-item {
  white-space: nowrap;
}
.sample-card__row .testitems-text {
  color: #303133;
  font-weight: 500;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.sample-card__times {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  color: #909399 !important;
  font-size: 11px !important;
}
.sample-card__times .time-item { color: #909399; }
.sample-card__times .time-item b { color: #909399; }
.sample-card__times .diag-item {
  color: #606266;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}

/* ---------- 中间 Tab 患者信息 ---------- */
.middle-col--tabs .col-header--tabs {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
}
.middle-col--tabs .col-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.middle-col--tabs .patient-edit-bar {
  display: inline-flex;
  gap: 6px;
}
.patient-info-layout-b {
  height: calc(100% - 44px);
  overflow: hidden;
  padding: 0 12px 12px;
}
.patient-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.patient-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 4px;
  scrollbar-width: thin;
}
.patient-tabs :deep(.el-tabs__content)::-webkit-scrollbar { width: 5px; }
.patient-tabs :deep(.el-tabs__content)::-webkit-scrollbar-thumb {
  background: #d4d7de;
  border-radius: 3px;
}
.patient-tabs :deep(.el-tab-pane) {
  height: 100%;
}
.tab-content {
  padding: 4px 8px 20px;
}
.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  padding: 10px 2px 6px;
  margin-top: 4px;
  border-bottom: 1px solid #ebeef5;
  letter-spacing: 0.5px;
}
.section-title:first-child {
  margin-top: 0;
}
.patient-main--b {
  background: linear-gradient(135deg, #ecf5ff 0%, #f0f9eb 100%);
  border: 1px solid #d9ecff;
  border-radius: 8px;
  padding: 12px 16px;
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 4px;
}
.patient-main--b .patient-avatar {
  width: 52px;
  height: 52px;
  background: #fff;
  color: #409eff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(64,158,255,0.15);
  flex-shrink: 0;
}
.patient-main--b .patient-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.patient-main--b .patient-age {
  font-size: 13px;
  color: #606266;
  margin-left: 8px;
  font-weight: 400;
}
.patient-main--b .patient-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

/* 瓦片网格（方案B更紧凑） */
.overview-tiles--b {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 8px 0 4px;
}
.overview-tiles--b .overview-tile {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 6px 10px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.overview-tiles--b .overview-tile--full {
  grid-column: span 3;
}
.overview-tiles--b .overview-tile--edit {
  background: #fffbe6;
  border-color: #faecd8;
}
.overview-tiles--b .tile-label {
  font-size: 11px;
  color: #909399;
}
.overview-tiles--b .tile-value {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.overview-tiles--b .tile-value--wide {
  white-space: normal;
  line-height: 1.4;
}

/* 诊断块 */
.diagnosis-box {
  margin-top: 8px;
  background: #fdf6ec;
  border-left: 3px solid #e6a23c;
  border-radius: 4px;
  padding: 8px 12px;
}
.diagnosis-box .section-title {
  padding: 0 0 4px;
  border: none;
  margin-top: 0;
  color: #b88230;
  letter-spacing: 0.5px;
}
.diagnosis-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

/* 时间流程条（方案B更紧凑） */
.timeline-strip--b {
  padding: 14px 10px;
  margin-top: 6px;
}

/* 费用行（方案B横向排列） */
.fee-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 8px 0 4px;
}
.fee-card {
  background: linear-gradient(180deg, #fff 0%, #f5f9ff 100%);
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 14px;
  min-width: 130px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1 1 calc(33.33% - 10px);
  box-shadow: 0 1px 3px rgba(0,0,0,0.02);
}
.fee-card-label {
  font-size: 12px;
  color: #909399;
}
.fee-card-value {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}
.fee-card-value--sm {
  font-size: 13px;
  font-weight: 500;
}
.text-red {
  color: #f56c6c !important;
}
.text-orange {
  color: #e6a23c !important;
}

/* 响应式适配 */
@media (max-width: 1200px) {
  .overview-tiles--b {
    grid-template-columns: repeat(2, 1fr);
  }
  .overview-tiles--b .overview-tile--full {
    grid-column: span 2;
  }
  .fee-card {
    flex: 1 1 calc(50% - 10px);
  }
}
@media (max-width: 900px) {
  .overview-tiles--b {
    grid-template-columns: 1fr;
  }
  .overview-tiles--b .overview-tile--full {
    grid-column: span 1;
  }
  .sample-card__times .diag-item { max-width: 100%; }
}

/* ========== 方案D: 扁平Grid双列患者信息 ========== */
.patient-info-grid {
  padding: 8px 10px;
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;

  /* 无分组标题 / 无分割线 / 无上下大边距：覆盖原 info-section 等 */
  .info-section, .section-title, .section-title-bar, .diagnosis-box {
    display: none;
  }
}

/* 头卡：紧凑化 */
.patient-main--compact {
  display: flex;
  gap: 10px;
  align-items: center;
  background: linear-gradient(135deg, #ecf5ff 0%, #f0f9ff 100%);
  padding: 8px 10px;
  border-radius: 6px;
  margin: 0;
  border-bottom: none;
}
.patient-main--compact .patient-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #1a73e8;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.patient-main--compact .patient-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 3px;
}
.patient-main--compact .patient-age {
  font-size: 12px;
  color: #606266;
  margin-left: 4px;
}
.patient-main--compact .patient-meta {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

/* 时间流程条：紧凑化 */
.timeline-strip--compact {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  padding: 4px 2px;
  margin: 0;
  position: relative;
}
.timeline-strip--compact .timeline-node {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  min-width: 0;
}
.timeline-strip--compact .timeline-node-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f0f2f5;
  border: 2px solid #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  z-index: 1;
}
.timeline-strip--compact .timeline-node.is-done .timeline-node-dot {
  background: #1a73e8;
  border-color: #1a73e8;
  color: #fff;
}
.timeline-strip--compact .timeline-dot-index {
  font-size: 10px;
  font-weight: 600;
}
.timeline-strip--compact .timeline-node-body {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding-right: 4px;
  box-sizing: border-box;
}
.timeline-strip--compact .timeline-node-label {
  font-size: 10px;
  color: #909399;
  line-height: 1.2;
}
.timeline-strip--compact .timeline-node.is-done .timeline-node-label {
  color: #606266;
  font-weight: 500;
}
.timeline-strip--compact .timeline-node-time {
  font-size: 11px;
  color: #303133;
  font-weight: 500;
  line-height: 1.3;
  word-break: break-all;
  &.is-empty {
    color: #c0c4cc;
    font-weight: normal;
  }
}
.timeline-strip--compact .timeline-node.is-edit .timeline-node-body {
  background: #f4faff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  padding: 2px 3px;
  margin-top: 2px;
}
.timeline-strip--compact .timeline-connector {
  position: absolute;
  top: 8px;
  height: 2px;
  background: #e4e7ed;
  z-index: 0;
  pointer-events: none;
  width: calc(100% + 4px - 18px);
  left: 18px;
  right: auto;
  &.is-done { background: #1a73e8; }
}

/* 信息双列网格：核心布局 */
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 10px;
  padding: 2px 0 0;
  margin: 0;
}
.info-grid-item {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  line-height: 1.3;
  padding: 3px 4px;
  background: transparent;
  border-radius: 3px;
  &:hover { background: #fafbfc; }
}
.info-grid-item--full {
  grid-column: 1 / -1;
}
.info-grid-label {
  font-size: 11px;
  color: #909399;
  line-height: 1.25;
}
.info-grid-value {
  font-size: 12.5px;
  color: #303133;
  font-weight: 500;
  line-height: 1.45;
  word-break: break-all;
  white-space: normal;
  min-height: 1em;
}
.info-grid-editor {
  width: 100%;
  min-width: 0;
}

/* 费用卡片紧凑网格（和字段区一致的两列视觉） */
.fee-grid-compact {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  padding: 2px 0 0;
  margin: 0;
  border-top: none;
}
.fee-card-compact {
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 5px;
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.fee-card-compact__label {
  font-size: 11px;
  color: #909399;
}
.fee-card-compact__value {
  font-size: 14px;
  font-weight: 600;
  color: #e6a23c;
  line-height: 1.25;
  &--red { color: #f56c6c; }
  &--green { color: #67c23a; }
}

/* 窄屏：中间列 < 320 时 网格降 1 列 */
@media (max-width: 420px) {
  .info-grid { grid-template-columns: 1fr; }
  .fee-grid-compact { grid-template-columns: 1fr; }
}

/* ============================================================
   患者信息字段卡：一个独立小卡片 = 一个数据项（全部平铺，不用点击 Tab）
   说明：短字段半宽 / 长文本(fullSpan)占满整行，费用卡与字段卡混排
   ============================================================ */
.pit-card-grid {
  display: grid;
  /* 默认 2 列；根据中间列宽度是否 < 340 自动缩成 1 列 */
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 10px;
  margin-top: 4px;
  &[data-middle-narrow='true'] {
    grid-template-columns: minmax(0, 1fr);
    gap: 5px;
  }
}
.pit-card {
  position: relative;
  background: linear-gradient(180deg, #fafcff 0%, #ffffff 100%);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 6px 8px 7px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 44px;
  box-sizing: border-box;
  overflow: hidden;
  /* 占满整行的长文本字段：诊断/检验项目/住址/备注 */
  &--full {
    grid-column: 1 / -1;
    min-height: 52px;
  }
  /* 费用卡配色 */
  &--fee {
    background: #fff7e6;
    border-color: #ffe7ba;
    min-height: 46px;
  }
  &__label {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 11.5px;
    line-height: 1.2;
    color: #8a8f99;
    font-weight: 500;
    /* 费用卡标签颜色稍突出 */
    &--fee { color: #c58d3b; }
  }
  &__hint {
    font-size: 11px;
    color: #b1b5bd;
    cursor: help;
  }
  &__value {
    font-size: 13px;
    font-weight: 600;
    color: #1f2d3d;
    line-height: 1.35;
    word-break: break-word;
    min-height: 1.35em;
    &--trunc {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    &--fee {
      font-size: 15px;
      color: #e6a23c;
    }
  }
  .pit-card__value.fee-card-compact__value--red,
  .pit-card__value--red   { color: #f56c6c !important; }
  .pit-card__value.fee-card-compact__value--green,
  .pit-card__value--green { color: #67c23a !important; }
  :deep(.el-input .el-input__wrapper),
  :deep(.el-select .el-input__wrapper) {
    box-shadow: 0 0 0 1px var(--el-border-color) inset !important;
    padding: 0 8px;
  }
  :deep(.el-select) { width: 100%; }
}

/* ============================================================
   增强版时间流程条：5 列（申请→采集→核收→检验→报告），
   每个节点带圆点+连接线+标签+时间+操作人，布局更清晰
   ============================================================ */
.timeline-strip--enhanced {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  padding: 10px 6px 12px;
  margin: 4px 0 8px;
  background: linear-gradient(180deg, #f6f9ff 0%, #ffffff 100%);
  border: 1px solid #e3ecff;
  border-radius: 8px;
  position: relative;
}
.timeline-strip--enhanced .timeline-node {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
  padding: 0 4px;
}
.timeline-strip--enhanced .timeline-node-head {
  display: flex;
  align-items: center;
  width: 100%;
  height: 22px;
  position: relative;
}
.timeline-strip--enhanced .timeline-node-dot {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ffffff;
  border: 2px solid #d5dae3;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9aa0ac;
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  z-index: 2;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}
.timeline-strip--enhanced .timeline-node.is-done .timeline-node-dot {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
  box-shadow: 0 0 0 3px rgba(37,99,235,0.12), 0 2px 6px rgba(37,99,235,0.25);
}
.timeline-strip--enhanced .timeline-dot-index {
  font-size: 11px;
  font-weight: 600;
}
.timeline-strip--enhanced .timeline-connector {
  flex: 1;
  height: 3px;
  background: #e5e8ef;
  border-radius: 2px;
  z-index: 1;
  margin: 0 2px 0 4px;
  min-width: 10px;
  &.is-done {
    background: linear-gradient(90deg, #2563eb 0%, #4f8bff 100%);
    box-shadow: inset 0 0 3px rgba(37,99,235,0.2);
  }
}
.timeline-strip--enhanced .timeline-node-body {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 2px;
  box-sizing: border-box;
}
.timeline-strip--enhanced .timeline-node-label {
  font-size: 12px;
  color: #7a808c;
  line-height: 1.2;
  font-weight: 600;
  letter-spacing: 0.3px;
}
.timeline-strip--enhanced .timeline-node.is-done .timeline-node-label {
  color: #1f2937;
}
.timeline-strip--enhanced .timeline-node-time {
  font-size: 12px;
  color: #2d3848;
  font-weight: 600;
  line-height: 1.35;
  word-break: break-all;
  font-family: Consolas, Menlo, 'Courier New', monospace;
  &.is-empty {
    color: #b4b9c2;
    font-weight: normal;
  }
}
.timeline-strip--enhanced .timeline-node-operator {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.3;
  .op-label {
    color: #9298a3;
    font-weight: 500;
    flex-shrink: 0;
  }
  .op-name {
    color: #324157;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100%;
  }
  &.is-empty .op-name {
    color: #b4b9c2;
    font-weight: normal;
  }
}
.timeline-strip--enhanced .timeline-node.is-edit .timeline-node-body {
  background: #eaf3ff;
  border: 1px solid #93c5fd;
  border-radius: 5px;
  padding: 3px 4px;
  margin-top: 2px;
}

/* ============================================================
   标本卡片列表：修复数据重叠问题
   - 确保每个卡片 flex-shrink: 0，不被压缩
   - 卡片内部 overflow: visible 允许内容自然撑开
   - 内容超长时卡片随内容自适应高度
   ============================================================ */
.sample-card-list {
  /* 保留原滚动容器样式，同时用 flex-shrink 防止卡片被挤压重叠 */
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: calc(100vh - 245px);
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px;
  background: #f5f7fa;
  scrollbar-width: thin;
}
/* 强制每个卡片不被压缩，独立撑开高度，保证永不重叠 */
.sample-card-list > .sample-card,
.sample-card-list :deep(.sample-card) {
  flex-shrink: 0 !important;
  height: auto !important;
  min-height: min-content !important;
  overflow: visible !important;
  display: block !important;
}
/* 兜底：SampleCard 组件根元素 scoped 样式无法穿透时，
   利用全局样式再叠加一层保险（父组件内 :deep 可覆盖子组件） */
.sample-card-list :deep(.sample-card__body) {
  overflow: visible;
}
.sample-card-list :deep(.sample-card__row) {
  overflow: visible;
}

/* ============ 检验项目多 chip 展示（表格/弹窗/底部栏通用） ============ */
.tch-item-chips {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  width: 100%;
  line-height: 1;
  vertical-align: middle;
}
.tch-chip {
  display: inline-block;
  padding: 1px 8px;
  border: 1px solid #d9e2f2;
  background: #f5f8ff;
  color: #2c3e50;
  border-radius: 10px;
  font-size: 12px;
  line-height: 18px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &:hover {
    background: #eaf0ff;
    border-color: var(--el-color-primary-light-5);
  }
}
</style>
