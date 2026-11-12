#!/usr/bin/env node
/**
 * 仪器数据模拟发送工具
 * ------------------------------------------------
 * 用于向 LIS 系统的仪器数据接收模块发送模拟检验结果,
 * 报文遵循 ASTM E1394/E1381 协议, 与后端 ASTMParser 解析规则一致。
 *
 * 两种发送方式:
 *   1. TCP 模式 (默认, 最接近真实仪器): 直连 LIS 的 TCP 数据接收端口(默认 9100)。
 *      注意: 后端按客户端 IP 匹配 lis_instrument 台账, 需提前将本机 IP 配置到仪器台账,
 *      否则结果仅记录通讯日志, 不会匹配保存。
 *   2. HTTP 模式: 调用 仪器数据手动接收接口 /api/instrument/data/receive,
 *      需提供 instrumentId 与 brand。
 *
 * 用法示例:
 *   node simulator.js --list                                   # 列出内置检验项目组
 *   node simulator.js --panel 血常规 --sample BC202608080001   # TCP 发送血常规
 *   node simulator.js --panel 生化 --sample BC202608080001 --random
 *   node simulator.js --panel 自定义 --sample S01 --values "WBC=6.5,RBC=4.8,HGB=140"
 *   node simulator.js --mode http --instrument-id 1 --brand MINDRAY --panel 血常规 --sample BC202608080001
 *   node simulator.js --count 5 --sleep 2000 --panel 血常规 --sample BC202608080001   # 循环发送
 */
'use strict';
const net = require('net');
const http = require('http');

const DEFAULT_TCP_HOST = '127.0.0.1';
const DEFAULT_TCP_PORT = 9100;
const DEFAULT_HTTP_BASE = 'http://localhost:8080';

/* ============================ 内置检验项目组 ============================ */
// 每个项目: [检验代码, 名称, 单位, 参考范围, 默认值]
// 参考范围格式 "低-高", 用于生成异常标志 (N正常/H偏高/L偏低)
const PANELS = {
  '血常规': {
    code: 'CBP',
    name: '血常规',
    items: [
      ['WBC',     '白细胞计数',         '10^9/L', '3.5-9.5',  '6.5'],
      ['RBC',     '红细胞计数',         '10^12/L','4.3-5.8',  '4.8'],
      ['HGB',     '血红蛋白',           'g/L',    '130-175',  '140'],
      ['HCT',     '红细胞压积',         '%',      '40-50',    '45'],
      ['MCV',     '平均红细胞体积',     'fL',     '82-100',   '90'],
      ['MCH',     '平均红细胞血红蛋白量','pg',    '27-34',    '30'],
      ['MCHC',    '平均红细胞血红蛋白浓度','g/L', '316-354',  '335'],
      ['RDW',     '红细胞分布宽度',     '%',      '11-16',    '13'],
      ['PLT',     '血小板计数',         '10^9/L', '125-350',  '220'],
      ['PDW',     '血小板分布宽度',     'fL',     '9-17',     '12'],
      ['MPV',     '平均血小板体积',     'fL',     '9-13',     '10.5'],
      ['PCT',     '血小板压积',         '%',      '0.108-0.282','0.22'],
      ['NEUT',    '中性粒细胞计数',     '10^9/L', '1.8-6.3',  '3.8'],
      ['LYMPH',   '淋巴细胞计数',       '10^9/L', '1.1-3.2',  '2.0'],
      ['MONO',    '单核细胞计数',       '10^9/L', '0.1-0.6',  '0.4'],
      ['EOS',     '嗜酸性粒细胞计数',   '10^9/L', '0.02-0.52','0.15'],
      ['BASO',    '嗜碱性粒细胞计数',   '10^9/L', '0-0.06',   '0.03'],
      ['NEUT_P',  '中性粒细胞百分比',   '%',      '40-75',    '58'],
      ['LYMPH_P', '淋巴细胞百分比',     '%',      '20-50',    '31'],
      ['MONO_P',  '单核细胞百分比',     '%',      '3-10',     '6'],
      ['EOS_P',   '嗜酸性粒细胞百分比', '%',      '0.4-8',    '2.3'],
      ['BASO_P',  '嗜碱性粒细胞百分比', '%',      '0-1',      '0.5'],
    ],
  },

  '尿常规': {
    code: 'URINE',
    name: '尿常规',
    items: [
      ['U_COLOR',   '尿颜色',   '',       '',   '黄色'],
      ['U_CLARITY', '尿透明度', '',       '',   '透明'],
      ['U_GLU',     '尿葡萄糖', '',       '阴性', '阴性'],
      ['U_PRO',     '尿蛋白',   '',       '阴性', '阴性'],
      ['U_BLD',     '尿隐血',   '',       '阴性', '阴性'],
      ['U_LEU',     '尿白细胞', '',       '阴性', '阴性'],
      ['U_SG',      '尿比重',   '',       '1.003-1.030', '1.020'],
      ['U_PH',      '尿酸碱度', '',       '4.6-8.0', '6.0'],
      ['U_M_RBC',   '尿镜检红细胞', '/HP', '0-3', '1'],
      ['U_M_WBC',   '尿镜检白细胞', '/HP', '0-5', '2'],
      ['U_M_EPI',   '尿镜检上皮细胞', '/HP', '0-3', '1'],
      ['U_M_CAST',  '尿镜检管型', '/HP',  '阴性', '阴性'],
      ['U_M_BACT',  '尿镜检细菌', '/HP',  '阴性', '阴性'],
    ],
  },

  '大便常规': {
    code: 'STOOL',
    name: '大便常规',
    items: [
      ['S_APP',   '大便外观',   '',  '', '软'],
      ['S_COLOR', '大便颜色',   '',  '', '黄褐色'],
      ['S_OB',    '大便隐血',   '',  '阴性', '阴性'],
      ['S_WBC',   '大便白细胞', '/HP', '0-3', '1'],
      ['S_RBC',   '大便红细胞', '/HP', '0-3', '0'],
      ['S_PUS',   '大便脓细胞', '/HP', '阴性', '阴性'],
      ['S_OVA',   '大便虫卵',   '',  '阴性', '阴性'],
      ['S_MUC',   '大便粘液',   '',  '阴性', '阴性'],
    ],
  },

  '生化': {
    code: 'CHEM',
    name: '生化',
    items: [
      ['GLU',  '葡萄糖',   'mmol/L', '3.9-6.1', '5.2'],
      ['ALT',  '谷丙转氨酶','U/L',   '9-50',    '25'],
      ['AST',  '谷草转氨酶','U/L',   '15-40',   '22'],
      ['TP',   '总蛋白',   'g/L',   '65-85',   '72'],
      ['ALB',  '白蛋白',   'g/L',   '40-55',   '46'],
      ['TBIL', '总胆红素', 'umol/L','3.4-17.1','12'],
      ['DBIL', '直接胆红素','umol/L','0-6.8',  '3'],
      ['CRE', '肌酐',     'umol/L','57-97',   '75'],
      ['BUN',  '尿素',     'mmol/L','2.9-8.2', '5.5'],
      ['UA',   '尿酸',     'umol/L','208-428', '320'],
    ],
  },
};

/* ============================ 命令行参数解析 ============================ */
function parseArgs(argv) {
  const args = { mode: 'tcp', host: DEFAULT_TCP_HOST, port: DEFAULT_TCP_PORT,
                 httpBase: DEFAULT_HTTP_BASE, brand: 'MINDRAY', count: 1, sleep: 0 };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => argv[++i];
    switch (a) {
      case '--mode': args.mode = next(); break;
      case '--host': args.host = next(); break;
      case '--port': args.port = parseInt(next(), 10); break;
      case '--panel': args.panel = next(); break;
      case '--sample': args.sample = next(); break;
      case '--values': args.values = next(); break;
      case '--random': args.random = true; break;
      case '--count': args.count = parseInt(next(), 10) || 1; break;
      case '--sleep': args.sleep = parseInt(next(), 10) || 0; break;
      case '--http-base': args.httpBase = next(); break;
      case '--instrument-id': args.instrumentId = next(); break;
      case '--brand': args.brand = next(); break;
      case '--json': args.json = true; break;
      case '--list': args.list = true; break;
      case '--help': args.help = true; break;
      default:
        if (a.startsWith('-')) { console.error('未知参数: ' + a); args.help = true; }
    }
  }
  return args;
}

/* ============================ 工具函数 ============================ */
function rnd(min, max, digits) {
  const v = min + Math.random() * (max - min);
  return v.toFixed(digits === undefined ? 1 : digits);
}

// 根据参考范围计算异常标志: 正常 N / 偏高 H / 偏低 L
function abnormalFlag(valueStr, refRange) {
  if (!refRange || !refRange.includes('-')) return 'N';
  const v = Number(valueStr);
  if (isNaN(v)) return 'N';
  const [lo, hi] = refRange.split('-').map(Number);
  if (!isNaN(hi) && v > hi) return 'H';
  if (!isNaN(lo) && v < lo) return 'L';
  return 'N';
}

// 解析 --values "WBC=6.5,RBC=4.8" 覆盖默认值/随机值
function parseOverrides(valuesStr) {
  const map = {};
  if (!valuesStr) return map;
  for (const pair of valuesStr.split(',')) {
    const idx = pair.indexOf('=');
    if (idx > 0) map[pair.slice(0, idx).trim().toUpperCase()] = pair.slice(idx + 1).trim();
  }
  return map;
}

// 生成一个千分位可读的随机样本号 (若未提供)
function genSampleNo() {
  return 'SIM' + Date.now().toString().slice(-8);
}

/* ============================ ASTM 报文构造 ============================ */
/**
 * 构造 ASTM H/P/O/R/L 报文。
 * 与后端 ASTMParser 解析规则一致:
 *   O 记录: O|seq|样本号^^^||...   (字段2 第一个 ^ 分量为样本号)
 *   R 记录: R|seq|^^^testCode|value|unit|refRange|abnormalFlag|...
 *   L 记录: L|1|N   (结束标志)
 */
function buildAstmMessage(panel, sampleNo, itemEntries) {
  const now = new Date();
  const dateStr = now.toISOString().slice(0, 10).replace(/-/g, '');
  const lines = [];
  lines.push('H|\\^&|||SIMULATOR^^^^|||LIS||||||P|1|' + dateStr);
  lines.push('P|1|||SIM^Patient||19800101|M|||||^||');
  // O 记录: 样本号放字段2第一个分量
  lines.push('O|1|' + sampleNo + '^^^||' + panel.name + '|N|' +
             now.toISOString().slice(0, 14).replace(/[-T:]/g, '') + '||||||N||||||||||||||O');
  // R 记录
  let seq = 1;
  for (const it of itemEntries) {
    const [code, , unit, refRange, value] = it;
    const flag = abnormalFlag(value, refRange);
    lines.push('R|' + (seq++) + '|^^^' + code + '|' + value + '|' + unit + '|' +
               (refRange || '') + '|' + flag + '||F|||||||||');
  }
  lines.push('L|1|N');
  return lines.join('\n') + '\n';
}

// 解析检验项目组, 应用随机/覆盖
function resolveItems(panel, args, overrides) {
  const items = PANELS[panel];
  if (!items) throw new Error('未知检验项目组: ' + panel + ', 可用: ' + Object.keys(PANELS).join('/'));
  return items.items.map(it => {
    const code = it[0];
    let value = it[4];
    if (overrides[code]) {
      value = overrides[code];
    } else if (args.random) {
      const ref = it[3];
      if (ref && ref.includes('-') && !isNaN(Number(ref.split('-')[0]))) {
        const [lo, hi] = ref.split('-').map(Number);
        value = rnd(lo, hi, 1);
      }
    }
    return [it[0], it[1], it[2], it[3], value];
  });
}

/* ============================ 发送 (TCP) ============================ */
function sendTcp(host, port, message) {
  return new Promise((resolve, reject) => {
    const socket = new net.Socket();
    const timer = setTimeout(() => { socket.destroy(); reject(new Error('TCP 连接超时')); }, 5000);
    socket.setTimeout(5000);
    socket.once('connect', () => {
      clearTimeout(timer);
      socket.write(message, () => {
        // 发送成功后等待服务端处理后关闭
        setTimeout(() => { socket.end(); }, 300);
      });
    });
    socket.on('data', (d) => { /* 接收服务端应答(可选) */ });
    socket.on('timeout', () => socket.destroy());
    socket.on('close', () => resolve());
    socket.on('error', (e) => { clearTimeout(timer); reject(e); });
    socket.connect(port, host);
  });
}

/* ============================ 发送 (HTTP) ============================ */
function sendHttp(base, instrumentId, brand, rawData) {
  return new Promise((resolve, reject) => {
    const url = new URL(base + '/api/instrument/data/receive');
    url.searchParams.set('instrumentId', instrumentId);
    url.searchParams.set('brand', brand);
    // 以 text/plain 直接发送原始报文, 保留换行符, 否则换行被 JSON 转义导致后端无法解析
    const body = rawData;
    const req = http.request(url, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain', 'Content-Length': Buffer.byteLength(body) },
    }, (res) => {
      let b = '';
      res.on('data', (c) => b += c);
      res.on('end', () => resolve({ status: res.statusCode, body: b }));
    });
    req.on('error', reject);
    req.end(body);
  });
}

/* ============================ 主流程 ============================ */
async function main() {
  const args = parseArgs(process.argv.slice(2));

  if (args.help) {
    console.log(`
仪器数据模拟发送工具
用法: node simulator.js [选项]

  --list                列出内置检验项目组并退出
  --mode <tcp|http>     发送方式 (默认 tcp)
  --host <ip>            TCP 目标地址 (默认 127.0.0.1)
  --port <n>             TCP 目标端口 (默认 9100)
  --panel <名称>         检验项目组: ${Object.keys(PANELS).join('/')}/自定义
  --sample <条码/样本号> 标本条码或样本号 (必须已在系统内核收)
  --values "A=1,B=2"     覆盖指定检验代码的结果值
  --random               参考范围内随机生成结果值
  --count <n>            循环发送次数 (默认 1)
  --sleep <ms>           循环发送间隔毫秒 (默认 0)
  // HTTP 模式专用:
  --http-base <url>      后端地址 (默认 ${DEFAULT_HTTP_BASE})
  --instrument-id <id>   仪器ID (HTTP 模式必填)
  --brand <品牌>          仪器品牌 (HTTP 模式, 默认 MINDRAY)

示例:
  node simulator.js --list
  node simulator.js --panel 血常规 --sample BC202608080001
  node simulator.js --panel 生化 --sample BC202608080001 --random --count 3 --sleep 2000
  node simulator.js --mode http --instrument-id 1 --brand MINDRAY --panel 血常规 --sample BC202608080001
`);
    return;
  }

  if (args.list) {
    console.log('内置检验项目组:');
    for (const [name, p] of Object.entries(PANELS)) {
      console.log('  ' + name + ' (' + p.code + '): ' + p.items.map(i => i[0]).join(', '));
    }
    return;
  }

  const panel = args.panel || '血常规';
  const sampleNo = args.sample || genSampleNo();
  const overrides = parseOverrides(args.values);

  if (panel === '自定义' && !args.values) {
    console.error('自定义模式需通过 --values 提供结果, 例如 "WBC=6.5,RBC=4.8"');
    return;
  }

  const items = resolveItems(panel, args, overrides);
  const message = buildAstmMessage({ code: PANELS[panel].code, name: panel }, sampleNo, items);

  console.log('检验项目组: ' + panel);
  console.log('样本号/条码: ' + sampleNo);
  console.log('发送次数: ' + args.count + (args.sleep ? ' (间隔 ' + args.sleep + 'ms)' : ''));
  console.log('项目明细:');
  for (const it of items) {
    console.log('  ' + it[0].padEnd(8) + it[1].padEnd(12) + ' = ' + it[4] + ' ' + it[2] +
      ' [' + (it[3] || '-') + ']');
  }
  if (args.json) {
    console.log('\n===== ASTM 报文 =====');
    console.log(message);
  }

  for (let i = 0; i < args.count; i++) {
    try {
      if (args.mode === 'http') {
        if (!args.instrumentId) throw new Error('HTTP 模式需提供 --instrument-id');
        const res = await sendHttp(args.httpBase, args.instrumentId, args.brand, message);
        console.log(`[${i + 1}/${args.count}] HTTP 发送完成: status=${res.status}, body=${res.body.slice(0, 120)}`);
      } else {
        await sendTcp(args.host, args.port, message);
        console.log(`[${i + 1}/${args.count}] TCP 发送完成: ${args.host}:${args.port} (${message.length} bytes)`);
      }
    } catch (e) {
      console.error(`[${i + 1}/${args.count}] 发送失败: ` + e.message);
    }
    if (i < args.count - 1 && args.sleep > 0) {
      await new Promise(r => setTimeout(r, args.sleep));
    }
  }
}

main().catch(e => { console.error('发生错误: ' + e.message); process.exit(1); });