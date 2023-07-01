package com.yupi.springbootinit.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author wj
 * @create_time 2023/7/1
 * @description Rabbitmq 配置交换机和队列
 */
@Configuration
public class RabbitmqConfig {

    @Bean
    public DirectExchange directExchange() {
        return ExchangeBuilder.directExchange("exchange.bi").build();
    }

    @Bean
    public Queue queue() {
        return QueueBuilder.durable("queue.bi").build();
    }

    @Bean
    public Binding binding(DirectExchange directExchange, Queue queue) {
        return BindingBuilder.bind(queue).to(directExchange).with("bi");
    }

}
