const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  HeadingLevel, AlignmentType, LevelFormat, BorderStyle, WidthType,
  ShadingType, PageNumber, Header, Footer
} = require("docx");

const FONT = "Microsoft YaHei";
const PAGE_W = 11906; // A4
const MARGIN = 1440;
const CONTENT_W = PAGE_W - MARGIN * 2; // 9026

// ---------- helpers ----------
function h1(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text, bold: true })] });
}
function h2(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun({ text, bold: true })] });
}
function h3(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun({ text, bold: true })] });
}
function p(text, opts = {}) {
  return new Paragraph({
    spacing: { after: 120, line: 276 },
    children: [new TextRun({ text, font: FONT, size: 21, ...opts })],
  });
}
function bullet(text) {
  return new Paragraph({
    numbering: { reference: "bullets", level: 0 },
    spacing: { after: 60, line: 276 },
    children: [new TextRun({ text, font: FONT, size: 21 })],
  });
}
function numbered(text) {
  return new Paragraph({
    numbering: { reference: "numbers", level: 0 },
    spacing: { after: 60, line: 276 },
    children: [new TextRun({ text, font: FONT, size: 21 })],
  });
}

const border = { style: BorderStyle.SINGLE, size: 1, color: "BFBFBF" };
const borders = { top: border, bottom: border, left: border, right: border };

function cell(text, { bold = false, fill = null, width } = {}) {
  const shading = fill ? { fill, type: ShadingType.CLEAR } : undefined;
  return new TableCell({
    borders,
    width: { size: width, type: WidthType.DXA },
    shading,
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({
      spacing: { after: 0, line: 264 },
      children: [new TextRun({ text, font: FONT, size: 19, bold })],
    })],
  });
}
function headerCell(text, width) {
  return cell(text, { bold: true, fill: "2E75B6", width });
}
function dataCell(text, width) {
  return cell(text, { width });
}

function makeTable(widths, rows) {
  const total = widths.reduce((a, b) => a + b, 0);
  return new Table({
    width: { size: total, type: WidthType.DXA },
    columnWidths: widths,
    rows: rows.map((r, ri) =>
      new TableRow({
        children: r.map((c, ci) =>
          ri === 0 ? headerCell(c, widths[ci]) : dataCell(c, widths[ci])
        ),
      })
    ),
  });
}

// ---------- content ----------
const children = [];

// 封面标题
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 2400, after: 200 },
  children: [new TextRun({ text: "标本转运规则与逻辑文档", bold: true, size: 44, font: FONT, color: "1F3864" })],
}));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { after: 200 },
  children: [new TextRun({ text: "——基于《标本转运业务 + 科室分析》整理", size: 24, font: FONT, color: "595959" })],
}));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  children: [new TextRun({ text: "临床检验标本运送管理 · 规则模型与逻辑设计", size: 20, font: FONT, color: "808080" })],
}));

// ===== 1. 目的与背景 =====
children.push(h1("1. 文档目的与背景"));
children.push(h2("1.1 业务目的"));
children.push(p("为遵循有效的卫生学指导原则，预防细菌、病毒和其他病原微生物的传播和感染，规范医学标本的院内/外运送管理。执行部门覆盖临床科室、医技科室。"));
children.push(h2("1.2 待解决的业务问题"));
children.push(p("标本转运环节当前存在三类典型问题："));
children.push(bullet("标本未及时送达检验科（及时送达率低）。"));
children.push(bullet("送检人员将标本送错科室（送错率高）。"));
children.push(bullet("送检人员将标本弄丢（标本丢失）。"));
children.push(h2("1.3 关键数据支撑"));
children.push(p("根据医院统计：标本采集与转运时间占标本运行时间的约 50%；标本采集后未及时送检占比达 22.1%，严重影响了工作效率并降低了服务质量。因此标本转运环节有必要作为独立流程存在并重点管控。"));
children.push(p("实施“中转点分发”后，可在源头规避接收点核对标本时接错的情况，从源头解决问题，避免问题流转到下一环节增加处理难度。"));

// ===== 2. 核心概念 =====
children.push(h1("2. 核心概念与术语"));
children.push(makeTable(
  [2600, 6426],
  [
    ["术语", "说明"],
    ["采集点（采集科室/采集窗口）", "标本采集发生的地点，如门诊采血窗口、住院病区护士站。"],
    ["执行科室", "实际完成检验的科室，如检验科、检验中心、科研室、儿研室、核医学、生殖科、输血科等。医院常因“专业的人做专业的事、独立核算”而设多个检验性质科室。"],
    ["中转点", "在采集点与检验科之间设立的标本分发节点，用于按执行科室/标本类型分类。"],
    ["转运容器", "试管架、试管、转运箱等，用于盛放与转运标本，需编号与状态管理。"],
    ["转送性质", "1-转送；2-终结；3-混合。标识标本在某执行点是继续流转还是终结。"],
    ["容器状态", "0-未占用；1-占用。"],
    ["送检状态", "0-未送检；1-已送检。"],
  ]
));

// ===== 3. 转运存在性判断逻辑 =====
children.push(h1("3. 标本转运“存在性”判断逻辑（核心规则）"));
children.push(p("转运环节是否存在的实质判断，取决于两个维度："));
children.push(numbered("维度 A：采集科室与检验科是否一体（同一地点、同一科室）。"));
children.push(numbered("维度 B：标本是否由本科室（采集方所在执行科室）执行。"));
children.push(p("判断结论：当且仅当“采集与检验科非一体”或“标本非本科室执行”时，该标本存在跨科室转运需求，需进入转运流程（分发/接收校验）。若采集与检验科一体且标本本科室执行，则无需转运。"));
children.push(makeTable(
  [3009, 3009, 3008],
  [
    ["采集与检验科是否一体", "标本是否本科室执行", "是否需转运"],
    ["是（一体）", "是（本科室）", "否，无需转运"],
    ["是（一体）", "否（非本科室）", "是，需转运"],
    ["否（非一体）", "是（本科室）", "视物理距离，通常需要转运"],
    ["否（非一体）", "否（非本科室）", "是，需转运"],
  ]
));

// ===== 4. 转运模式 =====
children.push(h1("4. 转运模式分类（三种模式）"));
children.push(p("根据是否进行标本分发以及分发的依附点，转运分为三种模式："));
children.push(makeTable(
  [1805, 3610, 3611],
  [
    ["模式", "定义", "适用场景"],
    ["模式 A（无分发）", "采集后直接转运，不在采集点或中转点做分类。", "标本量小、单一执行科室、采集点紧邻检验科。"],
    ["模式 B（采集站分发）", "有分发，分发依附于采集站，无独立中转点。", "采集点具备分类条件，采集人员可承担分发。"],
    ["模式 C（中转点分发）", "有分发，分发依附于中转点。", "标本量大、多执行科室，需集中分发以从源头规避接错。"],
  ]
));
children.push(p("说明：采集方式（门诊/住院/体检）与转运模式共同决定实际流转路径；门诊、住院、体检各自的转运流程在此框架下分别适配。"));

// ===== 5. 分发规则模型（方案一） =====
children.push(h1("5. 标本分发规则模型（方案一：源头分发）"));
children.push(p("方案一的核心思想：在采集点/中转点，将标本按“标本类型 + 执行科室”分好类，从源头确保送到检验科的标本准确、可追溯；即便出现问题也能定位责任人。"));

children.push(h2("5.1 基础设置（数据模型）"));
children.push(h3("主体表：标本容器"));
children.push(makeTable(
  [3009, 2408, 3609],
  [
    ["字段名称", "字段类型", "说明"],
    ["容器ID", "VARCHAR2(20)", "主键"],
    ["容器名称", "VARCHAR2(100)", "—"],
    ["上级容器ID", "VARCHAR2(36)", "—"],
    ["容器类型ID", "VARCHAR2(36)", "外键"],
    ["执行科室ID", "VARCHAR2(36)", "外键（执行点ID）"],
  ]
));
children.push(h3("主体表：容器类型"));
children.push(makeTable(
  [3009, 2408, 3609],
  [
    ["字段名称", "字段类型", "说明"],
    ["容器类型ID", "VARCHAR2(36)", "主键"],
    ["容器类型名称", "VARCHAR2(100)", "试管架/转运箱等"],
    ["行值", "NUMBER(2)", "架子行列定位"],
    ["列值", "NUMBER(2)", "架子行列定位"],
  ]
));
children.push(h3("主体表：容器使用记录（业务表）"));
children.push(makeTable(
  [3009, 2408, 3609],
  [
    ["字段名称", "字段类型", "说明"],
    ["容器使用记录ID", "VARCHAR2(36)", "主键"],
    ["上级容器ID", "VARCHAR2(36)", "外键"],
    ["容器ID", "VARCHAR2(36)", "外键"],
    ["标本ID", "NUMBER(2)", "—"],
    ["标本条码", "NUMBER(2)", "—"],
    ["容器状态", "NUMBER(2)", "0-未占用，1-占用"],
    ["送检状态", "NUMBER(2)", "0-未送检，1-已送检"],
    ["执行科室ID", "VARCHAR2(36)", "冗余"],
  ]
));

children.push(h2("5.2 计算转运基本规则（使用分发模式）"));
children.push(p("采集扫码后，系统按如下逻辑计算标本应放置的位置："));
children.push(numbered("采集扫码后，查询当前标本的执行科室。"));
children.push(numbered("读取当前转运容器状态，计算出该标本应放的位置（哪个容器、哪个架子、哪个位置），并提示操作员放入对应容器/架子/位置。"));

children.push(h2("5.3 服务要求（信息化处理要点）"));
children.push(h3("（1）基础设置部分（容器信息初始化，可选）"));
children.push(makeTable(
  [1805, 2609, 4612],
  [
    ["服务名称", "主要参数", "服务作用/处理逻辑"],
    ["容器类型保存服务", "入参：容器类型基本信息", "初始化存放容器类型"],
    ["容器信息保存服务", "入参：容器信息、执行科ID", "初始化存放容器"],
    ["容器与采集点对照服务(可选)", "入参：容器ID、采集点ID", "用于采集时推荐相对固定的几个执行科室"],
    ["采集点与执行点对照服务(可选)", "入参：采集点ID、执行点ID", "用于采集时推荐相对固定的几个执行科室"],
  ]
));
children.push(h3("（2）推荐分发位置"));
children.push(makeTable(
  [2408, 3208, 3410],
  [
    ["服务名称", "主要参数", "服务作用/处理逻辑"],
    ["计算标本分发存放位置的服务", "入参：标本ID、执行科室ID；出参：容器ID、位置信息", "采集扫条码时推荐应放位置，实现标本分发"],
    ["转运容器使用登记服务", "入参：容器ID", "确定当前标本存放的容器"],
  ]
));
children.push(h3("（3）标本接收前校验"));
children.push(makeTable(
  [2408, 3208, 3410],
  [
    ["服务名称", "主要参数", "服务作用/处理逻辑"],
    ["校验标本执行科室", "入参：标本ID、标本条码、当前执行科室ID；出参：异常提示", "查询标本正确执行科室并对照，不一致则提示正确科室及时间约束"],
    ["打印非本执行科室标本明细的服务", "入参：标本ID", "打印标本明细供送检人员核对"],
  ]
));

// ===== 6. 接收校验规则（方案二） =====
children.push(h1("6. 标本接收校验规则（方案二：接收点提示）"));
children.push(p("方案二的核心思想：若医院未在接收前完成分类/分发，则在标本接收环节，将“非本科执行”的标本提示出来（或打印清单），告知应转送的科室与有效时长，做好进入检验科前的最后一道把关。"));
children.push(numbered("检查当前标本执行科室与本科室是否一致；非本科室的标本进行提示。"));
children.push(numbered("提示内容应包含：应送到哪个科室、有效时长（时间约束）是多少。"));
children.push(numbered("将非本执行科室的标本形成清单，支持打印，供接收室与送检人员核对。"));
children.push(numbered("避免标本流入核收环节才被发现为非本科标本，导致标本失效或需退回重采。"));

// ===== 7. 标本类型/时限/保存 =====
children.push(h1("7. 标本类型、时限与保存规则（卫生学要求）"));
children.push(p("不同标本对运送人员、时限、保存温度、容器有明确要求；为及时送达，多数标本固定时间统一送检，少有分批。"));
children.push(h2("7.1 各类标本运送时限与保存温度"));
children.push(makeTable(
  [2408, 2009, 2409, 2200],
  [
    ["标本类型", "运送时限", "保存温度", "运送容器"],
    ["痰标本", "≤2h", "延迟：4°C，≤24h", "专用贮存箱"],
    ["血标本", "≤2h", "全血/红细胞 2-8°C；冰冻血浆/冷沉淀 冰冻；血小板 20-24°C 轻摇；冰冻红细胞 ≤-65°C", "专用贮存箱"],
    ["尿标本", "≤2h", "延迟：4°C，≤24h", "专用贮存箱"],
    ["粪便标本", "≤2h", "室温≤2h；延迟：20°C以下，≤24h", "专用贮存箱"],
    ["脑脊液标本", "≤15min", "正常：4°C，≤24h", "专用贮存箱"],
    ["穿刺液标本", "≤15min", "正常：4°C，≤10天；必要时-20°C", "专用贮存箱"],
    ["组织标本", "≤15min", "立即 -80°C 或液氮", "专用贮存箱"],
    ["精液/前列腺液标本", "≤2h", "25-35°C", "专用贮存箱"],
    ["胃液/十二指肠引流液", "≤15min", "18-25°C", "专用贮存箱"],
    ["阴道分泌物", "≤15min", "25-35°C", "专用贮存箱"],
  ]
));
children.push(h2("7.2 时限敏感项目的送检要求"));
children.push(bullet("采样后须立即送检：血氨、血沉、血气分析、酸性磷酸酶、乳酸、各种细菌培养（尤其厌氧菌培养）。"));
children.push(bullet("采样后 0.5h 内送检：血糖、电解质、血液细胞学、体液细胞学、涂片找细菌/霉菌等。"));
children.push(bullet("采集后 1-2h 内送检：蛋白质类、色素类、激素类、脂类、酶类、抗原、抗体测定等。"));
children.push(bullet("采集后 2h 以上才能送检者，须采取保存手段（如血糖/乳酸分离血清后冷冻或用 NaF 稳定；K+ 分离血清密封 2-8°C；一般项目加盖密封 2-8°C，但血沉与细胞学不可用此法）。"));
children.push(h2("7.3 保存时长规则"));
children.push(bullet("标本保存 1 个月：检测物分离后 -20°C 存放。"));
children.push(bullet("标本需长期保存（3 个月以上）：检测物分离后 -70°C 保存，避免反复冻融。"));

// ===== 8. 包装与人员 =====
children.push(h1("8. 包装与人员资质规则"));
children.push(h2("8.1 运送包装"));
children.push(bullet("院内统一配置标本专用转运箱，标有生物危险标识、警告与提示用语。"));
children.push(bullet("院内两层包装：主容器（装样本管/瓶，用吸收/缓冲材料固定并保持直立）+ 运输容器（金属/塑料，耐压力灭菌或化学消毒，盖有垫圈、密闭、防水、防漏，定期清除污染）。"));
children.push(bullet("外送三层包装：主容器（防水防漏塑料管/瓶+信息标签）+ 辅助容器（防水防漏密闭塑料容器）+ 外包装（专用箱，内放冰排，附检验申请单，贴生物危险标识），主/辅容器间填足量吸收材料。"));
children.push(h2("8.2 人员资质"));
children.push(bullet("院内转运：须经过系统病原微生物实验室生物安全培训，掌握转运流程与生物安全防护知识。"));
children.push(bullet("运送高致病性病原微生物菌（毒）种或样本：须持《高致病性病原微生物菌（毒）种或样本准运证书》，护送人员不得少于两人。"));
children.push(bullet("外送标本：专车、专人、持证上岗。"));
children.push(h2("8.3 运送管理基本原则"));
children.push(p("医院实行标本运送专人管理、密闭容器转运、固定行走路线、严格标本交接登记，并有防污染应急措施。"));

// ===== 9. 方案对比 =====
children.push(h1("9. 方案对比与选型结论"));
children.push(h2("9.1 方案一（源头分发）"));
children.push(p("优势："));
children.push(bullet("定义送检容器后，系统可自动计算并推荐当前试管/容器应放位置（试管架/箱子）。"));
children.push(bullet("可精准跟踪每个标本的具体位置，实现实时监控。"));
children.push(bullet("从源头控制，确保送达检验科的标本准确率与质量。"));
children.push(bullet("可跟踪出错环节、明确责任人。"));
children.push(bullet("可明确标本的执行科室。"));
children.push(p("可能的问题："));
children.push(bullet("实施成本：初始化送检容器虽工作量不大，但增加与医院沟通成本。"));
children.push(bullet("医院管理：采集点可能不愿承担分发工作，管理复杂、增工作量。"));
children.push(bullet("资源成本：容器需编号；对时限要求高的标本容器可能未放满即须送出，容器利用率降低、所需容器更多。"));
children.push(bullet("三方机构：若送检外包给三方物流，医院不关心转运环节问题。"));
children.push(bullet("成效：前期限制多，发挥的作用可能有限，是否得不偿失存疑。"));
children.push(h2("9.2 方案二（接收点提示）"));
children.push(p("优势："));
children.push(bullet("在标本接收时做好进入检验科的最后一道把关。"));
children.push(bullet("可提示并打印清单，告知哪些标本非本科、应送往哪里、截止时限。"));
children.push(p("可能的问题："));
children.push(bullet("存在以箱子方式送来但含多个科室标本，接收室不逐一扫描标本的情况。"));
children.push(bullet("若送检起始环节未将标本与转运箱关联，接收时扫描箱子无法读出非本科标本清单。"));
children.push(h2("9.3 总体结论"));
children.push(p("综合考虑：在标本转运前端环节将标本分发清楚可能成效有限，但方案一可保留评估；无论前期是否使用中转/分发，或是否采用方案一规范分发，方案二（接收点提示）都可使用，均能在一定程度上减少转运/接收带来的错误。"));

// ===== 10. 与执行科室规则关联 =====
children.push(h1("10. 与执行科室规则的关联（逻辑衔接）"));
children.push(p("标本转运的“执行科室”是分发与接收校验的依据；其本身由“执行科室规则模型”计算得出。"));
children.push(h2("10.1 执行科室计算基本规则"));
children.push(numbered("传入检验项目/检验项目ID（不能为空）。"));
children.push(numbered("若“是否单个执行科室”标志为 1，直接返回该执行科室名称及信息。"));
children.push(numbered("若存在多个执行科室，依 项目→指标→设备→执行科室 的对照关系查出满足条件的科室列表；若多个科室满足，则进入优先级判断。"));
children.push(h2("10.2 优先级设置规则（建议顺序）"));
children.push(makeTable(
  [1405, 3409, 4212],
  [
    ["优先级", "因素", "规则说明"],
    ["1", "默认执行科室", "按病人属性判断（如急诊标志→急诊检验科），命中即直接推荐，不再走后续判断。"],
    ["2", "院区", "同一项目多院区可做时，优先推荐当前院区。"],
    ["3", "排班时间", "默认筛选出处于上班状态的执行科室。"],
    ["4", "TAT 转运时间", "依基础数据/统计得出最大/最小/平均耗时，推荐路线最近、耗时最短的科室。"],
    ["5", "标本接收数量阈值", "多科室满足时，选择当前标本积压量最小的科室。"],
  ]
));
children.push(p("以上各因素可设置启用/停用，并自定义优先级顺序。"));
children.push(h2("10.3 数据结构支撑"));
children.push(p("通过 部门表↔设备表↔指标表↔项目表 的一对多对照关系，下达任一检验项目时均可关联出可执行的设备与执行科室列表，为分发与校验提供数据基础。"));

// ===== 11. 待解决问题 =====
children.push(h1("11. 已知问题与待解决项"));
children.push(makeTable(
  [4208, 4818],
  [
    ["问题", "建议方案"],
    ["采集扫标本条码时，若执行科室太多，同一采集窗口可能将标本送到不同转运箱，且物理位置相隔较远，带来巨大困扰。", "设置参数，让某个窗口采集的标本指定默认执行科室，避免上述问题并实现准确分发。"],
    ["转运容器分类较多，容器编号使用困难；新用容器需先扫编码，箱子/架子还需扫码关联，使用不便。", "不过度细分，只要确定最终转运箱送过去的是同一执行科室即可。"],
    ["以箱子送来却含多个科室标本，接收时扫描箱子无法读出非本科标本清单。", "在送检起始环节将标本与转运箱关联起来，使接收扫描可读出清单。"],
    ["多个检验性质科室项目交叉时，执行科室设置冗余且复杂（一对多、多对多）。", "完善执行科室规则模型中“优先级 + 默认执行科室”机制，降低冗余设置。"],
    ["多院区时行政上只设同一检验科，开医嘱时无法确定实际执行院区。", "在执行点基础设置中维护“所属院区”，由优先级“院区”因素自动推荐。"],
  ]
));

// ---------- numbering config ----------
const doc = new Document({
  styles: {
    default: { document: { run: { font: FONT, size: 21 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 30, bold: true, font: FONT, color: "1F3864" },
        paragraph: { spacing: { before: 280, after: 160 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 26, bold: true, font: FONT, color: "2E5496" },
        paragraph: { spacing: { before: 200, after: 120 }, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 23, bold: true, font: FONT, color: "404040" },
        paragraph: { spacing: { before: 140, after: 80 }, outlineLevel: 2 } },
    ],
  },
  numbering: {
    config: [
      { reference: "bullets",
        levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 600, hanging: 300 } } } }] },
      { reference: "numbers",
        levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 600, hanging: 300 } } } }] },
    ],
  },
  sections: [{
    properties: {
      page: {
        size: { width: PAGE_W, height: 16838 }, // A4
        margin: { top: MARGIN, right: MARGIN, bottom: MARGIN, left: MARGIN },
      },
    },
    headers: {
      default: new Header({ children: [new Paragraph({
        alignment: AlignmentType.RIGHT,
        children: [new TextRun({ text: "标本转运规则与逻辑文档", size: 16, font: FONT, color: "808080" })],
      })] }),
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [
          new TextRun({ text: "第 ", size: 16, font: FONT, color: "808080" }),
          new TextRun({ children: [PageNumber.CURRENT], size: 16, font: FONT, color: "808080" }),
          new TextRun({ text: " 页", size: 16, font: FONT, color: "808080" }),
        ],
      })] }),
    },
    children,
  }],
});

const out = "D:/traeitem/labWork/ALLcontrll/docs/标本转运规则和逻辑文档.docx";
Packer.toBuffer(doc).then((buffer) => {
  fs.writeFileSync(out, buffer);
  console.log("OK saved:", out, "bytes=", buffer.length);
});
