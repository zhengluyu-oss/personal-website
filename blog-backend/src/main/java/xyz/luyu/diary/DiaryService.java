package xyz.luyu.diary;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import xyz.luyu.utils.SecurityUtils;
import static xyz.luyu.diary.DiaryModels.*;

@Service
public class DiaryService {
    private final DiaryProperties properties;
    private final ObjectProvider<DiaryStore> stores;
    public DiaryService(DiaryProperties properties,ObjectProvider<DiaryStore> stores) {
        this.properties=properties;this.stores=stores;
    }
    public long requireOwner() {
        long owner=SecurityUtils.getUserId();
        if(owner<1) throw new DiaryFailure(401,"请先登录");
        if(properties.getOwnerUserId()==null) throw DiaryFailure.unavailable();
        if(owner!=properties.getOwnerUserId()) throw new DiaryFailure(403,"当前账号无权访问日记");
        return owner;
    }
    public Capabilities capabilities() {
        long id=SecurityUtils.getUserId();
        if(id<1) throw new DiaryFailure(401,"请先登录");
        boolean allowed=properties.getOwnerUserId()!=null && id==properties.getOwnerUserId();
        DiaryStore store=stores.getIfAvailable();
        return new Capabilities(allowed,allowed && properties.configured() && store!=null && store.available(id));
    }
    public DiaryStore store(long owner) {
        DiaryStore store=stores.getIfAvailable();
        if(!properties.configured() || store==null || !store.available(owner)) throw DiaryFailure.unavailable();
        return store;
    }
}
