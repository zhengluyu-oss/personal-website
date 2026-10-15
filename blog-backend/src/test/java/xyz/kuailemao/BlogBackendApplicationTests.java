package xyz.kuailemao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import xyz.kuailemao.config.Bean.CreateBean;
import static org.junit.jupiter.api.Assertions.*;

// Minimal real Spring assembly: external infrastructure is tested in explicit opt-in suites.
@SpringBootTest(classes = CreateBean.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class BlogBackendApplicationTests {

    @Autowired
    private PasswordEncoder passwordEncoder;


    @Test
    void contextLoads() {
        assertNotNull(passwordEncoder);
        String encoded = passwordEncoder.encode("isolated-test-password");
        assertNotEquals("isolated-test-password", encoded);
        assertTrue(passwordEncoder.matches("isolated-test-password", encoded));
        assertFalse(passwordEncoder.matches("different-password", encoded));
    }

}
