-- =============================================================================
-- 试剂管理：打印模板（条码标签 + 各类单据）
-- 幂等脚本：可重复执行；默认模板按 template_code 去重，不覆盖已修改的模板
-- =============================================================================

CREATE TABLE IF NOT EXISTS lis_print_template (
    id             BIGSERIAL PRIMARY KEY,
    template_code  VARCHAR(64)  NOT NULL,
    template_name  VARCHAR(128) NOT NULL,
    biz_type       VARCHAR(32)  NOT NULL,
    paper_width    DECIMAL(6,1) NOT NULL DEFAULT 210,
    paper_height   DECIMAL(6,1) NOT NULL DEFAULT 297,
    margin         DECIMAL(5,1) NOT NULL DEFAULT 5,
    template_json  TEXT         NOT NULL,
    is_default     SMALLINT     NOT NULL DEFAULT 0,
    status         SMALLINT     NOT NULL DEFAULT 1,
    remark         VARCHAR(500),
    create_by      VARCHAR(50),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(50),
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE lis_print_template IS '试剂管理打印模板（条码标签与单据）';
COMMENT ON COLUMN lis_print_template.biz_type IS 'REAGENT_LABEL/PURCHASE_APPLY/PURCHASE_ORDER/INBOUND/REQUISITION/STOCK_CHECK/SCRAP_DISPOSAL';
COMMENT ON COLUMN lis_print_template.template_json IS '画布与元素定义的 JSON，坐标单位 mm';
COMMENT ON COLUMN lis_print_template.is_default IS '0否 1是该业务类型的默认模板';

CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_print_template_code ON lis_print_template (template_code);
-- 同一业务类型只允许一个启用的默认模板
CREATE UNIQUE INDEX IF NOT EXISTS uk_lis_print_template_default
    ON lis_print_template (biz_type) WHERE is_default = 1 AND status = 1;
CREATE INDEX IF NOT EXISTS idx_lis_print_template_biz ON lis_print_template (biz_type, status);

-- =============================================================================
-- 默认模板种子（开箱即用，可在页面上继续编辑）
-- =============================================================================

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('LBL_REAGENT_DEFAULT', '试剂条码标签（默认）', 'REAGENT_LABEL', 60, 40, 2, $json$
{
  "version": 1,
  "canvas": { "width": 60, "height": 40, "background": "#ffffff" },
  "elements": [
    { "id": "l1", "type": "field", "x": 0, "y": 1, "w": 56, "h": 4.5, "field": "reagentName", "label": "试剂", "labelWidth": 10, "fontSize": 9 },
    { "id": "l2", "type": "field", "x": 0, "y": 6, "w": 56, "h": 4.5, "field": "lotNo", "label": "批号", "labelWidth": 10, "fontSize": 9 },
    { "id": "l3", "type": "field", "x": 0, "y": 11, "w": 56, "h": 4.5, "field": "expiryDate", "label": "效期", "labelWidth": 10, "fontSize": 9 },
    { "id": "l4", "type": "field", "x": 0, "y": 16, "w": 56, "h": 4.5, "field": "location", "label": "位置", "labelWidth": 10, "fontSize": 9 },
    { "id": "l5", "type": "barcode", "x": 0, "y": 21, "w": 56, "h": 12, "symbology": "CODE128", "value": "{{barcode}}", "showText": true }
  ]
}
$json$, 1, 1, 'system', '默认试剂条码标签：试剂名称/批号/效期/位置 + CODE128')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('PA_DEFAULT', '采购申请单（默认）', 'PURCHASE_APPLY', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂采购申请单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "applyNo", "label": "申请单号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "submitTime", "label": "提交时间", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "applyDeptName", "label": "申请科室", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "applyUserName", "label": "申请人", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "expectDate", "label": "期望到货", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "urgentFlagText", "label": "紧急程度", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 33, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 35, "w": 194, "h": 130, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 10, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 28, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 50, "align": "left" },
        { "field": "specification", "title": "规格", "width": 38, "align": "left" },
        { "field": "quantity", "title": "数量", "width": 18, "align": "right" },
        { "field": "unit", "title": "单位", "width": 14, "align": "center" },
        { "field": "estimatedPrice", "title": "预估单价", "width": 18, "align": "right" },
        { "field": "amount", "title": "金额", "width": 18, "align": "right" }
      ] },
    { "id": "f7", "type": "field", "x": 120, "y": 168, "w": 74, "h": 8, "field": "totalAmount", "label": "合计金额", "labelWidth": 22, "fontSize": 12, "bold": true },
    { "id": "f8", "type": "field", "x": 0, "y": 170, "w": 194, "h": 8, "field": "remark", "label": "备注", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 250, "w": 60, "h": 8, "text": "申请人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 250, "w": 60, "h": 8, "text": "科室负责人签字：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 250, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 262, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认采购申请单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('PO_DEFAULT', '采购订单（默认）', 'PURCHASE_ORDER', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂采购订单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "orderNo", "label": "订单编号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "orderDate", "label": "下单日期", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "supplierName", "label": "供应商", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "expectDate", "label": "期望到货", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "deptName", "label": "请购科室", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "operatorName", "label": "经办人", "labelWidth": 20, "fontSize": 10 },
    { "id": "f7", "type": "field", "x": 0, "y": 32, "w": 97, "h": 6, "field": "applyNo", "label": "来源申请单", "labelWidth": 22, "fontSize": 10 },
    { "id": "f8", "type": "field", "x": 97, "y": 32, "w": 97, "h": 6, "field": "statusText", "label": "订单状态", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 39, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 41, "w": 194, "h": 130, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 10, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 28, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 50, "align": "left" },
        { "field": "specification", "title": "规格", "width": 36, "align": "left" },
        { "field": "quantity", "title": "数量", "width": 16, "align": "right" },
        { "field": "unit", "title": "单位", "width": 12, "align": "center" },
        { "field": "unitPrice", "title": "单价", "width": 20, "align": "right" },
        { "field": "amount", "title": "金额", "width": 22, "align": "right" }
      ] },
    { "id": "f9", "type": "field", "x": 120, "y": 174, "w": 74, "h": 8, "field": "totalAmount", "label": "合计金额", "labelWidth": 22, "fontSize": 12, "bold": true },
    { "id": "f10", "type": "field", "x": 0, "y": 176, "w": 194, "h": 8, "field": "remark", "label": "备注", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 256, "w": 60, "h": 8, "text": "经办人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 256, "w": 60, "h": 8, "text": "供应商确认：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 256, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 268, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认采购订单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('IN_DEFAULT', '试剂入库单（默认）', 'INBOUND', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂入库单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "inboundNo", "label": "入库单号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "createTime", "label": "入库时间", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "supplierName", "label": "供应商", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "orderNo", "label": "采购订单", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "verifyStatusText", "label": "验收状态", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "verifierName", "label": "验收人", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 33, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 35, "w": 194, "h": 150, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 8, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 22, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 32, "align": "left" },
        { "field": "specification", "title": "规格", "width": 24, "align": "left" },
        { "field": "lotNo", "title": "批号", "width": 18, "align": "center" },
        { "field": "barcode", "title": "条码", "width": 24, "align": "center" },
        { "field": "quantity", "title": "数量", "width": 10, "align": "right" },
        { "field": "unit", "title": "单位", "width": 8, "align": "center" },
        { "field": "purchasePrice", "title": "单价", "width": 12, "align": "right" },
        { "field": "amount", "title": "金额", "width": 12, "align": "right" },
        { "field": "expiryDate", "title": "有效期至", "width": 20, "align": "center" }
      ] },
    { "id": "f7", "type": "field", "x": 120, "y": 194, "w": 74, "h": 8, "field": "totalAmount", "label": "合计金额", "labelWidth": 22, "fontSize": 12, "bold": true },
    { "id": "f8", "type": "field", "x": 0, "y": 196, "w": 194, "h": 8, "field": "remark", "label": "备注", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 256, "w": 60, "h": 8, "text": "验收人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 256, "w": 60, "h": 8, "text": "库管员签字：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 256, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 268, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认入库单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('RQ_DEFAULT', '试剂领用/出库单（默认）', 'REQUISITION', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂领用（出库）单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "reqNo", "label": "单据编号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "createTime", "label": "申请时间", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "deptName", "label": "领用科室", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "applyUserName", "label": "领用人", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "reqTypeText", "label": "单据类型", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "approverName", "label": "审核人", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 33, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 35, "w": 194, "h": 140, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 10, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 26, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 44, "align": "left" },
        { "field": "specification", "title": "规格", "width": 32, "align": "left" },
        { "field": "lotNo", "title": "批号", "width": 18, "align": "center" },
        { "field": "expiryDate", "title": "有效期至", "width": 22, "align": "center" },
        { "field": "quantity", "title": "数量", "width": 14, "align": "right" },
        { "field": "unit", "title": "单位", "width": 10, "align": "center" },
        { "field": "amount", "title": "金额", "width": 18, "align": "right" }
      ] },
    { "id": "f7", "type": "field", "x": 120, "y": 184, "w": 74, "h": 8, "field": "totalAmount", "label": "合计金额", "labelWidth": 22, "fontSize": 12, "bold": true },
    { "id": "f8", "type": "field", "x": 0, "y": 186, "w": 194, "h": 8, "field": "reason", "label": "领用原因", "labelWidth": 16, "fontSize": 10 },
    { "id": "f9", "type": "field", "x": 0, "y": 194, "w": 194, "h": 8, "field": "remark", "label": "备注", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 256, "w": 60, "h": 8, "text": "领用人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 256, "w": 60, "h": 8, "text": "发料人签字：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 256, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 268, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认领用/出库单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('SC_DEFAULT', '试剂盘点单（默认）', 'STOCK_CHECK', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂库存盘点单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "checkNo", "label": "盘点单号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "checkDate", "label": "盘点日期", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "warehouse", "label": "盘点仓库", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "operatorName", "label": "盘点人", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "statusText", "label": "盘点状态", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "auditorName", "label": "审核人", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 33, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 35, "w": 194, "h": 140, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 10, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 26, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 44, "align": "left" },
        { "field": "lotNo", "title": "批号", "width": 22, "align": "center" },
        { "field": "bookQty", "title": "账面数量", "width": 24, "align": "right" },
        { "field": "actualQty", "title": "实盘数量", "width": 24, "align": "right" },
        { "field": "diffQty", "title": "差异", "width": 20, "align": "right" },
        { "field": "diffReason", "title": "差异原因", "width": 24, "align": "left" }
      ] },
    { "id": "f7", "type": "field", "x": 0, "y": 184, "w": 194, "h": 8, "field": "remark", "label": "备注", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 256, "w": 60, "h": 8, "text": "盘点人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 256, "w": 60, "h": 8, "text": "监盘人签字：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 256, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 268, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认盘点单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;

INSERT INTO lis_print_template (template_code, template_name, biz_type, paper_width, paper_height, margin, template_json, is_default, status, create_by, remark)
VALUES ('SD_DEFAULT', '试剂报损单（默认）', 'SCRAP_DISPOSAL', 210, 297, 8, $json$
{
  "version": 1,
  "canvas": { "width": 210, "height": 297, "background": "#ffffff" },
  "elements": [
    { "id": "t1", "type": "text", "x": 0, "y": 0, "w": 194, "h": 10, "text": "试剂报损（报废）单", "fontSize": 20, "bold": true, "align": "center" },
    { "id": "f1", "type": "field", "x": 0, "y": 14, "w": 97, "h": 6, "field": "reqNo", "label": "报废单号", "labelWidth": 22, "fontSize": 10 },
    { "id": "f2", "type": "field", "x": 97, "y": 14, "w": 97, "h": 6, "field": "createTime", "label": "登记时间", "labelWidth": 20, "fontSize": 10 },
    { "id": "f3", "type": "field", "x": 0, "y": 20, "w": 97, "h": 6, "field": "deptName", "label": "报损科室", "labelWidth": 22, "fontSize": 10 },
    { "id": "f4", "type": "field", "x": 97, "y": 20, "w": 97, "h": 6, "field": "applyUserName", "label": "经办人", "labelWidth": 20, "fontSize": 10 },
    { "id": "f5", "type": "field", "x": 0, "y": 26, "w": 97, "h": 6, "field": "disposalMethod", "label": "处置方式", "labelWidth": 22, "fontSize": 10 },
    { "id": "f6", "type": "field", "x": 97, "y": 26, "w": 97, "h": 6, "field": "disposalDate", "label": "处置日期", "labelWidth": 20, "fontSize": 10 },
    { "id": "f7", "type": "field", "x": 0, "y": 32, "w": 97, "h": 6, "field": "disposalOrg", "label": "处置单位", "labelWidth": 22, "fontSize": 10 },
    { "id": "f8", "type": "field", "x": 97, "y": 32, "w": 97, "h": 6, "field": "disposalCert", "label": "处置凭证", "labelWidth": 20, "fontSize": 10 },
    { "id": "f9", "type": "field", "x": 0, "y": 38, "w": 97, "h": 6, "field": "hazardText", "label": "危险品", "labelWidth": 22, "fontSize": 10 },
    { "id": "f10", "type": "field", "x": 97, "y": 38, "w": 97, "h": 6, "field": "safetyApproverName", "label": "安全员", "labelWidth": 20, "fontSize": 10 },
    { "id": "ln1", "type": "line", "x": 0, "y": 45, "w": 194, "h": 0, "thickness": 0.3 },
    { "id": "tb1", "type": "table", "x": 0, "y": 47, "w": 194, "h": 130, "rowHeight": 8,
      "columns": [
        { "field": "index", "title": "序号", "width": 10, "align": "center" },
        { "field": "reagentCode", "title": "试剂编码", "width": 28, "align": "left" },
        { "field": "reagentName", "title": "试剂名称", "width": 48, "align": "left" },
        { "field": "lotNo", "title": "批号", "width": 22, "align": "center" },
        { "field": "expiryDate", "title": "有效期至", "width": 22, "align": "center" },
        { "field": "quantity", "title": "数量", "width": 20, "align": "right" },
        { "field": "unit", "title": "单位", "width": 12, "align": "center" },
        { "field": "amount", "title": "金额", "width": 32, "align": "right" }
      ] },
    { "id": "f11", "type": "field", "x": 120, "y": 180, "w": 74, "h": 8, "field": "totalAmount", "label": "合计金额", "labelWidth": 22, "fontSize": 12, "bold": true },
    { "id": "f12", "type": "field", "x": 0, "y": 182, "w": 194, "h": 10, "field": "reason", "label": "报损原因", "labelWidth": 16, "fontSize": 10 },
    { "id": "s1", "type": "text", "x": 0, "y": 256, "w": 60, "h": 8, "text": "经办人签字：", "fontSize": 10, "align": "left" },
    { "id": "s2", "type": "text", "x": 70, "y": 256, "w": 60, "h": 8, "text": "安全员签字：", "fontSize": 10, "align": "left" },
    { "id": "s3", "type": "text", "x": 140, "y": 256, "w": 54, "h": 8, "text": "日期：", "fontSize": 10, "align": "left" },
    { "id": "ft1", "type": "text", "x": 0, "y": 268, "w": 194, "h": 6, "text": "打印时间：{{printTime}}", "fontSize": 9, "align": "center" }
  ]
}
$json$, 1, 1, 'system', '默认报损单，A4 纵向')
ON CONFLICT (template_code) DO NOTHING;
