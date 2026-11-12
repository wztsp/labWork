const { Client } = require('pg');
const c = new Client({ host: 'localhost', port: 5432, database: 'lis_db', user: 'lis_user', password: 'lis_dev_2026' });
(async () => {
  await c.connect();
  try {
    await c.query('BEGIN');

    // ========== 1. 血常规(id=9): 补充 EOS%、BASO% ==========
    const cbc = [
      ['EOS_P', '嗜酸性粒细胞百分比', 'Eosinophil Percentage', 'EOS%', '血常规', '全血', '%', '仪器法', 9, 3],
      ['BASO_P', '嗜碱性粒细胞百分比', 'Basophil Percentage', 'BASO%', '血常规', '全血', '%', '仪器法', 9, 3]
    ];
    for (const [code, name, nameEn, short, cat, sp, unit, method, parent, tube] of cbc) {
      await c.query(
        `INSERT INTO lis_test_item (item_code,item_name,item_name_en,short_name,category,specimen_type,unit,method,is_panel,parent_id,price,status,tube_id)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,0,$9,0.00,1,$10)`,
        [code, name, nameEn, short, cat, sp, unit, method, parent, tube]
      );
    }

    // ========== 2. 尿液分析(id=21) 更名为 尿常规 ==========
    await c.query(`UPDATE lis_test_item SET item_name='尿常规', item_name_en='Urine Routine' WHERE id=21`);

    // ========== 3. 尿常规(id=21): 补充镜检/沉渣指标 ==========
    const ua = [
      ['U_COLOR',   '尿颜色',   'Urine Color',   '颜色', '尿液', '尿液', '', '目测法'],
      ['U_CLARITY', '尿透明度', 'Transparency',  '透明度', '尿液', '尿液', '', '目测法'],
      ['U_M_RBC',   '尿镜检红细胞', 'Microscopic RBC', '镜检RBC', '尿液', '尿液', '/HP', '镜检法'],
      ['U_M_WBC',   '尿镜检白细胞', 'Microscopic WBC', '镜检WBC', '尿液', '尿液', '/HP', '镜检法'],
      ['U_M_EPI',   '尿镜检上皮细胞', 'Epithelial Cells', '上皮细胞', '尿液', '尿液', '/HP', '镜检法'],
      ['U_M_CAST',  '尿镜检管型', 'Casts', '管型', '尿液', '尿液', '/HP', '镜检法'],
      ['U_M_CRY',   '尿镜检结晶', 'Crystals', '结晶', '尿液', '尿液', '/HP', '镜检法'],
      ['U_M_BACT',  '尿镜检细菌', 'Bacteria', '细菌', '尿液', '尿液', '/HP', '镜检法'],
      ['U_M_FUN',   '尿镜检真菌', 'Fungus', '真菌', '尿液', '尿液', '/HP', '镜检法'],
      ['U_M_MUC',   '尿粘液丝', 'Mucus', '粘液丝', '尿液', '尿液', '/HP', '镜检法']
    ];
    for (const [code, name, nameEn, short, cat, sp, unit, method] of ua) {
      await c.query(
        `INSERT INTO lis_test_item (item_code,item_name,item_name_en,short_name,category,specimen_type,unit,method,is_panel,parent_id,price,status,tube_id)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,0,21,0.00,1,8)`,
        [code, name, nameEn, short, cat, sp, unit, method]
      );
    }

    // ========== 4. 新建 大便常规 项目组 ==========
    const stoolPanel = await c.query(
      `INSERT INTO lis_test_item (item_code,item_name,item_name_en,short_name,category,specimen_type,is_panel,price,status,tube_id)
       VALUES ('STOOL_ROUTINE','大便常规','Stool Routine','大便常规','其他','粪便',1,15.00,1,9) RETURNING id`
    );
    const stoolPanelId = stoolPanel.rows[0].id;

    // ========== 5. 大便常规 检验指标 ==========
    const stool = [
      ['S_APP',   '大便外观',   'Appearance',   '外观', '其他', '粪便', '', '目测法'],
      ['S_COLOR', '大便颜色',   'Color',        '颜色', '其他', '粪便', '', '目测法'],
      ['S_OB',    '大便隐血',   'Occult Blood', '隐血', '其他', '粪便', '', '免疫法'],
      ['S_WBC',   '大便白细胞', 'WBC',          'WBC',  '其他', '粪便', '/HP', '镜检法'],
      ['S_RBC',   '大便红细胞', 'RBC',          'RBC',  '其他', '粪便', '/HP', '镜检法'],
      ['S_PUS',   '大便脓细胞', 'Pus Cells',    '脓细胞', '其他', '粪便', '/HP', '镜检法'],
      ['S_MAC',   '大便吞噬细胞', 'Macrophages', '吞噬细胞', '其他', '粪便', '/HP', '镜检法'],
      ['S_FUN',   '大便真菌',   'Fungus',       '真菌', '其他', '粪便', '/HP', '镜检法'],
      ['S_OVA',   '大便虫卵',   'Parasite Ova', '虫卵', '其他', '粪便', '/HP', '镜检法'],
      ['S_FAT',   '大便脂肪滴', 'Fat Droplets', '脂肪滴', '其他', '粪便', '/HP', '镜检法'],
      ['S_RES',   '大便食物残渣', 'Food Residue', '食物残渣', '其他', '粪便', '/HP', '镜检法'],
      ['S_MUC',   '大便粘液',   'Mucus',        '粘液', '其他', '粪便', '/HP', '镜检法']
    ];
    for (const [code, name, nameEn, short, cat, sp, unit, method] of stool) {
      await c.query(
        `INSERT INTO lis_test_item (item_code,item_name,item_name_en,short_name,category,specimen_type,unit,method,is_panel,parent_id,price,status,tube_id)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,0,$9,0.00,1,9)`,
        [code, name, nameEn, short, cat, sp, unit, method, stoolPanelId]
      );
    }

    await c.query('COMMIT');
    console.log('OK. 大便常规 panel id = ' + stoolPanelId);
    console.log('已添加: 血常规 EOS%/BASO%；尿常规更名+10个镜检指标；大便常规项目组+12个指标');
  } catch (e) {
    await c.query('ROLLBACK');
    console.error('FAILED:', e.message);
  } finally {
    await c.end();
  }
})();