/**
 * Copyright © 2016-2020 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.actors;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.msg.TbActorMsg;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 用于actor模型之间的消息传送的载体，mailbox
 * 每个Actor都有一个MailBox,其会检查MailBox并处理消息。
 * MailBox内部采用的是FIFO队列来存储消息，有一点不同的是，现实中我们的最新邮件
 * 会在邮箱的最前面。
 */
@Slf4j
@Data
public final class TbActorMailbox implements TbActorCtx {
    private static final boolean HIGH_PRIORITY = true;
    private static final boolean NORMAL_PRIORITY = false;

    private static final boolean FREE = false;
    private static final boolean BUSY = true;

    private static final boolean NOT_READY = false;
    private static final boolean READY = true;

    private final TbActorSystem system;
    private final TbActorSystemSettings settings;
    private final TbActorId selfId;
    private final TbActorRef parentRef;
    private final TbActor actor;
    private final Dispatcher dispatcher;
    private final ConcurrentLinkedQueue<TbActorMsg> highPriorityMsgs = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TbActorMsg> normalPriorityMsgs = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean busy = new AtomicBoolean(FREE);
    private final AtomicBoolean ready = new AtomicBoolean(NOT_READY);
    private final AtomicBoolean destroyInProgress = new AtomicBoolean();

    public void initActor() {
        dispatcher.getExecutor().execute(() -> tryInit(1));
//        同样的效果
//        dispatcher.getExecutor().execute(new Runnable() {
//            @Override
//            public void run() {
//                tryInit(1);
//            }
//        });
    }

    // 执行初始化，或者处理队列中遗留的消息
    private void tryInit(int attempt) {
        try {
            log.debug("[{}] Trying to init actor, attempt: {}", selfId, attempt);
            // 如果get的值为false，是的--> new AtomicBoolean() 默认值就是false
            if (!destroyInProgress.get()) {
                // 赋值给Abstract
                actor.init(this);
                if (!destroyInProgress.get()) {
                    ready.set(READY);
                    tryProcessQueue(false);
                }
            }
        } catch (Throwable t) {
            log.debug("[{}] Failed to init actor, attempt: {}", selfId, attempt, t);
            int attemptIdx = attempt + 1;
            InitFailureStrategy strategy = actor.onInitFailure(attempt, t);
            if (strategy.isStop() || (settings.getMaxActorInitAttempts() > 0 && attemptIdx > settings.getMaxActorInitAttempts())) {
                log.info("[{}] Failed to init actor, attempt {}, going to stop attempts.", selfId, attempt, t);
                system.stop(selfId);
            } else if (strategy.getRetryDelay() > 0) {
                log.info("[{}] Failed to init actor, attempt {}, going to retry in attempts in {}ms", selfId, attempt, strategy.getRetryDelay());
                log.debug("[{}] Error", selfId, t);
                system.getScheduler().schedule(() -> dispatcher.getExecutor().execute(() -> tryInit(attemptIdx)), strategy.getRetryDelay(), TimeUnit.MILLISECONDS);
            } else {
                log.info("[{}] Failed to init actor, attempt {}, going to retry immediately", selfId, attempt);
                log.debug("[{}] Error", selfId, t);

//                Dispatcher从ActorRef中获取消息并传递给MailBox,Dispatcher封装了一个线程池，之后在
//                线程池中执行MailBox。
                dispatcher.getExecutor().execute(() -> tryInit(attemptIdx));
            }
        }
    }

    private void enqueue(TbActorMsg msg, boolean highPriority) {
        if (highPriority) {
            highPriorityMsgs.add(msg);
        } else {
            normalPriorityMsgs.add(msg);
        }
        tryProcessQueue(true);
    }

    private void tryProcessQueue(boolean newMsg) {
        if (ready.get() == READY) {
            if (newMsg || !highPriorityMsgs.isEmpty() || !normalPriorityMsgs.isEmpty()) {
                if (busy.compareAndSet(FREE, BUSY)) {
                    dispatcher.getExecutor().execute(this::processMailbox);
                } else {
                    log.trace("[{}] MessageBox is busy, new msg: {}", selfId, newMsg);
                }
            } else {
                log.trace("[{}] MessageBox is empty, new msg: {}", selfId, newMsg);
            }
        } else {
            log.trace("[{}] MessageBox is not ready, new msg: {}", selfId, newMsg);
        }
    }

    /**
     * 处理消息 --> 指定的actor的process方法实现消息处理，这里验证了actor模型之间仅仅
     * 通过消息改变状态
     */
    private void processMailbox() {
        boolean noMoreElements = false;
        for (int i = 0; i < settings.getActorThroughput(); i++) {
            TbActorMsg msg = highPriorityMsgs.poll();
            if (msg == null) {
                msg = normalPriorityMsgs.poll();
            }
            if (msg != null) {
                try {
                    log.debug("[{}] Going to process message: {}", selfId, msg);
                    actor.process(msg);
                } catch (Throwable t) {
                    log.debug("[{}] Failed to process message: {}", selfId, msg, t);
                    ProcessFailureStrategy strategy = actor.onProcessFailure(t);
                    if (strategy.isStop()) {
                        system.stop(selfId);
                    }
                }
            } else {
                noMoreElements = true;
                break;
            }
        }
        if (noMoreElements) {
            busy.set(FREE);
            dispatcher.getExecutor().execute(() -> tryProcessQueue(false));
        } else {
            dispatcher.getExecutor().execute(this::processMailbox);
        }
    }

    @Override
    public TbActorId getSelf() {
        return selfId;
    }

    @Override
    public void tell(TbActorId target, TbActorMsg actorMsg) {
        system.tell(target, actorMsg);
    }

    @Override
    public void broadcastToChildren(TbActorMsg msg) {
        system.broadcastToChildren(selfId, msg);
    }

    @Override
    public void broadcastToChildren(TbActorMsg msg, Predicate<TbActorId> childFilter) {
        system.broadcastToChildren(selfId, childFilter, msg);
    }

    @Override
    public List<TbActorId> filterChildren(Predicate<TbActorId> childFilter) {
        return system.filterChildren(selfId, childFilter);
    }

    @Override
    public void stop(TbActorId target) {
        system.stop(target);
    }

    @Override
    public TbActorRef getOrCreateChildActor(TbActorId actorId, Supplier<String> dispatcher, Supplier<TbActorCreator> creator) {
        TbActorRef actorRef = system.getActor(actorId);
        if (actorRef == null) {
            return system.createChildActor(dispatcher.get(), creator.get(), selfId);
        } else {
            return actorRef;
        }
    }

    public void destroy() {
        destroyInProgress.set(true);
        dispatcher.getExecutor().execute(() -> {
            try {
                ready.set(NOT_READY);
                actor.destroy();
            } catch (Throwable t) {
                log.warn("[{}] Failed to destroy actor: {}", selfId, t);
            }
        });
    }

    @Override
    public TbActorId getActorId() {
        return selfId;
    }

    @Override
    public void tell(TbActorMsg actorMsg) {
        enqueue(actorMsg, NORMAL_PRIORITY);
    }

    @Override
    public void tellWithHighPriority(TbActorMsg actorMsg) {
        enqueue(actorMsg, HIGH_PRIORITY);
    }

}
