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
    // 当前 AI 请求的唯一令牌，用于阻止已失效请求的迟到回调修改灶台喵
    public java.util.UUID activeAiRequestId;
    // 当前 AI 请求 future，用于灶台销毁或插件停用时取消后台网络任务喵
    public java.util.concurrent.CompletableFuture<?> activeAiFuture;
    // 最近一次有效交互时间，单位：毫秒，用于回收长期空闲的空灶台状态喵
    public long lastActiveAtMillis;

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
        this.activeAiRequestId = null;
        this.activeAiFuture = null;
        this.lastActiveAtMillis = System.currentTimeMillis();
    }

    /**
     * 判断灶台是否没有任何需要保留的运行时内容喵~
     * 输入：无
     * 输出：燃料、食材、调料、液体、药水和 AI 请求都为空时返回 true
     * 边界：AI 失败后仍冻结的灶台必须保留，避免玩家失去手动解冻入口喵
     */
    public boolean isEmpty() {
        // 喵~防御：AI 请求中或失败待解冻的灶台不能回收喵
        if (frozen || activeAiRequestId != null) return false;
        // 喵~防御：任一实体内容存在时保留灶台状态喵
        if (!fuels.isEmpty() || !seasonings.isEmpty() || !waterSources.isEmpty() || !potionEffects.isEmpty()) return false;
        // 喵~防御：水或油仍存在时保留灶台状态喵
        if (waterAmount > 0 || oilAmount > 0) return false;
        // 逐格检查食材槽是否为空喵
        for (IngredientSlot slot : slots) {
            // 喵~防御：任一食材存在时保留灶台状态喵
            if (slot != null) return false;
        }
        return true;
    }

    /**
     * 使当前 AI 请求失效并尝试取消 future，避免迟到回调修改已销毁的灶台喵~
     * 输入：无
     * 输出：无
     * 边界：取消 HTTP 阻塞不一定立即生效，调用方仍必须校验请求令牌喵
     */
    public void invalidateAiRequest() {
        // 清除令牌使所有已提交回调失去修改状态的资格喵
        activeAiRequestId = null;
        // 喵~防御：future不存在时无需取消喵
        if (activeAiFuture != null) {
            // 尝试中断后台任务，网络层仍由超时与连接关闭兜底喵
            activeAiFuture.cancel(true);
            activeAiFuture = null;
        }
    }

    /**
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
