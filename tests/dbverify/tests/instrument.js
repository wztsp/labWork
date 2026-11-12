// 仪器管理模块业务逻辑测试 (仪器台账/通讯/维护计划/维护日志/模板)
module.exports = {
  async run({ req, check }) {
    // 1. 仪器状态列表 (台账)
    const st = await req('GET', '/api/instrument/status');
    check('仪器台账列表', st.body && st.body.code === 200 && Array.isArray(st.body.data), 'count=' + ((st.body && st.body.data) || []).length);
    const insts = (st.body && st.body.data) || [];
    const first = insts[0];
    if (first) {
      const id = first.id;
      // 2. 仪器通讯日志
      const logs = await req('GET', '/api/instrument/' + id + '/logs?page=1&size=10');
      check('仪器通讯日志', logs.body && logs.body.code === 200, 'code=' + logs.body.code);
    }

    // 3. 维护计划-到期
    const due = await req('GET', '/api/instrument-mgmt/plans/due');
    check('维护计划到期列表', due.body && due.body.code === 200 && Array.isArray(due.body.data), 'count=' + ((due.body && due.body.data) || []).length);

    // 4. 维护任务列表
    const tasks = await req('GET', '/api/instrument-mgmt/plans/tasks');
    check('维护任务列表', tasks.body && tasks.body.code === 200 && Array.isArray(tasks.body.data), 'count=' + ((tasks.body && tasks.body.data) || []).length);

    // 5. 维护日志列表
    const logs = await req('GET', '/api/instrument-mgmt/logs?current=1&size=10');
    check('维护日志分页', logs.body && logs.body.code === 200, logs.body && logs.body.code === 200 ? 'total=' + (logs.body.data && logs.body.data.total) : (logs.body && logs.body.message || ''));

    // 6. 维护模板列表
    const tpl = await req('GET', '/api/instrument-mgmt/templates?current=1&size=10');
    check('维护模板分页', tpl.body && (tpl.body.code === 200), tpl.body && tpl.body.code === 200 ? 'total=' + (tpl.body.data && tpl.body.data.total) : (tpl.body && tpl.body.message || ''));
  }
};