package io.github.thebusybiscuit.exoticgarden.cooking.state;

public class SeasoningEntry {
    public String seasoningId;
    public double progress;
    public double weight;

    public SeasoningEntry(String seasoningId, double progress, double weight) {
        this.seasoningId = seasoningId;
        this.progress = progress;
        this.weight = weight;
    }
}
