package com.gymrats.ui.profile;

import android.net.Uri;

import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.List;
import java.util.Locale;

// 내 정보 카드와 내가 만든 운동법 목록이 같이 쓰는 조회·통계·이동 기능
final class MyPostsHelper {

    static final int LIMIT = 50;

    private MyPostsHelper() {
    }

    // 작성자 프로필과 같은 조건이라 이미 만들어진 복합 색인(authorId + status + createdAt)을 그대로 씀
    static Query query(FirebaseFirestore db, String uid) {
        return db.collection("posts")
                .whereEqualTo("authorId", uid)
                .whereEqualTo("status", "published")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(LIMIT);
    }

    // 운동법 수, 받은 좋아요 합계, 전체 평균 별점(별점 합계 ÷ 후기 수)
    static String summary(List<DocumentSnapshot> posts) {
        long likes = 0;
        long reviews = 0;
        double ratingSum = 0;
        for (DocumentSnapshot post : posts) {
            likes += count(post, "likeCount");
            reviews += count(post, "reviewCount");
            Double sum = post.getDouble("ratingSum");
            ratingSum += sum == null ? 0 : sum;
        }
        String rating = reviews == 0 ? "-" : String.format(Locale.KOREA, "%.1f", ratingSum / reviews);
        String text = String.format(Locale.KOREA, "운동법 %d개 · 받은 좋아요 %d · 평균 별점 %s",
                posts.size(), likes, rating);
        return posts.size() >= LIMIT ? text + " (최근 " + LIMIT + "개 기준)" : text;
    }

    // 게시글 상세는 커뮤니티 그룹 안에 있어서 ID 대신 딥링크 주소로 이동
    static void openPost(Fragment fragment, String postId) {
        NavHostFragment.findNavController(fragment)
                .navigate(Uri.parse("gymrats://post/" + Uri.encode(postId)));
    }

    private static long count(DocumentSnapshot doc, String field) {
        Long value = doc.getLong(field);
        return value == null ? 0 : value;
    }
}