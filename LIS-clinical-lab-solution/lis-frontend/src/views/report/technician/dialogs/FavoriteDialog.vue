<template>
  <el-dialog v-model="visible" title="收藏夹" width="560px" :close-on-click-modal="false" @open="handleOpen">
    <div class="tabs">
      <el-radio-group v-model="activeType" size="small">
        <el-radio-button label="sample">标本收藏</el-radio-button>
        <el-radio-button label="keyword">搜索条件收藏</el-radio-button>
        <el-radio-button label="panel">检验项目收藏</el-radio-button>
      </el-radio-group>
      <el-button type="primary" size="small" style="float: right" :icon="Plus" @click="onAdd">
        新增收藏
      </el-button>
    </div>
    <el-table
      :data="filteredList"
      size="small"
      border
      stripe
      style="margin-top: 12px; max-height: 360px"
    >
      <el-table-column label="#" width="48" type="index" />
      <el-table-column label="收藏名称" prop="name" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" width="96" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.type)">
            {{ typeText(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" />
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="onUse(row)">使用</el-button>
          <el-button size="small" link type="danger" @click="onRemove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增收藏 -->
    <el-dialog
      v-model="addVisible"
      :title="`新增${typeText(activeType)}收藏`"
      width="440px"
      append-to-body
    >
      <el-form :model="addForm" label-width="88px">
        <el-form-item label="收藏名称" required>
          <el-input v-model="addForm.name" placeholder="请输入收藏名称（便于识别）" />
        </el-form-item>
        <el-form-item label="收藏内容">
          <el-tag v-if="activeType === 'sample' && selectedSamples.length" type="info" effect="plain">
            已选 {{ selectedSamples.length }} 个标本
          </el-tag>
          <span v-else-if="activeType === 'sample'" class="text-gray">请在列表中先选中需要收藏的标本</span>
          <el-tag v-else type="info" effect="plain">将使用当前页面条件作为收藏内容</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAdd">保存</el-button>
      </template>
    </el-dialog>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getFavorites, addFavorite, removeFavorite,
  type FavoriteItem, type TechnicianSample
} from '@/api/sample'

const props = defineProps<{
  modelValue: boolean
  selectedSamples: TechnicianSample[]
  currentKeyword?: string
  currentPanels?: string[]
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'use', fav: FavoriteItem): void
}>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })

const activeType = ref<'sample' | 'keyword' | 'panel'>('sample')
const list = ref<FavoriteItem[]>([])
const addVisible = ref(false)
const addForm = reactive({ name: '' })

const filteredList = computed(() => list.value.filter(f => f.type === activeType.value))
function typeText(t: string) {
  return ({ sample: '标本', keyword: '搜索条件', panel: '检验项目' } as any)[t] || t
}
function typeTag(t: string) {
  return ({ sample: '', keyword: 'info', panel: 'warning' } as any)[t] || ''
}

async function handleOpen() {
  try { list.value = await getFavorites() } catch { list.value = [] }
}
function onAdd() {
  if (activeType.value === 'sample' && props.selectedSamples.length === 0) {
    ElMessage.warning('请先在列表中选中需要收藏的标本'); return
  }
  addForm.name = ''; addVisible.value = true
}
async function confirmAdd() {
  if (!addForm.name.trim()) { ElMessage.warning('请输入收藏名称'); return }
  const payload: any = {}
  if (activeType.value === 'sample') payload.sampleIds = props.selectedSamples.map(s => s.id)
  if (activeType.value === 'keyword') payload.keyword = props.currentKeyword
  if (activeType.value === 'panel') payload.panels = props.currentPanels
  try {
    await addFavorite({ name: addForm.name, type: activeType.value, payload })
    ElMessage.success('已加入收藏夹'); addVisible.value = false
    await handleOpen()
  } catch (e: any) { ElMessage.error(e?.message || '加入收藏失败') }
}
async function onRemove(row: FavoriteItem) {
  try {
    await ElMessageBox.confirm(`确认删除收藏 [${row.name}]？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await removeFavorite(row.id)
    ElMessage.success('已删除'); await handleOpen()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}
function onUse(row: FavoriteItem) {
  emit('use', row); visible.value = false
}
</script>
<style scoped>
.text-gray { color: #909399; font-size: 12px }
</style>
