package uk.ac.cam.gurdon.oak.analysis;

import java.util.ArrayList;
import java.util.Arrays;

import ij.measure.ResultsTable;
import uk.ac.cam.gurdon.oak.Cell;

public class PositiveCaller {

	public static void run(ArrayList<Cell> cells, String featureKey, double zThresh, ResultsTable rt){
		
		double[] values = cells.stream().mapToDouble( c->c.getValue(featureKey) ).map( v->Double.isNaN(v)?0.0:v ).toArray();
		double mean = Arrays.stream(values).summaryStatistics().getAverage();
		double stdDev = Math.sqrt( Arrays.stream(values).map( v->(v-mean)*(v-mean) ).sum()/(float)(values.length-1) );
		double[] zScore = Arrays.stream(values).map( v->(v-mean)/stdDev ).toArray();
		
		for(int i=0;i<cells.size();i++){
			rt.setValue(featureKey+" call", i, zScore[i]>=zThresh?"+":"-");
		}
		
	}
	
}
