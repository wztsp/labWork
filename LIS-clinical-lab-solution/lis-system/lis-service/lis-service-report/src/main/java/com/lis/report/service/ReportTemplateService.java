package com.lis.report.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lis.report.entity.ReportTemplate;
import java.util.List;

public interface ReportTemplateService {
    Page<ReportTemplate> queryPage(int current, int size, String name, Long instrumentId, Integer reportType);
    List<ReportTemplate> listAll();
    ReportTemplate getById(Long id);
    ReportTemplate getByInstrumentId(Long instrumentId);
    ReportTemplate getByInstrumentAndType(Long instrumentId, Integer reportType);
    ReportTemplate getDefault();
    ReportTemplate getDefaultByType(Integer reportType);
    ReportTemplate create(ReportTemplate template);
    ReportTemplate update(ReportTemplate template);
    void delete(Long id);
    void setDefault(Long id);
}
