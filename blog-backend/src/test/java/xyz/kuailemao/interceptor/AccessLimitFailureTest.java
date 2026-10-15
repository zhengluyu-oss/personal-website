package xyz.kuailemao.interceptor;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import xyz.kuailemao.annotation.AccessLimit;
import xyz.kuailemao.service.AccessWindowService;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AccessLimitFailureTest {
    static class Endpoint { @AccessLimit(seconds=2,maxCount=2) public void limited() {} }
    @Test void failureReturnsExplicitUnavailableJson() throws Exception {
        var windows=mock(AccessWindowService.class);
        when(windows.increment(anyString(),anyInt())).thenThrow(new RedisConnectionFailureException("isolated"));
        var interceptor=new AccessLimitInterceptor(); ReflectionTestUtils.setField(interceptor,"accessWindowService",windows);
        var request=new MockHttpServletRequest("GET","/qa"); request.setRemoteAddr("127.0.0.1");
        var response=new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request,response,new HandlerMethod(new Endpoint(),Endpoint.class.getMethod("limited"))));
        assertEquals(503,response.getStatus()); assertTrue(response.getContentAsString().contains("暂不可用"));
        assertTrue(response.getContentType().contains("application/json"));
    }
}
