package ncfuelsim;

import java.util.List;

class Materials {
	
	static class Stack {
		
		final String name;
		final double amount;
		
		Stack(final String name, final double amount) {
			this.name = name;
			this.amount = amount;
		}
		
		@Override
		public String toString() {
			return "[" + name + ", " + amount + "]";
		}
	}
	
	static class Fuel {
		
		final double time, supply, craftProductLimit, recipeWeight;
		private final double fuelRate;
		double amount, processed = 0;
		final long vessels;
		final boolean hasReprocessingRecipe, hasCraftingRecipe;
		final List<Stack> reprocessingProducts, craftingIngredients;
		
		Fuel(final double time, final long vessels, final double start, final double supply, final boolean hasReprocessingRecipe, final List<Stack> reprocessingProducts, final double craftProductLimit, final boolean hasCraftingRecipe, final List<Stack> craftingIngredients, final double recipeWeight) {
			this.time = time;
			this.vessels = vessels;
			fuelRate = FuelSim.SPEED*vessels/time;
			this.amount = start;
			this.supply = supply;
			this.hasReprocessingRecipe = hasReprocessingRecipe;
			this.reprocessingProducts = reprocessingProducts;
			this.craftProductLimit = craftProductLimit < 0D ? Double.MAX_VALUE : craftProductLimit;
			this.hasCraftingRecipe = hasCraftingRecipe;
			this.craftingIngredients = craftingIngredients;
			this.recipeWeight = Math.min(1D, Math.max(0D, recipeWeight));
		}
		
		double fuelUse() {
			return  Math.min(fuelRate, amount);
		}
		
		double amountUntilCraftLimit() {
			return craftProductLimit - amount;
		}
	}
	
	static class Resource {
		
		double amount;
		final double supply, decayThreshold, recipeWeight, craftingThreshold, decayProductLimit;
		final boolean hasDecayRecipe;
		final Stack decayProduct;
		
		Resource(final double start, final double supply, final boolean hasDecayRecipe, final Stack decayProduct, final double decayThreshold, final double recipeWeight, final double craftingThreshold, final double decayProductLimit) {
			this.amount = start;
			this.supply = supply;
			this.hasDecayRecipe = hasDecayRecipe;
			this.decayProduct = decayProduct;
			this.decayThreshold = decayThreshold < 0D ? Double.MAX_VALUE : decayThreshold;
			this.recipeWeight = Math.min(1D, Math.max(0D, recipeWeight));
			this.craftingThreshold = craftingThreshold < 0D ? Double.MAX_VALUE : craftingThreshold;
			this.decayProductLimit = decayProductLimit < 0D ? Double.MAX_VALUE : decayProductLimit;
		}
		
		double amountForDecaying() {
			return amount - decayThreshold;
		}
		
		double amountForCrafting() {
			return amount - craftingThreshold;
		}
		
		double amountUntilDecayLimit() {
			return decayProductLimit - amount;
		}
	}
}
