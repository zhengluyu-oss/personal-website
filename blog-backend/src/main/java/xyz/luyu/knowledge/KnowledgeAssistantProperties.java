package xyz.luyu.knowledge;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Separate opt-in and credentials from diary AI. Never generate a secret-bearing toString. */
@Getter @Setter @Component @ConfigurationProperties("knowledge.assistant")
public final class KnowledgeAssistantProperties {
    private boolean enabled;
    private String endpoint="",apiKey="",model="",dataPolicy="";
    private Set<String> allowedHosts=Set.of();
    private int dailyPerAccount=10,dailySite=100,maxConcurrent=2,maxQuestionChars=500;
    private int maxEvidence=4,maxEvidenceChars=4800,maxOutputTokens=800,timeoutSeconds=20;
    public boolean configured(){
        if(!enabled||apiKey.isBlank()||model.isBlank()||dataPolicy.isBlank()||dailyPerAccount<1||dailyPerAccount>100
            ||dailySite<1||dailySite>1000||maxConcurrent<1||maxConcurrent>4||maxQuestionChars<1||maxQuestionChars>500
            ||maxEvidence<1||maxEvidence>4||maxEvidenceChars<1||maxEvidenceChars>4800
            ||maxOutputTokens<1||maxOutputTokens>800||timeoutSeconds<1||timeoutSeconds>20)return false;
        try {URI uri=URI.create(endpoint);return "https".equals(uri.getScheme())&&uri.getHost()!=null
            &&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null
            &&(uri.getPort()==-1||uri.getPort()==443)&&allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT));}
        catch(RuntimeException invalid){return false;}
    }
}
