package org.journey.dao.redis.achieve;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.journey.dao.redis.annotation.NotInRedis;
import org.journey.dao.redis.annotation.RedisDateFormat;
import org.journey.dao.redis.util.RedisConstant;
import org.journey.dao.redis.util.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.util.JedisClusterCRC16;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author wudan-mac
 * @ClassName: RedisDaoImpl
 * @ClassNameExplain:
 * @Description:
 * @date 16/5/16 下午7:47
 */
public class RedisDao extends JedisCluster {

    Logger logger = LoggerFactory.getLogger(RedisDao.class);

    Gson gson = new Gson();

    public RedisDao(HostAndPort node) {
        super(node);
    }

    public RedisDao(HostAndPort node, int timeout) {
        super(node, timeout);
    }

    public RedisDao(HostAndPort node, int timeout, int maxRedirections) {
        super(node, timeout, maxRedirections);
    }

    public RedisDao(HostAndPort node, GenericObjectPoolConfig poolConfig) {
        super(node, poolConfig);
    }

    public RedisDao(HostAndPort node, int timeout, GenericObjectPoolConfig poolConfig) {
        super(node, timeout, poolConfig);
    }

    public RedisDao(HostAndPort node, int timeout, int maxRedirections, GenericObjectPoolConfig poolConfig) {
        super(node, timeout, maxRedirections, poolConfig);
    }

    public RedisDao(HostAndPort node, int connectionTimeout, int soTimeout, int maxRedirections, GenericObjectPoolConfig poolConfig) {
        super(node, connectionTimeout, soTimeout, maxRedirections, poolConfig);
    }

    public RedisDao(Set<HostAndPort> nodes) {
        super(nodes);
    }

    public RedisDao(Set<HostAndPort> nodes, int timeout) {
        super(nodes, timeout);
    }

    public RedisDao(Set<HostAndPort> nodes, int timeout, int maxRedirections) {
        super(nodes, timeout, maxRedirections);
    }

    public RedisDao(Set<HostAndPort> nodes, GenericObjectPoolConfig poolConfig) {
        super(nodes, poolConfig);
    }

    public RedisDao(Set<HostAndPort> nodes, int timeout, GenericObjectPoolConfig poolConfig) {
        super(nodes, timeout, poolConfig);
    }

    public RedisDao(Set<HostAndPort> jedisClusterNode, int timeout, int maxRedirections, GenericObjectPoolConfig poolConfig) {
        super(jedisClusterNode, timeout, maxRedirections, poolConfig);
    }

    public RedisDao(Set<HostAndPort> jedisClusterNode, int connectionTimeout, int soTimeout, int maxRedirections, GenericObjectPoolConfig poolConfig) {
        super(jedisClusterNode, connectionTimeout, soTimeout, maxRedirections, poolConfig);
    }

    /**
     * @param bean java对象
     * @return java.lang.String 成功返回 OK
     * @Title: setBean
     * @TitleExplain:
     * @Description: 向redis写入一个javaBean 以map的形式存储
     * @version 1.0.0
     * @author wudan-mac
     */
    public String bset(Object bean) throws Exception {

        if (bean == null) {
            throw new JedisDataException("bean sent to redis cannot be null");
        }

        //根据java bean 注解获得对应javabean的存储键
        final String key = Reflections.getRedisKey(bean);
        if (StringUtils.isEmpty(String.valueOf(key))) {
            throw new JedisDataException("bean key to redis cannot be null");
        }

        //用来存储全部字段的map
        final Map<String, String> poMap = new HashMap();

        //反射获得对应javabean 的class对象
        Class clazz = bean.getClass();
        //取得全部字段
        Field[] fields = clazz.getDeclaredFields();
        //循环处理字段
        for (Field field : fields) {

            /**
             * 排除序列化id
             */
            if (RedisConstant.SERIAL_VERSION_UID_FIELD_NAME.equals(field.getName())) {
                continue;
            }

            /**
             * 排除key 前缀字段
             */
            if (RedisConstant.REDIS_KEY_PREFIX_FIELD_NAME.equals(field.getName())) {
                continue;
            }

            /**
             * 如果该字段被标注不写入redis 则跳过
             */
            if (field.getAnnotation(NotInRedis.class) != null) {
                continue;
            }

            /**
             * 反射get方法获取属性值
             */
            Object tempValueObject;
            tempValueObject = Reflections.invokeGetter(bean, field.getName());
            if (tempValueObject == null) {
                continue;
            }

            /**
             * 判断字段类型是否为基本类型
             * 是 直接写入字段值
             * 不是  json序列化后写入json
             */
            String value = "";
            if (!field.getType().isPrimitive()) {

                /**
                 * 如果该字段标注了日期格式注解 则按注解格式进行json序列化
                 */
                if (field.getType().equals(Date.class) && field.getAnnotation(RedisDateFormat.class) != null) {
                    Gson gsonTemp = new GsonBuilder().setDateFormat(field.getAnnotation(RedisDateFormat.class).pattern()).create();
                    value = gsonTemp.toJson(tempValueObject);
                } else {
                    value = gson.toJson(tempValueObject);
                }
            } else {
                value = tempValueObject.toString();
            }
            poMap.put(field.getName(), value);
        }

        /**
         * 执行写入
         */
        logger.debug("where is key[" + key + " : " + JedisClusterCRC16.getSlot(key) + "]");
        return hmset(key, poMap);
    }

    /**
     * @param keySuffix 要查询redis结构的key后缀
     * @param clazz     要返回的实体对象类型
     * @return T 传入的实体对象类型
     * @Title: getObject
     * @TitleExplain:
     * @Description: 获取redis中一个po实体信息
     * @version 1.0.0
     * @author wudan-mac
     */
    public <T> T bget(String keySuffix, Class<T> clazz) throws Exception {

        if (keySuffix == null || clazz == null) {
            throw new JedisDataException("get bean param cannot be null");
        }
        T result = null;
        /**
         * 拼接key
         */
        String className = clazz.getName();
        String key = className.substring(className.lastIndexOf(".") + 1, className.length());
        if (!"".equals(keySuffix)) {
            key = key + RedisConstant.REDIS_KEY_SEPARATOR + keySuffix;
        }

        //实例化返回的对象
        result = clazz.newInstance();

        //取得全部字段
        Field[] fields = clazz.getDeclaredFields();

        //获取redis Map
        Map<String, String> map = hgetAll(key);

        /**
         * 将取得的hashmap对象转化为java对象
         */
        Object value;
        for (Field field : fields) {

            /**
             * 如果该字段被标注不写入redis 则跳过
             */
            if (field.getAnnotation(NotInRedis.class) != null) {
                continue;
            }

            String valueInMap = map.get(field.getName());
            /*if (field.getType().equals(String.class)) {
                //这样做是为了解决字符串中出现  空格等特殊字符引起的 json反序列化错误
                value = gson.fromJson("\"" + valueInMap + "\"", field.getType());
            } else */
            if (field.getType().equals(Date.class)) {
                //如果是日期类型 则按照注解格式反序列化
                Gson gsonTemp = new GsonBuilder().setDateFormat(field.getAnnotation(RedisDateFormat.class).pattern()).create();
                value = gsonTemp.fromJson(valueInMap, field.getType());
            } else {
                value = gson.fromJson(valueInMap, field.getType());
            }
            //设置当前字段的值
            Reflections.invokeSetter(
                    result,
                    field.getName(),
                    value
            );
        }
        return result;

    }

    /**
     * @param clazz java bean 类型
     * @param keySuffix key后缀
     * @param field    要自增的字段
     * @param value    要自增的值(增量)
     * @return Long 自增后的值
     * @Title: bincrBy
     * @TitleExplain:
     * @Description: 将redis hash map 转java 对象 取出
     * @version 1.0.0
     * @author wudan-mac
     */
    public Long bincrBy(Class clazz, String keySuffix, String field , Long value) throws Exception {

        if (keySuffix == null || clazz == null || field == null || value == null) {
            throw new JedisDataException("bincrBy bean field param cannot be null");
        }

        /**
         * 拼接key
         */
        String className = clazz.getName();
        String key = className.substring(className.lastIndexOf(".") + 1, className.length());
        if (!"".equals(keySuffix)) {
            key = key + RedisConstant.REDIS_KEY_SEPARATOR + keySuffix;
        }

        /**
         * 执行增长或减少
         */
        return hincrBy(key, field, value);
    }

    /**
     * @param clazz java bean 类型
     * @param keySuffix key后缀
     * @param field    要自增的字段
     * @param value    要自增的值(增量)
     * @return Double 自增后的值
     * @Title: bincrByFloat
     * @TitleExplain:
     * @Description: 将redis hash map 转java 对象 取出
     * @version 1.0.0
     * @author wudan-mac
     */
    public Double bincrByFloat(Class clazz, String keySuffix, String field , Double value) throws Exception {

        if (keySuffix == null || clazz == null || field == null || value == null) {
            throw new JedisDataException("bincrBy bean field param cannot be null");
        }

        /**
         * 拼接key
         */
        String className = clazz.getName();
        String key = className.substring(className.lastIndexOf(".") + 1, className.length());
        if (!"".equals(keySuffix)) {
            key = key + RedisConstant.REDIS_KEY_SEPARATOR + keySuffix;
        }

        /**
         * 执行增长或减少
         */
        return hincrByFloat(key, field, value);
    }

}