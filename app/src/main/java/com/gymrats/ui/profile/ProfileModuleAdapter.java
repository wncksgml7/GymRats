package com.gymrats.ui.profile;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.gymrats.R;
import com.gymrats.model.ProfileModule;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileModuleAdapter extends RecyclerView.Adapter<ProfileModuleAdapter.Holder> {

    public interface DragStarter{
        void startDrag(RecyclerView.ViewHolder holder);
    }

    //modules는 CustomizeActivity와 같은 List를 공유
    private final List<ProfileModule> modules;
    private final DragStarter dragStarter;

    private final Map<String, String> subtitles = new HashMap<>();

    public ProfileModuleAdapter(List<ProfileModule> modules, DragStarter dragStarter){
        this.modules = modules;
        this.dragStarter = dragStarter;
    }

    //설명이 바뀌면 subtitle에 저장하고 notify로 RecylerView에 반영
    public void setSubtitle(String type, String subtitle){
        subtitles.put(type, subtitle);
        for (int i = 0; i < modules.size(); i++){
            if (modules.get(i).getType().equals(type)) {
                notifyItemChanged(i);
            }
        }
    }

    //List에서 두 개의 위치를 바꾸고 이를 알림
    public void move(int from, int to){
        //if문은 범위를 벗어난 위치를 무시하기 위한 장치
        if (from < 0 || to < 0 || from >= modules.size() || to >= modules.size()) {
            return;
        }
        Collections.swap(modules, from, to);
        notifyItemMoved(from, to);
    }

    @NonNull
    @Override
    //item_profile_module.xml을 inflate시켜서 한 줄의 view를 생성
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile_module, parent, false);
        return new Holder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    //줄에 데이터 채우기 (화면에 보여야 할때마다 불림, e.g. : 스크롤, notify 등)
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ProfileModule module = modules.get(position);
        String type = module.getType();

        h.tvTitle.setText(ProfileModule.titleOf(type));
        String subtitle = subtitles.get(type);
        h.tvSubtitle.setText(subtitle != null ? subtitle : ProfileModule.descriptionOf(type));

        //줄 뷰는 재사용 돼서 예전 Listener가 남아있으면 엉뚱한 값 나올 수 있음
        h.switchVisible.setOnCheckedChangeListener(null); //예전 Listener 제거
        h.switchVisible.setChecked(module.isVisible());   //값 넣기
        h.textBox.setAlpha(module.isVisible() ? 1f : 0.4f); //꺼진 모듈의 글자 흐릿하게 만들기
        //새 Listener 연결
        h.switchVisible.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int pos = h.getBindingAdapterPosition(); // 누르는 순간의 실제 위치를 표현하기 위해 그냥 position 대신 사용
            //위치를 알 수 없는 경우 그냥 넘어감
            if (pos == RecyclerView.NO_POSITION){
                return;
            }
            modules.get(pos).setVisible(isChecked);
            h.textBox.setAlpha(isChecked ? 1f : 0.4f);
        });

        //첫 줄이면 숨기고, 그 외에는 한칸 위로 올림
        h.btnUp.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        h.btnUp.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos <= 0) {
                return;
            }
            move(pos, pos - 1);
            notifyItemRangeChanged(pos -1, 2);
        });

        //터치하는 순간 끌기 시작, return false는 터치를 ItemTouchHelper에 넘기기 위함
        h.tvDragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN){
                dragStarter.startDrag(h);
            }
            return false;
        });
    }

    @Override
    //RecyclerView에 줄이 몇개인지 알려줌
    public int getItemCount() {
        return modules.size();
    }

    //한 줄의 view를 findViewById로 한번만 찾아서 필드에 보관
    static class Holder extends RecyclerView.ViewHolder{
        final TextView tvDragHandle, tvTitle, tvSubtitle;
        final View textBox;
        final MaterialSwitch switchVisible;
        final ImageButton btnUp;

        Holder(@NonNull View itemView) {
            super(itemView);
            tvDragHandle = itemView.findViewById(R.id.tvDragHandle);
            textBox = itemView.findViewById(R.id.textBox);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            switchVisible = itemView.findViewById(R.id.switchVisible);
            btnUp = itemView.findViewById(R.id.btnUp);
        }
    }
}
