package uk.ac.cam.gurdon.oak.analysis;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;

import uk.ac.cam.gurdon.oak.Cell;


public class IntensityDistribution {

	public static void run(ArrayList<Cell> cells, String featureKey){
		try{
			
			double[] values = cells.stream().mapToDouble( c->c.getValue(featureKey) ).map( v->Double.isNaN(v)?0.0:v ).toArray();
			
			if(values.length==0) return;
			int nbins = (int) Math.sqrt(values.length);
			HistogramDataset dataset = new HistogramDataset();
			dataset.setType(HistogramType.RELATIVE_FREQUENCY);
			dataset.addSeries(featureKey, values, nbins);
			JFreeChart chart = ChartFactory.createHistogram(featureKey+" Distribution", featureKey, "f", dataset, PlotOrientation.VERTICAL, true, true, false);
			XYPlot plot = chart.getXYPlot();
			XYBarRenderer barr = new XYBarRenderer();
			barr.setSeriesPaint(0, Color.BLACK);
			barr.setBarPainter( new StandardXYBarPainter() );
			plot.setRenderer( barr );
			
			
			LegendItemCollection legend = new LegendItemCollection();
			
			/*double mean = Arrays.stream(values).sum()/(float)(values.length);
			plot.addAnnotation( new XYLineAnnotation(mean,-100,mean,100,new BasicStroke(1),Color.RED) );
			legend.add( new LegendItem("Mean", Color.RED) );
			
			double stdDev = Arrays.stream(values).map(v->(v-mean)*(v-mean)).sum()/(float)(values.length-1);
			double sem = stdDev/Math.sqrt(values.length);
			plot.addAnnotation( new XYLineAnnotation(mean+sem,-100,mean+sem,100,new BasicStroke(1),Color.GREEN) );
			plot.addAnnotation( new XYLineAnnotation(mean-sem,-100,mean-sem,100,new BasicStroke(1),Color.GREEN) );
			legend.add( new LegendItem("SEM", Color.GREEN) );
			
			Arrays.sort(values);
			double median = values[(int)(values.length/2f)];
			double q1 = values[(int)(values.length/4f)];
			double q3 = values[(int)(values.length*(3/4f))];
			plot.addAnnotation( new XYLineAnnotation(median,-100,median,100,new BasicStroke(1),Color.BLUE) );
			plot.addAnnotation( new XYLineAnnotation(q1,-100,q1,100,new BasicStroke(1),Color.BLUE) );
			plot.addAnnotation( new XYLineAnnotation(q3,-100,q3,100,new BasicStroke(1),Color.BLUE) );
			legend.add( new LegendItem("Q1, Median, Q3", Color.BLUE) );
			
			double V95 = values[(int)(values.length*0.95)];
			plot.addAnnotation( new XYLineAnnotation(V95,-100,V95,100,new BasicStroke(1),Color.MAGENTA) );
			legend.add( new LegendItem("95%", Color.MAGENTA) );
			
			double iqr = q3-q1;
			double tukeyK = 1.5;
			double tukeyMin = q1-tukeyK*iqr;
			plot.addAnnotation( new XYLineAnnotation(tukeyMin,-100,tukeyMin,100,new BasicStroke(1),Color.YELLOW) );
			double tukeyMax = q3+tukeyK*iqr;
			plot.addAnnotation( new XYLineAnnotation(tukeyMax,-100,tukeyMax,100,new BasicStroke(1),Color.YELLOW) );
			legend.add( new LegendItem("Tukey Limit (K=1.5)", Color.YELLOW) );
			
			IJ.log(featureKey);
			IJ.log("Q3 = "+IJ.d2s(q3,2)+" \u00b5m");
			IJ.log("Tukey Limit = "+IJ.d2s(tukeyMax,2)+" \u00b5m");
			IJ.log("95% = "+IJ.d2s(V95,2)+" \u00b5m");
			IJ.log("");
			*/
			
			plot.setFixedLegendItems(legend);
			

			ChartFrame frame = new ChartFrame(featureKey+" Distribution", chart);
			frame.setSize(500, 500);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
			

			
		}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
}
