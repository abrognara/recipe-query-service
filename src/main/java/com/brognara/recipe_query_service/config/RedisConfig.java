package com.brognara.recipe_query_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
public class RedisConfig {

    @Value("${upstash.redis-url}")
    private String redisUrl;

    @Bean
    public JedisPooled jedisClient() {
        return new JedisPooled(redisUrl);
    }

}
