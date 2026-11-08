package ingsis.snippet.runner.redis

import org.austral.ingsis.redis.RedisStreamProducer
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class FormatResultProducer(
    redisTemplate: RedisTemplate<String, String>,
) : RedisStreamProducer(streamKey = "format-result", redis = redisTemplate)
