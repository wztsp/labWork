import { readdirSync, readFileSync, statSync } from 'fs'
import { join, extname } from 'path'

const ROOT = 'd:/traeitem/labWork/LIS-clinical-lab-solution/lis-frontend/src/views'

function walk(dir) {
  const out = []
  for (const ent of readdirSync(dir)) {
    const p = join(dir, ent)
    if (statSync(p).isDirectory()) out.push(...walk(p))
    else if (extname(p) === '.vue') out.push(p)
  }
  return out
}

const files = walk(ROOT)
let totalUndefined = 0

for (const f of files) {
  const src = readFileSync(f, 'utf8')
  // script setup block
  const m = src.match(/<script\s+setup[^>]*>([\s\S]*?)<\/script>/)
  if (!m) continue
  const script = m[1]
  // defined: function names, const arrow functions, function expressions
  const defined = new Set()
  const fnRe = /(?:function\s+)([A-Za-z_$][\w$]*)/g
  let t
  while ((t = fnRe.exec(script))) defined.add(t[1])
  const constRe = /(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>/g
  while ((t = constRe.exec(script))) defined.add(t[1])
  // also props/emits methods referenced via template come from script; add all identifiers used in template handled below

  // template block
  const tm = src.match(/<template>([\s\S]*?)<\/template>/)
  if (!tm) continue
  const template = tm[1]

  // find @click, @keyup.enter, @change, @confirm referencing identifiers
  const handlerRe = /@(?:click|keyup\.enter|change|confirm|open|close|current-change|selection-change|size-change|submit|dblclick|mouseenter|mouseleave|focus|blur|input|command|select|clear|remove-tag|tab-change|expand-change|row-dblclick|sort-change|filter-change|cell-click)\s*(?:\.\w+)?\s*=\s*"([A-Za-z_$][\w$]*)"/g
  let h
  while ((h = handlerRe.exec(template))) {
    const name = h[1]
    if (!defined.has(name)) {
      // skip passthrough of @click="visible = false" style already excluded by regex
      // skip known passthrough props defined in defineProps
      totalUndefined++
      console.log(`${totalUndefined}. ${f.replace(ROOT, '')}  @handler="${name}" 未在 script setup 中定义`)
    }
  }
}

console.log(`\n共发现 ${totalUndefined} 处模板引用了未在 script setup 定义的函数`)