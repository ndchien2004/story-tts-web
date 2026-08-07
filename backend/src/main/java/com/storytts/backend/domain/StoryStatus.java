package com.storytts.backend.domain;

/** Trạng thái truyện (mục 4.2 đề bài): đang ra / hoàn thành. */
public enum StoryStatus {
    ONGOING("Đang ra"),
    COMPLETED("Hoàn thành");

    private final String label;

    StoryStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
