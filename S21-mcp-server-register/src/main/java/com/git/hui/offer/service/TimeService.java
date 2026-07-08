package com.git.hui.offer.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 方式二：@Tool 注解 + ToolCallbackProvider 注册 的工具服务。
 *
 * <p>本类使用 Spring AI 通用的 {@link Tool @Tool} 注解标记工具方法，
 * 然后由 {@link ToolCallbackConfig} 通过 {@code MethodToolCallbackProvider}
 * 扫描并桥接为 MCP Server 可识别的工具规范。</p>
 *
 * <h3>与方式一的区别</h3>
 * <ul>
 *   <li>{@code @Tool} 是 Spring AI 通用注解，可用于 ChatClient、ChatModel 等多种场景</li>
 *   <li>{@code @McpTool} 是 MCP 专用注解，仅在 MCP Server 场景下由注解扫描器自动注册</li>
 *   <li>使用 {@code @Tool} 时，需要手动编写配置类将其桥接到 MCP Server</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/7/8
 * @see ToolCallbackConfig 配置类，负责将本服务注册为 MCP 工具
 */
@Service
public class TimeService {

    /**
     * 根据时区查询当前时间。
     *
     * @param area 目标时区标识，如 Asia/Tokyo、America/New_York
     * @return 格式化后的时间字符串
     */
    @Tool(description = "根据用户指定的时区，返回该时区的当前本地时间。" +
            "当用户询问某个城市或地区的当前时间时调用此工具。" +
            "参数为 IANA 标准时区名称，如 Asia/Tokyo、America/New_York 等。")
    public String getTimeByZone(
            @ToolParam(description = "需要查询时间的目标时区标识，" +
                    "使用 IANA 标准时区名称格式，如 Asia/Shanghai、Asia/Tokyo") ZoneId area) {

        ZonedDateTime time = ZonedDateTime.now(area);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String ans = time.format(formatter);
        System.out.println("[ToolCallbackProvider] 查询时区时间: " + area + " -> " + ans);
        return ans;
    }
}
