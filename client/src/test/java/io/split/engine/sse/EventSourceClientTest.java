package io.split.engine.sse;

import io.split.SplitServersMock;
import io.split.engine.sse.dtos.ErrorNotification;
import io.split.engine.sse.dtos.SplitChangeNotification;
import io.split.engine.sse.exceptions.EventParsingException;
import org.awaitility.Awaitility;
import org.glassfish.grizzly.utils.Pair;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.sse.OutboundSseEvent;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class EventSourceClientTest {
    private NotificationParser _notificationParser;
    private NotificationProcessor _notificationProcessor;
    private PushStatusTracker _pushStatusTracker;

    @Before
    public void setUp() {
        _notificationParser = Mockito.mock(NotificationParser.class);
        _notificationProcessor = Mockito.mock(NotificationProcessor.class);
        _pushStatusTracker = Mockito.mock(PushStatusTracker.class);
    }

    @Test
    public void startShouldConnect() throws IOException {
        SplitServersMock.SseEventQueue eventQueue = new SplitServersMock.SseEventQueue();
        SplitServersMock sseServer = buildSSEMockServer(eventQueue);
        sseServer.start();
        Client client = ClientBuilder.newBuilder().readTimeout(70, TimeUnit.SECONDS).build();

        EventSourceClient eventSourceClient = new EventSourceClientImp("http://localhost:" + sseServer.getPort(), _notificationParser, _notificationProcessor, client, _pushStatusTracker);

        boolean result = eventSourceClient.start("channel-test","token-test");

        Assert.assertTrue(result);

        Mockito.verify(_pushStatusTracker, Mockito.times(1)).handleSseStatus(SseStatus.CONNECTED);
    }

    @Test
    public void startShouldNotConnect() throws IOException, InterruptedException {
        SplitServersMock.SseEventQueue eventQueue = new SplitServersMock.SseEventQueue();
        SplitServersMock sseServer = buildSSEMockServer(eventQueue);
        sseServer.start();
        Client client = ClientBuilder.newBuilder().readTimeout(70, TimeUnit.SECONDS).build();

        EventSourceClient eventSourceClient = new EventSourceClientImp("http://fake:" + sseServer.getPort(), _notificationParser, _notificationProcessor, client, _pushStatusTracker);

        boolean result = eventSourceClient.start("channel-test","token-test");

        Assert.assertFalse(result);

        Awaitility.await()
                .atMost(50L, TimeUnit.SECONDS)
                .untilAsserted(() -> Mockito.verify(_pushStatusTracker, Mockito.times(1)).handleSseStatus(SseStatus.NONRETRYABLE_ERROR));
    }

    @Test
    public void startAndReceiveNotification() throws IOException, InterruptedException, EventParsingException {
        SplitServersMock.SseEventQueue eventQueue = new SplitServersMock.SseEventQueue();
        SplitServersMock sseServer = buildSSEMockServer(eventQueue);
        sseServer.start();
        Client client = ClientBuilder.newBuilder().readTimeout(70, TimeUnit.SECONDS).build();

        EventSourceClient eventSourceClient = new EventSourceClientImp("http://localhost:" + sseServer.getPort(), _notificationParser, _notificationProcessor, client, _pushStatusTracker);

        boolean result = eventSourceClient.start("channel-test","token-test");

        Assert.assertTrue(result);

        Mockito.verify(_pushStatusTracker, Mockito.times(1)).handleSseStatus(SseStatus.CONNECTED);

        OutboundSseEvent sseEvent = new OutboundEvent
                .Builder()
                .name("message")
                .data("{\"id\":\"22\",\"clientId\":\"22\",\"timestamp\":1592590436082,\"encoding\":\"json\",\"channel\":\"xxxx_xxxx_splits\",\"data\":\"{\\\"type\\\":\\\"SPLIT_UPDATE\\\",\\\"changeNumber\\\":1585948850111}\"}")
                .build();
        eventQueue.push(sseEvent);

        Awaitility.await()
                .atMost(50L, TimeUnit.SECONDS)
                .untilAsserted(() -> Mockito.verify(_notificationParser, Mockito.times(1)).parseMessage(Mockito.anyString()));

        Awaitility.await()
                .atMost(50L, TimeUnit.SECONDS)
                .untilAsserted(() -> Mockito.verify(_notificationProcessor, Mockito.times(1)).process(Mockito.any(SplitChangeNotification.class)));

        OutboundSseEvent sseEventError = new OutboundEvent
                .Builder()
                .name("error")
                .data("{\"message\":\"Token expired\",\"code\":40142,\"statusCode\":401,\"href\":\"https://help.io/error/40142\"}")
                .build();
        eventQueue.push(sseEventError);

        Awaitility.await()
                .atMost(50L, TimeUnit.SECONDS)
                .untilAsserted(() -> Mockito.verify(_notificationParser, Mockito.times(1)).parseError(Mockito.anyString()));

        Awaitility.await()
                .atMost(50L, TimeUnit.SECONDS)
                .untilAsserted(() -> Mockito.verify(_pushStatusTracker, Mockito.times(1)).handleIncomingAblyError(Mockito.any(ErrorNotification.class)));
    }

    private SplitServersMock buildSSEMockServer(SplitServersMock.SseEventQueue eventQueue) {
        return new SplitServersMock(eventQueue, (token, version, channel) -> {
            if (!"1.1".equals(version)) {
                return new Pair<>(new OutboundEvent.Builder().data("wrong version").build(), false);
            }
            return new Pair<>(null, true);
        });
    }
}
