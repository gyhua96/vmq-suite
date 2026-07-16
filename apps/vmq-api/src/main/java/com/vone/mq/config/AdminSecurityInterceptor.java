package com.vone.mq.config;

import com.vone.mq.utils.ResUtil;
import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class AdminSecurityInterceptor implements HandlerInterceptor {
    public static final String LOGIN_ATTR = "login";
    public static final String CSRF_ATTR = "csrfToken";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private final Gson gson = new Gson();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        boolean adminPath = path.startsWith("/admin/");
        boolean qrDecodePath = "/deQrcode".equals(path) || "/deQrcode2".equals(path);
        if (!adminPath && !qrDecodePath) return true;
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(LOGIN_ATTR) == null) {
            writeJson(response, 401, "未登录");
            return false;
        }
        if (isUnsafe(request.getMethod())) {
            // 排除 getMenu 只读菜单请求的 CSRF 校验
            if (path.endsWith("/getMenu")) {
                return true;
            }
            String expected = (String) session.getAttribute(CSRF_ATTR);
            String actual = request.getHeader(CSRF_HEADER);
            if (actual == null || actual.isEmpty()) actual = request.getParameter("csrfToken");
            if (expected == null || actual == null || !expected.equals(actual)) {
                writeJson(response, 403, "CSRF 校验失败");
                return false;
            }
        }
        return true;
    }

    private boolean isUnsafe(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(gson.toJson(ResUtil.error(message)));
    }
}
