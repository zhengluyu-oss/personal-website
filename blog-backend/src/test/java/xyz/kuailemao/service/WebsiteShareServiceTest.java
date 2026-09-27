package xyz.kuailemao.service;

import org.junit.jupiter.api.Test;
import xyz.kuailemao.domain.dto.WebsiteShareDTO;
import xyz.kuailemao.domain.entity.WebsiteShare;
import xyz.kuailemao.service.impl.WebsiteShareServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebsiteShareServiceTest {
    private WebsiteShareDTO valid() {
        WebsiteShareDTO dto = new WebsiteShareDTO();
        dto.setTitle("网站"); dto.setSiteUrl("https://example.com/");
        dto.setSummary("简介"); dto.setContent("完整正文");
        return dto;
    }

    @Test void newShareDefaultsToDraftAndDoesNotSetMigrationIdentity() {
        WebsiteShareServiceImpl service = spy(new WebsiteShareServiceImpl());
        doAnswer(call -> { ((WebsiteShare) call.getArgument(0)).setId(9L); return true; }).when(service).save(any(WebsiteShare.class));
        assertEquals(200, service.saveShare(valid()).getCode());
        verify(service).save(argThat(item -> item.getStatus() == 0 && item.getIsDeleted() == 0
                && item.getOrderNum() == 1 && item.getSourceArticleId() == null));
    }

    @Test void rejectsScriptAndCredentialUrlsBeforeWriting() {
        WebsiteShareServiceImpl service = spy(new WebsiteShareServiceImpl());
        for (String url : new String[]{"javascript:alert(1)", "https://user:secret@example.com/", "not-a-url"}) {
            WebsiteShareDTO dto = valid(); dto.setSiteUrl(url);
            assertNotEquals(200, service.saveShare(dto).getCode());
        }
        verify(service, never()).save(any());
    }

    @Test void updatingMissingShareDoesNotCreateIt() {
        WebsiteShareServiceImpl service = spy(new WebsiteShareServiceImpl());
        doReturn(null).when(service).getOne(any());
        WebsiteShareDTO dto = valid(); dto.setId(99L);
        assertNotEquals(200, service.saveShare(dto).getCode());
        verify(service, never()).save(any());
    }

    @Test void emptyDeleteIsRejected() {
        assertNotEquals(200, new WebsiteShareServiceImpl().deleteShares(java.util.List.of()).getCode());
    }
}
