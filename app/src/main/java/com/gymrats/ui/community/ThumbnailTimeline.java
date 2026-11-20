package com.gymrats.ui.community;

import java.util.Locale;

/** 시간 조절 바의 위치를 실제 영상 길이에 맞춰 변환합니다. */
final class ThumbnailTimeline {
    static final int MAX_PROGRESS = 10000;

    private ThumbnailTimeline() {}

    static long timeAt(int progress, long durationMs) {
        long lastTime = Math.max(0, durationMs - 1);
        int position = Math.max(0, Math.min(progress, MAX_PROGRESS));
        return Math.round(lastTime * (position / (double) MAX_PROGRESS));
    }

    static int progressAt(long timeMs, long durationMs) {
        long lastTime = Math.max(0, durationMs - 1);
        if (lastTime == 0) return 0;
        long time = Math.max(0, Math.min(timeMs, lastTime));
        return (int) Math.round(time * (double) MAX_PROGRESS / lastTime);
    }

    static String formatTime(long timeMs) {
        long time = Math.max(0, timeMs);
        return String.format(Locale.getDefault(), "%02d:%02d.%03d",
                time / 60000, (time / 1000) % 60, time % 1000);
    }
}
