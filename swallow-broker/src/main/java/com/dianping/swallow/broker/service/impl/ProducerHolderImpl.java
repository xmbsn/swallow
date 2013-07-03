package com.dianping.swallow.broker.service.impl;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dianping.swallow.broker.monitor.NotifyService;
import com.dianping.swallow.broker.service.ProducerHolder;
import com.dianping.swallow.common.internal.config.ConfigChangeListener;
import com.dianping.swallow.common.internal.config.DynamicConfig;
import com.dianping.swallow.common.message.Destination;
import com.dianping.swallow.common.producer.exceptions.RemoteServiceInitFailedException;
import com.dianping.swallow.producer.Producer;
import com.dianping.swallow.producer.ProducerConfig;
import com.dianping.swallow.producer.ProducerMode;
import com.dianping.swallow.producer.impl.ProducerFactoryImpl;

@Service
public class ProducerHolderImpl implements ProducerHolder, ConfigChangeListener {
    private static final String   TOPIC       = "topic";

    private static final Logger   LOG         = LoggerFactory.getLogger(ProducerHolderImpl.class);

    private Map<String, Producer> producerMap = new ConcurrentHashMap<String, Producer>();

    @Autowired
    private DynamicConfig         dynamicConfig;

    @Autowired
    private NotifyService         notifyService;

    @PostConstruct
    public void init() throws RemoteServiceInitFailedException {
        //<topic>,<topic>
        String topicStr = dynamicConfig.get(TOPIC);
        String[] topics = StringUtils.split(topicStr, ';');
        LOG.info("Initing producers with topics(" + Arrays.toString(topics) + ")");

        //每个topic创建一个producer(生产者是可以并发使用的)
        if (topics != null) {
            for (String topic : topics) {
                initializeProducer(topic);
            }
        }

        //监听lion
        dynamicConfig.addConfigChangeListener(this);

        LOG.info("Producer map" + producerMap);
    }

    private void initializeProducer(String topic) throws RemoteServiceInitFailedException {
        ProducerConfig config = new ProducerConfig();
        config.setMode(ProducerMode.SYNC_MODE);
        Producer producer = ProducerFactoryImpl.getInstance().createProducer(Destination.topic(topic), config);
        if (!producerMap.containsKey(topic)) {//不存在该topic的producer时，才会添加改Producer
            producerMap.put(topic, producer);
        }
    }

    @Override
    public Producer getProducer(String topic) {
        return producerMap.get(topic);
    }

    @Override
    public void onConfigChange(String key, String value) {
        if (StringUtils.equals(key, TOPIC)) {
            String[] topics = StringUtils.split(value, ';');
            LOG.info("Initing producers with topics(" + Arrays.toString(topics) + ")");

            //每个topic创建一个producer(生产者是可以并发使用的)
            if (topics != null) {
                for (String topic : topics) {
                    try {
                        initializeProducer(topic);
                    } catch (RemoteServiceInitFailedException e) {
                        notifyService.alarm("Error initialize producer ", e, true);
                    }
                }
            }

            LOG.info("Producer map" + producerMap);
        }
    }

}