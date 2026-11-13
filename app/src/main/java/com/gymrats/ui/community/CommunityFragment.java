package com.gymrats.ui.community;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.gymrats.R;

public class CommunityFragment extends Fragment {

    private static final String TAG = "CommunityFragment";

    private PostAdapter adapter;
    private ProgressBar progressBar;
    private TextView messageView;
    private ListenerRegistration postsListener;

    public CommunityFragment() {
        super(R.layout.fragment_community);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        progressBar = view.findViewById(R.id.progressPosts);
        messageView = view.findViewById(R.id.textPostsMessage);

        // 게시글을 2열 카드 목록으로 표시
        RecyclerView recyclerView =
                view.findViewById(R.id.recyclerPosts);
        // 카드 클릭 시 게시글 ID와 함께 상세 화면으로 이동
        adapter = new PostAdapter(postId -> {
            Bundle arguments = new Bundle();
            arguments.putString(
                    PostDetailFragment.ARG_POST_ID,
                    postId
            );

            NavHostFragment.findNavController(this)
                    .navigate(R.id.postDetailFragment, arguments);
        });
        recyclerView.setLayoutManager(
                new GridLayoutManager(requireContext(), 2)
        );
        recyclerView.setAdapter(adapter);

        // 기존 게시글 작성 화면 이동 유지
        view.findViewById(R.id.btnCreatePost).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.postCreateFragment)
        );
    }

    @Override
    public void onStart() {
        super.onStart();

        progressBar.setVisibility(View.VISIBLE);
        messageView.setVisibility(View.GONE);

        // 공개된 게시글을 최신순으로 최대 50개 조회
        postsListener = FirebaseFirestore.getInstance()
                .collection("posts")
                .whereEqualTo("status", "published")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((snapshot, error) -> {
                    if (getView() == null || adapter == null) {
                        return;
                    }

                    progressBar.setVisibility(View.GONE);

                    // 조회 실패와 빈 목록을 구분
                    if (error != null) {
                        Log.e(TAG, "게시글 목록 조회 실패", error);

                        messageView.setText(
                                "게시글을 불러오지 못했습니다."
                        );
                        messageView.setVisibility(View.VISIBLE);
                        return;
                    }

                    if (snapshot == null) {
                        messageView.setText(
                                "게시글을 불러오지 못했습니다."
                        );
                        messageView.setVisibility(View.VISIBLE);
                        return;
                    }

                    adapter.submitPosts(snapshot.getDocuments());

                    messageView.setText(
                            "아직 등록된 운동법이 없습니다."
                    );
                    messageView.setVisibility(
                            snapshot.isEmpty()
                                    ? View.VISIBLE
                                    : View.GONE
                    );
                });
    }

    @Override
    public void onStop() {
        // 화면을 떠나면 실시간 구독 해제
        if (postsListener != null) {
            postsListener.remove();
            postsListener = null;
        }

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        adapter = null;
        progressBar = null;
        messageView = null;
    }
}