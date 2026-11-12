const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const r = await c.query(`SELECT s.sample_barcode AS barcode, s.patient_name AS name, im.image_name,
      LEFT(im.image_url, 30) AS url_prefix, length(im.image_url) AS url_len, im.sort_no
    FROM lis_sample_image im
    JOIN lis_sample s ON s.id = im.sample_id
    WHERE s.sample_barcode LIKE 'BC20260806030%'
    ORDER BY s.sample_barcode, im.sort_no`);
  console.table(r.rows);
  const bad = r.rows.filter(x => !x.url_prefix.startsWith('data:image/jpeg') || x.url_len < 1000);
  console.log('无效图像数:', bad.length);
  await c.end();
})();