package xyz.luyu.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static xyz.luyu.knowledge.KnowledgeModels.*;

class KnowledgeFileUploadTest {
    private final KnowledgeAccess access=mock(KnowledgeAccess.class);
    private final KnowledgeStore store=mock(KnowledgeStore.class);
    private final KnowledgePrivateObjects objects=mock(KnowledgePrivateObjects.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<KnowledgePrivateObjects> provider=mock(ObjectProvider.class);
    private final KnowledgeProperties properties=new KnowledgeProperties();
    private final UUID entryId=UUID.randomUUID();

    private KnowledgeFileController controller(){
        properties.getProcessing().setEnabled(true);
        when(access.requireManage()).thenReturn(7L);
        when(access.store()).thenReturn(store);
        when(provider.getIfAvailable()).thenReturn(objects);
        when(objects.ready()).thenReturn(true);
        when(store.read(7L,entryId,false)).thenReturn(new Entry(entryId,1,
            new Content(Kind.DOCUMENT,"测试文件","","",null,List.of()),null,Instant.now(),Instant.now(),null));
        return new KnowledgeFileController(access,properties,provider);
    }

    @Test void interruptedRequestNeverCreatesAnObjectOrDatabaseVersion() throws Exception {
        var controller=controller();
        MultipartFile file=mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("notes.txt");
        when(file.getSize()).thenReturn(12L);
        when(file.getInputStream()).thenReturn(new InputStream(){
            boolean once;
            @Override public int read(byte[] buffer,int offset,int length) throws IOException {
                if(once)throw new IOException("upload interrupted");
                once=true;buffer[offset]='a';return 1;
            }
            @Override public int read() throws IOException {throw new IOException("upload interrupted");}
        });
        assertEquals(503,assertThrows(KnowledgeFailure.class,
            ()->controller.upload(entryId,1,UUID.randomUUID(),file)).status());
        verify(objects,never()).putChecked(anyString(),any(InputStream.class),anyLong(),anyString());
        verify(store,never()).attachFile(anyLong(),any(),anyLong(),any(),any(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyLong());
    }

    @Test void databaseRejectionDeletesThePrivateObject() throws Exception {
        var controller=controller();
        MultipartFile file=mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("notes.txt");
        when(file.getSize()).thenReturn(5L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("hello".getBytes()));
        String key="knowledge/private/objects/"+UUID.randomUUID();
        when(objects.newKey()).thenReturn(key);
        when(store.attachFile(anyLong(),any(),anyLong(),any(),any(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyLong()))
            .thenThrow(KnowledgeFailure.conflict());
        assertEquals(409,assertThrows(KnowledgeFailure.class,
            ()->controller.upload(entryId,1,UUID.randomUUID(),file)).status());
        verify(objects).putChecked(eq(key),any(InputStream.class),eq(5L),eq("text/plain"));
        verify(objects).delete(key);
    }
}
