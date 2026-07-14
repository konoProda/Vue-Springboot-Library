package com.example.demo.interceptor;

import com.example.demo.commom.Result;
import com.example.demo.entity.User;
import com.example.demo.utils.TokenUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT 认证拦截器。
 * 对所有非白名单请求验证 Token，解析出 userId 和 role 放入 request 属性中。
 *
 * 白名单路径（无需认证）:
 *   - /user/login
 *   - /user/register
 *
 * 同时放行 OPTIONS 预检请求以避免跨域问题。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    /** 请求属性键：当前登录用户的 ID */
    public static final String REQUEST_ATTR_USER_ID = "userId";

    /** 请求属性键：当前登录用户的角色 (1=admin, 2=reader) */
    public static final String REQUEST_ATTR_ROLE = "role";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // ========== 1. 放行 OPTIONS 预检请求（CORS） ==========
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // ========== 2. 放行白名单路径 ==========
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 无需登录的路径
        if (uri.startsWith("/user/login") || uri.startsWith("/user/register")) {
            return true;
        }
        // 展示板 — 无需登录
        if (uri.startsWith("/dashboard")) {
            return true;
        }
        // 图书查询 — 任何人都可以查
        if (uri.startsWith("/book") && "GET".equalsIgnoreCase(method)) {
            return true;
        }

        // ========== 3. 提取 Token（支持 Authorization 和 token 两种头） ==========
        String token = extractToken(request);
        if (token == null || token.isEmpty()) {
            sendUnauthorized(response, "未登录，请先登录");
            return false;
        }

        // ========== 4. 解析 Token 获取用户 ==========
        User user = TokenUtils.getUserFromToken(token);
        if (user == null) {
            sendUnauthorized(response, "Token无效或已过期，请重新登录");
            return false;
        }

        // ========== 5. 将用户信息放入 request 属性 ==========
        request.setAttribute(REQUEST_ATTR_USER_ID, user.getId());
        request.setAttribute(REQUEST_ATTR_ROLE, user.getRole());
        request.setAttribute("username", user.getNickName() != null ? user.getNickName() : user.getUsername());

        return true;
    }

    /**
     * 从 HTTP 请求头中提取 Token。
     * 优先读取标准 Authorization: Bearer xxx 头，
     * 其次读取自定义 token 头（兼容旧前端）。
     */
    private String extractToken(HttpServletRequest request) {
        // 标准 Authorization 头
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 兼容旧 token 头
        String tokenHeader = request.getHeader("token");
        if (tokenHeader != null && !tokenHeader.isEmpty()) {
            return tokenHeader;
        }

        return null;
    }

    /**
     * 返回 401 未授权 JSON 响应。
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"401\",\"msg\":\"" + message + "\"}");
    }

    /**
     * 权限检查：要求当前用户为管理员（role=1）。
     * 返回 null 表示检查通过；返回 Result.error 表示无权限，调用方应直接返回该 Result。
     *
     * @param request HTTP 请求（需已通过 Token 拦截器设置 role 属性）
     * @return null 表示有权限，非 null 表示无权限（含错误信息）
     */
    public static Result<?> requireAdmin(HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute(REQUEST_ATTR_ROLE);
        if (role == null || role != 1) {
            return Result.error("403", "无权限操作");
        }
        return null;
    }
}
