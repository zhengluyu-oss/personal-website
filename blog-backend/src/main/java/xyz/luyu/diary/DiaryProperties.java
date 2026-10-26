package xyz.luyu.diary;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("diary")
public class DiaryProperties {
    private boolean enabled;
    private Long ownerUserId;
    private String url = "";
    private String username = "diary_runtime";
    private String password = "";
    public boolean configured() {
        return enabled && ownerUserId != null && ownerUserId > 0
                && url.startsWith("jdbc:postgresql:") && "diary_runtime".equals(username)
                && !password.isBlank();
    }
}
