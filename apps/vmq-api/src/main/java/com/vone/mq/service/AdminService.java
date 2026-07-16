package com.vone.mq.service;

import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.PageRes;
import com.vone.mq.entity.PayQrcode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    @Autowired
    private AdminAuthService adminAuthService;
    @Autowired
    private AdminSettingService adminSettingService;
    @Autowired
    private AdminOrderService adminOrderService;
    @Autowired
    private AdminQrcodeService adminQrcodeService;

    public CommonRes login(String user,String pass){
        return adminAuthService.login(user, pass);
    }
    public CommonRes saveSetting(String user,String pass,String notifyUrl,String returnUrl,String key,String wxpay,String zfbpay,String close,String payQf,String callbackAsync){
        return adminSettingService.saveSetting(user, pass, notifyUrl, returnUrl, key, wxpay, zfbpay, close, payQf, callbackAsync);
    }
    public CommonRes getSettings(){
        return adminSettingService.getSettings();
    }

    public PageRes getOrders(Integer page, Integer limit, Integer type, Integer state){
        return adminOrderService.getOrders(page, limit, type, state);
    }

    public CommonRes setBd(Integer id){
        return adminOrderService.resendCallback(id);
    }

    public CommonRes addPayQrcode(PayQrcode payQrcode){
        return adminQrcodeService.addPayQrcode(payQrcode);
    }

    public CommonRes updatePayQrcode(PayQrcode payQrcode){
        return adminQrcodeService.updatePayQrcode(payQrcode);
    }

    public CommonRes getMain(){
        return adminOrderService.getMain();
    }

    public PageRes getPayQrcodes(Integer page, Integer limit, Integer type){
        return adminQrcodeService.getPayQrcodes(page, limit, type);
    }
    public CommonRes delPayQrcode(Long id){
        return adminQrcodeService.deletePayQrcode(id);
    }
    public CommonRes delOrder(Long id){
        return adminOrderService.deleteOrder(id);
    }
    public CommonRes delGqOrder(){
        return adminOrderService.deleteClosedOrders();
    }

    public CommonRes delLastOrder(){
        return adminOrderService.deleteOrdersBefore7Days();
    }

}
