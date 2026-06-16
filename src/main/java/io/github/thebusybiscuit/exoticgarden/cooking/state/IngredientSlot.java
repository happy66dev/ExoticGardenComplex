package io.github.thebusybiscuit.exoticgarden.cooking.state;

import java.util.ArrayList;
import java.util.List;

public class IngredientSlot {
    public String ingredientId;
    public FoodState state;
    public double frontDoneness;
    public double backDoneness;
    public ActiveFace currentFace;
    public double charSeconds;
    // 已向灶台释放的水/油累计量，防止重复释放喵
    public double releasedWaterMl;
    public double releasedOilMl;
    // 该食材烹饪期间经历的燃料风味效果列表（中文显示名），可配置喵
    public List<String> fuelEffects;
    // 食材放入灶台时是否已过期喵
    public boolean isExpired;

    public IngredientSlot(String ingredientId, FoodState state, double frontDoneness,
                          double backDoneness, ActiveFace currentFace, double charSeconds) {
        this.ingredientId = ingredientId;
        this.state = state;
        this.frontDoneness = frontDoneness;
        this.backDoneness = backDoneness;
        this.currentFace = currentFace;
        this.charSeconds = charSeconds;
        this.releasedWaterMl = 0;
        this.releasedOilMl = 0;
        // 喵~防御：初始化为空列表，避免 NPE 喵
        this.fuelEffects = new ArrayList<>();
        this.isExpired = false;
    }
}
