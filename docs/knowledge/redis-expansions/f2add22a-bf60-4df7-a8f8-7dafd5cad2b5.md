## 三十一、在 Spring Boot 中把缓存边界说清楚

RedisTemplate 和 StringRedisTemplate 都是客户端访问入口，不会自动解决缓存一致性、序列化兼容和故障处理。若业务值本来就是字符串，StringRedisTemplate 更直观；若存对象，须明确 key/value 序列化器、字段兼容与升级策略。不要把 Java 对象的默认序列化结果当作可长期跨版本读取的公共格式。

示意代码如下，省略连接配置和异常映射：

    String key = "product:detail:" + productId;
    String cached = stringRedisTemplate.opsForValue().get(key);
    if (cached != null) return decode(cached);
    Product product = productRepository.findById(productId);
    if (product == null) return null;
    stringRedisTemplate.opsForValue().set(
        key, encode(product), Duration.ofMinutes(5));
    return product;

写入数据库后要删除对应缓存；缓存不可用时可否回源、回源是否会把数据库压垮，必须由业务和容量决定。上面的简化代码在并发下可能重复回源，热点业务还要处理击穿。

### 计数与验证码的边界

    Long views = stringRedisTemplate.opsForValue()
        .increment("article:view:" + articleId);

单次 increment 是 Redis 命令原子递增，但不保证数据库计数同步成功，也不保证“一个用户一天只计一次”。验证码可用带 TTL 的条件写入，校验还需要尝试次数、一次性消费与失败限制；不要只比较字符串后忘记删除或让重试无限次。

### 连接、序列化和错误处理

1. 给 Redis 请求设置有界超时，并区分“没找到键”和“连接失败”。两者不能都返回 null 后继续业务。
2. Key 前缀按环境和用途隔离；避免测试与生产共用命名空间。
3. 对缓存 JSON 添加可演进字段，升级时考虑旧值解码失败后的删除与回源。
4. 监控连接池、命令延迟、命中率与回源率。增加 Redis 不应让数据库故障时发生无界重试。

Redis 与 MySQL 的事务不能自动合并成一个原子提交。核心订单、支付和库存应以权威数据库或明确的一致性协议为准，不要因为 Redis 操作成功就声称业务已持久成功。

延伸阅读：[Spring Data RedisTemplate](https://docs.spring.io/spring-data/redis/reference/redis/template.html) · [Redis Cache Aside](https://redis.io/docs/latest/develop/use-cases/cache-aside/)
## 三十二、一个完整的“读缓存、写数据库”例子

商品详情的主表在 MySQL。读路径先按 product:detail:{id} 查询 Redis；命中且反序列化成功就返回；未命中则查数据库，找到后以有限 TTL 写回。写路径先在数据库事务中更新商品，提交成功后删除缓存。若事务回滚，不应提前让缓存显示一个未提交的新值。

这只是基础模式。数据库提交成功而 DEL 失败，会留下旧缓存；旧读请求也可能在写后重新回填旧数据。可用短 TTL 限制窗口，并根据业务需要引入重试、版本号或延迟校正。对库存和订单这类强约束数据，数据库条件更新与唯一约束仍然是最终防线。

### RedisTemplate 代码审查清单

- Key 是否有明确前缀、环境隔离与最大长度？是否意外包含用户隐私？
- Value 使用什么序列化格式？升级后旧缓存能否读取，不能读取时如何回源？
- 每种临时数据是否明确设置 TTL？覆盖键时 TTL 是否被改变？
- 缓存未命中和 Redis 连接失败是否分开处理？
- 数据库更新后是否有可观测的缓存失效与补偿？
- 热点键过期时，回源并发能否压垮数据库？

### 与事务的关系

Redis 的 MULTI/EXEC 可让一组 Redis 命令按顺序执行，但不能与 MySQL 事务自动组成跨系统原子提交，也不等同于关系型数据库的回滚语义。若业务必须跨两个系统一致，应以可补偿的流程或可靠事件机制设计，而不是在两个客户端上各开一个事务就认为完成了分布式事务。
