package xyz.kuailemao.diary.ai;

import org.junit.jupiter.api.Test;
import xyz.kuailemao.diary.DiaryFailure;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

class DiaryAiTextTest {
    @Test void codePointChunksPreserveEveryCharacterAndSourceOffsets() {
        String value=("段落🌊".repeat(400)+"\n\n"+"测试中文🙂".repeat(600));
        var chunks=DiaryAiText.chunks(value);boolean[] covered=new boolean[points(value)];
        assertTrue(chunks.size()>2);
        for(var chunk:chunks) {
            assertTrue(points(chunk.text())<=1000);assertEquals(slice(value,chunk.start(),chunk.end()),chunk.text());
            assertEquals(DiaryAiText.hash(chunk.text()),chunk.sha256());
            for(int i=chunk.start();i<chunk.end();i++)covered[i]=true;
        }
        for(boolean present:covered)assertTrue(present);
        assertTrue(DiaryAiText.chunks("\n  ").isEmpty());
    }
    @Test void scopeRequiresExclusiveCompleteAndUniqueSelection() {
        var id=UUID.randomUUID();new Scope(List.of(id),null,null,null).validate(true);
        assertThrows(DiaryFailure.class,()->new Scope(List.of(id,id),null,null,null).validate(true));
        assertThrows(DiaryFailure.class,()->new Scope(List.of(id),"created",LocalDate.now(),LocalDate.now()).validate(true));
        assertThrows(DiaryFailure.class,()->new Scope(null,"covered",null,null).validate(false));
        assertThrows(DiaryFailure.class,()->new Scope(null,"created",LocalDate.of(2026,2,2),LocalDate.of(2026,2,1)).validate(true));
        new Scope(null,"covered",LocalDate.of(2024,2,29),LocalDate.of(2024,3,1)).validate(true);
    }
    @Test void profileChangesWhenModelDimensionOrChunkingIdentityChanges() {
        var p=new DiaryAiProperties();p.setProvider("fixture");p.setDimensions(3);p.setEmbeddingModel("one");
        var before=DiaryAiText.profile(p);p.setDimensions(4);assertNotEquals(before,DiaryAiText.profile(p));
        p.setDimensions(3);p.setEmbeddingModel("two");assertNotEquals(before,DiaryAiText.profile(p));
        assertFalse(p.configured());assertFalse(p.isEnabled());
    }
}
