package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.beans.Transient;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        //Shop shop = queryWithPassThrough(id);


        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("用户不存在");
        }
        return Result.ok(shop);
    }
//缓存击穿
    public Shop queryWithMutex(Long id){
        String key="cache:shop:"+id;
        //取redis里找
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //如果存在就反序列化，返回对象
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson !=null){
            return null;
        }

        //实现缓存重建
        String locKey="lock:shop:"+id;
        Shop shop = null;
        try {
            boolean isLock=tryLock(locKey);
            if(!isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //不在redis就去数据库获取
            shop = getById(id);
            Thread.sleep(200);
            //都不在返回错误
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key,"",2, TimeUnit.MINUTES);

                return null;
            }
            //在数据库找到就写入redis中再返回对象
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);

            unlock(locKey);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unlock(locKey);
        }

        return shop;
    }
//线程池
    private static final ExecutorService CACHE_REBUILD = Executors.newFixedThreadPool(10);

//逻辑过期缓存击穿
public Shop queryWithLogicalExpire(Long id){
    String key="cache:shop:"+id;
    //取redis里找
    String shopJson = stringRedisTemplate.opsForValue().get(key);
    //如果不存在就返回空
    if (StrUtil.isBlank(shopJson)) {
        return null;
    }

    //命中，将json反序列化成对象
    RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
    LocalDateTime expireTime = redisData.getExpireTime();
    //判断是否过期
    if (expireTime.isAfter(LocalDateTime.now())) {
        return shop;
    }
    String lockKey="lock:shop:"+id;
    boolean isLock = tryLock(lockKey);
    if(isLock){
            //成功开启独立线程
        CACHE_REBUILD.submit(()->{
            try {
                this.saveShop2Redis(id,20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }finally {
                unlock(lockKey);
            }

        });
    }
    return shop;

}
public void saveShop2Redis(Long id ,Long expireSeconds) throws InterruptedException {
    Shop shop = getById(id);
    Thread.sleep(200);
    RedisData redisData=new RedisData();
    redisData.setData(shop);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
    stringRedisTemplate.opsForValue().set("cache:shop:"+id,JSONUtil.toJsonStr(redisData));
}




//缓存穿透
    public Shop queryWithPassThrough(Long id){
        String key="cache:shop:"+id;
        //取redis里找
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //如果存在就反序列化，返回对象
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson !=null){
            return null;
        }
        //不在redis就去数据库获取
        Shop shop = getById(id);
        //都不在返回错误
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key,"",2, TimeUnit.MINUTES);

            return null;
        }
        //在数据库找到就写入redis中再返回对象
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);
        return shop;
    }
    //添加锁
    private boolean tryLock(String key){
        Boolean flag= stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商户不存在");
        }
        updateById(shop);
        stringRedisTemplate.delete("cache:shop:"+id);


        return Result.ok();
    }
}
