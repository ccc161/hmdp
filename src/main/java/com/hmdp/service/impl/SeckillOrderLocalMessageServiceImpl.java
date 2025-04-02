package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.SeckillOrderLocalMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillOrderLocalMessageMapper;
import com.hmdp.service.ISeckillOrderLocalMessageService;
import org.springframework.stereotype.Service;

import static com.hmdp.utils.MessageConstants.STATUS_PENDING;
import static com.hmdp.utils.MessageConstants.TYPE_SECKILL_ORDER;

@Service
public class SeckillOrderLocalMessageServiceImpl
        extends ServiceImpl<SeckillOrderLocalMessageMapper, SeckillOrderLocalMessage>
        implements ISeckillOrderLocalMessageService {
    @Override
    public SeckillOrderLocalMessage createMessage(VoucherOrder voucherOrder) {
        SeckillOrderLocalMessage message = new SeckillOrderLocalMessage();
        message.setMessageId(voucherOrder.getId());
        message.setBusinessType(TYPE_SECKILL_ORDER);
        message.setStatus(STATUS_PENDING);
        message.setContent(JSONUtil.toJsonStr(voucherOrder));
        message.setRetryCount(0);
        message.setVersion(0);
        return message;
    }
}
