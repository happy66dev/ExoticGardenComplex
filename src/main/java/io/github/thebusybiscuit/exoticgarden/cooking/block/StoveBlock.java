package io.github.thebusybiscuit.exoticgarden.cooking.block;

import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StoveBlock {
    public static final Map<Location, StoveState> activeStoves = new ConcurrentHashMap<>();
}
