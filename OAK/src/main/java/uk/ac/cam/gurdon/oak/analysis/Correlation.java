package uk.ac.cam.gurdon.oak.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

import ij.IJ;
import uk.ac.cam.gurdon.oak.Cell;

public class Correlation {

	public static void run(ArrayList<Cell> cells, String key, String key2) {
		
		double[] valuesA = IntStream.range(0,cells.size()).mapToDouble( i -> cells.get(i).getValue( key ) ).toArray();
		double[] valuesB = IntStream.range(0,cells.size()).mapToDouble( i -> cells.get(i).getValue( key2 ) ).toArray();
		
		double meanA = Arrays.stream(valuesA).sum() / (float)valuesA.length;
		double meanB = Arrays.stream(valuesB).sum() / (float)valuesB.length;

		double covar = 0;
		double varA = 0;
		double varB = 0;
		for(int i=0;i<cells.size();i++){
			covar += (valuesA[i]-meanA)*(valuesB[i]-meanB);
			varA += (valuesA[i]-meanA)*(valuesA[i]-meanA);
			varB += (valuesB[i]-meanB)*(valuesB[i]-meanB);
		}
		double pcc = covar/Math.sqrt(varA*varB);
		
		IJ.log(key+" vs "+key2+" correlation = "+IJ.d2s(pcc, 3));
		
	}

}
