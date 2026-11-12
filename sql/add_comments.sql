-- ============================================================
-- 数据库表/字段中文备注脚本
-- 适用数据库: lis_db (PostgreSQL)
-- 说明: BATTLECHAR 编码为 UTF-8, 备注均为中文, 不会乱码
-- ============================================================

-- ============ lis_antibiotic (抗生素字典) ============
COMMENT ON TABLE lis_antibiotic IS '抗生素字典表, 维护检验科使用的抗生素基础信息';
COMMENT ON COLUMN lis_antibiotic.id IS '主键ID';
COMMENT ON COLUMN lis_antibiotic.antibiotic_code IS '抗生素编码';
COMMENT ON COLUMN lis_antibiotic.antibiotic_name IS '抗生素名称';
COMMENT ON COLUMN lis_antibiotic.category IS '抗生素类别(如青霉素类/头孢类等)';
COMMENT ON COLUMN lis_antibiotic.unit IS '计量单位(如μg/ml等)';
COMMENT ON COLUMN lis_antibiotic.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_antibiotic.create_time IS '创建时间';

-- ============ lis_antibiotic_breakpoint (抗生素药敏折点) ============
COMMENT ON TABLE lis_antibiotic_breakpoint IS '抗生素药敏折点表, 用于判断药敏结果的敏感/耐药/中介';
COMMENT ON COLUMN lis_antibiotic_breakpoint.id IS '主键ID';
COMMENT ON COLUMN lis_antibiotic_breakpoint.antibiotic_id IS '抗生素ID, 关联lis_antibiotic';
COMMENT ON COLUMN lis_antibiotic_breakpoint.bacteria_id IS '细菌ID, 关联lis_bacteria';
COMMENT ON COLUMN lis_antibiotic_breakpoint.method IS '药敏试验方法(如K-B法/最小抑菌浓度法)';
COMMENT ON COLUMN lis_antibiotic_breakpoint.standard IS '判断标准(如CLSI/EUCAST)';
COMMENT ON COLUMN lis_antibiotic_breakpoint.standard_version IS '判断标准版本号';
COMMENT ON COLUMN lis_antibiotic_breakpoint.s_breakpoint IS '敏感(S)折点值';
COMMENT ON COLUMN lis_antibiotic_breakpoint.r_breakpoint IS '耐药(R)折点值';
COMMENT ON COLUMN lis_antibiotic_breakpoint.i_breakpoint IS '中介(I)折点值';
COMMENT ON COLUMN lis_antibiotic_breakpoint.s_dd IS '敏感抑菌圈直径判读值';
COMMENT ON COLUMN lis_antibiotic_breakpoint.r_dd IS '耐药抑菌圈直径判读值';
COMMENT ON COLUMN lis_antibiotic_breakpoint.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_antibiotic_breakpoint.create_time IS '创建时间';

-- ============ lis_antibiotic_sensitivity (抗生素药敏结果) ============
COMMENT ON TABLE lis_antibiotic_sensitivity IS '标本抗生素药敏试验结果表, 记录每个抗生素对菌株的药敏判定';
COMMENT ON COLUMN lis_antibiotic_sensitivity.id IS '主键ID';
COMMENT ON COLUMN lis_antibiotic_sensitivity.micro_report_id IS '微生物报告ID, 关联lis_micro_report';
COMMENT ON COLUMN lis_antibiotic_sensitivity.organism_id IS '菌株ID, 关联lis_micro_organism';
COMMENT ON COLUMN lis_antibiotic_sensitivity.antibiotic_name IS '抗生素名称';
COMMENT ON COLUMN lis_antibiotic_sensitivity.antibiotic_code IS '抗生素编码';
COMMENT ON COLUMN lis_antibiotic_sensitivity.mic_value IS '最小抑菌浓度(MIC)值';
COMMENT ON COLUMN lis_antibiotic_sensitivity.result IS '药敏结果, S敏感/R耐药/I中介';
COMMENT ON COLUMN lis_antibiotic_sensitivity.breakpoint IS '采用的折点值';
COMMENT ON COLUMN lis_antibiotic_sensitivity.create_time IS '创建时间';

-- ============ lis_bacteria (细菌字典) ============
COMMENT ON TABLE lis_bacteria IS '细菌字典表, 维护临床微生物检测涉及的细菌基础信息';
COMMENT ON COLUMN lis_bacteria.id IS '主键ID';
COMMENT ON COLUMN lis_bacteria.bacteria_code IS '细菌编码';
COMMENT ON COLUMN lis_bacteria.bacteria_name IS '细菌中文名称';
COMMENT ON COLUMN lis_bacteria.bacteria_name_latin IS '细菌拉丁学名';
COMMENT ON COLUMN lis_bacteria.gram_type IS '革兰染色类型, 阳性/阴性';
COMMENT ON COLUMN lis_bacteria.bacteria_category IS '细菌类别';
COMMENT ON COLUMN lis_bacteria.morphology IS '菌落形态特征';
COMMENT ON COLUMN lis_bacteria.is_common IS '是否常见菌, 1是 0否';
COMMENT ON COLUMN lis_bacteria.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_bacteria.create_time IS '创建时间';

-- ============ lis_barcode_print_log (条码打印日志) ============
COMMENT ON TABLE lis_barcode_print_log IS '标本条码打印日志表, 记录条码的打印/补打记录';
COMMENT ON COLUMN lis_barcode_print_log.id IS '主键ID';
COMMENT ON COLUMN lis_barcode_print_log.sample_id IS '标本ID, 关联lis_sample';
COMMENT ON COLUMN lis_barcode_print_log.sample_barcode IS '标本条码号';
COMMENT ON COLUMN lis_barcode_print_log.print_type IS '打印类型, 首次打印/补打';
COMMENT ON COLUMN lis_barcode_print_log.copies IS '打印份数';
COMMENT ON COLUMN lis_barcode_print_log.printer_name IS '打印机名称';
COMMENT ON COLUMN lis_barcode_print_log.reason IS '补打原因';
COMMENT ON COLUMN lis_barcode_print_log.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_barcode_print_log.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_barcode_print_log.print_time IS '打印时间';

-- ============ lis_barcode_rule (条码规则) ============
COMMENT ON TABLE lis_barcode_rule IS '标本条码生成规则表, 定义条码的编码规则';
COMMENT ON COLUMN lis_barcode_rule.id IS '主键ID';
COMMENT ON COLUMN lis_barcode_rule.rule_code IS '规则编码';
COMMENT ON COLUMN lis_barcode_rule.rule_name IS '规则名称';
COMMENT ON COLUMN lis_barcode_rule.prefix IS '条码前缀';
COMMENT ON COLUMN lis_barcode_rule.date_pattern IS '日期格式(纳入条码的日期部分)';
COMMENT ON COLUMN lis_barcode_rule.seq_length IS '序列号长度';
COMMENT ON COLUMN lis_barcode_rule.seq_strategy IS '序列号策略, 按日/按全局自增';
COMMENT ON COLUMN lis_barcode_rule.enabled IS '是否启用, 1启用 0停用';
COMMENT ON COLUMN lis_barcode_rule.remark IS '备注';
COMMENT ON COLUMN lis_barcode_rule.create_time IS '创建时间';
COMMENT ON COLUMN lis_barcode_rule.update_time IS '更新时间';

-- ============ lis_blood_culture (血培养瓶) ============
COMMENT ON TABLE lis_blood_culture IS '血培养瓶记录表, 记录血培养标本的孵育与报警信息';
COMMENT ON COLUMN lis_blood_culture.id IS '主键ID';
COMMENT ON COLUMN lis_blood_culture.sample_id IS '标本ID, 关联lis_sample';
COMMENT ON COLUMN lis_blood_culture.bottle_no IS '培养瓶编号';
COMMENT ON COLUMN lis_blood_culture.medium_type IS '培养瓶培养基类型';
COMMENT ON COLUMN lis_blood_culture.instrument_id IS '血培养仪器ID';
COMMENT ON COLUMN lis_blood_culture.inoculation_time IS '接种时间';
COMMENT ON COLUMN lis_blood_culture.alarm_time IS '报阳报警时间';
COMMENT ON COLUMN lis_blood_culture.final_result IS '最终结果(阳性/阴性)';
COMMENT ON COLUMN lis_blood_culture.final_time IS '最终结果判定时间';
COMMENT ON COLUMN lis_blood_culture.ttp_hours IS '阳性报阳时间(小时,TTP)';
COMMENT ON COLUMN lis_blood_culture.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_blood_culture.status IS '状态, 0待处理/1阳性/2阴性等';
COMMENT ON COLUMN lis_blood_culture.create_time IS '创建时间';

-- ============ lis_blood_culture_subculture (血培养传代记录) ============
COMMENT ON TABLE lis_blood_culture_subculture IS '血培养瓶阳性后继代培养记录表';
COMMENT ON COLUMN lis_blood_culture_subculture.id IS '主键ID';
COMMENT ON COLUMN lis_blood_culture_subculture.blood_culture_id IS '血培养瓶ID, 关联lis_blood_culture';
COMMENT ON COLUMN lis_blood_culture_subculture.subculture_time IS '传代时间';
COMMENT ON COLUMN lis_blood_culture_subculture.medium IS '传代培养基';
COMMENT ON COLUMN lis_blood_culture_subculture.incubator_no IS '孵育箱编号';
COMMENT ON COLUMN lis_blood_culture_subculture.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_blood_culture_subculture.result IS '传代结果描述';
COMMENT ON COLUMN lis_blood_culture_subculture.remark IS '备注';

-- ============ lis_collection_method (采集方式字典) ============
COMMENT ON TABLE lis_collection_method IS '标本采集方式字典表, 如静脉采血/末梢采血等';
COMMENT ON COLUMN lis_collection_method.id IS '主键ID';
COMMENT ON COLUMN lis_collection_method.method_code IS '采集方式编码';
COMMENT ON COLUMN lis_collection_method.method_name IS '采集方式名称';
COMMENT ON COLUMN lis_collection_method.description IS '采集方式说明';
COMMENT ON COLUMN lis_collection_method.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_collection_method.sort_order IS '排序号';
COMMENT ON COLUMN lis_collection_method.create_time IS '创建时间';

-- ============ lis_eqa_record (室间质评记录) ============
COMMENT ON TABLE lis_eqa_record IS '室间质评(EQA)记录表, 记录外部质评的检测结果与评分';
COMMENT ON COLUMN lis_eqa_record.id IS '主键ID';
COMMENT ON COLUMN lis_eqa_record.eqa_batch_no IS '质评批次号';
COMMENT ON COLUMN lis_eqa_record.test_item_id IS '检验项目ID, 关联lis_test_item';
COMMENT ON COLUMN lis_eqa_record.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_eqa_record.target_value IS '靶值(目标值)';
COMMENT ON COLUMN lis_eqa_record.measure_value IS '实测值';
COMMENT ON COLUMN lis_eqa_record.sd IS '标准差';
COMMENT ON COLUMN lis_eqa_record.cv IS '变异系数';
COMMENT ON COLUMN lis_eqa_record.score IS '评分';
COMMENT ON COLUMN lis_eqa_record.grade IS '评价等级';
COMMENT ON COLUMN lis_eqa_record.passed IS '是否合格, 1合格 0不合格';
COMMENT ON COLUMN lis_eqa_record.measure_time IS '检测时间';
COMMENT ON COLUMN lis_eqa_record.result_time IS '结果回报时间';
COMMENT ON COLUMN lis_eqa_record.create_time IS '创建时间';
COMMENT ON COLUMN lis_eqa_record.test_item_name IS '检验项目名称(冗余)';
COMMENT ON COLUMN lis_eqa_record.instrument_name IS '仪器名称(冗余)';

-- ============ lis_group_instrument (实验室组-仪器关联) ============
COMMENT ON TABLE lis_group_instrument IS '实验室/专业组与仪器的关联关系表';
COMMENT ON COLUMN lis_group_instrument.group_id IS '实验室组ID, 关联lis_lab_group';
COMMENT ON COLUMN lis_group_instrument.instrument_id IS '仪器ID, 关联lis_instrument';

-- ============ lis_instrument (检验仪器) ============
COMMENT ON TABLE lis_instrument IS '检验仪器主表, 维护检验科所有仪器设备信息';
COMMENT ON COLUMN lis_instrument.id IS '主键ID';
COMMENT ON COLUMN lis_instrument.instrument_code IS '仪器编码';
COMMENT ON COLUMN lis_instrument.instrument_name IS '仪器名称';
COMMENT ON COLUMN lis_instrument.brand IS '品牌';
COMMENT ON COLUMN lis_instrument.model IS '型号';
COMMENT ON COLUMN lis_instrument.protocol_type IS '通信协议类型(如HL7/厂商私有协议)';
COMMENT ON COLUMN lis_instrument.connect_type IS '连接方式(串口/网络)';
COMMENT ON COLUMN lis_instrument.serial_port IS '串口端口号';
COMMENT ON COLUMN lis_instrument.baud_rate IS '串口波特率';
COMMENT ON COLUMN lis_instrument.ip_address IS '仪器IP地址';
COMMENT ON COLUMN lis_instrument.port IS '仪器端口号';
COMMENT ON COLUMN lis_instrument.status IS '启用状态, 1启用 0停用';
COMMENT ON COLUMN lis_instrument.last_online_time IS '最近在线时间';
COMMENT ON COLUMN lis_instrument.create_time IS '创建时间';
COMMENT ON COLUMN lis_instrument.serial_no IS '仪器序列号';
COMMENT ON COLUMN lis_instrument.install_date IS '安装日期';
COMMENT ON COLUMN lis_instrument.warranty_expiry IS '保修到期日期';
COMMENT ON COLUMN lis_instrument.department IS '所属科室';
COMMENT ON COLUMN lis_instrument.work_status IS '工作状态, 1正常 0故障';
COMMENT ON COLUMN lis_instrument.channel_type IS '通道类型, 用于仪器对接';

-- ============ lis_instrument_calibration (仪器校准记录) ============
COMMENT ON TABLE lis_instrument_calibration IS '仪器校准记录表, 记录仪器的校准历史';
COMMENT ON COLUMN lis_instrument_calibration.id IS '主键ID';
COMMENT ON COLUMN lis_instrument_calibration.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_instrument_calibration.calibration_no IS '校准编号';
COMMENT ON COLUMN lis_instrument_calibration.calibration_type IS '校准类型';
COMMENT ON COLUMN lis_instrument_calibration.calibration_time IS '校准时间';
COMMENT ON COLUMN lis_instrument_calibration.next_calibration_time IS '下次校准时间';
COMMENT ON COLUMN lis_instrument_calibration.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_instrument_calibration.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_instrument_calibration.result IS '校准结果(合格/不合格)';
COMMENT ON COLUMN lis_instrument_calibration.remark IS '备注';
COMMENT ON COLUMN lis_instrument_calibration.create_time IS '创建时间';
COMMENT ON COLUMN lis_instrument_calibration.update_time IS '更新时间';

-- ============ lis_instrument_comm_log (仪器通信日志) ============
COMMENT ON TABLE lis_instrument_comm_log IS '仪器通信日志表, 记录与仪器的原始通信报文';
COMMENT ON COLUMN lis_instrument_comm_log.id IS '主键ID';
COMMENT ON COLUMN lis_instrument_comm_log.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_instrument_comm_log.direction IS '报文方向, 发送/接收';
COMMENT ON COLUMN lis_instrument_comm_log.raw_message IS '原始报文内容';
COMMENT ON COLUMN lis_instrument_comm_log.protocol_type IS '协议类型';
COMMENT ON COLUMN lis_instrument_comm_log.parse_status IS '解析状态';
COMMENT ON COLUMN lis_instrument_comm_log.parse_result IS '解析结果';
COMMENT ON COLUMN lis_instrument_comm_log.message_time IS '报文时间';
COMMENT ON COLUMN lis_instrument_comm_log.create_time IS '创建时间';

-- ============ lis_instrument_item_map (仪器检验项目映射) ============
COMMENT ON TABLE lis_instrument_item_map IS '仪器与检验项目映射表, 含通道码和标本类型用于仪器对接';
COMMENT ON COLUMN lis_instrument_item_map.id IS '主键ID';
COMMENT ON COLUMN lis_instrument_item_map.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_instrument_item_map.test_item_id IS '检验项目ID, 关联lis_test_item';
COMMENT ON COLUMN lis_instrument_item_map.instrument_item_code IS '仪器端项目编码';
COMMENT ON COLUMN lis_instrument_item_map.instrument_item_name IS '仪器端项目名称';
COMMENT ON COLUMN lis_instrument_item_map.specimen_type IS '标本类型';
COMMENT ON COLUMN lis_instrument_item_map.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_instrument_item_map.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_instrument_item_map.channel_code IS '通道码, 用于仪器对接';

-- ============ lis_instrument_maintenance_log (仪器保养日志) ============
COMMENT ON TABLE lis_instrument_maintenance_log IS '仪器保养/维护日志表';
COMMENT ON COLUMN lis_instrument_maintenance_log.id IS '主键ID';
COMMENT ON COLUMN lis_instrument_maintenance_log.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_instrument_maintenance_log.template_id IS '保养模板ID, 关联lis_maintenance_template';
COMMENT ON COLUMN lis_instrument_maintenance_log.maintenance_date IS '保养日期';
COMMENT ON COLUMN lis_instrument_maintenance_log.maintenance_type IS '保养类型(日保/周保/月保等)';
COMMENT ON COLUMN lis_instrument_maintenance_log.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_instrument_maintenance_log.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_instrument_maintenance_log.reviewer_id IS '复核人ID';
COMMENT ON COLUMN lis_instrument_maintenance_log.reviewer_name IS '复核人姓名';
COMMENT ON COLUMN lis_instrument_maintenance_log.result IS '保养结果, 0未完成 1正常 2异常';
COMMENT ON COLUMN lis_instrument_maintenance_log.remark IS '备注';
COMMENT ON COLUMN lis_instrument_maintenance_log.create_time IS '创建时间';

-- ============ lis_instrument_maintenance_log_item (保养日志明细) ============
COMMENT ON TABLE lis_instrument_maintenance_log_item IS '仪器保养日志明细表, 记录每个保养项目的实际值';
COMMENT ON COLUMN lis_instrument_maintenance_log_item.id IS '主键ID';
COMMENT ON COLUMN lis_instrument_maintenance_log_item.log_id IS '保养日志ID, 关联lis_instrument_maintenance_log';
COMMENT ON COLUMN lis_instrument_maintenance_log_item.item_code IS '保养项目编码';
COMMENT ON COLUMN lis_instrument_maintenance_log_item.item_name IS '保养项目名称';
COMMENT ON COLUMN lis_instrument_maintenance_log_item.expected_value IS '期望值';
COMMENT ON COLUMN lis_instrument_maintenance_log_item.actual_value IS '实际值';
COMMENT ON COLUMN lis_instrument_maintenance_log_item.result IS '项目结果, 0未完成 1正常 2异常';
COMMENT ON COLUMN lis_instrument_maintenance_log_item.abnormal_reason IS '异常原因';

-- ============ lis_instrument_maintenance_plan (仪器保养计划) ============
COMMENT ON TABLE lis_instrument_maintenance_plan IS '仪器保养计划表, 定义保养周期与下次保养日期';
COMMENT ON COLUMN lis_instrument_maintenance_plan.id IS '主键ID';
COMMENT ON COLUMN lis_instrument_maintenance_plan.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_instrument_maintenance_plan.plan_type IS '计划类型(日保/周保/月保/季保/年保)';
COMMENT ON COLUMN lis_instrument_maintenance_plan.cycle_type IS '周期类型';
COMMENT ON COLUMN lis_instrument_maintenance_plan.first_date IS '首次保养日期';
COMMENT ON COLUMN lis_instrument_maintenance_plan.next_date IS '下次计划保养日期';
COMMENT ON COLUMN lis_instrument_maintenance_plan.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_instrument_maintenance_plan.remark IS '备注';
COMMENT ON COLUMN lis_instrument_maintenance_plan.create_time IS '创建时间';

-- ============ lis_instrument_maintenance_task (仪器保养任务) ============
COMMENT ON TABLE lis_instrument_maintenance_task IS '仪器保养任务表, 由计划生成的待执行保养任务';
COMMENT ON COLUMN lis_instrument_maintenance_task.id IS '主键ID';
COMMENT ON COLUMN lis_instrument_maintenance_task.plan_id IS '保养计划ID, 关联lis_instrument_maintenance_plan';
COMMENT ON COLUMN lis_instrument_maintenance_task.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_instrument_maintenance_task.plan_type IS '计划类型';
COMMENT ON COLUMN lis_instrument_maintenance_task.scheduled_date IS '计划执行日期';
COMMENT ON COLUMN lis_instrument_maintenance_task.finish_date IS '实际完成日期';
COMMENT ON COLUMN lis_instrument_maintenance_task.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_instrument_maintenance_task.status IS '任务状态, 0待执行 1已完成 2已取消';
COMMENT ON COLUMN lis_instrument_maintenance_task.cost IS '保养费用';
COMMENT ON COLUMN lis_instrument_maintenance_task.content IS '保养内容描述';

-- ============ lis_instrument_repair (仪器维修记录) ============
COMMENT ON TABLE lis_instrument_repair IS '仪器维修记录表, 记录仪器故障与维修全过程';
COMMENT ON COLUMN lis_instrument_repair.id IS '主键ID';
COMMENT ON COLUMN lis_instrument_repair.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_instrument_repair.repair_no IS '维修单号';
COMMENT ON COLUMN lis_instrument_repair.fault_time IS '故障发生时间';
COMMENT ON COLUMN lis_instrument_repair.fault_desc IS '故障描述';
COMMENT ON COLUMN lis_instrument_repair.repair_time IS '开始维修时间';
COMMENT ON COLUMN lis_instrument_repair.finish_time IS '维修完成时间';
COMMENT ON COLUMN lis_instrument_repair.repairman IS '维修人员';
COMMENT ON COLUMN lis_instrument_repair.downtime_hours IS '停机时长(小时)';
COMMENT ON COLUMN lis_instrument_repair.result IS '维修结果(正常/待观察)';
COMMENT ON COLUMN lis_instrument_repair.parts_replaced IS '更换配件';
COMMENT ON COLUMN lis_instrument_repair.cost IS '维修费用';
COMMENT ON COLUMN lis_instrument_repair.remark IS '备注';
COMMENT ON COLUMN lis_instrument_repair.create_time IS '创建时间';
COMMENT ON COLUMN lis_instrument_repair.update_time IS '更新时间';

-- ============ lis_instrument_specimen_map (仪器标本类型映射) ============
COMMENT ON TABLE lis_instrument_specimen_map IS '仪器与标本类型映射表, 定义仪器支持的标本类型';
COMMENT ON COLUMN lis_instrument_specimen_map.id IS '主键ID';
COMMENT ON COLUMN lis_instrument_specimen_map.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_instrument_specimen_map.specimen_type_id IS '标本类型ID, 关联lis_specimen_type';
COMMENT ON COLUMN lis_instrument_specimen_map.specimen_type_name IS '标本类型名称(冗余)';
COMMENT ON COLUMN lis_instrument_specimen_map.specimen_type_code IS '标本类型编码(冗余)';

-- ============ lis_lab_document (实验室文档) ============
COMMENT ON TABLE lis_lab_document IS '实验室文档/文件管理表, 存放SOP等受控文件';
COMMENT ON COLUMN lis_lab_document.id IS '主键ID';
COMMENT ON COLUMN lis_lab_document.doc_code IS '文档编号';
COMMENT ON COLUMN lis_lab_document.doc_name IS '文档名称';
COMMENT ON COLUMN lis_lab_document.doc_type IS '文档类型(SOP/规程/表格等)';
COMMENT ON COLUMN lis_lab_document.version IS '文档版本号';
COMMENT ON COLUMN lis_lab_document.group_id IS '所属实验室组ID, 关联lis_lab_group';
COMMENT ON COLUMN lis_lab_document.file_url IS '文件存储路径';
COMMENT ON COLUMN lis_lab_document.uploader_id IS '上传人ID';
COMMENT ON COLUMN lis_lab_document.upload_time IS '上传时间';
COMMENT ON COLUMN lis_lab_document.status IS '状态, 1启用 0停用';

-- ============ lis_lab_group (实验室/专业组) ============
COMMENT ON TABLE lis_lab_group IS '实验室/专业组表, 如生化组/免疫组/微生物组等';
COMMENT ON COLUMN lis_lab_group.id IS '主键ID';
COMMENT ON COLUMN lis_lab_group.group_code IS '组编码';
COMMENT ON COLUMN lis_lab_group.group_name IS '组名称';
COMMENT ON COLUMN lis_lab_group.parent_id IS '上级组ID, 支持层级结构';
COMMENT ON COLUMN lis_lab_group.leader_id IS '组长ID';
COMMENT ON COLUMN lis_lab_group.leader_name IS '组长姓名';
COMMENT ON COLUMN lis_lab_group.location IS '所在位置';
COMMENT ON COLUMN lis_lab_group.sort_order IS '排序号';
COMMENT ON COLUMN lis_lab_group.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_lab_group.create_time IS '创建时间';

-- ============ lis_lab_order (检验申请单) ============
COMMENT ON TABLE lis_lab_order IS '检验申请单主表, 记录一次检验申请(一个申请对应一个检验项目)';
COMMENT ON COLUMN lis_lab_order.id IS '主键ID';
COMMENT ON COLUMN lis_lab_order.order_no IS '申请单号';
COMMENT ON COLUMN lis_lab_order.visit_id IS '就诊ID, 关联lis_visit';
COMMENT ON COLUMN lis_lab_order.patient_id IS '患者ID, 关联lis_patient';
COMMENT ON COLUMN lis_lab_order.order_type IS '申请单类型, 1门诊 2住院 3急诊';
COMMENT ON COLUMN lis_lab_order.status IS '申请状态, 0待处理/1已接收等';
COMMENT ON COLUMN lis_lab_order.apply_doctor IS '申请医生';
COMMENT ON COLUMN lis_lab_order.apply_dept IS '申请科室';
COMMENT ON COLUMN lis_lab_order.clinical_diag IS '临床诊断';
COMMENT ON COLUMN lis_lab_order.apply_time IS '申请时间';
COMMENT ON COLUMN lis_lab_order.receive_time IS '接收时间';
COMMENT ON COLUMN lis_lab_order.cancel_time IS '取消时间';
COMMENT ON COLUMN lis_lab_order.his_sync_status IS 'HIS同步状态, 0未同步 1已同步';
COMMENT ON COLUMN lis_lab_order.create_time IS '创建时间';
COMMENT ON COLUMN lis_lab_order.collector_id IS '采集人ID';
COMMENT ON COLUMN lis_lab_order.collect_time IS '采集时间';
COMMENT ON COLUMN lis_lab_order.patient_condition IS '患者病情信息';
COMMENT ON COLUMN lis_lab_order.collection_method_id IS '采集方式ID';
COMMENT ON COLUMN lis_lab_order.collection_method_name IS '采集方式名称(冗余)';

-- ============ lis_maintenance_template (保养模板) ============
COMMENT ON TABLE lis_maintenance_template IS '仪器保养模板表, 定义各类保养的检查项目';
COMMENT ON COLUMN lis_maintenance_template.id IS '主键ID';
COMMENT ON COLUMN lis_maintenance_template.template_code IS '模板编码';
COMMENT ON COLUMN lis_maintenance_template.template_name IS '模板名称';
COMMENT ON COLUMN lis_maintenance_template.instrument_type IS '适用仪器类型';
COMMENT ON COLUMN lis_maintenance_template.cycle_type IS '周期类型(日保/周保/月保等)';
COMMENT ON COLUMN lis_maintenance_template.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_maintenance_template.create_time IS '创建时间';

-- ============ lis_maintenance_template_item (保养模板明细) ============
COMMENT ON TABLE lis_maintenance_template_item IS '保养模板项目明细表, 定义保养的具体检查项';
COMMENT ON COLUMN lis_maintenance_template_item.id IS '主键ID';
COMMENT ON COLUMN lis_maintenance_template_item.template_id IS '模板ID, 关联lis_maintenance_template';
COMMENT ON COLUMN lis_maintenance_template_item.item_code IS '检查项目编码';
COMMENT ON COLUMN lis_maintenance_template_item.item_name IS '检查项目名称';
COMMENT ON COLUMN lis_maintenance_template_item.item_type IS '项目类型(读数/操作/清洁等)';
COMMENT ON COLUMN lis_maintenance_template_item.expected_value IS '期望值';
COMMENT ON COLUMN lis_maintenance_template_item.sort_order IS '排序号';

-- ============ lis_micro_organism (微生物培养菌株) ============
COMMENT ON TABLE lis_micro_organism IS '微生物培养鉴定出的菌株信息表';
COMMENT ON COLUMN lis_micro_organism.id IS '主键ID';
COMMENT ON COLUMN lis_micro_organism.micro_report_id IS '微生物报告ID, 关联lis_micro_report';
COMMENT ON COLUMN lis_micro_organism.organism_name IS '菌株名称';
COMMENT ON COLUMN lis_micro_organism.organism_code IS '菌株编码';
COMMENT ON COLUMN lis_micro_organism.quantity IS '菌落数量';
COMMENT ON COLUMN lis_micro_organism.culture_method IS '培养方法';
COMMENT ON COLUMN lis_micro_organism.create_time IS '创建时间';

-- ============ lis_micro_report (微生物检验报告) ============
COMMENT ON TABLE lis_micro_report IS '微生物检验报告主表, 记录微生物培养及鉴定结果';
COMMENT ON COLUMN lis_micro_report.id IS '主键ID';
COMMENT ON COLUMN lis_micro_report.report_no IS '报告编号';
COMMENT ON COLUMN lis_micro_report.sample_id IS '标本ID, 关联lis_sample';
COMMENT ON COLUMN lis_micro_report.patient_id IS '患者ID, 关联lis_patient';
COMMENT ON COLUMN lis_micro_report.patient_name IS '患者姓名(冗余)';
COMMENT ON COLUMN lis_micro_report.specimen_type IS '标本类型';
COMMENT ON COLUMN lis_micro_report.culture_site IS '培养部位';
COMMENT ON COLUMN lis_micro_report.report_status IS '报告状态, 1草稿/2已审核等';
COMMENT ON COLUMN lis_micro_report.auditor_id IS '审核人ID';
COMMENT ON COLUMN lis_micro_report.audit_time IS '审核时间';
COMMENT ON COLUMN lis_micro_report.publish_time IS '发布时间';
COMMENT ON COLUMN lis_micro_report.create_time IS '创建时间';
COMMENT ON COLUMN lis_micro_report.update_time IS '更新时间';
COMMENT ON COLUMN lis_micro_report.gram_stain_result IS '革兰染色结果';
COMMENT ON COLUMN lis_micro_report.gram_stain_remark IS '革兰染色备注';
COMMENT ON COLUMN lis_micro_report.gram_stain_time IS '革兰染色时间';
COMMENT ON COLUMN lis_micro_report.gram_stain_operator IS '革兰染色操作人';
COMMENT ON COLUMN lis_micro_report.is_negative IS '是否阴性, 1阴性 0非阴性';
COMMENT ON COLUMN lis_micro_report.negative_remark IS '阴性结果备注';
COMMENT ON COLUMN lis_micro_report.negative_operator IS '阴性报告操作人';
COMMENT ON COLUMN lis_micro_report.negative_time IS '阴性报告时间';
COMMENT ON COLUMN lis_micro_report.bottle_no IS '培养瓶编号';
COMMENT ON COLUMN lis_micro_report.bottle_type IS '培养瓶类型';
COMMENT ON COLUMN lis_micro_report.volume IS '采血量';
COMMENT ON COLUMN lis_micro_report.inoculate_time IS '接种时间';
COMMENT ON COLUMN lis_micro_report.alarm_time IS '报阳报警时间';
COMMENT ON COLUMN lis_micro_report.ttp_hours IS '阳性报阳时间(小时,TTP)';
COMMENT ON COLUMN lis_micro_report.blood_culture_id IS '血培养瓶ID, 关联lis_blood_culture';
COMMENT ON COLUMN lis_micro_report.blood_culture_result IS '血培养结果';

-- ============ lis_micro_report_print_config (微生物报告打印配置) ============
COMMENT ON TABLE lis_micro_report_print_config IS '微生物报告打印配置表, 控制报告打印内容与版式';
COMMENT ON COLUMN lis_micro_report_print_config.id IS '主键ID';
COMMENT ON COLUMN lis_micro_report_print_config.config_name IS '配置名称';
COMMENT ON COLUMN lis_micro_report_print_config.show_mic IS '是否显示MIC值, 1显示 0不显示';
COMMENT ON COLUMN lis_micro_report_print_config.show_kb IS '是否显示纸片法(K-B)结果, 1显示 0不显示';
COMMENT ON COLUMN lis_micro_report_print_config.show_breakpoint IS '是否显示折点, 1显示 0不显示';
COMMENT ON COLUMN lis_micro_report_print_config.show_method IS '是否显示试验方法, 1显示 0不显示';
COMMENT ON COLUMN lis_micro_report_print_config.hidden_antibiotics IS '隐藏的抗生素(JSON)';
COMMENT ON COLUMN lis_micro_report_print_config.order_by_categories IS '按类别排序规则(JSON)';
COMMENT ON COLUMN lis_micro_report_print_config.breakpoint_version IS '折点版本';
COMMENT ON COLUMN lis_micro_report_print_config.paper_size IS '纸张大小, 默认A4';
COMMENT ON COLUMN lis_micro_report_print_config.is_default IS '是否默认配置, 1是 0否';
COMMENT ON COLUMN lis_micro_report_print_config.create_time IS '创建时间';

-- ============ lis_micro_report_subculture (微生物传代记录) ============
COMMENT ON TABLE lis_micro_report_subculture IS '微生物报告后继代培养记录表';
COMMENT ON COLUMN lis_micro_report_subculture.id IS '主键ID';
COMMENT ON COLUMN lis_micro_report_subculture.micro_report_id IS '微生物报告ID, 关联lis_micro_report';
COMMENT ON COLUMN lis_micro_report_subculture.subculture_time IS '传代时间';
COMMENT ON COLUMN lis_micro_report_subculture.medium IS '传代培养基';
COMMENT ON COLUMN lis_micro_report_subculture.incubator_no IS '孵育箱编号';
COMMENT ON COLUMN lis_micro_report_subculture.result IS '传代结果';
COMMENT ON COLUMN lis_micro_report_subculture.operator IS '操作人';
COMMENT ON COLUMN lis_micro_report_subculture.remark IS '备注';
COMMENT ON COLUMN lis_micro_report_subculture.create_time IS '创建时间';

-- ============ lis_order_item (申请单明细) ============
COMMENT ON TABLE lis_order_item IS '检验申请单明细表, 记录申请单下的检验项目';
COMMENT ON COLUMN lis_order_item.id IS '主键ID';
COMMENT ON COLUMN lis_order_item.order_id IS '申请单ID, 关联lis_lab_order';
COMMENT ON COLUMN lis_order_item.test_item_id IS '检验项目ID, 关联lis_test_item';
COMMENT ON COLUMN lis_order_item.test_item_code IS '检验项目编码(冗余)';
COMMENT ON COLUMN lis_order_item.test_item_name IS '检验项目名称(冗余)';
COMMENT ON COLUMN lis_order_item.status IS '状态, 0待处理/1已完成等';
COMMENT ON COLUMN lis_order_item.fee IS '费用金额';
COMMENT ON COLUMN lis_order_item.create_time IS '创建时间';

-- ============ lis_patient (患者) ============
COMMENT ON TABLE lis_patient IS '患者主表, 存储患者基本信息(敏感信息加密存储)';
COMMENT ON COLUMN lis_patient.id IS '主键ID';
COMMENT ON COLUMN lis_patient.patient_no IS '患者编号';
COMMENT ON COLUMN lis_patient.id_card_enc IS '身份证号(加密存储)';
COMMENT ON COLUMN lis_patient.id_card_hash IS '身份证号哈希值, 用于精确匹配';
COMMENT ON COLUMN lis_patient.name IS '患者姓名';
COMMENT ON COLUMN lis_patient.gender IS '性别, 1男 2女';
COMMENT ON COLUMN lis_patient.birth_date IS '出生日期';
COMMENT ON COLUMN lis_patient.age IS '年龄';
COMMENT ON COLUMN lis_patient.phone_enc IS '手机号(加密存储)';
COMMENT ON COLUMN lis_patient.phone_hash IS '手机号哈希值, 用于精确匹配';
COMMENT ON COLUMN lis_patient.address IS '联系地址';
COMMENT ON COLUMN lis_patient.create_time IS '创建时间';
COMMENT ON COLUMN lis_patient.update_time IS '更新时间';

-- ============ lis_qc_alert (质控预警) ============
COMMENT ON TABLE lis_qc_alert IS '质控预警表, 记录质控失控或异常的预警信息';
COMMENT ON COLUMN lis_qc_alert.id IS '主键ID';
COMMENT ON COLUMN lis_qc_alert.alert_type IS '预警类型';
COMMENT ON COLUMN lis_qc_alert.lot_id IS '质控品批次ID, 关联lis_qc_lot';
COMMENT ON COLUMN lis_qc_alert.test_item_id IS '检验项目ID, 关联lis_test_item';
COMMENT ON COLUMN lis_qc_alert.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_qc_alert.last_measure_value IS '最近质控测定值';
COMMENT ON COLUMN lis_qc_alert.last_z_score IS '最近Z分数';
COMMENT ON COLUMN lis_qc_alert.message IS '预警消息';
COMMENT ON COLUMN lis_qc_alert.status IS '预警状态, PENDING待处理/RESOLVED已处理';
COMMENT ON COLUMN lis_qc_alert.alert_time IS '预警时间';
COMMENT ON COLUMN lis_qc_alert.create_time IS '创建时间';

-- ============ lis_qc_lot (质控品批次) ============
COMMENT ON TABLE lis_qc_lot IS '质控品批次表, 定义质控品各项目的靶值与标准差';
COMMENT ON COLUMN lis_qc_lot.id IS '主键ID';
COMMENT ON COLUMN lis_qc_lot.lot_no IS '质控品批号';
COMMENT ON COLUMN lis_qc_lot.test_item_id IS '检验项目ID, 关联lis_test_item';
COMMENT ON COLUMN lis_qc_lot.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_qc_lot.target_value IS '靶值';
COMMENT ON COLUMN lis_qc_lot.sd IS '标准差';
COMMENT ON COLUMN lis_qc_lot.cv IS '变异系数';
COMMENT ON COLUMN lis_qc_lot.level IS '质控品水平(水平1/2/3)';
COMMENT ON COLUMN lis_qc_lot.start_date IS '生效起始日期';
COMMENT ON COLUMN lis_qc_lot.end_date IS '生效结束日期';
COMMENT ON COLUMN lis_qc_lot.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_qc_lot.create_time IS '创建时间';

-- ============ lis_qc_record (质控实测记录) ============
COMMENT ON TABLE lis_qc_record IS '质控实测记录表, 记录每次质控测定及Westgard规则判定结果';
COMMENT ON COLUMN lis_qc_record.id IS '主键ID';
COMMENT ON COLUMN lis_qc_record.qc_lot_id IS '质控品批次ID, 关联lis_qc_lot';
COMMENT ON COLUMN lis_qc_record.test_item_id IS '检验项目ID, 关联lis_test_item';
COMMENT ON COLUMN lis_qc_record.instrument_id IS '仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_qc_record.measure_value IS '质控测定值';
COMMENT ON COLUMN lis_qc_record.measure_time IS '测定时间';
COMMENT ON COLUMN lis_qc_record.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_qc_record.z_score IS 'Z分数';
COMMENT ON COLUMN lis_qc_record.rule_violation IS '违反的质控规则(如1-3s/2-2s等)';
COMMENT ON COLUMN lis_qc_record.qc_result IS '质控结果, IN_CONTROL在控/OUT_OF_CONTROL失控';
COMMENT ON COLUMN lis_qc_record.create_time IS '创建时间';

-- ============ lis_qc_rule (质控规则) ============
COMMENT ON TABLE lis_qc_rule IS 'Westgard质控规则配置表';
COMMENT ON COLUMN lis_qc_rule.id IS '主键ID';
COMMENT ON COLUMN lis_qc_rule.rule_code IS '规则编码(如1-2s/1-3s等)';
COMMENT ON COLUMN lis_qc_rule.rule_name IS '规则名称';
COMMENT ON COLUMN lis_qc_rule.rule_type IS '规则类型, WARNING警告/REJECT拒绝';
COMMENT ON COLUMN lis_qc_rule.enabled IS '是否启用, 1启用 0停用';
COMMENT ON COLUMN lis_qc_rule.description IS '规则说明';
COMMENT ON COLUMN lis_qc_rule.create_time IS '创建时间';

-- ============ lis_reagent (试剂) ============
COMMENT ON TABLE lis_reagent IS '试剂主表, 维护所有试剂基础信息';
COMMENT ON COLUMN lis_reagent.id IS '主键ID';
COMMENT ON COLUMN lis_reagent.reagent_code IS '试剂编码';
COMMENT ON COLUMN lis_reagent.reagent_name IS '试剂名称';
COMMENT ON COLUMN lis_reagent.specification IS '规格';
COMMENT ON COLUMN lis_reagent.manufacturer IS '生产厂家';
COMMENT ON COLUMN lis_reagent.cas_no IS 'CAS号';
COMMENT ON COLUMN lis_reagent.storage_cond IS '储存条件';
COMMENT ON COLUMN lis_reagent.unit IS '计量单位';
COMMENT ON COLUMN lis_reagent.instrument_id IS '适用仪器ID';
COMMENT ON COLUMN lis_reagent.test_item_id IS '关联检验项目ID';
COMMENT ON COLUMN lis_reagent.safety_stock IS '安全库存量';
COMMENT ON COLUMN lis_reagent.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_reagent.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent.category_id IS '试剂类别ID, 关联lis_reagent_category';
COMMENT ON COLUMN lis_reagent.manufacturer_id IS '生产厂家ID, 关联lis_supplier';
COMMENT ON COLUMN lis_reagent.reg_cert_no IS '注册证号';
COMMENT ON COLUMN lis_reagent.expected_use IS '预期用途';
COMMENT ON COLUMN lis_reagent.price_purchase IS '采购单价';
COMMENT ON COLUMN lis_reagent.price_selling IS '销售单价';
COMMENT ON COLUMN lis_reagent.usage_per_test IS '单次测试用量';
COMMENT ON COLUMN lis_reagent.opened_expiry_days IS '开封后有效天数';
COMMENT ON COLUMN lis_reagent.min_stock IS '最低库存量';
COMMENT ON COLUMN lis_reagent.max_stock IS '最高库存量';
COMMENT ON COLUMN lis_reagent.supplier IS '供应商名称';
COMMENT ON COLUMN lis_reagent.supplier_id IS '供应商ID, 关联lis_supplier';
COMMENT ON COLUMN lis_reagent.hazard_flag IS '是否危险化学品, 1是 0否';
COMMENT ON COLUMN lis_reagent.hazard_class IS '危险品类别';
COMMENT ON COLUMN lis_reagent.msds_summary IS 'MSDS安全说明书摘要';
COMMENT ON COLUMN lis_reagent.msds_url IS 'MSDS文件地址';
COMMENT ON COLUMN lis_reagent.storage_temp_min IS '储存温度下限';
COMMENT ON COLUMN lis_reagent.storage_temp_max IS '储存温度上限';

-- ============ lis_reagent_approval_node (试剂审批节点) ============
COMMENT ON TABLE lis_reagent_approval_node IS '试剂采购审批流程节点配置表';
COMMENT ON COLUMN lis_reagent_approval_node.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_approval_node.biz_type IS '业务类型';
COMMENT ON COLUMN lis_reagent_approval_node.node_level IS '节点级别(1,2,3...)';
COMMENT ON COLUMN lis_reagent_approval_node.node_name IS '节点名称';
COMMENT ON COLUMN lis_reagent_approval_node.trigger_cond IS '触发条件, ALWAYS/OVER_BUDGET等';
COMMENT ON COLUMN lis_reagent_approval_node.approver_role_code IS '审批角色编码';
COMMENT ON COLUMN lis_reagent_approval_node.approver_dept_scope IS '审批部门范围, 1本部门 2全院';
COMMENT ON COLUMN lis_reagent_approval_node.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_reagent_approval_node.remark IS '备注';
COMMENT ON COLUMN lis_reagent_approval_node.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_approval_node.update_time IS '更新时间';

-- ============ lis_reagent_budget (试剂预算) ============
COMMENT ON TABLE lis_reagent_budget IS '试剂年度预算表, 记录各科室各类别试剂预算及使用情况';
COMMENT ON COLUMN lis_reagent_budget.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_budget.budget_no IS '预算编号';
COMMENT ON COLUMN lis_reagent_budget.budget_year IS '预算年度';
COMMENT ON COLUMN lis_reagent_budget.dept_id IS '科室ID';
COMMENT ON COLUMN lis_reagent_budget.dept_name IS '科室名称(冗余)';
COMMENT ON COLUMN lis_reagent_budget.category_id IS '试剂类别ID';
COMMENT ON COLUMN lis_reagent_budget.category_name IS '试剂类别名称(冗余)';
COMMENT ON COLUMN lis_reagent_budget.total_amount IS '预算总额';
COMMENT ON COLUMN lis_reagent_budget.frozen_amount IS '已冻结(审批中)金额';
COMMENT ON COLUMN lis_reagent_budget.used_amount IS '已使用金额';
COMMENT ON COLUMN lis_reagent_budget.warn_ratio IS '预警比例, 默认0.8';
COMMENT ON COLUMN lis_reagent_budget.version IS '乐观锁版本号';
COMMENT ON COLUMN lis_reagent_budget.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_reagent_budget.remark IS '备注';
COMMENT ON COLUMN lis_reagent_budget.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_budget.update_time IS '更新时间';

-- ============ lis_reagent_budget_record (预算变动记录) ============
COMMENT ON TABLE lis_reagent_budget_record IS '试剂预算变动流水表, 记录冻结/使用/释放等变动';
COMMENT ON COLUMN lis_reagent_budget_record.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_budget_record.budget_id IS '预算ID, 关联lis_reagent_budget';
COMMENT ON COLUMN lis_reagent_budget_record.change_type IS '变动类型(冻结/使用/释放等)';
COMMENT ON COLUMN lis_reagent_budget_record.amount IS '变动金额';
COMMENT ON COLUMN lis_reagent_budget_record.before_frozen IS '变动前冻结金额';
COMMENT ON COLUMN lis_reagent_budget_record.after_frozen IS '变动后冻结金额';
COMMENT ON COLUMN lis_reagent_budget_record.before_used IS '变动前已用金额';
COMMENT ON COLUMN lis_reagent_budget_record.after_used IS '变动后已用金额';
COMMENT ON COLUMN lis_reagent_budget_record.source_type IS '来源类型';
COMMENT ON COLUMN lis_reagent_budget_record.source_id IS '来源ID(如申请单ID)';
COMMENT ON COLUMN lis_reagent_budget_record.source_no IS '来源单号';
COMMENT ON COLUMN lis_reagent_budget_record.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_reagent_budget_record.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_reagent_budget_record.remark IS '备注';
COMMENT ON COLUMN lis_reagent_budget_record.create_time IS '创建时间';

-- ============ lis_reagent_category (试剂类别) ============
COMMENT ON TABLE lis_reagent_category IS '试剂类别字典表, 支持树形层级结构';
COMMENT ON COLUMN lis_reagent_category.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_category.category_code IS '类别编码';
COMMENT ON COLUMN lis_reagent_category.category_name IS '类别名称';
COMMENT ON COLUMN lis_reagent_category.parent_id IS '上级类别ID';
COMMENT ON COLUMN lis_reagent_category.path IS '层级路径(如/1/2/3)';
COMMENT ON COLUMN lis_reagent_category.level_no IS '层级深度';
COMMENT ON COLUMN lis_reagent_category.hazard_category IS '危险品类别, 0非危险品';
COMMENT ON COLUMN lis_reagent_category.sort_order IS '排序号';
COMMENT ON COLUMN lis_reagent_category.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_reagent_category.remark IS '备注';
COMMENT ON COLUMN lis_reagent_category.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_category.update_time IS '更新时间';

-- ============ lis_reagent_consumption (试剂消耗) ============
COMMENT ON TABLE lis_reagent_consumption IS '试剂消耗记录表, 记录试剂的使用消耗情况';
COMMENT ON COLUMN lis_reagent_consumption.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_consumption.consumption_no IS '消耗单号';
COMMENT ON COLUMN lis_reagent_consumption.reagent_id IS '试剂ID, 关联lis_reagent';
COMMENT ON COLUMN lis_reagent_consumption.stock_id IS '库存批次ID, 关联lis_reagent_stock';
COMMENT ON COLUMN lis_reagent_consumption.lot_no IS '批号';
COMMENT ON COLUMN lis_reagent_consumption.quantity IS '消耗数量';
COMMENT ON COLUMN lis_reagent_consumption.unit IS '计量单位';
COMMENT ON COLUMN lis_reagent_consumption.consume_type IS '消耗类型, 1检验消耗 2其他消耗';
COMMENT ON COLUMN lis_reagent_consumption.related_no IS '关联单号(如检验报告号)';
COMMENT ON COLUMN lis_reagent_consumption.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_reagent_consumption.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_reagent_consumption.remark IS '备注';
COMMENT ON COLUMN lis_reagent_consumption.create_time IS '创建时间';

-- ============ lis_reagent_inbound (试剂入库) ============
COMMENT ON TABLE lis_reagent_inbound IS '试剂入库单主表, 记录试剂入库及验收信息';
COMMENT ON COLUMN lis_reagent_inbound.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_inbound.inbound_no IS '入库单号';
COMMENT ON COLUMN lis_reagent_inbound.supplier IS '供应商名称';
COMMENT ON COLUMN lis_reagent_inbound.total_amount IS '入库总金额';
COMMENT ON COLUMN lis_reagent_inbound.verify_status IS '验收状态, 0待验收 1已验收';
COMMENT ON COLUMN lis_reagent_inbound.verifier_id IS '验收人ID';
COMMENT ON COLUMN lis_reagent_inbound.verifier_name IS '验收人姓名';
COMMENT ON COLUMN lis_reagent_inbound.verify_time IS '验收时间';
COMMENT ON COLUMN lis_reagent_inbound.remark IS '备注';
COMMENT ON COLUMN lis_reagent_inbound.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_inbound.order_id IS '采购订单ID, 关联lis_reagent_purchase_order';
COMMENT ON COLUMN lis_reagent_inbound.order_no IS '采购订单号(冗余)';
COMMENT ON COLUMN lis_reagent_inbound.supplier_id IS '供应商ID, 关联lis_supplier';

-- ============ lis_reagent_inbound_item (试剂入库明细) ============
COMMENT ON TABLE lis_reagent_inbound_item IS '试剂入库单明细表, 记录每批次入库试剂';
COMMENT ON COLUMN lis_reagent_inbound_item.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_inbound_item.inbound_id IS '入库单ID, 关联lis_reagent_inbound';
COMMENT ON COLUMN lis_reagent_inbound_item.reagent_id IS '试剂ID, 关联lis_reagent';
COMMENT ON COLUMN lis_reagent_inbound_item.lot_no IS '批号';
COMMENT ON COLUMN lis_reagent_inbound_item.barcode IS '试剂条码';
COMMENT ON COLUMN lis_reagent_inbound_item.quantity IS '入库数量';
COMMENT ON COLUMN lis_reagent_inbound_item.unit IS '计量单位';
COMMENT ON COLUMN lis_reagent_inbound_item.purchase_price IS '采购单价';
COMMENT ON COLUMN lis_reagent_inbound_item.amount IS '金额';
COMMENT ON COLUMN lis_reagent_inbound_item.expiry_date IS '有效期至';
COMMENT ON COLUMN lis_reagent_inbound_item.location IS '存放位置';
COMMENT ON COLUMN lis_reagent_inbound_item.remark IS '备注';
COMMENT ON COLUMN lis_reagent_inbound_item.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_inbound_item.order_item_id IS '采购订单明细ID';
COMMENT ON COLUMN lis_reagent_inbound_item.warehouse_id IS '仓库ID, 关联lis_reagent_warehouse';

-- ============ lis_reagent_opening (试剂开封登记) ============
COMMENT ON TABLE lis_reagent_opening IS '试剂开封登记表, 记录试剂开封及开封后有效期';
COMMENT ON COLUMN lis_reagent_opening.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_opening.reagent_id IS '试剂ID, 关联lis_reagent';
COMMENT ON COLUMN lis_reagent_opening.stock_id IS '库存批次ID, 关联lis_reagent_stock';
COMMENT ON COLUMN lis_reagent_opening.lot_no IS '批号';
COMMENT ON COLUMN lis_reagent_opening.open_time IS '开封时间';
COMMENT ON COLUMN lis_reagent_opening.opened_expiry IS '开封后有效期至';
COMMENT ON COLUMN lis_reagent_opening.status IS '状态, 1正常 0已作废';
COMMENT ON COLUMN lis_reagent_opening.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_reagent_opening.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_reagent_opening.remark IS '备注';
COMMENT ON COLUMN lis_reagent_opening.create_time IS '创建时间';

-- ============ lis_reagent_purchase_apply (试剂采购申请) ============
COMMENT ON TABLE lis_reagent_purchase_apply IS '试剂采购申请单主表, 记录采购申请及审批流程';
COMMENT ON COLUMN lis_reagent_purchase_apply.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_purchase_apply.apply_no IS '申请单号';
COMMENT ON COLUMN lis_reagent_purchase_apply.apply_dept_id IS '申请科室ID';
COMMENT ON COLUMN lis_reagent_purchase_apply.apply_dept_name IS '申请科室名称(冗余)';
COMMENT ON COLUMN lis_reagent_purchase_apply.apply_user_id IS '申请人ID';
COMMENT ON COLUMN lis_reagent_purchase_apply.apply_user_name IS '申请人姓名';
COMMENT ON COLUMN lis_reagent_purchase_apply.budget_id IS '关联预算ID, 关联lis_reagent_budget';
COMMENT ON COLUMN lis_reagent_purchase_apply.budget_year IS '预算年度';
COMMENT ON COLUMN lis_reagent_purchase_apply.category_id IS '试剂类别ID';
COMMENT ON COLUMN lis_reagent_purchase_apply.total_amount IS '申请总金额';
COMMENT ON COLUMN lis_reagent_purchase_apply.status IS '申请状态, 0草稿/1审批中/2通过/3驳回等';
COMMENT ON COLUMN lis_reagent_purchase_apply.current_node_level IS '当前审批节点级别';
COMMENT ON COLUMN lis_reagent_purchase_apply.total_node_count IS '审批节点总数';
COMMENT ON COLUMN lis_reagent_purchase_apply.over_budget_flag IS '是否超预算, 1是 0否';
COMMENT ON COLUMN lis_reagent_purchase_apply.excess_amount IS '超预算金额';
COMMENT ON COLUMN lis_reagent_purchase_apply.urgent_flag IS '是否加急, 1是 0否';
COMMENT ON COLUMN lis_reagent_purchase_apply.expect_date IS '期望到货日期';
COMMENT ON COLUMN lis_reagent_purchase_apply.submit_time IS '提交时间';
COMMENT ON COLUMN lis_reagent_purchase_apply.finish_time IS '审批完成时间';
COMMENT ON COLUMN lis_reagent_purchase_apply.remark IS '备注';
COMMENT ON COLUMN lis_reagent_purchase_apply.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_purchase_apply.update_time IS '更新时间';

-- ============ lis_reagent_purchase_apply_item (试剂采购申请明细) ============
COMMENT ON TABLE lis_reagent_purchase_apply_item IS '试剂采购申请单明细表';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.apply_id IS '申请单ID, 关联lis_reagent_purchase_apply';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.reagent_id IS '试剂ID, 关联lis_reagent';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.reagent_code IS '试剂编码(冗余)';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.reagent_name IS '试剂名称(冗余)';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.specification IS '规格';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.category_id IS '试剂类别ID';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.quantity IS '申请数量';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.unit IS '计量单位';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.estimated_price IS '预计单价';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.amount IS '金额';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.remark IS '备注';
COMMENT ON COLUMN lis_reagent_purchase_apply_item.create_time IS '创建时间';

-- ============ lis_reagent_purchase_approval (采购审批记录) ============
COMMENT ON TABLE lis_reagent_purchase_approval IS '试剂采购审批记录表, 记录各节点审批意见';
COMMENT ON COLUMN lis_reagent_purchase_approval.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_purchase_approval.apply_id IS '申请单ID, 关联lis_reagent_purchase_apply';
COMMENT ON COLUMN lis_reagent_purchase_approval.apply_no IS '申请单号(冗余)';
COMMENT ON COLUMN lis_reagent_purchase_approval.node_level IS '审批节点级别';
COMMENT ON COLUMN lis_reagent_purchase_approval.node_name IS '节点名称';
COMMENT ON COLUMN lis_reagent_purchase_approval.action IS '审批动作, 1通过 2驳回';
COMMENT ON COLUMN lis_reagent_purchase_approval.approver_id IS '审批人ID';
COMMENT ON COLUMN lis_reagent_purchase_approval.approver_name IS '审批人姓名';
COMMENT ON COLUMN lis_reagent_purchase_approval.opinion IS '审批意见';
COMMENT ON COLUMN lis_reagent_purchase_approval.approve_time IS '审批时间';
COMMENT ON COLUMN lis_reagent_purchase_approval.round_no IS '审批轮次';
COMMENT ON COLUMN lis_reagent_purchase_approval.create_time IS '创建时间';

-- ============ lis_reagent_purchase_order (试剂采购订单) ============
COMMENT ON TABLE lis_reagent_purchase_order IS '试剂采购订单主表, 由审批通过的申请单转化为订单';
COMMENT ON COLUMN lis_reagent_purchase_order.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_purchase_order.order_no IS '订单号';
COMMENT ON COLUMN lis_reagent_purchase_order.apply_id IS '关联申请单ID';
COMMENT ON COLUMN lis_reagent_purchase_order.apply_no IS '申请单号(冗余)';
COMMENT ON COLUMN lis_reagent_purchase_order.supplier_id IS '供应商ID, 关联lis_supplier';
COMMENT ON COLUMN lis_reagent_purchase_order.supplier_name IS '供应商名称(冗余)';
COMMENT ON COLUMN lis_reagent_purchase_order.dept_id IS '采购科室ID';
COMMENT ON COLUMN lis_reagent_purchase_order.dept_name IS '采购科室名称';
COMMENT ON COLUMN lis_reagent_purchase_order.total_amount IS '订单总金额';
COMMENT ON COLUMN lis_reagent_purchase_order.expect_date IS '期望到货日期';
COMMENT ON COLUMN lis_reagent_purchase_order.order_date IS '下单日期';
COMMENT ON COLUMN lis_reagent_purchase_order.status IS '订单状态, 0待收货/1部分收货/2已完成等';
COMMENT ON COLUMN lis_reagent_purchase_order.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_reagent_purchase_order.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_reagent_purchase_order.remark IS '备注';
COMMENT ON COLUMN lis_reagent_purchase_order.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_purchase_order.update_time IS '更新时间';

-- ============ lis_reagent_purchase_order_item (试剂采购订单明细) ============
COMMENT ON TABLE lis_reagent_purchase_order_item IS '试剂采购订单明细表';
COMMENT ON COLUMN lis_reagent_purchase_order_item.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_purchase_order_item.order_id IS '订单ID, 关联lis_reagent_purchase_order';
COMMENT ON COLUMN lis_reagent_purchase_order_item.apply_item_id IS '申请明细ID';
COMMENT ON COLUMN lis_reagent_purchase_order_item.reagent_id IS '试剂ID, 关联lis_reagent';
COMMENT ON COLUMN lis_reagent_purchase_order_item.reagent_code IS '试剂编码(冗余)';
COMMENT ON COLUMN lis_reagent_purchase_order_item.reagent_name IS '试剂名称(冗余)';
COMMENT ON COLUMN lis_reagent_purchase_order_item.specification IS '规格';
COMMENT ON COLUMN lis_reagent_purchase_order_item.quantity IS '订购数量';
COMMENT ON COLUMN lis_reagent_purchase_order_item.arrived_qty IS '已到货数量';
COMMENT ON COLUMN lis_reagent_purchase_order_item.unit IS '计量单位';
COMMENT ON COLUMN lis_reagent_purchase_order_item.unit_price IS '单价';
COMMENT ON COLUMN lis_reagent_purchase_order_item.amount IS '金额';
COMMENT ON COLUMN lis_reagent_purchase_order_item.remark IS '备注';
COMMENT ON COLUMN lis_reagent_purchase_order_item.create_time IS '创建时间';

-- ============ lis_reagent_requisition (试剂领用申请) ============
COMMENT ON TABLE lis_reagent_requisition IS '试剂领用/退库申请单主表';
COMMENT ON COLUMN lis_reagent_requisition.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_requisition.req_no IS '申请单号';
COMMENT ON COLUMN lis_reagent_requisition.req_type IS '申请类型, 1领用 2退库';
COMMENT ON COLUMN lis_reagent_requisition.dept_id IS '申请科室ID';
COMMENT ON COLUMN lis_reagent_requisition.dept_name IS '申请科室名称(冗余)';
COMMENT ON COLUMN lis_reagent_requisition.apply_user_id IS '申请人ID';
COMMENT ON COLUMN lis_reagent_requisition.apply_user_name IS '申请人姓名';
COMMENT ON COLUMN lis_reagent_requisition.status IS '状态, 0待审批/1已批准/2已执行等';
COMMENT ON COLUMN lis_reagent_requisition.approver_id IS '审批人ID';
COMMENT ON COLUMN lis_reagent_requisition.approver_name IS '审批人姓名';
COMMENT ON COLUMN lis_reagent_requisition.approve_time IS '审批时间';
COMMENT ON COLUMN lis_reagent_requisition.approve_opinion IS '审批意见';
COMMENT ON COLUMN lis_reagent_requisition.executor_id IS '执行人ID';
COMMENT ON COLUMN lis_reagent_requisition.executor_name IS '执行人姓名';
COMMENT ON COLUMN lis_reagent_requisition.execute_time IS '执行时间';
COMMENT ON COLUMN lis_reagent_requisition.from_warehouse_id IS '出库仓库ID';
COMMENT ON COLUMN lis_reagent_requisition.to_warehouse_id IS '入库仓库ID';
COMMENT ON COLUMN lis_reagent_requisition.hazard_flag IS '是否含危险品, 1是 0否';
COMMENT ON COLUMN lis_reagent_requisition.expect_return_date IS '预计归还日期';
COMMENT ON COLUMN lis_reagent_requisition.source_req_id IS '来源申请单ID(退库对应原领用单)';
COMMENT ON COLUMN lis_reagent_requisition.reason IS '领用/退库原因';
COMMENT ON COLUMN lis_reagent_requisition.total_amount IS '总金额';
COMMENT ON COLUMN lis_reagent_requisition.remark IS '备注';
COMMENT ON COLUMN lis_reagent_requisition.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_requisition.update_time IS '更新时间';

-- ============ lis_reagent_requisition_item (试剂领用申请明细) ============
COMMENT ON TABLE lis_reagent_requisition_item IS '试剂领用申请单明细表';
COMMENT ON COLUMN lis_reagent_requisition_item.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_requisition_item.req_id IS '申请单ID, 关联lis_reagent_requisition';
COMMENT ON COLUMN lis_reagent_requisition_item.reagent_id IS '试剂ID, 关联lis_reagent';
COMMENT ON COLUMN lis_reagent_requisition_item.reagent_code IS '试剂编码(冗余)';
COMMENT ON COLUMN lis_reagent_requisition_item.reagent_name IS '试剂名称(冗余)';
COMMENT ON COLUMN lis_reagent_requisition_item.stock_id IS '库存批次ID, 关联lis_reagent_stock';
COMMENT ON COLUMN lis_reagent_requisition_item.lot_no IS '批号';
COMMENT ON COLUMN lis_reagent_requisition_item.expiry_date IS '有效期至';
COMMENT ON COLUMN lis_reagent_requisition_item.quantity IS '数量';
COMMENT ON COLUMN lis_reagent_requisition_item.unit IS '计量单位';
COMMENT ON COLUMN lis_reagent_requisition_item.unit_price IS '单价';
COMMENT ON COLUMN lis_reagent_requisition_item.amount IS '金额';
COMMENT ON COLUMN lis_reagent_requisition_item.hazard_flag IS '是否危险品, 1是 0否';
COMMENT ON COLUMN lis_reagent_requisition_item.remark IS '备注';
COMMENT ON COLUMN lis_reagent_requisition_item.create_time IS '创建时间';

-- ============ lis_reagent_scrap_disposal (试剂报废处置) ============
COMMENT ON TABLE lis_reagent_scrap_disposal IS '试剂报废处置记录表, 记录报废试剂的处置方式';
COMMENT ON COLUMN lis_reagent_scrap_disposal.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_scrap_disposal.req_id IS '关联申请单ID(报废/退库单)';
COMMENT ON COLUMN lis_reagent_scrap_disposal.disposal_method IS '处置方式';
COMMENT ON COLUMN lis_reagent_scrap_disposal.disposal_org IS '处置机构';
COMMENT ON COLUMN lis_reagent_scrap_disposal.disposal_cert IS '处置凭证';
COMMENT ON COLUMN lis_reagent_scrap_disposal.disposal_date IS '处置日期';
COMMENT ON COLUMN lis_reagent_scrap_disposal.hazard_flag IS '是否危险品, 1是 0否';
COMMENT ON COLUMN lis_reagent_scrap_disposal.hazard_class IS '危险品类别';
COMMENT ON COLUMN lis_reagent_scrap_disposal.safety_approver_id IS '安全审批人ID';
COMMENT ON COLUMN lis_reagent_scrap_disposal.safety_approver_name IS '安全审批人姓名';
COMMENT ON COLUMN lis_reagent_scrap_disposal.safety_approve_time IS '安全审批时间';
COMMENT ON COLUMN lis_reagent_scrap_disposal.remark IS '备注';
COMMENT ON COLUMN lis_reagent_scrap_disposal.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_scrap_disposal.update_time IS '更新时间';

-- ============ lis_reagent_stock (试剂库存) ============
COMMENT ON TABLE lis_reagent_stock IS '试剂库存表, 按批次记录试剂库存数量';
COMMENT ON COLUMN lis_reagent_stock.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_stock.reagent_id IS '试剂ID, 关联lis_reagent';
COMMENT ON COLUMN lis_reagent_stock.lot_no IS '批号';
COMMENT ON COLUMN lis_reagent_stock.barcode IS '试剂条码';
COMMENT ON COLUMN lis_reagent_stock.expiry_date IS '有效期至';
COMMENT ON COLUMN lis_reagent_stock.quantity IS '库存数量';
COMMENT ON COLUMN lis_reagent_stock.location IS '存放位置';
COMMENT ON COLUMN lis_reagent_stock.status IS '状态, 1正常 0已作废';
COMMENT ON COLUMN lis_reagent_stock.in_stock_time IS '入库时间';
COMMENT ON COLUMN lis_reagent_stock.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_stock.warehouse_id IS '所属仓库ID, 关联lis_reagent_warehouse';

-- ============ lis_reagent_stock_check (试剂库存盘点) ============
COMMENT ON TABLE lis_reagent_stock_check IS '试剂库存盘点单主表';
COMMENT ON COLUMN lis_reagent_stock_check.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_stock_check.check_no IS '盘点单号';
COMMENT ON COLUMN lis_reagent_stock_check.warehouse IS '仓库名称(冗余)';
COMMENT ON COLUMN lis_reagent_stock_check.check_date IS '盘点日期';
COMMENT ON COLUMN lis_reagent_stock_check.status IS '盘点状态, 0进行中/1已完成等';
COMMENT ON COLUMN lis_reagent_stock_check.operator_id IS '盘点人ID';
COMMENT ON COLUMN lis_reagent_stock_check.operator_name IS '盘点人姓名';
COMMENT ON COLUMN lis_reagent_stock_check.remark IS '备注';
COMMENT ON COLUMN lis_reagent_stock_check.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_stock_check.warehouse_id IS '仓库ID, 关联lis_reagent_warehouse';

-- ============ lis_reagent_stock_check_item (试剂库存盘点明细) ============
COMMENT ON TABLE lis_reagent_stock_check_item IS '试剂库存盘点单明细表, 记录各批次账面与实盘数量';
COMMENT ON COLUMN lis_reagent_stock_check_item.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_stock_check_item.check_id IS '盘点单ID, 关联lis_reagent_stock_check';
COMMENT ON COLUMN lis_reagent_stock_check_item.stock_id IS '库存批次ID, 关联lis_reagent_stock';
COMMENT ON COLUMN lis_reagent_stock_check_item.reagent_id IS '试剂ID, 关联lis_reagent';
COMMENT ON COLUMN lis_reagent_stock_check_item.lot_no IS '批号';
COMMENT ON COLUMN lis_reagent_stock_check_item.book_qty IS '账面数量';
COMMENT ON COLUMN lis_reagent_stock_check_item.actual_qty IS '实盘数量';
COMMENT ON COLUMN lis_reagent_stock_check_item.diff_qty IS '差异数量';
COMMENT ON COLUMN lis_reagent_stock_check_item.remark IS '备注';
COMMENT ON COLUMN lis_reagent_stock_check_item.create_time IS '创建时间';

-- ============ lis_reagent_stock_record (试剂库存流水) ============
COMMENT ON TABLE lis_reagent_stock_record IS '试剂库存变动流水表, 记录每次出入库变动';
COMMENT ON COLUMN lis_reagent_stock_record.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_stock_record.stock_id IS '库存批次ID, 关联lis_reagent_stock';
COMMENT ON COLUMN lis_reagent_stock_record.reagent_id IS '试剂ID, 关联lis_reagent';
COMMENT ON COLUMN lis_reagent_stock_record.record_type IS '变动类型, 1入库/2出库/3盘点调整等';
COMMENT ON COLUMN lis_reagent_stock_record.quantity IS '变动数量';
COMMENT ON COLUMN lis_reagent_stock_record.before_qty IS '变动前数量';
COMMENT ON COLUMN lis_reagent_stock_record.after_qty IS '变动后数量';
COMMENT ON COLUMN lis_reagent_stock_record.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_reagent_stock_record.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_reagent_stock_record.source_no IS '来源单号';
COMMENT ON COLUMN lis_reagent_stock_record.related_id IS '关联业务ID';
COMMENT ON COLUMN lis_reagent_stock_record.related_type IS '关联业务类型';
COMMENT ON COLUMN lis_reagent_stock_record.remark IS '备注';
COMMENT ON COLUMN lis_reagent_stock_record.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_stock_record.dept_id IS '科室ID';
COMMENT ON COLUMN lis_reagent_stock_record.dept_name IS '科室名称(冗余)';

-- ============ lis_reagent_warehouse (试剂仓库) ============
COMMENT ON TABLE lis_reagent_warehouse IS '试剂仓库表, 支持多级仓库结构';
COMMENT ON COLUMN lis_reagent_warehouse.id IS '主键ID';
COMMENT ON COLUMN lis_reagent_warehouse.wh_code IS '仓库编码';
COMMENT ON COLUMN lis_reagent_warehouse.wh_name IS '仓库名称';
COMMENT ON COLUMN lis_reagent_warehouse.parent_id IS '上级仓库ID';
COMMENT ON COLUMN lis_reagent_warehouse.path IS '层级路径(如/1/2/3)';
COMMENT ON COLUMN lis_reagent_warehouse.wh_type IS '仓库类型, 1中心库 2科室库';
COMMENT ON COLUMN lis_reagent_warehouse.dept_id IS '所属科室ID';
COMMENT ON COLUMN lis_reagent_warehouse.dept_name IS '所属科室名称(冗余)';
COMMENT ON COLUMN lis_reagent_warehouse.temp_min IS '环境温度下限';
COMMENT ON COLUMN lis_reagent_warehouse.temp_max IS '环境温度上限';
COMMENT ON COLUMN lis_reagent_warehouse.location_desc IS '位置描述';
COMMENT ON COLUMN lis_reagent_warehouse.sort_order IS '排序号';
COMMENT ON COLUMN lis_reagent_warehouse.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_reagent_warehouse.remark IS '备注';
COMMENT ON COLUMN lis_reagent_warehouse.create_time IS '创建时间';
COMMENT ON COLUMN lis_reagent_warehouse.update_time IS '更新时间';

-- ============ lis_reference_range (参考范围) ============
COMMENT ON TABLE lis_reference_range IS '检验项目参考范围表, 按性别/年龄/标本类型/仪器定义参考区间';
COMMENT ON COLUMN lis_reference_range.id IS '主键ID';
COMMENT ON COLUMN lis_reference_range.test_item_id IS '检验项目ID, 关联lis_test_item';
COMMENT ON COLUMN lis_reference_range.gender IS '适用性别, 1男 2女 空为不限';
COMMENT ON COLUMN lis_reference_range.age_min IS '适用年龄下限';
COMMENT ON COLUMN lis_reference_range.age_max IS '适用年龄上限';
COMMENT ON COLUMN lis_reference_range.ref_low IS '参考范围下限';
COMMENT ON COLUMN lis_reference_range.ref_high IS '参考范围上限';
COMMENT ON COLUMN lis_reference_range.ref_text IS '参考范围文本描述';
COMMENT ON COLUMN lis_reference_range.critical_low IS '危急值下限';
COMMENT ON COLUMN lis_reference_range.critical_high IS '危急值上限';
COMMENT ON COLUMN lis_reference_range.unit IS '单位';
COMMENT ON COLUMN lis_reference_range.specimen_type IS '标本类型';
COMMENT ON COLUMN lis_reference_range.instrument_id IS '适用仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_reference_range.condition_remark IS '匹配条件备注, 用于精确匹配';

-- ============ lis_report (检验报告) ============
COMMENT ON TABLE lis_report IS '检验报告主表, 记录报告审核发布状态';
COMMENT ON COLUMN lis_report.id IS '主键ID';
COMMENT ON COLUMN lis_report.report_no IS '报告编号';
COMMENT ON COLUMN lis_report.sample_id IS '标本ID, 关联lis_sample';
COMMENT ON COLUMN lis_report.patient_id IS '患者ID, 关联lis_patient';
COMMENT ON COLUMN lis_report.patient_name IS '患者姓名(冗余)';
COMMENT ON COLUMN lis_report.report_type IS '报告类型';
COMMENT ON COLUMN lis_report.status IS '报告状态, 0待审核/1已审核/2已终审等';
COMMENT ON COLUMN lis_report.auditor_id IS '审核人ID';
COMMENT ON COLUMN lis_report.audit_time IS '审核时间';
COMMENT ON COLUMN lis_report.publisher_id IS '发布人ID';
COMMENT ON COLUMN lis_report.publish_time IS '发布时间';
COMMENT ON COLUMN lis_report.report_doctor IS '报告医生';
COMMENT ON COLUMN lis_report.checker IS '复核人';
COMMENT ON COLUMN lis_report.pdf_path IS 'PDF报告文件路径';
COMMENT ON COLUMN lis_report.his_sync_status IS 'HIS同步状态, 0未同步 1已同步';
COMMENT ON COLUMN lis_report.his_sync_time IS 'HIS同步时间';
COMMENT ON COLUMN lis_report.create_time IS '创建时间';
COMMENT ON COLUMN lis_report.update_time IS '更新时间';
COMMENT ON COLUMN lis_report.patient_gender IS '患者性别(冗余)';
COMMENT ON COLUMN lis_report.patient_age IS '患者年龄(冗余)';

-- ============ lis_report_modify_log (报告修改日志) ============
COMMENT ON TABLE lis_report_modify_log IS '报告结果修改日志表, 记录审核后的结果修改';
COMMENT ON COLUMN lis_report_modify_log.id IS '主键ID';
COMMENT ON COLUMN lis_report_modify_log.report_id IS '报告ID, 关联lis_report';
COMMENT ON COLUMN lis_report_modify_log.item_id IS '检验项目ID, 关联lis_test_item';
COMMENT ON COLUMN lis_report_modify_log.old_value IS '修改前结果值';
COMMENT ON COLUMN lis_report_modify_log.new_value IS '修改后结果值';
COMMENT ON COLUMN lis_report_modify_log.modify_reason IS '修改原因';
COMMENT ON COLUMN lis_report_modify_log.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_report_modify_log.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_report_modify_log.modify_time IS '修改时间';

-- ============ lis_report_template (报告模板) ============
COMMENT ON TABLE lis_report_template IS '检验报告打印模板配置表';
COMMENT ON COLUMN lis_report_template.id IS '主键ID';
COMMENT ON COLUMN lis_report_template.template_name IS '模板名称';
COMMENT ON COLUMN lis_report_template.instrument_id IS '适用仪器ID';
COMMENT ON COLUMN lis_report_template.instrument_name IS '仪器名称(冗余)';
COMMENT ON COLUMN lis_report_template.template_config IS '模板配置(JSON)';
COMMENT ON COLUMN lis_report_template.dual_column IS '是否双栏, 1是 0否';
COMMENT ON COLUMN lis_report_template.paper_size IS '纸张大小, 默认A4';
COMMENT ON COLUMN lis_report_template.orientation IS '纸张方向, portrait纵向/landscape横向';
COMMENT ON COLUMN lis_report_template.margin IS '页边距, 默认10';
COMMENT ON COLUMN lis_report_template.is_default IS '是否默认模板, 1是 0否';
COMMENT ON COLUMN lis_report_template.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_report_template.create_time IS '创建时间';
COMMENT ON COLUMN lis_report_template.update_time IS '更新时间';

-- ============ lis_resistance_rule (耐药规则) ============
COMMENT ON TABLE lis_resistance_rule IS '耐药监测规则表, 定义特殊耐药菌的预警规则';
COMMENT ON COLUMN lis_resistance_rule.id IS '主键ID';
COMMENT ON COLUMN lis_resistance_rule.organism_code IS '菌株编码';
COMMENT ON COLUMN lis_resistance_rule.antibiotic_codes IS '耐药抗生素编码列表';
COMMENT ON COLUMN lis_resistance_rule.rule_name IS '规则名称';
COMMENT ON COLUMN lis_resistance_rule.is_critical IS '是否危急警报, 1是 0否';
COMMENT ON COLUMN lis_resistance_rule.alert_message IS '预警消息';
COMMENT ON COLUMN lis_resistance_rule.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_resistance_rule.create_time IS '创建时间';

-- ============ lis_result (检验结果) ============
COMMENT ON TABLE lis_result IS '检验结果主表, 记录每个标本各检验指标的检测结果';
COMMENT ON COLUMN lis_result.id IS '主键ID';
COMMENT ON COLUMN lis_result.sample_id IS '标本ID, 关联lis_sample';
COMMENT ON COLUMN lis_result.test_item_id IS '检验指标ID, 关联lis_test_item';
COMMENT ON COLUMN lis_result.result_value IS '检验结果数值';
COMMENT ON COLUMN lis_result.result_text IS '检验结果文本描述';
COMMENT ON COLUMN lis_result.unit IS '结果单位';
COMMENT ON COLUMN lis_result.ref_range IS '参考范围(冗余)';
COMMENT ON COLUMN lis_result.abnormal_flag IS '异常标志(如H高/L低/N正常)';
COMMENT ON COLUMN lis_result.is_critical IS '是否危急值, 1是 0否';
COMMENT ON COLUMN lis_result.instrument_id IS '检测仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_result.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_result.test_time IS '检测时间';
COMMENT ON COLUMN lis_result.audit_status IS '审核状态, 0未审/1已审';
COMMENT ON COLUMN lis_result.auditor_id IS '审核人ID';
COMMENT ON COLUMN lis_result.audit_time IS '审核时间';
COMMENT ON COLUMN lis_result.create_time IS '创建时间';
COMMENT ON COLUMN lis_result.update_time IS '更新时间';
COMMENT ON COLUMN lis_result.reagent_lot_id IS '试剂批次ID, 关联lis_reagent_stock';
COMMENT ON COLUMN lis_result.reagent_lot_no IS '试剂批号(冗余)';

-- ============ lis_sample (标本) ============
COMMENT ON TABLE lis_sample IS '标本主表, 记录标本采集/送检/签收/核收/检测全流程状态';
COMMENT ON COLUMN lis_sample.id IS '主键ID';
COMMENT ON COLUMN lis_sample.sample_barcode IS '标本条码号';
COMMENT ON COLUMN lis_sample.order_id IS '申请单ID, 关联lis_lab_order';
COMMENT ON COLUMN lis_sample.patient_id IS '患者ID, 关联lis_patient';
COMMENT ON COLUMN lis_sample.visit_id IS '就诊ID, 关联lis_visit';
COMMENT ON COLUMN lis_sample.patient_name IS '患者姓名(冗余)';
COMMENT ON COLUMN lis_sample.sample_type IS '标本类型';
COMMENT ON COLUMN lis_sample.container_type IS '容器类型';
COMMENT ON COLUMN lis_sample.status IS '标本状态, 0待采集/1已采集/2已送检/3已签收/4已核收/5检测中/6已报告等';
COMMENT ON COLUMN lis_sample.is_urgent IS '是否急诊, 1是 0否';
COMMENT ON COLUMN lis_sample.collect_time IS '采集时间';
COMMENT ON COLUMN lis_sample.receive_time IS '核收时间';
COMMENT ON COLUMN lis_sample.test_start_time IS '检测开始时间';
COMMENT ON COLUMN lis_sample.report_time IS '报告时间';
COMMENT ON COLUMN lis_sample.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_sample.department IS '科室';
COMMENT ON COLUMN lis_sample.remark IS '备注';
COMMENT ON COLUMN lis_sample.deleted IS '逻辑删除标记, 0未删除 1已删除';
COMMENT ON COLUMN lis_sample.create_time IS '创建时间';
COMMENT ON COLUMN lis_sample.update_time IS '更新时间';
COMMENT ON COLUMN lis_sample.collector_id IS '采集人ID';
COMMENT ON COLUMN lis_sample.sample_no IS '标本号';
COMMENT ON COLUMN lis_sample.instrument_id IS '核收仪器ID, 关联lis_instrument';
COMMENT ON COLUMN lis_sample.deliver_time IS '送检时间';
COMMENT ON COLUMN lis_sample.deliver_person IS '送检人姓名';
COMMENT ON COLUMN lis_sample.sign_off_time IS '签收时间';
COMMENT ON COLUMN lis_sample.sign_off_person IS '签收人姓名';
COMMENT ON COLUMN lis_sample.visit_no IS '就诊号(冗余)';
COMMENT ON COLUMN lis_sample.reject_time IS '拒收时间';
COMMENT ON COLUMN lis_sample.reject_person IS '拒收人姓名';
COMMENT ON COLUMN lis_sample.reject_type IS '拒收类型';
COMMENT ON COLUMN lis_sample.reject_reason IS '拒收原因';
COMMENT ON COLUMN lis_sample.reject_count IS '拒收次数';
COMMENT ON COLUMN lis_sample.deliver_person_id IS '送检人ID';
COMMENT ON COLUMN lis_sample.sign_off_person_id IS '签收人ID';
COMMENT ON COLUMN lis_sample.reject_person_id IS '拒收人ID';
COMMENT ON COLUMN lis_sample.waive_flag IS '是否作废标本, 1是 0否';
COMMENT ON COLUMN lis_sample.waive_reason IS '作废原因';
COMMENT ON COLUMN lis_sample.split_flag IS '是否拆分标本, 1是 0否';
COMMENT ON COLUMN lis_sample.parent_id IS '父标本ID, 关联拆分前的标本';
COMMENT ON COLUMN lis_sample.destroy_reason IS '销毁原因';
COMMENT ON COLUMN lis_sample.destroy_operator IS '销毁操作人';
COMMENT ON COLUMN lis_sample.destroy_time IS '销毁时间';
COMMENT ON COLUMN lis_sample.inspector IS '检验技师姓名';
COMMENT ON COLUMN lis_sample.inspector_user_id IS '检验技师用户ID';

-- ============ lis_sample_order_item (标本-申请明细关联) ============
COMMENT ON TABLE lis_sample_order_item IS '标本与申请单明细的关联表, 记录标本对应的检验项目';
COMMENT ON COLUMN lis_sample_order_item.id IS '主键ID';
COMMENT ON COLUMN lis_sample_order_item.sample_id IS '标本ID, 关联lis_sample';
COMMENT ON COLUMN lis_sample_order_item.order_id IS '申请单ID, 关联lis_lab_order';
COMMENT ON COLUMN lis_sample_order_item.order_item_id IS '申请单明细ID, 关联lis_order_item';
COMMENT ON COLUMN lis_sample_order_item.test_item_id IS '检验项目ID, 关联lis_test_item';
COMMENT ON COLUMN lis_sample_order_item.create_time IS '创建时间';

-- ============ lis_specimen_type (标本类型字典) ============
COMMENT ON TABLE lis_specimen_type IS '标本类型字典表, 如血清/血浆/尿液等';
COMMENT ON COLUMN lis_specimen_type.id IS '主键ID';
COMMENT ON COLUMN lis_specimen_type.type_code IS '标本类型编码';
COMMENT ON COLUMN lis_specimen_type.type_name IS '标本类型名称';
COMMENT ON COLUMN lis_specimen_type.description IS '标本类型说明';
COMMENT ON COLUMN lis_specimen_type.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_specimen_type.sort_order IS '排序号';
COMMENT ON COLUMN lis_specimen_type.create_time IS '创建时间';

-- ============ lis_staff (检验科员工) ============
COMMENT ON TABLE lis_staff IS '检验科员工表, 关联系统用户与人事员工';
COMMENT ON COLUMN lis_staff.id IS '主键ID';
COMMENT ON COLUMN lis_staff.staff_no IS '工号';
COMMENT ON COLUMN lis_staff.name IS '员工姓名';
COMMENT ON COLUMN lis_staff.phone IS '联系电话';
COMMENT ON COLUMN lis_staff.email IS '电子邮箱';
COMMENT ON COLUMN lis_staff.group_id IS '所属实验室组ID, 关联lis_lab_group';
COMMENT ON COLUMN lis_staff.user_id IS '系统用户ID, 关联sys_user';
COMMENT ON COLUMN lis_staff.education IS '学历';
COMMENT ON COLUMN lis_staff.major IS '专业';
COMMENT ON COLUMN lis_staff.entry_date IS '入职日期';
COMMENT ON COLUMN lis_staff.leave_date IS '离职日期';
COMMENT ON COLUMN lis_staff.id_card IS '身份证号';
COMMENT ON COLUMN lis_staff.signature_image IS '电子签名图片路径';
COMMENT ON COLUMN lis_staff.status IS '状态, 1在职 0离职';
COMMENT ON COLUMN lis_staff.create_time IS '创建时间';
COMMENT ON COLUMN lis_staff.update_time IS '更新时间';

-- ============ lis_staff_authorization (员工授权) ============
COMMENT ON TABLE lis_staff_authorization IS '员工岗位授权表, 记录授权范围与有效期';
COMMENT ON COLUMN lis_staff_authorization.id IS '主键ID';
COMMENT ON COLUMN lis_staff_authorization.staff_id IS '员工ID, 关联lis_staff';
COMMENT ON COLUMN lis_staff_authorization.auth_type IS '授权类型(如报告审核权/操作权等)';
COMMENT ON COLUMN lis_staff_authorization.auth_scope IS '授权范围';
COMMENT ON COLUMN lis_staff_authorization.start_date IS '授权开始日期';
COMMENT ON COLUMN lis_staff_authorization.end_date IS '授权结束日期';
COMMENT ON COLUMN lis_staff_authorization.grantor_id IS '授权人ID';
COMMENT ON COLUMN lis_staff_authorization.grantor_name IS '授权人姓名';
COMMENT ON COLUMN lis_staff_authorization.attachment_url IS '授权附件路径';
COMMENT ON COLUMN lis_staff_authorization.status IS '状态, 1有效 0失效';
COMMENT ON COLUMN lis_staff_authorization.create_time IS '创建时间';

-- ============ lis_staff_certificate (员工证书) ============
COMMENT ON TABLE lis_staff_certificate IS '员工职业资格证书表, 记录证书及有效期(支持到期预警)';
COMMENT ON COLUMN lis_staff_certificate.id IS '主键ID';
COMMENT ON COLUMN lis_staff_certificate.staff_id IS '员工ID, 关联lis_staff';
COMMENT ON COLUMN lis_staff_certificate.cert_type IS '证书类型';
COMMENT ON COLUMN lis_staff_certificate.cert_name IS '证书名称';
COMMENT ON COLUMN lis_staff_certificate.cert_no IS '证书编号';
COMMENT ON COLUMN lis_staff_certificate.issue_date IS '发证日期';
COMMENT ON COLUMN lis_staff_certificate.expiry_date IS '证书到期日期';
COMMENT ON COLUMN lis_staff_certificate.issue_org IS '发证机构';
COMMENT ON COLUMN lis_staff_certificate.attachment_url IS '证书附件路径';
COMMENT ON COLUMN lis_staff_certificate.alert_days IS '到期预警天数, 默认30';
COMMENT ON COLUMN lis_staff_certificate.status IS '状态, 1有效 0失效';
COMMENT ON COLUMN lis_staff_certificate.create_time IS '创建时间';

-- ============ lis_staff_schedule (员工排班) ============
COMMENT ON TABLE lis_staff_schedule IS '员工排班表, 记录每日班次与值班安排';
COMMENT ON COLUMN lis_staff_schedule.id IS '主键ID';
COMMENT ON COLUMN lis_staff_schedule.staff_id IS '员工ID, 关联lis_staff';
COMMENT ON COLUMN lis_staff_schedule.schedule_date IS '排班日期';
COMMENT ON COLUMN lis_staff_schedule.shift_type IS '班次类型(白班/夜班等)';
COMMENT ON COLUMN lis_staff_schedule.duty_type IS '值班类型';
COMMENT ON COLUMN lis_staff_schedule.create_time IS '创建时间';

-- ============ lis_staff_title (员工职称) ============
COMMENT ON TABLE lis_staff_title IS '员工职称信息表, 记录职称晋升历史';
COMMENT ON COLUMN lis_staff_title.id IS '主键ID';
COMMENT ON COLUMN lis_staff_title.staff_id IS '员工ID, 关联lis_staff';
COMMENT ON COLUMN lis_staff_title.title_code IS '职称编码, 关联lis_title_dict';
COMMENT ON COLUMN lis_staff_title.title_name IS '职称名称(冗余)';
COMMENT ON COLUMN lis_staff_title.acquire_date IS '取得职称日期';
COMMENT ON COLUMN lis_staff_title.expiry_date IS '职称到期日期';
COMMENT ON COLUMN lis_staff_title.certificate_no IS '职称证书编号';
COMMENT ON COLUMN lis_staff_title.attachment_url IS '证书附件路径';
COMMENT ON COLUMN lis_staff_title.is_current IS '是否当前职称, 1是 0否';
COMMENT ON COLUMN lis_staff_title.create_time IS '创建时间';

-- ============ lis_staff_training (员工培训) ============
COMMENT ON TABLE lis_staff_training IS '员工培训记录表, 记录培训内容与考核结果';
COMMENT ON COLUMN lis_staff_training.id IS '主键ID';
COMMENT ON COLUMN lis_staff_training.staff_id IS '员工ID, 关联lis_staff';
COMMENT ON COLUMN lis_staff_training.training_name IS '培训名称';
COMMENT ON COLUMN lis_staff_training.training_type IS '培训类型';
COMMENT ON COLUMN lis_staff_training.training_date IS '培训日期';
COMMENT ON COLUMN lis_staff_training.trainer IS '培训讲师';
COMMENT ON COLUMN lis_staff_training.result IS '考核结果';
COMMENT ON COLUMN lis_staff_training.attachment_url IS '培训附件路径';
COMMENT ON COLUMN lis_staff_training.remark IS '备注';
COMMENT ON COLUMN lis_staff_training.create_time IS '创建时间';

-- ============ lis_station_signin (工作站登录) ============
COMMENT ON TABLE lis_station_signin IS '工作站登录/注销记录表, 记录技师工作站使用情况';
COMMENT ON COLUMN lis_station_signin.id IS '主键ID';
COMMENT ON COLUMN lis_station_signin.signin_type IS '登录类型';
COMMENT ON COLUMN lis_station_signin.instrument_id IS '关联仪器ID';
COMMENT ON COLUMN lis_station_signin.operator_id IS '操作人ID';
COMMENT ON COLUMN lis_station_signin.operator_name IS '操作人姓名';
COMMENT ON COLUMN lis_station_signin.action IS '动作, signin登录/signout注销';
COMMENT ON COLUMN lis_station_signin.signin_time IS '登录时间';
COMMENT ON COLUMN lis_station_signin.signout_time IS '注销时间';
COMMENT ON COLUMN lis_station_signin.status IS '状态, 1在线 0离线';
COMMENT ON COLUMN lis_station_signin.remark IS '备注';

-- ============ lis_supplier (供应商) ============
COMMENT ON TABLE lis_supplier IS '供应商主表, 维护试剂供应商资质与评分信息';
COMMENT ON COLUMN lis_supplier.id IS '主键ID';
COMMENT ON COLUMN lis_supplier.supplier_code IS '供应商编码';
COMMENT ON COLUMN lis_supplier.supplier_name IS '供应商名称';
COMMENT ON COLUMN lis_supplier.short_name IS '供应商简称';
COMMENT ON COLUMN lis_supplier.credit_code IS '统一社会信用代码';
COMMENT ON COLUMN lis_supplier.contact_person IS '联系人';
COMMENT ON COLUMN lis_supplier.phone IS '联系电话';
COMMENT ON COLUMN lis_supplier.email IS '电子邮箱';
COMMENT ON COLUMN lis_supplier.address IS '联系地址';
COMMENT ON COLUMN lis_supplier.license_no IS '营业执照号';
COMMENT ON COLUMN lis_supplier.license_expiry IS '营业执照到期日期';
COMMENT ON COLUMN lis_supplier.medical_cert_no IS '医疗器械经营许可证号';
COMMENT ON COLUMN lis_supplier.medical_cert_expiry IS '医疗器械证到期日期';
COMMENT ON COLUMN lis_supplier.qual_status IS '资质状态, 0待审核/1合格/2不合格';
COMMENT ON COLUMN lis_supplier.score IS '供应商评分';
COMMENT ON COLUMN lis_supplier.source_type IS '来源类型, 1生产厂家 2经销商';
COMMENT ON COLUMN lis_supplier.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_supplier.remark IS '备注';
COMMENT ON COLUMN lis_supplier.create_time IS '创建时间';
COMMENT ON COLUMN lis_supplier.update_time IS '更新时间';

-- ============ lis_technician_favorite (技师收藏) ============
COMMENT ON TABLE lis_technician_favorite IS '技师工作站常用查询收藏表';
COMMENT ON COLUMN lis_technician_favorite.id IS '主键ID';
COMMENT ON COLUMN lis_technician_favorite.name IS '收藏名称';
COMMENT ON COLUMN lis_technician_favorite.type IS '收藏类型, 默认sample';
COMMENT ON COLUMN lis_technician_favorite.payload IS '收藏条件(JSON)';
COMMENT ON COLUMN lis_technician_favorite.create_time IS '创建时间';

-- ============ lis_test_item (检验项目/指标) ============
COMMENT ON TABLE lis_test_item IS '检验项目/指标表, is_panel=1为检验项目, is_panel=0为检验指标';
COMMENT ON COLUMN lis_test_item.id IS '主键ID';
COMMENT ON COLUMN lis_test_item.item_code IS '项目/指标编码';
COMMENT ON COLUMN lis_test_item.item_name IS '项目/指标名称';
COMMENT ON COLUMN lis_test_item.item_name_en IS '项目/指标英文名称';
COMMENT ON COLUMN lis_test_item.short_name IS '简称';
COMMENT ON COLUMN lis_test_item.category IS '类别';
COMMENT ON COLUMN lis_test_item.specimen_type IS '标本类型';
COMMENT ON COLUMN lis_test_item.unit IS '结果单位';
COMMENT ON COLUMN lis_test_item.method IS '检验方法';
COMMENT ON COLUMN lis_test_item.is_panel IS '是否检验项目, 1是(检验项目) 0否(检验指标)';
COMMENT ON COLUMN lis_test_item.parent_id IS '父级ID, 检验指标通过此关联所属检验项目';
COMMENT ON COLUMN lis_test_item.price IS '价格';
COMMENT ON COLUMN lis_test_item.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_test_item.create_time IS '创建时间';
COMMENT ON COLUMN lis_test_item.tube_id IS '关联检验试管ID, 关联lis_test_tube';

-- ============ lis_test_tube (检验试管) ============
COMMENT ON TABLE lis_test_tube IS '检验试管字典表, 定义试管类型与采血管';
COMMENT ON COLUMN lis_test_tube.id IS '主键ID';
COMMENT ON COLUMN lis_test_tube.tube_code IS '试管编码';
COMMENT ON COLUMN lis_test_tube.tube_name IS '试管名称';
COMMENT ON COLUMN lis_test_tube.tube_color IS '试管颜色';
COMMENT ON COLUMN lis_test_tube.tube_type IS '试管类型';
COMMENT ON COLUMN lis_test_tube.volume IS '采血量';
COMMENT ON COLUMN lis_test_tube.additive IS '添加剂';
COMMENT ON COLUMN lis_test_tube.sort_order IS '排序号';
COMMENT ON COLUMN lis_test_tube.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN lis_test_tube.remark IS '备注';
COMMENT ON COLUMN lis_test_tube.create_time IS '创建时间';

-- ============ lis_title_dict (职称字典) ============
COMMENT ON TABLE lis_title_dict IS '职称字典表, 维护职称等级';
COMMENT ON COLUMN lis_title_dict.id IS '主键ID';
COMMENT ON COLUMN lis_title_dict.title_code IS '职称编码';
COMMENT ON COLUMN lis_title_dict.title_name IS '职称名称';
COMMENT ON COLUMN lis_title_dict.title_level IS '职称级别';
COMMENT ON COLUMN lis_title_dict.sort_order IS '排序号';
COMMENT ON COLUMN lis_title_dict.status IS '状态, 1启用 0停用';

-- ============ lis_visit (就诊信息) ============
COMMENT ON TABLE lis_visit IS '就诊信息表, 记录一次就诊(门诊/住院/急诊)';
COMMENT ON COLUMN lis_visit.id IS '主键ID';
COMMENT ON COLUMN lis_visit.visit_no IS '就诊号';
COMMENT ON COLUMN lis_visit.patient_id IS '患者ID, 关联lis_patient';
COMMENT ON COLUMN lis_visit.visit_type IS '就诊类型, 1门诊 2住院 3急诊';
COMMENT ON COLUMN lis_visit.department IS '就诊科室';
COMMENT ON COLUMN lis_visit.bed_no IS '床位号';
COMMENT ON COLUMN lis_visit.doctor_id IS '主治医生ID';
COMMENT ON COLUMN lis_visit.doctor_name IS '主治医生姓名';
COMMENT ON COLUMN lis_visit.visit_time IS '就诊时间';
COMMENT ON COLUMN lis_visit.create_time IS '创建时间';

-- ============ sys_audit_log (审计日志) ============
COMMENT ON TABLE sys_audit_log IS '系统审计日志表, 记录关键操作日志';
COMMENT ON COLUMN sys_audit_log.id IS '主键ID';
COMMENT ON COLUMN sys_audit_log.user_id IS '操作人用户ID';
COMMENT ON COLUMN sys_audit_log.username IS '操作人用户名';
COMMENT ON COLUMN sys_audit_log.module IS '操作模块';
COMMENT ON COLUMN sys_audit_log.operation IS '操作描述';
COMMENT ON COLUMN sys_audit_log.method IS '请求方法';
COMMENT ON COLUMN sys_audit_log.params IS '请求参数';
COMMENT ON COLUMN sys_audit_log.result IS '操作结果';
COMMENT ON COLUMN sys_audit_log.ip IS '操作IP地址';
COMMENT ON COLUMN sys_audit_log.cost_time IS '耗时(毫秒)';
COMMENT ON COLUMN sys_audit_log.create_time IS '创建时间';

-- ============ sys_config (系统配置) ============
COMMENT ON TABLE sys_config IS '系统配置表, 存储键值对形式配置';
COMMENT ON COLUMN sys_config.id IS '主键ID';
COMMENT ON COLUMN sys_config.config_key IS '配置键';
COMMENT ON COLUMN sys_config.config_value IS '配置值';
COMMENT ON COLUMN sys_config.config_name IS '配置名称';
COMMENT ON COLUMN sys_config.remark IS '备注';
COMMENT ON COLUMN sys_config.create_time IS '创建时间';
COMMENT ON COLUMN sys_config.update_time IS '更新时间';

-- ============ sys_department (部门) ============
COMMENT ON TABLE sys_department IS '部门表, 支持树形层级结构';
COMMENT ON COLUMN sys_department.id IS '主键ID';
COMMENT ON COLUMN sys_department.dept_code IS '部门编码';
COMMENT ON COLUMN sys_department.dept_name IS '部门名称';
COMMENT ON COLUMN sys_department.parent_id IS '上级部门ID';
COMMENT ON COLUMN sys_department.sort_order IS '排序号';
COMMENT ON COLUMN sys_department.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN sys_department.create_time IS '创建时间';

-- ============ sys_permission (权限) ============
COMMENT ON TABLE sys_permission IS '权限表, 定义菜单与操作权限';
COMMENT ON COLUMN sys_permission.id IS '主键ID';
COMMENT ON COLUMN sys_permission.parent_id IS '上级权限ID';
COMMENT ON COLUMN sys_permission.perm_code IS '权限编码';
COMMENT ON COLUMN sys_permission.perm_name IS '权限名称';
COMMENT ON COLUMN sys_permission.perm_type IS '权限类型, 1菜单 2按钮';
COMMENT ON COLUMN sys_permission.path IS '路由路径';
COMMENT ON COLUMN sys_permission.component IS '组件路径';
COMMENT ON COLUMN sys_permission.icon IS '图标';
COMMENT ON COLUMN sys_permission.sort_order IS '排序号';
COMMENT ON COLUMN sys_permission.status IS '状态, 1启用 0停用';

-- ============ sys_role (角色) ============
COMMENT ON TABLE sys_role IS '角色表, 定义系统角色';
COMMENT ON COLUMN sys_role.id IS '主键ID';
COMMENT ON COLUMN sys_role.role_code IS '角色编码';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';
COMMENT ON COLUMN sys_role.description IS '角色描述';
COMMENT ON COLUMN sys_role.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN sys_role.create_time IS '创建时间';

-- ============ sys_role_permission (角色-权限关联) ============
COMMENT ON TABLE sys_role_permission IS '角色与权限关联表';
COMMENT ON COLUMN sys_role_permission.role_id IS '角色ID, 关联sys_role';
COMMENT ON COLUMN sys_role_permission.permission_id IS '权限ID, 关联sys_permission';

-- ============ sys_user (系统用户) ============
COMMENT ON TABLE sys_user IS '系统用户表, 用于登录认证';
COMMENT ON COLUMN sys_user.id IS '主键ID';
COMMENT ON COLUMN sys_user.username IS '用户名';
COMMENT ON COLUMN sys_user.password IS '密码(加密存储)';
COMMENT ON COLUMN sys_user.real_name IS '真实姓名';
COMMENT ON COLUMN sys_user.phone IS '联系电话';
COMMENT ON COLUMN sys_user.email IS '电子邮箱';
COMMENT ON COLUMN sys_user.department_id IS '所属部门ID, 关联sys_department';
COMMENT ON COLUMN sys_user.status IS '状态, 1启用 0停用';
COMMENT ON COLUMN sys_user.last_login_time IS '最近登录时间';
COMMENT ON COLUMN sys_user.create_time IS '创建时间';

-- ============ sys_user_role (用户-角色关联) ============
COMMENT ON TABLE sys_user_role IS '用户与角色关联表';
COMMENT ON COLUMN sys_user_role.user_id IS '用户ID, 关联sys_user';
COMMENT ON COLUMN sys_user_role.role_id IS '角色ID, 关联sys_role';