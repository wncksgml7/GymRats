package com.gymrats.ui.profile;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.gymrats.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// 내가 쓴 후기·댓글 한 줄: 종류(별점), 내용, 작성일, 어느 게시글에 썼는지
class MyCommentAdapter extends RecyclerView.Adapter<MyCommentAdapter.Holder> {

    interface OnEntryClickListener {
        void onEntryClick(String postId);
    }

    private final List<DocumentSnapshot> entries = new ArrayList<>();
    private final Map<String, String> postTitles = new HashMap<>();
    private boolean titlesLoaded;
    private final OnEntryClickListener clickListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA);

    MyCommentAdapter(OnEntryClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @SuppressLint("NotifyDataSetChanged")
    void submitEntries(List<DocumentSnapshot> list) {
        entries.clear();
        entries.addAll(list);
        postTitles.clear();
        titlesLoaded = false;
        notifyDataSetChanged();
    }

    // 게시글 제목은 목록을 먼저 보여 준 뒤 따로 불러와서 채움
    @SuppressLint("NotifyDataSetChanged")
    void setPostTitles(Map<String, String> titles) {
        postTitles.clear();
        postTitles.putAll(titles);
        titlesLoaded = true;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_comment, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        DocumentSnapshot entry = entries.get(position);
        String postId = MyCommentsHelper.postId(entry);

        if (MyCommentsHelper.isReview(entry)) {
            Long rating = entry.getLong("rating");
            h.kind.setText("후기 ★" + (rating == null ? 0 : rating));
        } else {
            h.kind.setText("댓글");
        }

        String content = entry.getString("content");
        h.content.setText(content == null ? "" : content);

        Timestamp createdAt = entry.getTimestamp("createdAt");
        h.date.setText(createdAt == null ? "" : dateFormat.format(createdAt.toDate()));

        String title = postTitles.get(postId);
        if (title != null) {
            h.post.setText("게시글 · " + title);
        } else {
            h.post.setText(titlesLoaded ? "삭제되었거나 볼 수 없는 게시글" : "게시글 불러오는 중…");
        }

        h.itemView.setOnClickListener(v -> clickListener.onEntryClick(postId));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView kind, date, content, post;

        Holder(@NonNull View itemView) {
            super(itemView);
            kind = itemView.findViewById(R.id.textEntryKind);
            date = itemView.findViewById(R.id.textEntryDate);
            content = itemView.findViewById(R.id.textEntryContent);
            post = itemView.findViewById(R.id.textEntryPost);
        }
    }
}