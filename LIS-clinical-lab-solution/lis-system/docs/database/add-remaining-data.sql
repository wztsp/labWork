-- Add data to remaining empty tables
INSERT INTO lis_instrument_maintenance_log_item (log_id, item_code, item_name, expected_value, actual_value, result, abnormal_reason) VALUES
(1, 'NEEDLE_CLEAN', 'Needle Cleanliness', 'CLEAN', 'CLEAN', 1, NULL),
(1, 'FLUID_CHECK', 'Fluid Path Check', 'NORMAL', 'NORMAL', 1, NULL),
(1, 'OPTICAL_CAL', 'Optical Calibration', 'PASS', 'PASS', 1, NULL),
(2, 'CUP_REPLACE', 'Reaction Cup Replace', 'NEW', 'NEW', 1, NULL),
(2, 'STIRRER_CLEAN', 'Stirrer Clean', 'CLEAN', 'CLEAN', 1, NULL),
(2, 'TEMP_CHECK', 'Reagent Temp', '2-8C', '4C', 1, NULL);

INSERT INTO lis_instrument_maintenance_task (plan_id, instrument_id, plan_type, scheduled_date, finish_date, operator_id, status, cost, content) VALUES
(1, 1, 'PM', '2026-07-29', NULL, 2, 0, 0.00, 'BC-7500 weekly maintenance pending'),
(2, 2, 'PM', '2026-07-29', NULL, 2, 0, 0.00, 'cobas 8000 weekly maintenance pending'),
(3, 1, 'PM', '2026-08-05', NULL, 2, 0, 0.00, 'BC-7500 next weekly maintenance');
