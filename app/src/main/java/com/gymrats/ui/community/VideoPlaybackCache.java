package com.gymrats.ui.community;

import android.app.Application;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 시간 정보가 잘못된 기존 게시글 영상만 내려받아 화면 수명 동안 보완합니다. */
public class VideoPlaybackCache extends AndroidViewModel {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Task<Uri> pending;
    private FileDownloadTask download;
    private File sourceFile;
    private File playableFile;
    private boolean cleared;

    public VideoPlaybackCache(@NonNull Application application) { super(application); }

    Task<Uri> prepare(String storagePath) {
        if (pending != null && (!pending.isComplete() || pending.isSuccessful())) return pending;
        cleanup();
        try {
            sourceFile = File.createTempFile("post-playback-", ".mp4", getApplication().getCacheDir());
        } catch (Exception error) {
            return Tasks.forException(error);
        }
        File source = sourceFile;
        download = FirebaseStorage.getInstance().getReference().child(storagePath).getFile(source);
        pending = download.onSuccessTask(snapshot -> Tasks.call(executor, () -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(source.getAbsolutePath());
                File repaired = VideoPreviewFile.createIfNeeded(getApplication(), Uri.fromFile(source), retriever);
                playableFile = repaired == null ? source : repaired;
                return Uri.fromFile(playableFile);
            } finally {
                retriever.release();
            }
        }));
        pending.addOnCompleteListener(task -> {
            if (!task.isSuccessful() || cleared) cleanup();
            if (cleared) executor.shutdown();
        });
        return pending;
    }

    private void cleanup() {
        if (sourceFile != null) sourceFile.delete();
        if (playableFile != null) playableFile.delete();
        sourceFile = null;
        playableFile = null;
    }

    @Override protected void onCleared() {
        cleared = true;
        if (download != null && !download.isComplete()) download.cancel();
        if (pending == null || pending.isComplete()) {
            cleanup();
            executor.shutdown();
        }
    }
}
