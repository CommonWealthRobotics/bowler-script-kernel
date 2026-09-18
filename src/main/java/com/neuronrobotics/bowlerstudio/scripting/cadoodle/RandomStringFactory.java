package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.Random;

import com.google.gson.annotations.Expose;

public class RandomStringFactory {
	@Expose(serialize = false, deserialize = false)
	private static final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	@Expose(serialize = false, deserialize = false)
	private static final int STRING_LENGTH = 10;

	public static String[] adjectives = {
			// original list (deduplicated, all kid-friendly)
			"Effervescent", "Zestful", "Vivacious", "Ebullient", "Sprightly", "Exuberant", "Jocund", "Mirthful",
			"Zippy", "Gleeful", "Buoyant", "Chipper", "Peppy", "Perky", "Jaunty", "Blithe", "Joyous", "Spirited",
			"Vibrant", "Lively", "Zealous", "Jubilant", "Merry", "Elated", "Euphoric", "Bubbly", "Chirpy", "Animated",
			"Bouncy", "Energetic", "Frisky", "Sparkling", "Vivid", "Zappy", "Snappy", "Effulgent", "Radiant",
			"Luminous", "Beaming", "Glowing", "Incandescent", "Resplendent", "Lustrous", "Glistening", "Scintillating",
			"Efflorescent", "Blooming", "Flourishing", "Thriving", "Burgeoning", "Prolific", "Teeming", "Abundant",
			"Plentiful", "Bountiful", "Copious", "Profuse", "Luxuriant", "Lush", "Verdant", "Fertile", "Productive",
			"Fruitful", "Generative", "Creative", "Imaginative", "Inventive", "Ingenious", "Clever", "Brilliant",
			"Dazzling", "Bright", "Gleaming", "Glittering", "Twinkling", "Shimmering", "Splendid", "Magnificent",
			"Majestic", "Grand", "Glorious", "Superb", "Marvelous", "Wonderful", "Fantastic", "Fabulous", "Astounding",
			"Amazing",

			// new additions (doubling the list)
			"Jolly", "Sunny", "Happy", "Playful", "Wacky", "Silly", "Goofy", "Quirky", "Nifty", "Snazzy", "Groovy",
			"Funky", "Breezy", "Cheerful", "Cheery", "Delightful", "Charming", "Whimsical", "Fanciful", "Curious",
			"Daring", "Bold", "Brave", "Valiant", "Heroic", "Mighty", "Powerful", "Sturdy", "Robust", "Speedy", "Swift",
			"Nimble", "Agile", "Quick", "Rapid", "Turbo", "Supersonic", "Cosmic", "Stellar", "Galactic", "Astral",
			"Celestial", "Magical", "Mystical", "Enchanted", "Wondrous", "Fantastical", "Legendary", "Epic", "Noble",
			"Gallant", "Gracious", "Kindly", "Friendly", "Jovial", "Genial", "Amiable", "Sociable", "Warmhearted",
			"Golden", "Silver", "Crystal", "Shiny", "Glossy", "Polished", "Sleek", "Smooth", "Silky", "Fluffy", "Fuzzy",
			"Cuddly", "Cozy", "Snug", "Comfy", "Tidy", "Neat", "Zany", "Wild", "Untamed", "Spunky", "Feisty", "Plucky",
			"Sassy", "Fizzy", "Sizzling", "Crackling", "Popping", "Starry", "Rainbow", "Colorful"};

	public static String[] creaturesMachines = {
			// original list (with scary/creepy words removed: Werewolf, Banshee, Basilisk,
			// Cerberus, Gorgon, Specter, Phantom, Ifrit)
			"Dragon", "Robot", "Unicorn", "Cyborg", "Griffin", "Automaton", "Phoenix", "Mech", "Kraken", "Chimera",
			"Golem", "Pegasus", "Android", "Hydra", "Centaur", "Drone", "Sphinx", "Exosuit", "Minotaur", "Cyclops",
			"Hologram", "Nanobot", "Replicant", "Hovercraft", "Jetpack", "Gargoyle", "Teleporter", "Forcefield",
			"Submarine", "Hoverboard", "Siren", "Skycycle", "Yeti", "Hoverbike", "Sasquatch", "Hyperloop",
			"Thunderbird", "Gyrocopter", "Airship", "Leviathan", "Starship", "Colossus", "Monowheel", "Titan",
			"Rocketship", "Gremlin", "Hovercar", "Imp", "Zeppelin", "Ogre", "Monorail", "Troll", "Gnome", "Leprechaun",
			"Hyperpod", "Fairy", "Gyrocar", "Elf", "Ornithopter", "Hoversuit", "Levitator", "Telekinetic", "Goblin",
			"Gravicycle", "Dwarf", "Jetbike", "Hobgoblin", "Ekranoplan", "Aerotrain", "Sprite", "Maglev", "Aerosled",
			"Cybertooth", "Warpod", "Elemental", "Hoversled", "Goliath", "Warpcraft", "Juggernaut", "Vortexer",
			"Behemoth",

			// new additions (doubling the list)
			"Astronaut", "Explorer", "Voyager", "Pilot", "Navigator", "Ranger", "Guardian", "Champion", "Wizard",
			"Sorcerer", "Wanderer", "Adventurer", "Knight", "Paladin", "Falcon", "Comet", "Meteor", "Satellite",
			"Rover", "Glider", "Blimp", "Biplane", "Jet", "Rocket", "Shuttle", "Cruiser", "Speedster", "Racer",
			"Turbojet", "Hovercopter", "Skiff", "Skimmer", "Skysurfer", "Windrunner", "Stormrider", "Thunderbolt",
			"Sparkler", "Firefly", "Hawk", "Eagle", "Owl", "Wolf", "Fox", "Otter", "Dolphin", "Narwhal", "Seahorse",
			"Butterfly", "Ladybug", "Cricket", "Sunbeam", "Moonbeam", "Stargazer", "Cloudrunner", "Skywhale",
			"Cloudship", "Aircar", "Skybike", "Aerobot", "Roboraptor", "Mechabot", "Sprocket", "Gizmo", "Gadgetbot",
			"Widget", "Beebot", "Turtlebot", "Bunnybot", "Puppybot", "Kittybot", "Snowbot", "Icebot", "Sunbot",
			"Starbot", "Moonrover", "Skyknight", "Cloudcastle", "Windwalker", "Skydrifter", "Nightowl", "Sundiver",
			"Moonwalker", "Skyhopper", "Cloudhopper", "Aerohawk", "Robopup", "Robokitty", "Pixiebot", "Gearbot",
			"Circuitbot", "Rocketeer", "Skypilot", "Windglider", "Starcruiser", "Galaxybot"};

	public static String getNextRandomName() {
		return adjectives[(int) (Math.random() * adjectives.length)] + "_"
				+ creaturesMachines[(int) (Math.random() * creaturesMachines.length)];
	}

	public static String generateRandomString() {
		Random random = new Random();
		StringBuilder stringBuilder = new StringBuilder(STRING_LENGTH);

		for (int i = 0; i < STRING_LENGTH; i++) {
			int randomIndex = random.nextInt(CHAR_POOL.length());
			stringBuilder.append(CHAR_POOL.charAt(randomIndex));
		}

		return getNextRandomName() + "_" + stringBuilder.toString();
	}

}
