package com.neuronrobotics.bowlerstudio;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
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
	X_NO_SOUND('X');

	private static final Map<Character, AudioStatus> lookup = new HashMap<>();

	static {
		for (AudioStatus s : EnumSet.allOf(AudioStatus.class))
			lookup.put(s.parsed, s);
	}
	public final char parsed;

	public static AudioStatus get(char code) {
		return lookup.get(code);
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
