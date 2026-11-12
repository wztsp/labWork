package com.lis.hr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.hr.entity.StaffCertificate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface StaffCertificateMapper extends BaseMapper<StaffCertificate> {

    /**
     * 查询即将到期 (当前日期 &lt;= expiry_date &lt;= threshold) 且有效的证书, 关联人员姓名。
     * 排除已过期证书 (expiry_date &gt;= 当前日期), 仅预警未来 ADVANCE_DAYS 天内到期的。
     */
    @Select("SELECT c.id, c.staff_id AS staffId, s.name AS staffName, s.staff_no AS staffNo, " +
            "c.cert_type AS certType, c.cert_name AS certName, c.cert_no AS certNo, " +
            "c.expiry_date AS expiryDate, " +
            "(c.expiry_date - CAST(CURRENT_DATE AS DATE)) AS daysRemaining " +
            "FROM lis_staff_certificate c " +
            "LEFT JOIN lis_staff s ON c.staff_id = s.id " +
            "WHERE c.status = 1 AND c.expiry_date IS NOT NULL " +
            "AND c.expiry_date >= CURRENT_DATE AND c.expiry_date <= #{threshold} " +
            "ORDER BY c.expiry_date ASC")
    List<Map<String, Object>> listExpiringSoon(@Param("threshold") LocalDate threshold);
}
