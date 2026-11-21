package com.gymrats.ui.profile;

import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.google.firebase.storage.FirebaseStorage;

/** 내 정보와 방문 화면이 같은 Storage 경로를 표시합니다. */
public final class ProfileImageLoader {
    private ProfileImageLoader() { }
    public static void show(ImageView image, String path) {
        image.setTag(path);
        Glide.with(image).clear(image);
        image.setImageResource(android.R.drawable.ic_menu_myplaces);
        if (path == null || path.trim().isEmpty()) return;
        FirebaseStorage.getInstance().getReference().child(path).getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    if (!path.equals(image.getTag()) || !image.isAttachedToWindow()) return;
                    Glide.with(image).load(uri).circleCrop()
                            .placeholder(android.R.drawable.ic_menu_myplaces)
                            .error(android.R.drawable.ic_menu_myplaces).into(image);
                }).addOnFailureListener(error -> android.util.Log.w("ProfileImage", "프로필 사진 조회 실패", error));
    }
    public static void clear(ImageView image) {
        image.setTag(null);
        Glide.with(image).clear(image);
    }
}
