package uk.ac.cam.gurdon.oak.segment2d;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Comparator;

import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.Wand;
import ij.plugin.filter.EDM;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

public abstract class SliceSegmenter implements Runnable {

	public static final String[] METHODS = new String[]{"Laplacian zero-crossings", "DoG", "HKM", "Adaptive Local Thresholding"};
	public static final int LAPLACIAN_ZC = 0;
	public static final int DOG = 1;
	public static final int HKM = 2;
	public static final int ALT = 3;
	
	public static final String[] WATERSHEDS = new String[]{"None", "Mild", "Standard", "Strong", "Irregular"};
	public static final int NONE = 0;
	public static final int MILD = 1;
	public static final int STANDARD = 2;
	public static final int STRONG = 3;
	public static final int IRREGULAR = 4;
	
	ImageProcessor map;
	int z;
	double minApx;
	int watershedMethod;
	Roi[] rois;

	
	public SliceSegmenter(ImageProcessor ip, int z, double minApx, int watershed){
		this.map = ip;
		this.z = z;
		this.minApx = minApx;
		this.watershedMethod = watershed;
	}
	
	public abstract void run();

	public Roi[] getRois(){
		return rois;
	}

	public int getZ(){
		return z;
	}

	public String toString(){
		return getClass()+" "+map.toString()+" Z"+z;
	}
	
	public static void fillHoles(ImageProcessor ip){
		int W = ip.getWidth();
		int H = ip.getHeight();
		FloodFiller ff = new FloodFiller(ip);
		ip.setColor(127);
		int background = 0;
		for(int y=0;y<H;y++){
		    if(ip.getPixel(0,y)==background) ff.fill(0, y);
		    if(ip.getPixel(W-1,y)==background) ff.fill(W-1, y);
		}
		for(int x=0;x<W;x++){
		    if(ip.getPixel(x,0)==background) ff.fill(x, 0);
		    if(ip.getPixel(x,H-1)==background)	ff.fill(x, H-1);
		}
		for(int i=0;i<W*H;i++){
			if(ip.get(i)==127) ip.set(i, 0);
			else ip.set(i, 255);
		}
	}
	
	public static void open(ImageProcessor ip){
		ip.erode();
		ip.dilate();
	}
	
	public static void close(ImageProcessor ip){
		ip.dilate();
		ip.erode();
	}
	
	public void watershed(ImageProcessor ip, int method){
		switch(method){
			case NONE:
				break;
			case MILD:
				watershed(ip, 5.0);
				break;
			case STANDARD:
				watershed(ip, 0.5);	//0.5 is the ImageJ default
				break;
			case STRONG:
				watershed(ip, 0.05);
				break;
			case IRREGULAR:
				irregularWatershed(ip, 0.25, minApx);
				break;
			default:
				break;
		}
	}
	
	public static void watershed(ImageProcessor ip, double tolerance){
		FloatProcessor edm = new EDM().makeFloatEDM(ip.convertToByte(false), 0, false);
		ImageProcessor maxIp = new MaximumFinder().findMaxima(edm, tolerance, ImageProcessor.NO_THRESHOLD, MaximumFinder.SEGMENTED, false, true);
		if (maxIp != null){
			ip.copyBits(maxIp, 0, 0, Blitter.AND);
		}
	}
	
	public static void irregularWatershed(ImageProcessor ip, double tolerance, double minApx){
		ImageProcessor mask = ip.duplicate();

		ImageProcessor cutip = mask.duplicate();
		ImageProcessor wscuts = mask.duplicate();
		watershed(wscuts, tolerance);
		cutip.copyBits(wscuts, 0,0, Blitter.XOR);
		
		cutip.setThreshold(255,255, ImageProcessor.NO_LUT_UPDATE);
		Roi[] cuts = new ShapeRoi(new ThresholdToSelection().convert(cutip)).getRois();
		
		Arrays.sort(cuts, new Comparator<Roi>(){	//sort by size - test shortest first and rejoin if small part created
			public int compare(Roi roi0, Roi roi1){
				return new Double(roi0.getLength()).compareTo( new Double(roi1.getLength()) );
			}
		});

		Wand wand = new Wand(mask);	//get areas from the uncut mask
		for (Roi cut : cuts){
			ImageStatistics cutStats = cut.getStatistics();
			wand.autoOutline( (int)cutStats.xCentroid, (int)cutStats.yCentroid );
			PolygonRoi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, PolygonRoi.POLYGON);
			ShapeRoi hull = new ShapeRoi(roi.getConvexHull());
			
			double maxLength = (roi.getLength() / (2*Math.PI));	//radius of circle with same area
			
			double cutLength = (cut.getLength() / 2.0) - 1;
			if(cutLength <= maxLength){
				ip.setColor(0);
				ip.fill(cut);					// cut the mask
				
				ip.resetRoi();
				ip.setThreshold(255,255, ImageProcessor.NO_LUT_UPDATE);
				Roi composite = new ThresholdToSelection().convert(ip);
				composite = new ShapeRoi(composite).and(new ShapeRoi(roi));
				Roi[] parts = new ShapeRoi(composite).getRois();
				for(Roi part : parts){
					Rectangle rect = part.getBounds();
					if(hull.contains((int)(rect.x+(rect.width/2.0)), (int)(rect.y+(rect.height/2.0)))){	//if this is part of the cut Roi
						double area = part.getStatistics().area;
						if(area < minApx){	//rejoin small parts
							ip.setColor(255);
							ip.fill(cut);
						}
					}
				}
			}
		}

	}
	
}