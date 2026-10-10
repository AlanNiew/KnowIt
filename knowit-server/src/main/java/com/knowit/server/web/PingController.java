package com.knowit.server.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.knowit.common.ApiResponse;
import com.knowit.server.KnowItApplication;

/** 存活探测。显式暴露一个接口，而不是只依赖 Actuator—— 运维配探针时容易只看 {@code /ping}，而 Actuator 默认端口/路径经常被防火墙挡住。 */
@RestController
public class PingController {

    /** 返回服务身份与运行时长，方便现场排查"这个端口后面到底是哪个服务"。 */
    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "knowit");
        body.put("status", "UP");
        body.put("time", Instant.now().toString());
        return ApiResponse.ok(body);
    }

    @GetMapping("/version")
    public ApiResponse<Map<String, Object>> version() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "knowit");
        body.put("implVersion", KnowItApplication.class.getPackage().getImplementationVersion());
        return ApiResponse.ok(body);
    }
}
