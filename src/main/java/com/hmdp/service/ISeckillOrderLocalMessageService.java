package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.SeckillOrderLocalMessage;
import com.hmdp.entity.VoucherOrder;

public interface ISeckillOrderLocalMessageService extends IService<SeckillOrderLocalMessage> {
    public SeckillOrderLocalMessage createMessage(VoucherOrder voucherOrder);
}