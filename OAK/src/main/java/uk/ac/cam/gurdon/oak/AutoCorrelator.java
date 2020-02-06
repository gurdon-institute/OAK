package uk.ac.cam.gurdon.oak;

import java.awt.Color;
import java.awt.Dimension;
import java.util.Arrays;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.DefaultXYDataset;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.FHT;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

public class AutoCorrelator {

	
	private static double PCC(ImageProcessor A, ImageProcessor B) throws Exception{
		ImageStatistics statsA = A.getStatistics();
		ImageStatistics statsB = B.getStatistics();
		int W = A.getWidth();
		int H = A.getHeight();
		if(B.getWidth()!=W||B.getHeight()!=H){
			throw new Exception(A+" and "+B+" are not the same size");
		}
		double covar = 0;
		double varA = 0;
		double varB = 0;
		for(int i=0;i<W*H;i++){
			covar += (A.get(i)-statsA.mean)*(B.get(i)-statsB.mean);
			varA += (A.get(i)-statsA.mean)*(A.get(i)-statsA.mean);
			varB += (B.get(i)-statsB.mean)*(B.get(i)-statsB.mean);
		}
		double pcc = covar/Math.sqrt(varA*varB);
		return pcc;
	}
	
	private static double fwhm(double[] x, double[] y) {
		double minY = Arrays.stream(y).min().getAsDouble();
		double maxY = Arrays.stream(y).max().getAsDouble();
		double halfMax =  ((maxY-minY) / 2.0) + minY;
		double negMind = Double.POSITIVE_INFINITY;
		double posMind = Double.POSITIVE_INFINITY;
		int negi = -1;
		int posi = -1;
		for(int i=0;i<x.length;i++){
			double d = Math.abs(y[i]-halfMax);
			if(x[i]<0 && d<negMind){
				negMind = d;
				negi = i+1;
			}
			else if(x[i]>0 && d<posMind){
				posMind = d;
				posi = i-1;
			}
		}
		double fwhm = Math.abs( x[posi]-x[negi] );
		return fwhm;
	}
	
	private static void plot( DefaultXYDataset col, String title, String X, String Y) throws Exception{
		JFreeChart chart = ChartFactory.createXYLineChart(title, X, Y, col, PlotOrientation.VERTICAL, true, true, true);
		XYPlot plot = chart.getXYPlot();
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);
		plot.setBackgroundPaint(Color.BLACK);
    	plot.setDomainGridlinePaint(Color.GRAY);
    	plot.setRangeGridlinePaint(Color.GRAY);
		
        XYItemRenderer render = plot.getRenderer();
        render.setSeriesPaint(0, Color.MAGENTA);
        render.setSeriesPaint(1, Color.CYAN);

        ChartFrame frame = new ChartFrame(title,chart);
        frame.pack();
		frame.setSize( new Dimension(500, 500) );
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
	
	public static void calculate(ImagePlus imp){

		try{
			Calibration cal = imp.getCalibration();
			ImageProcessor ip = imp.getProcessor();	//currently displayed channel/slice
			
			double maxOffsetCal = 20.0;		//microns
			int maxOffset = (int) (maxOffsetCal/cal.pixelWidth);	//px
			double[] offset = new double[maxOffset*2+1];
			double[] correlationX = new double[maxOffset*2+1];
			double[] correlationY = new double[maxOffset*2+1];
			for(int d=-maxOffset;d<=maxOffset;d++){
				ImageProcessor ipoffX = ip.duplicate();
				ipoffX.translate(d,0);
				ImageProcessor ipoffY = ip.duplicate();
				ipoffY.translate(0,d);
				offset[d+maxOffset] = d*cal.pixelWidth;
				correlationX[d+maxOffset] = PCC(ip, ipoffX);
				correlationY[d+maxOffset] = PCC(ip, ipoffY);
			}
			
			DefaultXYDataset dataset = new DefaultXYDataset();
			dataset.addSeries("PCC X-Offset", new double[][]{offset, correlationX});
			dataset.addSeries("PCC Y-Offset", new double[][]{offset, correlationY});
			plot(dataset, "Autocorrelation "+imp.getTitle(), "Offset ("+cal.getUnit()+")", "r");
			
			double rX = fwhm(offset, correlationX)/2f;
			double rY = fwhm(offset, correlationY)/2f;
			
			IJ.log("Radius from autocorrelation: "+imp.getTitle());
			IJ.log("X offset HWHM = "+IJ.d2s(rX,2)+" \u00b5m");
			IJ.log("Y offset HWHM = "+IJ.d2s(rY,2)+" \u00b5m");
	
			
			FHT fht = new FHT();
			float[] floatX = new float[correlationX.length];
			for(int i=0;i<correlationX.length;i++) floatX[i] = (float) correlationX[i];
			float[] famp = fht.fourier1D(floatX, FHT.HAMMING);
			
			/*System.out.println("frequency,amplitude,wavelength");
			for(int i=2;i<famp.length;i++){	//0 is DC, 1 is sampling frequency
				double f = i/(2*famp.length*cal.pixelWidth);
				double lambda = 1f/f;
				System.out.println(f+","+famp[i]+","+lambda);
			}*/
			int maxi = 2;
			for(int i=2;i<famp.length;i++){	//skip DC and sampling frequencies
				if(famp[i]>famp[maxi]) maxi = i;
			}
			double f = maxi/(2*famp.length*cal.pixelWidth);		//results[i] corresponds to a frequency of i/(2*results.length*dx) (FHT javadoc)
			double lambda = 1d/f;
			IJ.log("Principal Power Spectral Density Wavelength = "+IJ.d2s( Math.sqrt(lambda), 2)+" \u00b5m");
			
/*//////////////////////////////////////////////////////////////////////////////	TODO?
			IJ.log("Organoid Volume = "+volume+" \u00b5m\u00b2");
			double maxHWHM = Math.max(rX, rY);
			double dcv = volume * maxHWHM;
			IJ.log("Density-Corrected Organoid Volume = "+dcv+" \u00b5m\u2074");
			double dcs = Math.cbrt(volume) * maxHWHM;
			IJ.log("Density-Corrected Organoid Size = "+dcs+" \u00b5m");
			double cc = Math.cbrt(volume) / maxHWHM;
			IJ.log("Coefficient of Celliness = "+cc+" \u00b5m");
			
			double rintegral = Arrays.stream(correlationX).sum();
			IJ.log("Autocorrelation Integral = "+rintegral);
			double something = Math.cbrt(volume) / rintegral;
			IJ.log("something = "+something);
			
			double foo = maxHWHM / Math.cbrt(volume);
			IJ.log("foo = "+foo);
			
//////////////////////////////////////////////////////////////////////////////
*/			IJ.log("");
			
		}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
	
}
