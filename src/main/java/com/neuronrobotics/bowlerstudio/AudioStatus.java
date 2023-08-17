package com.neuronrobotics.bowlerstudio;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
/**
 * Define the types of mouth shapes
 * 
 * content from https://github.com/DanielSWolf/rhubarb-lip-sync
 * 
 * @author hephaestus
 *
 */
public enum AudioStatus {
	/*
	 * Closed mouth for the “P”, “B”, and “M” sounds. This is almost identical to
	 * the Ⓧ shape, but there is ever-so-slight pressure between the lips.
	 */
	A_PBM_SOUNDS('A'),
	/*
	 * Slightly open mouth with clenched teeth. This mouth shape is used for most
	 * consonants (“K”, “S”, “T”, etc.). It’s also used for some vowels such as the
	 * “EE” sound in bee.
	 */
	B_KST_SOUNDS('B'),
	/*
	 * Open mouth. This mouth shape is used for vowels like “EH” as in men and “AE”
	 * as in bat. It’s also used for some consonants, depending on context.
	 * 
	 * This shape is also used as an in-between when animating from Ⓐ or Ⓑ to Ⓓ. So
	 * make sure the animations ⒶⒸⒹ and ⒷⒸⒹ look smooth!
	 */
	C_EH_AE_SOUNDS('C'),
	/*
	 * Wide open mouth. This mouth shapes is used for vowels like “AA” as in father.
	 */
	D_AA_SOUNDS('D'),
	/*
	 * Slightly rounded mouth. This mouth shape is used for vowels like “AO” as in
	 * off and “ER” as in bird.
	 * 
	 * This shape is also used as an in-between when animating from Ⓒ or Ⓓ to Ⓕ.
	 * Make sure the mouth isn’t wider open than for Ⓒ. Both ⒸⒺⒻ and ⒹⒺⒻ should
	 * result in smooth animation.
	 */
	E_AO_ER_SOUNDS('E'),
	/*
	 * Puckered lips. This mouth shape is used for “UW” as in you, “OW” as in show,
	 * and “W” as in way.
	 */
	F_UW_OW_W_SOUNDS('F'),
	/*
	 * Upper teeth touching the lower lip for “F” as in for and “V” as in very.
	 * 
	 * This extended mouth shape is optional. If your art style is detailed enough,
	 * it greatly improves the overall look of the animation. If you decide not to
	 * use it, you can specify so using the extendedShapes option.
	 */
	G_F_V_SOUNDS('G'),
	/*
	 * This shape is used for long “L” sounds, with the tongue raised behind the
	 * upper teeth. The mouth should be at least far open as in Ⓒ, but not quite as
	 * far as in Ⓓ.
	 * 
	 * This extended mouth shape is optional. Depending on your art style and the
	 * angle of the head, the tongue may not be visible at all. In this case, there
	 * is no point in drawing this extra shape. If you decide not to use it, you can
	 * specify so using the extendedShapes option.
	 */
	H_L_SOUNDS('H'),
	/*
	 * Idle position. This mouth shape is used for pauses in speech. This should be
	 * the same mouth drawing you use when your character is walking around without
	 * talking. It is almost identical to Ⓐ, but with slightly less pressure between
	 * the lips: For Ⓧ, the lips should be closed but relaxed.
	 * 
	 * This extended mouth shape is optional. Whether there should be any visible
	 * difference between the rest position Ⓧ and the closed talking mouth Ⓐ depends
	 * on your art style and personal taste. If you decide not to use it, you can
	 * specify so using the extendedShapes option.
	 */
	X_NO_SOUND('X'),
	
	// User defined visemes
	I_user_defined('I'),
	J_user_defined('J'),
	K_user_defined('K'),
	L_user_defined('L'),
	M_user_defined('M');

	private static final Map<Character, AudioStatus> lookup = new HashMap<>();
	private static Map<String, AudioStatus> ArpabetToBlair;

	static {
		for (AudioStatus s : EnumSet.allOf(AudioStatus.class))
			lookup.put(s.parsed, s);
		ArpabetToBlair = new HashMap<>();
		ArpabetToBlair.put("-", AudioStatus.X_NO_SOUND);
		ArpabetToBlair.put("aa", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("ae", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("ah", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("ao", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("aw", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("ax", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("ay", AudioStatus.C_EH_AE_SOUNDS);
		ArpabetToBlair.put("b", AudioStatus.A_PBM_SOUNDS);
		ArpabetToBlair.put("bl", AudioStatus.A_PBM_SOUNDS);
		ArpabetToBlair.put("ch", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("d", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("dx", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("dh", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("eh", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("em", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("el", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("en", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("eng", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("er", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("ey", AudioStatus.C_EH_AE_SOUNDS);
		ArpabetToBlair.put("f", AudioStatus.G_F_V_SOUNDS);
		ArpabetToBlair.put("g", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("hh", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("ih", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("iy", AudioStatus.C_EH_AE_SOUNDS);
		ArpabetToBlair.put("jh", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("k", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("l", AudioStatus.H_L_SOUNDS);
		ArpabetToBlair.put("m", AudioStatus.A_PBM_SOUNDS);
		ArpabetToBlair.put("n", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("ng", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("nx", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("ow", AudioStatus.F_UW_OW_W_SOUNDS);
		ArpabetToBlair.put("oy", AudioStatus.F_UW_OW_W_SOUNDS);
		ArpabetToBlair.put("p", AudioStatus.A_PBM_SOUNDS);
		ArpabetToBlair.put("q", AudioStatus.F_UW_OW_W_SOUNDS);
		ArpabetToBlair.put("r", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("s", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("sh", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("t", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("th", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("uh", AudioStatus.D_AA_SOUNDS);
		ArpabetToBlair.put("uw", AudioStatus.F_UW_OW_W_SOUNDS);
		ArpabetToBlair.put("v", AudioStatus.G_F_V_SOUNDS);
		ArpabetToBlair.put("w", AudioStatus.F_UW_OW_W_SOUNDS);
		ArpabetToBlair.put("y", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("z", AudioStatus.B_KST_SOUNDS);
		ArpabetToBlair.put("zh", AudioStatus.B_KST_SOUNDS);
		

		//rhubarb docs
		AudioStatus.ArpabetToBlair.put("ao", AudioStatus.E_AO_ER_SOUNDS);
		AudioStatus.ArpabetToBlair.put("er", AudioStatus.E_AO_ER_SOUNDS);
		AudioStatus.ArpabetToBlair.put("ae", AudioStatus.C_EH_AE_SOUNDS);
		AudioStatus.ArpabetToBlair.put("eh", AudioStatus.C_EH_AE_SOUNDS);
		AudioStatus.ArpabetToBlair.put("q", AudioStatus.B_KST_SOUNDS);


		//fn opinion
		AudioStatus.ArpabetToBlair.put("hh", AudioStatus.D_AA_SOUNDS);
		AudioStatus.ArpabetToBlair.put("uh", AudioStatus.F_UW_OW_W_SOUNDS);
		AudioStatus.ArpabetToBlair.put("aa", AudioStatus.C_EH_AE_SOUNDS);
		AudioStatus.ArpabetToBlair.put("ih", AudioStatus.C_EH_AE_SOUNDS);

		AudioStatus.ArpabetToBlair.put("k", AudioStatus.C_EH_AE_SOUNDS);
		AudioStatus.ArpabetToBlair.put("n", AudioStatus.C_EH_AE_SOUNDS);
		AudioStatus.ArpabetToBlair.put("r", AudioStatus.E_AO_ER_SOUNDS);

		AudioStatus.ArpabetToBlair.put("d", AudioStatus.H_L_SOUNDS);
		AudioStatus.ArpabetToBlair.put("y", AudioStatus.C_EH_AE_SOUNDS);
		AudioStatus.ArpabetToBlair.put("z", AudioStatus.C_EH_AE_SOUNDS);
	}
	public final char parsed;

	public static AudioStatus get(char code) {
		return lookup.get(code);
	}
	public static AudioStatus get(String code) {
		return lookup.get((char)code.getBytes()[0]);
	}
	public static AudioStatus getFromPhoneme(String code) {
		return ArpabetToBlair.get(code);
	}
	public static Set<String> getPhonemes() {
		return ArpabetToBlair.keySet();
	}
	
	public double mouthOpenVector() {
		switch(this) {
		case B_KST_SOUNDS:
			return 0.3;
		case C_EH_AE_SOUNDS:
			return 0.6;
		case D_AA_SOUNDS:
			return 1;
		case E_AO_ER_SOUNDS:
			return 0.6;
		case F_UW_OW_W_SOUNDS:
			return 0.2;
		case G_F_V_SOUNDS:
			return 0.1;
		case H_L_SOUNDS:
			return 0.9;
		case A_PBM_SOUNDS:
			return 0.05;
		case X_NO_SOUND:
		default:
			break;
		}
		return 0;
	}
	
	public boolean isOpen() {
		switch(this) {
		case B_KST_SOUNDS:
		case C_EH_AE_SOUNDS:
		case D_AA_SOUNDS:
		case E_AO_ER_SOUNDS:
		case H_L_SOUNDS:
			return true;
		
		}
		return false;
	}

	AudioStatus(char self) {
		parsed = self;
	}
}
