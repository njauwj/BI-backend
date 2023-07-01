package com.yupi.springbootinit.mq;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author wj
 * @create_time 2023/7/1
 * @description 消息生产者
 */
@Component
public class MqProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;


    public void sendMessage(String msg) {
        Message message = MessageBuilder.withBody(msg.getBytes()).build();
        rabbitTemplate.convertAndSend("exchange.bi", "bi", message);
    }

}
