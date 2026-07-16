package com.vone.mq.service;

import com.vone.mq.dto.CommonRes;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminServiceTest {
    private AdminService adminService;
    private AdminAuthService adminAuthService;

    @Before
    public void setUp() {
        adminService = new AdminService();
        adminAuthService = mock(AdminAuthService.class);
        ReflectionTestUtils.setField(adminService, "adminAuthService", adminAuthService);
    }

    @Test
    public void loginDelegatesToAuthService() {
        CommonRes expected = new CommonRes();
        when(adminAuthService.login("admin", "password")).thenReturn(expected);

        CommonRes result = adminService.login("admin", "password");

        assertSame(expected, result);
    }
}
