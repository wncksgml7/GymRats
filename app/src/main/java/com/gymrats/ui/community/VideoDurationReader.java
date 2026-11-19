package com.gymrats.ui.community;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.IOException;

/** 대표 메타데이터에 길이가 없는 영상도 실제 영상 트랙에서 길이를 확인합니다. */
final class VideoDurationReader {
    private VideoDurationReader() {}

    static long read(Context context, Uri uri, MediaMetadataRetriever retriever) throws IOException {
        String metadata = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        if (metadata != null) {
            try {
                long duration = Long.parseLong(metadata);
                if (duration > 0) return duration;
            } catch (NumberFormatException ignored) {
                // 길이 메타데이터가 없거나 잘못되어도 영상 트랙을 다시 확인합니다.
            }
        }

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, uri, null);
            long durationUs = 0;
            boolean hasVideo = false;
            for (int index = 0; index < extractor.getTrackCount(); index++) {
                MediaFormat format = extractor.getTrackFormat(index);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("video/")) continue;
                hasVideo = true;
                extractor.selectTrack(index);
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = Math.max(durationUs, format.getLong(MediaFormat.KEY_DURATION));
                }
            }
            if (!hasVideo) throw new IOException("영상 트랙이 없습니다.");
            if (durationUs > 0) return Math.max(1, durationUs / 1000);

            // 조각화된 MP4처럼 트랙 길이도 없는 경우 실제 프레임 시각을 읽습니다.
            // 영상 디코딩 없이 샘플 정보만 순회하며 오디오 트랙은 제외합니다.
            long last = -1;
            long previous = -1;
            while (extractor.getSampleTime() >= 0) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("길이 확인이 취소되었습니다.");
                long time = extractor.getSampleTime();
                if (time > last) {
                    previous = last;
                    last = time;
                } else if (time < last && time > previous) {
                    previous = time;
                }
                if (!extractor.advance()) break;
            }
            if (last < 0) throw new IOException("영상 프레임이 없습니다.");
            long frameDuration = previous >= 0 ? Math.max(1, last - previous) : 1;
            return Math.max(1, (last + frameDuration + 999) / 1000);
        } finally {
            extractor.release();
        }
    }
}
