const fs = require('fs');
const d = JSON.parse(fs.readFileSync('response_detail2.json','utf8'));
const imgs = d.data.images;
console.log('images count:', imgs.length);
imgs.forEach((im,i)=>{
  console.log(`img${i+1}: keys=[${Object.keys(im)}] id=${im.id} imageName=${im.imageName} imageType=${im.imageType} sortNo=${im.sortNo} imageUrlLen=${im.imageUrl?im.imageUrl.length:0} prefix=${im.imageUrl?im.imageUrl.slice(0,23):''}`);
});
// verify all 5 samples
const all = d.data;
console.log('patientName:', all.patient.patientName);