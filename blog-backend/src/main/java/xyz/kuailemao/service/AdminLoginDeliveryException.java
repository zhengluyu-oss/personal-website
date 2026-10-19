package xyz.kuailemao.service;

import org.springframework.security.core.AuthenticationException;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.enums.RespEnum;

/** Only allowlisted, post-password code-delivery outcomes are safe for clients. */
public final class AdminLoginDeliveryException extends AuthenticationException {
    public enum Reason { CODE_SEND_COOLDOWN, CODE_SEND_UNAVAILABLE }
    public record Details(Reason reason, Long retryAfterSeconds) {}
    private final Reason reason;
    private final Long retryAfterSeconds;

    private AdminLoginDeliveryException(Reason reason, Long milliseconds) {
        super(reason.name());
        this.reason=reason;
        this.retryAfterSeconds=milliseconds != null && milliseconds > 0
                ? milliseconds / 1000 + (milliseconds % 1000 == 0 ? 0 : 1) : null;
    }
    public static AdminLoginDeliveryException cooldown(Long milliseconds) {
        return new AdminLoginDeliveryException(Reason.CODE_SEND_COOLDOWN, milliseconds);
    }
    public static AdminLoginDeliveryException unavailable() {
        return new AdminLoginDeliveryException(Reason.CODE_SEND_UNAVAILABLE, null);
    }
    public ResponseResult<Details> response() {
        boolean cooldown=reason==Reason.CODE_SEND_COOLDOWN;
        String message=cooldown
                ? "验证码发送过于频繁，请" + (retryAfterSeconds == null ? "稍后" : "等待 " + retryAfterSeconds + " 秒后") + "重试"
                : "验证码发送服务暂不可用，请稍后重试";
        return new ResponseResult<>((cooldown ? RespEnum.REQUEST_FREQUENTLY : RespEnum.OTHER_ERROR).getCode(),
                message,new Details(reason,retryAfterSeconds));
    }
    public static ResponseResult<Void> serviceUnavailable() {
        return ResponseResult.failure(RespEnum.OTHER_ERROR.getCode(), "登录服务暂不可用，请稍后重试");
    }
}
