package io.split.client.impressions;

import io.split.client.SplitClientConfig;
import io.split.client.dtos.KeyImpression;
import io.split.client.dtos.TestImpressions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.URISyntaxException;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Created by patricioe on 6/20/16.
 */
@RunWith(MockitoJUnitRunner.class)
public class ImpressionsManagerTest {

    @Captor
    private ArgumentCaptor<List<TestImpressions>> impressionsCaptor;

    @Test
    public void works() throws URISyntaxException {

        SplitClientConfig config = SplitClientConfig.builder()
                .impressionsQueueSize(4)
                .endpoint("nowhere.com", "nowhere.com")
                .build();

        ImpressionsSender senderMock = Mockito.mock(ImpressionsSender.class);

        ImpressionsManager treatmentLog = ImpressionsManager.instanceForTest(null, config, senderMock);

        KeyImpression ki1 = keyImpression("test1", "adil", "on", 1L);
        KeyImpression ki2 = keyImpression("test1", "adil", "on", 2L);
        KeyImpression ki3 = keyImpression("test1", "pato", "on", 3L);
        KeyImpression ki4 = keyImpression("test2", "pato", "on", 4L);

        treatmentLog.log(ki1.keyName, null, ki1.feature, ki1.treatment, ki1.time, null);
        treatmentLog.log(ki2.keyName, null, ki2.feature, ki2.treatment, ki2.time, null);
        treatmentLog.log(ki3.keyName, null, ki3.feature, ki3.treatment, ki3.time, null);
        treatmentLog.log(ki4.keyName, null, ki4.feature, ki4.treatment, ki4.time, null);

        // Do what the scheduler would do.
        treatmentLog.run();

        verify(senderMock).post(impressionsCaptor.capture());

        List<TestImpressions> captured = impressionsCaptor.getValue();

        assertThat(captured.size(), is(equalTo(2)));
    }

    @Test
    public void worksButDropsImpressions() throws URISyntaxException {

        SplitClientConfig config = SplitClientConfig.builder()
                .impressionsQueueSize(3)
                .endpoint("nowhere.com", "nowhere.com")
                .build();

        ImpressionsSender senderMock = Mockito.mock(ImpressionsSender.class);

        ImpressionsManager treatmentLog = ImpressionsManager.instanceForTest(null, config, senderMock);

        // These 4 unique test name will cause 4 entries but we are caping at the first 3.
        KeyImpression ki1 = keyImpression("test1", "adil", "on", 1L);
        KeyImpression ki2 = keyImpression("test2", "adil", "on", 2L);
        KeyImpression ki3 = keyImpression("test3", "pato", "on", 3L);
        KeyImpression ki4 = keyImpression("test4", "pato", "on", 4L);

        treatmentLog.log(ki1.keyName, null, ki1.feature, ki1.treatment, ki1.time, null);
        treatmentLog.log(ki2.keyName, null, ki2.feature, ki2.treatment, ki2.time, null);
        treatmentLog.log(ki3.keyName, null, ki3.feature, ki3.treatment, ki3.time, null);
        treatmentLog.log(ki4.keyName, null, ki4.feature, ki4.treatment, ki4.time, null);

        // Do what the scheduler would do.
        treatmentLog.run();

        verify(senderMock).post(impressionsCaptor.capture());

        List<TestImpressions> captured = impressionsCaptor.getValue();

        assertThat(captured.size(), is(equalTo(3)));
    }

    @Test
    public void works4ImpressionsInOneTest() throws URISyntaxException {

        SplitClientConfig config = SplitClientConfig.builder()
                .impressionsQueueSize(10)
                .endpoint("nowhere.com", "nowhere.com")
                .build();

        ImpressionsSender senderMock = Mockito.mock(ImpressionsSender.class);

        ImpressionsManager treatmentLog = ImpressionsManager.instanceForTest(null, config, senderMock);

        // These 4 unique test name will cause 4 entries but we are caping at the first 3.
        KeyImpression ki1 = keyImpression("test1", "adil", "on", 1L);
        KeyImpression ki2 = keyImpression("test1", "adil", "on", 2L);
        KeyImpression ki3 = keyImpression("test1", "pato", "on", 3L);
        KeyImpression ki4 = keyImpression("test1", "pato", "on", 4L);

        treatmentLog.log(ki1.keyName, null, ki1.feature, ki1.treatment, ki1.time, null);
        treatmentLog.log(ki2.keyName, null, ki2.feature, ki2.treatment, ki2.time, null);
        treatmentLog.log(ki3.keyName, null, ki3.feature, ki3.treatment, ki3.time, null);
        treatmentLog.log(ki4.keyName, null, ki4.feature, ki4.treatment, ki4.time, null);

        // Do what the scheduler would do.
        treatmentLog.run();

        verify(senderMock).post(impressionsCaptor.capture());

        List<TestImpressions> captured = impressionsCaptor.getValue();

        assertThat(captured.size(), is(equalTo(1)));
        assertThat(captured.get(0).keyImpressions.size(), is(equalTo(4)));
        assertThat(captured.get(0).keyImpressions.get(0), is(equalTo(ki1)));
    }

    @Test
    public void worksNoImpressions() throws URISyntaxException {

        SplitClientConfig config = SplitClientConfig.builder()
                .impressionsQueueSize(10)
                .endpoint("nowhere.com", "nowhere.com")
                .build();

        ImpressionsSender senderMock = Mockito.mock(ImpressionsSender.class);
        ImpressionsManager treatmentLog = ImpressionsManager.instanceForTest(null, config, senderMock);

        // There are no impressions to post.

        // Do what the scheduler would do.
        treatmentLog.run();

        verify(senderMock, never()).post(impressionsCaptor.capture());
    }

    private KeyImpression keyImpression(String feature, String key, String treatment, long time) {
        KeyImpression result = new KeyImpression();
        result.feature = feature;
        result.keyName = key;
        result.treatment = treatment;
        result.time = time;
        return result;
    }

}