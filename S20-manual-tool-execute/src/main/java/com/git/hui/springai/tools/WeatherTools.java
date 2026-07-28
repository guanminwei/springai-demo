package com.git.hui.springai.tools;

import com.git.hui.springai.tools.dto.WeatherCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * 天气查询工具类 —— 作为 Spring AI Function Calling 的工具提供方，向大模型暴露"查天气"能力。
 *
 * <p>本类通过 {@code @Tool} 注解将 {@link #queryWeather} 方法注册为可被大模型调用的工具。
 * 当用户提出"查一下北京的天气"等请求时，大模型会识别意图并调用此工具，
 * 由本类返回结构化的 {@link WeatherCard} 数据（温度、湿度、AQI、穿衣建议等）。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>使用 {@code @Tool(name = "queryWeather")} 显式指定工具名称，
 *       确保在多工具类场景下名称唯一（避免与 QuizTools 中注释的同名方法冲突）</li>
 *   <li>使用 {@code @ToolResponseType("card")} 自定义注解声明返回类型，
 *       在手动执行模式下可通过反射读取此元数据，用于前端渲染路由</li>
 *   <li>方法签名中包含 {@link ToolContext} 参数，用于接收调用方透传的业务上下文
 *       （如 sessionId、userId），实现工具执行时的链路追踪与权限控制</li>
 *   <li>当前使用随机模拟数据，实际生产环境应替换为真实天气 API 调用</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/3/6
 * @see WeatherCard
 * @see ToolResponseType
 */
@Slf4j
@Service
public class WeatherTools {
    /**
     * 查询指定城市的天气信息 —— 返回包含温度、湿度、AQI、风向、穿衣建议等完整天气数据。
     *
     * <p>本方法通过 {@code @Tool} 注解暴露给大模型。大模型从用户输入中提取城市名称后，
     * 以 JSON 参数形式调用本方法。方法内部通过 {@link ToolContext} 获取调用上下文信息，
     * 可用于日志审计、用户鉴权等横切关注点。</p>
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>从 ToolContext 中提取业务上下文（userId、sessionId）并记录日志</li>
     *   <li>生成模拟天气数据（温度、湿度、AQI、天气状况、风向风力）</li>
     *   <li>根据温度和天气状况计算穿衣建议</li>
     *   <li>根据天气状况和 AQI 生成出行提示</li>
     *   <li>组装 {@link WeatherCard} 返回</li>
     * </ol>
     *
     * @param city        城市名称（如"北京"、"上海"），由大模型从用户输入中提取
     * @param toolContext 工具执行上下文，包含调用方透传的 sessionId、userId 等业务信息（可选）
     * @return 结构化的天气卡片数据
     */
    @Tool(name = "queryWeather", description = "查询指定城市的天气信息，返回详细的天气状况、温度、湿度等数据")
    @ToolResponseType("card")  // 声明返回类型为 card，手动模式下可通过反射读取
    public WeatherCard queryWeather(
            @ToolParam(description = "城市名称，如北京、上海、广州等") String city,
            ToolContext toolContext) {  // ToolContext 由框架自动注入，无需大模型传参

        // ========== 1. 从工具上下文中提取业务信息 ==========
        // ToolContext 由 QaController 在调用时通过 toolContextData 构建并透传
        if (toolContext != null && !toolContext.getContext().isEmpty()) {
            log.info("【工具上下文】queryWeather - context: {}", toolContext.getContext());
            // 提取调用方标识，用于链路追踪和审计日志
            String userId = (String) toolContext.getContext().get("userId");
            String sessionId = (String) toolContext.getContext().get("sessionId");
            log.info("用户 {} 在会话 {} 中查询 {} 的天气", userId, sessionId, city);
        }

        log.info("[inner-tool] 查询天气：{}", city);

        // ========== 2. 生成模拟天气数据 ==========
        // TODO: 实际场景中应替换为真实天气 API 调用（如和风天气、高德天气等）
        Random random = new Random();
        int temperature = 15 + random.nextInt(20); // 温度范围：15-35°C
        int humidity = 40 + random.nextInt(40);    // 湿度范围：40-80%
        int aqi = 20 + random.nextInt(80);         // AQI 范围：20-100

        // 随机选取天气状况
        String[] conditions = {"晴", "多云", "阴", "小雨", "大雨"};
        String condition = conditions[random.nextInt(conditions.length)];

        // 随机选取风向和风力等级
        String[] directions = {"东风", "南风", "西风", "北风", "东南风", "东北风"};
        String windDirection = directions[random.nextInt(directions.length)];
        String windLevel = (random.nextInt(5) + 1) + "级";

        // ========== 3. 生成附加建议信息 ==========
        String dressAdvice = getDressAdvice(temperature, condition); // 穿衣建议
        String tips = getWeatherTips(condition, aqi);                // 出行提示

        // ========== 4. 组装并返回结构化天气卡片 ==========
        return WeatherCard.builder()
                .city(city)
                .condition(condition)
                .temperature(temperature)
                .humidity(humidity)
                .aqi(aqi)
                .windDirection(windDirection)
                .windLevel(windLevel)
                .dressAdvice(dressAdvice)
                .tips(tips)
                .build();
    }


    /**
     * 根据温度生成穿衣建议。
     *
     * <p>温度区间划分：</p>
     * <ul>
     *   <li>&lt; 10°C → 厚外套/羽绒服</li>
     *   <li>10-20°C → 长袖衬衫/薄外套</li>
     *   <li>20-28°C → 短袖 T 恤</li>
     *   <li>&ge; 28°C → 清凉夏装</li>
     * </ul>
     *
     * @param temperature 当前温度（°C）
     * @param condition   天气状况（预留参数，未来可扩展为根据雨天推荐防水外套等）
     * @return 穿衣建议文本
     */
    private String getDressAdvice(int temperature, String condition) {
        if (temperature < 10) {
            return "建议穿厚外套或羽绒服，注意保暖";
        } else if (temperature < 20) {
            return "建议穿长袖衬衫或薄外套";
        } else if (temperature < 28) {
            return "建议穿短袖 T 恤，清凉透气";
        } else {
            return "建议穿清凉夏装，注意防暑降温";
        }
    }

    /**
     * 根据天气状况和空气质量指数生成出行提示。
     *
     * <p>提示由两部分拼接：</p>
     * <ul>
     *   <li>天气状况提示：晴/多云/阴/小雨/大雨 各对应不同出行建议</li>
     *   <li>空气质量提示：AQI ≤ 50 优 / ≤ 100 良好 / &gt; 100 较差</li>
     * </ul>
     *
     * @param condition 天气状况文本（如"晴"、"小雨"）
     * @param aqi       空气质量指数
     * @return 拼接后的出行提示文本
     */
    private String getWeatherTips(String condition, int aqi) {
        StringBuilder tips = new StringBuilder();

        switch (condition) {
            case "晴":
                tips.append("阳光明媚，适合户外活动。");
                break;
            case "多云":
                tips.append("云层较多，紫外线适中。");
                break;
            case "阴":
                tips.append("天气阴沉，请保持好心情。");
                break;
            case "小雨":
                tips.append("有小雨，出门请带伞。");
                break;
            case "大雨":
                tips.append("雨势较大，尽量减少外出。");
                break;
        }

        if (aqi <= 50) {
            tips.append("空气质量优，适合户外运动。");
        } else if (aqi <= 100) {
            tips.append("空气质量良好，可正常活动。");
        } else {
            tips.append("空气质量较差，敏感人群减少外出。");
        }

        return tips.toString();
    }

}
