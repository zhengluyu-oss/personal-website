package xyz.luyu.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.luyu.config.OssProperties;
import xyz.luyu.domain.dto.DeletePhotoOrAlbumDTO;
import xyz.luyu.domain.entity.Photo;
import xyz.luyu.mapper.PhotoMapper;
import xyz.luyu.storage.FileStorageService;
import xyz.luyu.utils.FileUploadUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhotoDeletionBoundaryTest {
    private PhotoServiceImpl service; private PhotoMapper mapper; private FileStorageService storage;
    private static final String KEY = "photoAlbum/parent/child/synthetic.png";
    @BeforeEach void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Photo.class);
        service = new PhotoServiceImpl(); mapper = mock(PhotoMapper.class); storage = mock(FileStorageService.class);
        FileUploadUtils files = new FileUploadUtils(); OssProperties properties = new OssProperties();
        properties.setDomain("https://media.example.invalid");
        ReflectionTestUtils.setField(files, "ossProperties", properties); ReflectionTestUtils.setField(files, "fileStorageService", storage);
        ReflectionTestUtils.setField(service, "fileUploadUtils", files); ReflectionTestUtils.setField(service, "photoMapper", mapper);
        when(mapper.selectById(9L)).thenReturn(Photo.builder().id(9L).parentId(8L).type(2).url("https://media.example.invalid/" + KEY).build());
        when(mapper.deleteById(9L)).thenReturn(1);
    }
    private DeletePhotoOrAlbumDTO request() {
        DeletePhotoOrAlbumDTO dto = new DeletePhotoOrAlbumDTO(); dto.setId(9L); dto.setParentId(8L); dto.setType(2); return dto;
    }
    @Test void nestedOrRenamedAlbumUsesTheSavedFullKey() {
        when(storage.exists(KEY)).thenReturn(true, false);
        assertEquals(200, service.deletePhotoOrAlbum(request()).getCode());
        verify(storage).delete(KEY); verify(mapper, never()).selectById(8L); verify(mapper).deleteById(9L);
    }
    @Test void wrongTypeOrParentDoesNotTouchStorageOrDatabase() {
        DeletePhotoOrAlbumDTO type = request(); type.setType(1);
        assertNotEquals(200, service.deletePhotoOrAlbum(type).getCode());
        DeletePhotoOrAlbumDTO parent = request(); parent.setParentId(7L);
        assertNotEquals(200, service.deletePhotoOrAlbum(parent).getCode());
        verifyNoInteractions(storage); verify(mapper, never()).deleteById(anyLong());
    }
    @Test void nonemptyAlbumCannotBeDeletedByLabelingItAsPhoto() {
        when(mapper.selectById(9L)).thenReturn(Photo.builder().id(9L).parentId(8L).type(1).name("renamed").build());
        assertNotEquals(200, service.deletePhotoOrAlbum(request()).getCode());
        DeletePhotoOrAlbumDTO album = request(); album.setType(1);
        when(mapper.selectCount(any())).thenReturn(1L);
        assertNotEquals(200, service.deletePhotoOrAlbum(album).getCode());
        verifyNoInteractions(storage); verify(mapper, never()).deleteById(anyLong());
    }
    @Test void foreignStoredUrlIsRejectedAndTheRecordStays() {
        when(mapper.selectById(9L)).thenReturn(Photo.builder().id(9L).parentId(8L).type(2).url("https://foreign.invalid/" + KEY).build());
        assertNotEquals(200, service.deletePhotoOrAlbum(request()).getCode());
        verifyNoInteractions(storage); verify(mapper, never()).deleteById(anyLong());
    }
    @Test void missingRecordIsAnExplicitFailure() {
        when(mapper.selectById(9L)).thenReturn(null);
        assertNotEquals(200, service.deletePhotoOrAlbum(request()).getCode());
        verifyNoInteractions(storage); verify(mapper, never()).deleteById(anyLong());
    }
    @Test void storageFailureKeepsThePhotoRecord() {
        when(storage.exists(KEY)).thenReturn(true);
        doThrow(new IllegalStateException("synthetic OSS failure")).when(storage).delete(KEY);
        assertNotEquals(200, service.deletePhotoOrAlbum(request()).getCode());
        verify(mapper, never()).deleteById(anyLong());
    }
    @Test void databaseFailureAfterObjectRemovalIsRetryable() {
        when(storage.exists(KEY)).thenReturn(true, false);
        when(mapper.deleteById(9L)).thenThrow(new IllegalStateException("synthetic database failure"));
        assertThrows(IllegalStateException.class, () -> service.deletePhotoOrAlbum(request()));
        doReturn(1).when(mapper).deleteById(9L);
        assertEquals(200, service.deletePhotoOrAlbum(request()).getCode());
        verify(storage, times(1)).delete(KEY);
    }
    @Test void verifiedObjectAbsenceAllowsIdempotentRecordCleanup() {
        assertEquals(200, service.deletePhotoOrAlbum(request()).getCode());
        verify(storage, never()).delete(anyString()); verify(mapper).deleteById(9L);
    }
}
