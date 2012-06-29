/**
 * Project: swallow-producerclient
 * 
 * File Created at 2012-6-27
 * $Id$
 * 
 * Copyright 2010 dianping.com.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Dianping Company. ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with dianping.com.
 */
package com.dianping.swallow.producer.examples;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dianping.swallow.producer.ProducerMode;
import com.dianping.swallow.producer.ProducerOptionKey;
import com.dianping.swallow.producer.impl.ProducerImpl;
import com.dianping.swallow.producer.impl.ProducerFactoryImpl;

class ExampleTask implements Runnable {
   ProducerImpl producer;
   public ExampleTask(ProducerImpl producer){
      this.producer = producer;
   }
   @Override
   public void run() {
      try {
         producer.sendMessage(producer.getDestination());
      } catch (Exception e) {
         System.out.println("Got problems.");
      }
   }
   
}
/**
 * 多线程调用不同类型的Producer示例
 * 
 * @author tong.song
 */
public class ProducerMultipleThread {
   public static void main(String[] args) {
      ProducerFactoryImpl producerFactory = null;
      ExecutorService threadPool = Executors.newCachedThreadPool();
      //获取Producer工厂实例
      try {
         //Or: producerFactory = ProducerFactory.getInstance()//默认远程调用timeout为5000;
         producerFactory = ProducerFactoryImpl.getInstance(5000);
      } catch (Exception e) {
         System.out.println(e.toString());
      }
      //设置Producer选项
      Map<ProducerOptionKey, Object> pOptions = new HashMap<ProducerOptionKey, Object>();
      pOptions.put(ProducerOptionKey.PRODUCER_MODE, ProducerMode.ASYNC_MODE);
      pOptions.put(ProducerOptionKey.ASYNC_THREAD_POOL_SIZE, 10);
      pOptions.put(ProducerOptionKey.ASYNC_IS_CONTINUE_SEND, false);
      
      ProducerImpl producerAsync = null;
      //获取Producer实例
      try {
         producerAsync = producerFactory.getProducer("sampleAsync", pOptions);
      } catch (Exception e) {
         System.out.println(e.toString());
      }
      
      //重新配置Producer选项
      pOptions.put(ProducerOptionKey.PRODUCER_MODE, ProducerMode.SYNC_MODE);
      ProducerImpl producerSync = null;
      
      //获取Producer实例
      try {
         producerSync = producerFactory.getProducer("sampleSync", pOptions);
      } catch (Exception e) {
         System.out.println(e.toString());
      }
      
      threadPool.execute(new ExampleTask(producerAsync));
      threadPool.execute(new ExampleTask(producerSync));
   }
}