package com.vone.mq.service;

import com.vone.mq.config.AdminSecurityInterceptor;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminSessionService {

    public Map<String, String> establishLogin(HttpServletRequest request, HttpSession session) {
        try {
            request.changeSessionId();
        } catch (IllegalStateException ignored) {
            // Session may already be invalidated by container; continue with current session.
        }
        String csrf = newCsrfToken();
        session.setAttribute(AdminSecurityInterceptor.LOGIN_ATTR, "1");
        session.setAttribute(AdminSecurityInterceptor.CSRF_ATTR, csrf);
        Map<String, String> data = new HashMap<>();
        data.put("csrfToken", csrf);
        return data;
    }

    protected String newCsrfToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
