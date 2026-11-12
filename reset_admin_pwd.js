// 生成BCrypt密码并更新admin账号
const { Client } = require('pg')
const bcrypt = require('bcryptjs')
const client = new Client({
  host: 'localhost',
  port: 5432,
  database: 'lis_db',
  user: 'lis_user',
  password: 'lis_dev_2026'
})

async function main() {
  await client.connect()
  const pwd = '123456'
  const hash = bcrypt.hashSync(pwd, 10)
  console.log(`密码 '${pwd}' 的 BCrypt: ${hash}`)
  const r = await client.query('UPDATE sys_user SET password = $1 WHERE username = $2 RETURNING id, username, real_name', [hash, 'admin'])
  console.log('已更新:', r.rows)
  // 同时 tech / director 也改成同密码
  await client.query('UPDATE sys_user SET password = $1 WHERE username IN ($2, $3)', [hash, 'tech', 'director'])
  console.log('tech/director 也已重置为 123456')
  await client.end()
}
main().catch(e => { console.error(e); process.exit(1) })
