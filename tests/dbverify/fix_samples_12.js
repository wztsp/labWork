const { Client } = require('pg');
const fs = require('fs');
const path = require('path');

const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
const IMG_DIR = path.join(__dirname, 'img');
const IMG_FILES = ['blood_smear_1.jpg', 'blood_smear_2.jpg', 'blood_smear_3.jpg'];
const IMG_NAMES = ['血涂片-低倍镜', '血涂片-高倍镜(白细胞)', '血涂片-油镜(红细胞/血小板)'];
const TARGETS = [
  { name: '王芳', barcode: 'BC202608060301' },
  { name: '李强', barcode: 'BC202608060302' },
];

(async () => {
  await c.connect();
  const dataUris = IMG_FILES.map(f => 'data:image/jpeg;base64,' + fs.readFileSync(path.join(IMG_DIR, f)).toString('base64'));

  for (const t of TARGETS) {
    const s = await c.query('SELECT id, order_id FROM lis_sample WHERE sample_barcode=$1', [t.barcode]);
    if (!s.rows.length) { console.log(`[!!] ${t.name} 标本不存在`); continue; }
    const sampleId = s.rows[0].id;
    const orderId = s.rows[0].order_id;

    // 补 sample_order_item
    const soi = await c.query('SELECT id FROM lis_sample_order_item WHERE sample_id=$1', [sampleId]);
    if (!soi.rows.length) {
      const oi = await c.query('SELECT id FROM lis_order_item WHERE order_id=$1 AND test_item_id=9 LIMIT 1', [orderId]);
      const orderItemId = oi.rows[0].id;
      const nid = await c.query('SELECT COALESCE(MAX(id),0)+1 AS nid FROM lis_sample_order_item');
      await c.query(
        `INSERT INTO lis_sample_order_item (id, sample_id, order_id, order_item_id, test_item_id, create_time)
         VALUES ($1,$2,$3,$4,9,CURRENT_TIMESTAMP)`,
        [nid.rows[0].nid, sampleId, orderId, orderItemId]
      );
      console.log(`[OK] ${t.name} sampleId=${sampleId} sample_order_item 已补齐`);
    } else {
      console.log(`[SKIP] ${t.name} sample_order_item 已存在`);
    }

    // 补 3 张图像
    const imgCount = await c.query('SELECT COUNT(*)::int AS n FROM lis_sample_image WHERE sample_id=$1', [sampleId]);
    if (imgCount.rows[0].n === 0) {
      for (let k = 0; k < 3; k++) {
        await c.query(
          `INSERT INTO lis_sample_image (sample_id, image_url, image_name, image_type, sort_no, create_time)
           VALUES ($1,$2,$3,'血常规',$4,CURRENT_TIMESTAMP)`,
          [sampleId, dataUris[k], IMG_NAMES[k], k + 1]
        );
      }
      console.log(`[OK] ${t.name} 3 张图像已补齐`);
    } else {
      console.log(`[SKIP] ${t.name} 已有 ${imgCount.rows[0].n} 张图像`);
    }
  }

  const r = await c.query(`SELECT s.sample_barcode, s.patient_name, s.status, s.instrument_id,
      (SELECT COUNT(*) FROM lis_sample_image im WHERE im.sample_id=s.id) AS img_cnt,
      (SELECT COUNT(*) FROM lis_sample_order_item so WHERE so.sample_id=s.id) AS soi_cnt
    FROM lis_sample s
    WHERE s.sample_barcode LIKE 'BC20260806030%' ORDER BY s.sample_barcode`);
  console.log('\n=== 5 个待审核血常规项目 ===');
  console.table(r.rows);
  await c.end();
})().catch(async e => { console.error('ERROR:', e.message); await c.end(); process.exit(1); });