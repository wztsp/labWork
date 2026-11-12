-- ② 规范化折叠单测：全角空格(U+3000)/不断行空格(U+00A0)/Tab/大小写 应折叠为同一规范名
SELECT
  fn_supplier_name_norm('罗　氏　诊断') = fn_supplier_name_norm('罗氏诊断') AS fw_space_fold,
  fn_supplier_name_norm('roche'||chr(160)) = fn_supplier_name_norm('roche') AS nbsp_fold,
  fn_supplier_name_norm('roche'||chr(9))   = fn_supplier_name_norm('roche') AS tab_fold,
  fn_supplier_name_norm('ROCHE')           = fn_supplier_name_norm('roche') AS case_fold,
  fn_supplier_name_norm(' roche diagnostics'||chr(9)) = fn_supplier_name_norm('Roche Diagnostics') AS merge_fold;
