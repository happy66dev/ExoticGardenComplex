package io.github.thebusybiscuit.exoticgarden.cooking.state;

public class SeasoningEntry {
    public String seasoningId;
    public double progress;
    public double weight;
    // 调料加入灶台时的时间戳（毫秒），用于过期判断喵
    public long addedTimestamp;

    public SeasoningEntry(String seasoningId, double progress, double weight) {
        this.seasoningId = seasoningId;
        this.progress = progress;
        this.weight = weight;
        this.addedTimestamp = 0L; // 0 表示未记录（旧调料无过期机制）喵
    }
}
