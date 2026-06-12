package io.github.thebusybiscuit.exoticgarden.cooking.state;

public class FuelEntry {
    public String fuelId;
    public double ticksRemaining;

    public FuelEntry(String fuelId, double ticksRemaining) {
        this.fuelId = fuelId;
        this.ticksRemaining = ticksRemaining;
    }
}
