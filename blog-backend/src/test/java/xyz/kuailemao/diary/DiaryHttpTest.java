package xyz.kuailemao.diary;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DiaryHttpTest {
    @AfterEach void clearIdentity(){SecurityContextHolder.clearContext();}
    private void login(long id) {
        var principal=new LoginUser(User.builder().id(id).build(),List.of("ROLE_ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
    }
    @SuppressWarnings("unchecked")
    @Test void directCallsCannotUseAdminRoleOrSpoofOwnerAndErrorsArePrivate() throws Exception {
        var properties=new DiaryProperties();properties.setOwnerUserId(7L);
        var provider=mock(ObjectProvider.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DiaryController(new DiaryService(properties,provider)))
                .setControllerAdvice(new DiaryExceptionHandler()).build();
        String id=UUID.randomUUID().toString();
        mvc.perform(get("/diary/auth/entries/"+id)).andExpect(status().isUnauthorized());
        login(8);
        String write="{\"title\":\"\",\"body\":\"synthetic-private-secret\",\"entryId\":\""+id+"\",\"mutationId\":\""+UUID.randomUUID()+"\",\"expectedRevision\":1}";
        var attempts=List.of(get("/diary/auth/entries/"+id),get("/diary/auth/entries/"+id+"/history"),
                get("/diary/auth/calendar?month=2026-10"),post("/diary/auth/entries").content(write),
                put("/diary/auth/entries/"+id).content(write),delete("/diary/auth/entries/"+id).content("{\"expectedRevision\":1}"),
                post("/diary/auth/entries/"+id+"/restore").content("{\"expectedRevision\":1,\"sourceRevision\":1}"),
                post("/diary/auth/entries/search").content("{\"keyword\":\"synthetic-private-secret\"}"),
                post("/diary/auth/export").content("{\"format\":\"json\"}"));
        for(var attempt:attempts) {
            mvc.perform(attempt.param("owner","7").contentType("application/json"))
                    .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control","private, no-store"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-private-secret"))));
        }
        verifyNoInteractions(provider);
        login(7);
        mvc.perform(get("/diary/auth/entries/"+id)).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/diary/auth/entries").contentType("application/json").content("synthetic-private-secret"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic-private-secret"))));
        mvc.perform(get("/diary/auth/entries/"+id+"/restore")).andExpect(status().isMethodNotAllowed());
    }
}
