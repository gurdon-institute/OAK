package uk.ac.cam.gurdon.oak.segment2d;

import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;


public class LaplacianZCSegmenter extends SliceSegmenter{

	public LaplacianZCSegmenter(ImageProcessor ip, int z, double minApx, int watershed){
		super(ip,z, minApx, watershed);
	}
	
	@Override
	public void run() {
		
		int W = map.getWidth();
		int H = map.getHeight();
		
		ImageProcessor logmap = map.convertToFloatProcessor();
		ImageProcessor logsub = logmap.duplicate();
		double logSigma = Math.sqrt(minApx/Math.PI) / 2.0;
		logmap.blurGaussian(logSigma);
		logsub.blurGaussian(logSigma*1.4);
		logmap.copyBits(logsub, 0,0, Blitter.SUBTRACT);
		int r = 1;
		ByteProcessor cross = new ByteProcessor(W, H);
		double minDelta = 0.0;	//zero = no threshold
		for(int x=r;x<W-r;x++){
			for(int y=r;y<H-r;y++){
				box:
				for(int a=-r;a<=r;a+=1){
					for(int b=-r;b<=r;b+=1){
						float f0 = logmap.getf(x+a, y+b);
						float f1 = logmap.getf(x-a, y-b);
						if( f0 * f1 < 0 && Math.abs(f0 - f1) >= minDelta){	//if the signs are different and the difference is not just a small fluctuation
							cross.set(x,y,255);
							break box;
						}
					}
				}
				
			}
		}

		fillHoles(cross);
		open(cross);
		
		watershed(cross, watershedMethod);
		
		cross.setThreshold(255,255, ImageProcessor.NO_LUT_UPDATE);
		Roi composite = new ThresholdToSelection().convert(cross);
		rois = new ShapeRoi(composite).getRois();

	}
	
}
