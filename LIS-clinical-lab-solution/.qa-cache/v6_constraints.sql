\set ON_ERROR_STOP 0
BEGIN;

\echo ''
\echo '################ T1: lis_supplier.supplier_code UNIQUE ################'
SAVEPOINT sp1;
INSERT INTO lis_supplier(supplier_code, supplier_name) VALUES ('QA_DUP_SUP','QA Supplier A');
\echo '--> T1a first insert should say INSERT 0 1'
INSERT INTO lis_supplier(supplier_code, supplier_name) VALUES ('QA_DUP_SUP','QA Supplier B');
\echo '--> T1b MUST have raised duplicate key error above (uk_lis_supplier_code)'
ROLLBACK TO sp1;

\echo ''
\echo '################ T2: lis_reagent_purchase_apply.apply_no UNIQUE ################'
SAVEPOINT sp2;
INSERT INTO lis_reagent_purchase_apply(apply_no, apply_dept_id) VALUES ('QA_PA_DUP', 1);
\echo '--> T2a first insert should say INSERT 0 1'
INSERT INTO lis_reagent_purchase_apply(apply_no, apply_dept_id) VALUES ('QA_PA_DUP', 2);
\echo '--> T2b MUST have raised duplicate key error above (uk_lis_reagent_purchase_apply_no)'
ROLLBACK TO sp2;

\echo ''
\echo '########## T3: lis_reagent_budget COALESCE(category_id,-1) UNIQUE ##########'
\echo '--- T3-A: category_id = NULL twice (the COALESCE trick; plain UNIQUE would NOT catch this) ---'
SAVEPOINT sp3a;
INSERT INTO lis_reagent_budget(budget_year, dept_id, category_id, total_amount) VALUES (2099, 9901, NULL, 1000);
\echo '--> T3a-1 first NULL-category insert should say INSERT 0 1'
INSERT INTO lis_reagent_budget(budget_year, dept_id, category_id, total_amount) VALUES (2099, 9901, NULL, 2000);
\echo '--> T3a-2 MUST have raised duplicate key error above. If it inserted, COALESCE index is BROKEN.'
ROLLBACK TO sp3a;

\echo '--- T3-B: same non-null category_id twice ---'
SAVEPOINT sp3b;
INSERT INTO lis_reagent_budget(budget_year, dept_id, category_id, total_amount) VALUES (2099, 9902, 1, 1000);
\echo '--> T3b-1 first insert should say INSERT 0 1'
INSERT INTO lis_reagent_budget(budget_year, dept_id, category_id, total_amount) VALUES (2099, 9902, 1, 2000);
\echo '--> T3b-2 MUST have raised duplicate key error above'
ROLLBACK TO sp3b;

\echo '--- T3-C: different category_id under same year+dept must be ALLOWED ---'
SAVEPOINT sp3c;
INSERT INTO lis_reagent_budget(budget_year, dept_id, category_id, total_amount) VALUES (2099, 9903, 1, 1000);
INSERT INTO lis_reagent_budget(budget_year, dept_id, category_id, total_amount) VALUES (2099, 9903, 2, 1000);
\echo '--> T3c both inserts MUST succeed (2 x INSERT 0 1)'
ROLLBACK TO sp3c;

\echo ''
\echo '###### T4: lis_reagent_stock 3-col UNIQUE - THE CORE FIX (defect K) ######'
\echo '--- T4-A: same reagent+lot in DIFFERENT warehouses MUST NOW BE ALLOWED ---'
SAVEPOINT sp4a;
INSERT INTO lis_reagent_stock(reagent_id, lot_no, expiry_date, warehouse_id, quantity) VALUES (9901,'QA_LOT_K','2099-12-31', 1, 10);
INSERT INTO lis_reagent_stock(reagent_id, lot_no, expiry_date, warehouse_id, quantity) VALUES (9901,'QA_LOT_K','2099-12-31', 2, 20);
\echo '--> T4a BOTH MUST succeed. Under the OLD 2-col index the 2nd would crash. This proves defect K is fixed.'
SELECT reagent_id, lot_no, warehouse_id, quantity FROM lis_reagent_stock WHERE reagent_id=9901 ORDER BY warehouse_id;
\echo '--- T4-B: same reagent+lot in the SAME warehouse MUST STILL BE REJECTED ---'
SAVEPOINT sp4b;
INSERT INTO lis_reagent_stock(reagent_id, lot_no, expiry_date, warehouse_id, quantity) VALUES (9901,'QA_LOT_K','2099-12-31', 1, 99);
\echo '--> T4b MUST have raised duplicate key error above (uk_lis_reagent_stock_lot_wh)'
ROLLBACK TO sp4b;
ROLLBACK TO sp4a;

\echo ''
\echo '###### T5: lis_reagent_budget_record idempotency guard UNIQUE ######'
SAVEPOINT sp5;
INSERT INTO lis_reagent_budget_record(budget_id, change_type, amount, source_type, source_id) VALUES (9901, 1, 500, 'APPLY', 777);
\echo '--> T5a first insert should say INSERT 0 1'
INSERT INTO lis_reagent_budget_record(budget_id, change_type, amount, source_type, source_id) VALUES (9901, 1, 500, 'APPLY', 777);
\echo '--> T5b MUST have raised duplicate key error (this is the double-submit protection)'
ROLLBACK TO sp5;

\echo ''
\echo '################ T6: CHECK constraints actually enforce ################'
\echo '--- T6-A: budget negative amount ---'
SAVEPOINT sp6a;
INSERT INTO lis_reagent_budget(budget_year, dept_id, total_amount) VALUES (2098, 9904, -1);
\echo '--> T6a MUST violate ck_lis_reagent_budget_amount'
ROLLBACK TO sp6a;

\echo '--- T6-B: apply_item quantity = 0 ---'
SAVEPOINT sp6b;
INSERT INTO lis_reagent_purchase_apply_item(apply_id, reagent_id, quantity) VALUES (1, 1, 0);
\echo '--> T6b MUST violate ck_lis_reagent_purchase_apply_item_qty'
ROLLBACK TO sp6b;

\echo '--- T6-C: order_item arrived_qty > quantity (over-receipt) ---'
SAVEPOINT sp6c;
INSERT INTO lis_reagent_purchase_order_item(order_id, reagent_id, quantity, arrived_qty) VALUES (1, 1, 10, 11);
\echo '--> T6c MUST violate ck_lis_reagent_purchase_order_item_arrived'
ROLLBACK TO sp6c;

\echo '--- T6-D: requisition_item quantity negative ---'
SAVEPOINT sp6d;
INSERT INTO lis_reagent_requisition_item(req_id, reagent_id, quantity) VALUES (1, 1, -5);
\echo '--> T6d MUST violate ck_lis_reagent_requisition_item_qty'
ROLLBACK TO sp6d;

\echo ''
\echo '#### T7: section-22 supplier migration re-run collision (REAL deploy scenario) ####'
\echo '--- Simulates: script deployed, later NEW legacy supplier appears, script re-run ---'
SAVEPOINT sp7;
UPDATE lis_reagent SET supplier='QA Legacy Supplier ONE' WHERE id=(SELECT MIN(id) FROM lis_reagent);
\echo '--- migration pass 1 ---'
INSERT INTO lis_supplier (supplier_code, supplier_name, qual_status, source_type, status, remark)
SELECT 'SUP_MIG_' || LPAD(CAST(ROW_NUMBER() OVER (ORDER BY s.name) AS VARCHAR), 4, '0'),
       s.name, 0, 2, 1, 'auto-migrated'
FROM (
    SELECT DISTINCT TRIM(supplier) AS name FROM lis_reagent WHERE supplier IS NOT NULL AND TRIM(supplier) <> ''
    UNION
    SELECT DISTINCT TRIM(supplier) AS name FROM lis_reagent_inbound WHERE supplier IS NOT NULL AND TRIM(supplier) <> ''
) s
WHERE NOT EXISTS (SELECT 1 FROM lis_supplier x WHERE x.supplier_name = s.name);
SELECT supplier_code, supplier_name FROM lis_supplier WHERE source_type=2 ORDER BY supplier_code;
\echo '--- now a NEW legacy supplier appears in the source system ---'
UPDATE lis_reagent SET supplier='QA Legacy Supplier TWO' WHERE id=(SELECT MAX(id) FROM lis_reagent);
\echo '--- migration pass 2 (script re-run) ---'
INSERT INTO lis_supplier (supplier_code, supplier_name, qual_status, source_type, status, remark)
SELECT 'SUP_MIG_' || LPAD(CAST(ROW_NUMBER() OVER (ORDER BY s.name) AS VARCHAR), 4, '0'),
       s.name, 0, 2, 1, 'auto-migrated'
FROM (
    SELECT DISTINCT TRIM(supplier) AS name FROM lis_reagent WHERE supplier IS NOT NULL AND TRIM(supplier) <> ''
    UNION
    SELECT DISTINCT TRIM(supplier) AS name FROM lis_reagent_inbound WHERE supplier IS NOT NULL AND TRIM(supplier) <> ''
) s
WHERE NOT EXISTS (SELECT 1 FROM lis_supplier x WHERE x.supplier_name = s.name);
\echo '--> T7: if a duplicate-key error appeared above, section 22 is NOT idempotent under new data'
ROLLBACK TO sp7;

\echo ''
\echo '################ CLEANUP ################'
ROLLBACK;
\echo 'Transaction rolled back - database restored to pre-test state.'

\echo ''
\echo '=== POST-TEST CLEANLINESS: no QA rows must remain ==='
SELECT 'lis_supplier'        AS tbl, COUNT(*) AS qa_rows FROM lis_supplier WHERE supplier_code LIKE 'QA_%' OR supplier_name LIKE 'QA %'
UNION ALL SELECT 'lis_reagent_purchase_apply', COUNT(*) FROM lis_reagent_purchase_apply WHERE apply_no LIKE 'QA_%'
UNION ALL SELECT 'lis_reagent_budget',         COUNT(*) FROM lis_reagent_budget WHERE dept_id >= 9900
UNION ALL SELECT 'lis_reagent_budget_record',  COUNT(*) FROM lis_reagent_budget_record WHERE budget_id >= 9900
UNION ALL SELECT 'lis_reagent_stock(QA)',      COUNT(*) FROM lis_reagent_stock WHERE reagent_id >= 9900
UNION ALL SELECT 'lis_reagent(supplier dirty)',COUNT(*) FROM lis_reagent WHERE supplier LIKE 'QA %';
