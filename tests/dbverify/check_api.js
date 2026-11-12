const fs = require('fs');
const d = JSON.parse(fs.readFileSync('response_detail.json', 'utf8'));
const data = d.data || d;
console.log('code:', d.code, 'msg:', d.msg);
console.log('patient:', JSON.stringify(data.patient));
console.log('results count:', (data.results||[]).length);
const imgs = data.images || [];
console.log('images count:', imgs.length);
imgs.forEach((im,i)=>console.log(`  img${i+1}: id=${im.id} name=${im.imageName} type=${im.imageType} urlLen=${(im.imageUrl||'').length} prefix=${(im.imageUrl||'').slice(0,30)}`));