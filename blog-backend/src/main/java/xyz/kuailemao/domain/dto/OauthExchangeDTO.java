package xyz.kuailemao.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OauthExchangeDTO {
    @NotBlank(message = "登录凭据不能为空")
    private String code;
}
