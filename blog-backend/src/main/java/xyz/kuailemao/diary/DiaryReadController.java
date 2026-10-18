package xyz.kuailemao.diary;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.domain.response.ResponseResult;
import java.time.YearMonth;
import java.util.UUID;
import static xyz.kuailemao.diary.DiaryModels.*;

/** Explicit read allowlist; sharing never reaches owner management, jobs or model services. */
@RestController
@RequestMapping("/diary/view")
public final class DiaryReadController {
    private final DiaryModuleAccess access;
    private final DiaryService diary;
    private final ObjectProvider<DiaryReadStore> readers;
    public DiaryReadController(DiaryModuleAccess access,DiaryService diary,ObjectProvider<DiaryReadStore> readers) {
        this.access=access;this.diary=diary;this.readers=readers;
    }
    @GetMapping("/capabilities") public ResponseResult<DiaryModuleAccess.Capabilities> capabilities() {return ResponseResult.success(access.capabilities());}
    private DiaryReadStore reader(long owner) {diary.store(owner);var reader=readers.getIfAvailable();if(reader==null) throw DiaryFailure.unavailable();return reader;}
    @PostMapping("/entries/search") public ResponseResult<Page<Entry>> search(@RequestBody Search search) {
        long owner=access.requireViewer();
        if(Boolean.TRUE.equals(search.deleted())) throw new DiaryFailure(403,"只读访问不提供已删除记录");
        return ResponseResult.success(diary.store(owner).search(owner,new Search(search.axis(),search.from(),search.to(),search.keyword(),search.page(),search.pageSize(),false)));
    }
    @GetMapping("/entries/{id}") public ResponseResult<Entry> read(@PathVariable UUID id) {
        long owner=access.requireViewer();Entry value=diary.store(owner).read(owner,id);
        if(value.deletedAt()!=null) throw new DiaryFailure(404,"日记不存在或不可查看");return ResponseResult.success(value);
    }
    @GetMapping("/calendar") public ResponseResult<java.util.List<CalendarDay>> calendar(@RequestParam String month) {
        long owner=access.requireViewer();return ResponseResult.success(diary.store(owner).calendar(owner,YearMonth.parse(month)));
    }
    @GetMapping("/reports/{type:ai|period}") public ResponseResult<Page<DiaryReadStore.Summary>> reports(@PathVariable String type,@RequestParam(defaultValue="1") int page) {
        long owner=access.requireViewer();return ResponseResult.success(reader(owner).reports(owner,type,page));
    }
    @GetMapping("/reports/{type:ai|period}/{id}") public ResponseResult<DiaryReadStore.Report> report(@PathVariable String type,@PathVariable UUID id) {
        long owner=access.requireViewer();return ResponseResult.success(reader(owner).report(owner,type,id));
    }
    @GetMapping("/coaching/{type:goals|actions|feedback}") public ResponseResult<Page<DiaryReadStore.CoachingRow>> coaching(@PathVariable String type,@RequestParam(defaultValue="1") int page) {
        long owner=access.requireViewer();return ResponseResult.success(reader(owner).coaching(owner,type,page));
    }
}
