package com.lis.hr.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lis.hr.entity.CertificateAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CertificateAttachmentMapper extends BaseMapper<CertificateAttachment> {

    /**
     * 根据证书ID查询附件列表(不包含文件数据)
     */
    @Select("SELECT id, certificate_id AS certificateId, file_name AS fileName, " +
            "file_type AS fileType, file_size AS fileSize, content_type AS contentType, " +
            "uploader_id AS uploaderId, uploader_name AS uploaderName, " +
            "upload_time AS uploadTime, remark " +
            "FROM lis_certificate_attachment " +
            "WHERE certificate_id = #{certificateId} " +
            "ORDER BY upload_time DESC")
    List<CertificateAttachment> listByCertificateId(@Param("certificateId") Long certificateId);

    /**
     * 获取附件数量
     */
    @Select("SELECT COUNT(*) FROM lis_certificate_attachment WHERE certificate_id = #{certificateId}")
    int countByCertificateId(@Param("certificateId") Long certificateId);
}
