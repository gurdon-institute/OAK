package uk.ac.cam.gurdon.oak.segment2d;

import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.AutoThresholder;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

public class DoGSegmenter extends SliceSegmenter {

	public DoGSegmenter(ImageProcessor ip, int z, double minApx, int watershed) {
		super(ip, z, minApx, watershed);
	}

	@Override
	public void run() {
		ImageProcessor dogmap = map.duplicate();
		ImageProcessor dogsub = dogmap.duplicate();
		double sigma = Math.sqrt(minApx/Math.PI) / 2.0;
		dogmap.blurGaussian(sigma);
		dogsub.blurGaussian(sigma*5);
		dogmap.copyBits(dogsub, 0,0, Blitter.SUBTRACT);
		
		ImageStatistics stats = dogmap.getStatistics();
		int threshi = new AutoThresholder().getThreshold(AutoThresholder.Method.Otsu, stats.histogram );
		double thresh = ((threshi/255d) * (stats.max-stats.min)) + stats.min;
		dogmap.threshold( (int)thresh );
		
		fillHoles(dogmap);
		open(dogmap);
		
		watershed(dogmap, watershedMethod);
		
		dogmap.setThreshold(255,255, ImageProcessor.NO_LUT_UPDATE);
		Roi composite = new ThresholdToSelection().convert(dogmap);
		rois = new ShapeRoi(composite).getRois();
	}
	
}
