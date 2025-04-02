package com.hmdp.dto;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Builder
@TableName("tb_seckill_order_message_consumption")
public class SeckillOrderMessageConsumption {

    @TableId(value = "message_id", type = IdType.INPUT) // 主键，手动输入
    private String messageId;

    private String orderId;

    private String productId;

    private Integer quantity;

    private Integer processStatus;

    private Integer retryCount;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @Version
    private Integer version;
}