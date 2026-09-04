package ca.amartian.quickdot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// RANDOM_PORT (a real embedded server), not the default MOCK environment:
// ServletServerContainerFactoryBean (ws.WebSocketConfig) needs an actual
// jakarta.websocket.server.ServerContainer, which only a real server provides.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuickdotApplicationTests {

	@Test
	void contextLoads() {
	}

}
