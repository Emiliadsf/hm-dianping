package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWork {
        private static final long BEGIN_TIMESTAMP=1640995200L;
        private static final int COUNT_BITS=32;
        private StringRedisTemplate stringRedisTemplate;

        public RedisIdWork(StringRedisTemplate stringRedisTemplate) {
            this.stringRedisTemplate = stringRedisTemplate;
        }

        public long nextId(String keyPrefix){
            //生成时间戳
            LocalDateTime now = LocalDateTime.now();
            long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
            long l = epochSecond - BEGIN_TIMESTAMP;

            //生成序列号
            String format = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
            Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + format);

            return l<<COUNT_BITS | count;

        }




}
