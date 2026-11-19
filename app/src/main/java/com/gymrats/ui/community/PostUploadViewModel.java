package com.gymrats.ui.community;

import android.app.Application;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 프래그먼트 뷰의 생명주기와 독립적으로 미디어 준비 및 업로드 상태를 유지합니다. */
public class PostUploadViewModel extends AndroidViewModel {
    final MutableLiveData<String> status = new MutableLiveData<>("동영상을 선택해주세요.");
    final MutableLiveData<Boolean> completed = new MutableLiveData<>(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    Uri videoUri;
    byte[] thumbnail;
    boolean busy;
    private String contentType;
    long durationMs;
    long selectedTimeMs;
    boolean extracting;
    private boolean frameRunning;
    private int selectionVersion;
    private boolean cleared;
    private File previewFile;

    public PostUploadViewModel(@NonNull Application application) {
        super(application);
    }

    void selectVideo(Uri uri) {
        selectVideo(uri, 0);
    }

    void selectVideo(Uri uri, long restoredTimeMs) {
        if (busy) return;
        int version = ++selectionVersion;
        videoUri = null;
        File previousPreview = previewFile;
        previewFile = null;
        if (previousPreview != null) executor.execute(previousPreview::delete);
        thumbnail = null;
        durationMs = 0;
        selectedTimeMs = 0;
        extracting = false;
        busy = true;
        status.setValue("동영상 준비 중…");
        Tasks.call(executor, () -> {
            String type = getApplication().getContentResolver().getType(uri);
            if (type == null || !type.startsWith("video/")) {
                throw new IllegalArgumentException("동영상 파일만 선택할 수 있습니다.");
            }
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            File preparedPreview = null;
            boolean prepared = false;
            try {
                retriever.setDataSource(getApplication(), uri);
                if (!"yes".equals(retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))) {
                    throw new IllegalArgumentException("영상 트랙이 없는 파일입니다.");
                }
                long duration;
                try {
                    duration = VideoDurationReader.read(getApplication(), uri, retriever);
                } catch (Exception error) {
                    throw new VideoPreparationException("동영상 길이를 확인할 수 없습니다. 다른 동영상을 선택해주세요.", error);
                }
                long time = Math.max(0, Math.min(restoredTimeMs, duration - 1));
                preparedPreview = VideoPreviewFile.createIfNeeded(getApplication(), uri, retriever);
                if (preparedPreview != null) retriever.setDataSource(preparedPreview.getAbsolutePath());
                PreparedVideo result = new PreparedVideo(type, duration, time,
                        extractFrame(retriever, time), preparedPreview);
                prepared = true;
                return result;
            } finally {
                retriever.release();
                if (!prepared && preparedPreview != null) preparedPreview.delete();
            }
        }).addOnCompleteListener(task -> {
            if (cleared || version != selectionVersion) {
                if (task.isSuccessful() && task.getResult().previewFile != null) {
                    task.getResult().previewFile.delete();
                }
                return;
            }
            busy = false;
            if (task.isSuccessful()) {
                PreparedVideo result = task.getResult();
                videoUri = uri;
                thumbnail = result.thumbnail;
                previewFile = result.previewFile;
                contentType = result.type;
                durationMs = result.duration;
                selectedTimeMs = result.time;
                status.setValue("바를 움직여 썸네일로 사용할 장면을 선택해주세요.");
            } else {
                Log.w("PostUpload", "동영상 준비 실패", task.getException());
                Exception error = task.getException();
                status.setValue(error instanceof VideoPreparationException
                        ? error.getMessage()
                        : "동영상을 읽을 수 없습니다. 다른 동영상을 선택해주세요.");
            }
        });
    }

    void selectThumbnail(long timeMs) {
        if (busy || videoUri == null || cleared) return;
        selectedTimeMs = Math.max(0, Math.min(timeMs, durationMs - 1));
        selectionVersion++;
        extracting = true;
        status.setValue("선택한 장면 불러오는 중…");
        loadLatestFrame();
    }

    private void loadLatestFrame() {
        // 추출은 한 번에 하나만 실행하고, 드래그 중 쌓인 요청은 마지막 위치로 합칩니다.
        if (frameRunning || busy || !extracting || videoUri == null || cleared) return;
        frameRunning = true;
        int version = selectionVersion;
        Uri uri = previewFile == null ? videoUri : Uri.fromFile(previewFile);
        long time = selectedTimeMs;
        Tasks.call(executor, () -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(getApplication(), uri);
                return extractFrame(retriever, time);
            } finally {
                retriever.release();
            }
        }).addOnCompleteListener(task -> {
            frameRunning = false;
            if (cleared) return;
            if (version != selectionVersion) {
                // 늦게 도착한 이전 장면으로 최신 선택을 덮어쓰지 않습니다.
                loadLatestFrame();
                return;
            }
            extracting = false;
            if (task.isSuccessful()) {
                thumbnail = task.getResult();
                status.setValue("현재 보이는 장면이 게시글 썸네일로 저장됩니다.");
            } else {
                thumbnail = null;
                Log.w("PostUpload", "선택한 장면 추출 실패", task.getException());
                status.setValue("이 장면을 불러올 수 없습니다. 바를 다른 위치로 이동해주세요.");
            }
        });
    }

    private static byte[] extractFrame(MediaMetadataRetriever retriever, long timeMs) {
        Bitmap frame = null;
        try {
            // 밀리초를 마이크로초로 변환하고 선택 시각에 가장 가까운 실제 프레임을 가져옵니다.
            long timeUs = timeMs * 1000L;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                frame = retriever.getScaledFrameAtTime(timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST, 720, 720);
            } else {
                frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
            }
            if (frame == null) throw new IllegalArgumentException("썸네일을 만들 수 없는 장면입니다.");
            float scale = Math.min(1f, 720f / Math.max(frame.getWidth(), frame.getHeight()));
            if (scale < 1f) {
                Bitmap scaled = Bitmap.createScaledBitmap(frame,
                        Math.max(1, Math.round(frame.getWidth() * scale)),
                        Math.max(1, Math.round(frame.getHeight() * scale)), true);
                if (scaled != frame) frame.recycle();
                frame = scaled;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!frame.compress(Bitmap.CompressFormat.JPEG, 85, bytes)) {
                throw new IllegalStateException("썸네일 변환에 실패했습니다.");
            }
            return bytes.toByteArray();
        } finally {
            if (frame != null) frame.recycle();
        }
    }

    private static class VideoPreparationException extends Exception {
        VideoPreparationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class PreparedVideo {
        final String type;
        final long duration;
        final long time;
        final byte[] thumbnail;
        final File previewFile;

        PreparedVideo(String type, long duration, long time, byte[] thumbnail, File previewFile) {
            this.type = type;
            this.duration = duration;
            this.time = time;
            this.thumbnail = thumbnail;
            this.previewFile = previewFile;
        }
    }
    void upload(Map<String, Object> post, String uid) {
        if (busy || extracting || videoUri == null || thumbnail == null) return;
        busy = true;
        status.setValue("동영상 업로드 중…");
        DocumentReference document = FirebaseFirestore.getInstance().collection("posts").document();
        // Firestore 규칙과 동일하게 맨 앞에 슬래시가 없는 경로를 저장합니다.
        String mediaPath = "posts/" + uid + "/" + document.getId();
        StorageReference folder = FirebaseStorage.getInstance().getReference()
                .child(mediaPath);
        StorageReference video = folder.child("video");
        StorageReference cover = folder.child("thumbnail.jpg");
        post.put("videoPath", mediaPath + "/video");
        post.put("thumbnailPath", mediaPath + "/thumbnail.jpg");
        video.putFile(videoUri, new StorageMetadata.Builder().setContentType(contentType).build())
                .addOnProgressListener(snapshot -> {
                    long total = snapshot.getTotalByteCount();
                    if (total > 0) status.setValue("동영상 업로드 중… "
                            + (snapshot.getBytesTransferred() * 100 / total) + "%");
                })
                .onSuccessTask(snapshot -> {
                    status.setValue("썸네일 업로드 중…");
                    return cover.putBytes(thumbnail,
                            new StorageMetadata.Builder().setContentType("image/jpeg").build());
                })
                .addOnCompleteListener(mediaTask -> {
                    if (!mediaTask.isSuccessful()) {
                        // 아직 게시글을 저장하지 않았으므로 일부만 업로드된 파일을 삭제합니다.
                        video.delete().addOnFailureListener(e -> Log.w("PostUpload", "Video cleanup failed", e));
                        cover.delete().addOnFailureListener(e -> Log.w("PostUpload", "Thumbnail cleanup failed", e));
                        fail(mediaTask.getException());
                        return;
                    }
                    status.setValue("게시글 저장 중…");
                    document.set(post).addOnCompleteListener(saveTask -> {
                        if (saveTask.isSuccessful()) {
                            busy = false;
                            completed.setValue(true);
                        } else {
                            // 서버의 실제 저장 결과가 불확실할 수 있으므로 미디어는 유지합니다.
                            fail(saveTask.getException());
                        }
                    });
                });
    }

    private void fail(Exception error) {
        busy = false;
        Log.e("PostUpload", "Post upload failed", error);
        status.setValue("등록에 실패했습니다. 연결 상태와 업로드 권한을 확인한 뒤 다시 시도해주세요.");
    }

    @Override
    protected void onCleared() {
        cleared = true;
        File pendingDelete = previewFile;
        if (pendingDelete != null) executor.execute(pendingDelete::delete);
        executor.shutdown();
    }
}
