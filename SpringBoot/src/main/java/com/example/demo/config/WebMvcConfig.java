package com.example.demo.config;

import com.example.demo.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * Spring MVC 配置。
 * 注册 JWT 认证拦截器，拦截除白名单外的所有请求。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")                          // 拦截所有路径
                .excludePathPatterns(                            // 白名单：无需认证的路径
                        "/user/login",
                        "/user/register",
                        "/dashboard",
                        "/error"                                 // Spring Boot 默认错误页
                );
    }
}
