package uk.ac.cam.gurdon.oak.segment2d;

import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.filter.RankFilters;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;


public class ALTSegmenter extends SliceSegmenter{
	
	private double radius;
	
	
	public ALTSegmenter(ImageProcessor ip, int z, double radius, double minApx, int watershed) {
		super(ip, z, minApx, watershed);
		this.radius = radius;
	}

	public void run(){

			int W = map.getWidth();
			int H = map.getHeight();
			//ImageStatistics allStats = map.getStatistics();
			
			//Phansalkar et. al, Adaptive local thresholding for detection of nuclei in diversity stained cytology images. (2011)
			double k = 0.1;		//constant, Phansalkar et. al say that this should be in the range 0.2-0.5 and 0.25 gives the best results in RGB cytological images with dark signal on light background
			double r = 0.5;		//dynamic range of standard deviation, = 0.5 for a normalised image
			double p = 3.0;		//decides the magnitude to which the exponential term will affect the threshold
			double q = 10.0;	//chosen so that above a particular value of the local mean, the exponential term becomes negligible and the equation behaves just like Sauvola's equation
			
			RankFilters rf = new RankFilters();
			
			FloatProcessor normMap = map.convertToFloatProcessor();
			double sigmaPx = Math.sqrt(minApx/Math.PI) / 2.0;
			normMap.blurGaussian(sigmaPx);
			ImageStatistics stats = normMap.getStatistics();
			
			normMap.subtract(stats.min);
			normMap.multiply(1.0/(stats.max-stats.min));	//normalise

			FloatProcessor meanip = (FloatProcessor) normMap.duplicate();
			rf.rank(meanip, radius, RankFilters.MEAN);
			
			FloatProcessor sdip = (FloatProcessor) normMap.duplicate();
			rf.rank(sdip, radius, RankFilters.VARIANCE);
			sdip.sqrt();
			
			ByteProcessor bp = new ByteProcessor(W,H);
			FloatProcessor thresholds = new FloatProcessor(W,H);
			for(int x=0;x<W;x++){
				for(int y=0;y<H;y++){
					double mean = meanip.getf(x,y);
					double sd = sdip.getf(x,y);
					double localThreshold = mean * (1 + p * Math.exp(-q * mean) + k * ((sd / r) - 1)) ;	//Phansalkar
					
					int pixel = ( normMap.getf(x,y) >= localThreshold  ) ? 255 : 0;
					bp.set(x,y, pixel);
					
					thresholds.setf(x,y, (float)localThreshold);
				}
			}
			
			//new ImagePlus("", thresholds).show();
			
			fillHoles(bp);
			open(bp);
			
			watershed(bp, watershedMethod);

			bp.setThreshold(255,255, ImageProcessor.NO_LUT_UPDATE);
			Roi composite = new ThresholdToSelection().convert(bp);
			rois = new ShapeRoi(composite).getRois();

	}

}
