-- ⑥a 续号断言：新试剂字符串应生成 SUP_MIG_0008（基于库内 MAX 0007 续号，不撞键）
SELECT supplier_code, supplier_name, source_type
  FROM lis_supplier WHERE supplier_name='Brand New Supplier XYZ';
SELECT supplier_id AS reagent_90002_supplier_id FROM lis_reagent WHERE id=90002;
