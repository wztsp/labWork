const fs = require('fs');
const d = JSON.parse(fs.readFileSync('response_detail.json', 'utf8'));
const imgs = d.data.images;
console.log('images length:', imgs.length);
const o = imgs[0];
console.log('keys:', Object.keys(o));
for (const k of Object.keys(o)) {
  const v = o[k];
  const s = typeof v === 'string' ? `(string len=${v.length})` : typeof v;
  console.log(`  ${k}: ${s}`);
}