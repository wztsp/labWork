-- ③ 唯一索引拦截：④a 归并后 9001('Roche Diagnostics'->'roche diagnostics')占用该规范名
-- 再插入 'ROCHE DIAGNOSTICS'（同样归一 'roche diagnostics'）应被 uk_lis_supplier_name_norm 拒绝
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9099,'SUP_9099','ROCHE DIAGNOSTICS',0,2,1,'should fail');
