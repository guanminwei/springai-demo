package com.git.hui.springai.app.react.simple;

import com.git.hui.springai.app.react.service.LlmService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 同步版 ReAct Agent 演示运行器
 * <p>
 * 本类实现 {@link CommandLineRunner}，在应用启动时自动运行 ReAct Agent 示例。
 * 通过三个递进的示例展示 ReAct Agent 的能力：
 * <ul>
 *     <li>示例 1：简单加法 - 单次工具调用</li>
 *     <li>示例 2：多步计算 + 条件天气查询 - 多轮工具调用 + 条件判断</li>
 *     <li>示例 3：复杂应用题 - 连续多步计算</li>
 * </ul>
 * <p>
 * 运行流程：
 * <ol>
 *     <li>准备 CalculatorTools 工具集</li>
 *     <li>创建 SimpleReActAgent 实例（注入 ChatClient 和工具）</li>
 *     <li>依次运行示例问题，打印每轮的 ReAct 循环日志和最终结果</li>
 * </ol>
 *
 * @see SimpleReActAgent
 * @see CalculatorTools
 */
@Component
public class SimpleReActRunner implements CommandLineRunner {

    private final LlmService llmService;
    private final ChatClient chatClient;

    /**
     * 构造方法 - 通过 LlmService 获取默认模型的 ChatClient
     *
     * @param llmService LLM 服务工厂
     */
    public SimpleReActRunner(LlmService llmService) {
        this.llmService = llmService;
        this.chatClient = llmService.getChatClient(null);
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. 准备工具
        CalculatorTools calculatorTools = new CalculatorTools();
        List<ToolCallback> tools = calculatorTools.getTools();

        // 2. 创建 ReAct Agent
        SimpleReActAgent agent = new SimpleReActAgent(chatClient, tools);

        // 3. 运行示例
        System.out.println("\n========== 示例 1: 简单加法 ==========");
        String answer1 = agent.run("计算 25 + 37 等于多少？");
        System.out.println("最终结果：" + answer1);

        System.out.println("\n========== 示例 2: 多步计算 ==========");
        String answer2 = agent.run("先计算 100 + 50，然后将结果乘以 2，最后除以 3；\n最终根据上面这个计算返回的结果，是奇数就查询武汉天气、是偶数则查询北京天气");
        System.out.println("最终结果：" + answer2);

        System.out.println("\n========== 示例 3: 应用场景 ==========");
        String answer3 = agent.run("小明有 50 元钱，买了 3 本书，每本书 12 元，花了 2.5 元买了一个包子，还剩多少钱？");
        System.out.println("最终结果：" + answer3);
    }
}
