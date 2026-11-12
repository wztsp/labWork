// 人事管理模块业务逻辑测试 (员工/小组/排班/证书/职称/培训/授权/文档)
module.exports = {
  async run({ req, check }) {
    // 1. 员工分页
    const staff = await req('GET', '/api/hr/staff?current=1&size=10');
    check('员工分页', staff.body && staff.body.code === 200, staff.body && staff.body.code === 200 ? 'total=' + (staff.body.data && staff.body.data.total) : (staff.body && staff.body.message || ''));
    const staffTotal = staff.body && staff.body.data ? staff.body.data.total : 0;

    // 2. 在职员工列表 (下拉用, 关键业务: 采集/送检/签收人需关联员工)
    const active = await req('GET', '/api/hr/staff?current=1&size=500&status=1');
    check('在职员工列表', active.body && active.body.code === 200 && active.body.data && active.body.data.records && active.body.data.records.length > 0, 'count=' + ((active.body.data && active.body.data.records) || []).length);

    // 3. 小组列表与树
    const groups = await req('GET', '/api/hr/groups');
    check('小组列表', groups.body && groups.body.code === 200 && Array.isArray(groups.body.data), 'count=' + ((groups.body && groups.body.data) || []).length);
    const gt = await req('GET', '/api/hr/groups/tree');
    check('小组树', gt.body && gt.body.code === 200 && Array.isArray(gt.body.data));

    // 4. 排班分页
    const sched = await req('GET', '/api/hr/schedules?current=1&size=10');
    check('排班分页', sched.body && sched.body.code === 200, sched.body && sched.body.code === 200 ? 'total=' + (sched.body.data && sched.body.data.total) : (sched.body && sched.body.message || ''));

    // 5. 资质证书分页
    const cert = await req('GET', '/api/hr/certificates?current=1&size=10');
    check('资质证书分页', cert.body && cert.body.code === 200, cert.body && cert.body.code === 200 ? 'total=' + (cert.body.data && cert.body.data.total) : (cert.body && cert.body.message || ''));

    // 6. 职称字典
    const td = await req('GET', '/api/hr/title-dict');
    check('职称字典', td.body && td.body.code === 200 && Array.isArray(td.body.data), 'count=' + ((td.body && td.body.data) || []).length);

    // 7. 人员职称记录
    const titles = await req('GET', '/api/hr/titles');
    check('人员职称记录', titles.body && titles.body.code === 200, 'code=' + titles.body.code);

    // 8. 培训记录
    const tr = await req('GET', '/api/hr/training');
    check('培训记录', tr.body && tr.body.code === 200, 'code=' + tr.body.code);

    // 9. 岗位授权
    const auth = await req('GET', '/api/hr/authorizations');
    check('岗位授权列表', auth.body && auth.body.code === 200, 'code=' + auth.body.code);

    // 10. 文档资料分页
    const doc = await req('GET', '/api/hr/documents?current=1&size=10');
    check('文档资料分页', doc.body && doc.body.code === 200, doc.body && doc.body.code === 200 ? 'total=' + (doc.body.data && doc.body.data.total) : (doc.body && doc.body.message || ''));

    // 11. 证书到期列表
    const expiring = await req('GET', '/api/hr/certificates/expiring');
    check('证书到期列表', expiring.body && expiring.body.code === 200, 'code=' + (expiring.body && expiring.body.code));

    // 12. 岗位授权校验
    const authCheck = await req('GET', '/api/hr/authorizations/check');
    check('岗位授权校验接口已接通', authCheck.body && authCheck.body.code !== 200,
      'code=' + (authCheck.body && authCheck.body.code) + ' msg=' + (authCheck.body && authCheck.body.message || ''));

    // 13. 离职账号回收(契约校验: B-4; 不存在的员工ID返回业务错误而非崩溃)
    const resign = await req('POST', '/api/hr/staff/999999999999/resign', { reason: '测试离职' });
    check('离职账号回收接口已接通', resign.body && resign.body.code !== 200,
      'code=' + (resign.body && resign.body.code) + ' msg=' + (resign.body && resign.body.message || ''));
  }
};