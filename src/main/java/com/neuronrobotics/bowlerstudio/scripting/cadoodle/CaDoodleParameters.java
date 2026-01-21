package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

public class CaDoodleParameters {
	@Expose(serialize = true, deserialize = true)
	private ArrayList<Map.Entry<String, String>> params;

	private HashMap<String, Double> values = null;

	private CSGDatabaseInstance db;

	public String getString(String key) {
		for (Map.Entry<String, String> m : getParams()) {
			if (m.getKey().contentEquals(key))
				return m.getValue();
		}
		throw new NumberFormatException();
	}
	public void delete(String key) {
		Map.Entry<String, String> set = null;
		for (Map.Entry<String, String> m : getParams()) {
			if (m.getKey().contentEquals(key)) {
				set = m;
				break;
			}
		}
		if(set!=null)
			params.remove(set);
	}
	public void set(String key, Object value) {
		Map.Entry<String, String> set = null;
		for (Map.Entry<String, String> m : getParams()) {
			if (m.getKey().contentEquals(key)) {
				set = m;
				break;
			}
		}
		if (set == null) {
			set =Map.entry(key,value.toString());
			getParams().add(set);
		}
		set.setValue(value.toString());
		values=null;
	}
	public ArrayList<String> keys(){
		ArrayList<String> keys=new ArrayList<String>();
		for(Entry<String, String> e:getParams()) {
			keys.add(e.getKey());
		}
		return keys;
	}
	private ArrayList<Map.Entry<String, String>> getParams() {
		if (params == null) {
			params = new ArrayList<Map.Entry<String, String>>();
		}
		return params;
	}

	public double getValue(String key) throws Exception {
		return getValues().get(key).doubleValue();
	}
	
	private HashMap<String, Double> getValues() throws Exception {
		if (values == null) {
			String code = "HashMap<String,Double> numbers = new HashMap<>()\n";
			String vars = "";
			String equs = "";

			for (Map.Entry<String, String> m : getParams()) {
				// System.out.println(line);
				String value = m.getValue();
				String key =m.getKey();
				String reconstructed = key + "=" + value;
				try {
					Double.parseDouble(value);
					vars += reconstructed + "\n";
					vars += "numbers.put(\"" + key + "\"," + key + ");\n";
				} catch (NumberFormatException ex) {
					equs += reconstructed + "\n";
					equs += "numbers.put(\"" + key + "\"," + key + ");\n";
				}
			}
			code += vars;
			code += equs;
			code += "return numbers";
			// println code
			values = (HashMap<String, Double>) ScriptingEngine.inlineScriptStringRun(getDb(), code, null, "Groovy");
		}
		return values;
	}

	public CSGDatabaseInstance getDb() {
		return db;
	}

	public void setDb(CSGDatabaseInstance db) {
		this.db = db;
	}

}
