package com.vone.mq.controller;

import com.vone.mq.config.AdminSecurityInterceptor;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.dto.PageRes;
import com.vone.mq.entity.PayQrcode;
import com.vone.mq.service.AdminMenuService;
import com.vone.mq.service.AdminSessionService;
import com.vone.mq.service.AdminService;
import com.vone.mq.service.LoginAttemptService;
import com.vone.mq.utils.ResUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.*;

@RestController
public class AdminController {
    @Autowired
    private AdminService adminService;
    @Autowired
    private AdminMenuService adminMenuService;
    @Autowired
    private AdminSessionService adminSessionService;
    @Autowired
    private LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    public CommonRes login(HttpServletRequest request, HttpSession session, String user, String pass){
        if (user==null){
            return ResUtil.error("请输入账号");
        }
        if (pass==null){
            return ResUtil.error("请输入密码");
        }
        String attemptKey = request.getRemoteAddr() + "\u0000" + user.trim();
        long now = System.currentTimeMillis();
        if (!loginAttemptService.isAllowed(attemptKey, now)) {
            return ResUtil.error("登录尝试过于频繁，请稍后再试");
        }
        CommonRes r = adminService.login(user, pass);
        if (r.getCode()==1){
            loginAttemptService.recordSuccess(attemptKey);
            r.setData(adminSessionService.establishLogin(request, session));
        } else {
            loginAttemptService.recordFailure(attemptKey, now);
        }
        return r;
    }

    @GetMapping("/admin/getMenu")
    public List<Map<String,Object>> getMenu(HttpSession session){
        if (session.getAttribute("login")==null){
            return null;
        }
        return adminMenuService.getMenu();
    }
    @PostMapping("/admin/saveSetting")
    public CommonRes saveSetting(HttpSession session,String user,String pass,String notifyUrl,String returnUrl,String key,String wxpay,String zfbpay,String close,String payQf,String callbackAsync){
        if (session.getAttribute("login")==null){
            return ResUtil.error("未登录");
        }
        return adminService.saveSetting(user, pass, notifyUrl, returnUrl, key, wxpay, zfbpay, close, payQf, callbackAsync);
    }
    @GetMapping("/admin/getSettings")
    public CommonRes getSettings(HttpSession session){
        if (session.getAttribute("login")==null){
            return ResUtil.error("未登录");
        }
        return adminService.getSettings();
    }
    @GetMapping("/admin/getOrders")
    public PageRes getOrders(HttpSession session,Integer page, Integer limit, Integer type, Integer state){
        if (session.getAttribute("login")==null){
            PageRes p = new PageRes();
            p.setCode(-1);
            p.setMsg("未登录");
            return p;
        }
        return adminService.getOrders(page, limit, type,state);
    }
    @PostMapping("/admin/setBd")
    public CommonRes setBd(HttpSession session,Integer id){
        if (session.getAttribute("login")==null){
            return ResUtil.error("未登录");
        }
        if (id==null){
            return ResUtil.error();
        }
        return adminService.setBd(id);
    }
    @GetMapping("/admin/getPayQrcodes")
    public PageRes getPayQrcodes(HttpSession session,Integer page, Integer limit, Integer type){
        if (session.getAttribute("login")==null){
            PageRes p = new PageRes();
            p.setCode(-1);
            p.setMsg("未登录");
            return p;
        }
        return adminService.getPayQrcodes(page, limit, type);
    }
    @PostMapping("/admin/delPayQrcode")
    public CommonRes delPayQrcode(HttpSession session,Long id){
        if (session.getAttribute("login")==null){
            return ResUtil.error("未登录");
        }

        return adminService.delPayQrcode(id);
    }
    @PostMapping("/admin/addPayQrcode")
    public CommonRes addPayQrcode(HttpSession session,PayQrcode payQrcode){
        if (session.getAttribute("login")==null){
            return ResUtil.error("未登录");
        }
        return adminService.addPayQrcode(payQrcode);
    }
    @PostMapping("/admin/updatePayQrcode")
    public CommonRes updatePayQrcode(HttpSession session,PayQrcode payQrcode){
        if (session.getAttribute("login")==null){
            return ResUtil.error("未登录");
        }
        return adminService.updatePayQrcode(payQrcode);
    }

    @GetMapping("/admin/getMain")
    public CommonRes getMain(HttpSession session){
        if (session.getAttribute("login")==null){
            return ResUtil.error("未登录");
        }
        return adminService.getMain();
    }

    @PostMapping("/admin/delOrder")
    public CommonRes delOrder(HttpSession session,Long id){
        if (session.getAttribute("login")==null){
            return ResUtil.error("未登录");
        }

        return adminService.delOrder(id);
    }

    @PostMapping("/admin/delGqOrder")
    public CommonRes delGqOrder(HttpSession session){
        if (session.getAttribute("login")==null){
            return ResUtil.error("未登录");
        }

        return adminService.delGqOrder();
    }
    @PostMapping("/admin/delLastOrder")
    public CommonRes delLastOrder(HttpSession session){
        if (session.getAttribute("login")==null){
            return ResUtil.error("未登录");
        }

        return adminService.delLastOrder();
    }

    @PostMapping("/logout")
    public CommonRes logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return ResUtil.success();
    }
}
