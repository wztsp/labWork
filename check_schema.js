const { Client } = require('pg')
const client = new Client({
  host: 'localhost', port: 5432, database: 'lis_db',
  user: 'lis_user', password: 'lis_dev_2026'
})
async function main() {
  await client.connect()
  // 查询 sys_permission 和 sys_role_permission 的所有字段
  const r1 = await client.query(`
    SELECT column_name, data_type, is_nullable, column_default
    FROM information_schema.columns
    WHERE table_name IN ('sys_permission','sys_role_permission')
    ORDER BY table_name, ordinal_position
  `)
  console.table(r1.rows)
  // 也看一下 原有的数据行, 确认它们有哪些字段
  const r2 = await client.query(`SELECT * FROM sys_permission LIMIT 1`)
  console.log('\nsys_permission实际字段:', Object.keys(r2.rows[0]).join(', '))
  console.log(r2.rows[0])
  const r3 = await client.query(`SELECT * FROM sys_role_permission LIMIT 1`)
  console.log('\nsys_role_permission实际字段:', Object.keys(r3.rows[0]).join(', '))
  await client.end()
}
main().catch(e => { console.error(e); process.exit(1) })
