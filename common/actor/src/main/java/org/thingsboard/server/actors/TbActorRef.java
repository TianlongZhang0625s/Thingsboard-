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

/**
 * 可以理解为代理
 * 当ActorRef发送消息调用目标Actor的reveive方法时，MailBox中的run方法被执行，接着从消息队列中取出一条消息并传递给Actor处理
 * 在这里具体指process方法来进行执行。
 */
public interface TbActorRef {

    TbActorId getActorId();

    void tell(TbActorMsg actorMsg);

    void tellWithHighPriority(TbActorMsg actorMsg);

}
