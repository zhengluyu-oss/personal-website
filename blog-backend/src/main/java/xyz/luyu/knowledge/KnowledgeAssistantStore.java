package xyz.luyu.knowledge;

import org.springframework.stereotype.Component;
import java.util.*;
import static xyz.luyu.knowledge.KnowledgeModels.*;

/** Uses the knowledge owner's existing RLS transaction; usage lives in a separate schema. */
@Component
public final class KnowledgeAssistantStore {
    public record Evidence(UUID entryId,long revision,UUID fileVersion,boolean fileSource,String title,String excerpt,Integer page,Integer fromLine,Integer toLine) {}
    private final KnowledgeAccess access;
    KnowledgeAssistantStore(KnowledgeAccess access){this.access=access;}
    boolean ready(long owner){
        try {return access.store().tx(owner,()->Boolean.TRUE.equals(access.store().jdbc.queryForObject(
            "SELECT EXISTS(SELECT FROM knowledge.schema_version WHERE version=4) AND current_user='knowledge_runtime' AND EXISTS(SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='knowledge_assistant' AND c.relname='usage' AND c.relrowsecurity AND c.relforcerowsecurity AND r.rolname='knowledge_migrator')",
            Map.of(),Boolean.class)));}
        catch(RuntimeException unavailable){return false;}
    }
    List<Evidence> find(long owner,List<String> terms,int limit,int maxChars){
        var collected=new LinkedHashMap<UUID,Evidence>();int remaining=maxChars;
        for(String term:terms){
            Filter filter=new Filter(null,null,null,null,null,term,1,Math.min(8,limit*2),false);
            for(SearchHit hit:access.store().search(owner,filter).records()){
                if(collected.containsKey(hit.entry().id())||collected.size()>=limit)continue;
                Entry entry=access.store().read(owner,hit.entry().id(),false);
                String excerpt=Objects.toString(hit.excerpt(),"").strip();
                boolean bodyMatch=entry.content().body().toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
                boolean fileSource=!excerpt.isEmpty()&&!bodyMatch&&entry.fileVersion()!=null;
                UUID file=entry.fileVersion();
                if(excerpt.isEmpty()&&entry.content().title().toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)))excerpt=entry.content().body().strip();
                if(excerpt.isEmpty()||fileSource&&!fileReady(owner,entry.id(),file))continue;
                int size=Math.min(Math.min(1200,remaining),excerpt.length());if(size<1)return List.copyOf(collected.values());
                collected.put(entry.id(),new Evidence(entry.id(),entry.revision(),file,fileSource,entry.content().title(),excerpt.substring(0,size),hit.page(),hit.fromLine(),hit.toLine()));
                remaining-=size;if(remaining<=0||collected.size()>=limit)return List.copyOf(collected.values());
            }
        }
        return List.copyOf(collected.values());
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
