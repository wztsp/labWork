-- ⑥a 种子：先植入已存在的迁移编号 SUP_MIG_0007 + 一条新试剂字符串
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9010,'SUP_MIG_0007','Some Existing Migrated',0,2,1,'preexist');
INSERT INTO lis_reagent(id, reagent_name, reagent_code, supplier, supplier_id)
 VALUES (90002,'Test Reagent B','REA_90002','Brand New Supplier XYZ',NULL);
