package xyz.luyu.knowledge;

import org.junit.jupiter.api.Test;
import xyz.luyu.domain.dto.ArticleDTO;
import xyz.luyu.domain.entity.Article;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.mapper.ArticleMapper;
import xyz.luyu.mapper.KnowledgeArticleExportMapper;
import xyz.luyu.service.ArticleService;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeArticleDraftWriterTest {
    @Test void existingMappingReturnsOriginalDraftAndRejectsChangedDigestOrDeletion() {
        var mappings=mock(KnowledgeArticleExportMapper.class);
        var articles=mock(ArticleMapper.class);
        var service=mock(ArticleService.class);
        UUID export=UUID.randomUUID();
        when(mappings.find(7,export.toString())).thenReturn(new KnowledgeArticleExportMapper.Mapping(7,export.toString(),"digest",44));
        var draft=Article.builder().id(44L).userId(7L).status(3).isDeleted(0).build();
        when(articles.selectById(44L)).thenReturn(draft);
        var writer=new KnowledgeArticleDraftWriter(mappings,articles,service);
        var request=new ArticleDTO();request.setStatus(3);
        assertEquals(44L,writer.create(7,export,"digest",request));
        assertEquals(409,assertThrows(KnowledgeFailure.class,()->writer.create(7,export,"changed",request)).status());
        draft.setIsDeleted(1);
        assertEquals(410,assertThrows(KnowledgeFailure.class,()->writer.create(7,export,"digest",request)).status());
        verifyNoInteractions(service);
    }

    @Test void newDraftKeepsForcedDraftStatusAndRecordsMappingOnlyAfterSave() {
        var mappings=mock(KnowledgeArticleExportMapper.class);
        var articles=mock(ArticleMapper.class);
        var service=mock(ArticleService.class);
        UUID export=UUID.randomUUID();
        when(service.publish(any())).thenAnswer(invocation->{
            ArticleDTO value=invocation.getArgument(0);
            assertEquals(3,value.getStatus());assertNotNull(value.getId());
            when(articles.selectById(value.getId())).thenReturn(Article.builder().id(value.getId()).userId(7L).status(3).isDeleted(0).build());
            return ResponseResult.success();
        });
        when(mappings.insert(eq(7L),eq(export.toString()),eq("digest"),anyLong())).thenReturn(1);
        var request=new ArticleDTO();request.setStatus(3);
        long id=new KnowledgeArticleDraftWriter(mappings,articles,service).create(7,export,"digest",request);
        assertEquals(request.getId(),id);
        verify(mappings).insert(7,export.toString(),"digest",id);
        var invalid=new ArticleDTO();invalid.setId(id);invalid.setStatus(1);
        assertEquals(400,assertThrows(KnowledgeFailure.class,()->new KnowledgeArticleDraftWriter(mappings,articles,service).create(7,UUID.randomUUID(),"digest",invalid)).status());
    }
}
