// 系统管理模块业务逻辑测试 (用户/角色/权限/部门/配置/日志)
module.exports = {
  async run({ req, check }) {
    // 1. 当前登录用户信息 (前端实际使用 /api/auth/info)
    const authInfo = await req('GET', '/api/auth/info');
    check('登录用户信息(auth/info)', authInfo.body && authInfo.body.code === 200 && authInfo.body.data, authInfo.body ? JSON.stringify(authInfo.body.data).slice(0, 120) : '');
    // me 端点需 X-User-Id 头 (已由 test_api 注入)
    const me = await req('GET', '/api/system/users/me');
    check('当前用户信息(users/me)', me.body && me.body.code === 200 && me.body.data, me.body ? JSON.stringify(me.body.data).slice(0, 120) : '');

    // 2. 用户分页
    const users = await req('GET', '/api/system/users?current=1&size=10');
    check('用户分页', users.body && users.body.code === 200, users.body && users.body.code === 200 ? 'total=' + (users.body.data && users.body.data.total) : (users.body && users.body.message || ''));

    // 3. 角色列表
    const roles = await req('GET', '/api/system/roles?current=1&size=10');
    check('角色分页', roles.body && roles.body.code === 200, roles.body && roles.body.code === 200 ? 'total=' + (roles.body.data && roles.body.data.total) : (roles.body && roles.body.message || ''));

    // 4. 权限树
    const perms = await req('GET', '/api/system/permissions/tree');
    check('权限树', perms.body && perms.body.code === 200 && Array.isArray(perms.body.data), 'count=' + ((perms.body && perms.body.data) || []).length);

    // 5. 部门列表
    const depts = await req('GET', '/api/system/departments?current=1&size=10');
    check('部门分页', depts.body && depts.body.code === 200, depts.body && depts.body.code === 200 ? 'total=' + (depts.body.data && depts.body.data.total) : (depts.body && depts.body.message || ''));

    // 6. 系统配置 (按key查询)
    const cfg = await req('GET', '/api/system/configs?current=1&size=10');
    check('系统配置列表', cfg.body && cfg.body.code === 200 || (cfg.body && Array.isArray(cfg.body.data)), 'code=' + cfg.body.code);

    // 7. 操作日志
    const logs = await req('GET', '/api/system/logs?current=1&size=10');
    check('操作日志', logs.body && logs.body.code === 200, logs.body && logs.body.code === 200 ? 'total=' + (logs.body.data && logs.body.data.total) : (logs.body && logs.body.message || ''));

    // 8. 数据备份列表
    const bk = await req('GET', '/api/system/backup/list');
    check('备份列表', bk.body && bk.body.code === 200, 'code=' + bk.body.code);
  }
};