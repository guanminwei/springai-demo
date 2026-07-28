package com.git.hui.springai.app.react.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;

/**
 * 计算器与天气查询工具 - 供 ReAct Agent 调用的 Function Calling 工具集
 * <p>
 * 本类定义了 ReAct Agent 可使用的工具方法，包括基础四则运算和模拟天气查询。
 * 大模型在 ReAct 循环的 Thinking 阶段会根据问题自动决定调用哪些工具。
 * <p>
 * 工具列表：
 * <ul>
 *     <li>{@link #add(double, double)} - 加法运算</li>
 *     <li>{@link #subtract(double, double)} - 减法运算</li>
 *     <li>{@link #multiply(double, double)} - 乘法运算</li>
 *     <li>{@link #divide(double, double)} - 除法运算（含除零校验）</li>
 *     <li>{@link #weather(String)} - 天气查询（模拟数据）</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>
 * CalculatorTools tools = new CalculatorTools();
 * List&lt;ToolCallback&gt; callbacks = tools.getTools();
 * // 将 callbacks 传给 SimpleReActAgent 或 StreamReActAgent
 * </pre>
 *
 * @see SimpleReActAgent
 * @see StreamReActAgent
 */
public class CalculatorTools {
    private static final Logger log = LoggerFactory.getLogger(CalculatorTools.class);

    /**
     * 加法运算
     */
    @Tool(description = "执行加法运算，返回两个数的和")
    public double add(@ToolParam(description = "第一个加数") double a,
                      @ToolParam(description = "第二个加数") double b) {
        log.debug("[🔨] 执行加法：{} + {}", a, b);
        return a + b;
    }

    /**
     * 减法运算
     */
    @Tool(description = "执行减法运算，返回两个数的差")
    public double subtract(@ToolParam(description = "被减数") double a,
                           @ToolParam(description = "减数") double b) {
        log.debug("[🔨] 执行减法：{} - {}", a, b);
        return a - b;
    }

    /**
     * 乘法运算
     */
    @Tool(description = "执行乘法运算，返回两个数的积")
    public double multiply(@ToolParam(description = "第一个乘数") double a,
                           @ToolParam(description = "第二个乘数") double b) {
        log.debug("[🔨] 执行乘法：{} * {}", a, b);
        return a * b;
    }

    /**
     * 除法运算
     */
    @Tool(description = "执行除法运算，返回两个数的商")
    public double divide(@ToolParam(description = "被除数") double a,
                         @ToolParam(description = "除数") double b) {
        log.debug("[🔨] 执行除法：{} / {}", a, b);
        if (b == 0) {
            throw new ArithmeticException("除数不能为零");
        }
        return a / b;
    }


    /**
     * 天气查询 - 返回指定城市的模拟天气和温度信息
     * <p>
     * 注意：本方法为模拟实现，随机返回天气和温度。
     * 生产环境应替换为真实天气 API 调用。
     *
     * @param city 城市名称
     * @return 格式化的天气信息字符串
     */
    @Tool(description = "查询天气信息")
    public String weather(@ToolParam(description = "城市名称") String city) {
        log.debug("[🔨] 执行天气查询：{}", city);
        // 随机返回温度信息，有一个列表用于随机取值
        List<String> temperatures = List.of("25°C", "27°C", "23°C", "21°C", "19°C");
        // 天气列表
        List<String> weathers = List.of("晴天", "阴天", "雨天", "雷雨", "雪天");
        // 返回天气 + 温度
        return "当前" + city + "的天气为：" + weathers.get((int) (Math.random() * weathers.size())) + " ,气温为：" + temperatures.get((int) (Math.random() * temperatures.size()));
    }

    /**
     * 获取所有工具回调
     */
    public List<ToolCallback> getTools() {
        ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(this)
                .build()
                .getToolCallbacks();
        return List.of(toolCallbacks);
    }
}
