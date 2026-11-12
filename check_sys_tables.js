// 查询 sys_user 和相关系统表
const { Client } = require('pg')
const client = new Client({
  host: 'localhost',
  port: 5432,
  database: 'lis_db',
  user: 'lis_user',
  password: 'lis_dev_2026'
})

async function main() {
  await client.connect()
  const tables = [
    'sys_user', 'sys_role', 'sys_permission', 'sys_department', 'sys_audit_log', 'sys_config'
  ]
  for (const t of tables) {
    try {
      const r = await client.query(`SELECT COUNT(*) FROM ${t}`)
      console.log(`${t}: ${r.rows[0].count} rows`)
    } catch (e) {
      console.log(`${t}: 表不存在或查询失败 (${e.message.split('\n')[0]})`)
    }
  }
  console.log('\n--- sys_user 列表 ---')
  try {
    const r = await client.query('SELECT id, username, real_name, status, password, last_login_time FROM sys_user ORDER BY id')
    console.table(r.rows)
  } catch (e) { console.log('ERROR:', e.message) }

  console.log('\n--- sys_role 列表 ---')
  try {
    const r = await client.query('SELECT * FROM sys_role ORDER BY id')
    console.table(r.rows)
  } catch (e) { console.log('ERROR:', e.message) }
  await client.end()
}
main().catch(e => { console.error(e); process.exit(1) })
