package com.neuronrobotics.bowlerstudio.lipsync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.neuronrobotics.bowlerstudio.AudioStatus;

public class PhoneticDictionary {
	private Map<String, ArrayList<String>> dictionary;

	public PhoneticDictionary(File f) throws Exception {
		dictionary = new HashMap<>();
		init(f);
	}

	private String normalizePhonemes(String phonemes) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < phonemes.length(); i++) {
			char c = phonemes.charAt(i);
			if (c == ' ' || (c >= 'A' && c <= 'Z')) {
				result.append(c);
			}
		}
		return result.toString().toLowerCase();
	}

	private ArrayList<String>  parseEntry(String entry, Map<String, ArrayList<String>> d) {
		String[] tokens = entry.split(" ");
		if (tokens.length < 2) {
			return null;
		}
		String word = tokens[0].trim().toLowerCase();
		if (word.endsWith(")")) {
			return null;
		}
		ArrayList<String> mine = new ArrayList<String>();
		for(int i=1;i<tokens.length;i++) {
			String phonemes = normalizePhonemes(tokens[i].trim());
			mine.add(phonemes);
		}

		//println "Adding to dictionary :"+word+" = phoneme "+mine
		d.put(word, mine);
		return mine;
	}

	private Map<String, ArrayList<String>> parseDictionary(String dictionaryText) {
		Map<String, ArrayList<String>> dictionary = new HashMap<>();
		String[] entries = dictionaryText.split("\n");
		for (String entry : entries) {
			if (entry.startsWith(";;;")) {
				continue;
			}
			ArrayList<String> result = parseEntry(entry, dictionary);
			if (result == null) {
				continue;
			}
		}
		return dictionary;
	}

	private String fetchDictionaryText(File dictionaryUrl) throws Exception {
		BufferedReader reader = new BufferedReader(new FileReader(dictionaryUrl));
		StringBuilder stringBuilder = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			stringBuilder.append(line).append("\n");
		}
		reader.close();
		return stringBuilder.toString();
	}

	public void init(File dictionaryUrl) throws Exception {
		String dictionaryText = fetchDictionaryText(dictionaryUrl);
		dictionary = parseDictionary(dictionaryText);
	}

	public ArrayList<String> find(String w) {
		List<String>  extra=null;
		if(w.endsWith("n't")) {
			String newW =w.substring(0,w.length()-3);
			//println "Contraction reduced "+newW+" from "+w
			w=newW;
			extra =Arrays.asList("n", "t");
		}
		if(w.endsWith("'ar")) {
			String newW =w.substring(0,w.length()-3);
			//println "Contraction reduced "+newW+" from "+w
			w=newW;
			extra =Arrays.asList( "r");
		}
		if(w.endsWith("'ll")) {
			String newW =w.substring(0,w.length()-3);
			//println "Contraction reduced "+newW+" from "+w
			w=newW;
			extra =Arrays.asList("uw", "l");
		}
		if(w.endsWith("'ve")) {
			String newW =w.substring(0,w.length()-3);
			//println "Contraction reduced "+newW+" from "+w
			w=newW;
			extra =Arrays.asList("v");
		}
		if(w.endsWith("'re")) {
			String newW =w.substring(0,w.length()-3);
			//println "Contraction reduced "+newW+" from "+w
			w=newW;
			extra =Arrays.asList("r");
		}
		if(w.endsWith("'s")) {
			String newW =w.substring(0,w.length()-2);
			//println "Contraction reduced "+newW+" from "+w
			w=newW;
			extra =Arrays.asList("s");
		}
		if(w.endsWith("'d")) {
			String newW =w.substring(0,w.length()-2);
			//println "Contraction reduced "+newW+" from "+w
			w=newW;
			extra =Arrays.asList("d");
		}
		ArrayList<String>  phonemes = new ArrayList<String>();
		ArrayList<String> dictionaryGet = dictionary.get(w);
		if(dictionaryGet==null) {
			dictionaryGet = new ArrayList<String>();
			byte[] bytes = w.getBytes();
			//println "Sounding out "+w
			for(int i=0;i<w.length();i++) {
				String charAt = ((char)bytes[i])+"";
				//println charAt
				if(AudioStatus.getFromPhoneme(charAt)!=null) {
					dictionaryGet.add(charAt);
				}else {
					for(String s:AudioStatus.getPhonemes() ) {
						if(s.contains(charAt)) {
							dictionaryGet.add(s);
							break;
						}
					}
				}
			}
			//println "New Word: "+w+" "+dictionaryGet
			dictionary.put(w,dictionaryGet);
		}
		phonemes.addAll(dictionaryGet);
		if(extra!=null) {
			phonemes.addAll(extra);
		}
		return phonemes;
	}
}
