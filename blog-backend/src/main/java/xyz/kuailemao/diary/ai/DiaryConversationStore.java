package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import xyz.kuailemao.diary.DiaryFailure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

/** Conversation metadata stays in its own RLS schema; jobs and reports remain the source of truth. */
public final class DiaryConversationStore {
    public record Start(Request request) {}
    public record Followup(UUID requestId, String question) {}
    public record Summary(UUID id, Kind kind, String firstQuestion, Instant createdAt, Instant updatedAt) {}
    public record Turn(UUID id, int sequence, String question, Job job, Report report) {}
    public record Detail(Summary conversation, List<Turn> turns, long total, int page, boolean canFollow) {}
    public record Submitted(UUID conversationId, UUID turnId, Job job) {}
    record Context(Kind conversationKind, Scope scope, String anchorDigest, List<History> history) {}
    record History(String question, String answer) {}

    private final DiaryAiStore ai;
    DiaryConversationStore(DiaryAiStore ai) { this.ai=ai; }

    boolean available(long owner) {
        return ai.tx(owner,()->Boolean.TRUE.equals(ai.jdbc.queryForObject(
            "SELECT to_regclass('diary_conversation.conversation') IS NOT NULL AND " +
            "to_regclass('diary_conversation.turn') IS NOT NULL AND " +
            "to_regclass('diary_conversation.schema_version') IS NOT NULL AND " +
            "EXISTS(SELECT FROM pg_class WHERE oid=to_regclass('diary_conversation.schema_version')) AND " +
            "(SELECT count(*)=2 AND bool_and(c.relrowsecurity AND c.relforcerowsecurity AND r.rolname<>'diary_runtime') " +
            "FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner " +
            "WHERE n.nspname='diary_conversation' AND c.relkind='r' AND c.relname<>'schema_version')",
            Map.of(),Boolean.class))) && version(owner);
    }

    private boolean version(long owner) {
        return ai.tx(owner,()->Boolean.TRUE.equals(ai.jdbc.queryForObject(
            "SELECT EXISTS(SELECT FROM diary_conversation.schema_version WHERE version=1)",Map.of(),Boolean.class)));
    }

    Submitted start(long owner, Kind kind, Request request) {
        if(kind!=Kind.QUESTION && kind!=Kind.REVIEW) throw DiaryFailure.invalid("对话类型无效");
        if(request==null) throw DiaryFailure.invalid("缺少请求内容");
        request.validate(kind);
        return ai.tx(owner,()->{
            Submitted prior=existing(owner,request.requestId(),null,kind,request);
            if(prior!=null) return prior;
            UUID id=UUID.randomUUID(),turn=UUID.randomUUID();
            String digest=ai.scopeDigest(owner,request.scope());
            if(kind==Kind.REVIEW && !ai.preview(owner,request.scope()).snapshotDigest().equals(request.snapshotDigest()))
                throw new DiaryFailure(409,"复盘资料已变化，请重新预览");
            var p=new MapSqlParameterSource("owner",owner).addValue("id",id).addValue("kind",kind.name())
                .addValue("scope",JSON.toJSONString(request.scope())).addValue("digest",digest);
            ai.jdbc.update("INSERT INTO diary_conversation.conversation(owner_id,id,kind,scope,anchor_digest) " +
                "VALUES(:owner,:id,:kind,CAST(:scope AS jsonb),:digest)",p);
            Job job=ai.enqueue(owner,kind,request);
            p.addValue("turn",turn).addValue("request",request.requestId()).addValue("job",job.id())
                .addValue("question",request.question());
            ai.jdbc.update("INSERT INTO diary_conversation.turn(owner_id,id,conversation_id,sequence_no,request_id,job_id,user_text) " +
                "VALUES(:owner,:turn,:id,1,:request,:job,:question)",p);
            return new Submitted(id,turn,job);
        });
    }

    Submitted followup(long owner, UUID conversationId, Followup input) {
        if(input==null || input.requestId()==null || input.question()==null || input.question().isBlank()
            || points(input.question())>1000) throw DiaryFailure.invalid("追问需为1至1000字且带请求标识");
        return ai.tx(owner,()->{
            var p=new MapSqlParameterSource("owner",owner).addValue("id",conversationId);
            var rows=ai.jdbc.query("SELECT kind,scope,anchor_digest FROM diary_conversation.conversation " +
                "WHERE owner_id=:owner AND id=:id FOR UPDATE",p,(r,n)->new Anchor(Kind.valueOf(r.getString(1)),
                JSON.parseObject(r.getString(2),Scope.class),r.getString(3)));
            if(rows.isEmpty()) throw new DiaryFailure(404,"对话不存在或不可访问");
            Anchor anchor=rows.get(0);
            Request request=new Request(input.requestId(),input.question(),anchor.scope(),null,false);
            Submitted prior=existing(owner,input.requestId(),conversationId,Kind.QUESTION,request);
            if(prior!=null) return prior;
            ai.invalidate(owner);
            Integer firstState=ai.jdbc.queryForObject("SELECT count(*) FROM diary_conversation.turn t " +
                "LEFT JOIN diary_ai.report r ON r.owner_id=t.owner_id AND r.job_id=t.job_id AND r.state='SUCCEEDED' " +
                "WHERE t.owner_id=:owner AND t.conversation_id=:id AND t.sequence_no=1 AND r.id IS NOT NULL",p,Integer.class);
            if(firstState==null || firstState!=1) throw new DiaryFailure(409,"首轮结果已失效或未完成，请重新建立对话");
            Integer stale=ai.jdbc.queryForObject("SELECT count(*) FROM diary_conversation.turn t " +
                "JOIN diary_ai.job j ON j.owner_id=t.owner_id AND j.id=t.job_id " +
                "WHERE t.owner_id=:owner AND t.conversation_id=:id AND j.state='STALE'",p,Integer.class);
            if(stale!=null && stale>0) throw new DiaryFailure(409,"历史引用已失效，请重新核对资料并建立对话");
            Integer pending=ai.jdbc.queryForObject("SELECT count(*) FROM diary_conversation.turn t " +
                "JOIN diary_ai.job j ON j.owner_id=t.owner_id AND j.id=t.job_id " +
                "WHERE t.owner_id=:owner AND t.conversation_id=:id AND j.state IN('QUEUED','RUNNING')",p,Integer.class);
            if(pending!=null && pending>0) throw new DiaryFailure(409,"上一轮尚未完成");
            if(anchor.kind()==Kind.REVIEW && !Objects.equals(anchor.digest(),ai.scopeDigest(owner,anchor.scope())))
                throw new DiaryFailure(409,"复盘资料已变化，请重新预览并新建对话");
            int sequence=ai.jdbc.queryForObject("SELECT coalesce(max(sequence_no),0)+1 FROM diary_conversation.turn " +
                "WHERE owner_id=:owner AND conversation_id=:id",p,Integer.class);
            Job job=ai.enqueue(owner,Kind.QUESTION,request);
            UUID turn=UUID.randomUUID();
            p.addValue("turn",turn).addValue("sequence",sequence).addValue("request",input.requestId())
                .addValue("job",job.id()).addValue("question",input.question());
            ai.jdbc.update("INSERT INTO diary_conversation.turn(owner_id,id,conversation_id,sequence_no,request_id,job_id,user_text) " +
                "VALUES(:owner,:turn,:id,:sequence,:request,:job,:question)",p);
            ai.jdbc.update("UPDATE diary_conversation.conversation SET updated_at=now() WHERE owner_id=:owner AND id=:id",p);
            return new Submitted(conversationId,turn,job);
        });
    }

    private record Anchor(Kind kind, Scope scope, String digest) {}

    private Submitted existing(long owner, UUID requestId, UUID conversation, Kind kind, Request request) {
        var p=new MapSqlParameterSource("owner",owner).addValue("request",requestId);
        var prior=ai.jdbc.query("SELECT t.id,t.conversation_id,t.user_text,j.kind,j.request_hash " +
            "FROM diary_conversation.turn t JOIN diary_ai.job j ON j.owner_id=t.owner_id AND j.id=t.job_id " +
            "WHERE t.owner_id=:owner AND t.request_id=:request",p,(r,n)->new Existing(
                r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5)));
        if(prior.isEmpty()) {
            Integer occupied=ai.jdbc.queryForObject("SELECT count(*) FROM diary_ai.job WHERE owner_id=:owner AND request_id=:request",p,Integer.class);
            if(occupied!=null && occupied>0) throw new DiaryFailure(409,"请求标识已用于其他任务");
            return null;
        }
        Existing old=prior.get(0);
        String hash=DiaryAiText.hash(kind+"\n"+JSON.toJSONString(request));
        if((conversation!=null && !conversation.equals(old.conversation())) || !old.kind().equals(kind.name())
            || !old.hash().equals(hash)) throw new DiaryFailure(409,"请求标识已用于不同内容");
        UUID job=ai.jdbc.queryForObject("SELECT job_id FROM diary_conversation.turn WHERE owner_id=:owner AND id=:turn",
            new MapSqlParameterSource("owner",owner).addValue("turn",old.turn()),UUID.class);
        return new Submitted(old.conversation(),old.turn(),ai.job(owner,job));
    }
    private record Existing(UUID turn,UUID conversation,String question,String kind,String hash) {}

    Page<Summary> list(long owner, Kind kind, int page) {
        if(page<1 || page>100000 || kind!=Kind.QUESTION && kind!=Kind.REVIEW) throw DiaryFailure.invalid("列表参数无效");
        return ai.tx(owner,()->{
            var p=new MapSqlParameterSource("owner",owner).addValue("kind",kind.name()).addValue("offset",(page-1)*20);
            long total=ai.jdbc.queryForObject("SELECT count(*) FROM diary_conversation.conversation WHERE owner_id=:owner AND kind=:kind",p,Long.class);
            var items=ai.jdbc.query("SELECT c.*,t.user_text first_question FROM diary_conversation.conversation c " +
                "LEFT JOIN diary_conversation.turn t ON t.owner_id=c.owner_id AND t.conversation_id=c.id AND t.sequence_no=1 " +
                "WHERE c.owner_id=:owner AND c.kind=:kind ORDER BY c.updated_at DESC,c.id DESC LIMIT 20 OFFSET :offset",p,
                (r,n)->summary(r));
            return new Page<>(items,total,page,20);
        });
    }

    Detail detail(long owner, UUID id, int page) {
        if(page<1 || page>100000) throw DiaryFailure.invalid("页码无效");
        return ai.tx(owner,()->{
            ai.invalidate(owner);
            var p=new MapSqlParameterSource("owner",owner).addValue("id",id).addValue("offset",(page-1)*20);
            var conversations=ai.jdbc.query("SELECT c.*,t.user_text first_question FROM diary_conversation.conversation c " +
                "LEFT JOIN diary_conversation.turn t ON t.owner_id=c.owner_id AND t.conversation_id=c.id AND t.sequence_no=1 " +
                "WHERE c.owner_id=:owner AND c.id=:id",p,(r,n)->summary(r));
            if(conversations.isEmpty()) throw new DiaryFailure(404,"对话不存在或不可访问");
            long total=ai.jdbc.queryForObject("SELECT count(*) FROM diary_conversation.turn WHERE owner_id=:owner AND conversation_id=:id",p,Long.class);
            boolean canFollow=Boolean.TRUE.equals(ai.jdbc.queryForObject("SELECT " +
                "EXISTS(SELECT FROM diary_conversation.turn t JOIN diary_ai.report r ON r.owner_id=t.owner_id AND r.job_id=t.job_id AND r.state='SUCCEEDED' " +
                "WHERE t.owner_id=:owner AND t.conversation_id=:id AND t.sequence_no=1) " +
                "AND NOT EXISTS(SELECT FROM diary_conversation.turn t JOIN diary_ai.job j ON j.owner_id=t.owner_id AND j.id=t.job_id " +
                "WHERE t.owner_id=:owner AND t.conversation_id=:id AND j.state IN('STALE','QUEUED','RUNNING'))",p,Boolean.class));
            var rows=ai.jdbc.query("SELECT t.id,t.sequence_no,t.user_text,t.job_id,r.id report_id,r.state report_state " +
                "FROM diary_conversation.turn t LEFT JOIN diary_ai.report r ON r.owner_id=t.owner_id AND r.job_id=t.job_id " +
                "WHERE t.owner_id=:owner AND t.conversation_id=:id ORDER BY t.sequence_no DESC LIMIT 20 OFFSET :offset",p,
                (r,n)->new TurnRow(r.getObject("id",UUID.class),r.getInt("sequence_no"),r.getString("user_text"),
                    r.getObject("job_id",UUID.class),r.getObject("report_id",UUID.class),r.getString("report_state")));
            var turns=new ArrayList<Turn>();
            for(int i=rows.size()-1;i>=0;i--) {
                var row=rows.get(i);
                Job job=ai.job(owner,row.job());
                Report report=row.report()!=null && "SUCCEEDED".equals(row.reportState()) ? ai.report(owner,row.report()) : null;
                turns.add(new Turn(row.id(),row.sequence(),row.question(),job,report));
            }
            return new Detail(conversations.get(0),List.copyOf(turns),total,page,canFollow);
        });
    }
    private record TurnRow(UUID id,int sequence,String question,UUID job,UUID report,String reportState) {}

    Context context(long owner, UUID jobId) {
        return ai.tx(owner,()->{
            if(!Boolean.TRUE.equals(ai.jdbc.queryForObject("SELECT to_regclass('diary_conversation.turn') IS NOT NULL",Map.of(),Boolean.class)))
                return null;
            var p=new MapSqlParameterSource("owner",owner).addValue("job",jobId);
            var rows=ai.jdbc.query("SELECT c.id,c.kind,c.scope,c.anchor_digest,t.sequence_no FROM diary_conversation.turn t " +
                "JOIN diary_conversation.conversation c ON c.owner_id=t.owner_id AND c.id=t.conversation_id " +
                "WHERE t.owner_id=:owner AND t.job_id=:job",p,(r,n)->new ContextRow(r.getObject(1,UUID.class),
                    Kind.valueOf(r.getString(2)),JSON.parseObject(r.getString(3),Scope.class),r.getString(4),r.getInt(5)));
            if(rows.isEmpty() || rows.get(0).sequence()==1) return null;
            ContextRow row=rows.get(0);
            if(row.kind()==Kind.REVIEW && !row.digest().equals(ai.scopeDigest(owner,row.scope())))
                throw new DiaryFailure(409,"复盘资料已变化");
            p.addValue("conversation",row.conversation()).addValue("sequence",row.sequence());
            var previous=ai.jdbc.query("SELECT t.user_text,r.id,r.result FROM diary_conversation.turn t " +
                "JOIN diary_ai.report r ON r.owner_id=t.owner_id AND r.job_id=t.job_id AND r.state='SUCCEEDED' " +
                "WHERE t.owner_id=:owner AND t.conversation_id=:conversation AND t.sequence_no<:sequence " +
                "ORDER BY t.sequence_no DESC LIMIT 4",p,(r,n)->new Prior(r.getString(1),r.getObject(2,UUID.class),r.getString(3)));
            var history=new ArrayList<History>();
            for(int i=previous.size()-1;i>=0;i--) {
                Prior item=previous.get(i);
                // This revalidates every history report before it can enter a prompt.
                ai.report(owner,item.report());
                String answer=DiaryConversationText.summary(item.result());
                history.add(new History(item.question(),answer));
            }
            if(history.isEmpty()) throw new DiaryFailure(409,"没有可用的历史回答，请新建对话");
            return new Context(row.kind(),row.scope(),row.digest(),List.copyOf(history));
        });
    }
    private record ContextRow(UUID conversation,Kind kind,Scope scope,String digest,int sequence) {}
    private record Prior(String question,UUID report,String result) {}

    void delete(long owner, UUID id) {
        ai.tx(owner,()->{
            var p=new MapSqlParameterSource("owner",owner).addValue("id",id);
            var found=ai.jdbc.query("SELECT id FROM diary_conversation.conversation WHERE owner_id=:owner AND id=:id FOR UPDATE",p,
                (r,n)->r.getObject(1,UUID.class));
            if(found.isEmpty()) throw new DiaryFailure(404,"对话不存在或不可访问");
            ai.jdbc.update("UPDATE diary_ai.job SET state='CANCELED',sources=NULL,payload='{}' " +
                "WHERE owner_id=:owner AND id IN(SELECT job_id FROM diary_conversation.turn WHERE owner_id=:owner AND conversation_id=:id)",p);
            if(Boolean.TRUE.equals(ai.jdbc.queryForObject("SELECT to_regclass('diary_diagnostics.failure') IS NOT NULL",Map.of(),Boolean.class)))
                ai.jdbc.update("DELETE FROM diary_diagnostics.failure WHERE owner_id=:owner AND job_id IN" +
                    "(SELECT job_id FROM diary_conversation.turn WHERE owner_id=:owner AND conversation_id=:id)",p);
            ai.jdbc.update("DELETE FROM diary_ai.report WHERE owner_id=:owner AND job_id IN" +
                "(SELECT job_id FROM diary_conversation.turn WHERE owner_id=:owner AND conversation_id=:id)",p);
            ai.jdbc.update("DELETE FROM diary_conversation.conversation WHERE owner_id=:owner AND id=:id",p);
            return null;
        });
    }

    private static Summary summary(ResultSet r) throws SQLException {
        return new Summary(r.getObject("id",UUID.class),Kind.valueOf(r.getString("kind")),r.getString("first_question"),
            r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    }
}
