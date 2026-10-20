package xyz.kuailemao.knowledge;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter @ConfigurationProperties("knowledge")
public class KnowledgeProperties {
    private boolean enabled;
    private Long ownerUserId;
    private Postgres postgres=new Postgres();
    private Storage storage=new Storage();
    private Processing processing=new Processing();
    @Getter @Setter public static class Postgres {
        private String url="",username="knowledge_runtime",password="";
    }
    @Getter @Setter public static class Storage {
        private String endpoint="",bucket="",accessKey="",secretKey="",prefix="knowledge/";
        private long quotaBytes=1073741824L;
    }
    @Getter @Setter public static class Processing {
        private boolean enabled;
        private String temporaryDirectory="";
        private int timeoutSeconds=60;
        private int maxPages=300;
        private long pdfBytes=20971520L,textBytes=5242880L,outputBytes=2097152L,temporaryBytes=268435456L;
    }
    public boolean configured(){return enabled&&ownerUserId!=null&&ownerUserId>0&&postgres!=null
        &&postgres.url.startsWith("jdbc:postgresql:")&&"knowledge_runtime".equals(postgres.username)&&!postgres.password.isBlank();}
}
