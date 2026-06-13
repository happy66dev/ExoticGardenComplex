package io.github.thebusybiscuit.exoticgarden.cooking.state;

public enum CharLevel {
    NONE, LIGHT, MEDIUM, HEAVY, SEVERE;

    public static CharLevel fromSeconds(double s) {
        if (s < 10) return NONE;
        if (s < 20) return LIGHT;
        if (s < 30) return MEDIUM;
        if (s < 45) return HEAVY;
        return SEVERE;
    }
}
