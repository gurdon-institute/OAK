package uk.ac.cam.gurdon.oak;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYShapeAnnotation;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.ProfilePlot;
import ij.measure.Calibration;
import ij.measure.CurveFitter;
import ij.plugin.filter.MaximumFinder;
import ij.process.ImageProcessor;

public class SizeProfile {

	public static void run(ImagePlus imp) {
		try{
			Calibration cal = imp.getCalibration();
			int W = imp.getWidth();
			int H = imp.getHeight();
			int channel = imp.getChannel();
			int frame = imp.getFrame();
			double cx = W/2f;
			double cy = H/2f;
			int nAngles = 8;
			double scale = Math.min(cx, cy);	//min half of image size to stay inside bounds
			double lineW = 10.0/cal.pixelWidth;	//10 microns in px
			
			double minD = 2.0d;	//extrema peak distance/2 inclusion range (µm)
			double maxD = 10d;
			
		ArrayList<Double> distanceList = new ArrayList<Double>();
		for(int z=1;z<imp.getNSlices();z++){	

			ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(channel,z,frame)).duplicate();
			ImagePlus wrapper = new ImagePlus("", ip);	//wrapper for ProfilePlot

			for(int a=0;a<nAngles;a++){
				double theta = (a/(float)nAngles) * Math.PI;	//semicircle
				double dx = Math.cos(theta) * scale;
				double dy = Math.sin(theta) * scale;
				
				Line line = new Line(cx-dx,cy-dy, cx+dx, cx+dy);	//line through centroid
				line.setStrokeWidth(lineW);
				wrapper.setRoi(line);
				double[] values = new ProfilePlot(wrapper, true).getProfile();
				
				
				double[] profile = IntStream.range(1,values.length-1).mapToDouble(i->(values[i-1]+values[i]+values[i+1])/3f).toArray();	//smooth
				//double[] profile = values;
				
				double[] deriv = IntStream.range(0,profile.length-1).mapToDouble(i->(profile[i+1]-profile[i])/cal.pixelWidth).toArray();	//discrete differential by forward differences
				//double derivMean = Arrays.stream(deriv).sum() / (float)deriv.length;
				//double derivSD = Math.sqrt( Arrays.stream(deriv).map(d -> (d-derivMean)*(d-derivMean) ).sum() / (float)deriv.length );
				//double tol = derivMean + derivSD;
				double tol = (Arrays.stream(deriv).max().getAsDouble() - Arrays.stream(deriv).min().getAsDouble()) / 2f;
				tol = Math.max(tol, 20.0);	//50.0

				//int[] mini = MaximumFinder.findMinima(deriv, tol, true);
				int[] maxi = MaximumFinder.findMaxima(deriv, tol, true);
				//Arrays.sort(mini);	//sort into index order instead of magnitude order
				Arrays.sort(maxi);
				
				
				/*for(int j=0;j<maxi.length;j++){	//distances from max to next min
					double maxDist = maxi[j] * cal.pixelWidth;
					for(int k=0;k<mini.length;k++){
						double minDist = mini[k] * cal.pixelWidth;
						double delta = (minDist-maxDist)/2f;	//half distance between this max and the next min = candidate peak radius
						if(delta>=minD&&delta<=maxD){
							distanceList.add(delta);
							break;
						}
					}
				}*/
				for(int j=0;j<maxi.length-1;j++){		//distances from max to next max
					double delta = ((maxi[j+1]-maxi[j]) * cal.pixelWidth)/2f;	//half distance between this max and the next max = candidate peak radius
					if(delta>=minD&&delta<=maxD){
						distanceList.add(delta);
					}
				}
				

				
				/*if(z==6&&a==1){
					double[] dists = IntStream.range(0,values.length-1).mapToDouble(i->i*cal.pixelWidth).toArray();	//x-axis
					double[] minX = Arrays.stream(mini).mapToDouble(i->dists[i]).toArray();	//distances of extrema indices for plot annotations
					double[] maxX = Arrays.stream(maxi).mapToDouble(i->dists[i]).toArray();
					DefaultXYDataset dataset = new DefaultXYDataset();
					dataset.addSeries("profile", new double[][]{dists, deriv});
					makeLineChart( dataset, "profile-"+IJ.d2s(theta,2)+" "+IJ.d2s(tol,6), "dist", "v", minX, maxX );
				}*/
	
			}
		}	
			double[] radii = distanceList.stream().mapToDouble(d->d).toArray();
			makeHistogram(imp.getTitle()+" profile radii", "radius (\u00b5m)", radii);
			
		}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
	private static void makeHistogram(String title, String label, double[] values){
		if(values.length<3){
			IJ.log("< 3 values for histogram");
			return;
		}
		
		double mean = Arrays.stream(values).sum() / (float)values.length;
		Arrays.sort(values);
		double median = (values.length%2==0)? (values[values.length/2-1]+values[values.length/2+1])/2f : values[values.length/2];
		double q1 = values[(int)(values.length/4f)];
		double q3 = values[(int)(values.length*(3/4f))];
		double iqr = q3-q1;
		double iqmean = Arrays.stream(values).filter( v->v>=q1&&v<=q3 ).summaryStatistics().getAverage();
		
		double vMin = Arrays.stream(values).min().getAsDouble();
		double vMax = Arrays.stream(values).max().getAsDouble();
					
		double binW = 2*( iqr/Math.cbrt(values.length) );	//Freedman-Diaconis
		int nbins = (int) ((vMax-vMin)/binW);
		
		HistogramDataset dataset = new HistogramDataset();
		//dataset.setType(HistogramType.RELATIVE_FREQUENCY);
		dataset.setType(HistogramType.FREQUENCY);
		dataset.addSeries(label, values, nbins);
		JFreeChart chart = ChartFactory.createHistogram(title, label, "n", dataset, PlotOrientation.VERTICAL, false, true, false);
		XYPlot plot = chart.getXYPlot();
		XYBarRenderer barr = new XYBarRenderer();
		barr.setSeriesPaint(0, Color.GREEN);
		barr.setBarPainter( new StandardXYBarPainter() );
		plot.setRenderer( barr );

		

		double[] binValues = IntStream.range(0,nbins).mapToDouble( i->dataset.getX(0, i).doubleValue() ).toArray();
		double[] binCounts = IntStream.range(0,nbins).mapToDouble( i->dataset.getY(0, i).doubleValue() ).toArray();
		CurveFitter fitter = new CurveFitter(binValues, binCounts);
		fitter.doFit( CurveFitter.GAUSSIAN );	//y = a + (b-a)*exp(-(x-c)*(x-c)/(2*d*d))
		double[] params = fitter.getParams();	// c = peak x position, d = stdDev
		Path2D.Double gaussianCurve = new Path2D.Double();
		gaussianCurve.moveTo(vMin, fitter.f(vMin));
		for(double x=vMin;x<=vMax;x+=0.1d){
			double y = fitter.f(x);
			gaussianCurve.lineTo(x,y);
		}
		plot.addAnnotation( new XYShapeAnnotation(gaussianCurve,new BasicStroke(1),Color.MAGENTA) );
		
		IJ.log(title+" (n="+values.length+")");
		IJ.log("Mean Cell Radius = "+IJ.d2s(mean,3)+" \u00b5m");
		IJ.log("Median Cell Radius = "+IJ.d2s(median,3)+" \u00b5m");
		IJ.log( "Inter-Quartile Mean Cell Radius = "+IJ.d2s(iqmean,3)+" \u00b5m" );
		
		if(params[2]>0){
			IJ.log("Gaussian Fitted Radius = "+IJ.d2s(params[2],3)+" \u00b5m (r\u00b2 = "+IJ.d2s(fitter.getRSquared(),3)+")");
		}
		else{
			IJ.log("Gaussian Fit did not find a peak");
		}
		IJ.log("");

		ChartFrame frame = new ChartFrame(title+" Frequency Distribution", chart);
		frame.setSize(500, 500);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

}
