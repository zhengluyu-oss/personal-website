package xyz.kuailemao.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.config.OssProperties;
import xyz.kuailemao.storage.FileStorageService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoragePurposeBoundaryTest {
    private FileUploadUtils files;
    private FileStorageService storage;
    private static final String PREFIX = "article/articleCover/";
    @BeforeEach void setUp() {
        files = new FileUploadUtils(); storage = mock(FileStorageService.class);
        OssProperties properties = new OssProperties();
        properties.setDomain("https://media.example.invalid");
        properties.setMediaDomain("https://video.example.invalid");
        properties.setBucketName("synthetic"); properties.setEndpoint("oss-cn-beijing.aliyuncs.com");
        ReflectionTestUtils.setField(files, "ossProperties", properties);
        ReflectionTestUtils.setField(files, "fileStorageService", storage);
    }
    @Test void purposeKeysAcceptOnlyConfiguredSources() {
        for (String origin : List.of("https://media.example.invalid", "https://video.example.invalid",
                "https://synthetic.oss-cn-beijing.aliyuncs.com", "https://media.example.invalid:443")) {
            assertEquals(PREFIX + "synthetic.png", files.requireObjectKey(origin + "/" + PREFIX + "synthetic.png", PREFIX));
        }
    }
    @Test void rawKeysForeignOriginsAndAmbiguousPathsNeverAuthorizeDeletion() {
        for (String url : List.of(PREFIX + "synthetic.png", "https://foreign.invalid/" + PREFIX + "synthetic.png",
                "https://user@media.example.invalid/" + PREFIX + "synthetic.png",
                "http://media.example.invalid/" + PREFIX + "synthetic.png",
                "https://media.example.invalid:444/" + PREFIX + "synthetic.png",
                "https://media.example.invalid/websiteInfo/avatar/synthetic.png",
                "https://media.example.invalid/" + PREFIX + "synthetic.png?x=1",
                "https://media.example.invalid/" + PREFIX + "synthetic.png#fragment",
                "https://media.example.invalid/" + PREFIX + "../avatar.png",
                "https://media.example.invalid/" + PREFIX + "%2e%2e/avatar.png",
                "https://media.example.invalid/" + PREFIX + "%252e%252e/avatar.png",
                "https://media.example.invalid/article%2farticleCover/synthetic.png",
                "https://media.example.invalid/" + PREFIX + "x%5cy.png",
                "https://media.example.invalid/" + PREFIX + "x%00.png",
                "https://media.example.invalid/" + PREFIX + "x%0a.png")) {
            assertThrows(IllegalArgumentException.class, () -> files.deleteTrustedFile(url, PREFIX), url);
        }
        verifyNoInteractions(storage);
    }
    @Test void encodedReferencesAreRecognizedWithoutAllowingMutation() {
        String url = "https://media.example.invalid/article%2farticleCover%2fsynthetic.png?x-oss-process=image/resize,w_100";
        assertEquals(PREFIX + "synthetic.png", files.referenceObjectKey(url, PREFIX));
        assertThrows(IllegalArgumentException.class, () -> files.requireObjectKey(url, PREFIX));
    }
    @Test void nestedPhotoKeySurvivesUnicodeAndAlbumRenames() {
        assertEquals("photoAlbum/父/child/p.png", files.requireObjectKey(
                "https://media.example.invalid/photoAlbum/%E7%88%B6/child/p.png", "photoAlbum/"));
    }
    @Test void storageFailureIsNotDeletionSuccess() {
        when(storage.exists(PREFIX + "synthetic.png")).thenReturn(true);
        doThrow(new IllegalStateException("synthetic storage failure")).when(storage).delete(PREFIX + "synthetic.png");
        assertFalse(files.deleteTrustedFile("https://media.example.invalid/" + PREFIX + "synthetic.png", PREFIX));
    }
    @Test void verifiedAbsenceIsIdempotentAndSuccessfulDeletionIsChecked() {
        String url = "https://media.example.invalid/" + PREFIX + "synthetic.png";
        assertTrue(files.deleteTrustedFile(url, PREFIX));
        verify(storage, never()).delete(anyString());
        when(storage.exists(PREFIX + "synthetic.png")).thenReturn(true, false);
        assertTrue(files.deleteTrustedFile(url, PREFIX));
        verify(storage).delete(PREFIX + "synthetic.png");
    }
}
