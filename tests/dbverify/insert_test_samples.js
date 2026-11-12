const { Client } = require('pg');
const fs = require('fs');
const path = require('path');

const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });

const IMG_DIR = path.join(__dirname, 'img');
const IMG_FILES = ['blood_smear_1.jpg', 'blood_smear_2.jpg', 'blood_smear_3.jpg'];
const IMG_NAMES = ['血涂片-低倍镜', '血涂片-高倍镜(白细胞)', '血涂片-油镜(红细胞/血小板)'];

// 5 个待审核血常规项目 (患者姓名/科室/诊断)
const ITEMS = [
  { name: '王芳',   dept: '门诊内科', diag: '发热待查',   barcode: 'BC202608060301', sampleNo: 'SN0301' },
  { name: '李强',   dept: '住院部',   diag: '贫血待查',   barcode: 'BC202608060302', sampleNo: 'SN0302' },
  { name: '张丽',   dept: '门诊儿科', diag: '呼吸道感染', barcode: 'BC202608060303', sampleNo: 'SN0303' },
  { name: '刘洋',   dept: '急诊科',   diag: '急性腹痛',   barcode: 'BC202608060304', sampleNo: 'SN0304' },
  { name: '陈静',   dept: '内分泌科', diag: '糖尿病随访', barcode: 'BC202608060305', sampleNo: 'SN0305' },
];

function toDataUri(file) {
  const buf = fs.readFileSync(path.join(IMG_DIR, file));
  return 'data:image/jpeg;base64,' + buf.toString('base64');
}

(async () => {
  await c.connect();

  // 1. 创建 lis_sample_image 表 (若不存在)
  await c.query(`CREATE TABLE IF NOT EXISTS lis_sample_image (
    id BIGSERIAL PRIMARY KEY,
    sample_id BIGINT NOT NULL,
    image_url TEXT,
    image_name VARCHAR(255),
    image_type VARCHAR(50),
    sort_no INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  )`);
  console.log('[OK] lis_sample_image 表已就绪');

  // 2. 读取 3 张图像 base64
  const dataUris = IMG_FILES.map(toDataUri);
  console.log('[OK] 3 张图像 base64 已加载, 大小(KB): ' + IMG_FILES.map(f => Math.round(fs.statSync(path.join(IMG_DIR, f)).size / 1024)).join(', '));

  const now = new Date();
  const collectBase = new Date(now.getTime() - 3 * 3600 * 1000); // 采集时间 3 小时前
  const receiveTime = new Date(now.getTime() - 60 * 60 * 1000);  // 核收时间 1 小时前

  for (let i = 0; i < ITEMS.length; i++) {
    const it = ITEMS[i];
    // 幂等: 已存在该条码标本则跳过
    const dup = await c.query('SELECT id FROM lis_sample WHERE sample_barcode=$1', [it.barcode]);
    if (dup.rows.length) {
      console.log(`[SKIP] #${i + 1} ${it.name} barcode=${it.barcode} 已存在, 跳过`);
      continue;
    }
    const gender = i % 2 === 0 ? 1 : 2; // 1男 2女
    const age = 25 + i * 5;
    const patientNo = 'P20260806030' + (i + 1);

    // 患者
    const existing = await c.query('SELECT id FROM lis_patient WHERE patient_no=$1', [patientNo]);
    let patientId;
    if (existing.rows.length) {
      patientId = existing.rows[0].id;
    } else {
      const patient = await c.query(
        `INSERT INTO lis_patient (patient_no, name, gender, age, birth_date, create_time, update_time)
         VALUES ($1, $2, $3, $4, (CURRENT_DATE - make_interval(years => $4))::date, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
         RETURNING id`,
        [patientNo, it.name, gender, age]
      );
      patientId = patient.rows[0].id;
    }

    // 就诊
    const visitNo = 'V20260806030' + (i + 1);
    const visit = await c.query(
      `INSERT INTO lis_visit (visit_no, patient_id, visit_type, department, doctor_id, doctor_name, visit_time, create_time)
       VALUES ($1, $2, 1, $3, 1, '张医生', $4, CURRENT_TIMESTAMP)
       RETURNING id`,
      [visitNo, patientId, it.dept, collectBase]
    );
    const visitId = visit.rows[0].id;

    // 检验申请单
    const orderNo = 'ORD20260806030' + (i + 1);
    const order = await c.query(
      `INSERT INTO lis_lab_order (order_no, visit_id, patient_id, order_type, status, apply_doctor, apply_dept, clinical_diag, apply_time, create_time)
       VALUES ($1, $2, $3, 1, 1, '张医生', $4, $5, $6, CURRENT_TIMESTAMP)
       RETURNING id`,
      [orderNo, visitId, patientId, it.dept, it.diag, collectBase]
    );
    const orderId = order.rows[0].id;

    // 申请明细: 血常规 (test_item_id=9, CBP)
    const orderItem = await c.query(
      `INSERT INTO lis_order_item (order_id, test_item_id, test_item_code, test_item_name, status, fee, create_time)
       VALUES ($1, 9, 'CBP', '血常规', 1, 35.00, CURRENT_TIMESTAMP)
       RETURNING id`,
      [orderId]
    );
    const orderItemId = orderItem.rows[0].id;

    // 标本 (已核收/待审核 status=4, 全血, 迈瑞BC-7500 instrument_id=1)
    const sample = await c.query(
      `INSERT INTO lis_sample (
         sample_barcode, sample_no, order_id, patient_id, visit_id, patient_name,
         sample_type, container_type, status, is_urgent, department,
         collect_time, receive_time, operator_id, instrument_id,
         deliver_time, deliver_person, sign_off_time, sign_off_person,
         visit_no, create_time, update_time, deleted
       ) VALUES (
         $1, $2, $3, $4, $5, $6,
         '全血', '紫头管', 4, 0, $7,
         $8, $9, 1, 1,
         $10, '送检员', $11, '签收员',
         $12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
       ) RETURNING id`,
      [it.barcode, it.sampleNo, orderId, patientId, visitId, it.name,
       it.dept,
       collectBase, receiveTime,
       new Date(receiveTime.getTime() - 2 * 3600 * 1000),
       new Date(receiveTime.getTime() - 1 * 3600 * 1000),
       visitNo]
    );
    const sampleId = sample.rows[0].id;

    // 标本-申请关联 (该表 id 无默认序列, 需显式提供)
    const nextSoiId = await c.query('SELECT COALESCE(MAX(id),0)+1 AS nid FROM lis_sample_order_item');
    await c.query(
      `INSERT INTO lis_sample_order_item (id, sample_id, order_id, order_item_id, test_item_id, create_time)
       VALUES ($4, $1, $2, $3, 9, CURRENT_TIMESTAMP)`,
      [sampleId, orderId, orderItemId, nextSoiId.rows[0].nid]
    );

    // 3 张标本图像
    for (let k = 0; k < 3; k++) {
      await c.query(
        `INSERT INTO lis_sample_image (sample_id, image_url, image_name, image_type, sort_no, create_time)
         VALUES ($1, $2, $3, '血常规', $4, CURRENT_TIMESTAMP)`,
        [sampleId, dataUris[k], IMG_NAMES[k], k + 1]
      );
    }

    console.log(`[OK] #${i + 1} ${it.name} sampleId=${sampleId} barcode=${it.barcode} 已创建, 3 张图像已插入`);
  }

  // 校验
  const cnt = await c.query(`SELECT (SELECT COUNT(*) FROM lis_sample) AS samples,
    (SELECT COUNT(*) FROM lis_sample_image) AS images,
    (SELECT COUNT(*) FROM lis_sample WHERE status=4 AND sample_type='全血' AND instrument_id=1) AS pending_cbc`);
  console.log('\n=== 校验 ===');
  console.table(cnt.rows);

  await c.end();
})().catch(async e => { console.error('ERROR:', e.message); await c.end(); process.exit(1); });