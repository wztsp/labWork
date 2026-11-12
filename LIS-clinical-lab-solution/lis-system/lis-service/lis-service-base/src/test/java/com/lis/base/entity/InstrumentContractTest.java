package com.lis.base.entity;

import com.lis.instrument.entity.Instrument;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-5 字段契约测试 (方案 A)。
 * <p>
 * base 模块的 {@code Instrument} 是 {@code lis_instrument} 表的<b>写权威</b> (承载 CRUD / 校准 /
 * 维修 / 人员映射 等写逻辑), instrument 模块的 {@code Instrument} 仅为<b>只读运行时投影</b>
 * (连接管理 / 状态展示, 不含写逻辑)。
 * 两实体字段必须保持一致, 否则会出现<b>字段漂移</b> (投影缺字段 / 写权威多字段)。
 * 跨模块契约由 lis-api 的 {@code InstrumentDTO} 承载, 本测试用反射兜底字段漂移。
 */
class InstrumentContractTest {

    /** 提取某类的实例字段 (name -> 类型全名), 排除 static / 合成字段。 */
    private static Map<String, String> fieldMap(Class<?> clazz) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (f.isSynthetic()) {
                continue;
            }
            map.put(f.getName(), f.getType().getName());
        }
        return map;
    }

    @Test
    void baseInstrumentIsWriteAuthority() {
        // 写权威必须持有主键 id 与全部台账业务字段
        Map<String, String> base = fieldMap(com.lis.base.entity.Instrument.class);
        for (String key : new String[]{"id", "instrumentCode", "instrumentName",
                "status", "workStatus", "lastOnlineTime", "createTime"}) {
            assertTrue(base.containsKey(key), "base.Instrument 缺失写权威字段: " + key);
        }
    }

    @Test
    void instrumentProjectionFieldsAreSubsetOfBase() {
        // 投影实体的每个字段必须在写权威实体中存在且类型一致 (字段漂移被契约捕获)
        Map<String, String> base = fieldMap(com.lis.base.entity.Instrument.class);
        Map<String, String> projection = fieldMap(Instrument.class);
        for (Map.Entry<String, String> e : projection.entrySet()) {
            String name = e.getKey();
            assertTrue(base.containsKey(name),
                    "字段漂移: instrument.Instrument 字段 [" + name + "] 在 base.Instrument 中不存在");
            assertEquals(base.get(name), e.getValue(),
                    "字段漂移: instrument.Instrument 字段 [" + name + "] 类型与 base.Instrument 不一致");
        }
    }

    @Test
    void fieldSetsAreConsistent() {
        // B-5 契约: 两实体字段集合应保持一致, 任一侧新增/删除字段都应被本测试捕获
        Map<String, String> base = fieldMap(com.lis.base.entity.Instrument.class);
        Map<String, String> projection = fieldMap(Instrument.class);
        assertEquals(base.keySet(), projection.keySet(),
                "base.Instrument 与 instrument.Instrument 字段集合应保持一致 (B-5 字段漂移契约)");
        for (String name : base.keySet()) {
            assertEquals(base.get(name), projection.get(name), "字段类型不一致: " + name);
        }
    }
}
