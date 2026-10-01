package xyz.kuailemao.service.impl;

import org.junit.jupiter.api.Test;
import xyz.kuailemao.domain.dto.TagDTO;
import xyz.kuailemao.domain.entity.Tag;
import xyz.kuailemao.domain.response.ResponseResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class TagServiceImplCreateTest {
    @Test
    void articleEditorReceivesDatabaseGeneratedTagId() {
        TagServiceImpl service = spy(new TagServiceImpl());
        doAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            assertNull(tag.getId());
            tag.setId(73L);
            return true;
        }).when(service).save(any(Tag.class));

        ResponseResult<Long> result = service.addTag(new TagDTO().setId(999L).setTagName("新标签"));

        assertEquals(200, result.getCode());
        assertEquals(73L, result.getData());
    }
}
