package com.lis.sample.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lis.api.dto.OrderDTO;
import com.lis.api.dto.OrderQueryDTO;
import com.lis.api.vo.OrderVO;
import com.lis.sample.entity.LabOrder;

import java.util.List;

/**
 * 检验申请 Service
 */
public interface LabOrderService extends IService<LabOrder> {

    /**
     * 接收HIS检验申请: 一个申请只能含一个检验项目, 多个项目拆分成多个独立申请
     * 每个拆分后的申请独立生成 orderNo, 共用同一 visitId/patientId
     * @return 拆分后的申请列表 (1个item = 1个申请)
     */
    List<LabOrder> receiveOrder(OrderDTO dto);

    /**
     * 查询申请
     */
    LabOrder getOrder(Long id);

    /**
     * 取消申请
     */
    void cancelOrder(Long id, String reason);

    /**
     * 分页查询申请 (支持就诊号/患者姓名/申请单号查询, 返回申请+项目+患者+就诊信息)
     */
    Page<OrderVO> queryPage(OrderQueryDTO query);

    /**
     * 临时: 清理patient/apply_dept等字段含 ??? 乱码的申请及关联数据
     * @return 清理的申请条数
     */
    int cleanupGarbage();

    /**
     * 更新申请的采集方式
     * @param orderId 申请ID
     * @param collectionMethodId 采集方式ID
     * @param collectionMethodName 采集方式名称(冗余)
     */
    void updateCollectionMethod(Long orderId, Long collectionMethodId, String collectionMethodName);
}
