const g=[{key:"A4",name:"A4 (210×297mm)",width:210,height:297},{key:"A5",name:"A5 (148×210mm)",width:148,height:210},{key:"LABEL_40x30",name:"标签 40×30mm",width:40,height:30},{key:"LABEL_50x30",name:"标签 50×30mm",width:50,height:30},{key:"LABEL_60x40",name:"标签 60×40mm",width:60,height:40},{key:"LABEL_70x40",name:"标签 70×40mm",width:70,height:40},{key:"LABEL_80x40",name:"标签 80×40mm",width:80,height:40},{key:"LABEL_80x50",name:"标签 80×50mm",width:80,height:50},{key:"LABEL_90x60",name:"标签 90×60mm",width:90,height:60},{key:"LABEL_100x60",name:"标签 100×60mm",width:100,height:60},{key:"LABEL_100x80",name:"标签 100×80mm",width:100,height:80},{key:"CUSTOM",name:"自定义",width:0,height:0}],b="lis:print-config:",p={"sample-barcode":{paperSizePreset:"LABEL_70x40",paperWidth:70,paperHeight:40,scale:1,barcodeWidth:1.5,barcodeHeight:30,labelFontSize:9,margin:2,border:1},"plate-barcode":{paperSizePreset:"LABEL_60x40",paperWidth:60,paperHeight:40,scale:1,barcodeWidth:1.3,barcodeHeight:26,labelFontSize:8,margin:2,border:1},"reagent-barcode":{paperSizePreset:"LABEL_60x40",paperWidth:60,paperHeight:40,scale:1,barcodeWidth:1.3,barcodeHeight:26,labelFontSize:9,margin:2,border:1},"report-clinical":{paperSizePreset:"A4",paperWidth:210,paperHeight:297,scale:1,barcodeWidth:2,barcodeHeight:50,labelFontSize:12,margin:10,border:0},"report-micro":{paperSizePreset:"A4",paperWidth:210,paperHeight:297,scale:1,barcodeWidth:2,barcodeHeight:50,labelFontSize:12,margin:10,border:0},"report-qc":{paperSizePreset:"A4",paperWidth:210,paperHeight:297,scale:1,barcodeWidth:2,barcodeHeight:50,labelFontSize:12,margin:10,border:0}};function a(r,t,e){return Number.isNaN(r)?(t+e)/2:Math.min(e,Math.max(t,r))}function n(r){return b+r}function c(r){try{const t=localStorage.getItem(n(r));if(t){const e=JSON.parse(t),i=p[r];return{scene:r,paperSizePreset:e.paperSizePreset||i.paperSizePreset,paperWidth:a(Number(e.paperWidth)||i.paperWidth,20,500),paperHeight:a(Number(e.paperHeight)||i.paperHeight,20,500),scale:a(Number(e.scale)||i.scale,.3,3),barcodeWidth:a(Number(e.barcodeWidth)||i.barcodeWidth,.8,4),barcodeHeight:a(Number(e.barcodeHeight)||i.barcodeHeight,10,120),labelFontSize:a(Number(e.labelFontSize)||i.labelFontSize,6,24),margin:a(Number(e.margin)??i.margin,0,50),border:e.border===1||e.border===0?e.border:i.border,extraCss:typeof e.extraCss=="string"?e.extraCss:void 0}}}catch{}return{scene:r,...p[r]}}function l(r){const t=r.scene==="default"?"default":r.scene,e={...r};return localStorage.setItem(n(t),JSON.stringify(e)),!0}function u(r){localStorage.removeItem(n(r))}function S(r,t){const e=g.find(i=>i.key===t);return!e||t==="CUSTOM"?{...r,paperSizePreset:t}:{...r,paperSizePreset:t,paperWidth:e.width,paperHeight:e.height}}function f(r,t=".barcode-label"){const{paperWidth:e,paperHeight:i,margin:o,scale:h,labelFontSize:d,border:m,extraCss:s}=r;return`
  @page {
    size: ${e}mm ${i}mm;
    margin: ${o}mm;
  }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body {
    width: ${e}mm;
    height: auto;
    min-height: ${i}mm;
  }
  body {
    font-family: "Microsoft YaHei", "SimSun", sans-serif;
    font-size: ${d}px;
    padding: 0;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
  .print-scale-wrap {
    /* 打印缩放: 在每一页根元素上应用 */
    width: ${e-o*2}mm;
    transform-origin: top left;
    transform: scale(${h});
    margin: 0 auto;
  }
  ${t} {
    width: ${e-o*2}mm;
    min-height: ${i-o*2}mm;
    ${m?"border: 1px solid #333;":"border: none;"}
    padding: 3mm 4mm;
    margin: 0 auto;
    page-break-after: always;
    display: flex;
    flex-direction: column;
    box-sizing: border-box;
    font-size: ${d}px;
    line-height: 1.45;
  }
  ${t}:last-child {
    page-break-after: auto;
  }
  @media print {
    body { padding: 0; }
    ${t} { margin: 0; }
    .no-print { display: none !important; }
  }
  ${s||""}
`}export{g as P,S as a,f as b,c as g,u as r,l as s};
