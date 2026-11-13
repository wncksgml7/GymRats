package com.gymrats.ui.community;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gymrats.R;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PostDetailFragment extends Fragment {

    public static final String ARG_POST_ID = "postId";
    private static final String TAG = "PostDetailFragment";

    public PostDetailFragment() {
        super(R.layout.fragment_post_detail);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

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

        setText(
                view,
                R.id.textDetailStats,
                String.format(
                        Locale.getDefault(),
                        "좋아요 %d · ★ %.1f · 후기 %d",
                        likeCount == null ? 0L : likeCount,
                        ratingAverage == null ? 0.0 : ratingAverage,
                        reviewCount == null ? 0L : reviewCount
                )
        );

        view.findViewById(R.id.progressPostDetail)
                .setVisibility(View.GONE);
        view.findViewById(R.id.textDetailMessage)
                .setVisibility(View.GONE);
        view.findViewById(R.id.scrollPostDetail)
                .setVisibility(View.VISIBLE);
        // 게시글 작성자의 공개 정보 조회
        loadAuthor(view, post.getString("authorId"));
    }

    private void showError(View view, String message) {
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