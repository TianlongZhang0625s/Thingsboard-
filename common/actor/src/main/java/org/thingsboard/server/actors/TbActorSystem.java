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

import org.thingsboard.server.common.msg.TbActorMsg;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;

/**
 * actor模型整体的行为，调度，分配，创建父子actor，停止actor，向所有actor广播，过滤子actor
 * 调用的juc包中的工具实现
 * ActorSystem作为顶级Actor，可以创建和停止Actors,甚至可关闭整个Actor环境，
 * 此外Actors是按层次划分的，ActorSystem就好比Java中的Object对象，Scala中的Any，
 * 是所有Actors的根，当你通过ActorSystem的actof方法创建Actor时，实际就是在ActorSystem
 * 下创建了一个子Actor。
 */
public interface TbActorSystem {

    ScheduledExecutorService getScheduler();

    void createDispatcher(String dispatcherId, ExecutorService executor);

    void destroyDispatcher(String dispatcherId);

    TbActorRef getActor(TbActorId actorId);

    /**
     * ActorSystem通过actorOf创建Actor，但其并不返回TeacherActor而是返
     * 回一个类型为ActorRef的东西。
     * ActorRef作为Actor的代理，使得客户端并不直接与Actor对话，这种Actor
     * 模型也是为了避免Actor的自定义/私有方法或变量被直接访问，所
     * 以你最好将消息发送给ActorRef，由它去传递给目标Actor
     */
    TbActorRef createRootActor(String dispatcherId, TbActorCreator creator);

    TbActorRef createChildActor(String dispatcherId, TbActorCreator creator, TbActorId parent);

    void tell(TbActorId target, TbActorMsg actorMsg);

    void tellWithHighPriority(TbActorId target, TbActorMsg actorMsg);

    void stop(TbActorRef actorRef);

    void stop(TbActorId actorId);

    void stop();

    void broadcastToChildren(TbActorId parent, TbActorMsg msg);

    void broadcastToChildren(TbActorId parent, Predicate<TbActorId> childFilter, TbActorMsg msg);

    List<TbActorId> filterChildren(TbActorId parent, Predicate<TbActorId> childFilter);
}
