# netty-mqtt 模块分析

## MqttClientConfig 类
要启动一个应用首先需要配置，所以先看下MqttClientConfig配置类，其中定义的字段如下：
```java
    // ssl认证的内容
    private final SslContext sslContext;
    // 随机分配的客户端id
    private final String randomClientId;

    private String clientId;
    // 超时
    private int timeoutSeconds = 60;
    private MqttVersion protocolVersion = MqttVersion.MQTT_3_1;
    @Nullable private String username = null;
    @Nullable private String password = null;
    private boolean cleanSession = true;
    @Nullable private MqttLastWill lastWill;
    private Class<? extends Channel> channelClass = NioSocketChannel.class;

    private boolean reconnect = true;
    private long reconnectDelay = 1L;
    private int maxBytesInMessage = 8092;
```
配置类的形式在很多的开源框架中都存在，例如spring、zookeeper等都会有一个配置类，这个作为我们的入口分析下这个模块：

默认配置如下：
```java
    public MqttClientConfig() {
        this(null);
    }

    public MqttClientConfig(SslContext sslContext) {
        this.sslContext = sslContext;
        Random random = new Random();
        String id = "netty-mqtt/";
        String[] options = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".split("");
        for(int i = 0; i < 8; i++){
            id += options[random.nextInt(options.length)];
        }
        this.clientId = id;
        this.randomClientId = id;
    }
```
可以看到id产生是从上面的字符串中通过随机出来的数字作为索引然后去组装一个8位的clientId。
默认情况下clientId和默认随机生成的randomClientid是一致的。
剩余的都是些getter和setter方法，就不在细说了，在这需要注意下：
```java
    public void setMaxBytesInMessage(int maxBytesInMessage) {
        if (maxBytesInMessage <= 0 || maxBytesInMessage > 256_000_000) {
            throw new IllegalArgumentException("maxBytesInMessage must be > 0 or < 256_000_000");
        }
        this.maxBytesInMessage = maxBytesInMessage;
    }
```
这里可以看到消息的大小是有限制的。
## MqttClient 接口
定义了MqttClient的所有行为：
```java
public interface MqttClient {
    Future<MqttConnectResult> connect(String host);
    Future<MqttConnectResult> connect(String host, int port);
    boolean isConnected();
    Future<MqttConnectResult> reconnect();
    EventLoopGroup getEventLoop();
    void setEventLoop(EventLoopGroup eventLoop);
    Future<Void> on(String topic, MqttHandler handler);
    Future<Void> on(String topic, MqttHandler handler, MqttQoS qos);
    Future<Void> once(String topic, MqttHandler handler);
    Future<Void> once(String topic, MqttHandler handler, MqttQoS qos);
    Future<Void> off(String topic, MqttHandler handler);
    Future<Void> off(String topic);
    Future<Void> publish(String topic, ByteBuf payload);
    Future<Void> publish(String topic, ByteBuf payload, MqttQoS qos);
    Future<Void> publish(String topic, ByteBuf payload, boolean retain);
    Future<Void> publish(String topic, ByteBuf payload, MqttQoS qos, boolean retain);
    MqttClientConfig getClientConfig();
    static MqttClient create(MqttClientConfig config, MqttHandler defaultHandler){
        return new MqttClientImpl(config, defaultHandler);
    }
    void disconnect();
    void setCallback(MqttClientCallback callback);

}
```
## MqttConnectResult
此类定义了mqtt链接的结果，由于整个这个MqttClient都是基于netty实现的，所以里面用了很多的future模式：
此结果消息类仅定义了是否成功，返回状态码：
```java
    private final boolean success;
    private final MqttConnectReturnCode returnCode;
    private final ChannelFuture closeFuture;
```
## MqttClientCallback接口
由于netty是异步调用的方式，所以有些操作可以在发送请求后继续以异步的方式处理其他的逻辑，所以需要定义一个MqttClientCallback接口：
此接口定义了两个行为：
```java
public interface MqttClientCallback {
    void connectionLost(Throwable cause);
    void onSuccessfulReconnect();
}
```
## MqttHandler
Handler和netty中的理解一样，也和spring中handler的理解类似，也就是说其是通过责任链模式完成消息的处理，完成在消息处理连路上
不同状态下消息的处理。
```java
public interface MqttHandler {

    void onMessage(String topic, ByteBuf payload);
}
```
所以其定的仅有一个onMessage方法，也就是说，在得到消息其进行的处理，本质上是基于topic进行处理的。
## 主角 MqttClientImpl 类
对于具体字段的解析，可参考对应的源码：MqttClientImpl.java
默认的MqttClient实现需要传入一个MqttHandler来处理，对于配置来说，则可以使用默认配置即可。
```java
    public MqttClientImpl(MqttHandler defaultHandler) {
        this.clientConfig = new MqttClientConfig();
        this.defaultHandler = defaultHandler;
    }
```
对于连接connect方法则需要传入一个host地址，默认端口为1883，且默认重连为不重连：
```java
    public Future<MqttConnectResult> connect(String host) {
        return connect(host, 1883);
    }

    public Future<MqttConnectResult> connect(String host, int port) {
        return connect(host, port, false);
    }
```

### connect连接过程：
```java
    private Future<MqttConnectResult> connect(String host, int port, boolean reconnect) {
        if (this.eventLoop == null) {
            this.eventLoop = new NioEventLoopGroup();
        }
        this.host = host;
        this.port = port;
        Promise<MqttConnectResult> connectFuture = new DefaultPromise<>(this.eventLoop.next());
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(this.eventLoop);
        bootstrap.channel(clientConfig.getChannelClass());
        bootstrap.remoteAddress(host, port);
        bootstrap.handler(new MqttChannelInitializer(connectFuture, host, port, clientConfig.getSslContext()));
        ChannelFuture future = bootstrap.connect();

        future.addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                MqttClientImpl.this.channel = f.channel();
                MqttClientImpl.this.channel.closeFuture().addListener((ChannelFutureListener) channelFuture -> {
                    if (isConnected()) {
                        return;
                    }
                    ChannelClosedException e = new ChannelClosedException("Channel is closed!");
                    if (callback != null) {
                        callback.connectionLost(e);
                    }
                    pendingSubscriptions.clear();
                    serverSubscriptions.clear();
                    subscriptions.clear();
                    pendingServerUnsubscribes.clear();
                    qos2PendingIncomingPublishes.clear();
                    pendingPublishes.clear();
                    pendingSubscribeTopics.clear();
                    handlerToSubscribtion.clear();
                    scheduleConnectIfRequired(host, port, true);
                });
            } else {
                scheduleConnectIfRequired(host, port, reconnect);
            }
        });
        return connectFuture;
    }
```