package xyz.kuailemao.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminLoginChallengeVO {
    private String challengeId;
    private String maskedEmail;
    private Integer expiresIn;
    private Integer resendAfter;
    private boolean secondFactorRequired;
}
