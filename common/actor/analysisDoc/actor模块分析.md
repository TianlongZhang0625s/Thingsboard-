# Actor 模块分析
### 1.Actor模型
在使用Java进行并发编程时需要特别的关注锁和内存原子性等一系列线程问题，而Actor模型内部的状态由它自己维护即它内部数据只能由它自己修改(通过消息传递来进行状态修改)，所以使用Actors模型进行并发编程可以很好地避免这些问题，Actor由状态(state)、行为(Behavior)和邮箱(mailBox)三部分组成

状态(state)：Actor中的状态指的是Actor对象的变量信息，状态由Actor自己管理，避免了并发环境下的锁和内存原子性等问题
行为(Behavior)：行为指定的是Actor中计算逻辑，通过Actor接收到消息来改变Actor的状态
邮箱(mailBox)：邮箱是Actor和Actor之间的通信桥梁，邮箱内部通过FIFO消息队列来存储发送方Actor消息，接受方Actor从邮箱队列中获取消息
Actor的基础就是消息传递
###2.使用Actor模型的好处：
事件模型驱动--Actor之间的通信是异步的，即使Actor在发送消息后也无需阻塞或者等待就能够处理其他事情
强隔离性--Actor中的方法不能由外部直接调用，所有的一切都通过消息传递进行的，从而避免了Actor之间的数据共享，想要
观察到另一个Actor的状态变化只能通过消息传递进行询问
位置透明--无论Actor地址是在本地还是在远程机上对于代码来说都是一样的
轻量性--Actor是非常轻量的计算单机，单个Actor仅占400多字节，只需少量内存就能达到高并发
这个包中定义了与Actor相关的接口，以及其相应的默认实现。
在此模块中主要包括 TbActor，TbActorCreator，TbActorCtx，TbActorId，TbActorRef，TbActorSystem
## TBActor接口
```java
// 定义了基本的行为，也就是每个actor基本单元（每个线程）的行为。
public interface TbActor {

    boolean process(TbActorMsg msg);

    TbActorRef getActorRef();
    default void init(TbActorCtx ctx) throws TbActorException {
    }

    default void destroy() throws TbActorException {
    }
// 启动失败策略:延时重试
    default InitFailureStrategy onInitFailure(int attempt, Throwable t) {
        return InitFailureStrategy.retryWithDelay(5000 * attempt);
    }
// 处理过程失败策略：Error--->停止
    default ProcessFailureStrategy onProcessFailure(Throwable t) {
        if (t instanceof Error) {
            return ProcessFailureStrategy.stop();
        } else {
            return ProcessFailureStrategy.resume();
        }
    }
}

```
## TbActorCreator接口
```java
public interface TbActorCreator {

    TbActorId createActorId();

    TbActor createActor();

}
```
## 其他接口以及默认实现
### TbActorId 
有两种实现形式，TbEntityActorId和TbStringActorId。
分别实现了（复写）toString，equals和hashcode方法。便于根据对象内容确定是否对象唯一。

### dispatcher
```java
class Dispatcher {

    private final String dispatcherId;
    private final ExecutorService executor;

}
```
可以看到，这里其实是使用的线程池来实现处理，dispatcher可以理解为分发器。
dispatcherId可以理解为开启的一个新别名。
### TbActorRef、TbActorCtx
默认实现类为TbActorMailbox。具体的可以参考源码中对应的分析：
消息分为高优先级和普通优先级两种，均存储在concurrentHashMap中，从这里可以看出，这是为了
适应高并发的场景，所以在这里可以猜到会使用多线程编程实现。
```java
private final ConcurrentLinkedQueue<TbActorMsg> highPriorityMsgs = new ConcurrentLinkedQueue<>();
private final ConcurrentLinkedQueue<TbActorMsg> normalPriorityMsgs = new ConcurrentLinkedQueue<>();
```



