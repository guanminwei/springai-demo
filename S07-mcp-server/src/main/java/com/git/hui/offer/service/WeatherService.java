package com.git.hui.offer.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * 天气查询工具服务 —— MCP Server 对外暴露的示例工具实现。
 *
 * <p>本类演示如何通过 {@link Tool @Tool} 注解将方法标记为 AI 可调用的工具，
 * 与 {@link DateService} 一起被 {@link ToolConfig} 扫描并注册为 MCP 可用工具。</p>
 *
 * @author YiHui
 * @date 2025/7/27
 * @see ToolConfig 工具注册配置类
 */
@Service
public class WeatherService {

    /**
     * 根据城市名称查询当前天气信息。
     *
     * @param city 目标城市名称，如 "北京"、"Tokyo"、"New York" 等
     * @return 模拟的天气信息字符串
     */
    @Tool(description = "根据城市名称查询该城市的当前天气信息。" +
            "当用户询问某个城市的天气、气温、天气状况时调用此工具。")
    public String getWeather(
            @ToolParam(description = "需要查询天气的目标城市名称，如 北京、Shanghai、Tokyo 等") String city) {

        // 模拟天气数据返回（实际项目中应对接真实天气 API）
        String weather = city + "：晴，25°C，湿度 60%";
        System.out.println("查询城市天气：" + weather);
        return weather;
    }
}
