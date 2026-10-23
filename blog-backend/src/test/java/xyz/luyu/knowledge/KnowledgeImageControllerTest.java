package xyz.luyu.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static xyz.luyu.knowledge.KnowledgeModels.*;

class KnowledgeImageControllerTest {
    private final KnowledgeAccess access=mock(KnowledgeAccess.class);
    private final KnowledgeStore store=mock(KnowledgeStore.class);
    private final KnowledgePrivateObjects objects=mock(KnowledgePrivateObjects.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<KnowledgePrivateObjects> provider=mock(ObjectProvider.class);
    private final KnowledgeProperties properties=new KnowledgeProperties();
    private final UUID entryId=UUID.randomUUID();
    private KnowledgeImageController controller(){
        when(access.requireManage()).thenReturn(1L);
        when(access.requireRead()).thenReturn(1L);
        when(access.store()).thenReturn(store);
        when(provider.getIfAvailable()).thenReturn(objects);
        when(objects.ready()).thenReturn(true);
        when(objects.newKey()).thenReturn("knowledge/private/objects/"+UUID.randomUUID());
        when(store.read(1L,entryId,false)).thenReturn(new Entry(entryId,1,
            new Content(Kind.NOTE,"图片测试","","",null,List.of()),null,Instant.now(),Instant.now(),null));
        return new KnowledgeImageController(access,properties,provider);
    }
    private MockMultipartFile png() throws Exception {
        var output=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB),"png",output);
        return new MockMultipartFile("file","test.png","image/png",output.toByteArray());
    }
    @Test void uploadUsesPrivateStoreAndReturnsOpaqueId() throws Exception {
        var controller=controller();UUID id=UUID.randomUUID();
        when(store.attachImage(eq(1L),eq(entryId),any(),any(),anyString(),eq("image/png"),anyLong(),eq(8),eq(8),anyString(),anyLong()))
            .thenReturn(new KnowledgeStore.ImageCommit(id,true));
        assertEquals(id,controller.upload(entryId,UUID.randomUUID(),png()).getBody().getData());
        verify(objects).putChecked(anyString(),any(InputStream.class),anyLong(),eq("image/png"));
        verify(objects,never()).delete(anyString());
    }
    @Test void storageFailureDoesNotLeaveReference() throws Exception {
        var controller=controller();
        when(store.attachImage(eq(1L),eq(entryId),any(),any(),anyString(),anyString(),anyLong(),anyInt(),anyInt(),anyString(),anyLong()))
            .thenThrow(KnowledgeFailure.conflict());
        assertEquals(409,assertThrows(KnowledgeFailure.class,()->controller.upload(entryId,UUID.randomUUID(),png())).status());
        verify(objects).delete(anyString());
    }
    @Test void rejectsFakeImageBeforeObjectWrite() {
        var controller=controller();
        var fake=new MockMultipartFile("file","test.png","image/png","<svg onload=alert(1)>".getBytes());
        assertEquals(400,assertThrows(KnowledgeFailure.class,()->controller.upload(entryId,UUID.randomUUID(),fake)).status());
        verify(objects,never()).putChecked(anyString(),any(InputStream.class),anyLong(),anyString());
    }
    @Test void revokedReaderCannotOpenObject() {
        var controller=controller();
        when(access.requireRead()).thenThrow(new KnowledgeFailure(403,"no access"));
        assertEquals(403,assertThrows(KnowledgeFailure.class,()->controller.image(UUID.randomUUID())).status());
        verify(objects,never()).open(anyString());
    }
}
