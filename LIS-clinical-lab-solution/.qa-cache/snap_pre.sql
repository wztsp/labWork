\echo '=== PRE SNAPSHOT (before re-running fixed script) ==='
SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE') AS tables;
SELECT COUNT(*) AS suppliers FROM lis_supplier;
SELECT COUNT(*) AS reagents FROM lis_reagent;
SELECT COUNT(*) AS stock FROM lis_reagent_stock;
SELECT COUNT(*) AS inbound_items FROM lis_reagent_inbound_item;
\echo '--- fn_supplier_name_norm exists? ---'
SELECT COUNT(*) AS fn_exists FROM pg_proc WHERE proname='fn_supplier_name_norm';
\echo '--- uk_lis_supplier_name_norm exists? ---'
SELECT COUNT(*) AS idx_exists FROM pg_indexes WHERE tablename='lis_supplier' AND indexname='uk_lis_supplier_name_norm';
\echo '--- lis_reagent_stock.warehouse_id nullability ---'
SELECT column_name, is_nullable, column_default FROM information_schema.columns WHERE table_name='lis_reagent_stock' AND column_name='warehouse_id';
\echo '--- lis_reagent_stock 3-col uk ---'
SELECT indexname FROM pg_indexes WHERE tablename='lis_reagent_stock' AND (indexname='uk_lis_reagent_stock_lot' OR indexname='uk_lis_reagent_stock_lot_wh');
\echo '--- lis_reagent NOT NULL columns (no default) ---'
SELECT column_name, column_default FROM information_schema.columns WHERE table_name='lis_reagent' AND is_nullable='NO' ORDER BY column_name;
