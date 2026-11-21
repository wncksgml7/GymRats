package com.gymrats.ui.community;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import java.util.UUID;

public class ReviewDraftViewModel extends ViewModel {
    private final SavedStateHandle state;
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<String> result = new MutableLiveData<>();

    public ReviewDraftViewModel(SavedStateHandle state) { this.state = state; }
    public String content() { return state.get("content") == null ? "" : state.get("content"); }
    public int rating() { return state.get("rating") == null ? 0 : state.get("rating"); }
    public void draft(String content, int rating) { state.set("content", content); state.set("rating", rating); }
    public LiveData<Boolean> saving() { return saving; }
    public LiveData<String> result() { return result; }
    public void consumeResult() { result.setValue(null); }

    public void submit(String postId) {
        if (Boolean.TRUE.equals(saving.getValue())) return;
        if (state.get("requestId") == null) state.set("requestId", UUID.randomUUID().toString());
        saving.setValue(true);
        new PostInteractions().addEntry(postId, "reviews", state.get("requestId"), content(), rating())
                .addOnCompleteListener(task -> {
                    saving.setValue(false);
                    result.setValue(task.isSuccessful() ? "" : "후기를 저장하지 못했습니다. 네트워크와 접근 권한을 확인하고 다시 시도해 주세요.");
                });
    }
}
