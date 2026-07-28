package com.git.hui.springai.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具响应类型注解 —— 用于在工具方法上声明其返回内容的展示类型。
 *
 * <p>本注解是手动工具执行模式（{@code internalToolExecutionEnabled(false)}）下的扩展机制。
 * 在 {@code QaController.qa()} 方法中，通过反射获取 {@code MethodToolCallback} 内部的
 * {@code toolMethod}，再读取该方法上的 {@code @ToolResponseType} 注解值，
 * 从而在运行时动态获知工具返回数据的类型标识。</p>
 *
 * <p>典型应用场景：</p>
 * <ul>
 *   <li>前端渲染路由：根据类型标识决定使用哪种 UI 组件
 *       （如 "card" → 天气卡片组件，"quiz" → 答题交互组件，"chart" → 图表组件）</li>
 *   <li>结果后处理：根据类型选择不同的序列化/加工策略</li>
 *   <li>日志分类：按返回类型对工具调用结果进行分类统计</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @Tool(description = "查询天气")
 * @ToolResponseType("card")   // 声明返回类型为卡片
 * public WeatherCard queryWeather(String city) { ... }
 *
 * @Tool(description = "创建问答题目")
 * @ToolResponseType("quiz")   // 声明返回类型为问答
 * public QuizCard createQuiz(String topic) { ... }
 * }</pre>
 *
 * <p>注解元信息：</p>
 * <ul>
 *   <li>{@code @Target(METHOD)} - 仅可标注在方法上</li>
 *   <li>{@code @Retention(RUNTIME)} - 运行时保留，支持反射读取</li>
 *   <li>{@code @Documented} - 纳入 Javadoc 文档</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/3/6
 * @see com.git.hui.springai.mvc.QaController#qa(String)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolResponseType {
    /**
     * 响应类型标识 —— 用于描述工具方法返回数据的展示类型。
     *
     * <p>常见取值：</p>
     * <ul>
     *   <li>{@code "card"} - 卡片类型（如天气卡片），适合结构化展示</li>
     *   <li>{@code "quiz"} - 问答类型（如选择题），适合交互式答题组件</li>
     *   <li>{@code "chart"} - 图表类型，适合数据可视化展示</li>
     * </ul>
     *
     * @return 类型标识字符串
     */
    String value();
}
