package com.vone.mq.service;

import com.vone.mq.config.AdminSecurityInterceptor;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AdminSessionServiceTest {

    @Test
    public void establishLoginStoresLoginStateAndReturnsCsrfToken() {
        AdminSessionService service = new AdminSessionService() {
            @Override
            protected String newCsrfToken() {
                return "fixed-csrf-token";
            }
        };
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        Map<String, String> data = service.establishLogin(request, session);

        assertEquals("1", session.getAttribute(AdminSecurityInterceptor.LOGIN_ATTR));
        assertEquals("fixed-csrf-token", session.getAttribute(AdminSecurityInterceptor.CSRF_ATTR));
        assertEquals("fixed-csrf-token", data.get("csrfToken"));
    }
}
