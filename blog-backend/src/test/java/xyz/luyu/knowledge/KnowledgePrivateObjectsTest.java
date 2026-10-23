package xyz.luyu.knowledge;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.OSSObjectSummary;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import xyz.luyu.config.OssProperties;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgePrivateObjectsTest {
    @Test void cleanupDeletesOnlyAgedUnregisteredKnowledgeObjects(){
        var access=mock(KnowledgeAccess.class);
        var store=mock(KnowledgeStore.class);
        var objects=mock(KnowledgePrivateObjects.class);
        when(objects.ready()).thenReturn(true);
        when(access.owner()).thenReturn(7L);
        when(access.store()).thenReturn(store);
        when(store.staleFiles(7L)).thenReturn(List.of());
        String registered="knowledge/private/objects/registered";
        String orphan="knowledge/private/objects/orphan";
        when(objects.oldObjectKeys(any(Instant.class))).thenReturn(List.of(registered,orphan));
        when(store.registeredObjectKey(7L,registered)).thenReturn(true);
        var processing=new KnowledgeProcessing(access,objects,mock(RabbitTemplate.class),new KnowledgeProperties());
        processing.cleanExpiredObjects();
        verify(objects,never()).delete(registered);
        verify(objects).delete(orphan);
    }
    @Test void orphanInventoryOnlyReturnsAgedKnowledgeObjects(){
        OSS oss=mock(OSS.class);
        ObjectListing listing=mock(ObjectListing.class);
        when(oss.listObjects(any(ListObjectsRequest.class))).thenReturn(listing);
        Instant now=Instant.now();
        OSSObjectSummary old=summary("knowledge/private/objects/old",now.minusSeconds(172800));
        OSSObjectSummary fresh=summary("knowledge/private/objects/fresh",now.minusSeconds(60));
        OSSObjectSummary other=summary("public/article-cover",now.minusSeconds(172800));
        when(listing.getObjectSummaries()).thenReturn(List.of(old,fresh,other));
        var site=new OssProperties();site.setBucketName("existing-bucket");
        var knowledge=new KnowledgeProperties();
        var objects=new KnowledgePrivateObjects(oss,site,knowledge);
        assertEquals(List.of("knowledge/private/objects/old"),objects.oldObjectKeys(now.minusSeconds(86400)));
        var request=org.mockito.ArgumentCaptor.forClass(ListObjectsRequest.class);
        verify(oss).listObjects(request.capture());
        assertEquals("existing-bucket",request.getValue().getBucketName());
        assertEquals("knowledge/private/objects/",request.getValue().getPrefix());
        verify(oss,never()).deleteObject(any(String.class),any(String.class));
    }
    @Test void inventoryAdvancesPastRegisteredFirstPageWithoutCrossingPrefix(){
        OSS oss=mock(OSS.class);
        ObjectListing first=mock(ObjectListing.class),second=mock(ObjectListing.class);
        when(oss.listObjects(any(ListObjectsRequest.class))).thenReturn(first,second);
        Instant old=Instant.now().minusSeconds(172800);
        OSSObjectSummary a=summary("knowledge/private/objects/a",old);
        OSSObjectSummary b=summary("knowledge/private/objects/b",old);
        when(first.getObjectSummaries()).thenReturn(List.of(a));
        when(first.isTruncated()).thenReturn(true);
        when(second.getObjectSummaries()).thenReturn(List.of(b));
        var site=new OssProperties();site.setBucketName("existing-bucket");
        var objects=new KnowledgePrivateObjects(oss,site,new KnowledgeProperties());
        assertEquals(List.of("knowledge/private/objects/a","knowledge/private/objects/b"),
            objects.oldObjectKeys(Instant.now().minusSeconds(86400)));
        var requests=org.mockito.ArgumentCaptor.forClass(ListObjectsRequest.class);
        verify(oss,times(2)).listObjects(requests.capture());
        assertEquals("knowledge/private/objects/a",requests.getAllValues().get(1).getMarker());
    }
    @Test void inventoryResumesAfterItsPageBudgetAcrossScheduledRuns(){
        OSS oss=mock(OSS.class);
        var pages=new ArrayList<ObjectListing>();
        Instant old=Instant.now().minusSeconds(172800);
        for(int i=0;i<21;i++){
            ObjectListing listing=mock(ObjectListing.class);
            OSSObjectSummary object=summary("knowledge/private/objects/"+i,old);
            when(listing.getObjectSummaries()).thenReturn(List.of(object));
            when(listing.isTruncated()).thenReturn(i<20);
            if(i<20)when(listing.getNextMarker()).thenReturn("marker-"+i);
            pages.add(listing);
        }
        when(oss.listObjects(any(ListObjectsRequest.class))).thenAnswer(call->pages.remove(0));
        var site=new OssProperties();site.setBucketName("existing-bucket");
        var objects=new KnowledgePrivateObjects(oss,site,new KnowledgeProperties());
        assertEquals(20,objects.oldObjectKeys(Instant.now().minusSeconds(86400)).size());
        assertEquals(List.of("knowledge/private/objects/20"),objects.oldObjectKeys(Instant.now().minusSeconds(86400)));
        var requests=org.mockito.ArgumentCaptor.forClass(ListObjectsRequest.class);
        verify(oss,times(21)).listObjects(requests.capture());
        assertEquals("marker-19",requests.getAllValues().get(20).getMarker());
    }
    private static OSSObjectSummary summary(String key,Instant modified){
        OSSObjectSummary object=mock(OSSObjectSummary.class);
        when(object.getKey()).thenReturn(key);
        when(object.getLastModified()).thenReturn(Date.from(modified));
        return object;
    }
}
