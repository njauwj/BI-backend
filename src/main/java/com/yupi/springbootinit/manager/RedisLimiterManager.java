package com.yupi.springbootinit.manager;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.ThrowUtils;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @author wj
 * @create_time 2023/6/29
 * @description 基于redisson实现限流
 */
@Component
public class RedisLimiterManager {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 令牌桶限流
     *
     * @param key 唯一标识
     */
    public void allowRequest(String key) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        /*
        rate：限流速率，表示每秒允许通过的操作数量。
        rateInterval：限流间隔，表示时间间隔的长度。例如，如果设置为1，则表示限流器的速率是每秒允许通过的操作数量。
        rateIntervalUnit：限流间隔的时间单位，可以是TimeUnit.SECONDS、TimeUnit.MILLISECONDS等。
         */
        rateLimiter.trySetRate(RateType.OVERALL, 3, 1, RateIntervalUnit.SECONDS);
        //尝试获取一个令牌，permits可以设置一次获取多少令牌
        boolean canOperate = rateLimiter.tryAcquire();
        ThrowUtils.throwIf(!canOperate, ErrorCode.TOO_MANY_REQUEST, "请求过于频繁");
    }


}
