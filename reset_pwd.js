const { Client } = require('pg')
const client = new Client({
  host: 'localhost', port: 5432, database: 'lis_db',
  user: 'lis_user', password: 'lis_dev_2026'
})
const NEW_HASH = '$2a$10$zRaE9EKJPpq0d21tu.q/.O0pKo/7B7YXBS/cEjlU57ow3a0de0.ZO'
async function main() {
  await client.connect()
  const r = await client.query('UPDATE sys_user SET password = $1 RETURNING id, username, real_name', [NEW_HASH])
  console.log(`密码 123456 已更新用户 (${r.rowCount} 人):`)
  console.table(r.rows)
  await client.end()
}
main().catch(e => { console.error(e); process.exit(1) })
