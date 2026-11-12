#!/usr/bin/env node
/**
 * 标本转运规则校验 / 执行脚本
 * ---------------------------------------------------------------------------
 * 运行环境：Node.js >= 16（ESM），零外部依赖。
 * 规则来源：docs/标本转运规则-智能体可执行.md 中 ````rules ```` 围栏内的 JSON 块。
 *
 * 用法：
 *   node specimen-rules-validator.mjs --test
 *   node specimen-rules-validator.mjs --scenario ./scenario.json
 *   node specimen-rules-validator.mjs --scenario '{"specimen_type":"脑脊液标本", ...}'
 *   cat scenario.json | node specimen-rules-validator.mjs
 *
 * 作者：软件开发团队 / 工程师 寇豆码(Kou)
 */

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/** 默认规则 Markdown 文件路径（与本脚本同目录）。 */
export const DEFAULT_RULES_FILE = path.join(__dirname, '标本转运规则-智能体可执行.md');

/** 匹配 ````rules ```` 围栏块（四个反引号 + rules）。 */
const RULES_BLOCK_RE = /^`{4}rules[ \t]*\n([\s\S]*?)\n`{4}[ \t]*$/m;

/** 规则集必需的顶层字段。 */
const REQUIRED_RULE_FIELDS = [
  'meta',
  'concepts',
  'existence_logic',
  'transport_modes',
  'dispatch_rules',
  'receiving_validation',
  'specimen_types',
  'time_sensitive_projects',
  'packaging',
  'personnel_qualification',
  'exec_dept_priority',
];

/** 违规码常量表。 */
export const VIOLATION_CODES = {
  UNKNOWN_SPECIMEN_TYPE: 'UNKNOWN_SPECIMEN_TYPE',
  MISSING_ELAPSED: 'MISSING_ELAPSED',
  TIME_LIMIT_EXCEEDED: 'TIME_LIMIT_EXCEEDED',
  MISSING_TRANSPORT_MODE: 'MISSING_TRANSPORT_MODE',
  UNKNOWN_TRANSPORT_MODE: 'UNKNOWN_TRANSPORT_MODE',
  UNKNOWN_PACKAGING_SCOPE: 'UNKNOWN_PACKAGING_SCOPE',
  INSUFFICIENT_PACKAGING: 'INSUFFICIENT_PACKAGING',
  PERSONNEL_NOT_QUALIFIED: 'PERSONNEL_NOT_QUALIFIED',
  PROJECT_TIME_REQUIREMENT_EXCEEDED: 'PROJECT_TIME_REQUIREMENT_EXCEEDED',
  RECEIVING_DEPT_MISMATCH: 'RECEIVING_DEPT_MISMATCH',
};

// ---------------------------------------------------------------------------
// 1. 规则加载
// ---------------------------------------------------------------------------

/**
 * 从 JSON 解析错误中还原出行/列位置，便于定位规则块中的语法问题。
 * @param {string} text 被解析的 JSON 文本
 * @param {Error} err JSON.parse 抛出的错误
 * @returns {string} 人类可读的定位描述
 */
function describeJsonError(text = '', err = new Error('')) {
  const message = String(err && err.message ? err.message : err);
  const match = /position\s+(\d+)/i.exec(message);
  if (!match) return message;
  const pos = Number(match[1]);
  const before = text.slice(0, pos);
  const line = before.split('\n').length;
  const column = pos - before.lastIndexOf('\n');
  const snippet = text.split('\n')[line - 1] || '';
  return `${message}（第 ${line} 行，第 ${column} 列）\n  > ${snippet.trim()}`;
}

/**
 * 校验规则对象结构完整性。
 * @param {object} rules 规则对象
 * @param {string} sourcePath 来源文件路径（用于报错）
 * @returns {object} 原样返回规则对象
 */
export function assertRulesShape(rules, sourcePath = '(memory)') {
  if (!rules || typeof rules !== 'object' || Array.isArray(rules)) {
    throw new Error(`[规则结构错误] ${sourcePath}: rules 块必须是一个 JSON 对象。`);
  }
  const missing = REQUIRED_RULE_FIELDS.filter((field) => !(field in rules));
  if (missing.length > 0) {
    throw new Error(`[规则结构错误] ${sourcePath}: 缺少必需字段 -> ${missing.join(', ')}`);
  }
  if (!Array.isArray(rules.specimen_types) || rules.specimen_types.length === 0) {
    throw new Error(`[规则结构错误] ${sourcePath}: specimen_types 必须是非空数组。`);
  }
  if (!Array.isArray(rules.existence_logic?.conditions) || rules.existence_logic.conditions.length === 0) {
    throw new Error(`[规则结构错误] ${sourcePath}: existence_logic.conditions 必须是非空数组。`);
  }
  return rules;
}

/**
 * 读取 Markdown 文件并提取 ````rules ```` 块，返回解析后的规则对象。
 * @param {string} mdPath Markdown 文件路径
 * @returns {object} 规则对象
 */
export function loadRules(mdPath = DEFAULT_RULES_FILE) {
  const resolved = path.resolve(mdPath);
  if (!fs.existsSync(resolved)) {
    throw new Error(`[规则文件不存在] 找不到规则 Markdown：${resolved}\n  请确认路径，或使用 --rules <path> 指定。`);
  }

  let raw = '';
  try {
    raw = fs.readFileSync(resolved, 'utf8');
  } catch (err) {
    throw new Error(`[规则文件读取失败] ${resolved}\n  原因：${err.message}`);
  }

  // 统一换行，避免 CRLF 影响围栏匹配
  const normalized = raw.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const matched = RULES_BLOCK_RE.exec(normalized);
  if (!matched) {
    throw new Error(
      `[规则块缺失] ${resolved} 中未找到 \`\`\`\`rules 围栏块。\n` +
        '  要求：以单独一行的 ````rules 开始，以单独一行的 ```` 结束，中间为合法 JSON。',
    );
  }

  const jsonText = matched[1];
  let parsed = null;
  try {
    parsed = JSON.parse(jsonText);
  } catch (err) {
    throw new Error(`[规则 JSON 解析失败] ${resolved}\n  ${describeJsonError(jsonText, err)}`);
  }

  return assertRulesShape(parsed, resolved);
}

// ---------------------------------------------------------------------------
// 2. 查询辅助
// ---------------------------------------------------------------------------

/**
 * 判断值是否为有效数字。
 * @param {*} value 任意值
 * @returns {boolean} 是否为有限数字
 */
function isFiniteNumber(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

/**
 * 按名称或别名查找标本类型定义。
 * @param {object} rules 规则对象
 * @param {string} specimenType 标本类型名称
 * @returns {object|null} 标本类型定义或 null
 */
export function findSpecimenType(rules, specimenType = '') {
  const key = String(specimenType || '').trim();
  if (!key) return null;
  const list = Array.isArray(rules.specimen_types) ? rules.specimen_types : [];
  return (
    list.find((item) => item.type === key) ||
    list.find((item) => Array.isArray(item.aliases) && item.aliases.includes(key)) ||
    null
  );
}

/**
 * 查找转运模式定义，支持 "C" / "c" / "模式C" / 名称 四种写法。
 * @param {object} rules 规则对象
 * @param {string} mode 模式标识
 * @returns {object|null} 模式定义或 null
 */
export function findTransportMode(rules, mode = '') {
  const raw = String(mode || '').trim();
  if (!raw) return null;
  const key = raw.replace(/^模式/, '').toUpperCase();
  const list = Array.isArray(rules.transport_modes) ? rules.transport_modes : [];
  return list.find((item) => String(item.id).toUpperCase() === key) || list.find((item) => item.name === raw) || null;
}

/**
 * 查找时限敏感项目所属档位。
 * @param {object} rules 规则对象
 * @param {string} projectName 项目名称
 * @returns {object|null} 档位定义或 null
 */
export function findTimeSensitiveTier(rules, projectName = '') {
  const key = String(projectName || '').trim();
  if (!key) return null;
  const list = Array.isArray(rules.time_sensitive_projects) ? rules.time_sensitive_projects : [];
  return list.find((tier) => Array.isArray(tier.projects) && tier.projects.some((p) => p === key || p.includes(key))) || null;
}

/**
 * 依据 existence_logic 判定是否需要转运。
 * @param {object} rules 规则对象
 * @param {boolean} integrated 采集科室与检验科是否一体
 * @param {boolean} byOwnDept 是否由本科室执行
 * @returns {{needs_transport: boolean, matched_rule: object|null}} 判定结果
 */
export function decideNeedsTransport(rules, integrated = false, byOwnDept = false) {
  const logic = rules.existence_logic || {};
  const conditions = Array.isArray(logic.conditions) ? logic.conditions : [];
  const hit = conditions.find(
    (cond) =>
      Boolean(cond?.input?.collection_lab_integrated) === Boolean(integrated) &&
      Boolean(cond?.input?.specimen_executed_by_collecting_dept) === Boolean(byOwnDept),
  );
  if (hit) {
    return {
      needs_transport: Boolean(hit.needs_transport),
      matched_rule: { id: hit.id || null, note: hit.note || '', input: hit.input },
    };
  }
  return {
    needs_transport: logic.default_needs_transport !== false,
    matched_rule: null,
  };
}

/**
 * 按 exec_dept_priority 从候选执行科室中推荐一个。
 * @param {object} rules 规则对象
 * @param {Array<object>} candidates 候选科室数组
 * @param {object} context 上下文（current_campus 等）
 * @returns {{selected: object|null, trace: Array<object>}} 推荐结果与决策轨迹
 */
export function recommendExecutingDepartment(rules, candidates = [], context = {}) {
  const trace = [];
  let pool = Array.isArray(candidates) ? candidates.filter(Boolean) : [];
  if (pool.length === 0) return { selected: null, trace };

  const priorities = Array.isArray(rules.exec_dept_priority)
    ? [...rules.exec_dept_priority].sort((a, b) => (a.priority || 0) - (b.priority || 0))
    : [];

  for (const rule of priorities) {
    if (pool.length <= 1) break;
    let next = pool;
    switch (rule.key) {
      case 'default_dept': {
        const defaults = pool.filter((d) => d.is_default === true);
        if (defaults.length > 0) {
          trace.push({ priority: rule.priority, factor: rule.factor, result: '命中默认执行科室，终止后续规则' });
          return { selected: defaults[0], trace };
        }
        next = pool;
        break;
      }
      case 'campus': {
        if (context.current_campus) {
          const sameCampus = pool.filter((d) => d.campus === context.current_campus);
          if (sameCampus.length > 0) next = sameCampus;
        }
        break;
      }
      case 'schedule': {
        const onDuty = pool.filter((d) => d.on_duty === true);
        if (onDuty.length > 0) next = onDuty;
        break;
      }
      case 'tat': {
        const withTat = pool.filter((d) => isFiniteNumber(d.tat_minutes));
        if (withTat.length > 0) {
          const min = Math.min(...withTat.map((d) => d.tat_minutes));
          next = withTat.filter((d) => d.tat_minutes === min);
        }
        break;
      }
      case 'backlog': {
        const withBacklog = pool.filter((d) => isFiniteNumber(d.backlog_count));
        if (withBacklog.length > 0) {
          const min = Math.min(...withBacklog.map((d) => d.backlog_count));
          next = withBacklog.filter((d) => d.backlog_count === min);
        }
        break;
      }
      default:
        next = pool;
        break;
    }
    trace.push({ priority: rule.priority, factor: rule.factor, remaining: next.length });
    pool = next;
  }

  return { selected: pool[0] || null, trace };
}

// ---------------------------------------------------------------------------
// 3. 场景评估
// ---------------------------------------------------------------------------

/**
 * 归一化场景输入，补齐默认值（默认取保守值）。
 * @param {object} scenario 原始场景
 * @returns {object} 归一化后的场景
 */
function normalizeScenario(scenario = {}) {
  const s = scenario && typeof scenario === 'object' ? scenario : {};
  return {
    specimen_type: typeof s.specimen_type === 'string' ? s.specimen_type.trim() : '',
    collection_lab_integrated: Boolean(s.collection_lab_integrated),
    specimen_executed_by_collecting_dept: Boolean(s.specimen_executed_by_collecting_dept),
    elapsed_minutes: isFiniteNumber(s.elapsed_minutes) ? s.elapsed_minutes : null,
    transport_mode: typeof s.transport_mode === 'string' && s.transport_mode.trim() ? s.transport_mode.trim() : null,
    packaging: typeof s.packaging === 'string' && s.packaging.trim() ? s.packaging.trim() : 'in_hospital',
    packaging_layers: isFiniteNumber(s.packaging_layers) ? s.packaging_layers : null,
    is_high_pathogen: Boolean(s.is_high_pathogen),
    personnel_trained: Boolean(s.personnel_trained),
    has_transport_permit: Boolean(s.has_transport_permit),
    escort_count: isFiniteNumber(s.escort_count) ? s.escort_count : 0,
    project_name: typeof s.project_name === 'string' && s.project_name.trim() ? s.project_name.trim() : null,
    executing_department_id: s.executing_department_id ?? null,
    executing_department_name: s.executing_department_name ?? null,
    receiving_department_id: s.receiving_department_id ?? null,
    valid_duration_minutes: isFiniteNumber(s.valid_duration_minutes) ? s.valid_duration_minutes : null,
    candidate_departments: Array.isArray(s.candidate_departments) ? s.candidate_departments : [],
    current_campus: s.current_campus ?? null,
  };
}

/**
 * 依据人员资质规则条目生成违规信息。
 * @param {object} rules 规则对象
 * @param {string} id 资质规则 id
 * @param {string} extra 附加说明
 * @returns {{code: string, rule_id: string, message: string}} 违规项
 */
function personnelViolation(rules, id, extra = '') {
  const list = Array.isArray(rules.personnel_qualification) ? rules.personnel_qualification : [];
  const item = list.find((x) => x.id === id);
  const requirement = item ? item.requirement : id;
  return {
    code: VIOLATION_CODES.PERSONNEL_NOT_QUALIFIED,
    rule_id: id,
    message: `人员资质缺失：${requirement}${extra ? `（${extra}）` : ''}`,
  };
}

/**
 * 对一个标本转运场景做全量规则判定。
 * @param {object} rules 由 loadRules 得到的规则对象
 * @param {object} scenario 场景对象
 * @returns {object} 结构化判定结果
 */
export function evaluateScenario(rules, scenario = {}) {
  if (!rules || typeof rules !== 'object') {
    throw new Error('[参数错误] evaluateScenario: rules 必须为 loadRules 返回的规则对象。');
  }
  const s = normalizeScenario(scenario);
  const violations = [];

  // --- 3.1 转运存在性 ---
  const existence = decideNeedsTransport(rules, s.collection_lab_integrated, s.specimen_executed_by_collecting_dept);
  const needsTransport = existence.needs_transport;

  // --- 3.2 标本类型与运送时限 ---
  const specimen = findSpecimenType(rules, s.specimen_type);
  let timeLimitMinutes = null;
  let timeWithinLimit = null;

  if (!specimen) {
    violations.push({
      code: VIOLATION_CODES.UNKNOWN_SPECIMEN_TYPE,
      rule_id: 'SPECIMEN-TYPE',
      message: `未知标本类型：「${s.specimen_type || '(未提供)'}」，无法查得运送时限与保存温度，请核对规则表。`,
    });
  } else {
    timeLimitMinutes = isFiniteNumber(specimen.time_limit_minutes) ? specimen.time_limit_minutes : null;
    if (timeLimitMinutes === null) {
      timeWithinLimit = null;
    } else if (s.elapsed_minutes === null) {
      violations.push({
        code: VIOLATION_CODES.MISSING_ELAPSED,
        rule_id: 'SPECIMEN-TIME',
        message: '缺少或非法的 elapsed_minutes，无法判定运送时限合规性。',
      });
    } else {
      timeWithinLimit = s.elapsed_minutes <= timeLimitMinutes;
      if (!timeWithinLimit) {
        violations.push({
          code: VIOLATION_CODES.TIME_LIMIT_EXCEEDED,
          rule_id: 'SPECIMEN-TIME',
          message: `超出运送时限：${specimen.type} 要求 ≤${timeLimitMinutes} 分钟，实际已耗时 ${s.elapsed_minutes} 分钟。`,
        });
      }
    }
  }

  // --- 3.3 转运模式 ---
  let transportModeOk = true;
  let recommendedNote = '';
  const modeDef = s.transport_mode ? findTransportMode(rules, s.transport_mode) : null;

  if (!needsTransport) {
    recommendedNote = `无需转运（${existence.matched_rule?.note || '采集科室与检验科一体且由本科室执行'}）。`;
    if (s.transport_mode && !modeDef) {
      transportModeOk = false;
      violations.push({
        code: VIOLATION_CODES.UNKNOWN_TRANSPORT_MODE,
        rule_id: 'TRANSPORT-MODE',
        message: `未知转运模式：「${s.transport_mode}」，合法取值为 A / B / C。`,
      });
    } else if (s.transport_mode && modeDef) {
      recommendedNote += `当前已指定「${modeDef.id}-${modeDef.name}」，请确认其必要性。`;
    }
  } else if (!s.transport_mode) {
    transportModeOk = false;
    violations.push({
      code: VIOLATION_CODES.MISSING_TRANSPORT_MODE,
      rule_id: 'TRANSPORT-MODE',
      message: '需要转运但未指定转运模式（transport_mode），请从 A / B / C 中选择。',
    });
    recommendedNote = '请根据标本量、执行科室数量与采集点距离选择转运模式。';
  } else if (!modeDef) {
    transportModeOk = false;
    violations.push({
      code: VIOLATION_CODES.UNKNOWN_TRANSPORT_MODE,
      rule_id: 'TRANSPORT-MODE',
      message: `未知转运模式：「${s.transport_mode}」，合法取值为 A / B / C。`,
    });
  } else {
    transportModeOk = true;
    const applicable = Array.isArray(modeDef.applicable_when) ? modeDef.applicable_when.join('、') : '';
    recommendedNote = `${modeDef.id}-${modeDef.name}：${modeDef.definition}${applicable ? `；适用于：${applicable}` : ''}。`;
  }

  // --- 3.4 包装要求 ---
  const packagingSpec = rules.packaging && rules.packaging[s.packaging] ? rules.packaging[s.packaging] : null;
  let packagingRequired = null;
  let packagingOk = true;

  if (!packagingSpec) {
    packagingOk = false;
    violations.push({
      code: VIOLATION_CODES.UNKNOWN_PACKAGING_SCOPE,
      rule_id: 'PACKAGING',
      message: `未知包装场景：「${s.packaging}」，合法取值为 in_hospital / out_send。`,
    });
  } else {
    packagingRequired = {
      key: s.packaging,
      scope: packagingSpec.scope,
      level: packagingSpec.level,
      level_text: packagingSpec.level_text,
      layers: packagingSpec.layers,
      container: packagingSpec.container,
      marks: packagingSpec.marks,
    };
    if (s.packaging_layers !== null && s.packaging_layers < packagingSpec.level) {
      packagingOk = false;
      violations.push({
        code: VIOLATION_CODES.INSUFFICIENT_PACKAGING,
        rule_id: 'PACKAGING',
        message: `包装等级不足：${packagingSpec.scope}要求${packagingSpec.level}层（${packagingSpec.layers.join(' + ')}），当前仅 ${s.packaging_layers} 层。`,
      });
    }
  }

  // --- 3.5 人员资质 ---
  let personnelOk = true;
  if (!s.personnel_trained) {
    personnelOk = false;
    violations.push(personnelViolation(rules, 'PQ-01'));
  }
  if (s.is_high_pathogen) {
    if (!s.has_transport_permit) {
      personnelOk = false;
      violations.push(personnelViolation(rules, 'PQ-02'));
    }
    if (s.escort_count < 2) {
      personnelOk = false;
      violations.push(personnelViolation(rules, 'PQ-03', `当前护送人数 ${s.escort_count} 人`));
    }
  }
  if (s.packaging === 'out_send' && !s.has_transport_permit) {
    personnelOk = false;
    violations.push(personnelViolation(rules, 'PQ-04'));
  }

  // --- 3.6 时限敏感项目 ---
  const tier = s.project_name ? findTimeSensitiveTier(rules, s.project_name) : null;
  let projectRequirement = null;
  if (tier) {
    projectRequirement = { tier: tier.tier, requirement: tier.requirement, max_minutes: tier.max_minutes ?? null };
    if (isFiniteNumber(tier.max_minutes) && s.elapsed_minutes !== null && s.elapsed_minutes > tier.max_minutes) {
      violations.push({
        code: VIOLATION_CODES.PROJECT_TIME_REQUIREMENT_EXCEEDED,
        rule_id: `TIME-SENSITIVE-${tier.tier}`,
        message: `超出项目时效要求：「${s.project_name}」${tier.requirement}（≤${tier.max_minutes} 分钟），实际已耗时 ${s.elapsed_minutes} 分钟。`,
      });
    }
  }

  // --- 3.7 接收点校验（方案二） ---
  let receivingCheck = null;
  if (s.executing_department_id !== null && s.receiving_department_id !== null) {
    const consistent = String(s.executing_department_id) === String(s.receiving_department_id);
    receivingCheck = {
      consistent,
      should_send_to: s.executing_department_name || s.executing_department_id,
      valid_duration_minutes: s.valid_duration_minutes ?? timeLimitMinutes,
    };
    if (!consistent) {
      violations.push({
        code: VIOLATION_CODES.RECEIVING_DEPT_MISMATCH,
        rule_id: 'PLAN-2',
        message: `标本执行科室与接收科室不一致，应送至「${receivingCheck.should_send_to}」，有效时长 ${receivingCheck.valid_duration_minutes ?? '未知'} 分钟。`,
      });
    }
  }

  // --- 3.8 执行科室推荐（规则 8） ---
  const recommendation =
    s.candidate_departments.length > 0
      ? recommendExecutingDepartment(rules, s.candidate_departments, { current_campus: s.current_campus })
      : null;

  return {
    scenario: s,
    needs_transport: needsTransport,
    matched_existence_rule: existence.matched_rule,
    transport_mode_ok: transportModeOk,
    recommended_note: recommendedNote,
    specimen_type_known: Boolean(specimen),
    time_limit_minutes: timeLimitMinutes,
    time_limit_text: specimen ? specimen.time_limit_text : null,
    storage_temp: specimen ? specimen.storage_temp : null,
    container: specimen ? specimen.container : null,
    time_sensitive: specimen ? Boolean(specimen.time_sensitive) : null,
    elapsed_minutes: s.elapsed_minutes,
    time_within_limit: timeWithinLimit,
    packaging_required: packagingRequired,
    packaging_ok: packagingOk,
    personnel_ok: personnelOk,
    project_time_requirement: projectRequirement,
    receiving_validation: receivingCheck,
    executing_department_recommendation: recommendation,
    violations,
    compliant: violations.length === 0,
  };
}

// ---------------------------------------------------------------------------
// 4. 内置自测
// ---------------------------------------------------------------------------

/** 简易断言收集器。 */
class TestRunner {
  constructor() {
    /** @type {Array<{name: string, error: string}>} */
    this.failures = [];
    this.passed = 0;
    this.current = '';
  }

  /**
   * 运行一个测试用例。
   * @param {string} name 用例名称
   * @param {Function} fn 用例函数
   * @returns {void}
   */
  run(name, fn) {
    this.current = name;
    try {
      fn(this);
      this.passed += 1;
      console.log(`  ✓ ${name}`);
    } catch (err) {
      this.failures.push({ name, error: err.message });
      console.log(`  ✗ ${name}\n      ${err.message}`);
    }
  }

  /**
   * 断言相等（严格相等）。
   * @param {*} actual 实际值
   * @param {*} expected 期望值
   * @param {string} label 描述
   * @returns {void}
   */
  eq(actual, expected, label = '') {
    if (actual !== expected) {
      throw new Error(`${label || '断言失败'}：期望 ${JSON.stringify(expected)}，实际 ${JSON.stringify(actual)}`);
    }
  }

  /**
   * 断言为真。
   * @param {*} value 值
   * @param {string} label 描述
   * @returns {void}
   */
  ok(value, label = '') {
    if (!value) throw new Error(`${label || '断言失败'}：期望为真，实际 ${JSON.stringify(value)}`);
  }

  /**
   * 断言 violations 中存在包含指定文本的项。
   * @param {Array<object>} violations 违规数组
   * @param {string} text 期望包含的文本
   * @returns {void}
   */
  hasViolationText(violations, text) {
    const hit = violations.some((v) => String(v.message).includes(text));
    if (!hit) {
      throw new Error(`期望 violations 含「${text}」，实际为：${JSON.stringify(violations.map((v) => v.message))}`);
    }
  }

  /**
   * 断言 violations 中存在指定 code。
   * @param {Array<object>} violations 违规数组
   * @param {string} code 违规码
   * @returns {void}
   */
  hasViolationCode(violations, code) {
    const hit = violations.some((v) => v.code === code);
    if (!hit) {
      throw new Error(`期望 violations 含 code=${code}，实际为：${JSON.stringify(violations.map((v) => v.code))}`);
    }
  }

  /**
   * 断言 violations 中不存在指定 code。
   * @param {Array<object>} violations 违规数组
   * @param {string} code 违规码
   * @returns {void}
   */
  noViolationCode(violations, code) {
    const hit = violations.some((v) => v.code === code);
    if (hit) {
      throw new Error(`期望 violations 不含 code=${code}，实际为：${JSON.stringify(violations.map((v) => v.message))}`);
    }
  }
}

/**
 * 运行内置自测。
 * @param {string} mdPath 规则文件路径
 * @returns {number} 进程退出码（0 表示全部通过）
 */
export function runSelfTest(mdPath = DEFAULT_RULES_FILE) {
  console.log('标本转运规则校验脚本 · 内置自测');
  console.log(`规则文件：${path.resolve(mdPath)}\n`);

  const t = new TestRunner();
  let rules = null;

  t.run('T00 loadRules 能提取并解析 ````rules ```` 块', () => {
    rules = loadRules(mdPath);
    t.ok(rules && typeof rules === 'object', 'rules 应为对象');
    t.eq(rules.meta.name, '标本转运规则集', 'meta.name');
  });

  if (!rules) {
    console.log('\n规则加载失败，后续用例无法执行。');
    return 1;
  }

  t.run('T01 规则完整性：10 种标本类型 / 4 条存在性条件 / 3 种模式 / 5 级优先级', () => {
    t.eq(rules.specimen_types.length, 10, 'specimen_types 数量');
    t.eq(rules.existence_logic.conditions.length, 4, 'existence_logic.conditions 数量');
    t.eq(rules.transport_modes.length, 3, 'transport_modes 数量');
    t.eq(rules.exec_dept_priority.length, 5, 'exec_dept_priority 数量');
    t.eq(rules.time_sensitive_projects.length, 4, 'time_sensitive_projects 档位数量');
  });

  t.run('T02 脑脊液标本 elapsed=10min mode=C → 需转运、未超时、无超时违规', () => {
    const r = evaluateScenario(rules, {
      specimen_type: '脑脊液标本',
      collection_lab_integrated: false,
      specimen_executed_by_collecting_dept: false,
      elapsed_minutes: 10,
      transport_mode: 'C',
      packaging: 'in_hospital',
      is_high_pathogen: false,
      personnel_trained: true,
    });
    t.eq(r.needs_transport, true, 'needs_transport');
    t.eq(r.time_limit_minutes, 15, 'time_limit_minutes');
    t.eq(r.time_within_limit, true, 'time_within_limit');
    t.eq(r.transport_mode_ok, true, 'transport_mode_ok');
    t.noViolationCode(r.violations, VIOLATION_CODES.TIME_LIMIT_EXCEEDED);
    t.eq(r.compliant, true, 'compliant');
  });

  t.run('T03 痰标本 elapsed=130min(>120) → 超时且 violations 含「超出运送时限」', () => {
    const r = evaluateScenario(rules, {
      specimen_type: '痰标本',
      collection_lab_integrated: false,
      specimen_executed_by_collecting_dept: false,
      elapsed_minutes: 130,
      transport_mode: 'B',
      packaging: 'in_hospital',
      personnel_trained: true,
    });
    t.eq(r.time_limit_minutes, 120, 'time_limit_minutes');
    t.eq(r.time_within_limit, false, 'time_within_limit');
    t.hasViolationCode(r.violations, VIOLATION_CODES.TIME_LIMIT_EXCEEDED);
    t.hasViolationText(r.violations, '超出运送时限');
    t.eq(r.compliant, false, 'compliant');
  });

  t.run('T04 高致病性标本 + personnel_trained=false → personnel_ok=false 且含资质缺失', () => {
    const r = evaluateScenario(rules, {
      specimen_type: '血标本',
      collection_lab_integrated: false,
      specimen_executed_by_collecting_dept: false,
      elapsed_minutes: 30,
      transport_mode: 'A',
      packaging: 'in_hospital',
      is_high_pathogen: true,
      personnel_trained: false,
    });
    t.eq(r.personnel_ok, false, 'personnel_ok');
    t.hasViolationCode(r.violations, VIOLATION_CODES.PERSONNEL_NOT_QUALIFIED);
    t.hasViolationText(r.violations, '人员资质缺失');
    t.hasViolationText(r.violations, '生物安全培训');
    t.hasViolationText(r.violations, '《准运证书》');
    t.hasViolationText(r.violations, '护送人员不少于2人');
  });

  t.run('T05 一体 + 本科室执行 → needs_transport=false', () => {
    const r = evaluateScenario(rules, {
      specimen_type: '血标本',
      collection_lab_integrated: true,
      specimen_executed_by_collecting_dept: true,
      elapsed_minutes: 5,
      packaging: 'in_hospital',
      personnel_trained: true,
    });
    t.eq(r.needs_transport, false, 'needs_transport');
    t.eq(r.transport_mode_ok, true, 'transport_mode_ok');
    t.eq(r.matched_existence_rule.id, 'EXIST-001-1', 'matched_existence_rule.id');
    t.eq(r.compliant, true, 'compliant');
  });

  t.run('T06 未知类型「XX标本」→ violations 含「未知标本类型」且时限为 null', () => {
    const r = evaluateScenario(rules, {
      specimen_type: 'XX标本',
      collection_lab_integrated: false,
      specimen_executed_by_collecting_dept: false,
      elapsed_minutes: 10,
      transport_mode: 'A',
      packaging: 'in_hospital',
      personnel_trained: true,
    });
    t.eq(r.specimen_type_known, false, 'specimen_type_known');
    t.eq(r.time_limit_minutes, null, 'time_limit_minutes');
    t.eq(r.time_within_limit, null, 'time_within_limit');
    t.hasViolationCode(r.violations, VIOLATION_CODES.UNKNOWN_SPECIMEN_TYPE);
    t.hasViolationText(r.violations, '未知标本类型');
  });

  t.run('T07 其余三种存在性组合均需转运', () => {
    const combos = [
      [true, false],
      [false, true],
      [false, false],
    ];
    for (const [a, b] of combos) {
      const r = evaluateScenario(rules, {
        specimen_type: '尿标本',
        collection_lab_integrated: a,
        specimen_executed_by_collecting_dept: b,
        elapsed_minutes: 10,
        transport_mode: 'B',
        personnel_trained: true,
      });
      t.eq(r.needs_transport, true, `A=${a},B=${b} needs_transport`);
    }
  });

  t.run('T08 外送包装要求三层；仅两层 → 包装等级不足', () => {
    const r = evaluateScenario(rules, {
      specimen_type: '组织标本',
      collection_lab_integrated: false,
      specimen_executed_by_collecting_dept: false,
      elapsed_minutes: 5,
      transport_mode: 'C',
      packaging: 'out_send',
      packaging_layers: 2,
      personnel_trained: true,
      has_transport_permit: true,
    });
    t.eq(r.packaging_required.level, 3, 'out_send 包装层数');
    t.eq(r.packaging_ok, false, 'packaging_ok');
    t.hasViolationCode(r.violations, VIOLATION_CODES.INSUFFICIENT_PACKAGING);
    t.hasViolationText(r.violations, '包装等级不足');
  });

  t.run('T09 外送但无《准运证书》→ 触发 PQ-04 资质违规', () => {
    const r = evaluateScenario(rules, {
      specimen_type: '血标本',
      collection_lab_integrated: false,
      specimen_executed_by_collecting_dept: false,
      elapsed_minutes: 30,
      transport_mode: 'C',
      packaging: 'out_send',
      personnel_trained: true,
      has_transport_permit: false,
    });
    t.eq(r.personnel_ok, false, 'personnel_ok');
    t.hasViolationText(r.violations, '外送转运须专车专人并持有效证件');
  });

  t.run('T10 时限敏感项目「血糖」elapsed=45min(>30) → 超出项目时效要求', () => {
    const r = evaluateScenario(rules, {
      specimen_type: '血标本',
      collection_lab_integrated: false,
      specimen_executed_by_collecting_dept: false,
      elapsed_minutes: 45,
      transport_mode: 'B',
      packaging: 'in_hospital',
      personnel_trained: true,
      project_name: '血糖',
    });
    t.eq(r.time_within_limit, true, '血标本 120min 内');
    t.eq(r.project_time_requirement.tier, 'T2', '项目档位');
    t.hasViolationCode(r.violations, VIOLATION_CODES.PROJECT_TIME_REQUIREMENT_EXCEEDED);
    t.hasViolationText(r.violations, '超出项目时效要求');
  });

  t.run('T11 接收点校验：执行科室与接收科室不一致 → 提示应送科室', () => {
    const r = evaluateScenario(rules, {
      specimen_type: '尿标本',
      collection_lab_integrated: false,
      specimen_executed_by_collecting_dept: false,
      elapsed_minutes: 20,
      transport_mode: 'C',
      packaging: 'in_hospital',
      personnel_trained: true,
      executing_department_id: 'DEP-核医学',
      executing_department_name: '核医学',
      receiving_department_id: 'DEP-检验科',
    });
    t.eq(r.receiving_validation.consistent, false, 'receiving consistent');
    t.hasViolationCode(r.violations, VIOLATION_CODES.RECEIVING_DEPT_MISMATCH);
    t.hasViolationText(r.violations, '应送至「核医学」');
  });

  t.run('T12 执行科室优先级：命中默认执行科室即终止后续规则', () => {
    const { selected, trace } = recommendExecutingDepartment(
      rules,
      [
        { id: 'D1', name: '急诊检验科', is_default: true, campus: 'B院区', on_duty: false, tat_minutes: 30, backlog_count: 99 },
        { id: 'D2', name: '检验中心', is_default: false, campus: 'A院区', on_duty: true, tat_minutes: 5, backlog_count: 1 },
      ],
      { current_campus: 'A院区' },
    );
    t.eq(selected.id, 'D1', '应选中默认执行科室');
    t.eq(trace[trace.length - 1].priority, 1, '决策在优先级 1 终止');
  });

  t.run('T13 执行科室优先级：无默认时依次按院区/排班/TAT/积压筛选', () => {
    const { selected } = recommendExecutingDepartment(
      rules,
      [
        { id: 'D1', name: 'A院区-检验科', is_default: false, campus: 'A院区', on_duty: true, tat_minutes: 20, backlog_count: 3 },
        { id: 'D2', name: 'A院区-检验中心', is_default: false, campus: 'A院区', on_duty: true, tat_minutes: 8, backlog_count: 7 },
        { id: 'D3', name: 'B院区-检验科', is_default: false, campus: 'B院区', on_duty: true, tat_minutes: 2, backlog_count: 0 },
      ],
      { current_campus: 'A院区' },
    );
    t.eq(selected.id, 'D2', '应选中同院区中 TAT 最短者');
  });

  t.run('T14 别名与容错：「精液」可命中「精液/前列腺液标本」，「模式c」可命中模式 C', () => {
    const r = evaluateScenario(rules, {
      specimen_type: '精液',
      collection_lab_integrated: false,
      specimen_executed_by_collecting_dept: false,
      elapsed_minutes: 60,
      transport_mode: '模式c',
      packaging: 'in_hospital',
      personnel_trained: true,
    });
    t.eq(r.specimen_type_known, true, 'specimen_type_known');
    t.eq(r.time_limit_minutes, 120, 'time_limit_minutes');
    t.eq(r.transport_mode_ok, true, 'transport_mode_ok');
    t.eq(r.compliant, true, 'compliant');
  });

  t.run('T15 需转运但未指定转运模式 → transport_mode_ok=false', () => {
    const r = evaluateScenario(rules, {
      specimen_type: '粪便标本',
      collection_lab_integrated: false,
      specimen_executed_by_collecting_dept: false,
      elapsed_minutes: 30,
      packaging: 'in_hospital',
      personnel_trained: true,
    });
    t.eq(r.transport_mode_ok, false, 'transport_mode_ok');
    t.hasViolationCode(r.violations, VIOLATION_CODES.MISSING_TRANSPORT_MODE);
  });

  const total = t.passed + t.failures.length;
  console.log('');
  if (t.failures.length === 0) {
    console.log(`ALL TESTS PASSED (${t.passed}/${total})`);
    return 0;
  }
  console.log(`TESTS FAILED: ${t.failures.length}/${total}`);
  for (const f of t.failures) {
    console.log(`  - ${f.name}\n      ${f.error}`);
  }
  return 1;
}

// ---------------------------------------------------------------------------
// 5. CLI
// ---------------------------------------------------------------------------

const USAGE = `
标本转运规则校验 / 执行脚本 (Node ESM, 零依赖)

用法：
  node specimen-rules-validator.mjs --test
      运行内置自测；全部通过打印 ALL TESTS PASSED 并以 0 退出，否则以 1 退出。

  node specimen-rules-validator.mjs --scenario <file.json>
  node specimen-rules-validator.mjs --scenario '<json字符串>'
  cat scenario.json | node specimen-rules-validator.mjs
      评估一个转运场景，输出 JSON（2 空格缩进）。

可选参数：
  --rules <path>   指定规则 Markdown（默认：同目录下 标本转运规则-智能体可执行.md）
  --strict         场景不合规时以退出码 2 结束（默认始终 0）
  --help, -h       显示本帮助

场景字段（未提供时取保守默认值）：
  specimen_type                        string  标本类型，如 "脑脊液标本"
  collection_lab_integrated            bool    采集科室与检验科是否一体
  specimen_executed_by_collecting_dept bool    是否由本科室执行
  elapsed_minutes                      number  采集至今已耗时（分钟）
  transport_mode                       string  "A" | "B" | "C"
  packaging                            string  "in_hospital" | "out_send"
  packaging_layers                     number  实际包装层数（可选，用于等级校验）
  is_high_pathogen                     bool    是否高致病性病原微生物
  personnel_trained                    bool    是否已完成生物安全培训
  has_transport_permit                 bool    是否持《准运证书》/ 外送有效证件
  escort_count                         number  护送人数
  project_name                         string  检验项目名（用于时限敏感档位判定）
  executing_department_id/name         string  标本执行科室（接收校验用）
  receiving_department_id              string  当前接收科室（接收校验用）
  candidate_departments                array   候选执行科室（执行科室优先级推荐用）
  current_campus                       string  当前院区

示例：
  node specimen-rules-validator.mjs --scenario '{"specimen_type":"脑脊液标本","collection_lab_integrated":false,"specimen_executed_by_collecting_dept":false,"elapsed_minutes":10,"transport_mode":"C","packaging":"in_hospital","is_high_pathogen":false,"personnel_trained":true}'
`.trim();

/**
 * 解析命令行参数。
 * @param {Array<string>} argv 参数数组
 * @returns {object} 解析结果
 */
function parseArgs(argv = []) {
  const opts = { test: false, help: false, strict: false, scenario: null, rules: DEFAULT_RULES_FILE };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--test') opts.test = true;
    else if (arg === '--help' || arg === '-h') opts.help = true;
    else if (arg === '--strict') opts.strict = true;
    else if (arg === '--scenario') {
      opts.scenario = argv[i + 1] ?? '';
      i += 1;
    } else if (arg.startsWith('--scenario=')) opts.scenario = arg.slice('--scenario='.length);
    else if (arg === '--rules') {
      opts.rules = argv[i + 1] ?? DEFAULT_RULES_FILE;
      i += 1;
    } else if (arg.startsWith('--rules=')) opts.rules = arg.slice('--rules='.length);
  }
  return opts;
}

/**
 * 将 --scenario 的取值解析为场景对象（支持文件路径或 JSON 字符串）。
 * @param {string} input 参数值
 * @returns {object} 场景对象
 */
function parseScenarioInput(input = '') {
  const value = String(input).trim();
  if (!value) throw new Error('[参数错误] --scenario 需要一个 JSON 文件路径或 JSON 字符串。');
  if (!value.startsWith('{') && !value.startsWith('[')) {
    const file = path.resolve(value);
    if (!fs.existsSync(file)) throw new Error(`[场景文件不存在] ${file}`);
    const text = fs.readFileSync(file, 'utf8');
    try {
      return JSON.parse(text);
    } catch (err) {
      throw new Error(`[场景 JSON 解析失败] ${file}\n  ${describeJsonError(text, err)}`);
    }
  }
  try {
    return JSON.parse(value);
  } catch (err) {
    throw new Error(`[场景 JSON 解析失败] 命令行 JSON 字符串\n  ${describeJsonError(value, err)}`);
  }
}

/**
 * 从标准输入读取全部内容。
 * @returns {Promise<string>} stdin 文本
 */
async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return Buffer.concat(chunks).toString('utf8').trim();
}

/**
 * CLI 主入口。
 * @returns {Promise<number>} 退出码
 */
async function main() {
  const opts = parseArgs(process.argv.slice(2));

  if (opts.help) {
    console.log(USAGE);
    return 0;
  }
  if (opts.test) {
    return runSelfTest(opts.rules);
  }

  let scenarioRaw = opts.scenario;
  if (scenarioRaw === null && !process.stdin.isTTY) {
    const stdinText = await readStdin();
    if (stdinText) scenarioRaw = stdinText;
  }
  if (scenarioRaw === null || scenarioRaw === '') {
    console.log(USAGE);
    return 0;
  }

  const rules = loadRules(opts.rules);
  const scenario = parseScenarioInput(scenarioRaw);
  const scenarios = Array.isArray(scenario) ? scenario : [scenario];
  const results = scenarios.map((item) => evaluateScenario(rules, item));
  const output = Array.isArray(scenario) ? results : results[0];

  console.log(JSON.stringify(output, null, 2));

  if (opts.strict && results.some((r) => !r.compliant)) return 2;
  return 0;
}

// 仅在被直接执行时运行 CLI（作为模块 import 时不触发）
if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename)) {
  main()
    .then((code) => {
      process.exitCode = code;
    })
    .catch((err) => {
      console.error(err && err.message ? err.message : String(err));
      process.exitCode = 1;
    });
}
