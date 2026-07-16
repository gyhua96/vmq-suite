package com.vone.mq;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class MqApplicationTests {

    static {
        System.setProperty("vmq.admin.password", "test-only-password");
    }

    @Test
    public void contextLoads() {
    }

}

