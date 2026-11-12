-- ④a 种子：1 人工 + 1 草稿（规范名同 'roche diagnostics'）+ 1 存量试剂字符串
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9001,'SUP_0001','Roche Diagnostics',1,1,1,'manual');
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9002,'SUP_MIG_0001',' roche diagnostics'||chr(9),0,2,1,'draft');
INSERT INTO lis_reagent(id, reagent_name, reagent_code, supplier, supplier_id)
 VALUES (90001,'Test Reagent A','REA_90001',' roche diagnostics ',NULL);
