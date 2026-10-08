package xyz.kuailemao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.dto.EmailChangeStartDTO;
import xyz.kuailemao.domain.dto.UpdateEmailDTO;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.mapper.UserMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "BLOG_SECURITY_QA_REDIS", matches = "true")
class EmailChangeServiceIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private EmailChangeService service;
    private AccountAuthenticationVersion versions;
    private UserMapper users;
    private User user;
    private LoginUser login;
    private PasswordEncoder passwords;
    private RabbitTemplate mail;
    private final Map<String,String> codes = new HashMap<>();
    private final List<String> keys = new ArrayList<>();

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", 16379); config.setDatabase(15);
        factory = new LettuceConnectionFactory(config); factory.afterPropertiesSet(); redis = new StringRedisTemplate(factory);
        users = mock(UserMapper.class); passwords = mock(PasswordEncoder.class); mail = mock(RabbitTemplate.class);
        versions = new AccountAuthenticationVersion(); ReflectionTestUtils.setField(versions,"stringRedisTemplate",redis);
        service = new EmailChangeService();
        ReflectionTestUtils.setField(service,"userMapper",users); ReflectionTestUtils.setField(service,"stringRedisTemplate",redis);
        ReflectionTestUtils.setField(service,"authenticationVersion",versions); ReflectionTestUtils.setField(service,"passwordEncoder",passwords);
        EmailDeliveryService delivery = new EmailDeliveryService();
        ReflectionTestUtils.setField(delivery,"stringRedisTemplate",redis);
        ReflectionTestUtils.setField(delivery,"rabbitTemplate",mail);
        ReflectionTestUtils.setField(delivery,"exchange","qa");
        ReflectionTestUtils.setField(delivery,"routingKey","qa");
        ReflectionTestUtils.setField(service,"emailDeliveryService",delivery);
        ReflectionTestUtils.setField(service,"rabbitTemplate",mail); ReflectionTestUtils.setField(service,"exchange","qa"); ReflectionTestUtils.setField(service,"routingKey","qa");
        user = new User().setId(ThreadLocalRandom.current().nextLong(1_000_000_000L,Long.MAX_VALUE)).setRegisterType(0)
                .setEmail("old@example.invalid").setPassword("hash").setIsDeleted(0).setIsDisable(0);
        when(users.selectById(user.getId())).thenReturn(user);
        when(users.update(isNull(),any())).thenReturn(1);
        when(passwords.matches("correct","hash")).thenReturn(true);
        keys.add("auth:account-version:v1:"+user.getId()); keys.add("auth:email-change:v1:cooldown:"+user.getId());
        login = new LoginUser(user).setAuthenticationVersion(versions.forCompletedLogin(user.getId(),null)).setAuthorities(List.of());
        authenticate(false);
        doAnswer(invocation -> { Map<String,String> event=invocation.getArgument(2); codes.put(event.get("email"),event.get("code")); return null; })
                .when(mail).convertAndSend(eq("qa"),eq("qa"),any(Object.class), any(MessagePostProcessor.class));
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        try { redis.delete(keys); } finally { factory.destroy(); }
    }
    private void authenticate(boolean admin) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null,
                admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN")) : List.of()));
    }
    private EmailChangeService.Challenge start() {
        EmailChangeStartDTO dto=new EmailChangeStartDTO(); dto.setEmail("new@example.invalid"); dto.setPassword("correct");
        var challenge=service.start(dto); keys.add(key(challenge)); return challenge;
    }
    private String key(EmailChangeService.Challenge c) { return "auth:email-change:v1:"+OauthBrowserBinding.digest(c.challengeId()); }
    private UpdateEmailDTO completeDto(EmailChangeService.Challenge c) {
        UpdateEmailDTO dto=new UpdateEmailDTO(); dto.setChallengeId(c.challengeId()); dto.setEmail("new@example.invalid");
        dto.setCode(codes.get("new@example.invalid")); dto.setOldCode(codes.get("old@example.invalid")); return dto;
    }

    @Test void passwordAccountRequiresPasswordAndLegacyNewMailboxCodeCannotBypass() {
        EmailChangeStartDTO start=new EmailChangeStartDTO(); start.setEmail("new@example.invalid"); start.setPassword("wrong");
        assertThrows(BadCredentialsException.class,()->service.start(start)); verifyNoInteractions(mail);
        UpdateEmailDTO legacy=new UpdateEmailDTO(); legacy.setEmail("new@example.invalid"); legacy.setPassword("anything"); legacy.setCode("123456");
        assertThrows(BadCredentialsException.class,()->service.complete(legacy)); verify(users,never()).update(isNull(),any());
    }
    @Test void passwordOwnerCompletesOnceAndRevokesOldEpoch() {
        var c=start(); assertFalse(c.oldEmailRequired()); var dto=completeDto(c);
        assertEquals(200,service.complete(dto).getCode()); assertNotEquals(login.getAuthenticationVersion(),versions.current(user.getId()));
        assertThrows(BadCredentialsException.class,()->service.complete(dto)); verify(users,times(1)).update(isNull(),any());
    }
    @Test void administratorRequiresOldMailboxCode() {
        authenticate(true); var c=start(); assertTrue(c.oldEmailRequired()); assertEquals(2,codes.size());
        var dto=completeDto(c); dto.setOldCode("wrong"); assertThrows(BadCredentialsException.class,()->service.complete(dto));
        dto.setOldCode(codes.get("old@example.invalid")); assertEquals(200,service.complete(dto).getCode());
    }
    @Test void thirdPartyRequiresExactProviderIdentityAndBothMailboxes() {
        user.setRegisterType(1); var c=start(); var dto=completeDto(c);
        assertThrows(BadCredentialsException.class,()->service.complete(dto));
        assertThrows(BadCredentialsException.class,()->service.verifyProvider(c.challengeId(),2,user.getId()));
        assertThrows(BadCredentialsException.class,()->service.verifyProvider(c.challengeId(),1,user.getId()+1));
        service.verifyProvider(c.challengeId(),1,user.getId());
        dto.setOldCode(null); assertThrows(BadCredentialsException.class,()->service.complete(dto));
        dto.setOldCode(codes.get("old@example.invalid")); assertEquals(200,service.complete(dto).getCode());
    }
    @Test void firstThirdPartyBindingStillRequiresProviderButNotOldMailbox() {
        user.setRegisterType(2).setEmail(null); var c=start(); assertFalse(c.oldEmailRequired());
        service.verifyProvider(c.challengeId(),2,user.getId()); assertEquals(200,service.complete(completeDto(c)).getCode());
    }
    @Test void fiveFailuresConsumeProofAndExpiryIsBounded() {
        var c=start(); assertTrue(redis.getExpire(key(c)) <= 300); var dto=completeDto(c); dto.setCode("wrong");
        for(int i=0;i<5;i++) assertThrows(BadCredentialsException.class,()->service.complete(dto));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(key(c))));
        assertThrows(BadCredentialsException.class,()->service.complete(completeDto(c))); verify(users,never()).update(isNull(),any());
    }
    @Test void differentPurposeTargetOrExpiredProofCannotWrite() {
        var c=start(); var dto=completeDto(c); dto.setEmail("other@example.invalid");
        assertThrows(BadCredentialsException.class,()->service.complete(dto)); assertTrue(Boolean.TRUE.equals(redis.hasKey(key(c))));
        dto.setEmail("new@example.invalid"); redis.opsForHash().put(key(c),"purpose","login");
        assertThrows(BadCredentialsException.class,()->service.complete(dto));
        redis.expire(key(c),Duration.ZERO); assertThrows(BadCredentialsException.class,()->service.complete(dto));
        verify(users,never()).update(isNull(),any());
    }
    @Test void concurrentProofUseAuthorizesOnlyOneDatabaseAttempt() throws Exception {
        var c=start(); var dto=completeDto(c); ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> complete=()->{ authenticate(false); try{return service.complete(dto).getCode()==200;}catch(BadCredentialsException expected){return false;}finally{SecurityContextHolder.clearContext();} };
            var results=executor.invokeAll(List.of(complete,complete));
            assertEquals(1,(results.get(0).get()?1:0)+(results.get(1).get()?1:0)); verify(users,times(1)).update(isNull(),any());
        } finally {executor.shutdownNow();}
    }
    @Test void databaseConflictStillRevokesSessionsAndDoesNotLeakSql() {
        var c=start(); when(users.update(isNull(),any())).thenThrow(new DuplicateKeyException("private-sql-secret"));
        var result=service.complete(completeDto(c)); assertNotEquals(200,result.getCode()); assertFalse(result.asJsonString().contains("private-sql-secret"));
        assertNotEquals(login.getAuthenticationVersion(),versions.current(user.getId())); assertFalse(Boolean.TRUE.equals(redis.hasKey(key(c))));
    }
    @Test void changedCredentialsAndMissingEpochRejectPendingProofs() {
        var c=start(); user.setPassword("replacement"); assertThrows(BadCredentialsException.class,()->service.complete(completeDto(c)));
        user.setPassword("hash"); redis.delete("auth:account-version:v1:"+user.getId());
        assertThrows(BadCredentialsException.class,()->service.complete(completeDto(c))); verify(users,never()).update(isNull(),any());
    }
}
