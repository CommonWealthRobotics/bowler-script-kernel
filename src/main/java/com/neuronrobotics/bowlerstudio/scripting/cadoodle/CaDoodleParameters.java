package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

public class CaDoodleParameters {
	@Expose(serialize = true, deserialize = true)
	private ArrayList<CaDoodleParameter> params;

	private HashMap<String, Double> values = null;

	private CSGDatabaseInstance db;

	public String getString(String key) {
		for (CaDoodleParameter m : getParams()) {
			if (m.getKey().contentEquals(key))
				return m.getValue();
		}
		throw new NumberFormatException();
	}
	public void delete(String key) {
		CaDoodleParameter set = null;
		for (CaDoodleParameter m : getParams()) {
			if (m.getKey().contentEquals(key)) {
				set = m;
				break;
			}
		}
		if(set!=null)
			params.remove(set);
	}
	public void set(String key, Object value) {
		CaDoodleParameter set = null;
		for (CaDoodleParameter m : getParams()) {
			if (m.getKey().contentEquals(key)) {
				set = m;
				break;
			}
		}
		if (set == null) {
			set =new CaDoodleParameter(key,value.toString());
			getParams().add(set);
		}
		set.setValue(value.toString());
		values=null;
	}
	public ArrayList<String> keys(){
		ArrayList<String> keys=new ArrayList<String>();
		for(CaDoodleParameter e:getParams()) {
			keys.add(e.getKey());
		}
		return keys;
	}
	private ArrayList<CaDoodleParameter> getParams() {
		if (params == null) {
			params = new ArrayList<CaDoodleParameter>();
		}
		return params;
	}

	public double getValue(String key) throws Exception {
		Number double1 = getValues().get(key);
		return double1.doubleValue();
	}
	
	private HashMap<String, Double> getValues() throws Exception {
		if (values == null) {
			String code = "HashMap<String,Double> numbers = new HashMap<String,Double>()\n";
			String vars = "";
			String equs = "";

			for (CaDoodleParameter m : getParams()) {
				// System.out.println(line);
				String value = m.getValue();
				String variableName =m.getKey();
				String reconstructed = variableName + "=" + value;
				try {
					Double.parseDouble(value);
					vars += reconstructed + "\n";
					vars += "numbers.put(\"" + variableName + "\"," + variableName + ");\n";
				} catch (NumberFormatException ex) {
					equs += reconstructed + "\n";
					equs += "numbers.put(\"" + variableName + "\"," + variableName + ");\n";
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
