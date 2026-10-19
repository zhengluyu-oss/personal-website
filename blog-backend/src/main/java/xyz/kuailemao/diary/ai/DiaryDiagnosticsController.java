package xyz.kuailemao.diary.ai;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.diary.*;
import xyz.kuailemao.domain.response.ResponseResult;
import java.util.*;
import java.util.concurrent.*;

@RestController
@ConditionalOnProperty(name="diary.enabled",havingValue="true")
@RequestMapping("/diary/auth/ai/diagnostics")
public final class DiaryDiagnosticsController {
    private final DiaryService diary;private final DiaryProperties options;private final DiaryDiagnostics diagnostics;
    private final ScheduledExecutorService cleanup=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"diary-diagnostic-cleanup");t.setDaemon(true);return t;});
    public DiaryDiagnosticsController(DiaryService diary,DiaryProperties options,@Qualifier("diaryJdbcTemplate") NamedParameterJdbcTemplate jdbc,@Qualifier("diaryTransactionManager") DataSourceTransactionManager tx){this.diary=diary;this.options=options;this.diagnostics=new DiaryDiagnostics(jdbc,tx);}
    @EventListener(ApplicationReadyEvent.class) public void start(){cleanup.scheduleWithFixedDelay(()->{try{if(options.getOwnerUserId()!=null)diagnostics.cleanup(options.getOwnerUserId());}catch(RuntimeException ignored){/* Never log database exceptions containing private parameters. */}},0,60,TimeUnit.SECONDS);}
    @PreDestroy public void close(){cleanup.shutdownNow();}
    private long owner(String kind){long owner=diary.requireOwner();diary.store(owner);if(!Set.of("QUESTION","REVIEW","PERIOD").contains(kind))throw DiaryFailure.invalid("诊断类型无效");return owner;}
    private static <T>ResponseEntity<ResponseResult<T>> response(T body){return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Robots-Tag","noindex, nofollow, noarchive").body(ResponseResult.success(body));}
    @GetMapping("/{kind}/{id}") public ResponseEntity<ResponseResult<DiaryDiagnostics.View>> get(@PathVariable String kind,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean body){return response(diagnostics.read(owner(kind),id,kind,body));}
    @DeleteMapping("/{kind}/{id}") public ResponseEntity<ResponseResult<Void>> delete(@PathVariable String kind,@PathVariable UUID id){diagnostics.delete(owner(kind),id,kind);return response(null);}
}
