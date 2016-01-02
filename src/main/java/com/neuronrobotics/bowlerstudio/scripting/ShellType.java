package com.neuronrobotics.bowlerstudio.scripting;

import java.util.EnumSet;

public enum ShellType {
	GROOVY("Groovy"), 
	JYTHON("Jython"),
	CLOJURE("Clojure"),
	ROBOT("Robot XML"),
	NONE("None");
	
	private String nameOfShell;

	private ShellType(String name){
		setNameOfShell(name);
	}
    /**
   	 * 
   	 * 
   	 * @return
     * @throws Exception 
   	 */
	public static ShellType getFromSlug(String slug) throws Exception {
		for (ShellType cm : EnumSet.allOf(ShellType.class)) {
			if(cm.getNameOfShell().contains(slug))
				return cm;
		}
		throw new Exception("No ShellType availible for slug: "+slug);
	}
	

	public String getNameOfShell() {
		return nameOfShell;
	}

	public void setNameOfShell(String nameOfShell) {
		this.nameOfShell = nameOfShell;
	}
}
