# -*- coding: utf-8 -*-
"""重新生成 reagent 模块 javac 全量显式编译清单。

约束（与主理人要求一致）：
  · 显式列出全部 .java，禁用 -sourcepath（避免 javac 隐式补编译掩盖缺失文件）；
  · 保留原 args 文件头部的 -d / -encoding / -classpath 三段配置不变。
"""
import glob
import io
import os

ROOT = 'lis-system/lis-service/lis-service-reagent/src/main/java'
ARGS = 'build/selfcheck/javac-reagent.args'

srcs = sorted(p.replace(os.sep, '/') for p in
              glob.glob(ROOT + '/**/*.java', recursive=True))

old_lines = io.open(ARGS, encoding='utf-8').read().split('\n')
header = old_lines[:6]
assert header[0] == '-d', header[0]
assert header[2] == '-encoding', header[2]
assert header[4] == '-classpath', header[4]

out = header + ['"%s"' % s for s in srcs]
io.open(ARGS, 'w', encoding='utf-8').write('\n'.join(out) + '\n')

old_set = set(l.strip('"') for l in old_lines if l.endswith('.java"'))
new_set = set(srcs)
print('sources listed : %d' % len(srcs))
print('newly added    : %s' % sorted(new_set - old_set))
print('removed        : %s' % sorted(old_set - new_set))
