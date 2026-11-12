// Markdown -> Word .docx
// LIS 项目专用：处理标题、段落（粗体、行内code）、列表、表格、引用、图片、分隔线
// 同步输出 v1.2 带截图引用的 MD（自动补齐 §7 统计分析 & §9 人事 的截图小节）
const fs = require('fs');
const path = require('path');
const sizeOf = require('image-size');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel,
  Table, TableRow, TableCell, WidthType, AlignmentType,
  ImageRun, PageBreak, ShadingType, BorderStyle, TableOfContents,
  LevelFormat
} = require('docx');

const ROOT = path.resolve(__dirname, 'LIS-clinical-lab-solution', 'deploy-package');
const MD_IN  = path.join(ROOT, '操作文档.md');
const DEPLOY_MD = path.join(ROOT, '部署文档.md');
const SS_DIR = path.join(ROOT, 'screenshots');

// 路由 -> 截图（按截图文件内部路由片段模糊匹配，避免 eqc/eqa 类命名差异）
function buildRouteMatcher() {
  const files = fs.readdirSync(SS_DIR).filter(f => f.endsWith('.png')).sort();
  // 每个截图文件名 -> 去序号前缀后的 tokens，例如 22_qc_eqc.png -> ['qc','eqc']
  const infos = files.map(f => {
    const noExt = f.replace(/\.png$/i, '');
    const firstUs = noExt.indexOf('_');
    const keyBody = firstUs > 0 ? noExt.slice(firstUs + 1) : noExt;
    return { file: f, keyBody, tokens: [...new Set(keyBody.split(/[_-]/).filter(Boolean))] };
  });
  // 已知的 "路由 token" 到 "文件名 token" 的别名表（命名差异）
  const ALIASES = new Map([
    ['eqa', 'eqc'],            // 文档写 室间质评 /qc/eqa，文件存 qc_eqc
    ['tatmonitor', 'monitor'], // monitor/tat-monitor: rTokens=[monitor,tat,monitor] 去重后 [monitor,tat]
  ]);
  // 给定 route 'qc/eqa' -> 返回最匹配的文件
  function pick(route) {
    let rTokens = [...new Set(route.split(/[\/_-]/).filter(Boolean))];
    // 替换别名（不丢弃原 token，两个都保留做比对）
    const extra = [];
    for (const t of rTokens) if (ALIASES.has(t)) extra.push(ALIASES.get(t));
    rTokens = [...new Set([...rTokens, ...extra])];
    let best = null; let bestScore = -1;
    for (const info of infos) {
      // Jaccard 相似度 + 精确匹配奖励
      const exact = new Set(rTokens.filter(t => info.tokens.includes(t)));
      const union = new Set([...rTokens, ...info.tokens]);
      const jacc = exact.size / (union.size || 1);
      // 前缀匹配（1字符即可）作为弱加分
      let weak = 0;
      for (const r of rTokens) {
        if (exact.has(r)) continue;
        if (info.tokens.some(t => t.startsWith(r) || r.startsWith(t))) weak += 0.4;
      }
      const score = jacc * 10 + weak + (info.keyBody === route.replace(/\//g, '_') ? 2 : 0);
      if (score > bestScore) { bestScore = score; best = info.file; }
    }
    // 保底：Jaccard 太弱就不返回
    return bestScore >= 0.5 ? best : null;
  }
  return { pick, files };
}
const { pick: pickScreenshot, files: SS_FILES } = buildRouteMatcher();

// 根据小节标题 `/xxx/yyy` 返回截图
function pickForHeading(heading) {
  const m = heading.match(/`\/([^`]+)`/);
  if (!m) return null;
  return pickScreenshot(m[1]);
}

// 把「章节汇总表格」里每一行路由展开为子小节 + 对应截图（仅 §7、§9 适用）
function expandTableToSections(lines, startIdx) {
  // 读取整个表格
  const rows = [];
  let i = startIdx;
  while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) { rows.push(lines[i]); i++; }
  if (rows.length < 2) return { lines: null, endIdx: startIdx };
  const data = rows.map(r => r.replace(/^\|/, '').replace(/\|$/, '').split('|').map(c => c.trim()));
  if (!data[1].every(c => /^:?-+:?$/.test(c))) return { lines: null, endIdx: startIdx };
  const header = data[0];
  // 找到「路由」列索引
  const routeIdx = header.findIndex(h => /路由/.test(h));
  const nameIdx  = header.findIndex(h => /页面|名称/.test(h));
  const descIdx  = header.findIndex(h => /说明/.test(h));
  if (routeIdx < 0) return { lines: null, endIdx: startIdx };

  const extra = [];
  for (let r = 2; r < data.length; r++) {
    const row = data[r];
    const route = (row[routeIdx] || '').replace(/`/g, '').trim();
    const name  = (row[nameIdx] || '').trim();
    const desc  = (row[descIdx] || '').trim();
    if (!route) continue;
    const headingNo = `${name} \`/${route}\``;
    extra.push('');
    extra.push(`### ${headingNo}`);
    if (desc) { extra.push(''); extra.push(`> ${desc}`); }
    const ssFile = pickScreenshot(route);
    if (ssFile) {
      extra.push('');
      extra.push(`![${headingNo}](screenshots/${ssFile})`);
    }
  }
  return { lines: extra, endIdx: i, originalRows: rows };
}

// ============ 1. 生成 v1.2 带截图 MD ============
function injectScreenshotsIntoMd(md) {
  const lines = md.split(/\r?\n/);
  const out = [];
  const verRe = /版本：v[\d.]+/;
  for (let i = 0; i < lines.length; i++) {
    let line = lines[i];
    if (i < 6 && verRe.test(line)) line = line.replace(verRe, '版本：v1.2（带 84 张本地截图 / Word 版同源）');
    if (i === 4 && /^\*\*\*$/.test(line)) {
      out.push('');
      out.push('> 📷 本操作文档含 **84 张**本地页面截图，与 MD 同目录下的 `screenshots/` 文件夹一一对应。');
      out.push('>');
      out.push('> 打开方式：');
      out.push('> - **Word 版**：双击「操作文档.docx」查看嵌入版，可直接打印/分发；');
      out.push('> - **Markdown 版**：用 Typora / VSCode / Obsidian 打开本 MD，图片相对路径自动渲染。');
      out.push('');
    }
    out.push(line);

    // §7 统计分析 / §9 人事：在表格后展开成子小节并插入对应截图
    if (/^##\s+(7\.)|^##\s+(9\.)/.test(line)) {
      // 往后跳过几行（引用、段落），直到遇到表格或下一节
      let j = i + 1;
      while (j < lines.length && !/^\s*\|/.test(lines[j]) && !/^##\s+/.test(lines[j])) j++;
      if (j < lines.length && /^\s*\|/.test(lines[j])) {
        // 先输出表格本身（已经由循环到 j 的时刻输出过，但因为循环还没走到 j，先写到这里保证顺序）
        // 实际表格在后续行中，所以这里只需要记录一个展开标记：直接展开表格为子节
        const { lines: extras, endIdx } = expandTableToSections(lines, j);
        if (extras) {
          // 先把原表格行 push 到 out（因为循环还没到 j），再加展开
          for (let k = i + 1; k < endIdx; k++) out.push(lines[k]);
          // 再追加展开的小节
          out.push('');
          out.push('---');
          out.push(...extras);
          i = endIdx - 1;  // 下次循环跳到 endIdx
          continue;
        }
      }
    }

    // 章节标题 ## 1. 首页 `/home` 特殊处理
    if (/^##\s+1\.\s+首页\s+`\/home`/.test(line)) {
      out.push('');
      out.push(`![首页](screenshots/01_home.png)`);
      out.push('');
    }

    // ### X.Y ... `route` 后插入对应截图
    if (/^###\s+/.test(line)) {
      const ssFile = pickForHeading(line);
      if (ssFile) {
        out.push('');
        out.push(`![${line.replace(/^###\s+/, '').trim()}](screenshots/${ssFile})`);
        out.push('');
      }
    }
  }
  return out.join('\n');
}

// ============ 2. MD -> docx ============
function parseInline(text) {
  const runs = [];
  let i = 0;
  while (i < text.length) {
    if (text[i] === '*' && text[i+1] === '*') {
      const end = text.indexOf('**', i + 2);
      if (end < 0) { runs.push(new TextRun({ text: text.slice(i) })); break; }
      runs.push(new TextRun({ text: text.slice(i+2, end), bold: true, font: '微软雅黑' }));
      i = end + 2;
    } else if (text[i] === '`') {
      const end = text.indexOf('`', i + 1);
      if (end < 0) { runs.push(new TextRun({ text: text.slice(i) })); break; }
      runs.push(new TextRun({
        text: text.slice(i+1, end),
        font: 'Consolas',
        color: '1F2937',
        shading: { type: ShadingType.CLEAR, fill: 'F3F4F6' },
        size: 20
      }));
      i = end + 1;
    } else {
      let j = i;
      while (j < text.length) {
        if (text[j] === '*' && text[j+1] === '*') break;
        if (text[j] === '`') break;
        j++;
      }
      if (j > i) runs.push(new TextRun({ text: text.slice(i, j), font: '微软雅黑', size: 21 }));
      i = j;
    }
  }
  if (runs.length === 0) runs.push(new TextRun({ text: '', font: '微软雅黑', size: 21 }));
  return runs;
}

function paragraphFromLine(line, indentLevel=0) {
  const runs = parseInline(line);
  return new Paragraph({
    children: runs,
    spacing: { before: 80, after: 80, line: 360 },
    indent: indentLevel > 0 ? { left: indentLevel * 480 } : undefined
  });
}

function headingLevel(level) {
  return {
    1: HeadingLevel.HEADING_1,
    2: HeadingLevel.HEADING_2,
    3: HeadingLevel.HEADING_3,
    4: HeadingLevel.HEADING_4,
  }[level] || HeadingLevel.HEADING_3;
}

function parseTable(rows) {
  const data = rows.map(r => r.replace(/^\|/, '').replace(/\|$/, '').split('|').map(c => c.trim()));
  if (data.length < 2) return null;
  const isSep = data[1].every(c => /^:?-+:?$/.test(c));
  const body = isSep ? [data[0], ...data.slice(2)] : data;
  const colCount = Math.max(...body.map(r => r.length));
  const colWidthPct = Math.floor(9000 / colCount);

  const border = { style: BorderStyle.SINGLE, size: 6, color: 'D1D5DB' };
  const makeCell = (text, isHeader=false) => new TableCell({
    width: { size: colWidthPct, type: WidthType.DXA },
    borders: { top: border, bottom: border, left: border, right: border },
    shading: isHeader ? { type: ShadingType.CLEAR, fill: 'EEF2FF' } : undefined,
    children: [new Paragraph({
      spacing: { before: 40, after: 40 },
      children: parseInline(text).map(r => isHeader ? new TextRun({ ...r, bold: true }) : r)
    })]
  });

  const [header, ...rest] = body;
  const headerCells = header.map(c => makeCell(c, true));
  while (headerCells.length < colCount) headerCells.push(makeCell('', true));

  const tr = new TableRow({ children: headerCells });
  const tblRows = [tr];
  for (const row of rest) {
    const cells = row.map(c => makeCell(c, false));
    while (cells.length < colCount) cells.push(makeCell('', false));
    tblRows.push(new TableRow({ children: cells }));
  }

  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    rows: tblRows
  });
}

function buildDoc(mdPath, docTitle) {
  const raw = fs.readFileSync(mdPath, 'utf8');
  const isOpsDoc = /操作文档/.test(docTitle);
  const md = isOpsDoc ? injectScreenshotsIntoMd(raw) : raw;

  if (isOpsDoc) {
    const outMd = mdPath.replace(/操作文档\.md$/, '操作文档 v1.2 带截图.md');
    fs.writeFileSync(outMd, md, 'utf8');
    console.log('  v1.2 MD ->', outMd);
  }

  const lines = md.split(/\r?\n/);
  const children = [];

  // 标题页 + 目录
  children.push(
    new Paragraph({ spacing: { before: 3000 } }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 300 },
      children: [new TextRun({ text: docTitle, bold: true, size: 56, font: '微软雅黑' })]
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 200 },
      children: [new TextRun({ text: 'LIS 临床实验室信息系统', size: 28, color: '6B7280', font: '微软雅黑' })]
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 600 },
      children: [new TextRun({ text: `生成日期：${new Date().toLocaleDateString('zh-CN')}`, size: 20, color: '6B7280', font: '微软雅黑' })]
    }),
    new TableOfContents('目录', { hyperlink: true, headingStyleRange: '1-3' }),
    new PageBreak()
  );

  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (/^\s*(\*{3,}|-{3,}|_{3,})\s*$/.test(line.trim())) {
      children.push(new Paragraph({ spacing: { before: 200, after: 200 } }));
      i++; continue;
    }
    if (line.trim() === '') { i++; continue; }

    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) {
      const level = h[1].length;
      const text  = h[2];
      children.push(new Paragraph({
        heading: headingLevel(level),
        spacing: { before: level<=2 ? 480 : 240, after: 200 },
        children: parseInline(text)
      }));
      i++; continue;
    }

    const img = line.match(/^!\[([^\]]*)\]\(([^)]+)\)\s*$/);
    if (img) {
      const rel = img[2].replace(/\//g, path.sep);
      const full = path.resolve(path.dirname(mdPath), rel);
      if (fs.existsSync(full)) {
        try {
          const dim = sizeOf(full);
          const MAX_W = 6_200_000;
          const MAX_H = 3_900_000;
          const W_EMU = dim.width * 9525;
          const H_EMU = dim.height * 9525;
          const scale = Math.min(1, MAX_W / W_EMU, MAX_H / H_EMU);
          const w = Math.round(W_EMU * scale);
          const h2 = Math.round(H_EMU * scale);
          children.push(new Paragraph({
            alignment: AlignmentType.CENTER,
            spacing: { before: 160, after: 160 },
            children: [
              new ImageRun({
                data: fs.readFileSync(full),
                transformation: { width: w / 9525, height: h2 / 9525 },
                type: 'png'
              })
            ]
          }));
          if (img[1]) {
            children.push(new Paragraph({
              alignment: AlignmentType.CENTER,
              spacing: { after: 280 },
              children: [new TextRun({ text: '图：' + img[1], italics: true, size: 18, color: '6B7280', font: '微软雅黑' })]
            }));
          }
        } catch (e) {
          children.push(paragraphFromLine(`[图片加载失败：${full}]`));
        }
      } else {
        children.push(paragraphFromLine(`[图片不存在：${full}]`));
      }
      i++; continue;
    }

    if (/^\s*\|.*\|\s*$/.test(line)) {
      const rows = [];
      while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) rows.push(lines[i++]);
      const table = parseTable(rows);
      if (table) { children.push(table); continue; }
      for (const r of rows) children.push(paragraphFromLine(r));
      continue;
    }

    if (/^\s*>/.test(line)) {
      const buff = [];
      while (i < lines.length && /^\s*>/.test(lines[i])) {
        buff.push(lines[i].replace(/^\s*>\s?/, ''));
        i++;
      }
      for (const b of buff) {
        if (b.trim() === '') continue;
        const runs = parseInline(b);
        children.push(new Paragraph({
          spacing: { before: 60, after: 60, line: 320 },
          indent: { left: 360 },
          shading: { type: ShadingType.CLEAR, fill: 'F5F7FA' },
          children: runs.map(r => new TextRun({ ...r, italics: true }))
        }));
      }
      continue;
    }

    const ol = line.match(/^(\s*)(\d+)\.\s+(.*)$/);
    if (ol) {
      const level = (ol[1].length / 2) | 0;
      while (i < lines.length) {
        const m = lines[i].match(/^(\s*)(\d+)\.\s+(.*)$/);
        if (!m) break;
        const lvl = (m[1].length / 2) | 0;
        const runs = parseInline(`${m[2]}. ${m[3]}`);
        children.push(new Paragraph({
          spacing: { before: 40, after: 40, line: 320 },
          indent: { left: lvl * 480 },
          children: runs
        }));
        i++;
      }
      continue;
    }

    const ul = line.match(/^(\s*)[-*]\s+(.*)$/);
    if (ul) {
      while (i < lines.length) {
        const m = lines[i].match(/^(\s*)[-*]\s+(.*)$/);
        if (!m) break;
        const lvl = (m[1].length / 2) | 0;
        const runs = parseInline('- ' + m[2]);
        children.push(new Paragraph({
          spacing: { before: 40, after: 40, line: 320 },
          indent: { left: lvl * 480 },
          children: runs
        }));
        i++;
      }
      continue;
    }

    children.push(paragraphFromLine(line));
    i++;
  }

  const doc = new Document({
    creator: 'LIS Team',
    title: docTitle,
    styles: {
      default: {
        document: { run: { font: '微软雅黑', size: 21 } }
      },
      headingStyles: {
        heading1: { run: { size: 44, bold: true, color: '0F172A', font: '微软雅黑' }, paragraph: { spacing: { before: 480, after: 240 } } },
        heading2: { run: { size: 34, bold: true, color: '1E3A8A', font: '微软雅黑' }, paragraph: { spacing: { before: 360, after: 200 } } },
        heading3: { run: { size: 26, bold: true, color: '1E40AF', font: '微软雅黑' }, paragraph: { spacing: { before: 240, after: 160 } } }
      }
    },
    sections: [{
      properties: { page: { margin: { top: 1200, bottom: 1200, left: 1200, right: 1200 } } },
      children
    }]
  });

  return doc;
}

async function main() {
  console.log('Building 操作文档.docx ...');
  const docOps = buildDoc(MD_IN, '操作文档');
  const outOps = path.join(ROOT, '操作文档.docx');
  const bufOps = await Packer.toBuffer(docOps);
  fs.writeFileSync(outOps, bufOps);
  console.log('  OK ->', outOps, `(${(bufOps.length/1024/1024).toFixed(2)} MB)`);

  console.log('Building 部署文档.docx ...');
  const docDep = buildDoc(DEPLOY_MD, '部署文档');
  const outDep = path.join(ROOT, '部署文档.docx');
  const bufDep = await Packer.toBuffer(docDep);
  fs.writeFileSync(outDep, bufDep);
  console.log('  OK ->', outDep, `(${(bufDep.length/1024/1024).toFixed(2)} MB)`);
}

main().catch(e => { console.error(e); process.exit(1); });
