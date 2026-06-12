package io.github.thebusybiscuit.exoticgarden.cooking.state;

public class IngredientSlot {
    public String ingredientId;
    public FoodState state;
    public double frontDoneness;
    public double backDoneness;
    public ActiveFace currentFace;
    public double charSeconds;

    public IngredientSlot(String ingredientId, FoodState state, double frontDoneness,
                          double backDoneness, ActiveFace currentFace, double charSeconds) {
        this.ingredientId = ingredientId;
        this.state = state;
        this.frontDoneness = frontDoneness;
        this.backDoneness = backDoneness;
        this.currentFace = currentFace;
        this.charSeconds = charSeconds;
    }
}
