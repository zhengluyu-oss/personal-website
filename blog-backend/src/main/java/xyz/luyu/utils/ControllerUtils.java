package xyz.luyu.utils;

import xyz.luyu.domain.response.ResponseResult;

import java.util.function.Supplier;

/**
 * @author luyu
 * <p>
 * 创建时间：2023/10/30 9:52
 */
public class ControllerUtils {
    public static  <T> ResponseResult<T> messageHandler(Supplier<T> supplier) {
        return ResponseResult.success(supplier.get());
    }
}
