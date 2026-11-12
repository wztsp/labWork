<template>
  <el-dialog v-model="visible" title="批量补打条码" width="600px" :close-on-click-modal="false" @open="handleOpen">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      共选中 <b>{{ samples.length }}</b> 个标本，可根据需要调整打印份数和模板。
    </el-alert>
    <el-form :model="form" label-width="100px">
      <el-form-item label="打印份数">
        <el-input-number v-model="form.copies" :min="1" :max="5" />
      </el-form-item>
      <el-form-item label="打印模板">
        <el-select v-model="form.template" style="width: 260px">
          <el-option label="标准条码标签 (70×40mm)" value="STD_70x40" />
          <el-option label="小标签 (50×30mm)" value="SMALL_50x30" />
          <el-option label="含患者信息标签 (100×60mm)" value="FULL_100x60" />
        </el-select>
      </el-form-item>
      <el-form-item label="打印机选择">
        <el-select v-model="form.printer" style="width: 260px">
          <el-option label="默认系统打印机" value="DEFAULT" />
          <el-option label="条码打印机1 (HP_LaserJet_XXX)" value="PRN001" />
          <el-option label="条码打印机2 (Zebra_XXX)" value="PRN002" />
        </el-select>
      </el-form-item>
    </el-form>
    <div class="section-title">预览标本</div>
    <el-table :data="samples" size="small" border stripe max-height="220">
      <el-table-column label="#" width="42" type="index" />
      <el-table-column label="姓名" prop="patientName" width="80" />
      <el-table-column label="条码号" prop="sampleBarcode" width="120" />
      <el-table-column label="标本号" prop="sampleNo" width="120" show-overflow-tooltip />
      <el-table-column label="检验项目" prop="testItems" min-width="140" show-overflow-tooltip />
    </el-table>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :icon="Printer" :loading="submitting" @click="submit">生成打印任务</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Printer } from '@element-plus/icons-vue'
import { batchReprintBarcode, type TechnicianSample } from '@/api/sample'

const props = defineProps<{ modelValue: boolean; samples: TechnicianSample[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: v => emit('update:modelValue', v) })
const form = reactive({ copies: 1, template: 'STD_70x40', printer: 'DEFAULT' })
const submitting = ref(false)
function handleOpen() { form.copies = 1; form.template = 'STD_70x40'; form.printer = 'DEFAULT' }
async function submit() {
  submitting.value = true
  try {
    await batchReprintBarcode(props.samples.map(s => s.id))
    ElMessage.success(`已提交打印任务：${props.samples.length} 个标本，每份 ${form.copies} 张 (${form.template})`)
    emit('success'); visible.value = false
  } catch (e: any) { ElMessage.error(e?.message || '提交打印失败') }
  finally { submitting.value = false }
}
</script>
<style scoped>
.section-title { font-weight: 600; margin: 12px 0 6px }
</style>
