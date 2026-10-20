package xyz.kuailemao.knowledge;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.service.SiteModuleAccessService;
import xyz.kuailemao.utils.SecurityUtils;
import static xyz.kuailemao.knowledge.KnowledgeModels.*;

@Service
public final class KnowledgeAccess {
    private final KnowledgeProperties properties;private final SiteModuleAccessService modules;
    private final UserMapper users;private final ObjectProvider<KnowledgeStore> stores;
    public KnowledgeAccess(KnowledgeProperties properties,SiteModuleAccessService modules,UserMapper users,ObjectProvider<KnowledgeStore> stores){this.properties=properties;this.modules=modules;this.users=users;this.stores=stores;}
    public boolean active(long id){var u=users.selectById(id);return u!=null&&Integer.valueOf(0).equals(u.getIsDisable())&&Integer.valueOf(0).equals(u.getIsDeleted());}
    public boolean canRead(){long id=SecurityUtils.getUserId();return (id<=0||active(id))&&modules.canView("knowledge");}
    public boolean canManage(){long id=SecurityUtils.getUserId();return properties.getOwnerUserId()!=null&&id==properties.getOwnerUserId()&&active(id)&&modules.canView("knowledge");}
    public long owner(){Long owner=properties.getOwnerUserId();if(owner==null||owner<=0)throw KnowledgeFailure.unavailable();return owner;}
    public long requireRead(){if(!canRead())throw new KnowledgeFailure(SecurityUtils.isLogin()?403:401,"没有查看知识库的权限");return owner();}
    public long requireManage(){if(!canManage())throw new KnowledgeFailure(SecurityUtils.isLogin()?403:401,"仅知识库站主可执行此操作");return owner();}
    public KnowledgeStore store(){if(!properties.configured())throw KnowledgeFailure.unavailable();var store=stores.getIfAvailable();if(store==null||!store.available(owner()))throw KnowledgeFailure.unavailable();return store;}
    public Capabilities capabilities(){boolean allowed=canRead(),ready=false;if(allowed&&properties.configured()){var store=stores.getIfAvailable();ready=store!=null&&store.available(owner());}return new Capabilities(allowed,ready,allowed&&canManage(),false,200,2097152L,properties.getProcessing().getPdfBytes(),properties.getProcessing().getTextBytes(),properties.getStorage().getQuotaBytes(),!allowed?"知识库需要模块授权":!ready?"知识库尚未启用或暂不可用":"笔记与资料库；来源链接仅记录，不会抓取");}
}
