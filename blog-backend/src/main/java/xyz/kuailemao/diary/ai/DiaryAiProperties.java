package xyz.kuailemao.diary.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.*;

/** 不生成 toString，防止秘密被配置日志打印。 */
@Getter @Setter @ConfigurationProperties("diary.ai")
public class DiaryAiProperties {
    private boolean enabled;
    private String provider="", embeddingEndpoint="",generationEndpoint="",embeddingKey="",generationKey="";
    private String embeddingModel="",generationModel="",dataPolicy="";
    private Set<String> allowedHosts=new HashSet<>();
    private int dimensions, dailyGenerations=20,dailyEmbeddingBlocks=200,maxQueue=5,maxReports=1000;
    private int maxReviewCharacters=24000,maxQuestionContext=12000,maxPromptCharacters=32000;
    private int maxOutputTokens=2000,httpTimeoutSeconds=30,maxResponseBytes=4*1024*1024;
    private double minimumSimilarity=Double.NaN;
    public boolean configured() {
        return enabled && !provider.isBlank() && !embeddingModel.isBlank() && !generationModel.isBlank()
                && !embeddingKey.isBlank() && !generationKey.isBlank() && !dataPolicy.isBlank()
                && dimensions>0 && dimensions<=4096 && validEndpoint(embeddingEndpoint) && validEndpoint(generationEndpoint)
                && Double.isFinite(minimumSimilarity) && minimumSimilarity>=-1 && minimumSimilarity<=1
                && dailyGenerations>0 && dailyEmbeddingBlocks>0 && maxQueue>0 && maxQueue<=5
                && maxReports>0 && maxReports<=1000 && maxReviewCharacters>0 && maxReviewCharacters<=24000
                && maxQuestionContext>0 && maxQuestionContext<=12000 && maxPromptCharacters>0 && maxPromptCharacters<=32000
                && maxOutputTokens>0 && maxOutputTokens<=2000 && httpTimeoutSeconds>0 && httpTimeoutSeconds<=30
                && maxResponseBytes>0 && maxResponseBytes<=4*1024*1024;
    }
    private boolean validEndpoint(String endpoint) {
        try {
            URI uri=URI.create(endpoint);
            return "https".equals(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null
                    && uri.getQuery()==null && uri.getFragment()==null && (uri.getPort()==-1 || uri.getPort()==443)
                    && allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
        } catch(IllegalArgumentException e) { return false; }
    }
}
