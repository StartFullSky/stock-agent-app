package com.stockagent.dto;
import lombok.Data;
@Data
public class StockSearchDTO {
    private String stockCode;
    private String stockName;
    private String market;
    private String industry;
}
