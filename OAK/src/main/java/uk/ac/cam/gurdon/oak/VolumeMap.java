package uk.ac.cam.gurdon.oak;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.StackStatistics;

public class VolumeMap {

	private ImageStack stack;
	private Roi[] rois;
	private AtomicLong nVoxels;
	
	
	private class Slice implements Runnable{
		private ImageProcessor ip;
		private int z;
		private double thresh;
		private Roi roi;
		
		
		private Slice(ImageProcessor ip, int z, double thresh){
			this.ip = ip;
			this.z = z;
			this.thresh = thresh;
		}
		
		@Override
		public void run() {
			
			int W = ip.getWidth();
			int H = ip.getHeight();
			ByteProcessor bp = new ByteProcessor(W,H);
			long n = 0;
			for(int i=0;i<W*H;i++){
				float value = ip.getf(i);
				if(value>=thresh){
					bp.set(i, 255);
					n++;
				}
			}
			nVoxels.getAndAdd(n);
			bp.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
			roi = new ThresholdToSelection().convert(bp);

		}
		
	}
	
	
	public void execute(){
		
		StackStatistics stats = new StackStatistics(new ImagePlus("wrapper", stack));
		double threshi = new AutoThresholder().getThreshold(AutoThresholder.Method.Li, stats.histogram);
		double thresh = ((threshi/255d) * (stats.max-stats.min)) + stats.min;
		
		int nThreads = Runtime.getRuntime().availableProcessors() - 1;
		ExecutorService executor = Executors.newFixedThreadPool(nThreads);
		ArrayList<Slice> slices = new ArrayList<Slice>();
		for(int z=1;z<=stack.getSize();z++){
			ImageProcessor ip = stack.getProcessor(z);
			Slice slice = new Slice(ip, z, thresh);
			slices.add(slice);
			executor.submit(slice);
		}
		executor.shutdown();
		try {
			executor.awaitTermination(7L, TimeUnit.DAYS);
		} catch (InterruptedException ie) {
			System.out.println(ie.toString());
		}
		
		for(Slice slice:slices){
			rois[slice.z] = slice.roi;
		}
		
	}
	
	public VolumeMap(ImageStack stack){
		this.stack = stack;
		this.rois = new Roi[stack.getSize()+1];
		this.nVoxels = new AtomicLong();
	}

	public Roi[] getRois() {
		return rois;
	}

	public long getNVoxels() {
		return nVoxels.get();
	}
	
}
