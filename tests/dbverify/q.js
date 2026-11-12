const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  const q = async (label, sql, args=[]) => {
    try {
      const r = await c.query(sql, args);
      console.log('=== ' + label + ' ===');
      console.log(JSON.stringify(r.rows, (k,v)=> v===undefined?'':v, 2));
    } catch(e) { console.log('=== ' + label + ' ERROR ==='); console.log(e.message); }
  };
  await q('sample', "SELECT id, sample_barcode, sample_type, status, instrument_id, order_id FROM lis_sample WHERE sample_barcode=$1", ['BC202608050145']);
  await q('au5800 instrument', "SELECT id, instrument_code, instrument_name FROM lis_instrument WHERE instrument_name ILIKE '%au5800%' OR instrument_code ILIKE '%au5800%'");
  await q('specimen map au5800', "SELECT * FROM lis_instrument_specimen_map WHERE instrument_id IN (SELECT id FROM lis_instrument WHERE instrument_name ILIKE '%au5800%' OR instrument_code ILIKE '%au5800%')");
  await q('specimen dict', "SELECT * FROM lis_specimen_type LIMIT 50");
  await c.end();
})();