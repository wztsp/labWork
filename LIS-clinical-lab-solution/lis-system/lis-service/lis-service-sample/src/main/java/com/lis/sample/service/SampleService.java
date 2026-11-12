package com.lis.sample.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.api.dto.SampleDTO;
import com.lis.api.dto.SampleQueryDTO;
import com.lis.api.dto.SampleReceiveDTO;
import com.lis.api.dto.SampleCollectDTO;
import com.lis.api.dto.SampleBatchDTO;
import com.lis.api.dto.SampleRejectDTO;
import com.lis.api.dto.BatchBarcodeDTO;
import com.lis.api.vo.SampleVO;
import com.lis.api.vo.TubeGroupVO;
import com.lis.sample.entity.Sample;

import java.util.List;
import java.util.Map;

/**
 * 标本 Service
 */
public interface SampleService extends IService<Sample> {

    /**
     * 创建标本: 生成条码(用StringUtil.genBizNo("S")), 保存到DB, 发MQ消息 sample.created
     */
    SampleVO createSample(SampleDTO dto);

    /**
     * 标本核收: 校验仪器能力, 按仪器序号生成标本号, 更新状态为已核收(2), 记录核收时间/仪器/标本号, 发MQ消息 sample.received
     */
    SampleVO receiveSample(SampleReceiveDTO dto);

    /**
     * 核收前预检: 校验仪器是否能检测该条码对应的所有检验项目
     * 返回不可检测的项目名称列表, 空列表表示校验通过
     */
    List<String> precheckReceive(String barcode, Long instrumentId);

    /**
     * 按就诊/门诊/住院标识查询可核收标本(状态=已送检2/已签收3), 用于技师工作站按标识核收时多标本选择
     * @param type    标识类型: visitNo就诊号 / outpatient门诊号 / inpatient住院号
     * @param keyword 标识值(就诊号)
     * @return 可核收标本列表(含检验项目文字)
     */
    List<Map<String, Object>> lookupReceivable(String type, String keyword);

    /**
     * 标本采集: 更新状态为已采集(1), 记录采集时间和采集人
     * 采集人自动取当前登录用户(通过userId查lis_staff获取员工ID+姓名)
     * @param currentUserId 当前登录用户ID (sys_user.id, 由网关注入)
     */
    SampleVO collectSample(SampleCollectDTO dto, Long currentUserId);

    /**
     * 标本送检: 校验状态为已采集(1), 记录送检时间和送检人
     * 送检人自动取当前登录用户
     * @param currentUserId 当前登录用户ID (sys_user.id)
     */
    List<SampleVO> deliverSamples(List<String> barcodes, Long currentUserId);

    /**
     * 标本签收: 校验已送检(deliverTime不为空), 记录签收时间和签收人
     * 签收人自动取当前登录用户
     * @param currentUserId 当前登录用户ID (sys_user.id)
     */
    List<SampleVO> signOffSamples(List<String> barcodes, Long currentUserId);

    /**
     * 标本送检 (DTO版本)
     */
    default List<SampleVO> deliverSamples(SampleBatchDTO dto, Long currentUserId) {
        return deliverSamples(dto.getBarcodes(), currentUserId);
    }

    /**
     * 标本签收 (DTO版本)
     */
    default List<SampleVO> signOffSamples(SampleBatchDTO dto, Long currentUserId) {
        return signOffSamples(dto.getBarcodes(), currentUserId);
    }

    /**
     * 按条码查询
     */
    SampleVO getByBarcode(String barcode);

    /**
     * 按ID查询
     */
    SampleVO getById(Long id);

    /**
     * 分页查询
     */
    Page<SampleVO> queryPage(SampleQueryDTO query);

    /**
     * 更新标本状态
     */
    SampleVO updateStatus(Long id, int status);

    /**
     * 标本追踪(返回全生命周期状态)
     */
    SampleVO trackSample(Long id);

    /**
     * 标本退费: 校验标本状态, 更新为 STATUS_REFUNDED, 记录 remark, 发 MQ 消息 sample.refunded
     */
    SampleVO refundSample(Long id, String reason);

    /**
     * 标本作废: 校验标本状态, 更新为 STATUS_VOIDED, 记录 remark
     */
    SampleVO voidSample(Long id, String reason);

    /**
     * 标本拒收: 校验状态, 更新为 STATUS_REJECTED, 记录拒收时间/人/不合格类型/原因
     * 拒收人自动取当前登录用户
     * @param currentUserId 当前登录用户ID (sys_user.id)
     */
    SampleVO rejectSample(SampleRejectDTO dto, Long currentUserId);

    /**
     * 标本取消送检: 批量取消送检, 清除送检时间/送检人
     * 仅允许已送检但未签收、未核收、未报告的标本取消送检
     * @param currentUserId 当前登录用户ID (sys_user.id)
     */
    List<SampleVO> cancelDeliverSamples(List<String> barcodes, Long currentUserId);

    /**
     * 标本取消送检 (DTO版本)
     */
    default List<SampleVO> cancelDeliverSamples(SampleBatchDTO dto, Long currentUserId) {
        return cancelDeliverSamples(dto.getBarcodes(), currentUserId);
    }

    /**
     * 标本取消签收: 批量取消签收, 状态回退到已送检, 清除签收时间/签收人
     * 仅允许已签收但未核收、未报告的标本取消签收
     * @param currentUserId 当前登录用户ID (sys_user.id)
     */
    List<SampleVO> cancelSignOffSamples(List<String> barcodes, Long currentUserId);

    /**
     * 标本取消签收 (DTO版本)
     */
    default List<SampleVO> cancelSignOffSamples(SampleBatchDTO dto, Long currentUserId) {
        return cancelSignOffSamples(dto.getBarcodes(), currentUserId);
    }

    /**
     * 计算试管分组: 按就诊维度查询所有申请的检验项目, 按相同试管+相同标本类型跨申请合管
     * 返回合管后的试管分组列表 (每个分组可能包含来自不同申请的项目)
     * @param visitId 就诊ID
     */
    List<TubeGroupVO> calculateTubeGroups(Long visitId);

    /**
     * 批量生成条码: 按试管分组批量创建标本, 每组一个条码
     */
    List<SampleVO> batchCreateBarcodes(BatchBarcodeDTO dto);

    /**
     * 取消条码: 删除标本及中间表关联, 允许重新生成条码
     * 仅允许未采集(STATUS_APPLIED)的标本取消条码, 已采集/送检/签收/核收的标本不可取消
     * @param barcode 标本条码号
     */
    void cancelBarcode(String barcode);

    /**
     * 标本取消采集: 批量取消采集, 状态回退到已申请(0), 清除采集时间/采集人
     * 仅允许已采集(1)且未送检/未签收/未核收/未报告的标本取消采集
     * @param currentUserId 当前登录用户ID (sys_user.id)
     */
    List<SampleVO> cancelCollectSamples(List<String> barcodes, Long currentUserId);

    /**
     * 标本取消采集 (DTO版本)
     */
    default List<SampleVO> cancelCollectSamples(SampleBatchDTO dto, Long currentUserId) {
        return cancelCollectSamples(dto.getBarcodes(), currentUserId);
    }
}
