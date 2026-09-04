package xyz.kuailemao.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLoginResendDTO {
    @NotBlank
    private String challengeId;
}
