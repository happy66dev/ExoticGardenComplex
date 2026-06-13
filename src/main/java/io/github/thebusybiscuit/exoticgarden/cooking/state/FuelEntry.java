package io.github.thebusybiscuit.exoticgarden.cooking.state;

public class FuelEntry {
    public String fuelId;
    public int ticksRemaining;

    public FuelEntry(String fuelId, int ticksRemaining) {
        this.fuelId = fuelId;
        this.ticksRemaining = ticksRemaining;
    }
}
