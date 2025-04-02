package com.hmdp.utils;

/**
 * 消息系统常量定义
 * 包含消息类型、状态、优先级、重试策略等常量
 */
public final class MessageConstants {

    private MessageConstants() {
        // 防止实例化
    }

    // ================= 消息类型 =================
    /** 秒杀订单消息 */
    public static final int TYPE_SECKILL_ORDER = 1;
    /** 普通订单消息 */
    public static final int TYPE_NORMAL_ORDER = 0;
    /** 支付消息 */
    public static final int TYPE_PAYMENT = 2;
    /** 退款消息 */
    public static final int TYPE_REFUND = 3;
    /** 库存消息 */
    public static final int TYPE_INVENTORY = 4;
    /** 物流消息 */
    public static final int TYPE_LOGISTICS = 5;
    /** 系统通知消息 */
    public static final int TYPE_SYSTEM_NOTIFICATION = 6;

    // ================= 消息状态 =================
    /** 待发送 */
    public static final int STATUS_PENDING = 0;
    /** 发送成功 */
    public static final int STATUS_SEND_SUCCESSFUL = 1;
    /** 发送失败 */
    public static final int STATUS_SEND_FAILED = 2;
    /** 接收处理中 */
    public static final int STATUS_RECEIVE_PROCESSING = 3;
    /** 接收处理成功 */
    public static final int STATUS_RECEIVE_PROCESS_SUCCESS = 4;
    /** 接收处理失败 */
    public static final int STATUS_RECEIVE_PROCESS_FAILED = 5;
    /** 消息已过期 */
    public static final int STATUS_EXPIRED = 6;
    /** 消息已取消 */
    public static final int STATUS_CANCELED = 7;
    /** 等待重试 */
    public static final int STATUS_WAITING_RETRY = 8;

    // ================= 消息优先级 =================
    /** 最低优先级 */
    public static final int PRIORITY_LOWEST = 0;
    /** 低优先级 */
    public static final int PRIORITY_LOW = 1;
    /** 普通优先级 */
    public static final int PRIORITY_NORMAL = 2;
    /** 高优先级 */
    public static final int PRIORITY_HIGH = 3;
    /** 最高优先级 */
    public static final int PRIORITY_HIGHEST = 4;

    // ================= 重试策略 =================
    /** 不重试 */
    public static final int RETRY_POLICY_NONE = 0;
    /** 固定间隔重试 */
    public static final int RETRY_POLICY_FIXED = 1;
    /** 指数退避重试 */
    public static final int RETRY_POLICY_BACKOFF = 2;

    /** 默认最大重试次数 */
    public static final int DEFAULT_MAX_RETRY_TIMES = 3;
    /** 默认重试间隔(毫秒) */
    public static final long DEFAULT_RETRY_INTERVAL = 5000L;

    // ================= 消息队列相关 =================
    /** 默认队列名称 */
    public static final String DEFAULT_QUEUE_NAME = "default_queue";
    /** 死信队列名称 */
    public static final String DEAD_LETTER_QUEUE = "dead_letter_queue";
    /** 延迟队列名称 */
    public static final String DELAY_QUEUE = "delay_queue";

    // ================= 消息过期时间 =================
    /** 默认消息过期时间(毫秒) */
    public static final long DEFAULT_EXPIRE_TIME = 86400000L; // 24小时
    /** 秒杀消息过期时间(毫秒) */
    public static final long SECKILL_EXPIRE_TIME = 1800000L; // 30分钟
    /** 即时消息过期时间(毫秒) */
    public static final long IMMEDIATE_EXPIRE_TIME = 600000L; // 10分钟

    // ================= 错误码 =================
    /** 成功 */
    public static final int ERROR_CODE_SUCCESS = 0;
    /** 消息格式错误 */
    public static final int ERROR_CODE_INVALID_FORMAT = 1001;
    /** 消息重复 */
    public static final int ERROR_CODE_DUPLICATE = 1002;
    /** 消息过期 */
    public static final int ERROR_CODE_EXPIRED = 1003;
    /** 队列已满 */
    public static final int ERROR_CODE_QUEUE_FULL = 1004;
    /** 处理超时 */
    public static final int ERROR_CODE_TIMEOUT = 1005;
}