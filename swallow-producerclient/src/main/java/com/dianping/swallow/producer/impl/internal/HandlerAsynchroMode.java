package com.dianping.swallow.producer.impl.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dianping.dpsf.exception.NetException;
import com.dianping.filequeue.DefaultFileQueueConfig.FileQueueConfigHolder;
import com.dianping.filequeue.DefaultFileQueueImpl;
import com.dianping.filequeue.FileQueue;
import com.dianping.filequeue.FileQueueClosedException;
import com.dianping.swallow.common.internal.packet.Packet;
import com.dianping.swallow.common.internal.producer.MQService;
import com.dianping.swallow.common.internal.threadfactory.DefaultPullStrategy;
import com.dianping.swallow.common.internal.threadfactory.MQThreadFactory;
import com.dianping.swallow.common.producer.exceptions.ServerDaoException;
import com.dianping.swallow.producer.impl.ProducerFactoryImpl;

/**
 * Producer的异步模式消息处理类
 * 
 * @author tong.song
 */
public class HandlerAsynchroMode {
   private static final Logger          logger                 = LoggerFactory.getLogger(ProducerImpl.class);
   private static final MQThreadFactory threadFactory          = new MQThreadFactory();

   private static final int             DEFAULT_FILEQUEUE_SIZE = 512 * 1024 * 1024;

   private ProducerImpl                 producer;
   private FileQueue<Packet>            messageQueue;                                                               //filequeue

   private int                          delayBase              = ProducerFactoryImpl.getRemoteServiceTimeout();
   private DefaultPullStrategy          defaultPullStrategy    = new DefaultPullStrategy(delayBase, 10 * delayBase);

   //构造函数
   public HandlerAsynchroMode(ProducerImpl producer) {
      FileQueueConfigHolder fileQueueConfig = new FileQueueConfigHolder();
      this.producer = producer;
      fileQueueConfig.setMaxDataFileSize(DEFAULT_FILEQUEUE_SIZE);
      messageQueue = new DefaultFileQueueImpl<Packet>(fileQueueConfig, producer.getDestination().getName(),
            !producer.isContinueSend());
      this.start();
   }

   //对外的接口//异步处理只需将pkt放入filequeue即可，放入失败抛出异常
   public void doSendMsg(Packet pkt) throws FileQueueClosedException {
      messageQueue.add(pkt);
   }

   //启动处理线程
   private void start() {
      int idx;
      int threadPoolSize = producer.getThreadPoolSize();
      for (idx = 0; idx < threadPoolSize; idx++) {
         threadFactory.newThread(new TskGetAndSend(), "AsyncProducer_" + idx).start();
      }
   }

   //从filequeue队列获取并发送Message的runnable
   private class TskGetAndSend implements Runnable {

      private final int defaultRetryTimes = producer.getRetryTimes();
      private int       leftRetryTimes    = defaultRetryTimes;
      private Packet    message           = null;
      private MQService remoteService     = producer.getRemoteService();

      @Override
      public void run() {
         while (true) {
            //从filequeue获取message，如果filequeue无元素则阻塞            
            message = messageQueue.get();
            //发送message，重试次数从Producer获取
            for (leftRetryTimes = defaultRetryTimes; leftRetryTimes > 0; leftRetryTimes--) {
               try {
                  remoteService.sendMessage(message);
               } catch (ServerDaoException e) {
                  //如果剩余重试次数<=1，将终止重试，记日志。
                  if (leftRetryTimes <= 1) {
                     logger.error("[AsyncHandler]:[Message sent failed.][Reason=DAO]", e);
                  }
                  try {
                     defaultPullStrategy.fail(true);
                  } catch (InterruptedException ie) {
                     //睡眠失败则不睡眠直接发送
                  }
                  //发送失败，重发
                  continue;
               } catch (NetException e) {
                  if (leftRetryTimes <= 1) {
                     logger.error("[AsyncHandler]:[Message sent failed.][Reason=Network]", e);
                  }
                  try {
                     defaultPullStrategy.fail(true);
                  } catch (InterruptedException ie) {
                     //睡眠失败则不睡眠直接发送
                  }
                  //发送失败，重发
                  continue;
               } catch (Exception e) {
                  try {
                     defaultPullStrategy.fail(true);
                  } catch (InterruptedException ie) {
                     //睡眠失败则不睡眠直接发送
                  }
                  //捕获到未知异常，不管
                  continue;
               }
               defaultPullStrategy.succeess();
               //如果发送成功则跳出循环//TODO 需要日志记录消息重发的次数吗？发送成功需要记日志吗？
               break;
            }
         }
      }
   }
}