package uk.ac.cam.gurdon.oak.segment2d;

import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

public class HKMSegmenter extends SliceSegmenter {

	private double minArea;
	private double maxArea;

	public HKMSegmenter(ImageProcessor ip, int z, double minArea, double maxArea, int watershed) {
		super(ip, z, minArea, watershed);
		this.minArea = minArea;	//px²
		this.maxArea = maxArea;
	}

	@Override
	public void run() {
		map = super.map.duplicate();
		ImageProcessor sub = map.duplicate();
		double dogSigma = Math.sqrt(minArea/Math.PI) / 2.0;
		map.blurGaussian(dogSigma);
		sub.blurGaussian(dogSigma*5);
		map.copyBits(sub, 0,0, Blitter.SUBTRACT);
		
		int W = map.getWidth();
		int H = map.getHeight();
		ImageStatistics stats = map.getStatistics();
		double[] levels = new HistogramCluster(map).getLevels(16, 0);
	//System.out.println(Arrays.toString(levels));
		ByteProcessor gotMask = new ByteProcessor(W,H);
		gotMask.setColor(255);

		for(double level:levels){
			map.setThreshold(level, stats.max, ImageProcessor.NO_LUT_UPDATE);
			Roi composite = new ThresholdToSelection().convert(map);
			Roi[] levelRois = new ShapeRoi(composite).getRois();
	//System.out.println(level+" : "+levelRois.length);
			for(Roi roi:levelRois){
				gotMask.setRoi(roi);
				ImageStatistics roiStats = gotMask.getStatistics();
				if(roiStats.mean == 0 && roiStats.area>=minArea && roiStats.area <= maxArea){	//px²
					gotMask.fill(roi);
				}
			}
		}
		
		
		fillHoles(gotMask);
		open(gotMask);
		
		watershed(gotMask, watershedMethod);

		gotMask.setThreshold(255,255, ImageProcessor.NO_LUT_UPDATE);
		Roi composite = new ThresholdToSelection().convert(gotMask);
		rois = new ShapeRoi(composite).getRois();
	}


}
