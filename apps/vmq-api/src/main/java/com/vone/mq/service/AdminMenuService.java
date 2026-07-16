package com.vone.mq.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminMenuService {

    public List<Map<String, Object>> getMenu() {
        List<Map<String, Object>> menu = new ArrayList<>();
        menu.add(urlNode("系统设置", route("/settings")));
        menu.add(urlNode("监控端设置", route("/monitor")));

        List<Map<String, Object>> wxMenu = new ArrayList<>();
        wxMenu.add(urlNode("添加", route("/qrcodes/new/wechat")));
        wxMenu.add(urlNode("管理", route("/qrcodes/wechat")));
        menu.add(menuNode("微信二维码", wxMenu));

        List<Map<String, Object>> zfbMenu = new ArrayList<>();
        zfbMenu.add(urlNode("添加", route("/qrcodes/new/alipay")));
        zfbMenu.add(urlNode("管理", route("/qrcodes/alipay")));
        menu.add(menuNode("支付宝二维码", zfbMenu));

        menu.add(urlNode("订单列表", route("/orders")));
        return menu;
    }

    private String route(String path) {
        return "/admin-ui/index.html#" + path;
    }

    private Map<String, Object> urlNode(String name, String url) {
        Map<String, Object> node = new HashMap<>();
        node.put("name", name);
        node.put("type", "url");
        node.put("url", url);
        return node;
    }

    private Map<String, Object> menuNode(String name, List<Map<String, Object>> children) {
        Map<String, Object> node = new HashMap<>();
        node.put("name", name);
        node.put("type", "menu");
        node.put("node", children);
        return node;
    }
}
