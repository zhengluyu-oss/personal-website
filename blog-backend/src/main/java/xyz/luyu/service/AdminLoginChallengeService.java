package xyz.luyu.service;

import xyz.luyu.domain.entity.LoginUser;
import xyz.luyu.domain.vo.AdminLoginChallengeVO;

public interface AdminLoginChallengeService {
    AdminLoginChallengeVO create(LoginUser user, String clientAddress);
    AdminLoginChallengeVO resend(String challengeId, String clientAddress);
    LoginUser verify(String challengeId, String code, String clientAddress);
}
