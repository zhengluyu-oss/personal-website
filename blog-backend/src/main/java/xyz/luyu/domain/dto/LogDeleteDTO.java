package xyz.luyu.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @author luyu
 * <p>
 * 创建时间：2023/12/11 20:15
 */
@Data
public class LogDeleteDTO {
    @NotNull
    List<Long> Ids;
}
