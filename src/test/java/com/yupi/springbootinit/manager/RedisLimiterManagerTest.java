package com.yupi.springbootinit.manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisLimiterManagerTest {
    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Test
    void testLimit() throws InterruptedException {
        String userId = "111";
        for (int i = 0; i < 2; i++) {
            redisLimiterManager.allowRequest(userId);
            System.out.println("success");
        }
        Thread.sleep(1000);
        for (int i = 0; i < 10; i++) {
            redisLimiterManager.allowRequest(userId);
            System.out.println("success");
        }
    }

}