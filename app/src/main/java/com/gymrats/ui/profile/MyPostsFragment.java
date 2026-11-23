package com.gymrats.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gymrats.R;
import com.gymrats.ui.community.PostAdapter;

import java.util.List;

// 내가 만든 운동법 전체 목록 (내 정보 카드의 "전체보기")
public class MyPostsFragment extends Fragment {

    private PostAdapter adapter;
    private int loadRequest;

    public MyPostsFragment() {
        super(R.layout.fragment_my_posts);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnBackMyPosts).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btnRetryMyPosts).setOnClickListener(v -> load());

        adapter = new PostAdapter(postId -> MyPostsHelper.openPost(this, postId));
        RecyclerView recycler = view.findViewById(R.id.rvAllMyPosts);
        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recycler.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 게시글 상세에서 돌아왔을 때 좋아요·후기 수가 바뀌었을 수 있어 다시 읽음
        load();
    }

    private void load() {
        View view = getView();
        if (view == null) return;
        int request = ++loadRequest;
        if (adapter.getItemCount() == 0) showState(view, "운동법 불러오는 중…", true, false);

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            showState(view, "회원 정보를 찾을 수 없습니다. 다시 로그인해 주세요.", false, false);
            return;
        }

        MyPostsHelper.query(FirebaseFirestore.getInstance(), uid).get().addOnCompleteListener(task -> {
            // 화면이 없어졌거나 더 새 요청이 있으면 이 결과는 버림
            if (getView() != view || request != loadRequest) return;
            if (!task.isSuccessful()) {
                showState(view, "운동법을 불러오지 못했습니다.", false, true);
                return;
            }
            List<DocumentSnapshot> posts = task.getResult().getDocuments();
            adapter.submitPosts(posts);
            ((TextView) view.findViewById(R.id.tvMyPostsSummary)).setText(MyPostsHelper.summary(posts));
            if (posts.isEmpty()) {
                showState(view, "아직 작성한 운동법이 없습니다.", false, false);
            } else {
                view.findViewById(R.id.myPostsState).setVisibility(View.GONE);
            }
        });
    }

    private void showState(View view, String text, boolean loading, boolean retry) {
        view.findViewById(R.id.myPostsState).setVisibility(View.VISIBLE);
        view.findViewById(R.id.progressMyPosts).setVisibility(loading ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btnRetryMyPosts).setVisibility(retry ? View.VISIBLE : View.GONE);
        ((TextView) view.findViewById(R.id.textMyPostsState)).setText(text);
    }

    @Override
    public void onDestroyView() {
        loadRequest++;
        ((RecyclerView) requireView().findViewById(R.id.rvAllMyPosts)).setAdapter(null);
        adapter = null;
        super.onDestroyView();
    }
}