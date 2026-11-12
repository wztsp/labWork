SET client_encoding TO 'UTF8';
\echo === inbound.supplier 乱码 ===
SELECT id, supplier, supplier_id FROM lis_reagent_inbound WHERE supplier ~ '[榛鏋寰楂鍛勫織潤愪附熠編鐜枃鍗樻]';
\echo === supplier.supplier_name 乱码候选 ===
SELECT id, supplier_name FROM lis_supplier WHERE supplier_name ~ '[榛鏋寰楂鍛勫織潤愪附熠編鐜枃鍗樻]';
\echo === inbound.supplier 全部 distinct ===
SELECT DISTINCT supplier FROM lis_reagent_inbound ORDER BY supplier;