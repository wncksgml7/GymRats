package com.gymrats.ui.community;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.HashSet;
import java.util.Set;

/** 화면 회전이나 후기 작성 화면 왕복 중에도 입력 내용을 유지합니다. */
public class DiscussionState extends ViewModel {
    private final SavedStateHandle state;
    final Set<String> expanded = new HashSet<>();
    final Set<String> sending = new HashSet<>();
    final MutableLiveData<Integer> completed = new MutableLiveData<>(0);
    void completed() { completed.setValue(completed.getValue() + 1); }
    public DiscussionState(SavedStateHandle state) { this.state = state; }
    String draft(String key) { String value = state.get(key); return value == null ? "" : value; }
    void draft(String key, String value) { state.set(key, value); }
    String kind() { return "comments".equals(state.get("kind")) ? "comments" : "reviews"; }
    void kind(String value) { state.set("kind", value); }
}
