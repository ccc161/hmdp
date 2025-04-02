package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.SeckillOrderMessageConsumption;
import com.hmdp.service.ISeckillOrderMessageConsumptionService;
import com.hmdp.mapper.SeckillOrderMessageConsumptionMapper;
import org.springframework.stereotype.Service;

@Service
public class SeckillOrderMessageConsumptionServiceImpl
        extends ServiceImpl<SeckillOrderMessageConsumptionMapper, SeckillOrderMessageConsumption>
        implements ISeckillOrderMessageConsumptionService {

}