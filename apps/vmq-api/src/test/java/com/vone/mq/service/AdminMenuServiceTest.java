package com.vone.mq.service;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AdminMenuServiceTest {

    @Test
    public void getMenuBuildsAdminUiMenuTree() {
        AdminMenuService service = new AdminMenuService();

        List<Map<String, Object>> menu = service.getMenu();

        assertEquals(5, menu.size());
        assertUrlNode(menu.get(0), "系统设置", "/admin-ui/index.html#/settings");
        assertUrlNode(menu.get(1), "监控端设置", "/admin-ui/index.html#/monitor");

        assertEquals("微信二维码", menu.get(2).get("name"));
        assertEquals("menu", menu.get(2).get("type"));
        List<Map<String, Object>> wxChildren = children(menu.get(2));
        assertEquals(2, wxChildren.size());
        assertUrlNode(wxChildren.get(0), "添加", "/admin-ui/index.html#/qrcodes/new/wechat");
        assertUrlNode(wxChildren.get(1), "管理", "/admin-ui/index.html#/qrcodes/wechat");

        assertEquals("支付宝二维码", menu.get(3).get("name"));
        assertEquals("menu", menu.get(3).get("type"));
        List<Map<String, Object>> zfbChildren = children(menu.get(3));
        assertEquals(2, zfbChildren.size());
        assertUrlNode(zfbChildren.get(0), "添加", "/admin-ui/index.html#/qrcodes/new/alipay");
        assertUrlNode(zfbChildren.get(1), "管理", "/admin-ui/index.html#/qrcodes/alipay");

        assertUrlNode(menu.get(4), "订单列表", "/admin-ui/index.html#/orders");
    }

    private void assertUrlNode(Map<String, Object> node, String name, String url) {
        assertEquals(name, node.get("name"));
        assertEquals("url", node.get("type"));
        assertEquals(url, node.get("url"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> children(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("node");
    }
}
