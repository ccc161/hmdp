package com.hmdp.utils;

/**
 * 订单状态常量定义
 * 包含订单全生命周期状态、退款状态及相关常量
 */
public final class OrderConstants {

    private OrderConstants() {
        // 防止实例化
    }

    // ================= 订单主状态 =================
    /** 订单已创建，待支付 */
    public static final int STATUS_CREATED = 0;
    /** 已支付，待发货 */
    public static final int STATUS_PAID = 1;
    /** 已发货，待收货 */
    public static final int STATUS_SHIPPED = 2;
    /** 已收货，订单完成 */
    public static final int STATUS_COMPLETED = 3;
    /** 订单已取消（未支付） */
    public static final int STATUS_CANCELED = 4;
    /** 订单已关闭（支付后取消） */
    public static final int STATUS_CLOSED = 5;
    /** 订单已过期（未支付超时） */
    public static final int STATUS_EXPIRED = 6;

    // ================= 退款/退货状态 =================
    /** 退款申请中 */
    public static final int REFUND_APPLYING = 10;
    /** 退款处理中 */
    public static final int REFUND_PROCESSING = 11;
    /** 退款成功 */
    public static final int REFUND_SUCCESS = 12;
    /** 退款失败 */
    public static final int REFUND_FAILED = 13;
    /** 退货申请中 */
    public static final int RETURN_APPLYING = 14;
    /** 退货处理中（待用户寄回） */
    public static final int RETURN_PROCESSING = 15;
    /** 退货已收货（待退款） */
    public static final int RETURN_RECEIVED = 16;
    /** 退货完成 */
    public static final int RETURN_COMPLETED = 17;
    /** 退货被拒绝 */
    public static final int RETURN_REJECTED = 18;

    // ================= 订单类型 =================
    /** 普通订单 */
    public static final int TYPE_NORMAL = 0;
    /** 秒杀订单 */
    public static final int TYPE_SECKILL = 1;
    /** 团购订单 */
    public static final int TYPE_GROUP_BUY = 2;
    /** 预售订单 */
    public static final int TYPE_PRE_SALE = 3;

    // ================= 订单操作 =================
    /** 创建订单 */
    public static final int OPERATION_CREATE = 0;
    /** 支付订单 */
    public static final int OPERATION_PAY = 1;
    /** 取消订单 */
    public static final int OPERATION_CANCEL = 2;
    /** 发货 */
    public static final int OPERATION_SHIP = 3;
    /** 确认收货 */
    public static final int OPERATION_CONFIRM = 4;
    /** 申请退款 */
    public static final int OPERATION_REFUND_APPLY = 5;
    /** 申请退货 */
    public static final int OPERATION_RETURN_APPLY = 6;

    // ================= 状态流转相关 =================
    /** 自动取消时间（分钟） */
    public static final long AUTO_CANCEL_TIME = 30;
    /** 自动确认收货时间（天） */
    public static final long AUTO_CONFIRM_DAYS = 7;
    /** 退款超时时间（天） */
    public static final long REFUND_TIMEOUT_DAYS = 7;

    /**
     * 判断订单是否已完成状态
     */
    public static boolean isFinalStatus(int status) {
        return status == STATUS_COMPLETED
                || status == STATUS_CANCELED
                || status == STATUS_CLOSED
                || status == STATUS_EXPIRED;
    }

    /**
     * 判断订单是否可以退款
     */
    public static boolean canRefund(int status) {
        return status == STATUS_PAID
                || status == STATUS_SHIPPED
                || status == STATUS_COMPLETED;
    }
}