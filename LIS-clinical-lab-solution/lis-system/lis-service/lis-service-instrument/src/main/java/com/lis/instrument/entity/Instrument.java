package com.lis.instrument.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 仪器字典 (查询 lis_instrument 表用于状态展示)
 */
@Data
@TableName("lis_instrument")
public class Instrument implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String instrumentCode;
    private String instrumentName;
    private String brand;
    private String model;
    private String protocolType;
    private String connectType;
    private String channelType;
    private String serialPort;
    private Integer baudRate;
    private String ipAddress;
    private Integer port;
    private String serialNo;
    private LocalDate installDate;
    private LocalDate warrantyExpiry;
    private String department;
    private Integer status;
    private Integer workStatus;
    private LocalDateTime lastOnlineTime;
    private LocalDateTime createTime;

    // ===== 本地文件采集配置 (connectType=FILE) =====
    /** 结果文件目录或文件路径 */
    private String filePath;
    /** 文件格式: CSV / TXT / MINDRAY / ROCHE / ABX 等 */
    private String fileFormat;
    /** 文件名匹配模式 (正则) */
    private String filePattern;
    /** 已处理文件归档目录 */
    private String processedDir;
    /** 轮询间隔 (秒) */
    private Integer pollInterval;

    // ===== 数据库采集配置 (connectType=DB) =====
    /** 数据库类型: POSTGRESQL / MYSQL / SQLSERVER / ORACLE */
    private String dbType;
    private String dbHost;
    private Integer dbPort;
    private String dbName;
    private String dbUser;
    private String dbPassword;
    /** 驱动的类名 (可选, 默认按 dbType 推断) */
    private String dbDriver;
    /** 结果表名 */
    private String dbTable;
    /** 列映射: 字段=源列, 逗号分隔, 如 sampleNo=sample_no,testCode=item_code,value=result_value,unit=unit,refRange=ref_range,abnormalFlag=flag */
    private String dbColumns;
    /** 附加查询条件 (where 片段, 不含 WHERE 关键字) */
    private String dbWhere;
    /** 增量水位线列 (源表自增ID或时间戳列, 用于增量读取去重) */
    private String dbWatermarkCol;
    /** 上次成功轮询时间 (水位线) */
    private LocalDateTime lastPolledTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInstrumentCode() {
        return instrumentCode;
    }

    public void setInstrumentCode(String instrumentCode) {
        this.instrumentCode = instrumentCode;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }

    public String getConnectType() {
        return connectType;
    }

    public void setConnectType(String connectType) {
        this.connectType = connectType;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getSerialPort() {
        return serialPort;
    }

    public void setSerialPort(String serialPort) {
        this.serialPort = serialPort;
    }

    public Integer getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(Integer baudRate) {
        this.baudRate = baudRate;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public LocalDate getInstallDate() {
        return installDate;
    }

    public void setInstallDate(LocalDate installDate) {
        this.installDate = installDate;
    }

    public LocalDate getWarrantyExpiry() {
        return warrantyExpiry;
    }

    public void setWarrantyExpiry(LocalDate warrantyExpiry) {
        this.warrantyExpiry = warrantyExpiry;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getWorkStatus() {
        return workStatus;
    }

    public void setWorkStatus(Integer workStatus) {
        this.workStatus = workStatus;
    }

    public LocalDateTime getLastOnlineTime() {
        return lastOnlineTime;
    }

    public void setLastOnlineTime(LocalDateTime lastOnlineTime) {
        this.lastOnlineTime = lastOnlineTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    public String getProcessedDir() {
        return processedDir;
    }

    public void setProcessedDir(String processedDir) {
        this.processedDir = processedDir;
    }

    public Integer getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Integer pollInterval) {
        this.pollInterval = pollInterval;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public String getDbHost() {
        return dbHost;
    }

    public void setDbHost(String dbHost) {
        this.dbHost = dbHost;
    }

    public Integer getDbPort() {
        return dbPort;
    }

    public void setDbPort(Integer dbPort) {
        this.dbPort = dbPort;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getDbUser() {
        return dbUser;
    }

    public void setDbUser(String dbUser) {
        this.dbUser = dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public String getDbDriver() {
        return dbDriver;
    }

    public void setDbDriver(String dbDriver) {
        this.dbDriver = dbDriver;
    }

    public String getDbTable() {
        return dbTable;
    }

    public void setDbTable(String dbTable) {
        this.dbTable = dbTable;
    }

    public String getDbColumns() {
        return dbColumns;
    }

    public void setDbColumns(String dbColumns) {
        this.dbColumns = dbColumns;
    }

    public String getDbWhere() {
        return dbWhere;
    }

    public void setDbWhere(String dbWhere) {
        this.dbWhere = dbWhere;
    }

    public String getDbWatermarkCol() {
        return dbWatermarkCol;
    }

    public void setDbWatermarkCol(String dbWatermarkCol) {
        this.dbWatermarkCol = dbWatermarkCol;
    }

    public LocalDateTime getLastPolledTime() {
        return lastPolledTime;
    }

    public void setLastPolledTime(LocalDateTime lastPolledTime) {
        this.lastPolledTime = lastPolledTime;
    }
}
