package com.risk;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 测试环境不启 Redis：给一个永远 miss 的 RedisTemplate，规则服务因此直接读 H2。
 */
@TestConfiguration
public class TestRedisConfig {

    @Bean
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = Mockito.mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = Mockito.mock(ValueOperations.class);
        Mockito.when(template.opsForValue()).thenReturn(ops);
        Mockito.when(ops.get(Mockito.anyString())).thenReturn(null);
        Mockito.when(template.keys(Mockito.anyString())).thenReturn(java.util.Collections.emptySet());
        return template;
    }
}
