package com.gymrats.ui.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.gymrats.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PostAdapter
        extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private final List<DocumentSnapshot> posts = new ArrayList<>();

    // 조회 결과로 목록 갱신
    public void submitPosts(List<DocumentSnapshot> documents) {
        posts.clear();
        posts.addAll(documents);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);

        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull PostViewHolder holder,
            int position
    ) {
        DocumentSnapshot post = posts.get(position);

        // 제목과 운동 부위 표시
        String title = post.getString("title");
        String bodyPartId = post.getString("primaryBodyPartId");

        holder.title.setText(title == null ? "" : title);
        holder.bodyPart.setText(getBodyPartName(bodyPartId));

        // 집계값이 없는 기존 문서도 기본값으로 표시
        Long likeCount = post.getLong("likeCount");
        Long reviewCount = post.getLong("reviewCount");
        Double ratingAverage = post.getDouble("ratingAverage");

        holder.stats.setText(String.format(
                Locale.getDefault(),
                "좋아요 %d · ★ %.1f (%d)",
                likeCount == null ? 0L : likeCount,
                ratingAverage == null ? 0.0 : ratingAverage,
                reviewCount == null ? 0L : reviewCount
        ));
        // 현재 카드가 표시하는 게시글 열기
        holder.itemView.setOnClickListener(v ->
                clickListener.onPostClick(post.getId())
        );
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    // DB의 부위 ID를 화면 표시 이름으로 변환
    private String getBodyPartName(String id) {
        if (id == null) {
            return "운동 부위 미지정";
        }

        switch (id) {
            case "chest":
                return "가슴";
            case "back":
                return "등";
            case "legs":
                return "하체";
            case "shoulders":
                return "어깨";
            case "arms":
                return "팔";
            case "abs":
                return "복근";
            default:
                return id;
        }
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {

        final TextView title;
        final TextView bodyPart;
        final TextView stats;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);

            title = itemView.findViewById(R.id.textPostTitle);
            bodyPart = itemView.findViewById(R.id.textPostBodyPart);
            stats = itemView.findViewById(R.id.textPostStats);
        }
    }
    // 클릭한 게시글 ID를 Fragment에 전달
    public interface OnPostClickListener {
        void onPostClick(String postId);
    }

    private final OnPostClickListener clickListener;

    public PostAdapter(OnPostClickListener clickListener) {
        this.clickListener = clickListener;
    }

}