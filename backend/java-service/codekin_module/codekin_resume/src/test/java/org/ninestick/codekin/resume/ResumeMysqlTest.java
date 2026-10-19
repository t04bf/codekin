package org.ninestick.codekin.resume;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.ResultHandler;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.ninestick.codekin.dubbo.api.file.IFileApi;
import org.ninestick.codekin.model.common.*;
import org.ninestick.codekin.model.file.dto.ReadyFileDto;
import org.ninestick.codekin.security.*;
import org.ninestick.codekin.resume.document.ResumeDocumentValidator;
import org.ninestick.codekin.resume.domain.dto.ResumeSaveDto;
import org.ninestick.codekin.resume.domain.entity.*;
import org.ninestick.codekin.resume.domain.enums.*;
import org.ninestick.codekin.resume.mapper.*;
import org.ninestick.codekin.resume.service.IResumeService;
import org.ninestick.codekin.resume.service.impl.ResumeServiceImpl;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.node.ObjectNode;

/**
 * 使用真实 MySQL 的独立临时数据库验证 JSON、归属、CAS、幂等重试和事务回滚。
 * 不启动 Nacos/Dubbo，也不连接业务数据库；未显式指定测试 URL 时跳过。
 * @author ninestick
 */
@EnabledIfEnvironmentVariable(named = "CODEKIN_RESUME_TEST_JDBC_URL", matches = ".+codekin_resume_test_.+")
class ResumeMysqlTest {
    private static AnnotationConfigApplicationContext context;
    private IResumeService service;
    private JdbcTemplate jdbc;
    private IFileApi files;
    private static final String TEMPLATE = "910000000000000001";

    /** 测试专用 Spring/MyBatis 配置，事务代理与实际 Mapper 保持生产语义。 @author ninestick */
    @Configuration @EnableTransactionManagement
    @MapperScan("org.ninestick.codekin.resume.mapper")
    @Import({ResumeServiceImpl.class, ResumeDocumentValidator.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("CODEKIN_RESUME_TEST_JDBC_URL"),
                    System.getenv("CODEKIN_RESUME_TEST_DB_USER"), System.getenv("CODEKIN_RESUME_TEST_DB_PASSWORD"));
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source, QueryCounter counter) throws Exception {
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source); factory.setConfiguration(configuration); factory.setPlugins(counter);
            return factory.getObject();
        }
        @Bean QueryCounter queryCounter() { return new QueryCounter(); }
        @Bean LocalValidatorFactoryBean requestValidator() { return new LocalValidatorFactoryBean(); }
    }

    /** 固定记录实际查询次数，防止列表转换引入逐条查询。 @author ninestick */
    @Intercepts(@Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    static class QueryCounter implements Interceptor {
        final AtomicInteger count = new AtomicInteger();
        @Override public Object intercept(Invocation invocation) throws Throwable {
            count.incrementAndGet(); return invocation.proceed();
        }
    }

    @BeforeAll static void start() { context = new AnnotationConfigApplicationContext(Config.class); }
    @AfterAll static void stop() { if (context != null) context.close(); }
    @BeforeEach void setup() {
        service = context.getBean(IResumeService.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.update("DELETE FROM user_resume_revision"); jdbc.update("DELETE FROM user_resume");
        jdbc.update("UPDATE resume_template SET status = 'PUBLISHED'");
        files = mock(IFileApi.class);
        when(files.getResumePhotoUrl(anyString(), anyLong())).thenReturn(RpcResult.success(
                org.ninestick.codekin.model.file.dto.SignedFileUrlDto.builder().url("https://storage.example/photo")
                        .expiresAt(java.time.Instant.now().plusSeconds(7200)).build()));
        // 设置代理内部目标的 RPC 引用，业务流程和 SQL 均保持实际实现。
        var target = org.springframework.test.util.AopTestUtils.getTargetObject(service);
        ReflectionTestUtils.setField(target, "fileApi", files);
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("mysql-test").build());
    }
    @AfterEach void clear() { UserContextHolder.clear(); }
    private ResumeSaveDto request(String mutation, String version) throws Exception {
        var request = ResumeDocumentValidatorTest.request("example");
        request.setMutationId(mutation); request.setExpectedVersion(version);
        return request;
    }

    @Test void jsonObjectRoundTripAndStringIdsMatchHttpContract() throws Exception {
        var templates = service.getTemplates();
        assertEquals(9, templates.size());
        for (var template : templates) {
            var request = ResumeSaveDto.builder().title(template.getName()).templateId(template.getId())
                    .schemaVersion(template.getSchemaVersion()).content(template.getDefaultContent())
                    .layout(template.getDefaultLayout()).mutationId("template-validation").build();
            assertDoesNotThrow(() -> context.getBean(ResumeDocumentValidator.class).validateAndHash(request));
        }
        var created = service.createResume(request("create", null));
        assertEquals("1", created.getVersion()); assertEquals("classic", created.getTemplateKey());
        assertEquals(1, created.getTemplateVersion());
        assertEquals(1, created.getSchemaVersion());
        assertEquals(9, jdbc.queryForObject("SELECT COUNT(*) FROM resume_template", Integer.class));
        var detail = service.getResume(Long.valueOf(created.getId()));
        assertEquals(created.getContent(), detail.getContent()); assertEquals(created.getLayout(), detail.getLayout());
        assertEquals("OBJECT", jdbc.queryForObject("SELECT JSON_TYPE(content) FROM user_resume WHERE id = ?", String.class, created.getId()));
        assertEquals("OBJECT", jdbc.queryForObject("SELECT JSON_TYPE(layout) FROM user_resume WHERE id = ?", String.class, created.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_resume_revision", Integer.class));
        assertEquals(created.getCreatedAt(), detail.getCreatedAt());
    }
    @Test void foreignAndDeletedResumesAreInvisibleAndCannotBeUpdated() throws Exception {
        var created = service.createResume(request("create", null));
        Long id = Long.valueOf(created.getId());
        UserContextHolder.set(AuthenticatedUser.builder().userId(43L).sessionId("other").build());
        assertEquals(ResumeCode.NOT_FOUND, assertThrows(BusinessException.class, () -> service.getResume(id)).getRecode());
        var update = request("other-user", "1");
        assertEquals(ResumeCode.NOT_FOUND, assertThrows(BusinessException.class, () -> service.saveResume(id, update)).getRecode());
        assertTrue(service.getResumes(1, 20).getItems().isEmpty());
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("owner").build());
        jdbc.update("UPDATE user_resume SET deleted_at = ? WHERE id = ?", LocalDateTime.now(), id);
        assertEquals(ResumeCode.NOT_FOUND, assertThrows(BusinessException.class, () -> service.getResume(id)).getRecode());
        assertTrue(service.getResumes(1, 20).getItems().isEmpty());
    }
    @Test void updateRetryAndConflictDoNotWriteExtraVersions() throws Exception {
        long id = Long.parseLong(service.createResume(request("create", null)).getId());
        var update = request("update", "1"); update.setCheckpoint(true);
        assertEquals("2", service.saveResume(id, update).getVersion());
        assertEquals("2", service.saveResume(id, update).getVersion());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_resume_revision", Integer.class));
        update.setTitle("不同载荷");
        assertEquals(ResumeCode.MUTATION_REUSED, assertThrows(BusinessException.class, () -> service.saveResume(id, update)).getRecode());
        var stale = request("stale", "1");
        assertEquals(ResumeCode.VERSION_CONFLICT, assertThrows(BusinessException.class, () -> service.saveResume(id, stale)).getRecode());
        var auto = request("auto", "2");
        assertEquals("3", service.saveResume(id, auto).getVersion());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_resume_revision", Integer.class));
    }
    @Test void revisionFailureRollsBackCurrentDocument() throws Exception {
        long id = Long.parseLong(service.createResume(request("create", null)).getId());
        var original = context.getBean(UserResumeMapper.class).selectById(id);
        context.getBean(UserResumeRevisionMapper.class).insert(UserResumeRevisionEntity.builder().id(999L).resumeId(id)
                .resumeVersion(2L).title(original.getTitle()).templateId(original.getTemplateId()).schemaVersion(1)
                .content(original.getContent()).layout(original.getLayout()).reason("MANUAL").createdAt(LocalDateTime.now()).build());
        var update = request("rollback", "1"); update.setCheckpoint(true); update.setTitle("应该回滚");
        assertThrows(DataIntegrityViolationException.class, () -> service.saveResume(id, update));
        var actual = service.getResume(id);
        assertEquals("1", actual.getVersion()); assertEquals(original.getTitle(), actual.getTitle());
        assertEquals("create", context.getBean(UserResumeMapper.class).selectById(id).getLastMutationId());
    }
    @Test void onlyPublishedCompatibleTemplatesCanBeCreatedAndRetiredCanBeRetained() throws Exception {
        jdbc.update("UPDATE resume_template SET schema_version = 99 WHERE id = ?", TEMPLATE);
        var incompatible = request("incompatible", null);
        assertEquals(ResumeCode.TEMPLATE_UNAVAILABLE, assertThrows(BusinessException.class, () -> service.createResume(incompatible)).getRecode());
        jdbc.update("UPDATE resume_template SET schema_version = 1 WHERE id = ?", TEMPLATE);
        long id = Long.parseLong(service.createResume(request("create", null)).getId());
        jdbc.update("UPDATE resume_template SET status = 'RETIRED' WHERE id = ?", TEMPLATE);
        var create = request("create-retired", null);
        assertEquals(ResumeCode.TEMPLATE_UNAVAILABLE, assertThrows(BusinessException.class, () -> service.createResume(create)).getRecode());
        assertEquals("2", service.saveResume(id, request("retain", "1")).getVersion());
        assertEquals(8, service.getTemplates().size());
    }
    @Test void readyPhotoMustBelongToUserAndHaveResumePurpose() throws Exception {
        var create = request("photo", null);
        ((ObjectNode)create.getContent().get("basic")).put("photo", "").put("photoFileId", "99");
        when(files.getReadyFile("99", 42L, "RESUME_PHOTO")).thenReturn(RpcResult.success(ReadyFileDto.builder()
                .fileId("99").ownerUserId(43L).typeCode("RESUME_PHOTO").build()));
        assertEquals(ResumeCode.PHOTO_NOT_READY, assertThrows(BusinessException.class, () -> service.createResume(create)).getRecode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_resume", Integer.class));
        when(files.getReadyFile("99", 42L, "RESUME_PHOTO")).thenReturn(RpcResult.success(ReadyFileDto.builder()
                .fileId("99").ownerUserId(42L).typeCode("RESUME_PHOTO").build()));
        assertEquals("99", service.createResume(create).getContent().path("basic").path("photoFileId").asString());
    }
    @Test void deleteIsOwnerScopedVersionedAndRepeatable() throws Exception {
        long id = Long.parseLong(service.createResume(request("create", null)).getId());
        var stale = org.ninestick.codekin.resume.domain.dto.ResumeDeleteDto.builder().expectedVersion("2").build();
        assertEquals(ResumeCode.VERSION_CONFLICT, assertThrows(BusinessException.class, () -> service.deleteResume(id, stale)).getRecode());
        var correct = org.ninestick.codekin.resume.domain.dto.ResumeDeleteDto.builder().expectedVersion("1").build();
        UserContextHolder.set(AuthenticatedUser.builder().userId(43L).sessionId("other").build());
        assertEquals(ResumeCode.NOT_FOUND, assertThrows(BusinessException.class, () -> service.deleteResume(id, correct)).getRecode());
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("owner").build());
        service.deleteResume(id, correct); service.deleteResume(id, correct);
        assertTrue(service.getResumes(1, 20).getItems().isEmpty());
        assertEquals(2L, context.getBean(UserResumeMapper.class).selectById(id).getVersion());
        assertEquals(ResumeCode.NOT_FOUND, assertThrows(BusinessException.class, () -> service.getResume(id)).getRecode());
    }
    @Test void detailSignsPhotoWithoutPersistingUrl() throws Exception {
        var create = request("photo-create", null);
        ((ObjectNode)create.getContent().get("basic")).put("photo", "").put("photoFileId", "99");
        when(files.getReadyFile("99", 42L, "RESUME_PHOTO")).thenReturn(RpcResult.success(ReadyFileDto.builder()
                .fileId("99").ownerUserId(42L).typeCode("RESUME_PHOTO").build()));
        long id = Long.parseLong(service.createResume(create).getId());
        var detail = service.getResume(id);
        assertEquals("https://storage.example/photo", detail.getPhotoUrl());
        assertEquals("", detail.getContent().path("basic").path("photo").asString());
        verify(files).getResumePhotoUrl("99", 42L);
        assertFalse(context.getBean(UserResumeMapper.class).selectById(id).getContent().contains("storage.example"));
    }
    @Test void paginationAndCatalogBatchHandleMultipleResumes() throws Exception {
        when(files.getReadyFile("99", 42L, "RESUME_PHOTO")).thenReturn(RpcResult.success(ReadyFileDto.builder()
                .fileId("99").ownerUserId(42L).typeCode("RESUME_PHOTO").build()));
        var photo = org.ninestick.codekin.model.file.dto.SignedFileUrlDto.builder()
                .url("https://storage.example/photo").expiresAt(java.time.Instant.now().plusSeconds(7200)).build();
        when(files.getResumePhotoUrls(List.of("99"), 42L)).thenReturn(RpcResult.success(Map.of("99", photo)));
        var document = request("create-0", null);
        ((ObjectNode)document.getContent().get("basic")).put("photo", "").put("photoFileId", "99");
        for (int i = 0; i < 4; i++) {
            document.setMutationId("create-" + i);
            service.createResume(document);
        }
        // 外部账号记录不能进入列表或照片批次。
        UserContextHolder.set(AuthenticatedUser.builder().userId(43L).sessionId("foreign").build());
        service.createResume(request("foreign", null));
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("mysql-test").build());
        clearInvocations(files);
        context.getBean(QueryCounter.class).count.set(0);
        var page = service.getResumes(1, 3);
        // 列表与模板各查询一次，记录数量增加也不能引入逐条查询。
        assertEquals(2, context.getBean(QueryCounter.class).count.get());
        assertEquals(3, page.getItems().size()); assertTrue(page.isHasMore());
        for (var item : page.getItems()) {
            assertEquals(document.getContent(), item.getContent());
            assertEquals(document.getLayout(), item.getLayout());
            assertEquals(photo.getUrl(), item.getPhotoUrl());
            assertEquals(photo.getExpiresAt(), item.getPhotoUrlExpiresAt());
        }
        verify(files, times(1)).getResumePhotoUrls(List.of("99"), 42L);
        verifyNoMoreInteractions(files);
        var last = service.getResumes(2, 3);
        assertEquals(1, last.getItems().size()); assertFalse(last.isHasMore());
        assertEquals("classic", last.getItems().getFirst().getTemplateKey());
        assertThrows(BusinessException.class, () -> service.getResumes(1, 1000));
    }

    @Test void photoBatchFailureCannotBecomeAnApparentlyEmptyPreview() throws Exception {
        var request = request("photo", null);
        ((ObjectNode)request.getContent().get("basic")).put("photo", "").put("photoFileId", "99");
        when(files.getReadyFile("99", 42L, "RESUME_PHOTO")).thenReturn(RpcResult.success(ReadyFileDto.builder()
                .fileId("99").ownerUserId(42L).typeCode("RESUME_PHOTO").build()));
        service.createResume(request);
        when(files.getResumePhotoUrls(anyList(), eq(42L))).thenReturn(RpcResult.success(Map.of()));
        assertEquals(ResumeCode.FILE_SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> service.getResumes(1, 20)).getRecode());
        when(files.getResumePhotoUrls(anyList(), eq(42L))).thenReturn(RpcResult.failure(CommonCode.INTERNAL_ERROR, "unavailable"));
        assertEquals(ResumeCode.FILE_SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> service.getResumes(1, 20)).getRecode());
    }

    @Test void emptyListSkipsTemplateAndPhotoLookups() {
        context.getBean(QueryCounter.class).count.set(0);
        var page = service.getResumes(1, 20);
        assertTrue(page.getItems().isEmpty());
        assertFalse(page.isHasMore());
        assertEquals(1, context.getBean(QueryCounter.class).count.get());
        verifyNoInteractions(files);
    }
    @Test void concurrentDifferentMutationsHaveOnlyOneWinner() throws Exception { concurrent(false); }
    @Test void concurrentIdenticalRetryReturnsSameSavedVersion() throws Exception { concurrent(true); }
    private void concurrent(boolean identical) throws Exception {
        long id = Long.parseLong(service.createResume(request("create", null)).getId());
        var barrier = new CyclicBarrier(2);
        when(files.getReadyFile("99", 42L, "RESUME_PHOTO")).thenAnswer(call -> {
            barrier.await(10, TimeUnit.SECONDS);
            return RpcResult.success(ReadyFileDto.builder().fileId("99").ownerUserId(42L).typeCode("RESUME_PHOTO").build());
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                var update = request(identical ? "same" : "different-" + i, "1"); update.setCheckpoint(true);
                ((ObjectNode)update.getContent().get("basic")).put("photo", "").put("photoFileId", "99");
                futures.add(executor.submit(() -> {
                    UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("concurrent").build());
                    try { return service.saveResume(id, update).getVersion(); }
                    catch (BusinessException exception) { return exception.getRecode().getCode(); }
                    finally { UserContextHolder.clear(); }
                }));
            }
            var results = new ArrayList<String>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            assertEquals(identical ? 2 : 1, Collections.frequency(results, "2"));
            if (!identical) assertEquals(1, Collections.frequency(results, ResumeCode.VERSION_CONFLICT.getCode()));
        }
        assertEquals("2", service.getResume(id).getVersion());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_resume_revision", Integer.class));
    }
}
