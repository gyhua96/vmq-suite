package com.vone.mq.controller;

import com.vone.mq.config.AdminSecurityInterceptor;
import com.vone.mq.config.WebMvcConfig;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.PageRes;
import com.vone.mq.entity.PayQrcode;
import com.vone.mq.service.AdminMenuService;
import com.vone.mq.service.AdminSessionService;
import com.vone.mq.service.AdminService;
import com.vone.mq.service.LoginAttemptService;
import com.vone.mq.utils.ResUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(AdminController.class)
@Import({AdminSecurityInterceptor.class, WebMvcConfig.class})
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AdminService adminService;
    @MockBean
    private AdminMenuService adminMenuService;
    @MockBean
    private AdminSessionService adminSessionService;
    @MockBean
    private LoginAttemptService loginAttemptService;

    @org.junit.Before
    public void allowLoginAttemptsByDefault() {
        when(loginAttemptService.isAllowed(anyString(), anyLong())).thenReturn(true);
    }

    private static final String LOGIN_ATTR = AdminSecurityInterceptor.LOGIN_ATTR;
    private static final String CSRF_ATTR = AdminSecurityInterceptor.CSRF_ATTR;
    private static final String CSRF_HEADER = AdminSecurityInterceptor.CSRF_HEADER;

    @Test
    public void loginRequiresUser() throws Exception {
        mockMvc.perform(post("/login").param("pass", "password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("请输入账号"));
    }

    @Test
    public void loginRequiresPassword() throws Exception {
        mockMvc.perform(post("/login").param("user", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("请输入密码"));
    }

    @Test
    public void loginReturnsServiceErrorWithoutCreatingSessionContract() throws Exception {
        when(adminService.login("admin", "bad")).thenReturn(ResUtil.error("账号或密码不正确"));

        mockMvc.perform(post("/login").param("user", "admin").param("pass", "bad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("账号或密码不正确"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    public void loginSuccessCreatesSessionAndReturnsCsrfToken() throws Exception {
        CommonRes success = ResUtil.success();
        when(adminService.login("admin", "strong-pass")).thenReturn(success);
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("csrfToken", "fixed-csrf-token");
        when(adminSessionService.establishLogin(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    jakarta.servlet.http.HttpSession session = invocation.getArgument(1);
                    session.setAttribute(AdminSecurityInterceptor.LOGIN_ATTR, "1");
                    session.setAttribute(AdminSecurityInterceptor.CSRF_ATTR, "fixed-csrf-token");
                    return sessionData;
                });

        mockMvc.perform(post("/login").param("user", "admin").param("pass", "strong-pass"))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttribute(AdminSecurityInterceptor.LOGIN_ATTR, "1"))
                .andExpect(request().sessionAttribute(AdminSecurityInterceptor.CSRF_ATTR, notNullValue()))
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("成功"))
                .andExpect(jsonPath("$.data.csrfToken").value("fixed-csrf-token"));
    }

    @Test
    public void adminWriteApiRequiresLoginBeforeControllerMethod() throws Exception {
        mockMvc.perform(post("/admin/saveSetting"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("未登录"));
    }

    @Test
    public void adminWriteApiRejectsMissingCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/saveSetting")
                        .sessionAttr(AdminSecurityInterceptor.LOGIN_ATTR, "1")
                        .sessionAttr(AdminSecurityInterceptor.CSRF_ATTR, "csrf-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("CSRF 校验失败"));
    }

    @Test
    public void saveSettingDelegatesWhenCsrfHeaderMatches() throws Exception {
        when(adminService.saveSetting("admin", "pass", "https://merchant.example/cb",
                "https://merchant.example/return", "secret", "wx-qr", "zfb-qr", "5", "1", "0"))
                .thenReturn(ResUtil.success());

        mockMvc.perform(post("/admin/saveSetting")
                        .sessionAttr(AdminSecurityInterceptor.LOGIN_ATTR, "1")
                        .sessionAttr(AdminSecurityInterceptor.CSRF_ATTR, "csrf-token")
                        .header(AdminSecurityInterceptor.CSRF_HEADER, "csrf-token")
                        .param("user", "admin")
                        .param("pass", "pass")
                        .param("notifyUrl", "https://merchant.example/cb")
                        .param("returnUrl", "https://merchant.example/return")
                        .param("key", "secret")
                        .param("wxpay", "wx-qr")
                        .param("zfbpay", "zfb-qr")
                        .param("close", "5")
                        .param("payQf", "1")
                        .param("callbackAsync", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("成功"));

        verify(adminService).saveSetting("admin", "pass", "https://merchant.example/cb",
                "https://merchant.example/return", "secret", "wx-qr", "zfb-qr", "5", "1", "0");
    }

    @Test
    public void adminMenuWithoutLoginIsRejectedByInterceptor() throws Exception {
        mockMvc.perform(get("/admin/getMenu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("未登录"));
    }

    @Test
    public void adminMenuReturnsMenuForLoggedInSession() throws Exception {
        Map<String, Object> menu = new HashMap<>();
        menu.put("name", "系统设置");
        menu.put("type", "url");
        menu.put("url", "/admin-ui/index.html#/settings");
        when(adminMenuService.getMenu()).thenReturn(Collections.singletonList(menu));

        mockMvc.perform(get("/admin/getMenu")
                        .sessionAttr(AdminSecurityInterceptor.LOGIN_ATTR, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("系统设置"))
                .andExpect(jsonPath("$[0].type").value("url"))
                .andExpect(jsonPath("$[0].url").value("/admin-ui/index.html#/settings"));
    }

    @Test
    public void getSettingsDelegatesForLoggedInSession() throws Exception {
        Map<String, String> settings = new HashMap<>();
        settings.put("pass", "********");
        settings.put("callbackAsync", "0");
        when(adminService.getSettings()).thenReturn(ResUtil.success(settings));

        mockMvc.perform(get("/admin/getSettings").sessionAttr(LOGIN_ATTR, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.pass").value("********"))
                .andExpect(jsonPath("$.data.callbackAsync").value("0"));

        verify(adminService).getSettings();
    }

    @Test
    public void getOrdersReturnsLayuiPageContract() throws Exception {
        Map<String, Object> order = new HashMap<>();
        order.put("orderId", "202607011030301234");
        order.put("state", 0);
        when(adminService.getOrders(2, 20, 1, 0))
                .thenReturn(PageRes.success(1, Collections.singletonList(order)));

        mockMvc.perform(get("/admin/getOrders")
                        .sessionAttr(LOGIN_ATTR, "1")
                        .param("page", "2")
                        .param("limit", "20")
                        .param("type", "1")
                        .param("state", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].orderId").value("202607011030301234"))
                .andExpect(jsonPath("$.data[0].state").value(0));

        verify(adminService).getOrders(2, 20, 1, 0);
    }

    @Test
    public void setBdRequiresIdAfterCsrfPasses() throws Exception {
        mockMvc.perform(post("/admin/setBd")
                        .sessionAttr(LOGIN_ATTR, "1")
                        .sessionAttr(CSRF_ATTR, "csrf-token")
                        .header(CSRF_HEADER, "csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("失败"));
    }

    @Test
    public void setBdDelegatesWhenIdIsPresent() throws Exception {
        when(adminService.setBd(7)).thenReturn(ResUtil.success());

        mockMvc.perform(post("/admin/setBd")
                        .sessionAttr(LOGIN_ATTR, "1")
                        .sessionAttr(CSRF_ATTR, "csrf-token")
                        .header(CSRF_HEADER, "csrf-token")
                        .param("id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(adminService).setBd(7);
    }

    @Test
    public void getPayQrcodesReturnsLayuiPageContract() throws Exception {
        PayQrcode qrcode = new PayQrcode();
        qrcode.setId(3L);
        qrcode.setPayUrl("https://qr.example/pay");
        qrcode.setPrice(49.95);
        qrcode.setType(2);
        when(adminService.getPayQrcodes(1, 10, 2))
                .thenReturn(PageRes.success(1, Collections.singletonList(qrcode)));

        mockMvc.perform(get("/admin/getPayQrcodes")
                        .sessionAttr(LOGIN_ATTR, "1")
                        .param("page", "1")
                        .param("limit", "10")
                        .param("type", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[0].price").value(closeTo(49.95, 0.001)))
                .andExpect(jsonPath("$.data[0].type").value(2));

        verify(adminService).getPayQrcodes(1, 10, 2);
    }

    @Test
    public void addPayQrcodeBindsFormAndDelegates() throws Exception {
        when(adminService.addPayQrcode(any(PayQrcode.class))).thenReturn(ResUtil.success());

        mockMvc.perform(post("/admin/addPayQrcode")
                        .sessionAttr(LOGIN_ATTR, "1")
                        .sessionAttr(CSRF_ATTR, "csrf-token")
                        .header(CSRF_HEADER, "csrf-token")
                        .param("payUrl", "https://qr.example/pay")
                        .param("price", "12.34")
                        .param("type", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(adminService).addPayQrcode(org.mockito.ArgumentMatchers.argThat(qrcode ->
                qrcode != null
                        && "https://qr.example/pay".equals(qrcode.getPayUrl())
                        && new BigDecimal("12.34").compareTo(qrcode.getPrice()) == 0
                        && qrcode.getType() == 1));
    }

    @Test
    public void delPayQrcodeDelegatesWithCsrf() throws Exception {
        when(adminService.delPayQrcode(3L)).thenReturn(ResUtil.success());

        mockMvc.perform(post("/admin/delPayQrcode")
                        .sessionAttr(LOGIN_ATTR, "1")
                        .sessionAttr(CSRF_ATTR, "csrf-token")
                        .header(CSRF_HEADER, "csrf-token")
                        .param("id", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(adminService).delPayQrcode(3L);
    }

    @Test
    public void getMainDelegatesForLoggedInSession() throws Exception {
        Map<String, String> stats = new HashMap<>();
        stats.put("todayOrder", "10");
        stats.put("todayMoney", "199.90");
        when(adminService.getMain()).thenReturn(ResUtil.success(stats));

        mockMvc.perform(get("/admin/getMain").sessionAttr(LOGIN_ATTR, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.todayOrder").value("10"))
                .andExpect(jsonPath("$.data.todayMoney").value("199.90"));

        verify(adminService).getMain();
    }

    @Test
    public void orderDeletionApisDelegateWithCsrf() throws Exception {
        when(adminService.delOrder(9L)).thenReturn(ResUtil.success());
        when(adminService.delGqOrder()).thenReturn(ResUtil.success());
        when(adminService.delLastOrder()).thenReturn(ResUtil.success());

        mockMvc.perform(post("/admin/delOrder")
                        .sessionAttr(LOGIN_ATTR, "1")
                        .sessionAttr(CSRF_ATTR, "csrf-token")
                        .header(CSRF_HEADER, "csrf-token")
                        .param("id", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(post("/admin/delGqOrder")
                        .sessionAttr(LOGIN_ATTR, "1")
                        .sessionAttr(CSRF_ATTR, "csrf-token")
                        .header(CSRF_HEADER, "csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(post("/admin/delLastOrder")
                        .sessionAttr(LOGIN_ATTR, "1")
                        .sessionAttr(CSRF_ATTR, "csrf-token")
                        .header(CSRF_HEADER, "csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(adminService).delOrder(9L);
        verify(adminService).delGqOrder();
        verify(adminService).delLastOrder();
    }
}
