package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.Random;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase;

public class RandomStringFactory {
	@Expose (serialize = false, deserialize = false)
    private static final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	@Expose (serialize = false, deserialize = false)
	private static final int STRING_LENGTH = 10;

	public static String[] adjectives = {
		    "Effervescent", "Zestful", "Vivacious", "Ebullient", "Sprightly",
		    "Exuberant", "Jocund", "Mirthful", "Zippy", "Gleeful",
		    "Buoyant", "Chipper", "Peppy", "Perky", "Jaunty",
		    "Blithe", "Joyous", "Spirited", "Vibrant", "Lively",
		    "Zealous", "Jubilant", "Merry", "Elated", "Euphoric",
		    "Bubbly", "Chirpy", "Animated", "Bouncy", "Energetic",
		    "Frisky", "Sparkling", "Vivid", "Zappy", "Zippy",
		    "Snappy", "Peppy", "Effulgent", "Radiant", "Luminous",
		    "Beaming", "Glowing", "Incandescent", "Resplendent", "Lustrous",
		    "Glistening", "Scintillating", "Efflorescent", "Blooming", "Flourishing",
		    "Thriving", "Burgeoning", "Prolific", "Fecund", "Teeming",
		    "Abundant", "Plentiful", "Bountiful", "Copious", "Profuse",
		    "Exuberant", "Luxuriant", "Lush", "Verdant", "Fertile",
		    "Productive", "Fruitful", "Generative", "Creative", "Imaginative",
		    "Inventive", "Ingenious", "Clever", "Brilliant", "Dazzling",
		    "Bright", "Luminous", "Radiant", "Gleaming", "Glittering",
		    "Sparkling", "Twinkling", "Shimmering", "Glowing", "Beaming",
		    "Effulgent", "Refulgent", "Resplendent", "Splendid", "Magnificent",
		    "Majestic", "Grand", "Glorious", "Superb", "Marvelous",
		    "Wonderful", "Fantastic", "Fabulous", "Astounding", "Amazing"
		};

	public static String[] creaturesMachines = {
		    "Dragon", "Robot", "Unicorn", "Cyborg", "Griffin",
		    "Automaton", "Phoenix", "Mech", "Kraken", "Droid",
		    "Chimera", "Golem", "Pegasus", "Android", "Hydra",
		    "Centaur", "Drone", "Sphinx", "Exosuit", "Minotaur",
		    "Cyclops", "Hologram", "Werewolf", "Nanobot", "Gorgon",
		    "Replicant", "Banshee", "Hovercraft", "Basilisk", "Jetpack",
		    "Gargoyle", "Teleporter", "Manticore", "Forcefield",
		    "Submarine", "Cerberus", "Hoverboard", "Siren", "Skycycle",
		    "Yeti", "Hoverbike", "Sasquatch", "Hyperloop", "Thunderbird",
		    "Gyrocopter", "Leviathan", "Airship", "Behemoth", "Starship",
		    "Colossus", "Monowheel", "Titan", "Rocketship", "Gremlin",
		    "Hovercar", "Imp", "Zeppelin", "Ogre", "Monorail",
		    "Troll", "Bathyscaphe", "Gnome", "Segway", "Leprechaun",
		    "Hyperpod", "Fairy", "Gyrocar", "Elf", "Ornithopter",
		    "Wraith", "Hoversuit", "Specter", "Levitator", "Phantom",
		    "Telekinetic", "Goblin", "Gravicycle", "Dwarf",
		     "Jetbike", "Hobgoblin", "Ekranoplan", 
		    "Aerotrain", "Sprite", "Maglev",  "Aerosled",
		     "Cybertooth", "Ifrit", "Warpod", "Elemental",
		    "Hoversled", "Goliath", "Warpcraft", "Juggernaut", "Vortexer"
		};
	
	private static int index = ((Double) ConfigurationDatabase.get(
			"CaDoodle", 
			"projectNameIndex", 
			(Math.random()*creaturesMachines.length))).intValue();
	

	public static String getNextRandomName() {
		index++;
		if(index>=creaturesMachines.length)
			index=0;
		ConfigurationDatabase.put("CaDoodle", "projectNameIndex", index);
		return adjectives[(int) (Math.random()*adjectives.length)]+"_"+creaturesMachines[index];
	}

    public static String generateRandomString() {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder(STRING_LENGTH);
        
        for (int i = 0; i < STRING_LENGTH; i++) {
            int randomIndex = random.nextInt(CHAR_POOL.length());
            stringBuilder.append(CHAR_POOL.charAt(randomIndex));
        }
        
        return getNextRandomName()+"_"+stringBuilder.toString();
    }
    
}
