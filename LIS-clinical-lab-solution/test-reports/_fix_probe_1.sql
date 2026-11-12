SET client_encoding TO 'UTF8';
-- 双编码修复辅助函数: 把 ['UTF8字节被当GBK读'] 的乱码还原成原始汉字
-- 原理: 正确中文->UTF8字节B; B被(错误地)按GBK解码成乱码串S存库。
--       反向: 乱码串S->convert_to(S,'GBK') 得到B; 再 convert_from(B,'UTF8') 得到原始中文。
CREATE OR REPLACE FUNCTION fix_double_encoding(val text) RETURNS text LANGUAGE plpgsql AS $$
DECLARE
  cleaned text;
  bytes  bytea;
  out    text;
BEGIN
  IF val IS NULL THEN RETURN NULL; END IF;
  -- 去掉可能存在的尾部替换符(U+FFFD)
  cleaned := regexp_replace(val, '\uFFFD+$', '', 'g');
  IF cleaned = '' THEN RETURN NULL; END IF;
  BEGIN
    bytes := convert_to(cleaned, 'GBK');
    out := convert_from(bytes, 'UTF8');
  EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
  END;
  -- 结果需不含替换符(U+FFFD)且是合法中文, 否则视为非乱码
  IF position(chr(65533) in out) > 0 THEN RETURN NULL; END IF;
  RETURN out;
END $$;

-- 看 lis_report 全部姓名及其复原结果, 判断哪些是乱码
SELECT patient_name AS raw, fix_double_encoding(patient_name) AS fixed
FROM (SELECT DISTINCT patient_name FROM lis_report) t
WHERE patient_name ~* '[一-龥]'
ORDER BY raw;