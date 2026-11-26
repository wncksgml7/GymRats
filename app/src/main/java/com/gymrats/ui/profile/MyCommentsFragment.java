package com.gymrats.ui.profile;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gymrats.R;

import java.util.List;

// 내가 쓴 후기·댓글 목록 (내 정보 통계의 "댓글" 칸)
public class MyCommentsFragment extends Fragment {

    private MyCommentAdapter adapter;
    private int loadRequest;

    public MyCommentsFragment() {
        super(R.layout.fragment_my_comments);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btnBackMyComments).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.btnRetryMyComments).setOnClickListener(v -> load());

        adapter = new MyCommentAdapter(postId -> MyPostsHelper.openPost(this, postId));
        RecyclerView recycler = view.findViewById(R.id.rvMyComments);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        View view = getView();
        if (view == null) return;
        int request = ++loadRequest;
        if (adapter.getItemCount() == 0) showState(view, "후기·댓글 불러오는 중…", true, false);

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            showState(view, "회원 정보를 찾을 수 없습니다. 다시 로그인해 주세요.", false, false);
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        MyCommentsHelper.loadRecent(db, uid).addOnCompleteListener(task -> {
            if (getView() != view || request != loadRequest) return;
            if (!task.isSuccessful()) {
                // 색인이 없으면 이 오류 메시지 안에 색인 만들기 링크가 있음 (Logcat에서 "MyComments" 검색)
                Log.w("MyComments", "후기·댓글을 불러오지 못했습니다", task.getException());
                showState(view, "후기·댓글을 불러오지 못했습니다.", false, true);
                return;
            }
            List<DocumentSnapshot> entries = task.getResult();
            adapter.submitEntries(entries);
            ((TextView) view.findViewById(R.id.tvMyCommentsSummary)).setText(MyCommentsHelper.summary(entries));
            if (entries.isEmpty()) {
                showState(view, "아직 작성한 후기·댓글이 없습니다.", false, false);
            } else {
                view.findViewById(R.id.myCommentsState).setVisibility(View.GONE);
            }

            MyCommentsHelper.loadPostTitles(db, entries).addOnSuccessListener(titles -> {
                if (getView() != view || request != loadRequest) return;
                adapter.setPostTitles(titles);
            });
        });
    }

    private void showState(View view, String text, boolean loading, boolean retry) {
        view.findViewById(R.id.myCommentsState).setVisibility(View.VISIBLE);
        view.findViewById(R.id.progressMyComments).setVisibility(loading ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btnRetryMyComments).setVisibility(retry ? View.VISIBLE : View.GONE);
        ((TextView) view.findViewById(R.id.textMyCommentsState)).setText(text);
    }

    @Override
    public void onDestroyView() {
        loadRequest++;
        ((RecyclerView) requireView().findViewById(R.id.rvMyComments)).setAdapter(null);
        adapter = null;
        super.onDestroyView();
    }
}