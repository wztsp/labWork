import { scanQueryBarcode, getReagentPage } from '@/api/reagent'

/**
 * 试剂扫码解析结果（统一结构，供各业务页面追加明细/预填表单）。
 */
export interface ScannedReagent {
  /** 试剂主数据 id（必选，业务单明细依赖） */
  reagentId: number
  reagentName: string
  unit?: string
  price?: number
  /** 命中库存批次时以下字段才有值 */
  fromStock: boolean
  stockId?: number
  batchNo?: string
  lotNo?: string
  expiryDate?: string
  location?: string
}

/**
 * 解析扫码/输入的条码，返回可用的试剂信息。
 * 解析顺序：
 *  1. 库存批次条码（scanQueryBarcode，入库验收生成的批次条码）
 *  2. 试剂编码模糊匹配（getReagentPage.reagentCode）
 *  3. 试剂名称精确匹配（getReagentPage.reagentName）
 * 均未命中返回 null（调用方提示用户）。
 */
export async function resolveScannedCode(code: string): Promise<ScannedReagent | null> {
  const raw = (code || '').trim()
  if (!raw) return null

  // 1) 库存批次条码
  try {
    const stock = await scanQueryBarcode(raw)
    if (stock && stock.reagentId) {
      return {
        reagentId: stock.reagentId,
        reagentName: stock.reagentName || '',
        unit: stock.unit,
        fromStock: true,
        stockId: stock.id,
        batchNo: stock.batchNo || stock.lotNo,
        lotNo: stock.lotNo || stock.batchNo,
        expiryDate: stock.expiryDate,
        location: stock.location
      }
    }
  } catch {
    // 忽略，继续走试剂匹配
  }

  // 2) 试剂编码
  try {
    const byCode = await getReagentPage({ pageNum: 1, pageSize: 5, reagentCode: raw })
    const hitCode = toReagent(byCode.records || [], raw)
    if (hitCode) return hitCode
  } catch {
    // ignore
  }

  // 3) 试剂名称
  try {
    const byName = await getReagentPage({ pageNum: 1, pageSize: 5, reagentName: raw })
    return toReagent(byName.records || [], raw)
  } catch {
    return null
  }
}

function toReagent(list: Array<{ id?: number; reagentCode?: string; reagentName?: string; unit?: string; price?: number }>, raw: string): ScannedReagent | null {
  const exact = list.find((r) => r.reagentCode === raw || r.reagentName === raw)
  const row = exact || list[0]
  if (!row || !row.id) return null
  return {
    reagentId: row.id,
    reagentName: row.reagentName || '',
    unit: row.unit,
    price: row.price,
    fromStock: false
  }
}