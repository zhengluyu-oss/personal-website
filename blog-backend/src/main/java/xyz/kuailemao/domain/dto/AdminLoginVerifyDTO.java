package xyz.kuailemao.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminLoginVerifyDTO {
    @NotBlank
    private String challengeId;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$")
    private String code;
}
