const http = require('http');
function get(p) {
  return new Promise((res, rej) => {
    http.get('http://localhost:8080' + p, r => {
      let d = '';
      r.on('data', c => d += c);
      r.on('end', () => res(JSON.parse(d)));
    }).on('error', rej);
  });
}
(async () => {
  const list = await get('/api/report/technician/samples?size=500');
  const targets = list.data.filter(s => String(s.sampleBarcode).startsWith('BC2026080603'));
  console.log('匹配到目标标本数:', targets.length);
  for (const s of targets) {
    console.log(`\n=== barcode=${s.sampleBarcode} patient=${s.patientName} status=${s.status} testItems=${s.testItems} id=${s.id}`);
    const det = await get('/api/report/technician/' + (s.sampleId || s.id));
    const imgs = det.data && det.data.images;
    if (!imgs || !imgs.length) { console.log('  图像: 无'); continue; }
    imgs.forEach((im, i) => {
      console.log(`  img${i + 1}: name=${im.imageName} type=${im.imageType} len=${im.imageUrl ? im.imageUrl.length : 0} prefix=${im.imageUrl ? im.imageUrl.slice(0, 23) : ''}`);
    });
  }
})().catch(e => { console.error('ERR:', e.message); process.exit(1); });