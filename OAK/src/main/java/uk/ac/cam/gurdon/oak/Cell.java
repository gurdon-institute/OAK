package uk.ac.cam.gurdon.oak;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import ij.gui.Roi;

public class Cell{

	public static String X = "X";
	public static String Y = "Y";
	public static String Z = "Z";
	public static String VOLUME = "Volume";
	public static String NUCLEUS_MEAN = "Nucleus Mean";
	public static String SURROUNDING_MEAN = "Cytoplasm Mean";
	public static String MEMBRANE_MEAN = "Membrane Mean";
	
	public P3D centroid;
	public Roi[] rois;
	public Color colour;
	
	private HashMap<String,Double> features;
	
	
	public Cell(P3D centroid, Roi[] roiArr) {
		this.centroid = centroid;
		this.rois = roiArr;
		this.features = new HashMap<String,Double>();
	}

	
	public void setValue(String key, int c, double value) {
		features.put(key+"-C"+c, value);
	}
	
	public void setValue(String key, double value) {
		features.put(key, value);
	}
	
	public double getValue(String key, int c){
		return features.get(key+"-C"+c);
	}
	
	public double getValue(String key){
		try{
			if(key.equals(X)) return centroid.x;
			else if(key.equals(Y)) return centroid.y;
			else if(key.equals(Z)) return centroid.z;
			else return features.get(key);
		}catch(NullPointerException npe){
			System.out.println( key+" not found "+Arrays.toString(getKeys().toArray()) );
			return Double.NaN;
		}
	}
	
	public boolean hasKey(String key){
		return getKeys().contains(key);
	}
	
	public Set<String> getKeys(){
		return features.keySet();
	}
	
	public Double[] getValues(){
		Double[] values = new Double[features.size()];
		int i = 0;
		for(String key:getKeys()){
			values[i] = getValue(key);
			i++;
		}
		return values;
	}
	
	public void setColour(Color colour){
		this.colour = colour;
	}

}
