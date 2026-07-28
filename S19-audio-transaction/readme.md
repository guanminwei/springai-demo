# S19-audio-transaction - 语音转文字（Audio Transcription）

## 模块简介

本模块演示如何使用 **Spring AI** 的 `TranscriptionModel` 接口，通过兼容 OpenAI 风格的 API 将音频文件转换为文本。

核心功能：
- 使用 Spring Boot 自动配置的 `TranscriptionModel` 进行语音识别
- 手动创建 `OpenAiAudioTranscriptionModel` 实例进行语音识别
- 支持多种语音识别模型（TeleAI/TeleSpeechASR、FunAudioLLM/SenseVoiceSmall）
- 支持纯文本和 JSON 两种响应格式

## 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot 3.x | 应用框架 |
| Spring AI | AI 模型集成框架 |
| OpenAI 兼容 API | SiliconFlow 平台提供的兼容接口 |
| JDK HttpClient | 底层 HTTP 客户端，支持 multipart 音频上传 |

## 支持的语音识别模型

| 模型名称 | 提供方 | 说明 |
|----------|--------|------|
| `TeleAI/TeleSpeechASR` | TeleAI | 语音识别模型，支持多种语言 |
| `FunAudioLLM/SenseVoiceSmall` | FunAudioLLM | 小型语音识别模型，支持多语言，识别准确率高 |

## 项目结构

```
S19-audio-transaction/
├── src/main/
│   ├── java/com/git/hui/springai/
│   │   ├── S19Application.java              # Spring Boot 启动类
│   │   └── AudioTransactionController.java  # 语音转文字控制器
│   └── resources/
│       ├── application.yml                  # 应用配置文件
│       └── test.mp3                         # 测试音频文件
└── pom.xml
```

## 核心类说明

### S19Application

Spring Boot 启动类，主要职责：
- 启动 Spring Boot 应用
- 配置 `RestClient.Builder` Bean，确保底层 HTTP 客户端支持 multipart 音频上传

**为什么需要自定义 RestClient.Builder？**

Spring AI 的语音转写 API 需要将音频文件以 `multipart/form-data` 格式上传。默认的 RestClient 可能使用不支持 multipart 的 HTTP 客户端实现，导致音频上传失败。通过显式配置 `JdkClientHttpRequestFactory` 和相关的 `HttpMessageConverter`，可以确保使用 JDK 内置的 HttpClient，它完整支持 multipart 格式。

### AudioTransactionController

语音转文字控制器，提供两个接口：

| 接口路径 | 说明 | 模型 | 响应格式 |
|----------|------|------|----------|
| `/translateAudio` | 使用自动配置的默认模型 | TeleAI/TeleSpeechASR | TEXT（纯文本） |
| `/translateAudioV2` | 使用手动创建的模型实例 | FunAudioLLM/SenseVoiceSmall | JSON |

**两种调用方式对比：**

| 方式 | 优点 | 缺点 |
|------|------|------|
| 自动配置（方式一） | 配置简单，开箱即用 | 灵活性较低，配置受限于配置文件 |
| 手动创建（方式二） | 灵活性高，可动态配置 | 需要手动管理实例和配置 |

## 配置说明

### application.yml

```yaml
spring:
  ai:
    openai:
      # API 密钥（支持环境变量注入）
      api-key: ${SILICON_API_KEY:${AI_API_KEY:}}
      # 语音转写配置
      transcription:
        api-key: ${SILICON_API_KEY:${AI_API_KEY:}}
        base-url: https://api.siliconflow.cn/v1
        options:
          model: FunAudioLLM/SenseVoiceSmall
          response-format: text
      # 聊天模型（可选）
      chat:
        options:
          model: deepseek-ai/DeepSeek-R1-0528-Qwen3-8B
      base-url: https://api.siliconflow.cn
```

### API Key 配置方式

支持三种配置方式（按优先级）：
1. Spring 配置属性（application.yml 或 `--spring.ai.openai.api-key=xxx`）
2. JVM 系统属性（`-Dsilicon-api-key=xxx`）
3. 操作系统环境变量（`SILICON_API_KEY` 或 `AI_API_KEY`）

## 快速开始

### 1. 配置 API Key

在环境变量中设置 SiliconFlow 的 API Key：

```bash
# Windows PowerShell
$env:SILICON_API_KEY="your-api-key-here"

# Linux/Mac
export SILICON_API_KEY=your-api-key-here
```

或者通过启动参数：

```bash
java -Dspring.ai.openai.api-key=your-api-key-here -jar target/S19-audio-transaction-1.0-SNAPSHOT.jar
```

### 2. 启动应用

```bash
cd S19-audio-transaction
mvn spring-boot:run
```

### 3. 测试接口

**方式一：使用自动配置的模型**

```bash
curl http://localhost:8080/translateAudio
```

**方式二：使用手动创建的模型**

```bash
curl http://localhost:8080/translateAudioV2
```

### 4. 浏览器访问

也可以直接在浏览器中访问：
- http://localhost:8080/translateAudio
- http://localhost:8080/translateAudioV2

## 关键代码片段

### 自动配置方式（方式一）

```java
@Autowired
private TranscriptionModel transcriptionModel;

@GetMapping(path = "translateAudio")
public Object translateAudio() throws IOException {
    AudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
            .model("TeleAI/TeleSpeechASR")
            .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
            .build();

    AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);
    AudioTranscriptionResponse response = transcriptionModel.call(prompt);
    
    return response.getResult().getOutput();
}
```

### 手动创建方式（方式二）

```java
// 构造函数中创建模型实例
OpenAiAudioApi openAiAudioApi = OpenAiAudioApi.builder()
        .apiKey(getApiKey(environment, "silicon-api-key"))
        .baseUrl("https://api.siliconflow.cn")
        .build();

initTranscriptionModel = new OpenAiAudioTranscriptionModel(openAiAudioApi,
        OpenAiAudioTranscriptionOptions.builder()
                .model("TeleAI/TeleSpeechASR")
                .build());

// 接口中使用
@GetMapping(path = "translateAudioV2")
public Object translateAudioV2() throws IOException {
    AudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
            .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.JSON)
            .model("FunAudioLLM/SenseVoiceSmall")
            .language("zh")
            .build();

    AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);
    AudioTranscriptionResponse response = initTranscriptionModel.call(prompt);
    
    return response.getResult().getOutput();
}
```

## 常见问题

### 1. 音频上传失败

**原因：** 底层 HTTP 客户端不支持 multipart 格式

**解决方案：** 确保在 `S19Application` 中配置了 `RestClient.Builder` Bean，使用 `JdkClientHttpRequestFactory`：

```java
@Bean
public RestClient.Builder restClientBuilder() {
    return RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory())
            .messageConverters(converters -> {
                converters.add(new FormHttpMessageConverter());
                converters.add(new ResourceHttpMessageConverter());
                converters.add(new StringHttpMessageConverter());
            });
}
```

### 2. API Key 未找到

**原因：** API Key 未正确配置

**解决方案：** 
- 检查环境变量 `SILICON_API_KEY` 或 `AI_API_KEY` 是否设置
- 或通过启动参数 `-Dspring.ai.openai.api-key=xxx` 传入
- 或在 `application.yml` 中直接配置（不推荐，存在安全风险）

### 3. 识别准确率低

**建议：**
- 使用 `FunAudioLLM/SenseVoiceSmall` 模型，识别准确率更高
- 显式指定语言参数（如 `.language("zh")` 表示中文）
- 确保音频质量清晰，背景噪音少

## 参考资料

- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [SiliconFlow API 文档](https://docs.siliconflow.cn/)
- [OpenAI Audio API](https://platform.openai.com/docs/guides/speech-to-text)

## 作者

- **YiHui**
- 日期：2026/2/25
