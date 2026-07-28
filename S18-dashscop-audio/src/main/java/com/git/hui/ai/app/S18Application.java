package com.git.hui.ai.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * S18 示例模块启动类 —— 演示基于阿里云百炼（DashScope）的语音合成（TTS）功能。
 *
 * <p>本模块通过 DashScope SDK 调用 {@code qwen3-tts-flash} 系列多模态语音合成模型，
 * 将文本转换为自然语音音频文件。对外提供 REST 接口 {@code GET /audio?text=...}，
 * 返回音频文件的临时下载 URL。</p>
 *
 * <p>启动前请在 {@code .env} 或 {@code application.yml} 中配置以下必要参数：</p>
 * <ul>
 *     <li>{@code spring.tts.api-key} —— 百炼平台 API Key</li>
 *     <li>{@code spring.tts.model}   —— TTS 模型名称（可选，有默认值）</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/11
 * @see com.git.hui.ai.app.mvc.AudioController
 */
@SpringBootApplication
public class S18Application {

    /**
     * Spring Boot 应用入口方法。
     *
     * @param args 命令行参数，可覆盖配置文件中的部分设置
     */
    public static void main(String[] args) {
        SpringApplication.run(S18Application.class, args);
    }
}