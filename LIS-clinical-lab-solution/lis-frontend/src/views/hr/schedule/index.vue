<template>
  <div class="schedule-page">
    <!-- 顶部工具条: 周导航 + 图例 + 批量指定 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <div class="week-nav">
          <el-button-group>
            <el-button @click="moveWeek(-1)">‹ 上一周</el-button>
            <el-button @click="moveWeek(1)">下一周 ›</el-button>
          </el-button-group>
          <el-button plain @click="moveWeek(0)">本周</el-button>
          <span class="week-range">{{ weekRangeText }}</span>
        </div>

        <div class="shift-legend">
          <span v-for="s in SHIFTS" :key="s.value" class="legend-item">
            <el-tag :type="s.tag" size="small" effect="dark" disable-transitions>{{ s.label }}</el-tag>
          </span>
          <span class="legend-tip">点击日期格快速切换班次</span>
        </div>

        <div class="actions">
          <el-button type="primary" :disabled="selectedStaffIds.length === 0" @click="openBulkAssign">
            指定班次<template v-if="selectedStaffIds.length"> (已选 {{ selectedStaffIds.length }} 人)</template>
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 排班网格表: 行=人员, 列=周一到周日 -->
    <el-card shadow="never" class="grid-card" v-loading="loading">
      <el-table
        :data="staffList"
        row-key="id"
        border
        v-loading="loading"
        @selection-change="onSelectionChange"
        class="schedule-table"
      >
        <el-table-column type="selection" width="44" align="center" fixed="left" />
        <el-table-column label="人员 / 班次" width="170" fixed="left">
          <template #header>
            <div class="name-header">人员</div>
          </template>
          <template #default="{ row }">
            <div class="staff-cell">
              <div class="staff-name">{{ row.name }}</div>
              <div class="staff-no">{{ row.staffNo }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column
          v-for="d in weekDays"
          :key="d.date"
          :label="d.weekday"
          min-width="120"
          align="center"
        >
          <template #header>
            <div class="date-header">
              <div class="date-weekday">{{ d.weekday }}</div>
              <div class="date-num">{{ d.monthDay }}<span v-if="d.isToday" class="today-dot">·今</span></div>
            </div>
          </template>
          <template #default="{ row }">
            <div
              class="shift-cell"
              :class="{ 'is-today': d.isToday }"
              @click.stop="openCellMenu($event, row.id, d.date)"
            >
              <el-tag
                v-if="getShift(row.id, d.date)"
                :type="shiftTag(getShift(row.id, d.date))"
                size="small"
                effect="dark"
                disable-transitions
              >{{ getShift(row.id, d.date) }}</el-tag>
              <span v-else class="cell-empty">＋</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 单元格快选班次: 悬浮在点击位置 -->
    <div
      v-if="cellMenu.visible"
      class="cell-menu"
      :style="{ left: cellMenu.x + 'px', top: cellMenu.y + 'px' }"
      @click.stop
    >
      <div class="cell-menu-title">{{ staffNameById(cellMenu.staffId) }} · {{ cellMenu.date }}</div>
      <div
        v-for="s in SHIFTS"
        :key="s.value"
        class="cell-menu-item"
        @click="assignCell(s.value)"
      ><el-tag :type="s.tag" size="small" effect="dark" disable-transitions>{{ s.label }}</el-tag></div>
      <div class="cell-menu-item is-clear" @click="clearCell">清除排班</div>
    </div>

    <!-- 批量指定班次弹窗 -->
    <el-dialog v-model="bulkVisible" title="批量指定班次" width="620px">
      <el-form label-width="90px">
        <el-form-item label="选择人员" required>
          <el-select
            v-model="bulkStaffIds"
            multiple
            filterable
            placeholder="可继续添加人员（已在表格勾选的默认带入）"
            style="width: 100%"
          >
            <el-option
              v-for="s in staffList"
              :key="s.id"
              :label="`${s.name} (${s.staffNo})`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选择日期" required>
          <el-checkbox-group v-model="bulkDates" class="date-checkbox-group">
            <el-checkbox v-for="d in weekDays" :key="d.date" :label="d.date" :value="d.date">
              {{ d.weekday }} {{ d.monthDay }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="班次" required>
          <el-select v-model="bulkShift" placeholder="请选择班次" style="width: 100%">
            <el-option v-for="s in SHIFTS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bulkVisible = false">取消</el-button>
        <el-button type="primary" :loading="bulkLoading" @click="confirmBulkAssign">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getScheduleRange,
  assignScheduleBatch,
  deleteSchedule,
  getAllActiveStaff,
  type Staff
} from '@/api/hr'

// 医院常用班次
const SHIFTS = [
  { label: '白班', value: '白班', tag: 'success' },
  { label: '中班', value: '中班', tag: 'warning' },
  { label: '夜班', value: '夜班', tag: 'danger' },
  { label: '休息', value: '休息', tag: 'info' }
] as const

const tagMap: Record<string, any> = { 白班: 'success', 中班: 'warning', 夜班: 'danger', 休息: 'info' }
function shiftTag(shift: string): any {
  return tagMap[shift] || 'info'
}

const loading = ref(false)
const staffList = ref<Staff[]>([])
const staffMap = ref(new Map<number, Staff>())

// 周视图状态
const anchorDate = ref(new Date()) // 当前聚焦的开始定位
const weekDays = computed(() => buildWeekDays(anchorDate.value))

const weekRangeText = computed(() => {
  const days = weekDays.value
  if (!days.length) return ''
  return `${days[0].date} ~ ${days[days.length - 1].date}`
})

// 网格数据: key = `${staffId}_${date}` -> { shift, id }
const scheduleMap = ref(new Map<string, { shift: string; id?: number }>())

// 表格多选
const selectedStaffIds = ref<number[]>([])

// 单元格快选菜单
const cellMenu = reactive({ visible: false, x: 0, y: 0, staffId: 0, date: '', shiftId: 0 })

// 批量指定
const bulkVisible = ref(false)
const bulkLoading = ref(false)
const bulkStaffIds = ref<number[]>([])
const bulkDates = ref<string[]>([])
const bulkShift = ref('')

// ===== 日期工具 =====
function pad(n: number) {
  return String(n).padStart(2, '0')
}
function fmt(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
function startOfMonday(anchor: Date): Date {
  const d = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate())
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff)
  return d
}
function buildWeekDays(anchor: Date) {
  const monday = startOfMonday(anchor)
  const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const todayStr = fmt(new Date())
  return weekdays.map((wd, i) => {
    const d = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + i)
    return {
      date: fmt(d),
      weekday: wd,
      monthDay: `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`,
      isToday: fmt(d) === todayStr
    }
  })
}
function moveWeek(offset: number) {
  if (offset === 0) {
    anchorDate.value = new Date()
  } else {
    const d = new Date(anchorDate.value.getFullYear(), anchorDate.value.getMonth(), anchorDate.value.getDate() + offset * 7)
    anchorDate.value = d
  }
  closeCellMenu()
  loadSchedule()
}

// ===== 数据加载 =====
async function loadStaff() {
  try {
    staffList.value = await getAllActiveStaff()
    staffMap.value = new Map(staffList.value.map((s) => [s.id, s]))
  } catch (e) {
    staffList.value = []
  }
}

function staffNameById(id: number): string {
  return staffMap.value.get(id)?.name || ''
}

async function loadSchedule() {
  if (!weekDays.value.length) return
  loading.value = true
  const start = weekDays.value[0].date
  const end = weekDays.value[weekDays.value.length - 1].date
  const map = new Map<string, { shift: string; id?: number }>()
  try {
    const list: any = await getScheduleRange(start, end)
    for (const item of (Array.isArray(list) ? list : [])) {
      map.set(`${item.staffId}_${item.scheduleDate}`, {
        shift: item.shiftType,
        id: item.id
      })
    }
  } catch (e) {
    map.clear()
  } finally {
    scheduleMap.value = map
    loading.value = false
  }
}

function getShift(staffId: number, date: string): string {
  return scheduleMap.value.get(`${staffId}_${date}`)?.shift || ''
}

// ===== 多选 =====
function onSelectionChange(rows: Staff[]) {
  selectedStaffIds.value = rows.map((r) => r.id)
}

// ===== 单元格快选 =====
function openCellMenu(e: MouseEvent, staffId: number, date: string) {
  closeCellMenu()
  const menuW = 140
  const menuH = SHIFTS.length * 34 + 34 + 26
  let x = e.clientX + 8
  let y = e.clientY + 8
  if (x + menuW > window.innerWidth) x = window.innerWidth - menuW - 8
  if (y + menuH > window.innerHeight) y = e.clientY - menuH - 8
  cellMenu.x = x
  cellMenu.y = y
  cellMenu.staffId = staffId
  cellMenu.date = date
  cellMenu.visible = true
}
function closeCellMenu() {
  cellMenu.visible = false
  cellMenu.shiftId = 0
}
async function assignCell(shift: string) {
  const { staffId, date } = cellMenu
  closeCellMenu()
  if (!staffId || !date) return
  try {
    await assignScheduleBatch({ staffIds: [staffId], dates: [date], shiftType: shift })
    ElMessage.success(`已将 ${staffNameById(staffId)} ${date} 指定为${shift}`)
    loadSchedule()
  } catch (e) {
    ElMessage.error('指定失败')
  }
}
async function clearCell() {
  const { staffId, date } = cellMenu
  closeCellMenu()
  const rec = scheduleMap.value.get(`${staffId}_${date}`)
  if (!rec?.id) return
  try {
    await deleteSchedule(rec.id)
    ElMessage.success('已清除排班')
    loadSchedule()
  } catch (e) {
    ElMessage.error('清除失败')
  }
}

// 关闭浮层: 点击其它区域
window.addEventListener('click', () => {
  if (cellMenu.visible) closeCellMenu()
})

// ===== 批量指定 =====
function openBulkAssign() {
  bulkStaffIds.value = [...selectedStaffIds.value]
  bulkDates.value = []
  bulkShift.value = ''
  bulkVisible.value = true
}
async function confirmBulkAssign() {
  if (bulkStaffIds.value.length === 0) {
    ElMessage.warning('请至少选择一名人员')
    return
  }
  if (bulkDates.value.length === 0) {
    ElMessage.warning('请至少选择一个日期')
    return
  }
  if (!bulkShift.value) {
    ElMessage.warning('请选择班次')
    return
  }
  bulkLoading.value = true
  try {
    const count = await assignScheduleBatch({
      staffIds: bulkStaffIds.value,
      dates: bulkDates.value,
      shiftType: bulkShift.value
    })
    ElMessage.success(`已为 ${bulkStaffIds.value.length} 人 × ${bulkDates.value.length} 天 指定${bulkShift.value}，共 ${count} 条`)
    bulkVisible.value = false
    loadSchedule()
  } catch (e) {
    ElMessage.error('批量指定失败')
  } finally {
    bulkLoading.value = false
  }
}

onMounted(() => {
  loadStaff()
  loadSchedule()
})
</script>

<style scoped lang="scss">
.schedule-page {
  padding: 16px;

  .toolbar-card {
    margin-bottom: 12px;

    :deep(.el-card__body) {
      padding: 14px 16px;
    }

    .toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 12px;
    }

    .week-nav {
      display: flex;
      align-items: center;
      gap: 8px;

      .week-range {
        font-size: 14px;
        font-weight: 600;
        color: #303133;
        margin-left: 4px;
      }
    }

    .shift-legend {
      display: flex;
      align-items: center;
      gap: 6px;

      .legend-item {
        margin-right: 2px;
      }
      .legend-tip {
        color: #909399;
        font-size: 12px;
        margin-left: 8px;
      }
    }
  }

  .grid-card {
    :deep(.el-card__body) {
      padding: 0;
    }
    .schedule-table {
      :deep(.el-table__header th) {
        background: #f5f7fa;
      }
    }
  }

  .name-header {
    font-weight: 600;
  }

  .staff-cell {
    display: flex;
    flex-direction: column;
    .staff-name {
      font-weight: 600;
      color: #303133;
    }
    .staff-no {
      font-size: 12px;
      color: #909399;
    }
  }

  .date-header {
    line-height: 1.2;
    .date-weekday {
      font-weight: 600;
      color: #303133;
    }
    .date-num {
      font-size: 12px;
      color: #909399;
      .today-dot {
        color: #1a73e8;
        margin-left: 2px;
      }
    }
  }

  .shift-cell {
    height: 48px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    border-radius: 4px;
    transition: background 0.15s;
    &:hover {
      background: #ecf5ff;
      border: 1px dashed #1a73e8;
    }
    &.is-today {
      background: #f0f9eb;
    }
    .cell-empty {
      color: #c0c4cc;
      font-size: 16px;
    }
  }

  // 单元格快选浮层
  .cell-menu {
    position: fixed;
    z-index: 3000;
    width: 140px;
    background: #fff;
    border: 1px solid #e4e7ed;
    border-radius: 6px;
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
    padding: 6px;
    user-select: none;

    .cell-menu-title {
      font-size: 12px;
      color: #909399;
      padding: 2px 6px 6px;
      border-bottom: 1px solid #f0f2f5;
      margin-bottom: 4px;
    }
    .cell-menu-item {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 8px;
      border-radius: 4px;
      cursor: pointer;
      &:hover {
        background: #f5f7fa;
      }
      &.is-clear {
        color: #f56c6c;
        font-size: 13px;
        margin-top: 2px;
        border-top: 1px solid #f0f2f5;
        padding-top: 8px;
      }
    }
  }
}
</style>