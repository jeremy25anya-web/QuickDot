package ca.amartian.quickdot.monitoring;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Last line of defence before an event leaves the process for Sentry (spec §3:
 * "errors only, no PII"). Picked up by the Sentry Spring Boot starter as both
 * the before-send and before-breadcrumb callback.
 *
 * <ul>
 *   <li>Drops request data and user info outright — no IPs, headers, URLs.</li>
 *   <li>Drops every exception message, keeping only type + stack trace.
 *       Library messages can quote input (e.g. a JSON parser echoing the
 *       payload it choked on), which could be room content.</li>
 *   <li>Redacts room codes from log messages and breadcrumbs — a code is a
 *       live bearer credential for its room, and Spring's WebSocket error
 *       logging includes the session URI ({@code /ws/CODE}).</li>
 * </ul>
 */
@Component
public class SentryScrubber implements SentryOptions.BeforeSendCallback, SentryOptions.BeforeBreadcrumbCallback {

    private static final Pattern ROOM_PATH = Pattern.compile("(/(?:ws|r|api/rooms)/)[A-Za-z0-9]+");
    static final String REDACTED_MESSAGE = "[redacted]";

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        event.setRequest(null);
        event.setUser(null);
        event.setServerName(null);

        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            exceptions.forEach(e -> e.setValue(REDACTED_MESSAGE));
        }

        Message message = event.getMessage();
        if (message != null) {
            message.setFormatted(redact(message.getFormatted()));
            message.setMessage(redact(message.getMessage()));
            List<String> params = message.getParams();
            if (params != null) {
                message.setParams(params.stream().map(SentryScrubber::redact).toList());
            }
        }
        return event;
    }

    @Override
    public Breadcrumb execute(Breadcrumb breadcrumb, Hint hint) {
        breadcrumb.setMessage(redact(breadcrumb.getMessage()));
        Map<String, Object> data = breadcrumb.getData();
        data.replaceAll((key, value) -> value instanceof String s ? redact(s) : value);
        return breadcrumb;
    }

    static String redact(String text) {
        return text == null ? null : ROOM_PATH.matcher(text).replaceAll("$1:code");
    }
}
