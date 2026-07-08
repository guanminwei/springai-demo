package com.git.hui.offer.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具服务 —— MCP Server 对外暴露的工具实现。
 *
 * <p>本类通过 {@link Tool @Tool} 注解将方法标记为 AI 可调用的工具，
 * 由 {@link ToolConfig} 中的 {@link org.springframework.ai.tool.method.MethodToolCallbackProvider}
 * 扫描并注册为工具回调。当 AI 模型在对话中判断需要获取某个时区的当前时间时，
 * 会自动调用本类中的工具方法，并将返回结果作为上下文继续对话。</p>
 *
 * <h3>工具调用流程</h3>
 * <pre>
 *   用户提问 "现在东京几点？"
 *       ↓
 *   AI 模型识别意图，选择调用 getTimeByZoneId 工具
 *       ↓
 *   Spring AI 框架反射调用本方法，传入 area=Asia/Tokyo
 *       ↓
 *   方法返回格式化时间字符串
 *       ↓
 *   AI 模型将结果组织为自然语言回复用户
 * </pre>
 *
 * @author YiHui
 * @date 2025/7/27
 * @see ToolConfig 工具注册配置类，负责将本服务注册为 MCP 可用工具
 */
@Service
public class DateService {

    /**
     * 根据指定时区返回当前时间。
     *
     * <p>该方法被 {@link Tool @Tool} 注解标记，Spring AI 框架会将 description 内容
     * 作为工具描述提供给 AI 模型，帮助模型判断何时应调用此工具。
     * 参数 {@code area} 通过 {@link ToolParam @ToolParam} 注解描述，
     * 框架会将其生成到 JSON Schema 的参数定义中，引导模型传入正确的时区值。</p>
     *
     * @param area 目标时区标识，如 {@code Asia/Tokyo}、{@code America/New_York}、
     *             {@code Europe/London} 等标准 IANA 时区名称。
     *             该参数会被 AI 模型根据用户意图自动填充
     * @return 格式化后的时间字符串，格式为 {@code yyyy-MM-dd HH:mm:ss}，
     *         表示指定时区的当前本地时间
     */
    @Tool(description = "根据用户指定的时区，返回该时区的当前本地时间。" +
            "当用户询问某个城市或地区的当前时间时调用此工具。" +
            "参数为 IANA 标准时区名称，如 Asia/Tokyo、America/New_York、Europe/London 等。")
    public String getTimeByZoneId(
            @ToolParam(description = "需要查询时间的目标时区标识，" +
                    "使用 IANA 标准时区名称格式，如 Asia/Shanghai、Asia/Tokyo、America/New_York 等") ZoneId area) {

        // 基于系统当前时刻，转换到指定时区获取对应的 ZonedDateTime
        ZonedDateTime time = ZonedDateTime.now(area);

        // 定义输出格式：年-月-日 时:分:秒，符合中文用户常见阅读习惯
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String ans = time.format(formatter);

        // 控制台打印调试日志，便于开发阶段排查工具调用情况
        System.out.println("传入的时区是：" + area + "-" + ans);
        return ans;
    }
}
