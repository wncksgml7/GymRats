package com.gymrats.ui.community;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

/** 팔로우 관계 두 개와 양쪽의 공개·비공개 통계를 함께 저장합니다. */
public final class FollowRepository {
    private final FirebaseFirestore db;
    private final String uid;
    public FollowRepository(FirebaseFirestore db, String uid) { this.db = db; this.uid = uid; }
    public DocumentReference following(String target) {
        return db.collection("users").document(uid).collection("following").document(target);
    }
    public Task<Void> setFollowing(String target, boolean selected) {
        if (uid == null || target == null || target.isEmpty() || uid.equals(target)) {
            return Tasks.forException(new IllegalArgumentException("다른 회원에게만 팔로우할 수 있습니다."));
        }
        DocumentReference own = db.collection("users").document(uid);
        DocumentReference other = db.collection("users").document(target);
        DocumentReference ownPublic = db.collection("publicProfiles").document(uid);
        DocumentReference otherPublic = db.collection("publicProfiles").document(target);
        DocumentReference following = following(target);
        DocumentReference follower = other.collection("followers").document(uid);
        return db.runTransaction(tx -> {
            DocumentSnapshot me = tx.get(ownPublic), them = tx.get(otherPublic);
            DocumentSnapshot current = tx.get(following), mirror = tx.get(follower);
            if (!me.exists() || !them.exists()) throw new IllegalStateException("공개 프로필이 준비되지 않았습니다.");
            if (current.exists() != mirror.exists()) throw new IllegalStateException("팔로우 정보가 일치하지 않습니다.");
            if (current.exists() == selected) return null;
            long delta = selected ? 1 : -1;
            if (!selected && (PostInteractions.count(me, "followingCount") < 1
                    || PostInteractions.count(them, "followerCount") < 1)) {
                throw new IllegalStateException("팔로우 집계 정보를 확인해 주세요.");
            }
            if (selected) {
                Map<String, Object> data = new HashMap<>();
                data.put("createdAt", FieldValue.serverTimestamp());
                tx.set(following, data);
                tx.set(follower, data);
            } else {
                tx.delete(following);
                tx.delete(follower);
            }
            // 다른 회원의 동시 팔로우로 증가한 값도 보존합니다.
            tx.update(own, "followingCount", FieldValue.increment(delta), "lastFollowTargetId", target);
            tx.update(ownPublic, "followingCount", FieldValue.increment(delta), "lastFollowTargetId", target);
            tx.update(other, "followerCount", FieldValue.increment(delta));
            tx.update(otherPublic, "followerCount", FieldValue.increment(delta));
            return null;
        });
    }
}
