package com.git.hui.springai.tools;

import com.git.hui.springai.tools.dto.QuizCard;
import com.git.hui.springai.tools.dto.WeatherCard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 知识问答工具类 —— 作为 Spring AI Function Calling 的工具提供方，向大模型暴露"出题"能力。
 *
 * <p>本类通过 {@code @Tool} 注解将 {@link #createQuiz} 方法注册为可被大模型调用的工具。
 * 当用户提出"出一道关于 XX 的选择题"等请求时，大模型会识别并调用此工具，
 * 由本类返回结构化的 {@link QuizCard} 数据（包含题目、选项、答案、解析等）。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>使用 {@code @ToolResponseType("quiz")} 自定义注解声明返回类型，
 *       在手动执行模式下可通过反射读取此元数据，用于前端渲染路由</li>
 *   <li>内部维护一个基于主题关键词的题库（{@link #initQuizBank()}），
 *       支持 default / spring / ai 三个主题，未匹配时回退到默认题目</li>
 *   <li>被注释的 fetchWeather 方法演示了"同名工具冲突"问题：
 *       若两个工具类注册了同名工具（如 queryWeather），Spring AI 会抛出
 *       {@code IllegalStateException: Multiple tools with the same name}</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/3/6
 * @see QuizCard
 * @see ToolResponseType
 */
@Slf4j
@Component
public class QuizTools {

//    ========== 【反例演示】同名工具冲突 ==========
//    以下方法被注释，用于说明：若 QuizTools 和 WeatherTools 同时注册名为 "queryWeather" 的工具，
//    Spring AI 在执行时会抛出：
//    java.lang.IllegalStateException: Multiple tools with the same name (queryWeather) found in ToolCallingChatOptions
//    因此在多工具类协作时，必须确保工具名称全局唯一。
//
//    @Tool(name = "queryWeather", description = "查询指定国家的天气信息，返回详细的天气状况、温度、湿度等数据")
//    @ToolResponseType("card")  // 声明返回类型为 card
//    public WeatherCard fetchWeather(
//            @ToolParam(description = "城市名称，如北京、上海、广州等") String city,
//            ToolContext toolContext) {  // ✅ 添加工具上下文参数
//
//        // 从上下文中获取额外信息
//        if (toolContext != null && !toolContext.getContext().isEmpty()) {
//            log.info("【工具上下文】queryWeather - context: {}", toolContext.getContext());
//            // 可以从中获取 userId, sessionId, appId 等信息
//            String userId = (String) toolContext.getContext().get("userId");
//            String sessionId = (String) toolContext.getContext().get("sessionId");
//            log.info("用户 {} 在会话 {} 中查询 {} 的天气", userId, sessionId, city);
//        }
//
//        log.info("[inner-tool] 查询天气：{}", city);
//
//        // TODO: 实际场景中应该调用天气 API
//        // 这里使用模拟数据演示
//        Random random = new Random();
//        int temperature = 15 + random.nextInt(20); // 15-35 度
//        int humidity = 40 + random.nextInt(40);    // 40-80%
//        int aqi = 20 + random.nextInt(80);         // 20-100
//
//        String[] conditions = {"晴", "多云", "阴", "小雨", "大雨"};
//        String condition = conditions[random.nextInt(conditions.length)];
//
//        String[] directions = {"东风", "南风", "西风", "北风", "东南风", "东北风"};
//        String windDirection = directions[random.nextInt(directions.length)];
//        String windLevel = (random.nextInt(5) + 1) + "级";
//
//
//        return WeatherCard.builder()
//                .city(city)
//                .condition(condition)
//                .temperature(temperature)
//                .humidity(humidity)
//                .aqi(aqi)
//                .windDirection(windDirection)
//                .windLevel(windLevel)
//                .dressAdvice("")
//                .tips("")
//                .build();
//    }

    /**
     * 创建知识问答题目 —— 根据指定主题从题库中检索并返回结构化的选择题数据。
     *
     * <p>本方法通过 {@code @Tool} 注解暴露给大模型，大模型根据用户意图决定是否调用。
     * 返回的 {@link QuizCard} 包含完整的题目信息（题干、选项、正确答案、解析、难度），
     * 前端可据此渲染交互式答题组件。</p>
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>初始化题库（基于主题关键词索引）</li>
     *   <li>将传入的 topic 转小写后匹配题库，未命中则回退到 "default" 题目</li>
     *   <li>将内部 {@link QuizData} 转换为对外的 {@link QuizCard} DTO 返回</li>
     * </ol>
     *
     * @param topic 问题主题关键词（如 "spring"、"ai"、"地理"），由大模型从用户输入中提取
     * @return 结构化的问答题卡片，包含题目、选项、答案及解析
     */
    @Tool(description = "创建知识问答题目，支持多个主题领域，返回问题和候选项")
    @ToolResponseType("quiz")  // 声明返回类型为 quiz，手动模式下可通过反射读取
    public QuizCard createQuiz(@ToolParam(description = "问题主题，如 spring、ai、地理等") String topic) {
        log.info("[inner-tool] 创建知识问答：{}", topic);

        // 初始化题库：key 为主题关键词，value 为题目数据
        // TODO: 实际场景中应该根据主题调用 AI 动态生成题目或查询数据库
        Map<String, QuizData> quizBank = initQuizBank();

        // 主题匹配：转小写后查找，未命中则使用默认题目（容错设计）
        QuizData quizData = quizBank.getOrDefault(topic.toLowerCase(), quizBank.get("default"));

        // 将内部数据结构转换为对外 DTO，保持接口契约稳定
        return QuizCard.builder()
                .question(quizData.question)
                .description(quizData.description)
                .options(quizData.options)
                .correctAnswer(quizData.correctAnswer)
                .explanation(quizData.explanation)
                .difficulty(quizData.difficulty)
                .build();
    }

    /**
     * 初始化题库 —— 构建基于主题关键词索引的预设题目集合。
     *
     * <p>当前支持三个主题：</p>
     * <ul>
     *   <li>{@code default} - 基础地理题（中国首都）</li>
     *   <li>{@code spring} - Spring AI 框架知识</li>
     *   <li>{@code ai} - AI 基础概念</li>
     * </ul>
     *
     * @return 主题 → 题目数据 的映射表
     */
    private Map<String, QuizData> initQuizBank() {
        Map<String, QuizData> quizBank = new HashMap<>();

        // 默认题目
        quizBank.put("default", QuizData.builder()
                .question("中国的首都是哪里？")
                .description("这是一道基础地理题")
                .options(Arrays.asList(
                        QuizCard.Option.builder().key("A").value("上海").build(),
                        QuizCard.Option.builder().key("B").value("北京").build(),
                        QuizCard.Option.builder().key("C").value("广州").build(),
                        QuizCard.Option.builder().key("D").value("深圳").build()
                ))
                .correctAnswer("B")
                .explanation("北京是中国的首都，位于华北平原北部。")
                .difficulty(QuizCard.Difficulty.EASY)
                .build());

        // Spring 相关题目
        quizBank.put("spring", QuizData.builder()
                .question("Spring AI 中，用于构建流式响应的核心接口是？")
                .description("考察 Spring AI 基础知识")
                .options(Arrays.asList(
                        QuizCard.Option.builder().key("A").value("ChatClient").build(),
                        QuizCard.Option.builder().key("B").value("Flux").build(),
                        QuizCard.Option.builder().key("C").value("StreamBuilder").build(),
                        QuizCard.Option.builder().key("D").value("ResponseEmitter").build()
                ))
                .correctAnswer("A")
                .explanation("ChatClient 是 Spring AI 的核心接口，通过.stream() 方法可以构建流式响应。")
                .difficulty(QuizCard.Difficulty.MEDIUM)
                .build());

        // AI 相关题目
        quizBank.put("ai", QuizData.builder()
                .question("大语言模型（LLM）中的'LLM'代表什么？")
                .description("考察 AI 基础概念")
                .options(Arrays.asList(
                        QuizCard.Option.builder().key("A").value("Large Learning Machine").build(),
                        QuizCard.Option.builder().key("B").value("Language Learning Model").build(),
                        QuizCard.Option.builder().key("C").value("Large Language Model").build(),
                        QuizCard.Option.builder().key("D").value("Logic Language Model").build()
                ))
                .correctAnswer("C")
                .explanation("LLM = Large Language Model，即大语言模型，是基于海量文本数据训练的深度学习模型。")
                .difficulty(QuizCard.Difficulty.EASY)
                .build());

        return quizBank;
    }

    /**
     * 题库内部数据模型 —— 用于在题库 Map 中存储单道题目的完整信息。
     *
     * <p>与对外的 {@link QuizCard} DTO 不同，本类仅作为内部数据载体，
     * 通过 Lombok 的 {@code @Data} + {@code @Builder} 简化样板代码。</p>
     */
    @lombok.Data
    @lombok.Builder
    static class QuizData {
        /** 题干文本 */
        String question;
        /** 题目描述/补充说明 */
        String description;
        /** 候选项列表（A/B/C/D） */
        List<QuizCard.Option> options;
        /** 正确答案对应的选项 key（如 "B"） */
        String correctAnswer;
        /** 答案解析 */
        String explanation;
        /** 难度等级（EASY / MEDIUM / HARD） */
        QuizCard.Difficulty difficulty;
    }
}
