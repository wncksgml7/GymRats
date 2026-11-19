package com.gymrats.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
public class ProfileModule {

    //firestore에 저장되는 type 값 선언
    public static final String CONTRIBUTION = "contribution";
    public static final String ACHIEVEMENTS = "achievements";
    public static final String TROPHIES = "trophies";
    public static final String MY_POSTS = "myPosts";
    public static final String WORLDCUP = "worldcupRecord";
    public static final String COLLECTIONS = "collections";

    //여러개의 값 하나의 List로 만듬, 새 모듈을 추가할거면 여기에도 추가해야됨
    private static final List<String> DEFAULT_ORDER = Arrays.asList(CONTRIBUTION, ACHIEVEMENTS, TROPHIES,
            MY_POSTS, WORLDCUP, COLLECTIONS);

    private String type;
    private boolean visible;

    //firestore가 문서를 객체로 바꿀 때 필요한 빈 생성자
    public ProfileModule(){

    }

    public ProfileModule(String type, boolean visible){
        this.type = type;
        this.visible = visible;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    //firestore에 저장할 수 있게 Map으로 변환
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("visible", visible);
        return map;
    }

    //DEFAULT_ORDER 돌면서 모든 모듈을 visible = true로 만들어서 추가
    //firestore를 읽기 전에 일단 화면을 그리기 위해 사용
    public static List<ProfileModule> defaults() {
        List<ProfileModule> list = new ArrayList<>();
        for (String type : DEFAULT_ORDER) {
            list.add(new ProfileModule(type, true));
        }
        return list;
    }

    //firestore에서 읽은 값을 ProfileModule List로 변환
    @SuppressWarnings("unchecked")
    public static List<ProfileModule> fromFirestore(Object raw) {
        List<ProfileModule> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        //가져온 값이 List인지 확인
        if (raw instanceof List) {
            //List로 형변환
            for (Object item : (List<Object>) raw) {
                //원소가 Map이 아니면 continue
                if (!(item instanceof Map)) {
                    continue;
                }

                Map<String, Object> map = (Map<String, Object>) item;
                Object type = map.get("type");

                //type이 String이 아니거나 DEFAULT_ORDER에 없는 값이거나 중복된 값이면 continue
                if (!(type instanceof String) || !DEFAULT_ORDER.contains(type) || !seen.add((String) type)) {
                    continue;
                }

                //visible이 null이어도 앱이 죽지않고 false로 처리함
                list.add(new ProfileModule((String) type, Boolean.TRUE.equals(map.get("visible"))));
            }
        }

        //저장된 적이 없는 모듈은 List 맨 뒤에 추가
        for (String type : DEFAULT_ORDER) {
            if (!seen.contains(type)) {
                list.add(new ProfileModule(type, true));
            }
        }
        return list;
    }

    //객체마다 toMap()을 호출해서 List<Map>으로 만듬 (firestore에 저장하기 위해)
    public static List<Map<String, Object>> toFirestore(List<ProfileModule> modules) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ProfileModule m : modules) {
            list.add(m.toMap());
        }
        return list;
    }

    //화면에 보일 제목
    public static String titleOf(String type) {
        switch (type) {
            case CONTRIBUTION: return "커뮤니티 기여 그래프";
            case ACHIEVEMENTS: return "나의 도전과제";
            case TROPHIES: return "트로피 진열장";
            case MY_POSTS: return "내가 만든 운동법";
            case WORLDCUP: return "이상형 월드컵 기록";
            case COLLECTIONS: return "나의 북마크 컬렉션";
            default: return type;
        }
    }

    //화면에 보일 설명
    public static String descriptionOf(String type) {
        switch (type) {
            case CONTRIBUTION: return "게시글, 후기, 댓글 기록";
            case ACHIEVEMENTS: return "전시한 도전과제";
            case TROPHIES: return "전시한 트로피";
            case MY_POSTS: return "대표 운동법과 평가 통계";
            case WORLDCUP: return "내 우승작과 참여 기록";
            case COLLECTIONS: return "저장한 운동법 폴더";
            default: return "";
        }
    }
}
