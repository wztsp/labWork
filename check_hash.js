// 使用 Java 直接生成并测试密码哈希
const path = require('path')
const { Client } = require('pg')

const SPRING_SECURITY_VERSION = '6.2.1' // 看看 pom 里是什么版本

// 获取当前数据库中的密码hash
async function main() {
  const client = new Client({
    host: 'localhost', port: 5432, database: 'lis_db',
    user: 'lis_user', password: 'lis_dev_2026'
  })
  await client.connect()
  const r = await client.query("SELECT id, username, password FROM sys_user WHERE username='admin'")
  console.log('数据库存储的哈希：')
  console.log(r.rows[0].password)
  await client.end()
}
main().catch(console.error)
