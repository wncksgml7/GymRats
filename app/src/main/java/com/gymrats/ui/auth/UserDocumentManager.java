package com.gymrats.ui.auth;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public final class UserDocumentManager {

    private UserDocumentManager() {
    }

    public static Task<Void> createIfMissing(String uid) {
        DocumentReference userRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid);

        //회원가입한 유저, 기존 유저인데 users/{uid}문서 업는 유저에 대해서 문서 만들기.
        return FirebaseFirestore.getInstance().<Void>runTransaction(transaction -> {
            if (!transaction.get(userRef).exists()) {
                Map<String, Object> user = new HashMap<>();
                user.put("nickname", "");
                user.put("bio", "");
                user.put("profileImagePath", null);
                user.put("points", 0L);
                user.put("followerCount", 0L);
                user.put("followingCount", 0L);
                user.put("postCount", 0L);

                transaction.set(userRef, user);
            }

            return null;
        });
    }
}