<template>
  <div class="order-apply-page">
    <el-card class="form-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>新建检验申请单</span>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="order-form">
        <!-- 患者信息 -->
        <el-divider content-position="left">患者信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="患者姓名" prop="patientName">
              <el-input v-model="form.patientName" placeholder="请输入患者姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="性别" prop="patientGender">
              <el-select v-model="form.patientGender" placeholder="请选择" style="width: 100%">
                <el-option label="男" value="男" />
                <el-option label="女" value="女" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="年龄" prop="patientAge">
              <el-input v-model="form.patientAge" placeholder="请输入年龄">
                <template #append>岁</template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 申请信息 -->
        <el-divider content-position="left">申请信息</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="申请类型" prop="orderType">
              <el-select v-model="form.orderType" placeholder="请选择" style="width: 100%">
                <el-option label="门诊" value="门诊" />
                <el-option label="住院" value="住院" />
                <el-option label="急诊" value="急诊" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="申请科室" prop="applyDept">
              <el-select
                v-model="form.applyDept"
                placeholder="请选择申请科室"
                filterable
                clearable
                style="width: 100%"
              >
                <el-option v-for="opt in departmentOptions" :key="opt.id" :label="opt.label" :value="opt.label" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="申请医生" prop="applyDoctor">
              <el-input v-model="form.applyDoctor" placeholder="请输入申请医生" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="临床诊断" prop="clinicalDiag">
              <el-input v-model="form.clinicalDiag" placeholder="请输入临床诊断" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生理条件" prop="patientCondition">
              <el-select v-model="form.patientCondition" placeholder="如: 空腹/孕妇-孕早期" clearable filterable allow-create style="width: 100%">
                <el-option label="空腹" value="空腹" />
                <el-option label="立位" value="立位" />
                <el-option label="孕妇-孕早期" value="孕妇-孕早期" />
                <el-option label="孕妇-孕中期" value="孕妇-孕中期" />
                <el-option label="孕妇-孕晚期" value="孕妇-孕晚期" />
                <el-option label="女性-卵泡期" value="女性-卵泡期" />
                <el-option label="女性-排卵期" value="女性-排卵期" />
                <el-option label="女性-黄体期" value="女性-黄体期" />
                <el-option label="晨8点" value="晨8点" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 采集方式 -->
        <el-divider content-position="left">采集方式</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="采集方式" prop="collectionMethodId">
              <el-select v-model="form.collectionMethodId" placeholder="请选择采集方式" clearable filterable style="width: 100%" @change="onCollectionMethodChange">
                <el-option
                  v-for="m in collectionMethodOptions"
                  :key="m.id"
                  :label="m.methodName"
                  :value="m.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 检验项目选择 -->
        <el-divider content-position="left">检验项目</el-divider>
        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="选择项目" prop="items">
              <el-select
                v-model="tempSelectedIds"
                multiple
                filterable
                placeholder="搜索并选择检验项目 (支持多选)"
                style="width: 100%"
                :loading="loadingItems"
                @change="onItemsChange"
              >
                <el-option-group
                  v-for="group in groupedItems"
                  :key="group.label"
                  :label="group.label"
                >
                  <el-option
                    v-for="item in group.options"
                    :key="item.id"
                    :label="`${item.itemName} (${item.itemCode})`"
                    :value="item.id"
                  >
                    <div class="item-option">
                      <span>{{ item.itemName }}</span>
                      <span class="item-code">{{ item.itemCode }}</span>
                      <span v-if="item.price" class="item-price">¥{{ item.price }}</span>
                    </div>
                  </el-option>
                </el-option-group>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-button type="primary" plain @click="showItemDialog = true">
              浏览项目列表
            </el-button>
          </el-col>
        </el-row>

        <!-- 已选择项目列表 -->
        <el-table v-if="form.items.length > 0" :data="form.items" border size="small" style="margin-top: 10px">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="testItemCode" label="项目编码" width="120" />
          <el-table-column prop="testItemName" label="项目名称" />
          <el-table-column prop="fee" label="单价(元)" width="100" align="right">
            <template #default="{ row }">
              <el-input-number
                v-model="row.fee"
                :min="0"
                :precision="2"
                :step="1"
                size="small"
                controls-position="right"
                @change="updateTotal"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-row :gutter="20" v-if="form.items.length > 0" style="margin-top: 10px">
          <el-col :span="8" :offset="16" style="text-align: right">
            <span class="total-label">合计: </span>
            <span class="total-amount">¥{{ totalAmount.toFixed(2) }}</span>
          </el-col>
        </el-row>

        <el-form-item v-if="form.items.length === 0" style="margin-top: 10px">
          <el-empty description="请至少选择一个检验项目" :image-size="60" />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 底部操作栏 -->
    <div class="footer-bar">
      <el-button @click="router.back()">返回</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        提交申请
      </el-button>
    </div>

    <!-- 项目浏览弹窗 -->
    <el-dialog v-model="showItemDialog" title="浏览检验项目" width="900px">
      <div class="item-browser">
        <el-input
          v-model="itemSearchText"
          placeholder="搜索项目名称或编码"
          clearable
          style="margin-bottom: 10px"
        />
        <el-table
          :data="filteredItems"
          border
          stripe
          height="400"
          size="small"
          @selection-change="handleBrowserSelectionChange"
        >
          <el-table-column type="selection" width="45" :reserve-selection="true" />
          <el-table-column prop="itemCode" label="项目编码" width="120" />
          <el-table-column prop="itemName" label="项目名称" />
          <el-table-column prop="category" label="分类" width="100" />
          <el-table-column prop="specimenType" label="标本类型" width="100" />
          <el-table-column prop="price" label="价格" width="80" align="right">
            <template #default="{ row }">¥{{ row.price || '0.00' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="showItemDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmBrowserSelection">确认选择</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createOrder, type OrderItemDTO, type OrderCreateDTO } from '@/api/sample'
import { getAllTestItems, getAllCollectionMethods, type TestItem, type CollectionMethod } from '@/api/base'
import { getAllDepartments, type DepartmentOption } from '@/api/system'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const loadingItems = ref(false)

// 表单数据
const form = reactive<OrderCreateDTO>({
  patientName: '',
  patientGender: '',
  patientAge: '',
  orderType: '门诊',
  applyDoctor: '',
  applyDept: '',
  clinicalDiag: '',
  patientCondition: '',
  collectionMethodId: undefined,
  collectionMethodName: '',
  items: []
})

// 临时选择的项目 ID (用于 select 组件, 用原始类型保证显示正确)
const tempSelectedIds = ref<Array<number | string>>([])

// 表单验证规则
const rules: FormRules = {
  patientName: [{ required: true, message: '请输入患者姓名', trigger: 'blur' }],
  patientGender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  patientAge: [{ required: true, message: '请输入年龄', trigger: 'blur' }],
  orderType: [{ required: true, message: '请选择申请类型', trigger: 'change' }],
  applyDoctor: [{ required: true, message: '请输入申请医生', trigger: 'blur' }],
  applyDept: [{ required: true, message: '请输入申请科室', trigger: 'blur' }],
  items: [{ required: true, validator: (_rule: any, value: any, callback: any) => {
    if (!value || value.length === 0) callback(new Error('请选择至少一个检验项目'))
    else callback()
  }, trigger: 'change' }]
}

// 检验项目列表
const allItems = ref<TestItem[]>([])
const collectionMethodOptions = ref<CollectionMethod[]>([])
const showItemDialog = ref(false)
const itemSearchText = ref('')
const browserSelectedItems = ref<TestItem[]>([])
// 申请科室选项 (来自科室管理, 扁平化并保留层级缩进)
const departmentOptions = ref<{ id: number; label: string }[]>([])

// 分组项目列表 (按分类)
const groupedItems = computed(() => {
  const groups: Record<string, TestItem[]> = {}
  for (const item of allItems.value) {
    const category = isGarbled(item.category) ? '其他' : (item.category || '其他')
    if (!groups[category]) groups[category] = []
    groups[category].push(item)
  }
  return Object.entries(groups).map(([label, options]) => ({ label, options }))
})

// 过滤后的项目 (用于浏览弹窗)
const filteredItems = computed(() => {
  const keyword = itemSearchText.value.toLowerCase()
  if (!keyword) return allItems.value
  return allItems.value.filter(item =>
    item.itemName?.toLowerCase().includes(keyword) ||
    item.itemCode?.toLowerCase().includes(keyword)
  )
})

// 计算总价
const totalAmount = computed(() => {
  return form.items.reduce((sum, item) => sum + (item.fee || 0), 0)
})

// 更新总价
function updateTotal() {
  // 自动计算总价
}

/**
 * 判断文本是否为乱码/脏数据
 * - 含占位问号乱码 (如 "?????" )
 * - 含 mojibake 高字节字符 (Latin-1 扩展区, 正常中文项目名不会使用)
 */
function isGarbled(text?: string): boolean {
  if (!text) return true
  const str = String(text)
  // 乱码占位: 大量 '?' 字符
  if (str.includes('?') && ([...str].filter(c => c === '?').length / str.length) > 0.2) {
    return true
  }
  // mojibake / 高位符号: 中文以外的高字节区 (U+0080 - U+00FF)
  // eslint-disable-next-line no-control-regex
  if (/[\u0080-\u00FF]/.test(str)) {
    return true
  }
  return false
}

// 加载检验项目(仅检验项目, 不含检验指标; 并过滤乱码数据)
async function loadTestItems() {
  loadingItems.value = true
  try {
    const items = await getAllTestItems()
    // 只显示: 启用 + 检验项目(组合开单, isPanel=1) + 名称/编码无乱码
    allItems.value = (items || []).filter(item => {
      if (item.status !== 1) return false
      if (Number(item.isPanel) !== 1) return false
      if (isGarbled(item.itemName) || isGarbled(item.itemCode)) return false
      return true
    })
  } catch (e) {
    ElMessage.error('加载检验项目失败')
    allItems.value = []
  } finally {
    loadingItems.value = false
  }
}

// 加载采集方式
async function loadCollectionMethods() {
  try {
    collectionMethodOptions.value = await getAllCollectionMethods()
  } catch {
    collectionMethodOptions.value = []
  }
}

// 采集方式变化
function onCollectionMethodChange(val: number | string | undefined) {
  const method = collectionMethodOptions.value.find(m => m.id == val)
  form.collectionMethodName = method?.methodName || ''
}

// 检验项目选择变化: 直接添加所选检验项目本身(不再展开为检验指标)
function onItemsChange(selectedIds: Array<number | string>) {
  form.items = []
  for (const id of selectedIds) {
    const item = allItems.value.find(i => i.id == id)
    if (!item) continue
    form.items.push({
      testItemId: item.id,
      testItemCode: item.itemCode,
      testItemName: item.itemName,
      fee: item.price || 0
    })
  }
}

// 从已选项目列表删除
function removeItem(index: number) {
  const removed = form.items[index]
  form.items.splice(index, 1)

  // 同步从 select 选中项中移除对应 ID
  const idx = tempSelectedIds.value.findIndex(id => id == removed.testItemId)
  if (idx >= 0) {
    tempSelectedIds.value.splice(idx, 1)
  }
}

// 浏览弹窗选择变化
function handleBrowserSelectionChange(selection: TestItem[]) {
  browserSelectedItems.value = selection
}

// 确认浏览弹窗选择
function confirmBrowserSelection() {
  const existingIds = new Set(tempSelectedIds.value.map(id => String(id)))
  for (const item of browserSelectedItems.value) {
    if (!existingIds.has(String(item.id))) {
      tempSelectedIds.value.push(item.id)
      existingIds.add(String(item.id))
    }
  }
  onItemsChange(tempSelectedIds.value)
  showItemDialog.value = false
  const count = browserSelectedItems.value.length
  browserSelectedItems.value = []
  ElMessage.success(`已添加 ${count} 个项目`)
}

// 提交表单
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (form.items.length === 0) {
    ElMessage.warning('请至少选择一个检验项目')
    return
  }

  submitting.value = true
  try {
    const result = await createOrder({
      patientName: form.patientName,
      patientGender: form.patientGender,
      patientAge: form.patientAge,
      orderType: form.orderType,
      applyDoctor: form.applyDoctor,
      applyDept: form.applyDept,
      clinicalDiag: form.clinicalDiag,
      patientCondition: form.patientCondition,
      collectionMethodId: form.collectionMethodId,
      collectionMethodName: form.collectionMethodName,
      items: form.items
    })

    const orderCount = Array.isArray(result) ? result.length : 1
    ElMessageBox.alert(
      `检验申请提交成功！<br><br>
       <strong>申请数:</strong> ${orderCount} 条<br>
       <strong>项目数:</strong> ${form.items.length} 项<br>
       <strong>合计金额:</strong> ¥${totalAmount.value.toFixed(2)}`,
      '提交成功',
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '前往查看',
        cancelButtonText: '继续新增'
      }
    ).then(() => {
      router.push('/sample/pending')
    }).catch(() => {
      resetForm()
    })
  } catch (e: any) {
    ElMessage.error('提交失败: ' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

// 重置表单
function resetForm() {
  form.patientName = ''
  form.patientGender = ''
  form.patientAge = ''
  form.orderType = '门诊'
  form.applyDoctor = ''
  form.applyDept = ''
  form.clinicalDiag = ''
  form.patientCondition = ''
  form.collectionMethodId = undefined
  form.collectionMethodName = ''
  form.items = []
  tempSelectedIds.value = []
  formRef.value?.resetFields()
}

// 扁平化科室树为下拉选项 (保留层级缩进, 便于区分父子科室)
function flattenDepartments(nodes: DepartmentOption[], prefix = ''): { id: number; label: string }[] {
  const result: { id: number; label: string }[] = []
  for (const node of (nodes || [])) {
    result.push({ id: node.id, label: `${prefix}${node.deptName}` })
    if (node.children && node.children.length) {
      result.push(...flattenDepartments(node.children, `${prefix}　`))
    }
  }
  return result
}

async function loadDepartments() {
  try {
    const res: any = await getAllDepartments()
    departmentOptions.value = flattenDepartments(Array.isArray(res) ? res : [])
  } catch (e) {
    departmentOptions.value = []
  }
}

onMounted(() => {
  loadTestItems()
  loadCollectionMethods()
  loadDepartments()
})
</script>

<style scoped lang="scss">
.order-apply-page {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100%;
}

.form-card {
  margin-bottom: 16px;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
}

.order-form {
  padding: 0 10px;
}

.item-option {
  display: flex;
  align-items: center;
  gap: 8px;
  
  .item-code {
    color: #909399;
    font-size: 12px;
  }
  
  .item-price {
    color: #e6a23c;
    font-size: 12px;
    margin-left: auto;
  }
}

.total-label {
  font-weight: 600;
}

.total-amount {
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
}

.footer-bar {
  background: #fff;
  padding: 16px;
  border-radius: 6px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
}

.item-browser {
  .el-table {
    width: 100%;
  }
}

:deep(.el-divider__text) {
  font-weight: 600;
  color: #303133;
}
</style>
