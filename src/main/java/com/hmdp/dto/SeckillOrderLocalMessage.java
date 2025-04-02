package com.hmdp.dto;

import com.baomidou.mybatisplus.annotation.*;
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
     * 消息状态：0-待发送，1-发送失败，2-发送成功
     */
    private Integer status;

    /**
     * 消息内容（JSON格式存储订单关键信息）
     */
    private String content;

    /**
     * 下次发送时间
     */
    private Date nextSendTime;

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
