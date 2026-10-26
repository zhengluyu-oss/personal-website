package xyz.luyu.annotation;

import java.lang.annotation.*;

/**
 * @author luyu
 * @since 2024/9/5 下午10:10
 * 封禁验证注解
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckBlacklist {
}
