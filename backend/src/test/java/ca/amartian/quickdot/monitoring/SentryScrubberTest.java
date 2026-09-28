package ca.amartian.quickdot.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentryScrubberTest {

    private final SentryScrubber scrubber = new SentryScrubber();

    @Test
    void dropsRequestUserAndServerName() {
        SentryEvent event = new SentryEvent();
        Request request = new Request();
        request.setUrl("https://api.quickdot.amartian.ca/ws/ABC234");
        event.setRequest(request);
        User user = new User();
        user.setIpAddress("203.0.113.5");
        event.setUser(user);
        event.setServerName("machine-1");

        SentryEvent out = scrubber.execute(event, new Hint());

        assertThat(out.getRequest()).isNull();
        assertThat(out.getUser()).isNull();
        assertThat(out.getServerName()).isNull();
    }

    @Test
    void replacesExceptionMessagesButKeepsTypes() {
        SentryEvent event = new SentryEvent();
        SentryException ex = new SentryException();
        ex.setType("JsonParseException");
        ex.setValue("Unexpected character in \"my secret message\"");
        event.setExceptions(List.of(ex));

        SentryEvent out = scrubber.execute(event, new Hint());

        assertThat(out.getExceptions().get(0).getValue()).isEqualTo(SentryScrubber.REDACTED_MESSAGE);
        assertThat(out.getExceptions().get(0).getType()).isEqualTo("JsonParseException");
    }

    @Test
    void redactsRoomCodesInLogMessages() {
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setFormatted("Closing session StandardWebSocketSession[uri=wss://host/ws/ABC234]");
        message.setParams(List.of("/r/XYZ789"));
        event.setMessage(message);

        SentryEvent out = scrubber.execute(event, new Hint());

        assertThat(out.getMessage().getFormatted()).contains("/ws/:code").doesNotContain("ABC234");
        assertThat(out.getMessage().getParams()).containsExactly("/r/:code");
    }

    @Test
    void redactsRoomCodesInBreadcrumbs() {
        Breadcrumb crumb = new Breadcrumb();
        crumb.setMessage("GET /api/rooms/ABC234 failed");
        crumb.setData("url", "https://host/ws/ABC234");

        Breadcrumb out = scrubber.execute(crumb, new Hint());

        assertThat(out.getMessage()).isEqualTo("GET /api/rooms/:code failed");
        assertThat(out.getData("url")).isEqualTo("https://host/ws/:code");
    }

    @Test
    void leavesTextWithoutRoomPathsUntouched() {
        assertThat(SentryScrubber.redact("Room sweeper ran")).isEqualTo("Room sweeper ran");
        assertThat(SentryScrubber.redact(null)).isNull();
    }
}
