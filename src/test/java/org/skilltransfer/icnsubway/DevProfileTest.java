package org.skilltransfer.icnsubway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/** dev 프로필(H2 콘솔 켜짐)으로도 시작되는지. 다른 테스트와 DB가 섞이지 않게 메모리 DB 이름을 따로 쓴다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:icn-dev-test;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "dev"})
class DevProfileTest {
    @Autowired ApplicationContext context;

    @Test
    void startsWithH2ConsoleAndItsSecurityChain() {
        assertThat(context.containsBean("h2ConsoleSecurity")).isTrue();
        assertThat(context.containsBean("h2Console")).isTrue();
    }
}
