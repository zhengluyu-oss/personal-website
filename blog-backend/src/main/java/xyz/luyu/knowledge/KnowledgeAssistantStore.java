package xyz.luyu.knowledge;

import org.springframework.stereotype.Component;
import java.util.*;
import static xyz.luyu.knowledge.KnowledgeModels.*;

/** Uses the knowledge owner's existing RLS transaction; usage lives in a separate schema. */
@Component
public final class KnowledgeAssistantStore {
    public record Evidence(UUID entryId,long revision,UUID fileVersion,boolean fileSource,String title,String excerpt,Integer page,Integer fromLine,Integer toLine) {}
    record Location(String category,List<String> tags,String section) {}
    private record Ranked(Evidence evidence,int score) {}
    private final KnowledgeAccess access;
    KnowledgeAssistantStore(KnowledgeAccess access){this.access=access;}
    boolean ready(long owner){
        try {return access.store().tx(owner,()->Boolean.TRUE.equals(access.store().jdbc.queryForObject(
            "SELECT EXISTS(SELECT FROM knowledge.schema_version WHERE version=4) AND current_user='knowledge_runtime' AND EXISTS(SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='knowledge_assistant' AND c.relname='usage' AND c.relrowsecurity AND c.relforcerowsecurity AND r.rolname='knowledge_migrator')",
            Map.of(),Boolean.class)));}
        catch(RuntimeException unavailable){return false;}
    }
    List<Evidence> find(long owner,List<String> terms,int limit,int maxChars){
        if(terms.isEmpty())return List.of();
        var entries=new LinkedHashMap<UUID,Entry>();
        for(String term:terms){
            Filter filter=new Filter(null,null,null,null,null,term,1,8,false);
            for(SearchHit hit:access.store().search(owner,filter).records()){
                if(entries.size()>=32)break;
                entries.computeIfAbsent(hit.entry().id(),id->access.store().read(owner,id,false));
            }
        }
        List<Ranked> ranked=new ArrayList<>();
        for(Entry entry:entries.values()){
            String title=entry.content().title(),body=entry.content().body();
            Set<String> seen=new HashSet<>();
            for(String term:terms){
                int at=body.toLowerCase(Locale.ROOT).indexOf(term.toLowerCase(Locale.ROOT));
                if(at<0)continue;
                String excerpt=body.substring(Math.max(0,at-80),Math.min(body.length(),at+320)).strip();
                if(!excerpt.isEmpty()&&seen.add(excerpt))add(ranked,new Evidence(entry.id(),entry.revision(),entry.fileVersion(),false,title,excerpt,null,null,null),terms);
            }
            if(seen.isEmpty()&&KnowledgeAssistantQuery.overlap(title,terms)>=Math.min(2,terms.size())&&!body.isBlank()){
                String excerpt=body.substring(0,Math.min(320,body.length())).strip();
                add(ranked,new Evidence(entry.id(),entry.revision(),entry.fileVersion(),false,title,excerpt,null,null,null),terms);
            }
            if(entry.fileVersion()!=null&&fileReady(owner,entry.id(),entry.fileVersion())){
                var params=new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("owner",owner).addValue("entry",entry.id()).addValue("file",entry.fileVersion());
                StringBuilder predicate=new StringBuilder();
                for(int i=0;i<terms.size();i++){if(i>0)predicate.append(" OR ");predicate.append("position(:t").append(i).append(" in lower(s.body))>0");params.addValue("t"+i,terms.get(i).toLowerCase(Locale.ROOT));}
                var segments=access.store().tx(owner,()->access.store().jdbc.query(
                    "SELECT s.body,s.page,s.from_line,s.to_line FROM knowledge.extracted_segment s WHERE s.owner_id=:owner AND s.entry_id=:entry AND s.file_version=:file AND ("+predicate+") ORDER BY s.ordinal LIMIT 8",
                    params,(r,n)->new Object[]{r.getString("body"),r.getObject("page",Integer.class),r.getObject("from_line",Integer.class),r.getObject("to_line",Integer.class)}));
                for(Object[] row:segments){String segment=(String)row[0];int at=firstMatch(segment,terms);if(at<0)continue;
                    String excerpt=segment.substring(Math.max(0,at-80),Math.min(segment.length(),at+320)).strip();
                    if(!excerpt.isEmpty()&&seen.add("file:"+excerpt))add(ranked,new Evidence(entry.id(),entry.revision(),entry.fileVersion(),true,title,excerpt,(Integer)row[1],(Integer)row[2],(Integer)row[3]),terms);
                }
            }
        }
        ranked.sort(Comparator.comparingInt(Ranked::score).reversed());
        List<Evidence> selected=new ArrayList<>();int remaining=maxChars;
        for(Ranked item:ranked){if(selected.size()>=limit||remaining<=0)break;
            Evidence e=item.evidence();int size=Math.min(Math.min(1200,remaining),e.excerpt().length());
            if(size<4)break;
            selected.add(new Evidence(e.entryId(),e.revision(),e.fileVersion(),e.fileSource(),e.title(),e.excerpt().substring(0,size),e.page(),e.fromLine(),e.toLine()));remaining-=size;
        }
        return List.copyOf(selected);
    }
    private static int firstMatch(String text,List<String> terms){String lower=text.toLowerCase(Locale.ROOT);int at=Integer.MAX_VALUE;for(String term:terms){int found=lower.indexOf(term.toLowerCase(Locale.ROOT));if(found>=0)at=Math.min(at,found);}return at==Integer.MAX_VALUE?-1:at;}
    private static void add(List<Ranked> ranked,Evidence evidence,List<String> terms){
        int excerpt=KnowledgeAssistantQuery.overlap(evidence.excerpt(),terms),title=KnowledgeAssistantQuery.overlap(evidence.title(),terms);
        if(excerpt==0&&title<Math.min(2,terms.size()))return;
        if(terms.size()>1&&excerpt+title<2&&terms.stream().noneMatch(term->term.length()>=3&&evidence.excerpt().toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT))))return;
        ranked.add(new Ranked(evidence,excerpt*5+title*2+(evidence.fileSource()?1:0)));
    }
    Location location(long owner,Evidence evidence,String term){
        Entry entry=access.store().read(owner,evidence.entryId(),false);
        if(entry.revision()!=evidence.revision()||!Objects.equals(entry.fileVersion(),evidence.fileVersion()))return null;
        String body=evidence.fileSource()?evidence.excerpt():entry.content().body();
        if(!body.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)))return null;
        Map<UUID,Label> labels=new HashMap<>();
        for(Label label:access.store().labels(owner))labels.put(label.id(),label);
        Label category=labels.get(entry.content().categoryId());
        List<String> tags=entry.content().tagIds().stream().map(labels::get).filter(Objects::nonNull).map(Label::name).toList();
        return new Location(category==null?null:category.name(),tags,KnowledgeAssistantQuery.section(body,term));
    }
    boolean current(long owner,Evidence evidence){
        try {Entry entry=access.store().read(owner,evidence.entryId(),false);
            return entry.revision()==evidence.revision()&&Objects.equals(entry.fileVersion(),evidence.fileVersion())
                &&(!evidence.fileSource()||fileReady(owner,entry.id(),evidence.fileVersion()));}
        catch(RuntimeException changed){return false;}
    }
    private boolean fileReady(long owner,UUID entry,UUID file){
        return access.store().tx(owner,()->Boolean.TRUE.equals(access.store().jdbc.queryForObject(
            "SELECT EXISTS(SELECT FROM knowledge.entry e JOIN knowledge.processing_job j ON j.owner_id=e.owner_id AND j.entry_id=e.id AND j.file_version=e.current_file_version WHERE e.owner_id=:owner AND e.id=:entry AND e.current_file_version=:file AND e.deleted_at IS NULL AND j.state='READY')",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("owner",owner).addValue("entry",entry).addValue("file",file),Boolean.class)));
    }
    /** The owner row lock serializes all reservations across JVMs. A timed-out call remains charged. */
    boolean reserve(long owner,long account,UUID id,KnowledgeAssistantProperties properties){
        return access.store().tx(owner,()->{
            access.store().lockOwner(owner);
            var jdbc=access.store().jdbc;
            var base=new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("owner",owner).addValue("account",account);
            jdbc.update("UPDATE knowledge_assistant.usage SET state='UNCERTAIN',updated_at=now() WHERE owner_id=:owner AND state='IN_FLIGHT' AND created_at<now()-interval '2 minutes'",base);
            Long site=jdbc.queryForObject("SELECT count(*) FROM knowledge_assistant.usage WHERE owner_id=:owner AND usage_day=(now() AT TIME ZONE 'UTC')::date AND state<>'REJECTED'",base,Long.class);
            Long user=jdbc.queryForObject("SELECT count(*) FROM knowledge_assistant.usage WHERE owner_id=:owner AND account_id=:account AND usage_day=(now() AT TIME ZONE 'UTC')::date AND state<>'REJECTED'",base,Long.class);
            Long concurrent=jdbc.queryForObject("SELECT count(*) FROM knowledge_assistant.usage WHERE owner_id=:owner AND state='IN_FLIGHT'",base,Long.class);
            if(site>=properties.getDailySite()||user>=properties.getDailyPerAccount()||concurrent>=properties.getMaxConcurrent())return false;
            jdbc.update("INSERT INTO knowledge_assistant.usage(owner_id,id,account_id,usage_day,state) VALUES(:owner,:id,:account,(now() AT TIME ZONE 'UTC')::date,'IN_FLIGHT')",base.addValue("id",id));
            return true;
        });
    }
    void finish(long owner,UUID id,boolean completed){
        access.store().tx(owner,()->{access.store().jdbc.update("UPDATE knowledge_assistant.usage SET state=:state,updated_at=now() WHERE owner_id=:owner AND id=:id AND state='IN_FLIGHT'",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("owner",owner).addValue("id",id).addValue("state",completed?"COMPLETED":"UNCERTAIN"));return null;});
    }
}
