package uk.ac.cam.gurdon.oak.analysis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.stream.IntStream;

import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.DefaultXYDataset;

import ij.IJ;
import ij.measure.ResultsTable;
import uk.ac.cam.gurdon.oak.Cell;

public class PositiveSubpopulation {

	public static void run(ArrayList<Cell> cells, String featureKey, double zThresh, ResultsTable rt) {
		
		
		ArrayList<String> spaceKeys = new ArrayList<String>();
		spaceKeys.add( Cell.X );
		spaceKeys.add( Cell.Y );
		spaceKeys.add( Cell.Z );
		double[][] spaceMatrix = new double[cells.size()][spaceKeys.size()];
		for(int ci=0;ci<cells.size();ci++){
			for(int ki=0;ki<spaceKeys.size();ki++){
				spaceMatrix[ci][ki] = cells.get(ci).getValue(spaceKeys.get(ki));
			}
		}
		
		//project onto 2 principal spatial axes
		int nDim = 3;
		int nVectors = 2;
		
		Covariance covariance = new Covariance(spaceMatrix);
		RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
		EigenDecomposition ed = new EigenDecomposition(covarianceMatrix); 
		double[] realEigenvalues = ed.getRealEigenvalues();	//ordered high-low
		
		double eigenSum = Arrays.stream(realEigenvalues).sum();
		double[] expVar = new double[nDim];	//explained variance as proportion of total
		for(int f=0;f<nDim;f++){
			expVar[f] = realEigenvalues[f] / eigenSum;
		}
		//System.out.println( "PC0:"+(realEigenvalues[0] / eigenSum) +" PC1:"+(realEigenvalues[1] / eigenSum) );
		
		//get principal component eigenvectors
		double[][] featureVectorMatrix = new double[nDim][nVectors];
		for (int c=0; c<nVectors;c++){
			RealVector eigenVector = ed.getEigenvector(c);
			for (int f=0; f<nDim; f++){
				featureVectorMatrix[f][c] = eigenVector.getEntry(f);
			}
		}
		
		//matrix dot product to project data onto principal component eigenvectors
		double[][] trans = new double[cells.size()][nVectors];
		for(int i = 0; i < cells.size(); i++){
			for(int c = 0; c < nVectors; c++){
				for(int f = 0; f < nDim; f++){
					trans[i][c] += spaceMatrix[i][f] * featureVectorMatrix[f][c];
				}
			}
		}
		
		double[] pc0 = Arrays.stream(trans).mapToDouble( v->v[0] ).toArray();
		double[] pc1 = Arrays.stream(trans).mapToDouble( v->v[1] ).toArray();
		
		
		//get major and minor axes from projection onto the plane of the two principal spatial eigenvectors rescaled to approximate real space lengths
		DoubleSummaryStatistics statsPC0 = Arrays.stream(pc0).summaryStatistics();
		double minPC0 = statsPC0.getMin();
		double maxPC0 = statsPC0.getMax();
		double[] pc0d = Arrays.stream(pc0).map( v->((v-minPC0)/(maxPC0-minPC0)) ).toArray();
		DoubleSummaryStatistics statsPC1 = Arrays.stream(pc1).summaryStatistics();
		double minPC1 = statsPC1.getMin();
		double maxPC1 = statsPC1.getMax();
		double[] pc1d = Arrays.stream(pc1).map( v->((v-minPC1)/(maxPC1-minPC1)) ).toArray();
		double maxD = 0;
		for(int a=0;a<cells.size();a++){
			for(int b=a+1;b<cells.size();b++){
				double dist = cells.get(a).centroid.distance(cells.get(b).centroid);
				maxD = Math.max(maxD, dist);
			}
		}
		double realProportion0 = (realEigenvalues[0] / eigenSum) * maxD;
		double realProportion1 = (realEigenvalues[1] / eigenSum) * maxD;
		for(int i=0;i<pc0d.length;i++){
			pc0d[i] *= realProportion0;
			pc1d[i] *= realProportion1;
		}
		
		//feature values for all cells with indices matching distance arrays
		double[] values = IntStream.range(0,cells.size()).mapToDouble( i -> cells.get(i).getValue( featureKey ) ).toArray();
		
		for(int i=0;i<values.length;i++){
			if(Double.isNaN(values[i])) values[i] = 0.0;
		}
		double mean = Arrays.stream(values).summaryStatistics().getAverage();
		double stdDev = Math.sqrt( Arrays.stream(values).map( v->(v-mean)*(v-mean) ).sum()/(float)(values.length-1) );
		double[] zScore = Arrays.stream(values).map( v->(v-mean)/stdDev ).toArray();
		
		ArrayList<Point2D.Double> posPoints = new ArrayList<Point2D.Double>();
        ArrayList<Point2D.Double> negPoints = new ArrayList<Point2D.Double>();
        
        for(int i=0;i<cells.size();i++){
        	if(zScore[i]>=zThresh){
        		posPoints.add( new Point2D.Double(pc0d[i], pc1d[i]) );
        		rt.setValue(featureKey+" call", i, "+");
        	}
        	else{
        		negPoints.add( new Point2D.Double(pc0d[i], pc1d[i]) );
        		rt.setValue(featureKey+" call", i, "-");
        	}
        }
        
		double[][] posData = new double[2][posPoints.size()];
		for(int p=0;p<posPoints.size();p++){
			Point2D.Double point = posPoints.get(p);
			posData[0][p] = point.x;
			posData[1][p] = point.y;
		}
		double[][] negData = new double[2][negPoints.size()];
		for(int p=0;p<negPoints.size();p++){
			Point2D.Double point = negPoints.get(p);
			negData[0][p] = point.x;
			negData[1][p] = point.y;
		}
		
		DefaultXYDataset ds = new DefaultXYDataset();
		ds.addSeries(featureKey+" +ve", posData);
		ds.addSeries(featureKey+" -ve", negData);
		
		JFreeChart chart = ChartFactory.createScatterPlot(featureKey+" Call Projection", "Major", "Minor", ds, PlotOrientation.VERTICAL, true, true, true);
		XYPlot plot = chart.getXYPlot();
		plot.setBackgroundPaint(Color.BLACK);
    	plot.setDomainGridlinePaint(Color.GRAY);
    	plot.setRangeGridlinePaint(Color.GRAY);
		XYItemRenderer render = plot.getRenderer();
        Shape shape = new Ellipse2D.Float(-1.0f, -1.0f, 3f, 3f);

        render.setSeriesPaint(0, Color.RED);
        render.setSeriesShape(0, shape );
        render.setSeriesPaint(1, Color.BLUE);
        render.setSeriesShape(1, shape );
		
		ChartFrame framea = new ChartFrame(featureKey+" Call Projection",chart);
        framea.pack();
		framea.setSize( new Dimension(800, 800) );
		framea.setLocationRelativeTo(null);
		framea.setVisible(true);
		
		subpopulationness(posPoints, negPoints, featureKey);
		
		polarity(pc0d, pc1d, values, featureKey);
        
	}

	
	//calculate polarity of signal of interest on the major and minor axes
	private static void polarity(double[] pc0d, double[] pc1d, double[] values, String featureKey){
        
		double halfD0 = Arrays.stream(pc0d).sum()/(float)pc0d.length;
		double halfD1 = Arrays.stream(pc1d).sum()/(float)pc1d.length;
		double total0A = 0.0;	double total0B = 0.0;
		double total1A = 0.0;	double total1B = 0.0;
		for(int i=0;i<pc0d.length;i++){
			if(Double.isNaN(values[i])) continue;	//should only happen with membrane signal when no membrane was detected
			if(pc0d[i]<=halfD0) total0A += values[i];
			else total0B += values[i];
			if(pc1d[i]<=halfD1) total1A += values[i];
			else total1B += values[i];
		}
		double polarity0 = Math.abs(total0A-total0B)/(total0A+total0B);
		double polarity1 = Math.abs(total1A-total1B)/(total1A+total1B);
		IJ.log("Major axis "+featureKey+" polarity "+IJ.d2s(polarity0,3));
		IJ.log("Minor axis "+featureKey+" polarity "+IJ.d2s(polarity1,3));
	}
	
	//calculate cluster goodness coefficients to quantify separation of positive and negative cells into sub-populations
	private static void subpopulationness(ArrayList<Point2D.Double> posPoints, ArrayList<Point2D.Double> negPoints, String featureKey){

		Point2D.Double posCentroid = new Point2D.Double(0,0);
        for(Point2D.Double pPoint:posPoints){
        	posCentroid.x += pPoint.x;
        	posCentroid.y += pPoint.y;

        }
        posCentroid.x /= (float) posPoints.size();
    	posCentroid.y /= (float) posPoints.size();
    	
    	Point2D.Double negCentroid = new Point2D.Double(0,0);
        for(Point2D.Double nPoint:negPoints){
        	negCentroid.x += nPoint.x;
        	negCentroid.y += nPoint.y;
        }
        negCentroid.x /= (float) negPoints.size();
    	negCentroid.y /= (float) negPoints.size();

    	
    	double posMeanDist = posPoints.stream().mapToDouble(p->p.distance(posCentroid)).sum() / (float)posPoints.size();
    	double negMeanDist = negPoints.stream().mapToDouble(p->p.distance(negCentroid)).sum() / (float)negPoints.size();
    	double centroidDist = posCentroid.distance(negCentroid);
    	double DB = 0.5 * ((posMeanDist+negMeanDist) / centroidDist);	//Davies-Bouldin index, lower = better
        IJ.log(featureKey+" +/-ve Davies-Bouldin index = "+IJ.d2s(DB,3));
        
        double maxIntraPos = Double.NEGATIVE_INFINITY;
        for(Point2D.Double pp:posPoints){
        	double maxDist = posPoints.stream().mapToDouble(p->p.distance(pp)).max().getAsDouble();
        	maxIntraPos = Math.max(maxDist, maxIntraPos);
        }
        double maxIntraNeg = Double.NEGATIVE_INFINITY;
    	for(Point2D.Double np:negPoints){
    		double maxDist = negPoints.stream().mapToDouble(p->p.distance(np)).max().getAsDouble();
        	maxIntraNeg = Math.max(maxDist, maxIntraNeg);
    	}
    	double D = centroidDist/Math.max(maxIntraPos, maxIntraNeg);	//Dunn Index, higher = better
    	IJ.log(featureKey+" +/-ve Dunn index = "+IJ.d2s(D,3));
        
        ArrayList<Double> silhouette = new ArrayList<Double>();
        for(Point2D.Double point:posPoints){
        	double a = posPoints.stream().filter(p->p!=point).mapToDouble(p->p.distance(point)).sum() / (float)(posPoints.size()-1);	//mean distance to all other points in the same cluster
        	double b = negPoints.stream().mapToDouble(p->p.distance(point)).sum() / (float)negPoints.size();							//mean distance to all points in the other cluster
        	double s = (b-a)/Math.max(a,b);
        	silhouette.add( s );
        }
        double SC = silhouette.stream().mapToDouble(d->d).sum() / (float)silhouette.size();	//Kaufman et al. Silhouette Coefficient, higher = better
        IJ.log(featureKey+" +ve Silhouette Coefficient = "+IJ.d2s(SC,3));
        
	}
	
}
