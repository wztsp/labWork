-- =============================================================================
-- 试剂批次追溯 - 数据库变更脚本
-- 为 lis_result 增加试剂批次字段, 用于检测结果与试剂批号的追溯关联
-- =============================================================================

ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_id BIGINT;       -- 试剂批次库存ID (关联 lis_reagent_stock.id)
ALTER TABLE lis_result ADD COLUMN IF NOT EXISTS reagent_lot_no VARCHAR(50);  -- 试剂批号 (冗余, 便于追溯查询)

COMMENT ON COLUMN lis_result.reagent_lot_id IS '试剂批次库存ID (关联 lis_reagent_stock.id)';
COMMENT ON COLUMN lis_result.reagent_lot_no IS '试剂批号 (冗余, 便于追溯查询)';

CREATE INDEX IF NOT EXISTS idx_lis_result_reagent_lot_no ON lis_result (reagent_lot_no);
CREATE INDEX IF NOT EXISTS idx_lis_result_reagent_lot_id ON lis_result (reagent_lot_id);
