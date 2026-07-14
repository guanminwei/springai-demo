package com.git.hui.springai.advisor;

import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 耗时统计 Advisor —— 用于记录 Spring AI ChatClient 调用大模型时的耗时。
 * <p>
 * 本类同时实现了 {@link CallAdvisor}（同步调用拦截）和 {@link StreamAdvisor}（流式调用拦截）两个接口，
 * 可以在 Advisor 链中对同步和流式两种调用方式进行耗时统计。
 * </p>
 *
 * <h3>核心原理</h3>
 * <ul>
 *   <li>在请求进入 Advisor 链之前记录起始时间戳，写入请求上下文 {@code context}；</li>
 *   <li>在 Advisor 链返回响应之后计算耗时，将结束时间戳与耗时写入响应上下文；</li>
 *   <li>对于流式调用，借助 {@link ChatClientMessageAggregator} 在所有 SSE 事件聚合完成后统一计算耗时。</li>
 * </ul>
 *
 * <h3>优先级</h3>
 * 通过 {@link #getOrder()} 返回 {@link Integer#MIN_VALUE}，确保本 Advisor 始终处于 Advisor 链的最外层，
 * 从而统计到包含其他 Advisor 处理时间在内的完整耗时。
 *
 * @author YiHui
 * @date 2025/8/4
 * @see CallAdvisor
 * @see StreamAdvisor
 */
public class CostAdvisor implements CallAdvisor, StreamAdvisor {
    /** 日志记录器，用于输出耗时统计信息 */
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CostAdvisor.class);

    /**
     * 同步调用拦截 —— 统计单次同步调用（{@code call()}）的耗时。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>记录当前时间戳作为起始时间，并存入请求上下文（key: {@code start-time}），
     *       便于下游 Advisor 或业务逻辑获取；</li>
     *   <li>调用 {@link CallAdvisorChain#nextCall} 将请求传递给 Advisor 链的下一环，
     *       最终到达大模型并获得同步响应；</li>
     *   <li>响应返回后，再次取当前时间戳作为结束时间，计算耗时（毫秒），
     *       将 {@code end-time} 和 {@code cost-time} 写入响应上下文；</li>
     *   <li>通过日志输出耗时信息，方便开发调试与监控。</li>
     * </ol>
     *
     * @param chatClientRequest  封装了用户提示词、系统提示、选项等信息的请求对象，
     *                           其 {@code context} 可在 Advisor 链中传递自定义数据
     * @param callAdvisorChain   Advisor 链的调用链，调用 {@code nextCall()} 将请求传递给下一个 Advisor
     * @return 大模型返回的 {@link ChatClientResponse}，其上下文中包含耗时统计信息
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        // 1. 记录同步调用的起始时间戳（毫秒）
        long start = System.currentTimeMillis();
        // 2. 将起始时间写入请求上下文，供下游组件读取
        chatClientRequest.context().put("start-time", start);

        // 3. 将请求传递给 Advisor 链的下一环，等待大模型返回同步响应
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);

        /**
         * CostAdvisor.adviseCall()
            │
            ├─ 第62行: start = currentTimeMillis()     ← 记录起始时间
            │
            ├─ 第67行: response = nextCall(request)    ← 进入下一层
            │   │
            │   ├─ LogAdvisor.adviseCall()
            │   │   │
            │   │   ├─ 前置逻辑（打印日志）
            │   │   │
            │   │   ├─ response = nextCall(request)    ← 再进入下一层
            │   │   │   │
            │   │   │   ├─ MemoryAdvisor.adviseCall()
            │   │   │   │   ├─ 前置逻辑（注入记忆）
            │   │   │   │   ├─ response = nextCall()   ← 到达大模型
            │   │   │   │   │   ┌──────────────────────┐
            │   │   │   │   │   │   ChatModel 调用      │ ← 真正调大模型 API
            │   │   │   │   │   │   (阻塞等待返回)      │
            │   │   │   │   │   └──────────────────────┘
            │   │   │   │   ├─ 后置逻辑（保存记忆）
            │   │   │   │   └─ return response          ← 返回给 LogAdvisor
            │   │   │   │
            │   │   │   └─ return response              ← 返回给 CostAdvisor
            │   │   │
            │   │   └─ 后置逻辑（打印响应日志）
            │   └─ return response                      ← 返回给 CostAdvisor
            │
            ├─ 第70行: end = currentTimeMillis()       ← 这一行能执行到，说明整条链都跑完了
            ├─ 第75行: log.info("cost: {} ms", cost)
            └─ return response
         */



        // 4. 计算耗时并将结果写入响应上下文
        long end = System.currentTimeMillis();
        long cost = end - start;
        response.context().put("end-time", end);
        response.context().put("cost-time", cost);
        // 5. 输出日志，便于监控和调试
        log.info("Prompt call cost: {} ms", cost);
        return response;
    }

    /**
     * 流式调用拦截 —— 统计流式调用（{@code stream()}）从发起请求到所有 SSE 事件聚合完成的总耗时。
     * <p>
     * 与同步调用不同，流式调用的响应是一个 {@link Flux} 响应流，
     * 不能简单地等待返回值来计算耗时。这里借助 {@link ChatClientMessageAggregator}
     * 对整个流式响应进行聚合（aggregate），在所有流式消息消费完毕后触发回调，
     * 在回调中统一计算并记录耗时。
     * </p>
     *
     * <h4>执行流程</h4>
     * <ol>
     *   <li>记录起始时间戳，写入请求上下文；</li>
     *   <li>调用 {@link StreamAdvisorChain#nextStream} 获取流式响应 {@link Flux}；</li>
     *   <li>使用 {@link ChatClientMessageAggregator#aggregateChatClientResponse}
     *       包装原始 Flux，在流完成时触发回调；</li>
     *   <li>回调中计算耗时，将 {@code end-time} 和 {@code cost-time} 写入聚合后的响应上下文。</li>
     * </ol>
     *
     * @param chatClientRequest   封装了用户提示词等信息的请求对象
     * @param streamAdvisorChain  Advisor 链的流式调用链
     * @return 包装了聚合回调的 {@link Flux} 流式响应
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        // 1. 记录流式调用的起始时间戳
        long start = System.currentTimeMillis();
        // 2. 将起始时间写入请求上下文
        chatClientRequest.context().put("start-time", start);

        // 3. 将请求传递给 Advisor 链的下一环，获取流式响应（SSE 事件流）
        Flux<ChatClientResponse> response = streamAdvisorChain.nextStream(chatClientRequest);

        // 4. 使用 ChatClientMessageAggregator 对流式响应进行聚合
        //    当所有 SSE 事件消费完毕后，触发回调函数计算总耗时
        return new ChatClientMessageAggregator().aggregateChatClientResponse(response, (res) -> {
            long end = System.currentTimeMillis();
            long cost = end - start;
            // 将结束时间和耗时写入聚合响应的上下文
            res.context().put("end-time", end);
            res.context().put("cost-time", cost);
            log.info("Prompt stream cost: {} ms", cost);
        });
    }

    /**
     * 返回当前 Advisor 的名称标识。
     * <p>
     * 该名称可用于日志输出、调试追踪或在 Advisor 链中识别特定的 Advisor 实例。
     * </p>
     *
     * @return Advisor 名称字符串 {@code "costAdvisor"}
     */
    @Override
    public String getName() {
        return "costAdvisor";
    }

    /**
     * 返回 Advisor 在链中的执行优先级顺序。
     * <p>
     * 返回 {@link Integer#MIN_VALUE} 表示最高优先级，确保本 Advisor 位于 Advisor 链的最外层。
     * 这样可以保证耗时统计覆盖整个 Advisor 链的处理过程，包括其他 Advisor 的前置/后置处理时间。
     * </p>
     * <p>
     * Spring 的 Ordered 约定：值越小优先级越高，{@code MIN_VALUE} 为最高优先级。
     * </p>
     *
     * @return {@link Integer#MIN_VALUE}，代表最高优先级
     */
    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
