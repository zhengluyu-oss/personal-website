package xyz.luyu.diary.coaching;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="diary.coaching")
public class CoachingProperties {
    private boolean enabled;
    private boolean aiEnabled;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled=value; }
    public boolean isAiEnabled() { return aiEnabled; }
    public void setAiEnabled(boolean value) { aiEnabled=value; }
}
