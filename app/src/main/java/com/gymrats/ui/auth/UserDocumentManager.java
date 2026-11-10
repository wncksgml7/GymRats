package com.gymrats.ui.auth;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public final class UserDocumentManager {

    private UserDocumentManager() {
    }

    public static Task<Void> createIfMissing(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference userRef =
                db.collection("users").document(uid);

        DocumentReference profileRef =
                db.collection("publicProfiles").document(uid);

        return db.<Void>runTransaction(transaction -> {
            // 트랜잭션의 조회는 쓰기보다 먼저 수행
            DocumentSnapshot userSnapshot = transaction.get(userRef);
            DocumentSnapshot profileSnapshot = transaction.get(profileRef);

            Map<String, Object> user = new HashMap<>();

            if (!userSnapshot.exists()) {
                // 신규 회원의 비공개 정보 초기화
                user.put("nickname", "");
                user.put("bio", "");
                user.put("profileImagePath", null);
                user.put("points", 0L);
                user.put("followerCount", 0L);
                user.put("followingCount", 0L);
                user.put("postCount", 0L);

                transaction.set(userRef, user);
            }

            if (!profileSnapshot.exists()) {
                // 공개 가능한 세 필드만 별도 문서로 생성
                Map<String, Object> profile = new HashMap<>();

                profile.put(
                        "nickname",
                        userSnapshot.exists()
                                ? userSnapshot.getString("nickname")
                                : user.get("nickname")
                );

                profile.put(
                        "followerCount",
                        userSnapshot.exists()
                                ? userSnapshot.getLong("followerCount")
                                : user.get("followerCount")
                );

                profile.put(
                        "followingCount",
                        userSnapshot.exists()
                                ? userSnapshot.getLong("followingCount")
                                : user.get("followingCount")
                );

                transaction.set(profileRef, profile);
            }

            return null;
        });
    }
}