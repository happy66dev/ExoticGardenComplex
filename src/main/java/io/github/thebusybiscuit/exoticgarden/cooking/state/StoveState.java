package io.github.thebusybiscuit.exoticgarden.cooking.state;

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

    public StoveState() {
        this.currentTemp = 30.0;
        this.fuels = new ArrayList<>();
        this.slots = new IngredientSlot[4];
        this.seasonings = new ArrayList<>();
        this.spatulaBoostTicksLeft = 0;
        this.pendingFuelClear = false;
        this.fuelClearConfirmTime = 0L;
        this.cookingInProgress = false;
        this.waterAmount = 0;
        this.oilAmount = 0;
    }
}
