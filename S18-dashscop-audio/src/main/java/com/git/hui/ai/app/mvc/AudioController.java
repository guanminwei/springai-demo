package com.git.hui.ai.app.mvc;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileOutputStream;

/**
 * 音频合成控制器 —— 基于阿里云百炼（DashScope）多模态大模型实现文本转语音（TTS）功能。
 *
 * <p>本控制器通过调用阿里云 DashScope 平台的 {@code qwen3-tts-flash} 系列语音合成模型，
 * 将用户输入的文本转换为自然语音音频文件，并将音频文件的下载 URL 返回给前端调用方。</p>
 *
 * <h3>核心流程：</h3>
 * <ol>
 *     <li>接收前端传入的文本参数 {@code text}</li>
 *     <li>构建 DashScope 多模态会话请求参数（包含模型名称、音色、语种等）</li>
 *     <li>调用 DashScope SDK 发起语音合成请求，获取音频文件的临时 URL</li>
 *     <li>使用 OkHttp 将音频文件下载到本地工作目录（{@code downloaded_audio.wav}）</li>
 *     <li>返回音频 URL 供调用方直接使用</li>
 * </ol>
 *
 * <h3>配置项说明：</h3>
 * <ul>
 *     <li>{@code spring.tts.api-key}  —— 百炼平台 API Key（必填，不同地域 Key 不同）</li>
 *     <li>{@code spring.tts.model}    —— TTS 模型名称，默认 {@code qwen3-tts-flash-2025-11-27}</li>
 *     <li>{@code spring.tts.url}      —— DashScope API 基础地址，默认 {@code https://dashscope.aliyuncs.com/api/v1}</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>
 *   GET /audio?text=你好，欢迎使用阿里云语音合成服务
 * </pre>
 *
 * @author YiHui
 * @date 2025/12/13
 * @see com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation
 * @see AudioParameters.Voice
 */
@Slf4j
@RestController
public class AudioController {

    /**
     * 百炼平台 API Key。
     * <p>新加坡和北京地域的 API Key 不同，需通过配置文件指定。
     * 获取方式：<a href="https://help.aliyun.com/zh/model-studio/get-api-key">阿里云文档</a></p>
     */
    @Value("${spring.tts.api-key}")
    private static String apiKey;

    /**
     * TTS 模型名称。
     * <p>默认使用 {@code qwen3-tts-flash-2025-11-27}，可在配置文件中覆盖。
     * 不同模型在音色质量、响应速度和计费标准上有所差异。</p>
     */
    @Value("${spring.tts.model:qwen3-tts-flash-2025-11-27}")
    private static String ttsModel;

    /**
     * DashScope API 基础地址。
     * <p>默认指向阿里云北京地域 {@code https://dashscope.aliyuncs.com/api/v1}，
     * 若使用新加坡地域或其他自定义部署，可通过配置文件修改。</p>
     */
    @Value("${spring.tts.url:https://dashscope.aliyuncs.com/api/v1}")
    private String url;

    /**
     * 核心语音合成方法 —— 将文本转换为语音并下载到本地。
     *
     * <p>处理步骤：</p>
     * <ol>
     *     <li>使用 DashScope SDK 构建多模态会话参数，指定模型、音色（CHERRY）和语种</li>
     *     <li>调用 {@link MultiModalConversation#call(MultiModalConversationParam)} 发起同步请求</li>
     *     <li>从返回结果中提取音频文件的临时下载 URL</li>
     *     <li>通过 OkHttp 下载音频二进制数据并写入本地文件 {@code downloaded_audio.wav}</li>
     * </ol>
     *
     * @param text 待合成的文本内容，建议与 {@code languageType} 指定的语种保持一致，
     *             以获得正确的发音和自然的语调
     * @return 音频文件的临时下载 URL（由百炼平台生成，有时效性）
     * @throws Exception 当 DashScope 调用失败或音频下载/写入失败时抛出异常
     */
    public String call(String text) throws Exception {
        // 创建多模态会话实例，用于与 DashScope 平台交互
        MultiModalConversation conv = new MultiModalConversation();

        // 构建语音合成请求参数
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                // 百炼 API Key，从配置文件中读取（不同地域 Key 不同）
                // 若未配置环境变量，可直接替换为：.apiKey("sk-xxx")
                .apiKey(apiKey)
                // 指定 TTS 模型，如 qwen3-tts-flash 系列
                .model(ttsModel)
                // 设置待合成的文本内容
                .text(text)
                // 选择预置音色：CHERRY（女声，温柔风格）
                // 其他可选音色参见 AudioParameters.Voice 枚举定义
                .voice(AudioParameters.Voice.CHERRY)
                // 设置语种为中文，建议与输入文本语种一致以保证发音准确
                .languageType("Chinese")
                .build();

        // 发起同步语音合成请求，等待百炼平台返回结果
        MultiModalConversationResult result = conv.call(param);

        // 从返回结果中提取音频文件的临时下载 URL
        String audioUrl = result.getOutput().getAudio().getUrl();
        log.info("百炼返回结果是：{}", result);

        // 使用 OkHttp 将音频文件下载到本地工作目录
        byte[] audioData = downloadAudioFromUrl(audioUrl);

        // 将音频二进制数据写入本地 WAV 文件（覆盖已有文件）
        try (FileOutputStream out = new FileOutputStream("downloaded_audio.wav")) {
            out.write(audioData);
        } catch (Exception e) {
            log.error("\n下载音频文件时出错: " + e.getMessage());
        }

        return audioUrl;
    }

    /**
     * 从指定 URL 下载音频文件的二进制数据。
     *
     * <p>使用 OkHttp 同步发起 GET 请求，将响应体完整读入内存。
     * 适用于音频文件体积较小的场景；若音频较大，建议改为流式写入磁盘。</p>
     *
     * @param audioUrl 音频文件的 HTTP(S) 下载地址（由百炼平台临时生成）
     * @return 音频文件的完整二进制数据
     * @throws Exception 当网络请求失败、响应码非 2xx 或响应体为空时抛出异常
     */
    private byte[] downloadAudioFromUrl(String audioUrl) throws Exception {
        // 创建 OkHttp 客户端实例（默认配置，无自定义超时/连接池参数）
        OkHttpClient client = new OkHttpClient();

        // 构建 HTTP GET 请求
        Request request = new Request.Builder()
                .url(audioUrl)
                .build();

        // 同步执行请求，try-with-resources 确保 Response 被正确关闭
        try (Response response = client.newCall(request).execute()) {
            // 校验 HTTP 响应码，非 2xx 视为失败
            if (!response.isSuccessful()) {
                throw new RuntimeException("下载音频文件失败: " + response.code());
            }

            ResponseBody responseBody = response.body();
            // 防御性检查：确保响应体不为空
            if (responseBody == null) {
                throw new RuntimeException("音频文件内容为空");
            }

            // 将整个响应体读入字节数组并返回
            return responseBody.bytes();
        }
    }

    /**
     * 文本转语音 HTTP 接口。
     *
     * <p>接收 GET 请求，将查询参数 {@code text} 传递给语音合成核心方法，
     * 返回音频文件的临时下载 URL。</p>
     *
     * <p>请求示例：</p>
     * <pre>
     *   GET /audio?text=你好世界
     * </pre>
     *
     * @param text 待合成的文本内容（URL 编码），作为查询参数传入
     * @return 音频文件的临时下载 URL 字符串
     * @throws Exception 当语音合成或音频下载过程中发生错误时抛出异常
     */
    @GetMapping("/audio")
    public String toAudio(String text) throws Exception {
        return call(text);
    }
}
