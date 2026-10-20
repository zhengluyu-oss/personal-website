package xyz.kuailemao.knowledge;

/** Application-owned messages only; never wrap JDBC/OSS/parser exception messages. */
public final class KnowledgeFailure extends RuntimeException {
    private final int status;
    public KnowledgeFailure(int status,String message){super(message);this.status=status;}
    public int status(){return status;}
    public static KnowledgeFailure invalid(String message){return new KnowledgeFailure(400,message);}
    public static KnowledgeFailure conflict(){return new KnowledgeFailure(409,"记录已变化或请求标识重复，请保留编辑并重新核对版本");}
    public static KnowledgeFailure unavailable(){return new KnowledgeFailure(503,"知识库暂不可用，未确认内容请保留");}
}
