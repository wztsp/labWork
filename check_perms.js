const { Client } = require('pg')
const client = new Client({
  host: 'localhost', port: 5432, database: 'lis_db',
  user: 'lis_user', password: 'lis_dev_2026'
})

async function main() {
  await client.connect()
  console.log('==== sys_permission 完整数据 (按parent_id, sort_order) ====')
  const r = await client.query(`
    SELECT id, parent_id, perm_code, perm_name, perm_type, path, icon, sort_order, status
    FROM sys_permission ORDER BY parent_id, sort_order, id
  `)
  // 构建树形显示
  const rows = r.rows.map(r => ({...r, children: []}))
  const byId = new Map(rows.map(r => [Number(r.id), r]))
  const roots = []
  for (const row of rows) {
    const pid = Number(row.parent_id)
    if (pid === 0) roots.push(row)
    else {
      const p = byId.get(pid)
      if (p) p.children.push(row)
    }
  }
  function dump(list, indent = '') {
    for (const n of list) {
      const tag = n.perm_type === 1 ? '📋菜单' : n.perm_type === 2 ? '🔘按钮' : '🔌接口'
      const extra = n.path ? `  path=${n.path}` : ''
      const icon = n.icon ? `  icon=${n.icon}` : ''
      console.log(`${indent} [${String(n.id).padStart(2)}] ${tag} ${n.perm_name}  (code=${n.perm_code})${extra}${icon}`)
      dump(n.children, indent + '   ')
    }
  }
  dump(roots)

  console.log('\n==== sys_role_permission (ADMIN=1 分配情况) ====')
  const r2 = await client.query(`
    SELECT rp.role_id, rp.permission_id, p.perm_name, p.perm_code, p.perm_type
    FROM sys_role_permission rp
    LEFT JOIN sys_permission p ON p.id = rp.permission_id
    WHERE rp.role_id = 1
    ORDER BY p.parent_id, p.sort_order
  `)
  console.table(r2.rows)
  await client.end()
}
main().catch(e => { console.error(e); process.exit(1) })
