package xyz.kuailemao.diary;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import xyz.kuailemao.service.SiteModuleAccessService;
import xyz.kuailemao.utils.SecurityUtils;

/** Module permission is the viewer's identity; PG ownership is always the configured author. */
@Service
public final class DiaryModuleAccess {
    private final DiaryProperties properties;
    private final SiteModuleAccessService modules;
    private final ObjectProvider<DiaryStore> stores;
    public DiaryModuleAccess(DiaryProperties properties, SiteModuleAccessService modules, ObjectProvider<DiaryStore> stores) {
        this.properties=properties; this.modules=modules; this.stores=stores;
    }
    public record Capabilities(boolean allowed, boolean available, boolean canManage) {}
    public long requireViewer() {
        if (!modules.canView("diary")) throw new DiaryFailure(SecurityUtils.isLogin()?403:401,"没有查看日记的权限");
        if (!properties.configured()) throw DiaryFailure.unavailable();
        return properties.getOwnerUserId();
    }
    public Capabilities capabilities() {
        boolean allowed=modules.canView("diary");
        if (!allowed) return new Capabilities(false,false,false);
        DiaryStore store=stores.getIfAvailable();
        boolean available=properties.configured() && store!=null && store.available(properties.getOwnerUserId());
        return new Capabilities(true,available,SecurityUtils.isLogin() && properties.getOwnerUserId()!=null
                && properties.getOwnerUserId().equals(SecurityUtils.getUserId()));
    }
}
