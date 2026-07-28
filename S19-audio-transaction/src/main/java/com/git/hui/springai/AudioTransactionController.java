package com.git.hui.springai;

import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * 语音转文字（Audio Transcription）控制器
 * <p>
 * 本控制器演示了如何使用 Spring AI 的 {@link TranscriptionModel} 接口，
 * 通过兼容 OpenAI 风格的 API 将音频文件转换为文本。
 * 支持两种调用方式：
 * <ul>
 *     <li>方式一（translateAudio）：使用 Spring Boot 自动配置注入的默认 {@link TranscriptionModel}，
 *         其 API Key、Base URL 等参数来自 application.yml 配置。</li>
 *     <li>方式二（translateAudioV2）：在构造函数中手动创建 {@link OpenAiAudioTranscriptionModel} 实例，
 *         通过 {@link OpenAiAudioApi} 直接指定 API 地址和密钥，适合需要更细粒度控制的场景。</li>
 * </ul>
 * <p>
 * 支持的语音识别模型包括：
 * <ul>
 *     <li>{@code TeleAI/TeleSpeechASR} — 由 TeleAI 提供的语音识别模型</li>
 *     <li>{@code FunAudioLLM/SenseVoiceSmall} — 由 FunAudioLLM 提供的小型语音识别模型，支持多语言</li>
 * </ul>
 *
 * @author YiHui
 * @date 2026/2/25
 * @see TranscriptionModel
 * @see OpenAiAudioTranscriptionModel
 */
@RestController
public class AudioTransactionController {

    private final Logger log = org.slf4j.LoggerFactory.getLogger(AudioTransactionController.class);

    /**
     * 手动创建的语音转写模型实例
     * <p>
     * 在构造函数中通过 {@link OpenAiAudioApi} 和 {@link OpenAiAudioTranscriptionOptions} 初始化，
     * 使用 SiliconFlow 平台的 TeleAI/TeleSpeechASR 模型。
     * 用于 {@link #translateAudioV2()} 接口。
     */
    private final TranscriptionModel initTranscriptionModel;

    /**
     * Spring Boot 自动配置注入的默认 {@link TranscriptionModel}
     * <p>
     * 其配置（api-key、base-url、model 等）来源于 application.yml 中
     * {@code spring.ai.openai.transcription} 节点。
     * 用于 {@link #translateAudio()} 接口。
     */
    @Autowired
    private TranscriptionModel transcriptionModel;

        /**
     * 待识别的音频资源文件
     * <p>
     * 从 classpath 下加载 test.mp3 文件作为语音识别的输入。
     */
    @Value("classpath:/test.mp3")
    private Resource resource;


    private final OpenAiAudioApi openAiAudioApi;

    private String apiKey;

    /**
     * 构造函数：手动创建语音转写模型
     * <p>
     * 通过 {@link Environment} 获取 API Key（支持多种配置方式），
     * 然后构建 {@link OpenAiAudioApi} 客户端，指向 SiliconFlow 平台，
     * 最终创建 {@link OpenAiAudioTranscriptionModel} 实例，默认使用 TeleAI/TeleSpeechASR 模型。
     *
     * @param environment Spring 环境对象，用于读取配置属性
     */
    public AudioTransactionController(Environment environment) {
        // 优先从环境变量获取，回退到 Spring 配置属性（application.yml 中已配置级联占位符）
        apiKey = getApiKey(environment, "SILICON_API_KEY");
        if (StringUtils.isBlank(apiKey)) {
            apiKey = environment.getProperty("spring.ai.openai.transcription.api-key");
        }
       
        if (StringUtils.isBlank(apiKey)) {
            // API Key 缺失时不阻塞启动，调用 translateAudioV2 时会给出明确提示
            log.warn("SILICON_API_KEY 未配置，translateAudioV2 接口不可用。请通过 .env 环境变量或 -DSILICON_API_KEY=xxx 注入");
            initTranscriptionModel = null;
            openAiAudioApi = null;
        } else {
            // 构建 OpenAiAudioApi 客户端，指向 SiliconFlow 兼容接口
            openAiAudioApi = OpenAiAudioApi.builder()
                    .apiKey(apiKey)
                    .baseUrl("https://api.siliconflow.cn")
                    .build();

            // 使用 TeleAI/TeleSpeechASR 模型创建语音转写模型实例
            initTranscriptionModel = new OpenAiAudioTranscriptionModel(openAiAudioApi,
                    OpenAiAudioTranscriptionOptions.builder()
                            .model("TeleAI/TeleSpeechASR")
                            .build());
        }
    }

    /**
     * 从多个来源获取 API Key
     * <p>
     * 按照以下优先级依次查找，返回第一个非空值：
     * <ol>
     *     <li>Spring 配置属性（application.yml 或 --启动参数）</li>
     *     <li>JVM 系统属性（-D 参数）</li>
     *     <li>操作系统环境变量</li>
     * </ol>
     *
     * @param environment Spring 环境对象
     * @param key         配置键名
     * @return API Key 字符串，若均未找到则返回 null
     */
    private String getApiKey(Environment environment, String key) {
        // 1. 通过 application.yml 或 --启动参数 获取
        String val = environment.getProperty(key);
        if (StringUtils.isBlank(val)) {
            // 2. 通过 JVM 参数 -Dsilicon-api-key=xxx 获取
            val = System.getProperty(key);
            if (val == null) {
                // 3. 通过操作系统环境变量获取
                val = System.getenv(key);
            }
        }
        return val;
    }



    /**
     * 语音转文字接口（方式一：使用自动配置的默认模型）
     * <p>
     * 使用 Spring Boot 自动注入的 {@link TranscriptionModel}，
     * 通过构建 {@link AudioTranscriptionPrompt} 发送语音识别请求。
     * <ul>
     *     <li>模型：TeleAI/TeleSpeechASR</li>
     *     <li>响应格式：纯文本（TEXT）</li>
     * </ul>
     *
     * @return 识别后的文本内容
     * @throws IOException 音频资源读取异常
     */
    @GetMapping(path = "translateAudio")
    @ResponseBody
    public Object translateAudio() throws IOException {
        // 构建语音转写选项：指定模型和响应格式为纯文本
        // AudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
        //         .model("TeleAI/TeleSpeechASR")
        //         .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
        //         .build();

        AudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
            .model("FunAudioLLM/SenseVoiceSmall")    // 换成已验证的模型
            .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.JSON)  // 使用 JSON 格式
            .language("zh")                           // 指定中文
            .build();

        // 创建语音转写请求：传入音频资源和选项
        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);

        // 调用模型进行语音识别
        AudioTranscriptionResponse response = transcriptionModel.call(prompt);
        log.info("translateAudio response -> {}", response.getResult());

        // 返回识别结果文本
        return response.getResult().getOutput();
    }

    /**
     * 语音转文字接口（方式二：使用手动创建的模型实例）
     * <p>
     * 使用构造函数中手动初始化的 {@link #initTranscriptionModel}，
     * 演示了如何指定不同的模型和参数。
     * <ul>
     *     <li>模型：FunAudioLLM/SenseVoiceSmall（支持多语言的小型语音识别模型）</li>
     *     <li>响应格式：JSON</li>
     *     <li>语言：zh（中文），显式指定语言可提高识别准确率</li>
     * </ul>
     *
     * @return 识别后的文本内容
     * @throws IOException 音频资源读取异常
     */
    @GetMapping(path = "translateAudioV2")
    @ResponseBody
    public Object translateAudioV2() throws IOException {
        if (initTranscriptionModel == null) {
            return "SILICON_API_KEY 未配置，无法调用语音转写接口。请设置环境变量后重启应用";
        }
        // 构建语音转写选项：使用 SenseVoiceSmall 模型，JSON 格式返回，指定中文
        AudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                .model("FunAudioLLM/SenseVoiceSmall")
                // .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.JSON)
                .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                // .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.VERBOSE_JSON)
                
                // .language("zh")
                 .language("")   // 空字符串，避免 null
                .prompt("")     // 空字符串
                .temperature(0f) // 或者不设置，因为日志里是 null，但设为 0 可能也会被发送
                .build();

        // 创建语音转写请求
        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);

        // 调用手动创建的模型实例进行语音识别
        AudioTranscriptionResponse response = initTranscriptionModel.call(prompt);
        log.info("translateAudioV2 response -> {}", response.getResult());

        // 返回识别结果文本
        return response.getResult().getOutput();
    }

    /**
     * 语音转文字接口（方式三：直接使用 OpenAiAudioApi 底层 API）
     * <p>
     * 绕过 {@link TranscriptionModel} 抽象层，直接构建 {@link OpenAiAudioApi.TranscriptionRequest}
     * 并调用 {@link OpenAiAudioApi#createTranscription}，适合需要精确控制 HTTP 请求参数的场景。
     *
     * @return 识别后的文本内容
     * @throws IOException 音频资源读取异常
     */
    @GetMapping(path = "translateAudioV3")
    @ResponseBody
    public Object translateAudioV3() throws IOException {
        if (openAiAudioApi == null) {
            return "SILICON_API_KEY 未配置，无法调用语音转写接口。请设置环境变量后重启应用";
        }
        // 使用 Builder 构造请求，file() 接受 byte[]
        OpenAiAudioApi.TranscriptionRequest request = OpenAiAudioApi.TranscriptionRequest.builder()
                .file(resource.getInputStream().readAllBytes())   // Resource -> byte[]
                .fileName(resource.getFilename())                 // 文件名
                .model("FunAudioLLM/SenseVoiceSmall")
                .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                .build();

        // TEXT 格式返回 String；使用泛型重载指定返回类型
        ResponseEntity<String> response =
                openAiAudioApi.createTranscription(request, String.class);
        log.info("translateAudioV3 response -> {}", response.getBody());
        return response.getBody();
    }


    @GetMapping(path = "translateAudioV4")
    @ResponseBody
    public Object translateAudioV4() throws IOException {
        Resource resource1 = new FileSystemResource("d:/hzzh/project/AI/spring-ai-demo-master/spring-ai-demo-master/S19-audio-transaction/src/main/resources/test.mp3");
        byte[] audioBytes = resource1.getInputStream().readAllBytes();
        log.info("Audio size: {} bytes", audioBytes.length);  // 确认非空
        log.info("Resource path: {}", resource1.getURL());

        // 使用 MultipartBodyBuilder 构建请求体
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "test.mp3";
            }
        }).contentType(MediaType.valueOf("audio/mpeg"));   // 或者 APPLICATION_OCTET_STREAM

        builder.part("model", "FunAudioLLM/SenseVoiceSmall");
        builder.part("response_format", "text");

        MultiValueMap<String, HttpEntity<?>> body = builder.build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        // 不要手动设置 Content-Type，RestTemplate 会自动加上 boundary

        HttpEntity<MultiValueMap<String, HttpEntity<?>>> requestEntity = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://api.siliconflow.cn/v1/audio/transcriptions",
                requestEntity,
                String.class
        );
        return response.getBody();
    }

}
