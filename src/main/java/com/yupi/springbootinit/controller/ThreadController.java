package com.yupi.springbootinit.controller;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author wj
 * @create_time 2023/6/30
 * @description
 */
@RestController
@Slf4j
@RequestMapping("/thread")
public class ThreadController {

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @GetMapping("/add")
    public void addTask(String task) {
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                System.out.println(task + "正在执行\t" + "执行线程:" + Thread.currentThread().getName());
                try {
                    Thread.sleep(6000000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, threadPoolExecutor);
    }

    @GetMapping("/get")
    public String getThreadMessage() {
        HashMap<String, Object> map = new HashMap<>();
        int corePoolSize = threadPoolExecutor.getCorePoolSize();
        map.put("核心线程数", corePoolSize);
        int maximumPoolSize = threadPoolExecutor.getMaximumPoolSize();
        map.put("最大线程数", maximumPoolSize);
        long taskCount = threadPoolExecutor.getTaskCount();
        map.put("任务总数", taskCount);
        int activeCount = threadPoolExecutor.getActiveCount();
        map.put("存活线程数", activeCount);
        int size = threadPoolExecutor.getQueue().size();
        map.put("消息队列里的消息个数", size);
        return JSONUtil.toJsonStr(map);
    }


}
