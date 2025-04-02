package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_seckill_order_local_message")
public class SeckillOrderLocalMessage {
    /**
     * 消息唯一ID（建议与订单ID一致）
     */
    @TableId(type = IdType.INPUT)
    private Long messageId;

    /**
     * 业务类型（1-秒杀订单）
     */
    private Integer businessType;

    /**
     * 消息状态：0-创建成功 1-MQ发送成功 2-MQ发送失败 3-处理中 4-处理成功 5-处理失败
     */
    private Integer status;

    /**
     * 消息内容（JSON格式存储订单关键信息）
     */
    private String content;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 最后更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

}
