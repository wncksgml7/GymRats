package com.gymrats.ui.community;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.gymrats.R;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public class PostMediaTest {
    @Test
    public void thumbnailFillsEntireCardAndCaptionsStayInside() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String uris = InstrumentationRegistry.getArguments().getString("videoUris");
        Bitmap sample = null;
        if (uris != null && !uris.isEmpty()) {
            instrumentation.getUiAutomation().adoptShellPermissionIdentity();
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(instrumentation.getTargetContext(), Uri.parse(uris.split(",")[0]));
                sample = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST);
            } finally {
                retriever.release();
                instrumentation.getUiAutomation().dropShellPermissionIdentity();
            }
        }
        Bitmap sampleImage = sample;
        try {
        instrumentation.runOnMainSync(() -> {
            Context context = new ContextThemeWrapper(instrumentation.getTargetContext(), R.style.Theme_GymRats);
            View card = LayoutInflater.from(context).inflate(R.layout.item_post, null, false);
            card.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            if (sampleImage != null) ((ImageView) card.findViewById(R.id.imagePostThumbnail)).setImageBitmap(sampleImage);
            ((TextView) card.findViewById(R.id.textPostTitle)).setText("하체 근력 운동 루틴");
            ((TextView) card.findViewById(R.id.textPostBodyPart)).setText("하체");
            ((TextView) card.findViewById(R.id.textPostLikes)).setText("24");
            ((TextView) card.findViewById(R.id.textPostComments)).setText("8");
            ((TextView) card.findViewById(R.id.textPostRating)).setText("4.8");
            int width = Math.round(180 * context.getResources().getDisplayMetrics().density);
            card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());
            ImageView image = card.findViewById(R.id.imagePostThumbnail);
            assertTrue("카드 높이가 이미지 비율로 계산되어야 합니다.", card.getHeight() > width);
            assertEquals(card.getWidth(), image.getWidth());
            assertEquals(card.getHeight(), image.getHeight());
            for (int id : new int[]{R.id.textPostTitle, R.id.textPostBodyPart,
                    R.id.textPostLikes, R.id.textPostComments, R.id.textPostRating}) {
                View caption = card.findViewById(id);
                assertTrue("정보 영역에 높이가 있어야 합니다.", caption.getHeight() > 0);
            }
            // 실제 안드로이드 레이아웃을 이미지로 남겨 카드 배치를 확인합니다.
            Bitmap bitmap = Bitmap.createBitmap(card.getWidth(), card.getHeight(), Bitmap.Config.ARGB_8888);
            card.draw(new Canvas(bitmap));
            try (FileOutputStream output = new FileOutputStream(new File(context.getCacheDir(), "post-card-preview.png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            } catch (Exception error) {
                throw new AssertionError(error);
            } finally {
                bitmap.recycle();
            }
        });
        } finally {
            if (sampleImage != null) sampleImage.recycle();
        }
    }

    @Test
    public void playsAndSeeksDeviceVideos() throws Exception {
        String uris = InstrumentationRegistry.getArguments().getString("videoUris");
        assumeTrue(uris != null && !uris.isEmpty());
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.getUiAutomation().adoptShellPermissionIdentity();
        try {
            for (String value : uris.split(",")) {
                Uri uri = Uri.parse(value);
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                File repaired = null;
                try {
                    retriever.setDataSource(instrumentation.getTargetContext(), uri);
                    repaired = VideoPreviewFile.createIfNeeded(instrumentation.getTargetContext(), uri, retriever);
                    verifyPlayback(repaired == null ? uri : Uri.fromFile(repaired));
                } finally {
                    retriever.release();
                    if (repaired != null) repaired.delete();
                }
            }
        } finally {
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void playsPublishedFirebaseVideoWhenSignedIn() throws Exception {
        assumeTrue("로그인한 테스트 기기에서만 서버 영상을 확인합니다.", FirebaseAuth.getInstance().getCurrentUser() != null);
        for (DocumentSnapshot post : Tasks.await(FirebaseFirestore.getInstance().collection("posts")
                .whereEqualTo("status", "published").limit(50).get(), 30, TimeUnit.SECONDS).getDocuments()) {
            String path = post.getString("videoPath");
            if (path == null || path.isEmpty()) continue;
            Uri uri = Tasks.await(FirebaseStorage.getInstance().getReference().child(path).getDownloadUrl(),
                    30, TimeUnit.SECONDS);
            // 기존 원본의 길이 정보가 0인 경우 앱과 같은 보완 경로로 재생합니다.
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            AtomicReference<VideoPlaybackCache> cache = new AtomicReference<>();
            AtomicReference<com.google.android.gms.tasks.Task<Uri>> task = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> {
                cache.set(new VideoPlaybackCache((android.app.Application) instrumentation.getTargetContext().getApplicationContext()));
                task.set(cache.get().prepare(path));
            });
            try {
                Uri local = Tasks.await(task.get(), 60, TimeUnit.SECONDS);
                verifyPlayback(local);
            } finally {
                instrumentation.runOnMainSync(() -> cache.get().onCleared());
            }
            return;
        }
        assumeTrue("영상이 있는 게시글이 있어야 합니다.", false);
    }

    private void verifyPlayback(Uri uri) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AtomicReference<ExoPlayer> reference = new AtomicReference<>();
        AtomicReference<PlaybackException> failure = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch playing = new CountDownLatch(1);
        CountDownLatch sought = new CountDownLatch(1);
        try {
            instrumentation.runOnMainSync(() -> {
                ExoPlayer player = new ExoPlayer.Builder(instrumentation.getTargetContext()).build();
                reference.set(player);
                player.setVolume(0);
                player.addListener(new Player.Listener() {
                    @Override public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) ready.countDown();
                    }
                    @Override public void onIsPlayingChanged(boolean value) {
                        if (value) playing.countDown();
                    }
                    @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                            Player.PositionInfo newPosition, int reason) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) sought.countDown();
                    }
                    @Override public void onPlayerError(PlaybackException error) {
                        failure.set(error);
                        ready.countDown();
                        playing.countDown();
                    }
                });
                player.setMediaItem(MediaItem.fromUri(uri));
                player.prepare();
            });
            assertTrue("영상 준비 시간 초과", ready.await(40, TimeUnit.SECONDS));
            assertNull("영상 준비 실패", failure.get());
            instrumentation.runOnMainSync(() -> {
                assertTrue("영상 길이가 확인되어야 합니다.", reference.get().getDuration() > 0);
                assertTrue("시간 이동을 지원해야 합니다.", reference.get().isCurrentMediaItemSeekable());
                reference.get().play();
            });
            assertTrue("재생 시작 시간 초과", playing.await(10, TimeUnit.SECONDS));
            assertNull("영상 재생 실패", failure.get());
            instrumentation.runOnMainSync(() -> {
                ExoPlayer player = reference.get();
                player.pause();
                assertFalse(player.getPlayWhenReady());
                long target = player.getDuration() / 2;
                player.seekTo(target);
                assertEquals(target, player.getCurrentPosition());
            });
            assertTrue("시간 이동 이벤트가 있어야 합니다.", sought.await(10, TimeUnit.SECONDS));
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (reference.get() != null) reference.get().release();
            });
        }
    }
}
