package com.stockagent.dto;
import lombok.Data;
@Data
public class MarketNewsDTO {
    private String category;
    private Long datetime;
    private String headline;
    private Long id;
    private String image;
    private String related;
    private String source;
    private String summary;
    private String url;
}
