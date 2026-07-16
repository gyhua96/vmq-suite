package com.vone.mq.controller;

import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.CreateOrderRes;
import com.vone.mq.config.AdminSecurityInterceptor;
import com.vone.mq.config.WebMvcConfig;
import com.vone.mq.service.CreateOrderEndpointService;
import com.vone.mq.service.QrcodeService;
import com.vone.mq.service.WebService;
import com.vone.mq.utils.ResUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(WebController.class)
@Import({AdminSecurityInterceptor.class, WebMvcConfig.class, CreateOrderEndpointService.class})
public class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private WebService webService;
    @MockBean
    private QrcodeService qrcodeService;

    @Test
    public void createOrderRequiresPayId() throws Exception {
        mockMvc.perform(post("/createOrder")
                        .param("type", "2")
                        .param("price", "49.95")
                        .param("sign", "sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("请传入商户订单号"));
    }

    @Test
    public void createOrderRequiresSupportedPayType() throws Exception {
        mockMvc.perform(post("/createOrder")
                        .param("payId", "P001")
                        .param("type", "3")
                        .param("price", "49.95")
                        .param("sign", "sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("支付方式错误=>1|微信 2|支付宝"));
    }

    @Test
    public void createOrderRejectsInvalidMoneyBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/createOrder")
                        .param("payId", "P001")
                        .param("type", "2")
                        .param("price", "49.999")
                        .param("sign", "sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("订单金额最多支持2位小数"));
        verifyNoMoreInteractions(webService);
    }

    @Test
    public void createOrderRequiresSign() throws Exception {
        mockMvc.perform(post("/createOrder")
                        .param("payId", "P001")
                        .param("type", "2")
                        .param("price", "49.95"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("请传入签名"));
    }

    @Test
    public void createOrderReturnsJsonByDefaultAndNormalizesNullParam() throws Exception {
        CreateOrderRes data = new CreateOrderRes("P001", "O001", 2, 49.95, 49.96,
                "pay-url", 1, 0, 5, 1782873030123L);
        when(webService.createOrder("P001", "", 2, "49.95", "https://merchant.example/cb",
                "https://merchant.example/return", "sign", null, null, null))
                .thenReturn(ResUtil.success(data));

        mockMvc.perform(post("/createOrder")
                        .param("payId", "P001")
                        .param("type", "2")
                        .param("price", "49.95")
                        .param("notifyUrl", "https://merchant.example/cb")
                        .param("returnUrl", "https://merchant.example/return")
                        .param("sign", "sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("成功"))
                .andExpect(jsonPath("$.data.payId").value("P001"))
                .andExpect(jsonPath("$.data.orderId").value("O001"))
                .andExpect(jsonPath("$.data.payType").value(2))
                .andExpect(jsonPath("$.data.price").value(49.95))
                .andExpect(jsonPath("$.data.reallyPrice").value(49.96))
                .andExpect(jsonPath("$.data.payUrl").value("pay-url"))
                .andExpect(jsonPath("$.data.isAuto").value(1))
                .andExpect(jsonPath("$.data.state").value(0))
                .andExpect(jsonPath("$.data.timeOut").value(5))
                .andExpect(jsonPath("$.data.date").value(1782873030123L));

        verify(webService).createOrder("P001", "", 2, "49.95", "https://merchant.example/cb",
                "https://merchant.example/return", "sign", null, null, null);
    }

    @Test
    public void createOrderSupportsHmacParameters() throws Exception {
        CreateOrderRes data = new CreateOrderRes("P001", "O001", 2, 49.95, 49.96,
                "pay-url", 1, 0, 5, 1782873030123L);
        when(webService.createOrder("P001", "user-1", 2, "49.95", null, null,
                "hmac", "HMAC_SHA256", "1782873030123", "nonce-1"))
                .thenReturn(ResUtil.success(data));

        mockMvc.perform(post("/createOrder")
                        .param("payId", "P001")
                        .param("param", "user-1")
                        .param("type", "2")
                        .param("price", "49.95")
                        .param("sign", "hmac")
                        .param("signType", "HMAC_SHA256")
                        .param("timestamp", "1782873030123")
                        .param("nonce", "nonce-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.orderId").value("O001"));

        verify(webService).createOrder("P001", "user-1", 2, "49.95", null, null,
                "hmac", "HMAC_SHA256", "1782873030123", "nonce-1");
    }

    @Test
    public void createOrderHtmlModeRedirectsToLegacyPayPageWhenSuccessful() throws Exception {
        CreateOrderRes data = new CreateOrderRes("P001", "O001", 2, 49.95, 49.96,
                "pay-url", 1, 0, 5, 1782873030123L);
        when(webService.createOrder("P001", "user-1", 2, "49.95", null, null,
                "sign", null, null, null))
                .thenReturn(ResUtil.success(data));

        mockMvc.perform(post("/createOrder")
                        .param("payId", "P001")
                        .param("param", "user-1")
                        .param("type", "2")
                        .param("price", "49.95")
                        .param("sign", "sign")
                        .param("isHtml", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("<script>window.location.href = '/payPage/pay.html?orderId=O001'</script>"));
    }

    @Test
    public void createOrderHtmlModeReturnsPlainErrorMessageWhenServiceFails() throws Exception {
        when(webService.createOrder("P001", "", 2, "49.95", null, null, "bad-sign", null, null, null))
                .thenReturn(ResUtil.error("签名校验不通过"));

        mockMvc.perform(post("/createOrder")
                        .param("payId", "P001")
                        .param("type", "2")
                        .param("price", "49.95")
                        .param("sign", "bad-sign")
                        .param("isHtml", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("签名校验不通过")));
    }

    @Test
    public void closeOrderRequiresOrderIdAndSign() throws Exception {
        mockMvc.perform(post("/closeOrder").param("sign", "sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("请传入云端订单号"));

        mockMvc.perform(post("/closeOrder").param("orderId", "O001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("请传入签名"));
    }

    @Test
    public void closeOrderDelegatesWithHmacParameters() throws Exception {
        when(webService.closeOrder("O001", "sign", "HMAC_SHA256", "1782873030123", "nonce-1"))
                .thenReturn(ResUtil.success());

        mockMvc.perform(post("/closeOrder")
                        .param("orderId", "O001")
                        .param("sign", "sign")
                        .param("signType", "HMAC_SHA256")
                        .param("timestamp", "1782873030123")
                        .param("nonce", "nonce-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("成功"));

        verify(webService).closeOrder("O001", "sign", "HMAC_SHA256", "1782873030123", "nonce-1");
    }

    @Test
    public void appHeartDelegatesLegacyMd5Parameters() throws Exception {
        when(webService.appHeart("1782873030123", "legacy-sign", null, null))
                .thenReturn(ResUtil.success());

        mockMvc.perform(post("/appHeart")
                        .param("t", "1782873030123")
                        .param("sign", "legacy-sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("成功"));

        verify(webService).appHeart("1782873030123", "legacy-sign", null, null);
    }

    @Test
    public void appHeartDelegatesHmacParameters() throws Exception {
        when(webService.appHeart("1782873030123", "hmac-sign", "HMAC_SHA256", "nonce-1"))
                .thenReturn(ResUtil.success());

        mockMvc.perform(post("/appHeart")
                        .param("t", "1782873030123")
                        .param("sign", "hmac-sign")
                        .param("signType", "HMAC_SHA256")
                        .param("nonce", "nonce-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("成功"));

        verify(webService).appHeart("1782873030123", "hmac-sign", "HMAC_SHA256", "nonce-1");
    }

    @Test
    public void appPushDelegatesLegacyMd5ParametersAndKeepsPriceString() throws Exception {
        when(webService.appPush(2, "1.0", "1782873030123", "legacy-sign", null, null))
                .thenReturn(ResUtil.success());

        mockMvc.perform(post("/appPush")
                        .param("type", "2")
                        .param("price", "1.0")
                        .param("t", "1782873030123")
                        .param("sign", "legacy-sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("成功"));

        verify(webService).appPush(2, "1.0", "1782873030123", "legacy-sign", null, null);
    }

    @Test
    public void appPushDelegatesHmacParameters() throws Exception {
        when(webService.appPush(2, "49.95", "1782873030123", "hmac-sign", "HMAC_SHA256", "nonce-1"))
                .thenReturn(ResUtil.success());

        mockMvc.perform(post("/appPush")
                        .param("type", "2")
                        .param("price", "49.95")
                        .param("t", "1782873030123")
                        .param("sign", "hmac-sign")
                        .param("signType", "HMAC_SHA256")
                        .param("nonce", "nonce-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("成功"));

        verify(webService).appPush(2, "49.95", "1782873030123", "hmac-sign", "HMAC_SHA256", "nonce-1");
    }

    @Test
    public void getOrderRequiresOrderId() throws Exception {
        mockMvc.perform(post("/getOrder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("请传入订单编号"));
    }

    @Test
    public void getOrderDelegatesOptionalSignatureParameters() throws Exception {
        when(webService.getOrder("O001", "sign", "HMAC_SHA256", "1782873030123", "nonce-1"))
                .thenReturn(ResUtil.success("order-data"));

        mockMvc.perform(post("/getOrder")
                        .param("orderId", "O001")
                        .param("sign", "sign")
                        .param("signType", "HMAC_SHA256")
                        .param("timestamp", "1782873030123")
                        .param("nonce", "nonce-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value("order-data"));

        verify(webService).getOrder("O001", "sign", "HMAC_SHA256", "1782873030123", "nonce-1");
    }

    @Test
    public void checkOrderRequiresOrderId() throws Exception {
        mockMvc.perform(post("/checkOrder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("请传入订单编号"));
    }

    @Test
    public void checkOrderDelegatesOptionalSignatureParameters() throws Exception {
        when(webService.checkOrder("O001", "sign", "HMAC_SHA256", "1782873030123", "nonce-1"))
                .thenReturn(ResUtil.success("https://merchant.example/return"));

        mockMvc.perform(post("/checkOrder")
                        .param("orderId", "O001")
                        .param("sign", "sign")
                        .param("signType", "HMAC_SHA256")
                        .param("timestamp", "1782873030123")
                        .param("nonce", "nonce-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value("https://merchant.example/return"));

        verify(webService).checkOrder("O001", "sign", "HMAC_SHA256", "1782873030123", "nonce-1");
    }

    @Test
    public void getStateRequiresTimestampAndSign() throws Exception {
        mockMvc.perform(post("/getState").param("sign", "sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("请传入t"));

        mockMvc.perform(post("/getState").param("t", "1782873030123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("请传入sign"));
    }

    @Test
    public void getStateDelegatesLegacySignatureParameters() throws Exception {
        when(webService.getState("1782873030123", "sign"))
                .thenReturn(ResUtil.success("state-data"));

        mockMvc.perform(post("/getState")
                        .param("t", "1782873030123")
                        .param("sign", "sign"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value("state-data"));

        verify(webService).getState("1782873030123", "sign");
    }

    @Test
    public void deQrcodeRequiresLogin() throws Exception {
        mockMvc.perform(post("/deQrcode").param("base64", "base64"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("未登录"));
    }

    @Test
    public void deQrcodeRejectsLargeBase64BeforeDecode() throws Exception {
        String largeBase64 = repeat("a", QrcodeService.MAX_BASE64_CHARS + 1);

                mockMvc.perform(post("/deQrcode")
                        .sessionAttr("login", 1)
                        .sessionAttr("csrfToken", "csrf")
                        .header("X-CSRF-Token", "csrf")
                        .param("base64", largeBase64))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("二维码图片过大"));

        verifyNoMoreInteractions(qrcodeService);
    }

    @Test
    public void deQrcodeReturnsDecodedContent() throws Exception {
        when(qrcodeService.decodeBase64("base64")).thenReturn("qr-content");

                mockMvc.perform(post("/deQrcode")
                        .sessionAttr("login", 1)
                        .sessionAttr("csrfToken", "csrf")
                        .header("X-CSRF-Token", "csrf")
                        .param("base64", "base64"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value("qr-content"));

        verify(qrcodeService).decodeBase64("base64");
    }

    @Test
    public void deQrcodeReturnsLegacyErrorWhenDecodeFails() throws Exception {
        when(qrcodeService.decodeBase64("bad-base64")).thenThrow(new IllegalArgumentException("bad image"));

                mockMvc.perform(post("/deQrcode")
                        .sessionAttr("login", 1)
                        .sessionAttr("csrfToken", "csrf")
                        .header("X-CSRF-Token", "csrf")
                        .param("base64", "bad-base64"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1));
    }

    @Test
    public void deQrcode2RequiresLogin() throws Exception {
                mockMvc.perform(multipart("/deQrcode2")
                        .file("file", "content".getBytes()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("未登录"));
    }

    @Test
    public void deQrcode2RejectsEmptyFileBeforeDecode() throws Exception {
                mockMvc.perform(multipart("/deQrcode2")
                        .file("file", new byte[0])
                        .sessionAttr("login", 1)
                        .sessionAttr("csrfToken", "csrf")
                        .header("X-CSRF-Token", "csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1));

        verifyNoMoreInteractions(qrcodeService);
    }

    @Test
    public void deQrcode2RejectsLargeFileBeforeDecode() throws Exception {
        byte[] largeFile = new byte[(int) QrcodeService.MAX_FILE_BYTES + 1];

                mockMvc.perform(multipart("/deQrcode2")
                        .file("file", largeFile)
                        .sessionAttr("login", 1)
                        .sessionAttr("csrfToken", "csrf")
                        .header("X-CSRF-Token", "csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value("二维码图片过大"));

        verifyNoMoreInteractions(qrcodeService);
    }

    @Test
    public void deQrcode2ReturnsDecodedContent() throws Exception {
        when(qrcodeService.decode(any(byte[].class))).thenReturn("uploaded-qr-content");

                mockMvc.perform(multipart("/deQrcode2")
                        .file("file", "content".getBytes())
                        .sessionAttr("login", 1)
                        .sessionAttr("csrfToken", "csrf")
                        .header("X-CSRF-Token", "csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value("uploaded-qr-content"));
    }

    @Test
    public void deQrcode2ReturnsLegacyErrorWhenDecodeFails() throws Exception {
        when(qrcodeService.decode(any(byte[].class))).thenThrow(new IllegalArgumentException("bad image"));

                mockMvc.perform(multipart("/deQrcode2")
                        .file("file", "bad-content".getBytes())
                        .sessionAttr("login", 1)
                        .sessionAttr("csrfToken", "csrf")
                        .header("X-CSRF-Token", "csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1));
    }

    private String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
