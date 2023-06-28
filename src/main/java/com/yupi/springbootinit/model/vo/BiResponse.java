package com.yupi.springbootinit.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author wj
 * @create_time 2023/6/28
 * @description 图表返回封装类
 */
@Data
@AllArgsConstructor
public class BiResponse {

    /**
     * 生成的图表数据
     */
    private String genChart;

    /**
     * 生成的分析结论
     */
    private String genResult;

    /**
     * 图表ID
     */
    private Long chartId;
}
