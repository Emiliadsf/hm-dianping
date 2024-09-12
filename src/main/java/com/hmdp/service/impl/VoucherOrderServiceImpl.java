package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWork;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWork  redisIdWork;



    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {

        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        if (voucher.getStock()<1) {
            return Result.fail("库存不足");

        }

        boolean success= seckillVoucherService
                .update().
                setSql("stock=stock-1")
                .eq("voucher_id",voucher).gt("stock",0)
                .update();

        if (!success){
            return Result.fail("fail");
        }
        //一人一单
        Long userid = UserHolder.getUser().getId();
        synchronized (userid.toString()) {
            return createVoucherOrder(voucherId);
        }
    }
    @Transactional
    public synchronized Result createVoucherOrder(Long voucherId) {
        Long userid = UserHolder.getUser().getId();

            int count = query().eq("user_id", userid).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("已经购买过了");
            }
            boolean success = seckillVoucherService
                    .update().
                    setSql("stock=stock-1")
                    .eq("voucher_id", voucherId).gt("stock", 0)
                    .update();

            if (!success) {
                return Result.fail("fail");
            }
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWork.nextId("order");
            voucherOrder.setId(orderId);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(orderId);
        }

}
