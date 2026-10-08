package xyz.kuailemao.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailChangeStartDTO {
    @NotBlank @Email @Size(max = 254)
    private String email;
    @Size(max = 128)
    private String password;
}
