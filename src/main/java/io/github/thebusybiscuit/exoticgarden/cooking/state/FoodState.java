package io.github.thebusybiscuit.exoticgarden.cooking.state;

public enum FoodState {
    WHOLE, SLICED, JULIENNED, DICED, SAUCE;

    // 各切割状态对应的成熟速度倍率喵：切割越细熟得越快喵
    public double getMultiplier() {
        return switch (this) {
            case WHOLE     -> 1.0; // 整块，无加速喵
            case SLICED    -> 1.5; // 切片，加速1.5倍喵
            case JULIENNED -> 1.8; // 切条，加速1.8倍喵
            case DICED     -> 2.0; // 切丁，加速2.0倍喵
            case SAUCE     -> 3.0; // 酱汁，加速3.0倍喵
        };
    }
}
