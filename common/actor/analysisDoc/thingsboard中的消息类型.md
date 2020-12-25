# 消息类型

源码分析
```java
public enum MsgType {

   /**
    *partition位置改变消息，表明存储消息的partition改变
    */
    PARTITION_CHANGE_MSG,

    APP_INIT_MSG,

    /**
     * ADDED/UPDATED/DELETED events for main entities.
     *
     */
    COMPONENT_LIFE_CYCLE_MSG,

    /**
     * Misc messages consumed from the Queue and forwarded to Rule Engine Actor.
     *
     */
    QUEUE_TO_RULE_ENGINE_MSG,

    /**
     * Message that is sent by RuleChainActor to RuleActor with command to process TbMsg.
     */
    RULE_CHAIN_TO_RULE_MSG,

    /**
     * Message that is sent by RuleChainActor to other RuleChainActor with command to process TbMsg.
     */
    RULE_CHAIN_TO_RULE_CHAIN_MSG,

    /**
     * Message that is sent by RuleActor to RuleChainActor with command to process TbMsg by next nodes in chain.
     */
    RULE_TO_RULE_CHAIN_TELL_NEXT_MSG,

    /**
     * Message forwarded from original rule chain to remote rule chain due to change in the cluster structure or originator entity of the TbMsg.
     */
    REMOTE_TO_RULE_CHAIN_TELL_NEXT_MSG,

    /**
     * Message that is sent by RuleActor implementation to RuleActor itself to log the error.
     */
    RULE_TO_SELF_ERROR_MSG,

    /**
     * Message that is sent by RuleActor implementation to RuleActor itself to process the message.
     */
    RULE_TO_SELF_MSG,

    DEVICE_ATTRIBUTES_UPDATE_TO_DEVICE_ACTOR_MSG,

    DEVICE_CREDENTIALS_UPDATE_TO_DEVICE_ACTOR_MSG,

    DEVICE_NAME_OR_TYPE_UPDATE_TO_DEVICE_ACTOR_MSG,

    DEVICE_RPC_REQUEST_TO_DEVICE_ACTOR_MSG,

    SERVER_RPC_RESPONSE_TO_DEVICE_ACTOR_MSG,

    DEVICE_ACTOR_SERVER_SIDE_RPC_TIMEOUT_MSG,

    /**
     * Message that is sent from the Device Actor to Rule Engine. Requires acknowledgement
     */

    SESSION_TIMEOUT_MSG,

    STATS_PERSIST_TICK_MSG,

    STATS_PERSIST_MSG,

    /**
     * Message that is sent by TransportRuleEngineService to Device Actor. Represents messages from the device itself.
     */
    TRANSPORT_TO_DEVICE_ACTOR_MSG;

}

```