package com.neuronrobotics.bowlerstudio.vitamins;

import java.util.HashMap;

public class PurchasingData {
	private String size;
	private String variant;
	private HashMap<String, String> variantParameters;
	private HashMap<Integer, Double>  pricsUSD;
	private String 	urlAPI;
	private String 	db;
	private String serverType;
	private String cartURL;
	
	public HashMap<String, String> getVariantParameters() {
		return variantParameters;
	}
	public void setVariantParameters(HashMap<String, String> variantParameters) {
		this.variantParameters = variantParameters;
	}
	public String getVariant() {
		return variant;
	}
	public void setVariant(String variant) {
		this.variant = variant;
	}
	public String getSize() {
		return size;
	}
	public void setSize(String size) {
		this.size = size;
	}

	public double getPricsUSD(int qty) {
		return pricsUSD.get(qty);
	}
	public void setPricsUSD( int qty,double pricsUSD) {
		this.pricsUSD .put(qty, pricsUSD);
	}
	
	public String getAPIUrl() {
		return urlAPI;
	}
	public void setAPIUrl(String url) {
		this.urlAPI = url;
	}
	public String getDatabase() {
		return db;
	}
	public void setDatabase(String db) {
		this.db = db;
	}
	public String getServerType() {
		return serverType;
	}
	public void setServerType(String serverType) {
		this.serverType = serverType;
	}
	public String getCartUrl() {
		return cartURL;
	}
	public void setCartUrl(String cartURL) {
		this.cartURL = cartURL;
	}
	
}
