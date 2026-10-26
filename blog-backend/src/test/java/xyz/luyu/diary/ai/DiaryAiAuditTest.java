package xyz.luyu.diary.ai;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class DiaryAiAuditTest {
    @Test void unsafeFreeTextNeverReachesAuditLine(CapturedOutput output) {
        String privateText="PRIVATE_DIARY_PROBE";
        DiaryAiAudit.event(UUID.fromString("00000000-0000-0000-0000-000000000123"),privateText,"VALIDATION",privateText,12,8L,5L,true);
        assertFalse(output.getAll().contains(privateText));
        assertTrue(output.getAll().contains("possibly_charged=true"));
    }
}
