package uk.ac.cam.gurdon.oak.analysis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
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
import uk.ac.cam.gurdon.oak.Cell;


public class OrganoidPolarity {

	
	public static void run(ArrayList<Cell> cells, String featureKey){
		try{
			
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
			
			//calculate polarity of signal of interest on the major and minor axes
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
			
			//plot
			double[][] dataPC0 = new double[][]{pc0d, values};
			double[][] dataPC1 = new double[][]{pc1d, values};
			
			DefaultXYDataset dataset = new DefaultXYDataset();
			dataset.addSeries(featureKey+" Major Axis", dataPC0);
			dataset.addSeries(featureKey+" Minor Axis", dataPC1);
			
			JFreeChart chart = ChartFactory.createScatterPlot(featureKey+" vs Projected Axes", "Position (\u00b5m)", "Intensity", dataset, PlotOrientation.VERTICAL, true, true, true);
			XYPlot plot = chart.getXYPlot();
			plot.setBackgroundPaint(Color.BLACK);
	    	plot.setDomainGridlinePaint(Color.GRAY);
	    	plot.setRangeGridlinePaint(Color.GRAY);
			XYItemRenderer render = plot.getRenderer();
	        Shape shape = new Ellipse2D.Float(-0.5f, -0.5f, 1f, 1f);
	        for(int r=0;r<dataset.getSeriesCount();r++){
	        	float h = r/(float)(dataset.getSeriesCount()+1);
	    		float s = 1.0f;
	    		float v = 1.0f;
	    		Color colour = Color.getHSBColor(h,s,v);	//major red, minor green
	        	render.setSeriesPaint(r, colour);
	        	render.setSeriesShape(r, shape );
	        }
			
			ChartFrame frame = new ChartFrame(featureKey+" vs Projected Axes",chart);
	        frame.pack();
			frame.setSize( new Dimension(800, 800) );
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
			
			
		}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}	
	}

}
