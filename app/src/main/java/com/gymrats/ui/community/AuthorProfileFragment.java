package com.gymrats.ui.community;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.gymrats.R;

import java.util.Locale;

public class AuthorProfileFragment extends Fragment {

    private static final String TAG = "AuthorProfileFragment";

    public AuthorProfileFragment() {
        super(R.layout.fragment_author_profile);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        // 이전 화면으로 복귀
        view.findViewById(R.id.btnBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack()
        );

        String userId = getArguments() == null
                ? null
                : getArguments().getString("userId");

        if (userId == null || userId.isEmpty()) {
            TextView nickname =
                    view.findViewById(R.id.textAuthorNickname);
            nickname.setText("회원 정보 없음");

            TextView message =
                    view.findViewById(R.id.textAuthorPostsMessage);
            message.setText("회원 정보를 확인할 수 없습니다.");
            return;
        }

        loadProfile(view, userId);
        loadPosts(view, userId);
    }

    private void loadProfile(View view, String userId) {
        // 비공개 users 문서 대신 공개 프로필만 조회
        FirebaseFirestore.getInstance()
                .collection("publicProfiles")
                .document(userId)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    TextView nicknameView =
                            view.findViewById(R.id.textAuthorNickname);
                    TextView countsView =
                            view.findViewById(R.id.textAuthorFollowCounts);

                    if (!task.isSuccessful()) {
                        Log.e(TAG, "프로필 조회 실패", task.getException());
                        nicknameView.setText("프로필 조회 실패");
                        return;
                    }

                    DocumentSnapshot profile = task.getResult();

                    if (profile == null || !profile.exists()) {
                        nicknameView.setText("닉네임 미설정");
                        countsView.setText("공개 프로필이 아직 없습니다.");
                        return;
                    }

                    String nickname = profile.getString("nickname");
                    Long followers = profile.getLong("followerCount");
                    Long following = profile.getLong("followingCount");

                    nicknameView.setText(
                            nickname == null || nickname.trim().isEmpty()
                                    ? "닉네임 미설정"
                                    : nickname
                    );

                    countsView.setText(String.format(
                            Locale.getDefault(),
                            "팔로워 %d · 팔로잉 %d",
                            followers == null ? 0L : followers,
                            following == null ? 0L : following
                    ));
                });
    }

    private void loadPosts(View view, String userId) {
        RecyclerView recycler =
                view.findViewById(R.id.recyclerAuthorPosts);
        TextView message =
                view.findViewById(R.id.textAuthorPostsMessage);

        // 프로필의 게시글 카드에서도 상세 화면 이동
        PostAdapter adapter = new PostAdapter(postId -> {
            Bundle arguments = new Bundle();
            arguments.putString(PostDetailFragment.ARG_POST_ID, postId);

            NavHostFragment.findNavController(this)
                    .navigate(R.id.postDetailFragment, arguments);
        });

        recycler.setLayoutManager(
                new GridLayoutManager(requireContext(), 2)
        );
        recycler.setAdapter(adapter);

        // 작성자 UID와 공개 상태로 필터링
        FirebaseFirestore.getInstance()
                .collection("posts")
                .whereEqualTo("authorId", userId)
                .whereEqualTo("status", "published")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded() || getView() != view) {
                        return;
                    }

                    if (!task.isSuccessful()) {
                        Log.e(TAG, "작성자 게시글 조회 실패",
                                task.getException());
                        message.setText("게시글을 불러오지 못했습니다.");
                        return;
                    }

                    if (task.getResult() == null) {
                        message.setText("게시글을 불러오지 못했습니다.");
                        return;
                    }

                    adapter.submitPosts(
                            task.getResult().getDocuments()
                    );

                    message.setText("공개된 게시글이 없습니다.");
                    message.setVisibility(
                            task.getResult().isEmpty()
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                });
    }
}