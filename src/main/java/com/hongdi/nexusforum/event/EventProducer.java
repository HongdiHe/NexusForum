package com.hongdi.nexusforum.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongdi.nexusforum.entity.Event;
import com.hongdi.nexusforum.util.CommunityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * @author He Hongdi
 * @version 1.0.0
 * @ClassName EventProducer.java
 * @Description 事件生产者
 * @createTime 2020/5/14 21:32
 */

@Component
public class EventProducer {

    private static final Logger logger = LoggerFactory.getLogger(EventProducer.class);
    private static final ObjectMapper objectMapper = CommunityUtil.getObjectMapper();

    @Autowired
    private KafkaTemplate kafkaTemplate;

    // 处理事件
    public void fireEvent(Event event){
        // 将事件发送到指定主题，其中把内容转换为json对象，消费者受到json后，可以将json转换成Event
        try {
            kafkaTemplate.send(event.getTopic(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            logger.error("序列化Event失败", e);
        }
    }
}
