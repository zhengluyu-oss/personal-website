package xyz.luyu.knowledge;

import com.aliyun.oss.model.OSSObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeParseRunnerTest {
    @TempDir Path temporary;
    private KnowledgePrivateObjects objects(){
        var storage=mock(KnowledgePrivateObjects.class);
        var object=mock(OSSObject.class);
        when(storage.open("knowledge/private/objects/fixture")).thenReturn(object);
        when(object.getObjectContent()).thenAnswer(invocation->new ByteArrayInputStream("hello".getBytes()));
        return storage;
    }
    private KnowledgeProperties settings(int timeout){
        var properties=new KnowledgeProperties();
        properties.getProcessing().setTemporaryDirectory(temporary.toString());
        properties.getProcessing().setTimeoutSeconds(timeout);
        return properties;
    }
    private List<String> child(String mode){
        String javaBinary=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        String testClasses=Path.of(java.net.URI.create(KnowledgeParserCrashFixture.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm())).toString();
        return List.of(javaBinary,"-Xmx16m","-cp",testClasses,KnowledgeParserCrashFixture.class.getName(),mode);
    }
    private void emptyTemporaryDirectory() throws Exception {
        try(var files=Files.list(temporary)){assertEquals(0,files.count());}
    }
    @Test void timeoutTerminatesChildAndRemovesTemporaryInput() throws Exception {
        var result=new KnowledgeParseRunner(objects(),settings(1),(input,mime,pages)->child("sleep"))
                .run("knowledge/private/objects/fixture","text/plain",5);
        assertEquals("TIMEOUT",result.failure().code());
        assertTrue(result.failure().retryable());
        emptyTemporaryDirectory();
    }
    @Test void outOfMemoryExitIsRetryableAndTemporaryInputIsRemoved() throws Exception {
        var result=new KnowledgeParseRunner(objects(),settings(10),(input,mime,pages)->child("oom"))
                .run("knowledge/private/objects/fixture","text/plain",5);
        assertEquals("RESOURCE_LIMIT",result.failure().code());
        assertTrue(result.failure().retryable());
        emptyTemporaryDirectory();
    }
    @Test void processStartFailureIsRetryableAndTemporaryInputIsRemoved() throws Exception {
        var result=new KnowledgeParseRunner(objects(),settings(1),(input,mime,pages)->List.of("missing-knowledge-parser-executable"))
                .run("knowledge/private/objects/fixture","text/plain",5);
        assertEquals("PARSER_UNAVAILABLE",result.failure().code());
        assertTrue(result.failure().retryable());
        emptyTemporaryDirectory();
    }
}
