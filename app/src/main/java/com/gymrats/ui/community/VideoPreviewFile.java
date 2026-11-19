package com.gymrats.ui.community;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/** 길이가 없는 MP4의 썸네일 탐색을 위해 재인코딩 없이 임시 MP4를 만듭니다. */
final class VideoPreviewFile {
    private VideoPreviewFile() {}

    static File createIfNeeded(Context context, Uri uri, MediaMetadataRetriever retriever) throws IOException {
        String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        try {
            if (duration != null && Long.parseLong(duration) > 0) return null;
        } catch (NumberFormatException ignored) {
            // 원본의 길이 메타데이터가 잘못된 경우에도 임시 파일로 보완합니다.
        }
        File file = File.createTempFile("thumbnail-preview-", ".mp4", context.getCacheDir());
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean success = false;
        try {
            extractor.setDataSource(context, uri, null);
            muxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (rotation != null) muxer.setOrientationHint(Integer.parseInt(rotation));
            Map<Integer, Integer> tracks = new HashMap<>();
            int bufferSize = 8 * 1024 * 1024;
            for (int index = 0; index < extractor.getTrackCount(); index++) {
                MediaFormat format = extractor.getTrackFormat(index);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !(mime.startsWith("video/") || mime.startsWith("audio/"))) continue;
                tracks.put(index, muxer.addTrack(format));
                extractor.selectTrack(index);
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    bufferSize = Math.max(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                }
            }
            if (tracks.isEmpty()) throw new IOException("복사할 영상 트랙이 없습니다.");
            muxer.start();
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (extractor.getSampleTrackIndex() >= 0) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("영상 준비가 취소되었습니다.");
                if (Build.VERSION.SDK_INT >= 28) {
                    long size = extractor.getSampleSize();
                    if (size > Integer.MAX_VALUE) throw new IOException("영상 프레임이 너무 큽니다.");
                    if (size > buffer.capacity()) buffer = ByteBuffer.allocateDirect((int) size);
                }
                buffer.clear();
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                // 동기 프레임 표시만 전달합니다. 추출기와 코덱의 다른 플래그 값은 서로 다릅니다.
                int flags = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                info.set(0, size, extractor.getSampleTime(), flags);
                muxer.writeSampleData(tracks.get(extractor.getSampleTrackIndex()), buffer, info);
                if (!extractor.advance()) break;
            }
            muxer.stop();
            success = true;
            return file;
        } finally {
            extractor.release();
            try {
                if (muxer != null) muxer.release();
            } finally {
                if (!success) file.delete();
            }
        }
    }
}
