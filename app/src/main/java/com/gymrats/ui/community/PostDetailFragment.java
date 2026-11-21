package com.gymrats.ui.community;

import android.os.Bundle;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.gymrats.R;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public class PostDetailFragment extends Fragment {

    public static final String ARG_POST_ID = "postId";
    private static final String TAG = "PostDetailFragment";
    private ExoPlayer player;
    private PlayerView playerView;
    private String videoPath;
    private Uri videoUri;
    private long playbackPosition;
    private boolean playWhenReady;
    private boolean started;
    private boolean resolvingVideo;
    private int videoRequest;
    private boolean postLoaded;
    private VideoPlaybackCache playbackCache;
    private PostDiscussion discussion;

    public PostDetailFragment() {
        super(R.layout.fragment_post_detail);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        postLoaded = false;
        playbackCache = new ViewModelProvider(this).get(VideoPlaybackCache.class);
        playerView = view.findViewById(R.id.postVideoPlayer);
        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong("videoPosition", 0);
            playWhenReady = savedInstanceState.getBoolean("videoPlaying", false);
            videoPath = savedInstanceState.getString("videoPath");
        }
        view.findViewById(R.id.btnRetryVideo).setOnClickListener(v -> {
            releasePlayer();
            videoUri = null;
            resolveVideo(view);
        });

        // 이전 피드 화면으로 복귀
        view.findViewById(R.id.btnBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .popBackStack()
        );

        // 목록에서 전달받은 게시글 ID 확인
        Bundle arguments = getArguments();
        String postId = arguments == null
                ? null
                : arguments.getString(ARG_POST_ID);

        if (postId == null || postId.trim().isEmpty()) {
            showError(view, "게시글 정보를 확인할 수 없습니다.");
            return;
        }

        discussion = new PostDiscussion(this, view, postId);
        loadPost(view, postId);
    }

    private void loadPost(View view, String postId) {
        // 선택한 게시글 문서 하나 조회
        FirebaseFirestore.getInstance()
                .collection("posts")
                .document(postId)
                .get()
                .addOnCompleteListener(task -> {
                    // 화면을 떠났거나 새로 생성된 경우 이전 View 접근 방지
                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    if (!task.isSuccessful()) {
                        Log.e(
                                TAG,
                                "게시글 상세 조회 실패",
                                task.getException()
                        );

                        showError(
                                view,
                                "게시글을 불러올 수 없습니다. 삭제되었거나 접근할 수 없는 글일 수 있습니다."
                        );
                        return;
                    }

                    DocumentSnapshot post = task.getResult();

                    if (post == null || !post.exists()) {
                        showError(view, "존재하지 않는 게시글입니다.");
                        return;
                    }

                    showPost(view, post);
                });
    }

    private void showPost(View view, DocumentSnapshot post) {
        postLoaded = true;
        // 제목, 운동 설명과 주의사항 표시
        setText(view, R.id.textDetailTitle,
                getText(post, "title"));

        setText(view, R.id.textDetailDescription,
                getText(post, "description"));

        String precaution = getText(post, "precaution");

        setText(
                view,
                R.id.textDetailPrecaution,
                precaution.trim().isEmpty()
                        ? "등록된 주의사항이 없습니다."
                        : precaution
        );

        // DB의 분류 ID를 한글 표시 이름으로 변환
        String primaryId = post.getString("primaryBodyPartId");

        setText(
                view,
                R.id.textDetailPrimaryBodyPart,
                "주 운동 부위: "
                        + (primaryId == null ? "미지정" : getLabel(primaryId))
        );

        setText(
                view,
                R.id.textDetailSecondaryBodyParts,
                "보조 운동 부위: "
                        + getLabels(post.get("secondaryBodyPartId"))
        );

        setText(
                view,
                R.id.textDetailEquipment,
                "필요 기구: " + getLabels(post.get("equipmentIds"))
        );

        // 좋아요와 별점 집계 표시
        Long likeCount = post.getLong("likeCount");
        Long reviewCount = post.getLong("reviewCount");
        Double ratingAverage = post.getDouble("ratingAverage");

        Long commentCount = post.getLong("commentCount");
        long likes = likeCount == null ? 0L : likeCount;
        long comments = commentCount == null ? 0L : commentCount;
        double rating = ratingAverage == null ? 0.0 : ratingAverage;
        setText(view, R.id.textDetailLikes, String.valueOf(likes));
        setText(view, R.id.textDetailComments, String.valueOf(comments));
        setText(view, R.id.textDetailRating, String.format(Locale.getDefault(), "%.1f (%d)",
                rating, reviewCount == null ? 0L : reviewCount));
        view.findViewById(R.id.textDetailLikes).setContentDescription(getString(R.string.post_likes_description, likes));
        view.findViewById(R.id.textDetailComments).setContentDescription(getString(R.string.post_comments_description, comments));
        view.findViewById(R.id.textDetailRating).setContentDescription(getString(
                R.string.post_rating_description, rating, reviewCount == null ? 0L : reviewCount));

        view.findViewById(R.id.progressPostDetail)
                .setVisibility(View.GONE);
        view.findViewById(R.id.textDetailMessage)
                .setVisibility(View.GONE);
        view.findViewById(R.id.scrollPostDetail)
                .setVisibility(View.VISIBLE);
        // 게시글 작성자의 공개 정보 조회
        loadAuthor(view, post.getString("authorId"));
        String path = post.getString("videoPath");
        if (videoPath != null && !videoPath.equals(path)) {
            releasePlayer();
            videoUri = null;
            playbackPosition = 0;
            playWhenReady = false;
        }
        videoPath = path;
        if (path == null || path.isEmpty()) {
            playerView.setVisibility(View.GONE);
            setVideoStatus(view, R.string.post_video_missing, false);
        } else {
            playerView.setVisibility(View.VISIBLE);
            resolveVideo(view);
        }
    }

    private void resolveVideo(View view) {
        if (videoPath == null || videoPath.isEmpty() || resolvingVideo) return;
        if (videoUri != null) {
            initializePlayer();
            return;
        }
        resolvingVideo = true;
        int request = ++videoRequest;
        setVideoStatus(view, R.string.post_video_loading, false);
        FirebaseStorage.getInstance().getReference().child(videoPath).getDownloadUrl()
                .addOnCompleteListener(task -> {
                    if (request != videoRequest || getView() != view) return;
                    resolvingVideo = false;
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "동영상 주소 조회 실패", task.getException());
                        setVideoStatus(view, R.string.post_video_failed, true);
                        return;
                    }
                    videoUri = task.getResult();
                    initializePlayer();
                });
    }

    private void initializePlayer() {
        View view = getView();
        if (!started || !postLoaded || view == null || playerView == null || videoUri == null || player != null) return;
        ExoPlayer current = new ExoPlayer.Builder(requireContext().getApplicationContext()).build();
        player = current;
        current.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        current.setHandleAudioBecomingNoisy(true);
        playerView.setPlayer(current);
        current.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (player != current || getView() != view) return;
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                    if (current.getDuration() <= 0) {
                        if (videoUri != null && !"file".equals(videoUri.getScheme())) {
                            prepareLocalPlayback(view);
                        } else {
                            setVideoStatus(view, R.string.post_video_failed, true);
                        }
                        return;
                    }
                    view.findViewById(R.id.textVideoPlaybackStatus).setVisibility(View.GONE);
                    view.findViewById(R.id.btnRetryVideo).setVisibility(View.GONE);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                if (player == current && playerView != null) playerView.setKeepScreenOn(playing);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (player != current || getView() != view) return;
                Log.w(TAG, "동영상 재생 실패", error);
                setVideoStatus(view, R.string.post_video_failed, true);
            }
        });
        setVideoStatus(view, R.string.post_video_loading, false);
        current.setMediaItem(MediaItem.fromUri(videoUri));
        current.seekTo(playbackPosition);
        current.setPlayWhenReady(playWhenReady);
        current.prepare();
        playerView.showController();
    }

    private void prepareLocalPlayback(View view) {
        releasePlayer();
        videoUri = null;
        resolvingVideo = true;
        int request = ++videoRequest;
        setVideoStatus(view, R.string.post_video_preparing, false);
        playbackCache.prepare(videoPath).addOnCompleteListener(task -> {
            if (request != videoRequest || getView() != view) return;
            resolvingVideo = false;
            if (!task.isSuccessful()) {
                Log.w(TAG, "기존 영상 재생 준비 실패", task.getException());
                setVideoStatus(view, R.string.post_video_failed, true);
                return;
            }
            videoUri = task.getResult();
            initializePlayer();
        });
    }

    private void setVideoStatus(View view, int message, boolean retry) {
        TextView status = view.findViewById(R.id.textVideoPlaybackStatus);
        status.setText(message);
        status.setVisibility(View.VISIBLE);
        view.findViewById(R.id.btnRetryVideo).setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private void releasePlayer() {
        if (player == null) return;
        playbackPosition = player.getCurrentPosition();
        playWhenReady = player.getPlayWhenReady();
        if (playerView != null) {
            playerView.setKeepScreenOn(false);
            playerView.setPlayer(null);
        }
        ExoPlayer previous = player;
        player = null;
        previous.release();
    }

    @Override
    public void onStart() {
        super.onStart();
        started = true;
        if (discussion != null) discussion.start();
        initializePlayer();
    }

    @Override
    public void onStop() {
        started = false;
        if (discussion != null) discussion.stop();
        releasePlayer();
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putLong("videoPosition", player == null ? playbackPosition : player.getCurrentPosition());
        state.putBoolean("videoPlaying", player == null ? playWhenReady : player.getPlayWhenReady());
        state.putString("videoPath", videoPath);
    }

    @Override
    public void onDestroyView() {
        if (discussion != null) discussion.stop();
        discussion = null;
        videoRequest++;
        resolvingVideo = false;
        releasePlayer();
        playerView = null;
        super.onDestroyView();
    }

    private void showError(View view, String message) {
        postLoaded = false;
        releasePlayer();
        // 로딩을 끝내고 실패 안내 표시
        view.findViewById(R.id.progressPostDetail)
                .setVisibility(View.GONE);
        view.findViewById(R.id.scrollPostDetail)
                .setVisibility(View.GONE);

        TextView messageView =
                view.findViewById(R.id.textDetailMessage);

        messageView.setText(message);
        messageView.setVisibility(View.VISIBLE);
    }

    private void setText(View view, int id, String value) {
        TextView textView = view.findViewById(id);
        textView.setText(value);
    }

    private String getText(DocumentSnapshot post, String field) {
        String value = post.getString(field);
        return value == null ? "" : value;
    }

    private String getLabels(Object value) {
        // 배열 필드를 읽어 표시 이름 목록으로 구성
        if (!(value instanceof List<?>)) {
            return "없음";
        }

        ArrayList<String> labels = new ArrayList<>();

        for (Object item : (List<?>) value) {
            if (item instanceof String) {
                labels.add(getLabel((String) item));
            }
        }

        return labels.isEmpty()
                ? "없음"
                : TextUtils.join(", ", labels);
    }

    private String getLabel(String id) {
        switch (id) {
            case "chest": return "가슴";
            case "back": return "등";
            case "legs": return "하체";
            case "shoulders": return "어깨";
            case "arms": return "팔";
            case "abs": return "복근";
            case "barbell": return "바벨";
            case "dumbbell": return "덤벨";
            case "cable": return "케이블";
            case "machine": return "머신";
            case "bodyweight": return "맨몸";
            case "band": return "밴드";
            case "kettlebell": return "케틀벨";
            default: return id;
        }
    }
    private void loadAuthor(View view, String authorId) {
        Button authorButton = view.findViewById(R.id.btnPostAuthor);

        if (authorId == null || authorId.isEmpty()) {
            authorButton.setText("작성자 정보 없음");
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("publicProfiles")
                .document(authorId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    if (!task.isSuccessful()) {
                        Log.e(TAG, "작성자 조회 실패", task.getException());
                        authorButton.setText("작성자 정보 조회 실패");
                        return;
                    }

                    DocumentSnapshot profile = task.getResult();
                    String nickname = profile == null
                            ? null
                            : profile.getString("nickname");

                    authorButton.setText(
                            nickname == null || nickname.trim().isEmpty()
                                    ? "닉네임 미설정 · 프로필 보기"
                                    : nickname + " · 프로필 보기"
                    );

                    authorButton.setEnabled(true);

                    // 공개 프로필 화면에 작성자 UID 전달
                    authorButton.setOnClickListener(v -> {
                        Bundle arguments = new Bundle();
                        arguments.putString("userId", authorId);

                        NavHostFragment.findNavController(this)
                                .navigate(
                                        R.id.authorProfileFragment,
                                        arguments
                                );
                    });
                });
    }
}
