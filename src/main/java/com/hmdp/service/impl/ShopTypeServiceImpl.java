package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    private final StringRedisTemplate stringRedisTemplate;

    public ShopTypeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result queryList(List<ShopType> typeList) {
        //查询redis缓存
        List<String> shopTypeJson=stringRedisTemplate.opsForList().range("shop:type:"+typeList, 0, -1);

        if(!shopTypeJson.isEmpty()){
            List<ShopType> shopTypeListre=shopTypeJson.stream().map(item -> {
                return JSONUtil.toBean(item, ShopType.class);
            }).collect(Collectors.toList());
            return Result.ok(shopTypeListre);
        }
        List<ShopType>shopTypeList=query().orderByAsc("sort").list();
        List<String> resList=shopTypeList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll("shop:type:"+typeList,resList);
        return Result.ok(shopTypeList);

    }
}
