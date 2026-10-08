package xyz.kuailemao.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class UpdateEmailDTO {
    //验证码
    @Schema(description = "验证码")
    @NotEmpty(message = "验证码不能为空")
    @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}")
    private String code;

    @Schema(description = "邮箱")
    @Email
    @NotEmpty
    @Length(min = 4, max = 254)
    private String email;

    // 密码
    @Schema(description = "密码")
    private String password;

    @NotEmpty(message = "请先发起邮箱安全验证")
    @jakarta.validation.constraints.Pattern(regexp = "[0-9a-f]{64}")
    private String challengeId;

    @jakarta.validation.constraints.Size(max = 6)
    private String oldCode;
}
