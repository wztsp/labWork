package com.lis.micro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lis.common.core.exception.BusinessException;
import com.lis.micro.dto.AntibioticTemplateApplyVO;
import com.lis.micro.dto.AntibioticTemplateSaveRequest;
import com.lis.micro.dto.AntibioticTemplateVO;
import com.lis.micro.entity.Antibiotic;
import com.lis.micro.entity.AntibioticBreakpoint;
import com.lis.micro.entity.AntibioticTemplate;
import com.lis.micro.entity.AntibioticTemplateBacteria;
import com.lis.micro.entity.AntibioticTemplateItem;
import com.lis.micro.entity.Bacteria;
import com.lis.micro.mapper.AntibioticTemplateBacteriaMapper;
import com.lis.micro.mapper.AntibioticTemplateItemMapper;
import com.lis.micro.mapper.AntibioticTemplateMapper;
import com.lis.micro.service.AntibioticBreakpointService;
import com.lis.micro.service.AntibioticService;
import com.lis.micro.service.AntibioticTemplateService;
import com.lis.micro.service.BacteriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 抗生素药敏模板 Service 实现
 */
@Service
public class AntibioticTemplateServiceImpl
        extends ServiceImpl<AntibioticTemplateMapper, AntibioticTemplate>
        implements AntibioticTemplateService {

    private final AntibioticTemplateItemMapper itemMapper;
    private final AntibioticTemplateBacteriaMapper bacteriaMapper;
    private final AntibioticService antibioticService;
    private final BacteriaService bacteriaService;
    private final AntibioticBreakpointService breakpointService;

    public AntibioticTemplateServiceImpl(AntibioticTemplateItemMapper itemMapper,
                                         AntibioticTemplateBacteriaMapper bacteriaMapper,
                                         AntibioticService antibioticService,
                                         BacteriaService bacteriaService,
                                         AntibioticBreakpointService breakpointService) {
        this.itemMapper = itemMapper;
        this.bacteriaMapper = bacteriaMapper;
        this.antibioticService = antibioticService;
        this.bacteriaService = bacteriaService;
        this.breakpointService = breakpointService;
    }

    @Override
    public Page<AntibioticTemplateVO> queryPage(long current, long size, String keyword, String category, Integer status) {
        LambdaQueryWrapper<AntibioticTemplate> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(AntibioticTemplate::getCode, keyword)
                    .or().like(AntibioticTemplate::getName, keyword));
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq(AntibioticTemplate::getTemplateCategory, category);
        }
        if (status != null) {
            wrapper.eq(AntibioticTemplate::getStatus, status);
        }
        wrapper.orderByDesc(AntibioticTemplate::getIsSystem)
                .orderByAsc(AntibioticTemplate::getSortNo)
                .orderByDesc(AntibioticTemplate::getCreateTime);

        Page<AntibioticTemplate> page = this.page(new Page<>(current, size), wrapper);

        // 批量补全每个模板的细菌绑定名称
        List<AntibioticTemplateVO> voList = new ArrayList<>();
        if (page.getRecords() != null && !page.getRecords().isEmpty()) {
            List<Long> templateIds = page.getRecords().stream()
                    .map(AntibioticTemplate::getId).collect(Collectors.toList());
            List<AntibioticTemplateBacteria> bindings = bacteriaMapper.selectList(
                    new LambdaQueryWrapper<AntibioticTemplateBacteria>().in(AntibioticTemplateBacteria::getTemplateId, templateIds));
            Map<Long, List<Long>> bacIdByTpl = bindings.stream().collect(Collectors.groupingBy(
                    AntibioticTemplateBacteria::getTemplateId,
                    Collectors.mapping(AntibioticTemplateBacteria::getBacteriaId, Collectors.toList())));
            Set<Long> allBacteriaIds = bindings.stream().map(AntibioticTemplateBacteria::getBacteriaId).collect(Collectors.toSet());
            Map<Long, String> nameById = allBacteriaIds.isEmpty() ? Collections.emptyMap()
                    : bacteriaService.listByIds(allBacteriaIds).stream()
                        .collect(Collectors.toMap(Bacteria::getId, Bacteria::getBacteriaName, (a, b) -> a));

            Map<Long, Integer> itemCountByTpl = loadItemCount(templateIds);

            for (AntibioticTemplate t : page.getRecords()) {
                AntibioticTemplateVO vo = toHeadVO(t);
                List<Long> ids = bacIdByTpl.getOrDefault(t.getId(), Collections.emptyList());
                vo.setBacteriaIds(ids);
                vo.setBacteriaNames(ids.stream().map(id -> nameById.getOrDefault(id, String.valueOf(id))).collect(Collectors.toList()));
                vo.setItemCount(itemCountByTpl.getOrDefault(t.getId(), 0));
                voList.add(vo);
            }
        }
        Page<AntibioticTemplateVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    private Map<Long, Integer> loadItemCount(List<Long> templateIds) {
        // MyBatis-Plus 无直接 group by，这里逐模板查 count（模板数量通常较小，可接受）
        Map<Long, Integer> map = new java.util.HashMap<>();
        for (Long tplId : templateIds) {
            Long cnt = itemMapper.selectCount(new LambdaQueryWrapper<AntibioticTemplateItem>()
                    .eq(AntibioticTemplateItem::getTemplateId, tplId));
            map.put(tplId, cnt == null ? 0 : cnt.intValue());
        }
        return map;
    }

    @Override
    public List<AntibioticTemplate> listEnabledByBacteria(Long bacteriaId) {
        if (bacteriaId == null) return Collections.emptyList();
        List<AntibioticTemplateBacteria> bindings = bacteriaMapper.selectList(
                new LambdaQueryWrapper<AntibioticTemplateBacteria>()
                        .eq(AntibioticTemplateBacteria::getBacteriaId, bacteriaId));
        if (bindings == null || bindings.isEmpty()) return Collections.emptyList();
        List<Long> templateIds = bindings.stream().map(AntibioticTemplateBacteria::getTemplateId).distinct().collect(Collectors.toList());
        return this.list(new LambdaQueryWrapper<AntibioticTemplate>()
                .in(AntibioticTemplate::getId, templateIds)
                .eq(AntibioticTemplate::getStatus, 1)
                .orderByDesc(AntibioticTemplate::getIsSystem)
                .orderByAsc(AntibioticTemplate::getSortNo));
    }

    @Override
    public AntibioticTemplateVO getDetail(Long id) {
        AntibioticTemplate t = this.getById(id);
        if (t == null) throw new BusinessException("模板不存在或已删除: " + id);
        AntibioticTemplateVO vo = toHeadVO(t);

        List<AntibioticTemplateBacteria> bindings = bacteriaMapper.selectList(
                new LambdaQueryWrapper<AntibioticTemplateBacteria>()
                        .eq(AntibioticTemplateBacteria::getTemplateId, id));
        List<Long> bacteriaIds = bindings.stream().map(AntibioticTemplateBacteria::getBacteriaId).collect(Collectors.toList());
        vo.setBacteriaIds(bacteriaIds);
        if (!bacteriaIds.isEmpty()) {
            Map<Long, String> nameById = bacteriaService.listByIds(bacteriaIds).stream()
                    .collect(Collectors.toMap(Bacteria::getId, Bacteria::getBacteriaName, (a, b) -> a));
            vo.setBacteriaNames(bacteriaIds.stream().map(b -> nameById.getOrDefault(b, String.valueOf(b))).collect(Collectors.toList()));
        } else {
            vo.setBacteriaNames(Collections.emptyList());
        }

        List<AntibioticTemplateItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<AntibioticTemplateItem>()
                        .eq(AntibioticTemplateItem::getTemplateId, id)
                        .orderByAsc(AntibioticTemplateItem::getSortNo));
        List<AntibioticTemplateVO.ItemVO> itemVOs = new ArrayList<>();
        if (items != null && !items.isEmpty()) {
            List<Long> antibioticIds = items.stream().map(AntibioticTemplateItem::getAntibioticId).collect(Collectors.toList());
            Map<Long, Antibiotic> abMap = antibioticService.listByIds(antibioticIds).stream()
                    .collect(Collectors.toMap(Antibiotic::getId, Function.identity(), (a, b) -> a));
            for (AntibioticTemplateItem it : items) {
                Antibiotic ab = abMap.get(it.getAntibioticId());
                AntibioticTemplateVO.ItemVO iv = new AntibioticTemplateVO.ItemVO();
                iv.setId(it.getId());
                iv.setAntibioticId(it.getAntibioticId());
                iv.setAntibioticCode(ab != null ? ab.getAntibioticCode() : null);
                iv.setAntibioticName(ab != null ? ab.getAntibioticName() : "已停用/删除(#" + it.getAntibioticId() + ")");
                iv.setCategory(ab != null ? ab.getCategory() : null);
                iv.setDefaultMethod(it.getDefaultMethod());
                iv.setSortNo(it.getSortNo());
                iv.setRemark(it.getRemark());
                itemVOs.add(iv);
            }
        }
        vo.setItems(itemVOs);
        return vo;
    }

    @Override
    public AntibioticTemplateApplyVO buildApply(Long id, Long bacteriaId) {
        AntibioticTemplate t = this.getById(id);
        if (t == null) throw new BusinessException("模板不存在或已删除: " + id);
        if (t.getStatus() != null && t.getStatus() != 1) {
            throw new BusinessException("模板「" + t.getName() + "」已停用，无法应用");
        }
        AntibioticTemplateApplyVO vo = new AntibioticTemplateApplyVO();
        vo.setTemplateId(t.getId());
        vo.setTemplateName(t.getName());
        vo.setDefaultMethod(t.getDefaultMethod());

        List<AntibioticTemplateItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<AntibioticTemplateItem>()
                        .eq(AntibioticTemplateItem::getTemplateId, id)
                        .orderByAsc(AntibioticTemplateItem::getSortNo));
        List<AntibioticTemplateApplyVO.ApplyItem> applyItems = new ArrayList<>();
        if (items != null) {
            for (AntibioticTemplateItem it : items) {
                Antibiotic ab = antibioticService.getById(it.getAntibioticId());
                if (ab == null || (ab.getStatus() != null && ab.getStatus() != 1)) {
                    continue; // 抗生素已停用或删除，跳过该条
                }
                AntibioticTemplateApplyVO.ApplyItem ai = new AntibioticTemplateApplyVO.ApplyItem();
                ai.setAntibioticId(ab.getId());
                ai.setAntibioticCode(ab.getAntibioticCode());
                ai.setAntibioticName(ab.getAntibioticName());
                ai.setCategory(ab.getCategory());
                ai.setMethod(it.getDefaultMethod() != null && !it.getDefaultMethod().isEmpty()
                        ? it.getDefaultMethod() : t.getDefaultMethod());
                resolveBreakpoint(ai, ab.getId(), bacteriaId);
                applyItems.add(ai);
            }
        }
        vo.setItems(applyItems);
        return vo;
    }

    private void resolveBreakpoint(AntibioticTemplateApplyVO.ApplyItem ai, Long antibioticId, Long bacteriaId) {
        if (bacteriaId != null) {
            List<AntibioticBreakpoint> points = breakpointService.listByAntibioticAndBacteria(antibioticId, bacteriaId);
            if (points != null && !points.isEmpty()) {
                AntibioticBreakpoint p = points.get(0);
                List<String> parts = new ArrayList<>();
                if (p.getSBreakpoint() != null && !p.getSBreakpoint().isEmpty()) parts.add("敏感(S)≤" + p.getSBreakpoint());
                if (p.getRBreakpoint() != null && !p.getRBreakpoint().isEmpty()) parts.add("耐药(R)≥" + p.getRBreakpoint());
                if (p.getIBreakpoint() != null && !p.getIBreakpoint().isEmpty()) parts.add("中介(I)=" + p.getIBreakpoint());
                if (p.getSDd() != null && !p.getSDd().isEmpty()) parts.add("抑菌圈S≥" + p.getSDd() + "mm");
                if (p.getRDd() != null && !p.getRDd().isEmpty()) parts.add("抑菌圈R≤" + p.getRDd() + "mm");
                if (!parts.isEmpty()) {
                    ai.setBreakpoint(String.join(" ; ", parts));
                }
                String ver = String.join(" ", new String[]{
                        p.getStandard() == null ? "" : p.getStandard(),
                        p.getStandardVersion() == null ? "" : p.getStandardVersion(),
                        p.getMethod() == null ? "" : p.getMethod()
                }).trim();
                ai.setBreakpointSource("专属折点表" + (ver.isEmpty() ? "" : " (" + ver + ")"));
                return;
            }
        }
        ai.setBreakpoint(null);
        ai.setBreakpointSource("暂未配置折点，应用后请手工补录");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrUpdate(AntibioticTemplateSaveRequest req) {
        if (req == null) throw new BusinessException("请求体不能为空");
        if (req.getName() == null || req.getName().trim().isEmpty()) throw new BusinessException("模板名称不能为空");
        if (req.getCode() == null || req.getCode().trim().isEmpty()) throw new BusinessException("模板编码不能为空");
        String code = req.getCode().trim();
        LocalDateTime now = LocalDateTime.now();

        AntibioticTemplate t;
        boolean isUpdate = req.getId() != null;
        if (isUpdate) {
            t = this.getById(req.getId());
            if (t == null) throw new BusinessException("模板不存在或已删除: " + req.getId());
            if (t.getIsSystem() != null && t.getIsSystem() == 1 && !code.equals(t.getCode())) {
                throw new BusinessException("系统内置模板编码不可修改");
            }
            // 唯一性检查(排除自身)
            Long dupId = this.getBaseMapper().selectCount(new LambdaQueryWrapper<AntibioticTemplate>()
                    .eq(AntibioticTemplate::getCode, code)
                    .ne(AntibioticTemplate::getId, req.getId()));
            if (dupId != null && dupId > 0) throw new BusinessException("模板编码已存在: " + code);
            t.setCode(code);
        } else {
            // 新增唯一性检查
            Long dupId = this.getBaseMapper().selectCount(new LambdaQueryWrapper<AntibioticTemplate>()
                    .eq(AntibioticTemplate::getCode, code));
            if (dupId != null && dupId > 0) throw new BusinessException("模板编码已存在: " + code);
            t = new AntibioticTemplate();
            t.setCode(code);
            t.setStatus(req.getStatus() == null ? 1 : req.getStatus());
            t.setIsSystem(0); // 新增均为客户自建
            t.setCreateTime(now);
        }
        t.setName(req.getName().trim());
        t.setTemplateCategory(req.getTemplateCategory());
        t.setDefaultMethod(req.getDefaultMethod());
        t.setDescription(req.getDescription());
        t.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        t.setUpdateTime(now);
        this.saveOrUpdate(t);
        Long tplId = t.getId();

        // 整体替换明细
        itemMapper.delete(new LambdaQueryWrapper<AntibioticTemplateItem>().eq(AntibioticTemplateItem::getTemplateId, tplId));
        if (req.getItems() != null) {
            int sort = 0;
            for (AntibioticTemplateSaveRequest.ItemReq it : req.getItems()) {
                if (it.getAntibioticId() == null) continue;
                AntibioticTemplateItem item = new AntibioticTemplateItem();
                item.setTemplateId(tplId);
                item.setAntibioticId(it.getAntibioticId());
                item.setDefaultMethod(it.getDefaultMethod());
                item.setSortNo(it.getSortNo() == null ? sort++ : it.getSortNo());
                item.setRemark(it.getRemark());
                item.setCreateTime(now);
                itemMapper.insert(item);
                sort++;
            }
        }

        // 整体替换细菌绑定
        bacteriaMapper.delete(new LambdaQueryWrapper<AntibioticTemplateBacteria>().eq(AntibioticTemplateBacteria::getTemplateId, tplId));
        if (req.getBacteriaIds() != null) {
            for (Long bacId : new LinkedHashSet<>(req.getBacteriaIds())) {
                if (bacId == null) continue;
                // 校验细菌字典存在且启用
                Bacteria b = bacteriaService.getById(bacId);
                if (b == null || (b.getStatus() != null && b.getStatus() != 1)) {
                    throw new BusinessException("绑定的细菌无效或已停用: " + bacId);
                }
                AntibioticTemplateBacteria tb = new AntibioticTemplateBacteria();
                tb.setTemplateId(tplId);
                tb.setBacteriaId(bacId);
                tb.setCreateTime(now);
                bacteriaMapper.insert(tb);
            }
        }
        return tplId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        AntibioticTemplate t = this.getById(id);
        if (t == null) throw new BusinessException("模板不存在或已删除: " + id);
        if (t.getIsSystem() != null && t.getIsSystem() == 1) {
            throw new BusinessException("系统内置模板不允许删除，可停用或改名");
        }
        itemMapper.delete(new LambdaQueryWrapper<AntibioticTemplateItem>().eq(AntibioticTemplateItem::getTemplateId, id));
        bacteriaMapper.delete(new LambdaQueryWrapper<AntibioticTemplateBacteria>().eq(AntibioticTemplateBacteria::getTemplateId, id));
        this.removeById(id);
    }

    private AntibioticTemplateVO toHeadVO(AntibioticTemplate t) {
        AntibioticTemplateVO vo = new AntibioticTemplateVO();
        vo.setId(t.getId());
        vo.setCode(t.getCode());
        vo.setName(t.getName());
        vo.setTemplateCategory(t.getTemplateCategory());
        vo.setDefaultMethod(t.getDefaultMethod());
        vo.setDescription(t.getDescription());
        vo.setStatus(t.getStatus());
        vo.setIsSystem(t.getIsSystem());
        vo.setSortNo(t.getSortNo());
        vo.setCreateUserId(t.getCreateUserId());
        vo.setCreateTime(t.getCreateTime() == null ? null : t.getCreateTime().toString());
        return vo;
    }
}