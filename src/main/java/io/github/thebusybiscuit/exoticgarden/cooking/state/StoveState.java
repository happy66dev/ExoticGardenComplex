package io.github.thebusybiscuit.exoticgarden.cooking.state;

import io.github.thebusybiscuit.exoticgarden.cooking.CookingConstants;
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
    // AI请求期间冻结灶台（不允许交互，温度/食材状态不变），失败后保持冻结直到玩家右键解冻喵
    public boolean frozen;
    // 最近AI失败时的错误信息，解冻提示时显示喵
    public String frozenReason;
    // 首次tick标志：第一次被tick时清除可能残留的旧全息喵
    public boolean firstTick;

    // 喵~灶台液体热存储基础容量（虚拟200ml溶液，代表灶台本体的热容）喵
    public static final double BASE_LIQUID_ML = 200.0;

    public StoveState() {
        this.currentTemp = CookingConstants.BASE_AMBIENT_TEMP;
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
        this.frozen = false;
        this.frozenReason = null;
        this.firstTick = true;
    }

    /**
     * 向灶台混入新液体，根据热均衡公式更新温度喵~
     * 公式：新温度 = (当前存量*当前温度 + 新液量*30°C) / (当前存量 + 新液量)
     * 当前存量 = BASE_LIQUID_ML + 已有水量 + 已有油量
     * 新液体视为室温(30°C)喵
     */
    public void mixLiquid(double addedMl) {
        if (addedMl <= 0) return;
        double base = CookingConstants.BASE_AMBIENT_TEMP;
        double currentTotal = BASE_LIQUID_ML + waterAmount + oilAmount;
        double newTemp = (currentTotal * currentTemp + addedMl * base) / (currentTotal + addedMl);
        currentTemp = newTemp;
    }
}
