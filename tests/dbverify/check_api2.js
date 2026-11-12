const fs = require('fs');
const raw = fs.readFileSync('response_detail.json', 'utf8');
const d = JSON.parse(raw);
console.log('top-level keys:', Object.keys(d));
console.log('data keys:', Object.keys(d.data));
const imgs = d.data.images;
console.log('images[0] full:', JSON.stringify(imgs[0], null, 2));
// check if there is another images location
for (const k of Object.keys(d.data)) {
  const v = d.data[k];
  if (Array.isArray(v) && v.length && v[0] && typeof v[0] === 'object' && ('imageUrl' in v[0] || 'image_url' in v[0])) {
    console.log('FOUND images array at key:', k, 'len', v.length, JSON.stringify(v[0]).slice(0,200));
  }
}