package com.yupi.springbootinit.manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static com.yupi.springbootinit.constant.AIConstant.MODEL_ID;

@SpringBootTest
class AIManagerTest {
    @Resource
    private AIManager aiManager;

    @Test
    void testChat() {
        String s = aiManager.doChat(MODEL_ID, "分析需求：\n" +
                "分析网站的增长趋势\n" +
                "原始数据：\n" +
                "日期,用户数\n" +
                "1号，10\n" +
                "2号，20\n" +
                "3号，30");
        String[] split = s.split("【【【【【");
        System.out.println(s);
    }

    @Test
    void testSplit() {
        String s = "【【【【【\n" +
                "{\n" +
                "  title: {\n" +
                "    text: '网站用户增长趋势',\n" +
                "    textStyle: {\n" +
                "      fontSize: 16,\n" +
                "      fontWeight: 'bold'\n" +
                "    }\n" +
                "  },\n" +
                "  xAxis: {\n" +
                "    type: 'category',\n" +
                "    data: ['1号', '2号', '3号']\n" +
                "  },\n" +
                "  yAxis: {\n" +
                "    type: 'value'\n" +
                "  },\n" +
                "  series: [{\n" +
                "    type: 'line',\n" +
                "    data: [10, 20, 30]\n" +
                "  }]\n" +
                "}\n" +
                "【【【【【\n" +
                "根据提供的数据，可以看出网站的用户数在1号到3号期间呈现逐日增长的趋势。";
        String[] split = s.split("【【【【【");

    }

}