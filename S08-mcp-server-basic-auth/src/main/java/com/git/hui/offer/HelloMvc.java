package com.git.hui.offer;

import com.git.hui.offer.service.DateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;

/**
 * 普通 MVC 控制器 —— 用于验证鉴权过滤器不影响非 MCP 端点的正常访问
 * <p>
 * 本控制器暴露了一个简单的 HTTP 接口 {@code GET /showTime}，
 * 接收时区参数并返回该时区的当前时间。
 * <p>
 * 设计目的：
 * <ul>
 *   <li>验证 {@link ReqFilter} 仅对 MCP 端点（/sse、/mcp/messages）进行鉴权，
 *       普通 HTTP 接口无需认证即可访问</li>
 *   <li>复用 {@link DateService} 的时区转换能力，提供轻量级测试接口</li>
 * </ul>
 *
 * @author YiHui
 * @date 2025/7/28
 * @see DateService 提供时区时间获取能力
 */
@RestController
public class HelloMvc {

    /**
     * 时区时间服务，通过 Spring 依赖注入获取
     */
    @Autowired
    private DateService dateService;

    /**
     * 根据指定时区返回当前时间
     * <p>
     * 请求示例：{@code GET /showTime?area=Asia/Tokyo}
     * 返回示例：{@code 2025-07-28 15:30:00}
     *
     * @param area 时区ID，需为标准格式，如 Asia/Shanghai、America/New_York
     * @return 指定时区的当前时间字符串，格式为 yyyy-MM-dd HH:mm:ss
     */
    @GetMapping("showTime")
    public String showTime(String area) {
        return dateService.getTimeByZoneId(ZoneId.of(area));
    }
}
