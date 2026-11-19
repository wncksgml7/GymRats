package com.gymrats.ui.community;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import java.io.File;

/** videoUris 인자로 지정한 장면이 변화하는 테스트 영상으로 길이와 탐색을 확인합니다. */
public class VideoDurationReaderTest {
    @Test
    public void readsDurationAndSelectedFramesFromDeviceVideos() throws Exception {
        String uris = InstrumentationRegistry.getArguments().getString("videoUris");
        assumeTrue("검증할 영상 URI를 지정해야 합니다.", uris != null && !uris.isEmpty());
        assumeTrue(Build.VERSION.SDK_INT >= 33);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        // 파일 선택창을 거치지 않는 기기 테스트에서만 셸의 파일 읽기 권한을 사용합니다.
        instrumentation.getUiAutomation().adoptShellPermissionIdentity();
        try {
            for (String value : uris.split(",")) {
                Uri uri = Uri.parse(value);
                Log.i("VideoDurationTest", "검증할 영상=" + uri);
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                File preview = null;
                try {
                    retriever.setDataSource(instrumentation.getTargetContext(), uri);
                    String original = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    long duration = VideoDurationReader.read(instrumentation.getTargetContext(), uri, retriever);
                    assertTrue("영상 길이는 양수여야 합니다: " + uri, duration > 0);
                    preview = VideoPreviewFile.createIfNeeded(instrumentation.getTargetContext(), uri, retriever);
                    if (preview != null) retriever.setDataSource(preview.getAbsolutePath());
                    Bitmap first = null;
                    try {
                        for (int progress : new int[]{0, 5000, 9900}) {
                            long time = ThumbnailTimeline.timeAt(progress, duration);
                            Bitmap frame = retriever.getScaledFrameAtTime(time * 1000,
                                    MediaMetadataRetriever.OPTION_CLOSEST, 320, 320);
                            assertNotNull("선택 장면 추출 실패: " + time, frame);
                            if (first == null) {
                                first = frame;
                            } else {
                                Log.i("VideoDurationTest", "위치=" + progress + ", 첫 장면과 동일=" + first.sameAs(frame));
                                if (progress == 5000) assertTrue("중간 위치에서도 첫 장면이 반환됐습니다.", !first.sameAs(frame));
                                frame.recycle();
                            }
                        }
                    } finally {
                        if (first != null) first.recycle();
                    }
                    Log.i("VideoDurationTest", "영상=" + uri + ", 기존 길이=" + original + ", 확인한 길이=" + duration);
                } finally {
                    retriever.release();
                    if (preview != null) preview.delete();
                }
            }
        } finally {
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }
}
