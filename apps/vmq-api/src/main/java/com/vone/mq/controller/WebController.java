package com.vone.mq.controller;

import com.vone.mq.dto.CommonRes;
import com.vone.mq.service.CreateOrderEndpointService;
import com.vone.mq.service.QrcodeService;
import com.vone.mq.service.WebService;
import com.vone.mq.utils.ResUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

@RestController
public class WebController {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebController.class);

    @Autowired
    private WebService webService;
    @Autowired
    private QrcodeService qrcodeService;
    @Autowired
    private CreateOrderEndpointService createOrderEndpointService;

    @GetMapping("/enQrcode")
    public void enQrcode(HttpServletResponse resp, String url) throws IOException {
        if (url != null && !"".equals(url)) {
            if (url.length() > QrcodeService.MAX_ENCODE_CHARS) {
                resp.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return;
            }
            ServletOutputStream stream = null;
            try {
                stream = resp.getOutputStream();
                qrcodeService.writePng(url, stream);
            } catch (Exception e) {
                LOGGER.warn("Failed to encode qrcode image", e);
            } finally {
                if (stream != null) {
                    stream.flush();
                    stream.close();
                }
            }
        }
    }

    @PostMapping("/deQrcode")
    public CommonRes deQrcode(HttpSession session, String base64) {
        if (session == null || session.getAttribute("login") == null) {
            return ResUtil.error("未登录");
        }
        if (base64 != null && !"".equals(base64)) {
            if (base64.length() > QrcodeService.MAX_BASE64_CHARS) {
                return ResUtil.error("二维码图片过大");
            }
            try {
                return ResUtil.success(qrcodeService.decodeBase64(base64));
            } catch (Exception e) {
                LOGGER.warn("Failed to decode base64 qrcode image: {}", e.getClass().getSimpleName());
            }
        }
        return ResUtil.error();
    }

    @PostMapping("/deQrcode2")
    public CommonRes deQrcode2(HttpSession session, @RequestParam("file") MultipartFile file) {
        if (session == null || session.getAttribute("login") == null) {
            return ResUtil.error("未登录");
        }
        if (file != null) {
            if (file.isEmpty()) {
                return ResUtil.error();
            }
            if (file.getSize() > QrcodeService.MAX_FILE_BYTES) {
                return ResUtil.error("二维码图片过大");
            }
            try {
                // 二维码内容可能包含通讯密钥，不写入日志。
                return ResUtil.success(qrcodeService.decode(file.getBytes()));
            } catch (Exception e) {
                LOGGER.warn("Failed to decode uploaded qrcode image: {}", e.getClass().getSimpleName());
            }
        }
        return ResUtil.error();
    }

    /**
     * 创建订单
     *
     * @param payId     商户订单号
     * @param param     订单保存的信息
     * @param type      支付方式 1|微信 2|支付宝
     * @param price     订单价格
     * @param notifyUrl 异步通知地址，如果为空则使用系统后台设置的地址
     * @param returnUrl 支付完成后同步跳转地址，将会携带参数跳转
     * @param sign      签名认证 签名方式为 md5(payId + param + type + price + 通讯密钥)
     * @param isHtml    0返回json数据 1跳转到支付页面
     * @return
     */
    @RequestMapping(value = "/createOrder", method = {RequestMethod.GET, RequestMethod.POST})
    public String createOrder(String payId, String param, Integer type, String price, String notifyUrl, String returnUrl,
                              String sign, String signType, String timestamp, String nonce, Integer isHtml) {
        return createOrderEndpointService.handle(payId, param, type, price, notifyUrl, returnUrl,
                sign, signType, timestamp, nonce, isHtml);
    }

    @RequestMapping(value = "/closeOrder", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonRes closeOrder(String orderId, String sign, String signType, String timestamp, String nonce) {
        if (orderId == null) {
            return ResUtil.error("请传入云端订单号");
        }
        if (sign == null) {
            return ResUtil.error("请传入签名");
        }
        return webService.closeOrder(orderId, sign, signType, timestamp, nonce);
    }

    @RequestMapping(value = "/appHeart", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonRes appHeart(String t, String sign, String signType, String nonce) {
        return webService.appHeart(t, sign, signType, nonce);
    }

    @RequestMapping(value = "/appPush", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonRes appPush(Integer type, String price, String t, String sign, String signType, String nonce) {
        return webService.appPush(type, price, t, sign, signType, nonce);
    }

    @RequestMapping(value = "/getOrder", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonRes getOrder(String orderId, String sign, String signType, String timestamp, String nonce,
                              String accessToken) {
        if (orderId == null) {
            return ResUtil.error("请传入订单编号");
        }
        if (accessToken == null || accessToken.isBlank()) {
            return webService.getOrder(orderId, sign, signType, timestamp, nonce);
        }
        return webService.getOrder(orderId, sign, signType, timestamp, nonce, accessToken);
    }

    @RequestMapping(value = "/checkOrder", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonRes checkOrder(String orderId, String sign, String signType, String timestamp, String nonce,
                                String accessToken) {
        if (orderId == null) {
            return ResUtil.error("请传入订单编号");
        }
        if (accessToken == null || accessToken.isBlank()) {
            return webService.checkOrder(orderId, sign, signType, timestamp, nonce);
        }
        return webService.checkOrder(orderId, sign, signType, timestamp, nonce, accessToken);

    }

    @RequestMapping(value = "/getState", method = {RequestMethod.GET, RequestMethod.POST})
    public CommonRes getState(String t, String sign) {
        if (t == null) {
            return ResUtil.error("请传入t");
        }
        if (sign == null) {
            return ResUtil.error("请传入sign");
        }
        return webService.getState(t, sign);
    }
}
