package xyz.kuailemao.domain.vo;

import lombok.Data;

@Data
public class AdminLoginChallengeVO {
    private String challengeId;
    private String maskedEmail;
    private Integer expiresIn;
    private Integer resendAfter;
    private boolean secondFactorRequired;
    private String taskId;

    public AdminLoginChallengeVO(String challengeId, String maskedEmail, Integer expiresIn,
                                 Integer resendAfter, boolean secondFactorRequired) {
        this.challengeId = challengeId; this.maskedEmail = maskedEmail; this.expiresIn = expiresIn;
        this.resendAfter = resendAfter; this.secondFactorRequired = secondFactorRequired;
    }
}
