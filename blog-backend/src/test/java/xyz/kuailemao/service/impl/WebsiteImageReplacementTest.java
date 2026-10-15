package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.kuailemao.config.OssProperties;
import xyz.kuailemao.constants.WebsiteInfoConst;
import xyz.kuailemao.domain.entity.WebsiteInfo;
import xyz.kuailemao.enums.UploadEnum;
import xyz.kuailemao.mapper.WebsiteInfoMapper;
import xyz.kuailemao.storage.FileStorageService;
import xyz.kuailemao.utils.FileUploadUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebsiteImageReplacementTest {
    private WebsiteInfoServiceImpl service;
    private WebsiteInfoMapper mapper;
    private FileStorageService storage;
    private final Set<String> objects = ConcurrentHashMap.newKeySet();
    private final AtomicReference<WebsiteInfo> current = new AtomicReference<>();
    private static final String ORIGIN = "https://media.example.invalid/";
    private static final String OLD = "websiteInfo/avatar/old.png";
    private static final String SIBLING = "websiteInfo/avatar/unrelated.png";
    private String newKey;

    @BeforeEach void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WebsiteInfo.class);
        service = new WebsiteInfoServiceImpl(); mapper = mock(WebsiteInfoMapper.class); storage = mock(FileStorageService.class);
        FileUploadUtils files = new FileUploadUtils(); OssProperties properties = new OssProperties(); properties.setDomain(ORIGIN);
        ReflectionTestUtils.setField(files, "ossProperties", properties); ReflectionTestUtils.setField(files, "fileStorageService", storage);
        ReflectionTestUtils.setField(service, "fileUploadUtils", files); ReflectionTestUtils.setField(service, "baseMapper", mapper);
        current.set(WebsiteInfo.builder().id(WebsiteInfoConst.WEBSITE_INFO_ID).webmasterAvatar(ORIGIN + OLD).build());
        objects.add(OLD); objects.add(SIBLING);
        when(mapper.selectById(WebsiteInfoConst.WEBSITE_INFO_ID)).thenAnswer(call -> current.get());
        when(storage.exists(anyString())).thenAnswer(call -> objects.contains(call.getArgument(0)));
        doAnswer(call -> { newKey = call.getArgument(0); objects.add(newKey); return null; })
                .when(storage).put(anyString(), any(), anyLong(), any());
        doAnswer(call -> { objects.remove(call.getArgument(0)); return null; }).when(storage).delete(anyString());
        when(mapper.update(isNull(), any())).thenAnswer(call -> {
            LambdaUpdateWrapper<WebsiteInfo> update = call.getArgument(1);
            update.getSqlSet(); update.getSqlSegment();
            assertTrue(update.getSqlSegment().contains("webmaster_avatar"));
            assertTrue(update.getParamNameValuePairs().containsValue(ORIGIN + OLD));
            String next = update.getParamNameValuePairs().values().stream().filter(value -> value instanceof String
                    && value.toString().startsWith(ORIGIN) && !value.equals(ORIGIN + OLD)).map(Object::toString).findFirst().orElseThrow();
            synchronized (current) {
                if (!Objects.equals(current.get().getWebmasterAvatar(), ORIGIN + OLD)) return 0;
                assertTrue(objects.contains(OLD), "old object must survive until the winning reference update");
                current.set(WebsiteInfo.builder().id(WebsiteInfoConst.WEBSITE_INFO_ID).webmasterAvatar(next).build()); return 1;
            }
        });
    }
    private MockMultipartFile file() { return new MockMultipartFile("avatar", "synthetic.png", "image/png", new byte[]{1, 2, 3}); }
    private void assertOriginalUsable() {
        assertEquals(ORIGIN + OLD, current.get().getWebmasterAvatar()); assertTrue(objects.contains(OLD)); assertTrue(objects.contains(SIBLING));
    }
    @Test void invalidUploadsNeverDeleteTheOldImage() {
        for (MockMultipartFile invalid : List.of(new MockMultipartFile("avatar", "bad.png", "image/png", new byte[0]),
                new MockMultipartFile("avatar", "bad.exe", "application/octet-stream", new byte[]{1}),
                new MockMultipartFile("avatar", "large.png", "image/png", new byte[400_000]))) {
            assertNotEquals(200, service.uploadImageInsertOrUpdate(UploadEnum.WEBSITE_INFO_AVATAR, invalid, 0).getCode());
            assertOriginalUsable();
        }
        verify(storage, never()).delete(anyString()); verify(storage, never()).put(anyString(), any(), anyLong(), any());
        verify(mapper, never()).update(isNull(), any());
    }
    @Test void uploadFailureKeepsTheOriginalReference() {
        doThrow(new IllegalStateException("synthetic put failure")).when(storage).put(anyString(), any(), anyLong(), any());
        assertNotEquals(200, service.uploadImageInsertOrUpdate(UploadEnum.WEBSITE_INFO_AVATAR, file(), 0).getCode());
        assertOriginalUsable(); verify(storage, never()).delete(anyString());
    }
    @Test void databaseFailureCleansOnlyTheUnreferencedNewImage() {
        doThrow(new IllegalStateException("synthetic database failure")).when(mapper).update(isNull(), any());
        assertThrows(IllegalStateException.class, () -> service.uploadImageInsertOrUpdate(UploadEnum.WEBSITE_INFO_AVATAR, file(), 0));
        assertOriginalUsable(); assertFalse(objects.contains(newKey)); verify(storage, never()).delete(OLD);
    }
    @Test void successfulReplacementPreservesUnrelatedObjects() {
        assertEquals(200, service.uploadImageInsertOrUpdate(UploadEnum.WEBSITE_INFO_AVATAR, file(), 0).getCode());
        assertEquals(ORIGIN + newKey, current.get().getWebmasterAvatar()); assertTrue(objects.contains(newKey));
        assertFalse(objects.contains(OLD)); assertTrue(objects.contains(SIBLING)); verify(storage, never()).list(anyString());
    }
    @Test void cleanupFailureDoesNotLieAboutTheCommittedReference() {
        doThrow(new IllegalStateException("synthetic delete failure")).when(storage).delete(OLD);
        assertEquals(200, service.uploadImageInsertOrUpdate(UploadEnum.WEBSITE_INFO_AVATAR, file(), 0).getCode());
        assertEquals(ORIGIN + newKey, current.get().getWebmasterAvatar()); assertTrue(objects.contains(OLD)); assertTrue(objects.contains(newKey));
    }
    @Test void transactionRollbackRetainsTheOldImageAndCommitCleansAfterCompletion() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals(200, service.uploadImageInsertOrUpdate(UploadEnum.WEBSITE_INFO_AVATAR, file(), 0).getCode());
            assertTrue(objects.contains(OLD));
            current.set(WebsiteInfo.builder().id(WebsiteInfoConst.WEBSITE_INFO_ID).webmasterAvatar(ORIGIN + OLD).build());
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertOriginalUsable(); assertFalse(objects.contains(newKey));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals(200, service.uploadImageInsertOrUpdate(UploadEnum.WEBSITE_INFO_AVATAR, file(), 0).getCode());
            assertTrue(objects.contains(OLD));
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            assertFalse(objects.contains(OLD)); assertTrue(objects.contains(newKey));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void concurrentReplacementsCannotOverwriteTheWinnerOrDeleteItsImage() throws Exception {
        CyclicBarrier bothUploaded = new CyclicBarrier(2);
        doAnswer(call -> { objects.add(call.getArgument(0)); bothUploaded.await(10, TimeUnit.SECONDS); return null; })
                .when(storage).put(anyString(), any(), anyLong(), any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> service.uploadImageInsertOrUpdate(UploadEnum.WEBSITE_INFO_AVATAR, file(), 0).getCode());
            var b = executor.submit(() -> service.uploadImageInsertOrUpdate(UploadEnum.WEBSITE_INFO_AVATAR, file(), 0).getCode());
            List<Integer> codes = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(1, codes.stream().filter(code -> code == 200).count());
            assertEquals(Set.of(SIBLING, current.get().getWebmasterAvatar().substring(ORIGIN.length())), objects);
        } finally { executor.shutdownNow(); }
    }
}
