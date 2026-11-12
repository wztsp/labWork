-- ④d 种子：2 条人工重名供应商（source_type=1），规范名同为 'roche'
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9003,'SUP_0003','Roche',1,1,1,'manual A');
INSERT INTO lis_supplier(id, supplier_code, supplier_name, qual_status, source_type, status, remark)
 VALUES (9004,'SUP_0004',' roche ',1,1,1,'manual B');
