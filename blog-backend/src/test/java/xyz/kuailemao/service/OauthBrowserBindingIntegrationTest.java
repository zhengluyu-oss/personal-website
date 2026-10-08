package xyz.kuailemao.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Fixed loopback QA Redis; provider HTTP and real accounts are never used. */
@EnabledIfEnvironmentVariable(named = "BLOG_SECURITY_QA_REDIS", matches = "true")
class OauthBrowserBindingIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private OauthBrowserBinding service;
    private final List<String> keys = new ArrayList<>();
    private record Attempt(String state, Cookie cookie, MockHttpServletRequest request) {}
    @BeforeEach void setup() {
        var config = new RedisStandaloneConfiguration("127.0.0.1",16379); config.setDatabase(15);
        factory=new LettuceConnectionFactory(config); factory.afterPropertiesSet(); redis=new StringRedisTemplate(factory);
        service=new OauthBrowserBinding(); ReflectionTestUtils.setField(service,"stringRedisTemplate",redis);
    }
    @AfterEach void cleanup() { try { if(!keys.isEmpty()) redis.delete(keys); } finally {factory.destroy();} }
    private Attempt begin(int provider, String challenge) {
        var request=new MockHttpServletRequest(); request.setServerName("example.invalid"); var response=new MockHttpServletResponse();
        String callback="https://example.invalid/api/oauth/"+(provider==1?"gitee":"github")+"/callback";
        String state=challenge==null ? service.begin(request,response,provider,callback)
                :service.beginReauthentication(request,response,provider,callback,challenge);
        keys.add(OauthBrowserBinding.STATE_PREFIX+state);
        String header=response.getHeader("Set-Cookie"); assertNotNull(header);
        for(String required: List.of("HttpOnly","Secure","SameSite=Lax","Path=/api/oauth/","Max-Age=600")) assertTrue(header.contains(required));
        assertFalse(header.contains("Domain=")); assertEquals("no-store",response.getHeader("Cache-Control"));
        String[] pair=header.split(";",2)[0].split("=",2); Cookie cookie=new Cookie(pair[0],pair[1]); request.setCookies(cookie);
        return new Attempt(state,cookie,request);
    }
    private String issue(Attempt a,int provider) {
        service.validateCallback(a.request(),a.state(),provider);
        assertNull(service.reauthenticationChallenge(a.request(),provider));
        String code=service.issue(a.request(),provider,42);
        keys.add(OauthBrowserBinding.EXCHANGE_PREFIX+OauthBrowserBinding.digest(code)); return code;
    }
    @Test void attackerLinkWrongProviderAndReplayDoNotConsumeLegitimateAttempt() {
        for(int provider:List.of(1,2)) {
            var a=begin(provider,null); var wrong=new MockHttpServletRequest(); wrong.setCookies(new Cookie(a.cookie().getName(),"f".repeat(64)));
            assertThrows(BadCredentialsException.class,()->service.validateCallback(wrong,a.state(),provider));
            assertThrows(BadCredentialsException.class,()->service.validateCallback(a.request(),a.state(),3-provider));
            String code=issue(a,provider);
            assertThrows(BadCredentialsException.class,()->service.exchange(wrong,code));
            assertEquals(provider+":42",service.exchange(a.request(),code));
            assertThrows(BadCredentialsException.class,()->service.exchange(a.request(),code));
        }
    }
    @Test void reauthenticationCannotIssueLoginExchangeAndPreservesServerSelectedPurpose() {
        var a=begin(2,"a".repeat(64)); service.validateCallback(a.request(),a.state(),2);
        assertThrows(BadCredentialsException.class,()->service.issue(a.request(),2,42));
        assertEquals("a".repeat(64),service.reauthenticationChallenge(a.request(),2));
        assertThrows(BadCredentialsException.class,()->service.reauthenticationChallenge(a.request(),2));
        assertThrows(BadCredentialsException.class,()->service.issue(a.request(),2,42));
    }
    @Test void realRedisConcurrentExchangeHasOneWinnerAndOtherTabIsIndependent() throws Exception {
        var a=begin(1,null); var b=begin(2,null); String code=issue(a,1), other=issue(b,2);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> exchange=()->{try {service.exchange(a.request(),code);return true;}catch(BadCredentialsException expected){return false;}};
            var results=executor.invokeAll(List.of(exchange,exchange));
            assertEquals(1,(results.get(0).get()?1:0)+(results.get(1).get()?1:0));
            assertEquals("2:42",service.exchange(b.request(),other));
        } finally {executor.shutdownNow();}
    }
    @Test void bothStateAndExchangeExpireAndCookiesLimitParallelAttempts() {
        var a=begin(1,null); assertTrue(redis.getExpire(OauthBrowserBinding.STATE_PREFIX+a.state())<=600);
        redis.expire(OauthBrowserBinding.STATE_PREFIX+a.state(),Duration.ZERO);
        assertThrows(BadCredentialsException.class,()->service.validateCallback(a.request(),a.state(),1));
        var b=begin(2,null); String code=issue(b,2); String key=OauthBrowserBinding.EXCHANGE_PREFIX+OauthBrowserBinding.digest(code);
        assertTrue(redis.getExpire(key)<=120); redis.expire(key,Duration.ZERO);
        assertThrows(BadCredentialsException.class,()->service.exchange(b.request(),code));
        Cookie[] cookies=new Cookie[5]; for(int i=0;i<5;i++) cookies[i]=new Cookie("blog_oauth_"+i,"x");
        b.request().setCookies(cookies);
        assertThrows(BadCredentialsException.class,()->service.begin(b.request(),new MockHttpServletResponse(),2,"https://example.invalid/api/oauth/github/callback"));
    }
    @Test void callbackInjectionAndUnapprovedInsecureCookiesAreRejected() {
        var request=new MockHttpServletRequest(); var response=new MockHttpServletResponse();
        for(String url:List.of("http://example.invalid/api/oauth/gitee/callback", "https://user@example.invalid/api/oauth/gitee/callback",
                "https://example.invalid/api/oauth/gitee/callback?redirect=evil", "https://example.invalid/api/oauth/gitee/callback#evil",
                "https://example.invalid/api/../oauth/gitee/callback", "https://example.invalid/api/%6fauth/gitee/callback", "not a uri")) {
            assertThrows(BadCredentialsException.class,()->service.begin(request,response,1,url));
        }
        ReflectionTestUtils.setField(service,"allowLocalInsecureCookie",true); request.setServerName("localhost");
        assertThrows(BadCredentialsException.class,()->service.begin(request,response,1,"http://example.invalid/api/oauth/gitee/callback"));
    }
    @Test void redisWriteAndReadFailuresNeverProduceCredential() {
        var a=begin(1,null); service.validateCallback(a.request(),a.state(),1);
        StringRedisTemplate broken=mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new IllegalStateException("private-secret"));
        ReflectionTestUtils.setField(service,"stringRedisTemplate",broken);
        var failure=assertThrows(BadCredentialsException.class,()->service.issue(a.request(),1,42)); assertFalse(failure.getMessage().contains("private-secret"));
        assertThrows(BadCredentialsException.class,()->service.begin(new MockHttpServletRequest(),new MockHttpServletResponse(),1,"https://example.invalid/api/oauth/gitee/callback"));
    }
}
