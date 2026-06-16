package io.github.thebusybiscuit.exoticgarden.cooking.state;

import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

public class StoveState {
    public double currentTemp;
    public List<FuelEntry> fuels;
    public IngredientSlot[] slots;
    public List<SeasoningEntry> seasonings;
    public int spatulaBoostTicksLeft;
    public boolean pendingFuelClear;
    public long fuelClearConfirmTime;
    public boolean cookingInProgress;
    public double waterAmount;
    public double oilAmount;
    public List<String> waterSources;
    // 药水调料添加的效果列表，食用菜肴时应用给玩家喵
    public List<PotionEffect> potionEffects;

    public StoveState() {
        this.currentTemp = 30.0;
        this.fuels = new ArrayList<>();
        this.slots = new IngredientSlot[4];
        this.seasonings = new ArrayList<>();
        this.waterSources = new ArrayList<>();
        this.potionEffects = new ArrayList<>();
        this.spatulaBoostTicksLeft = 0;
        this.pendingFuelClear = false;
        this.fuelClearConfirmTime = 0L;
        this.cookingInProgress = false;
        this.waterAmount = 0;
        this.oilAmount = 0;
    }
}
