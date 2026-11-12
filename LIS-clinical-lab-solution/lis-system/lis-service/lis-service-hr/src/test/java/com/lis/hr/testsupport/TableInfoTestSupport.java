package com.lis.hr.testsupport;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * MyBatis-Plus {@code TableInfo} 单测注册工具。
 *
 * <p><b>为什么需要它</b>: {@code LambdaQueryWrapper.eq(Entity::getX, v)} 在构造条件时会
 * 通过 {@code LambdaUtils.getColumnMap(entityClass)} 反查列名, 该缓存由
 * {@code TableInfoHelper} 在 MyBatis 启动阶段填充。脱离 Spring 的纯单元测试中若不预先注册,
 * 会直接抛
 * {@code MybatisPlusException: can not find lambda cache for this entity [...]},
 * 且异常发生在被测方法内部构造 wrapper 时, <b>早于</b> spy 桩化的
 * {@code getOne()/list()/count()} 被调用, 导致用例无法进入业务分支。</p>
 *
 * <p>本工具只做元信息注册, <b>不建立任何数据库连接</b>, 也不引入 Spring 上下文,
 * 符合"单元测试不加载容器"的约束。注册后 wrapper 可正常生成 SQL 片段,
 * 测试因而能够断言真实的查询语义 (列名/条件/参数), 而不仅是桩返回值。</p>
 */
public final class TableInfoTestSupport {

    private TableInfoTestSupport() {
    }

    /**
     * 幂等注册实体的表元信息。已注册的实体会被跳过, 可在多个测试类的 {@code @BeforeAll} 中重复调用。
     *
     * @param entityClasses 需要参与 Lambda 条件构造的实体类
     */
    public static void ensureRegistered(Class<?>... entityClasses) {
        if (entityClasses == null) {
            return;
        }
        for (Class<?> entityClass : entityClasses) {
            if (entityClass == null || TableInfoHelper.getTableInfo(entityClass) != null) {
                continue;
            }
            MapperBuilderAssistant assistant =
                    new MapperBuilderAssistant(new MybatisConfiguration(), "");
            assistant.setCurrentNamespace("unittest." + entityClass.getName() + "Mapper");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }
}
