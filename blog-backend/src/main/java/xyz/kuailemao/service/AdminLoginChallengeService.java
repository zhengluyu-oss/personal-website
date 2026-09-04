package xyz.kuailemao.service;

import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.vo.AdminLoginChallengeVO;

public interface AdminLoginChallengeService {
    AdminLoginChallengeVO create(LoginUser user, String clientAddress);
    AdminLoginChallengeVO resend(String challengeId, String clientAddress);
    LoginUser verify(String challengeId, String code, String clientAddress);
}
