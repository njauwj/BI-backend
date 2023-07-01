package com.yupi.springbootinit.mq;

import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.AIManager;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

import static com.yupi.springbootinit.constant.AIConstant.MODEL_ID;

/**
 * @author wj
 * @create_time 2023/7/1
 * @description 消息消费者
 */
@Component
public class MqConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AIManager aiManager;


    @RabbitListener(queues = {"queue.bi"})
    public void consumeMessage(Message message, Channel channel) {
        Long deliveryTag = null;
        Chart chart = null;
        try {
            byte[] body = message.getBody();
            MessageProperties messageProperties = message.getMessageProperties();
            deliveryTag = messageProperties.getDeliveryTag();
            String chartId = new String(body);
            chart = chartService.getById(Long.parseLong(chartId));
            String goal = chart.getGoal();
            // 用户输入
            StringBuilder userInput = new StringBuilder();
            userInput.append("分析需求:\n").append(goal).append("\n");
            // 压缩后的数据
            String chartData = chart.getChartData();
            userInput.append("原始数据:\n").append(chartData).append("\n");
            //异步执行
            chart.setStatus("running");
            boolean b = chartService.updateById(chart);
            ThrowUtils.throwIf(!b, ErrorCode.PARAMS_ERROR, "更新图表失败");
            String[] answer = aiManager.doChat(MODEL_ID, userInput.toString()).split("【【【【【");
            ThrowUtils.throwIf(answer.length != 3, ErrorCode.PARAMS_ERROR, "生成数据失败请重试");
            String chatCode = answer[1];
            String chatConclusion = answer[2];
            chart.setGenChart(chatCode);
            chart.setGenResult(chatConclusion);
            chart.setStatus("succeed");
            boolean b1 = chartService.updateById(chart);
            ThrowUtils.throwIf(!b1, ErrorCode.PARAMS_ERROR, "更新图表失败");
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            try {
                if (chart != null) {
                    //todo 可以表字段加一个重试次数 每失败一次加一
                    chart.setStatus("failed");
                    chartService.updateById(chart);
                }
                //处理失败重新放回队列
                if (deliveryTag != null) {
                    //todo 先判断重试次数是否达到预定中，达到了就就该消息设置为死信 即requeue设置为false
                    channel.basicNack(deliveryTag, false, true);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            throw new RuntimeException(e);
        }
    }

}
