/**
 * 启动命令自动检测服务。
 *
 * 递归扫描指定根目录，识别可执行文件（jar / package.json / docker-compose / exe / bat），
 * 按服务名匹配后生成启动命令，支持批量应用到数据库。
 *
 * 匹配优先级：精确 > 前缀 > 包含。
 * 同优先级取最短文件名（最具体的匹配）。
 */

import { readdirSync, readFileSync, statSync, type Stats } from 'node:fs';
import path from 'node:path';
import type { Service } from '../types';
import type { ServiceRepository } from '../routes/services';

/** 匹配类型。 */
export type MatchType = 'exact' | 'prefix' | 'contains';

/** 置信度。exact=high, prefix=medium, contains=low。 */
export type Confidence = 'high' | 'medium' | 'low';

/** 单个服务的自动检测结果。 */
export interface DetectResult {
  service_id: number;
  service_name: string;
  detected_command: string;
  detected_work_dir: string | null;
  matched_file: string;
  match_type: MatchType;
  confidence: Confidence;
}

/** 扫描统计输出参数（可选）。 */
export interface ScanStats {
  scanned_files: number;
}

/** 可执行文件类型。 */
type FileType = 'jar' | 'package-json' | 'docker-compose' | 'exe' | 'bat';

/** 扫描到的候选文件。 */
interface CandidateFile {
  /** 文件完整绝对路径。 */
  filePath: string;
  /** 文件所在目录（作为 work_dir）。 */
  dir: string;
  /** 用于匹配服务名的名称（jar/exe/bat 为去扩展名的文件名，package.json/docker-compose 为目录名）。 */
  baseName: string;
  /** 完整文件名（含扩展名）。 */
  fileName: string;
  /** 文件类型。 */
  fileType: FileType;
}

/** 跳过的目录名集合（小写匹配），防止扫描 node_modules / 构建产物等噪声目录。 */
const SKIP_DIRS: ReadonlySet<string> = new Set<string>([
  'node_modules', '.git', '__pycache__', 'dist', 'build',
  '.next', 'target', '.idea', '.vscode', '.svn',
  'coverage', '.nyc_output', '.cache', '.gradle', '.m2',
  'logs', 'tmp', 'temp',
]);

/** 最大递归深度（根目录为 0，往下递增，超过此值不再深入）。 */
const MAX_SCAN_DEPTH: number = 3;

/** 通用前缀分段（从服务名中提取关键词时过滤掉）。 */
const GENERIC_SEGMENTS: ReadonlySet<string> = new Set<string>(['lis', 'service']);

/**
 * 计算匹配类型对应的优先级数值（越高越优先）。
 *
 * @param type 匹配类型
 * @returns 优先级数值
 */
function matchPriority(type: MatchType): number {
  switch (type) {
    case 'exact':
      return 3;
    case 'prefix':
      return 2;
    case 'contains':
      return 1;
    default:
      return 0;
  }
}

/**
 * 从服务名中提取有意义的匹配关键词。
 *
 * 按 `-` 分段，过滤掉通用前缀（lis、service）和过短（<2 字符）的分段，
 * 返回按长度降序排列的关键词列表（长的优先匹配，更具体）。
 *
 * @param serviceName 服务名（如 lis-service-instrument-mgmt）
 * @returns 关键词列表（如 ['instrument', 'mgmt']）
 */
function extractKeywords(serviceName: string): string[] {
  const segments: string[] = serviceName.split('-');
  const filtered: string[] = segments.filter(
    (seg: string): boolean =>
      seg.length >= 2 && !GENERIC_SEGMENTS.has(seg.toLowerCase()),
  );
  return filtered.sort((a: string, b: string): number => b.length - a.length);
}

/**
 * 判断候选文件是否匹配某个服务名，返回匹配类型与置信度。
 *
 * 匹配规则（优先级从高到低）：
 *   1. 精确：候选名 === 服务名（如 lis-gateway.jar → lis-gateway）
 *   2. 前缀：候选名以服务名开头（如 lis-gateway-1.0.0.jar → lis-gateway）
 *   3. 包含：候选名包含完整服务名，或包含服务名的关键词分段
 *      （如 gateway-service.jar 包含 lis-gateway 的 gateway 分段）
 *
 * @param serviceName 服务名
 * @param candidate 候选文件
 * @returns 匹配信息，不匹配返回 null
 */
function matchServiceToCandidate(
  serviceName: string,
  candidate: CandidateFile,
): { type: MatchType; confidence: Confidence } | null {
  const candidateName: string = candidate.baseName.toLowerCase();
  const serviceLower: string = serviceName.toLowerCase();

  // 精确匹配
  if (candidateName === serviceLower) {
    return { type: 'exact', confidence: 'high' };
  }

  // 前缀匹配：候选名以服务名开头
  if (candidateName.startsWith(serviceLower)) {
    return { type: 'prefix', confidence: 'medium' };
  }

  // 包含匹配：候选名包含完整服务名
  if (candidateName.includes(serviceLower)) {
    return { type: 'contains', confidence: 'low' };
  }

  // 包含匹配：候选名包含服务名的关键词分段
  const keywords: string[] = extractKeywords(serviceName);
  for (const keyword of keywords) {
    if (candidateName.includes(keyword.toLowerCase())) {
      return { type: 'contains', confidence: 'low' };
    }
  }

  return null;
}

/**
 * 根据候选文件类型生成启动命令与工作目录。
 *
 * @param candidate 候选文件
 * @returns 命令与工作目录
 */
function generateCommand(candidate: CandidateFile): { command: string; workDir: string } {
  switch (candidate.fileType) {
    case 'jar':
      return {
        command: `java -jar "${candidate.filePath}"`,
        workDir: candidate.dir,
      };
    case 'package-json':
      return {
        command: 'npm start',
        workDir: candidate.dir,
      };
    case 'docker-compose':
      return {
        command: 'docker-compose up -d',
        workDir: candidate.dir,
      };
    case 'exe':
      return {
        command: `"${candidate.filePath}"`,
        workDir: candidate.dir,
      };
    case 'bat':
      return {
        command: `"${candidate.filePath}"`,
        workDir: candidate.dir,
      };
    default: {
      // 穷尽性守卫：如果 FileType 新增成员但未覆盖，编译期报错
      const exhaustive: never = candidate.fileType;
      throw new Error(`未知的文件类型: ${exhaustive}`);
    }
  }
}

/**
 * 递归扫描目录，收集候选可执行文件。
 *
 * 遇到无权限读取的目录或文件时静默跳过，不中断整体扫描。
 *
 * @param dir 当前目录
 * @param depth 当前深度（根目录为 0）
 * @param candidates 候选文件收集数组（按引用填充）
 * @returns 扫描到的文件总数（含非候选文件）
 */
function scanDirectory(
  dir: string,
  depth: number,
  candidates: CandidateFile[],
): number {
  let fileCount: number = 0;

  if (depth > MAX_SCAN_DEPTH) {
    return 0;
  }

  let entries: string[];
  try {
    entries = readdirSync(dir);
  } catch {
    return 0;
  }

  for (const entry of entries) {
    const fullPath: string = path.join(dir, entry);

    let stat: Stats;
    try {
      stat = statSync(fullPath);
    } catch {
      continue;
    }

    if (stat.isDirectory()) {
      // 跳过 node_modules、.git 等噪声目录
      if (SKIP_DIRS.has(entry.toLowerCase())) {
        continue;
      }
      fileCount += scanDirectory(fullPath, depth + 1, candidates);
    } else if (stat.isFile()) {
      fileCount += 1;
      const lower: string = entry.toLowerCase();

      if (lower.endsWith('.jar')) {
        candidates.push({
          filePath: fullPath,
          dir,
          baseName: entry.slice(0, -4),
          fileName: entry,
          fileType: 'jar',
        });
      } else if (lower === 'package.json') {
        // 读取 package.json 检查是否含 start 脚本
        try {
          const content: string = readFileSync(fullPath, 'utf-8');
          const pkg = JSON.parse(content) as { scripts?: Record<string, unknown> };
          if (pkg.scripts && typeof pkg.scripts.start === 'string') {
            candidates.push({
              filePath: fullPath,
              dir,
              baseName: path.basename(dir),
              fileName: entry,
              fileType: 'package-json',
            });
          }
        } catch {
          // JSON 解析失败或读取失败，跳过
        }
      } else if (lower === 'docker-compose.yml' || lower === 'docker-compose.yaml') {
        candidates.push({
          filePath: fullPath,
          dir,
          baseName: path.basename(dir),
          fileName: entry,
          fileType: 'docker-compose',
        });
      } else if (lower.endsWith('.exe')) {
        candidates.push({
          filePath: fullPath,
          dir,
          baseName: entry.slice(0, -4),
          fileName: entry,
          fileType: 'exe',
        });
      } else if (lower.endsWith('.bat')) {
        candidates.push({
          filePath: fullPath,
          dir,
          baseName: entry.slice(0, -4),
          fileName: entry,
          fileType: 'bat',
        });
      }
    }
  }

  return fileCount;
}

/**
 * 扫描根目录并按服务名自动检测启动命令。
 *
 * 递归扫描 baseDir（最大深度 3 层），识别可执行文件，
 * 为每个服务找出最佳匹配（最高优先级 + 最短文件名），生成对应的启动命令。
 *
 * @param baseDir 根目录绝对路径
 * @param services 需要匹配的服务列表
 * @param stats 可选输出参数，填充 scanned_files 字段记录扫描的文件总数
 * @returns 检测结果数组（仅包含匹配到的服务）
 */
export function detectStartCommands(
  baseDir: string,
  services: readonly Service[],
  stats?: ScanStats,
): DetectResult[] {
  const candidates: CandidateFile[] = [];
  const scannedFiles: number = scanDirectory(baseDir, 0, candidates);

  if (stats !== undefined) {
    stats.scanned_files = scannedFiles;
  }

  const results: DetectResult[] = [];

  for (const service of services) {
    let bestMatch: { candidate: CandidateFile; type: MatchType; confidence: Confidence } | null = null;

    for (const candidate of candidates) {
      const match = matchServiceToCandidate(service.name, candidate);
      if (match === null) {
        continue;
      }

      if (bestMatch === null) {
        bestMatch = { candidate, type: match.type, confidence: match.confidence };
        continue;
      }

      const newPriority: number = matchPriority(match.type);
      const oldPriority: number = matchPriority(bestMatch.type);

      if (newPriority > oldPriority) {
        bestMatch = { candidate, type: match.type, confidence: match.confidence };
      } else if (newPriority === oldPriority && candidate.fileName.length < bestMatch.candidate.fileName.length) {
        // 同优先级取最短文件名（更具体的匹配）
        bestMatch = { candidate, type: match.type, confidence: match.confidence };
      }
    }

    if (bestMatch !== null) {
      const { candidate, type, confidence } = bestMatch;
      const { command, workDir } = generateCommand(candidate);
      results.push({
        service_id: service.id,
        service_name: service.name,
        detected_command: command,
        detected_work_dir: workDir,
        matched_file: candidate.filePath,
        match_type: type,
        confidence,
      });
    }
  }

  return results;
}

/**
 * 批量应用检测到的启动命令配置到数据库。
 *
 * 遍历 configs，调用 repository.update() 更新每个服务的 start_command 和 work_dir 字段。
 * 如果某个服务不存在，repository.update() 会抛出 HttpError(404)。
 *
 * @param configs 要应用的配置列表
 * @param repository 服务仓储实例
 * @returns 更新后的 Service 数组（顺序与 configs 一致）
 */
export function applyDetectedConfigs(
  configs: Array<{ id: number; start_command: string; work_dir: string | null }>,
  repository: ServiceRepository,
): Service[] {
  const updated: Service[] = [];
  for (const config of configs) {
    const result: Service = repository.update(config.id, {
      start_command: config.start_command,
      work_dir: config.work_dir,
    });
    updated.push(result);
  }
  return updated;
}
