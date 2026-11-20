package com.gymrats.ui.community;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThumbnailTimelineTest {
    @Test
    public void endpointsStayInsideVideo() {
        assertEquals(0, ThumbnailTimeline.timeAt(-1, 1000));
        assertEquals(999, ThumbnailTimeline.timeAt(10000, 1000));
        assertEquals(999, ThumbnailTimeline.timeAt(12000, 1000));
    }

    @Test
    public void shortAndMissingDurationsDoNotDivideByZero() {
        assertEquals(0, ThumbnailTimeline.timeAt(5000, 0));
        assertEquals(0, ThumbnailTimeline.timeAt(10000, 1));
        assertEquals(0, ThumbnailTimeline.progressAt(500, 1));
        assertEquals(0, ThumbnailTimeline.progressAt(500, -1));
    }

    @Test
    public void restoredSelectionMapsBackToSamePosition() {
        long duration = 120001;
        for (int progress = 0; progress <= 10000; progress += 137) {
            long time = ThumbnailTimeline.timeAt(progress, duration);
            assertEquals(progress, ThumbnailTimeline.progressAt(time, duration));
        }
    }

    @Test
    public void longVideosDoNotOverflowIntegerMilliseconds() {
        long duration = 3000000000L;
        assertEquals(duration - 1, ThumbnailTimeline.timeAt(10000, duration));
        assertTrue(ThumbnailTimeline.timeAt(5000, duration) > 0);
        assertEquals(10000, ThumbnailTimeline.progressAt(Long.MAX_VALUE, duration));
        assertEquals(0, ThumbnailTimeline.progressAt(-1, duration));
    }
}
