package com.lis.micro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 微生物室内质控记录
 * 支持 7 类物品: 生化反应试剂 / 抗菌药物 / 纸片法药敏 / 诊断血清 / 血培养瓶 / 染色液 / 标准菌株
 * 定性质控: 预期结果 vs 实际结果 -> 合格/不合格
 */
@TableName("lis_micro_qc_record")
public class MicroQcRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类别: 1生化反应试剂 2抗菌药物 3纸片法药敏 4诊断血清 5血培养瓶 6染色液 7标准菌株 */
    private Integer itemType;

    /** 物品编码 (抗菌药物/标准菌株等关联字典编码) */
    private String itemCode;

    /** 物品名称 */
    private String itemName;

    /** 批号 / 质控编号 */
    private String qcLotNo;

    /** 质控标准菌株 (如 ATCC25922 / 大肠埃希菌) */
    private String controlStrain;

    /** 预期结果 (如 阳性/阴性/S/I/R/生长/不生长) */
    private String expectedResult;

    /** 实际结果 */
    private String actualResult;

    /** 判定: PASS合格 FAIL不合格 */
    private String result;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 (冗余) */
    private String operatorName;

    /** 质控操作时间 */
    private LocalDateTime measureTime;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getItemType() { return itemType; }
    public void setItemType(Integer itemType) { this.itemType = itemType; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getQcLotNo() { return qcLotNo; }
    public void setQcLotNo(String qcLotNo) { this.qcLotNo = qcLotNo; }
    public String getControlStrain() { return controlStrain; }
    public void setControlStrain(String controlStrain) { this.controlStrain = controlStrain; }
    public String getExpectedResult() { return expectedResult; }
    public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }
    public String getActualResult() { return actualResult; }
    public void setActualResult(String actualResult) { this.actualResult = actualResult; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getMeasureTime() { return measureTime; }
    public void setMeasureTime(LocalDateTime measureTime) { this.measureTime = measureTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}