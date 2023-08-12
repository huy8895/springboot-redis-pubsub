package com.example.springbootredispubsub;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration.JedisClientConfigurationBuilder;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.SetParams;


@Configuration
@EnableCaching
@Slf4j
public class RedisConfiguration {
	
	@Value("${redis.host}")
	private String redisHost;

	@Value("${redis.port}")
	private int redisPort;
	
	@Value("${redis.database}")
	private int redisDatabase;
	
	@Value("${redis.password}")
	private String redisPassword;
	
	@Value("${redis.username}")
	private String username;
	
	@Value("${redis.timeout}")
	private int redisTimeout;
	
	@Bean
	public JedisConnectionFactory jedisConnectionFactory() {
		log.info("jedisConnectionFactory: {}, {}", redisHost, redisDatabase);
		RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
		redisStandaloneConfiguration.setHostName(redisHost);
		redisStandaloneConfiguration.setPort(redisPort);
		redisStandaloneConfiguration.setDatabase(redisDatabase);
		redisStandaloneConfiguration.setUsername(username);
		
		JedisClientConfigurationBuilder jedisClientConfiguration =
				JedisClientConfiguration.builder()
				                        .connectTimeout(Duration.ofMillis(redisTimeout));
		jedisClientConfiguration.usePooling();
		
		
		return new JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration.build());

	}
	
	@Bean
	public Jedis jedisDefault(){
		JedisPool pool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort, username, redisPassword);
		Jedis jedis = pool.getResource();
		return jedis;
	}
	
	@Bean
	public JedisPool poolConfig(Jedis jedisDefault){
		JedisPool pool = new JedisPool(new JedisPoolConfig(), redisHost, redisPort, username, redisPassword);
		Jedis jedis = pool.getResource();
		jedis.subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				String lockKey = "myLockKey";
				int lockTimeout = 10000; // 10 seconds
				boolean lockAcquired = false;
				try {
					// Try to acquire the lock
					SetParams params = new SetParams();
					params.nx()
					      .px(lockTimeout);
					String result = jedisDefault.set(lockKey, "lock", params);
					if ("OK".equals(result)) {
						// Lock acquired
						lockAcquired = true;
						// Process the event
						System.out.println("Key expired: " + message);
					} else {
						// Lock not acquired
						// Another instance is processing the event
						log.info("Lock not acquired Another instance is processing the event");
					}
				} finally {
					if (lockAcquired) {
						// Release the lock
						jedisDefault.del(lockKey);
					}
				}
			}
		}, "__keyevent@0__:expired");
		
		return pool;
	}

	
	
	@Bean
	public RedisTemplate<String, Object> redisTemplate() {
		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(jedisConnectionFactory());
		return redisTemplate;
	}

}
