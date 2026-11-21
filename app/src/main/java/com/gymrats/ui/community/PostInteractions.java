package com.gymrats.ui.community;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 반응 문서와 집계값을 한 트랜잭션으로 저장합니다. */
public final class PostInteractions {
    private final FirebaseFirestore db;
    private final String uid;

    public PostInteractions() {
        this(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance().getUid());
    }

    PostInteractions(FirebaseFirestore db, String uid) {
        this.db = db;
        this.uid = uid;
    }

    public DocumentReference post(String id) { return db.collection("posts").document(id); }
    public DocumentReference like(String id) { return post(id).collection("likes").document(uid); }
    public DocumentReference bookmark(String id) {
        return db.collection("users").document(uid).collection("bookmarks").document(id);
    }
    public boolean signedIn() { return uid != null; }
    DocumentReference profile(String uid) { return db.collection("publicProfiles").document(uid); }

    public Task<Void> setLiked(String postId, boolean selected) {
        if (!signedIn()) return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        DocumentReference post = post(postId), like = like(postId);
        return countedTransaction(post, (tx, p) -> {
            DocumentSnapshot current = tx.get(like);
            requirePublished(p);
            if (current.exists() == selected) return null;
            if (selected) {
                Map<String, Object> data = new HashMap<>();
                data.put("createdAt", FieldValue.serverTimestamp());
                tx.set(like, data);
            } else tx.delete(like);
            tx.update(post, "likeCount", Math.max(0, count(p, "likeCount") + (selected ? 1 : -1)));
            return null;
        });
    }

    public Task<Void> setBookmarked(String postId, boolean selected) {
        if (!signedIn()) return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        DocumentReference bookmark = bookmark(postId);
        return db.runTransaction(tx -> {
            DocumentSnapshot existing = tx.get(bookmark);
            if (existing.exists() == selected) return null;
            if (!selected) {
                tx.delete(bookmark);
                return null;
            }
            DocumentSnapshot p = tx.get(post(postId));
            requirePublished(p);
            Map<String, Object> data = new HashMap<>();
            data.put("postTitle", p.getString("title"));
            data.put("thumbnailPath", p.getString("thumbnailPath"));
            // 홈 탭에서 같은 문서의 collectionIds를 편집합니다. 이미 저장된 문서는 덮어쓰지 않습니다.
            data.put("collectionIds", new ArrayList<String>());
            data.put("savedAt", FieldValue.serverTimestamp());
            tx.set(bookmark, data);
            return null;
        });
    }

    public Task<Void> addEntry(String postId, String kind, String id, String content, int rating) {
        if (!signedIn()) return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        boolean review = "reviews".equals(kind);
        if (!review && !"comments".equals(kind)) return Tasks.forException(new IllegalArgumentException("잘못된 글 종류입니다."));
        String body = content.trim();
        if (body.length() < (review ? 10 : 1) || body.length() > (review ? 500 : 1000)
                || (review && (rating < 1 || rating > 5))) {
            return Tasks.forException(new IllegalArgumentException("글 길이와 별점을 확인해 주세요."));
        }
        DocumentReference post = post(postId), entry = post.collection(kind).document(id);
        return countedTransaction(post, (tx, p) -> {
            DocumentSnapshot previous = tx.get(entry);
            requirePublished(p);
            // 같은 요청을 다시 보내더라도 문서와 통계가 중복 생성되지 않습니다.
            if (previous.exists()) return null;
            Map<String, Object> data = new HashMap<>();
            data.put("authorId", uid);
            data.put("content", body);
            data.put("replyCount", 0L);
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            if (review) {
                data.put("rating", rating);
                data.put("likeCount", 0L);
            }
            Map<String, Object> stats = new HashMap<>();
            // 규칙에서 실제로 추가된 문서를 찾아 집계 변경을 검증할 때 사용합니다.
            stats.put(review ? "lastReviewId" : "lastCommentId", id);
            if (review) {
                long total = count(p, "reviewCount") + 1;
                long sum = count(p, "ratingSum") + rating;
                stats.put("reviewCount", total);
                stats.put("ratingSum", sum);
                stats.put("ratingAverage", (double) sum / total);
            } else stats.put("commentCount", count(p, "commentCount") + 1);
            tx.set(entry, data);
            tx.update(post, stats);
            return null;
        });
    }

    public Task<Void> addReply(DocumentReference parent, String id, String content) {
        if (!signedIn()) return Tasks.forException(new IllegalStateException("로그인이 필요합니다."));
        String body = content.trim();
        if (body.isEmpty() || body.length() > 1000) return Tasks.forException(new IllegalArgumentException("답글은 1~1000자로 입력해 주세요."));
        DocumentReference reply = parent.collection("replies").document(id);
        return countedTransaction(parent, (tx, original) -> {
            DocumentSnapshot existing = tx.get(reply);
            if (!original.exists()) throw new IllegalStateException("원문이 삭제되었습니다.");
            if (existing.exists()) return null;
            Map<String, Object> data = new HashMap<>();
            data.put("authorId", uid);
            data.put("content", body);
            data.put("createdAt", FieldValue.serverTimestamp());
            tx.set(reply, data);
            tx.update(parent, "replyCount", count(original, "replyCount") + 1, "lastReplyId", id);
            return null;
        });
    }

    static long count(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        return value instanceof Number ? ((Number) value).longValue() : 0;
    }

    private interface CountedWrite {
        Void apply(Transaction tx, DocumentSnapshot before) throws FirebaseFirestoreException;
    }

    private Task<Void> countedTransaction(DocumentReference counter, CountedWrite write) {
        return countedTransaction(counter, write, 3);
    }

    private Task<Void> countedTransaction(DocumentReference counter, CountedWrite write, int retries) {
        AtomicReference<Map<String, Object>> before = new AtomicReference<>();
        return db.<Void>runTransaction(tx -> {
            DocumentSnapshot snapshot = tx.get(counter);
            before.set(snapshot.getData());
            return write.apply(tx, snapshot);
        }).continueWithTask(task -> {
            if (task.isSuccessful()) return Tasks.forResult(null);
            Exception error = task.getException();
            // 다른 사람이 집계를 먼저 바꾸면 규칙 검사가 충돌 재시도보다 먼저 거부할 수 있습니다.
            // 서버 값이 실제로 바뀐 경우에만 다시 읽어 시도하고, 권한 오류 자체는 우회하지 않습니다.
            if (retries > 0 && error instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return counter.get(Source.SERVER).continueWithTask(latest -> {
                    if (latest.isSuccessful() && !Objects.equals(before.get(), latest.getResult().getData())) {
                        return countedTransaction(counter, write, retries - 1);
                    }
                    return Tasks.forException(error);
                });
            }
            return Tasks.forException(error == null ? new IllegalStateException("저장하지 못했습니다.") : error);
        });
    }

    private static void requirePublished(DocumentSnapshot doc) {
        if (!doc.exists() || !"published".equals(doc.getString("status"))) {
            throw new IllegalStateException("게시된 글에만 작성할 수 있습니다.");
        }
    }
}
