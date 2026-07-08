package com.git.hui.offer.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时区时间服务 —— MCP Server 工具提供者
 * <p>
 * 本服务通过 Spring AI 的 {@link Tool} 注解将方法注册为 MCP 工具，
 * 使 AI 模型能够在对话过程中调用该工具获取指定时区的当前时间。
 * <p>
 * 工具注册流程：
 * <ol>
 *   <li>Spring 容器启动时扫描本类，发现 {@link Tool} 注解</li>
 *   <li>{@link ToolConfig} 中的 {@code MethodToolCallbackProvider} 将本实例包装为 MCP 工具回调</li>
 *   <li>MCP Server 将工具元信息（名称、描述、参数 Schema）暴露给 MCP Client</li>
 *   <li>AI 模型在推理时可调用该工具，获取实时时区时间</li>
 * </ol>
 *
 * @author YiHui
 * @date 2025/7/27
 * @see ToolConfig 工具注册配置类
 */
@Service
public class DateService {

    /**
     * 根据指定时区返回当前时间
     * <p>
     * 本方法被 {@link Tool} 注解标记为 MCP 工具，AI 模型可通过函数调用（Function Calling）触发执行。
     * 工具描述信息会作为工具的说明文档传递给 AI 模型，帮助模型判断何时调用该工具。
     *
     * @param area 用户指定的时区ID，需为标准 IANA 时区格式，
     *             例如 Asia/Shanghai、Asia/Tokyo、America/New_York、Europe/London
     * @return 指定时区的当前时间字符串，格式为 yyyy-MM-dd HH:mm:ss
     */
    @Tool(description = "根据用户指定的时区返回该时区的当前时间，适用于用户明确提供了时区信息（如 Asia/Tokyo、America/New_York）的场景，内部会将系统时间转换为目标时区并格式化为 yyyy-MM-dd HH:mm:ss 返回")
    public String getTimeByZoneId(@ToolParam(description = "用户指定的时区ID，需为标准时区格式，例如 Asia/Shanghai、Asia/Tokyo、America/New_York、Europe/London 等") ZoneId area) {
        // 基于系统当前时间戳，转换到指定时区，得到 ZonedDateTime 对象
        ZonedDateTime time = ZonedDateTime.now(area);

        // 定义输出格式：年-月-日 时:分:秒，便于阅读和下游处理
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 将 ZonedDateTime 格式化为字符串
        String ans = time.format(formatter);

        // 打印日志，记录传入的时区和转换结果，便于调试
        System.out.println("传入的时区是：" + area + "-" + ans);

        return ans;
    }
}
