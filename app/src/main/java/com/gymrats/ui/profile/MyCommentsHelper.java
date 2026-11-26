package com.gymrats.ui.profile;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 내가 쓴 후기·댓글: 모든 게시글 아래의 reviews / comments를 한 번에 조회 (컬렉션 그룹)
final class MyCommentsHelper {

    static final int LIMIT = 50;

    private MyCommentsHelper() {
    }

    // 콘솔에 보안 규칙(match /{path=**}/reviews 등)과 색인(authorId + createdAt, 컬렉션 그룹)이 있어야 동작
    static Query entries(FirebaseFirestore db, String kind, String uid) {
        return db.collectionGroup(kind)
                .whereEqualTo("authorId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING);
    }

    // 후기 수 + 댓글 수. 목록과 같은 조건이라 같은 색인을 씀
    static Task<Long> countAll(FirebaseFirestore db, String uid) {
        Task<AggregateQuerySnapshot> reviews = entries(db, "reviews", uid).count().get(AggregateSource.SERVER);
        Task<AggregateQuerySnapshot> comments = entries(db, "comments", uid).count().get(AggregateSource.SERVER);
        return Tasks.<AggregateQuerySnapshot>whenAllSuccess(reviews, comments).continueWith(task -> {
            long total = 0;
            for (AggregateQuerySnapshot result : task.getResult()) total += result.getCount();
            return total;
        });
    }

    // 후기와 댓글을 각각 최근 50개씩 가져와 최신순으로 합친 뒤 50개만 남김
    static Task<List<DocumentSnapshot>> loadRecent(FirebaseFirestore db, String uid) {
        Task<QuerySnapshot> reviews = entries(db, "reviews", uid).limit(LIMIT).get();
        Task<QuerySnapshot> comments = entries(db, "comments", uid).limit(LIMIT).get();
        return Tasks.<QuerySnapshot>whenAllSuccess(reviews, comments).continueWith(task -> {
            List<DocumentSnapshot> all = new ArrayList<>();
            for (QuerySnapshot snapshot : task.getResult()) all.addAll(snapshot.getDocuments());
            all.sort(MyCommentsHelper::newestFirst);
            return all.size() > LIMIT ? new ArrayList<>(all.subList(0, LIMIT)) : all;
        });
    }

    private static int newestFirst(DocumentSnapshot a, DocumentSnapshot b) {
        Timestamp ta = a.getTimestamp("createdAt");
        Timestamp tb = b.getTimestamp("createdAt");
        if (ta == null || tb == null) return ta == null ? (tb == null ? 0 : 1) : -1;
        return tb.compareTo(ta);
    }

    static boolean isReview(DocumentSnapshot entry) {
        return "reviews".equals(entry.getReference().getParent().getId());
    }

    // 경로가 posts/{postId}/reviews/{id} 이므로 두 단계 위가 게시글
    static String postId(DocumentSnapshot entry) {
        return entry.getReference().getParent().getParent().getId();
    }

    // 후기·댓글 문서에는 게시글 제목이 없어서 게시글을 따로 읽음 (같은 게시글은 한 번만)
    static Task<Map<String, String>> loadPostTitles(FirebaseFirestore db, List<DocumentSnapshot> entries) {
        Set<String> ids = new LinkedHashSet<>();
        for (DocumentSnapshot entry : entries) ids.add(postId(entry));
        List<Task<DocumentSnapshot>> reads = new ArrayList<>();
        for (String id : ids) reads.add(db.collection("posts").document(id).get());
        return Tasks.whenAllComplete(reads).continueWith(done -> {
            Map<String, String> titles = new HashMap<>();
            for (Task<DocumentSnapshot> read : reads) {
                // 지워졌거나 볼 수 없는 게시글은 건너뜀
                if (read.isSuccessful() && read.getResult().exists()) {
                    titles.put(read.getResult().getId(), read.getResult().getString("title"));
                }
            }
            return titles;
        });
    }

    static String summary(List<DocumentSnapshot> entries) {
        int reviews = 0;
        for (DocumentSnapshot entry : entries) if (isReview(entry)) reviews++;
        String text = "후기 " + reviews + "개 · 댓글 " + (entries.size() - reviews) + "개";
        return entries.size() >= LIMIT ? text + " (최근 " + LIMIT + "개 기준)" : text;
    }
}