package com.lis.instrument.handler;

/**
 * 仪器处理器接口 (每台仪器品牌一个 Handler)
 */
public interface InstrumentHandler {

    /**
     * 仪器品牌标识 (如 MINDRAY / SYSMEX / ROCHE / ABBOTT)
     */
    String getBrand();

    /**
     * 处理仪器原始数据
     * @param rawData 原始报文
     * @param instrumentId 仪器ID
     */
    void handleData(String rawData, Long instrumentId);
}
