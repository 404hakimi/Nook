package com.nook.biz.operation.internal;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单 server 的内存槽位 = 一个 FIFO 队列 + 一个"是否已有 worker 在跑"标志.
 *
 * <p>workerActive 是互斥的唯一真相源: CAS(false→true) 成功的线程负责起 worker, 跑完队列后 CAS(true→false).
 *
 * @author nook
 */
class ServerSlot {

    final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    final AtomicBoolean workerActive = new AtomicBoolean(false);
}
