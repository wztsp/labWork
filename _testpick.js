const fs = require("fs");
const path = require("path");
const SS_DIR = path.resolve(process.cwd(), "LIS-clinical-lab-solution/deploy-package/screenshots");
const files = fs.readdirSync(SS_DIR).filter(f => f.endsWith(".png")).sort();
const infos = files.map(f => {
  const noExt = f.replace(/\.png$/i, "");
  const firstUs = noExt.indexOf("_");
  const keyBody = firstUs > 0 ? noExt.slice(firstUs + 1) : noExt;
  return { file: f, keyBody, tokens: keyBody.split(/[_-]/).filter(Boolean) };
});
function pick(route) {
  const rTokens = route.split(/[\/_-]/).filter(Boolean);
  let best = null; let bestScore = 0;
  for (const info of infos) {
    let score = 0;
    for (const r of rTokens) {
      if (info.tokens.includes(r)) score += 3;
      else if (info.tokens.some(t => t.startsWith(r) || r.startsWith(t))) score += 1;
    }
    if (info.tokens.length === 1 && rTokens.length === 1 && info.tokens[0] === rTokens[0]) score += 3;
    if (score > bestScore) { bestScore = score; best = info.file; }
  }
  return { best, bestScore };
}
console.log("qc/eqa ->", pick("qc/eqa"));
console.log("qc/eqc ->", pick("qc/eqc"));
console.log("monitor/tat-monitor ->", pick("monitor/tat-monitor"));
console.log("monitor/tatmonitor  ->", pick("monitor/tatmonitor"));
