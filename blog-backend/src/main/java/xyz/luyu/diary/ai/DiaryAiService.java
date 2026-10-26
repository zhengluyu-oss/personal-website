package xyz.luyu.diary.ai;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import xyz.luyu.diary.*;
import static xyz.luyu.diary.ai.DiaryAiModels.*;

@Service
public final class DiaryAiService {
    private final DiaryService diary;private final DiaryAiProperties p;private final ObjectProvider<DiaryAiStore> stores;
    private final ObjectProvider<DiaryAiIndex> indexes;
    public DiaryAiService(DiaryService diary,DiaryAiProperties p,ObjectProvider<DiaryAiStore> stores,ObjectProvider<DiaryAiIndex> indexes){this.diary=diary;this.p=p;this.stores=stores;this.indexes=indexes;}
    public long owner(){return diary.requireOwner();}
    public Capabilities capabilities(){long owner=owner();if(!p.isEnabled())return new Capabilities(false,false,"AI 功能未开启，日记仍可正常保存");
        try{store(owner);return new Capabilities(true,true,"云端服务："+p.getProvider()+"；生成模型："+p.getGenerationModel()+"；数据政策："+p.getDataPolicy()+"。请先阅读数据流说明，再手动建立索引。");}
        catch(DiaryFailure e){return new Capabilities(true,false,"AI 配置或私密数据库尚未准备好，日记保存不受影响");}}
    public DiaryAiStore store(long owner){diary.store(owner);DiaryAiStore s=stores.getIfAvailable();if(!p.configured() || s==null || !s.available(owner))throw new DiaryFailure(503,"AI 暂不可用，日记保存不受影响");s.ensureProfile(owner);return s;}
    public DiaryAiIndex index(long owner){store(owner);DiaryAiIndex i=indexes.getIfAvailable();if(i==null)throw DiaryFailure.unavailable();return i;}
}
