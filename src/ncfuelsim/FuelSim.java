package ncfuelsim;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ncfuelsim.Materials.Fuel;
import ncfuelsim.Materials.Resource;
import ncfuelsim.Materials.Stack;

public class FuelSim {
	
	static final Map<String, Fuel> FUEL_MAP = new LinkedHashMap<String, Fuel>();
	static final Map<String, Resource> RESOURCE_MAP = new LinkedHashMap<String, Resource>();
	
	static final Map<Long, Map<String, String>> RECIPE_PRIORITY_MAP = new HashMap<Long, Map<String, String>>();
	static final List<String> RECIPE_INPUT_LIST = new BasicList<String>();
	static final List<String> RECIPE_TYPE_LIST = new BasicList<String>();
	
	/* Settings */
	
	static final double SPEED;
	static final long ITERATIONS;
	static final boolean PRINT_EACH;
	
	static {
		Document settingsDoc = null;
		try {
			settingsDoc = FileReader.buildDocument("simsettings");
		} catch (Exception e) {
			e.printStackTrace();
		}
		Element settingsRoot = settingsDoc.getDocumentElement();
		settingsRoot.normalize();
		
		NodeList settingsList = settingsDoc.getElementsByTagName(settingsRoot.getNodeName());
		Element settingsElement = (Element) settingsList.item(0);
		
		SPEED = readDouble(subElement(settingsElement, "speed"));
		ITERATIONS = readLong(subElement(settingsElement, "iterations"));
		PRINT_EACH = readBoolean(subElement(settingsElement, "print_each_iteration"));
	}
	
	public static void main(String[] args) throws Exception {
		
		mapFuels();
		mapResources();
		
		listRecipePriorities();
		
		printStartFuelData();
		printStartResourceData();
		
		printRecipeOrder();
		
		Scanner scanner = new Scanner(System.in);
		
		print("Press Enter to begin the simulation at " + SPEED + "x speed, iterating " + ITERATIONS + " times . . .");
		scanner.nextLine();
		
		if (!PRINT_EACH) print("Running . . .");
		
		long iter = 0;
		while (iter < ITERATIONS) {
			iter++;
			if (PRINT_EACH) print("Iteration " + iter + " . . .");
			
			process();
			
			if (PRINT_EACH) {
				printFuelData();
				printResourceData();
			}
		}
		
		print("Finished!");
		line();
		
		printFuelData();
		printResourceData();
		
		scanner.nextLine();
		scanner.close();
	}
	
	static void process() {
		for (Entry<String, Fuel> entry : FUEL_MAP.entrySet()) {
			Fuel fuel = entry.getValue();
			
			fuel.amount += FuelSim.SPEED*fuel.supply;
			
			double fuelUse = fuel.fuelUse();
			
			fuel.amount -= fuelUse;
			fuel.processed += fuelUse;
			
			for (Stack reprocessingProduct : fuel.reprocessingProducts) RESOURCE_MAP.get(reprocessingProduct.name).amount += fuelUse*reprocessingProduct.amount;
		}
		
		for (Entry<String, Resource> entry : RESOURCE_MAP.entrySet()) {
			Resource resource = entry.getValue();
			
			resource.amount += FuelSim.SPEED*resource.supply;
		}
		
		for (int i = 0; i < RECIPE_TYPE_LIST.size(); i++) {
			if (RECIPE_TYPE_LIST.get(i).equals("fuel")) {
				Fuel fuel = FUEL_MAP.get(RECIPE_INPUT_LIST.get(i));
				if (!fuel.hasCraftingRecipe || fuel.amountUntilCraftLimit() < 0D) continue;
				
				List<Double> craftMults = new BasicList<Double>();
				for (Stack ingredient : fuel.craftingIngredients) {
					Resource ingredientType = RESOURCE_MAP.get(ingredient.name);
					craftMults.add(ingredientType.amountForCrafting()/ingredient.amount);
				}
				double craftMult = Math.min(Collections.min(craftMults), fuel.amountUntilCraftLimit())*fuel.recipeWeight;
				
				if (craftMult > 0D) {
					for (Stack ingredient : fuel.craftingIngredients) RESOURCE_MAP.get(ingredient.name).amount -= ingredient.amount*craftMult;
					fuel.amount += craftMult;
				}
			}
			
			else if (RECIPE_TYPE_LIST.get(i).equals("resource")) {
				Resource resource = RESOURCE_MAP.get(RECIPE_INPUT_LIST.get(i));
				if (!resource.hasDecayRecipe) continue;
				
				Resource decayProduct = RESOURCE_MAP.get(resource.decayProduct.name);
				double decayMult = Math.min(resource.amountForDecaying(), decayProduct.amountUntilDecayLimit())*resource.recipeWeight;
				
				if (decayMult > 0D) {
					decayProduct.amount += resource.decayProduct.amount*decayMult;
					resource.amount -= decayMult;
				}
			}
		}
	}
	
	/* Material Mapping */
	
	static void mapFuels() throws Exception {
		Document fuelDoc = FileReader.buildDocument("fueldata");
		Element fuelRoot = fuelDoc.getDocumentElement();
		fuelRoot.normalize();
		
		NodeList fuelList = fuelDoc.getElementsByTagName("fuel");
		for (int i = 0; i < fuelList.getLength(); i++) {
			Element fuelElement = (Element) fuelList.item(i);
			
			String name = fuelElement.getAttribute("name");
			double time = readDouble(subElement(fuelElement, "process_time"));
			long vessels = readLong(subElement(fuelElement, "cells"));
			double start = readDouble(subElement(fuelElement, "starting_amount"));
			double supply = readDouble(subElement(fuelElement, "supply_rate"));
			boolean hasReprocessorRecipe = readBoolean(subElement(fuelElement, "has_reprocessor_recipe"));
			
			List<Stack> reprocessingProducts = new BasicList<Stack>();
			if (hasReprocessorRecipe) {
				Element recipeElement = subElement(fuelElement, "reprocessor_recipe");
				
				double inputAmount = readDouble(subElement(recipeElement, "input"));
				
				NodeList outputList = recipeElement.getElementsByTagName("output");
				for (int j = 0; j < outputList.getLength(); j++) {
					Element outputElement = (Element) outputList.item(j);
					
					String outputName = outputElement.getAttribute("name");
					double amount = readDouble(outputElement)/inputAmount;
					
					reprocessingProducts.add(stack(outputName, amount));
				}
			}
			
			boolean hasCraftingRecipe = readBoolean(subElement(fuelElement, "has_crafting_recipe"));
			
			List<Stack> craftingIngredients = new BasicList<Stack>();
			double craftProductLimit = 0D;
			double recipeWeight = 0D;
			if (hasCraftingRecipe) {
				Element recipeElement = subElement(fuelElement, "crafting_recipe");
				
				double resultAmount = readDouble(subElement(recipeElement, "result"));
				
				NodeList ingredientList = recipeElement.getElementsByTagName("ingredient");
				for (int j = 0; j < ingredientList.getLength(); j++) {
					Element ingredientElement = (Element) ingredientList.item(j);
					
					String ingredientName = ingredientElement.getAttribute("name");
					double amount = readDouble(ingredientElement)/resultAmount;
					
					craftingIngredients.add(stack(ingredientName, amount));
				}
				craftProductLimit = readDouble(subElement(fuelElement, "craft_product_limit"));
				
				long recipePriority = readLong(subElement(fuelElement, "recipe_priority"));
				mapRecipePriority(recipePriority, name, "fuel");
				
				recipeWeight = readDouble(subElement(fuelElement, "recipe_weight"));
			}
			
			FUEL_MAP.put(name, fuel(time, vessels, start, supply, hasReprocessorRecipe, reprocessingProducts, craftProductLimit, hasCraftingRecipe, craftingIngredients, recipeWeight));
		}
	}
	
	static void mapResources() throws Exception {
		Document resourceDoc = FileReader.buildDocument("resourcedata");
		Element resourceRoot = resourceDoc.getDocumentElement();
		resourceRoot.normalize();
		
		NodeList resourceList = resourceDoc.getElementsByTagName("resource");
		
		for (int i = 0; i < resourceList.getLength(); i++) {
			Element resourceElement = (Element) resourceList.item(i);
			
			String name = resourceElement.getAttribute("name");
			double start = readDouble(subElement(resourceElement, "starting_amount"));
			double supply = readDouble(subElement(resourceElement, "supply_rate"));
			boolean hasDecayRecipe = readBoolean(subElement(resourceElement, "has_decay_recipe"));
			
			Stack product = null;
			double decayThreshold = 0D;
			double recipeWeight = 0D;
			if (hasDecayRecipe) {
				Element recipeElement = subElement(resourceElement, "decay_recipe");
				
				String outputName = subElement(recipeElement, "output").getAttribute("name");
				double amount = readDouble(subElement(recipeElement, "output"))/readDouble(subElement(recipeElement, "input"));
				
				product = stack(outputName, amount);
				decayThreshold = readDouble(subElement(resourceElement, "decay_threshold"));
				
				long recipePriority = readLong(subElement(resourceElement, "recipe_priority"));
				mapRecipePriority(recipePriority, name, "resource");
				
				recipeWeight = readDouble(subElement(resourceElement, "recipe_weight"));
			}
			
			double craftingThreshold = readDouble(subElement(resourceElement, "craft_threshold"));
			
			double decayProductLimit = -1D;
			if (subElement(resourceElement, "decay_result_limit") != null) {
				decayProductLimit = readDouble(subElement(resourceElement, "decay_product_limit"));
			}
			
			RESOURCE_MAP.put(name, resource(start, supply, hasDecayRecipe, product, decayThreshold, recipeWeight, craftingThreshold, decayProductLimit));
		}
	}
	
	static void mapRecipePriority(long priority, String name, String type) {
		Map<String, String> recipeMap = RECIPE_PRIORITY_MAP.get(priority);
		if (recipeMap == null) {
			recipeMap = new HashMap<String, String>();
			recipeMap.put(name, type);
			RECIPE_PRIORITY_MAP.put(priority, recipeMap);
		}
		else recipeMap.put(name, type);
	}
	
	static void listRecipePriorities() {
		List<Long> priorities = new BasicList<Long>(RECIPE_PRIORITY_MAP.keySet());
		
		Collections.sort(priorities, Collections.reverseOrder());
		
		for (int i = 0; i < priorities.size(); i++) {
			Map<String, String> recipeMap = RECIPE_PRIORITY_MAP.get(priorities.get(i));
			for (Entry<String, String> recipeEntry : recipeMap.entrySet()) {
				RECIPE_INPUT_LIST.add(recipeEntry.getKey());
				RECIPE_TYPE_LIST.add(recipeEntry.getValue());
			}
		}
	}
	
	/* Materials */
	
	static Stack stack(final String name, final double amount) {
		return new Stack(name, amount);
	}
	
	static Fuel fuel(final double time, final long vessels, final double start, final double supply, final boolean hasReprocessingRecipe, final List<Stack> reprocessingProducts, final double craftProductLimit, final boolean hasCraftingRecipe, final List<Stack> craftingIngredients, final double recipeWeight) {
		return new Fuel(time, vessels, start, supply, hasReprocessingRecipe, reprocessingProducts, craftProductLimit, hasCraftingRecipe, craftingIngredients, recipeWeight);
	}
	
	static Resource resource(double start, final double supply, final boolean hasDecayRecipe, final Stack decayProduct, final double decayThreshold, final double recipeWeight, final double craftingThreshold, final double decayProductLimit) {
		return new Resource(start, supply, hasDecayRecipe, decayProduct, decayThreshold, recipeWeight, craftingThreshold, decayProductLimit);
	}
	
	/* Printing Methods */
	
	static void print(String s) {
		System.out.println(s);
	}
	
	static void line() {
		System.out.println("\n");
	}
	
	/* Fuel Data Print */
	
	static void printStartFuelData() {
		for (Entry<String, Fuel> entry : FUEL_MAP.entrySet()) {
			print("Fuel Type: " + entry.getKey());
			
			Fuel fuel = entry.getValue();
			print("Fuel Process Time: " + fuel.time);
			print("Number of Vessels: " + fuel.vessels);
			print("Starting Amount: " + fuel.amount);
			print("Supply Rate: " + fuel.supply);
			if (fuel.hasReprocessingRecipe) print("Reprocessing Outputs: " + fuel.reprocessingProducts.toString());
			if (fuel.hasCraftingRecipe) print("Crafting Ingredients: " + fuel.craftingIngredients.toString());
			if (fuel.hasCraftingRecipe && fuel.craftProductLimit < Double.MAX_VALUE) print("Crafting Limit: " + fuel.craftProductLimit);
			if (fuel.hasCraftingRecipe) print("Crafting Recipe Weight: " + fuel.recipeWeight);
			
			line();
		}
	}
	
	static void printFuelData() {
		for (Entry<String, Fuel> entry : FUEL_MAP.entrySet()) {
			print("Fuel Type: " + entry.getKey());
			
			Fuel fuel = entry.getValue();
			print("Amount Stored: " + fuel.amount);
			print("Amount Processed: " + fuel.processed);
			
			line();
		}
	}
	
	static void printRecipeOrder() {
		print("Recipe Order: " + RECIPE_INPUT_LIST.toString());
		line();
	}
	
	/* Resource Data Print */
	
	static void printStartResourceData() {
		for (Entry<String, Resource> entry : RESOURCE_MAP.entrySet()) {
			print("Resource Type: " + entry.getKey());
			
			Resource resource = entry.getValue();
			print("Starting Amount: " + resource.amount);
			print("Supply Rate: " + resource.supply);
			if (resource.hasDecayRecipe) print("Decay Result: " + resource.decayProduct.toString());
			if (resource.hasDecayRecipe && resource.decayThreshold < Double.MAX_VALUE) print("Decay Threshold: " + resource.decayThreshold);
			if (resource.hasDecayRecipe) print("Decay Recipe Weight: " + resource.recipeWeight);
			if (resource.craftingThreshold < Double.MAX_VALUE) print("Crafting Threshold: " + resource.craftingThreshold);
			if (resource.decayProductLimit < Double.MAX_VALUE) print("Decay Limit: " + resource.decayProductLimit);
			
			line();
		}
	}
	
	static void printResourceData() {
		for (Entry<String, Resource> entry : RESOURCE_MAP.entrySet()) {
			print("Resource Type: " + entry.getKey());
			
			Resource resource = entry.getValue();
			print("Amount Stored: " + resource.amount);
			
			line();
		}
	}
	
	/* XML Reading */
	
	static Element subElement(Element parent, String name) {
		return subElement(parent, name, 0);
	}
	
	static Element subElement(Element parent, String name, int i) {
		return (Element) parent.getElementsByTagName(name).item(i);
	}
	
	static String readString(Element element) {
		return element.getTextContent();
	}
	
	static boolean readBoolean(Element element) {
		return Boolean.parseBoolean(readString(element));
	}
	
	static double readDouble(Element element) {
		return FileReader.evaluate(readString(element));
	}
	
	static long readLong(Element element) {
		return (long)readDouble(element);
	}
}
