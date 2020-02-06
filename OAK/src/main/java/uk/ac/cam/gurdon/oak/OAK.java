package uk.ac.cam.gurdon.oak;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.TextRoi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.GaussianBlur3D;
import ij.plugin.HyperStackConverter;
import ij.plugin.RoiEnlarger;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.AutoThresholder;
import ij.process.Blitter;
import ij.process.EllipseFitter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;
import uk.ac.cam.gurdon.oak.analysis.Render3D;
import uk.ac.cam.gurdon.oak.gui.AnalysisPanel;
import uk.ac.cam.gurdon.oak.gui.ChannelPanel;
import uk.ac.cam.gurdon.oak.gui.OAK_GUI;
import uk.ac.cam.gurdon.oak.gui.TargetTable;
import uk.ac.cam.gurdon.oak.segment2d.ALTSegmenter;
import uk.ac.cam.gurdon.oak.segment2d.DoGSegmenter;
import uk.ac.cam.gurdon.oak.segment2d.HKMSegmenter;
import uk.ac.cam.gurdon.oak.segment2d.LaplacianZCSegmenter;
import uk.ac.cam.gurdon.oak.segment2d.SliceSegmenter;



@Plugin(type = Command.class, menuPath = "Plugins>OAK")
public class OAK implements Command{

	private static final BasicStroke DOTTED_STROKE = new BasicStroke( 1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1f, new float[]{8f, 8f}, 8f );
	private static final BasicStroke MEMBRANE_STROKE = new BasicStroke( 0.5f );
	private static final Font LABELFONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
	
	private OAK_GUI gui;
	
	private ArrayList<Cell> cells;
	private Color[] colours;
	private ShapeRoi[] excludeRois;
	private Roi[] sliceRois;
	private int roiHash = -1;
	private int cellHash = -1;
	
	private Integer nucleusC;
	private double volume, cellR, cytoExpR, maxCorrection;

	
	public void run() {
		gui = new OAK_GUI(this);
		gui.display();
	}

	public void setNucleusC(int nucleusC){
		this.nucleusC = nucleusC;
	}
	public void setCellRadius(double r){
		this.cellR = r;
	}
	public void setCytoplasmExpRadius(double r){
		this.cytoExpR = r;
	}
	public void setMaxCorrectionFactor(double mc) {
		this.maxCorrection = mc;
	}
	
	public Roi[] organoidVolume(boolean getCount){
		ImagePlus imp = IJ.getImage();
		int t = imp.getFrame();
		return organoidVolume(getCount, t);
	}
	
	public Roi[] organoidVolume(boolean getCount, int t){
		try{
			ImagePlus imp = IJ.getImage();
			
			gui.setStatus("Mapping organoid volume..."+(imp.getNFrames()>1?" T"+t:""));

			Calibration cal = imp.getCalibration();
			imp.killRoi();
	
			int C = imp.getNChannels();
			int Z = imp.getNSlices();
			int T = imp.getNFrames();
			Duplicator dup = new Duplicator();
			ImagePlus volMap = dup.run(imp, nucleusC,nucleusC, 1,Z, t,t);
	
			double sigmaXY = cellR/cal.pixelWidth;	//pixels
			double sigmaZ = sigmaXY*(cal.pixelWidth / cal.pixelDepth);
			GaussianBlur3D.blur(volMap, sigmaXY, sigmaXY, sigmaZ);
			
			ImageStack stack = volMap.getImageStack();
			
			//depth intensity correction
			double[] ratios = null;
			if (maxCorrection>1){
				double[] means = IntStream.range(1,Z+1).mapToDouble(z->stack.getProcessor(z).getStatistics().mean).toArray();
				double max = Arrays.stream(means).max().getAsDouble();
				ratios = Arrays.stream(means).map(v->max/v).toArray();
				for(int z=1;z<=Z;z++){
					if(ratios[z-1]>maxCorrection){
						ratios[z-1] = maxCorrection;
					}
					ImageProcessor ip = stack.getProcessor(z);
					ip.multiply(ratios[z-1]);
					stack.setProcessor(ip, z);
				}
			}
			
			VolumeMap vm = new VolumeMap(stack);
			vm.execute();
			Roi[] rois = vm.getRois();
			long nVoxels = vm.getNVoxels();
			
			volume = (nVoxels*cal.pixelWidth*cal.pixelHeight*cal.pixelDepth);
			double cellV = (4.0/3.0)*Math.PI*(cellR*cellR*cellR);

			for(int z=1;z<=Z;z++){
				if(rois[z]==null||rois[z].getLength()==0){
					continue;
				}
				if(C>1||T>1){
					rois[z].setPosition(0, z, t);
				}
				else{
					rois[z].setPosition(z);
				}
			}
				
			if(getCount){
				Overlay ol = new Overlay();
				for(int z=1;z<=Z;z++){
					rois[z].setStrokeColor(Color.YELLOW);
					ol.add(rois[z]);
				}
				imp.setOverlay(ol);
				IJ.log(imp.getTitle()+" C"+nucleusC);
				if(ratios!=null){
					String correction = Arrays.stream(ratios).mapToObj(r->(String)IJ.d2s(r,2)).collect(Collectors.joining(","));
					IJ.log("Depth correction factors = "+correction );
				}
				IJ.log("Signal volume = "+IJ.d2s(volume,3)+" "+cal.getUnit()+"\u00B3");
				IJ.log("Estimated cell count for cell radius "+IJ.d2s(cellR,3)+" \u00b5m = "+IJ.d2s(volume/cellV, 3));
				IJ.log("");
			}
			
			return rois;
		}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		return null;
	}
	
	private int getRoiHash(ImagePlus imp, int c, int t){
		int prime = 101;
		int hash = prime * (imp.getTitle().hashCode() +  new Integer(c).hashCode() +  new Integer(t).hashCode() + new Integer((int)(cellR*100)).hashCode());
		return hash;
	}
	
	private int getCellHash(ImagePlus imp, double cellRadius, int nucC, int t, int segMethod, int watershed, int startZ, int endZ){
		int prime = 487;
		int hash = prime * ( imp.getTitle().hashCode() + new Integer(nucC).hashCode() + new Integer(segMethod).hashCode() + new Integer(t).hashCode() + new Integer(watershed).hashCode() );
		hash += new Double(cellRadius).hashCode();
		hash += 3*startZ + 7*endZ;
		
		//System.out.println( imp.getTitle().hashCode() +" "+ new Integer(nucC).hashCode() +" "+ new Integer(segMethod).hashCode() +" "+ new Integer(t).hashCode() +" "+ new Integer(watershed).hashCode() );
		//System.out.println( imp.getTitle()+" "+cellRadius+" "+nucC+" "+t+" "+segMethod+" "+watershed+" "+startZ+" "+endZ+" -> "+hash );
		
		return hash;
	}
	
	public void segmentCells3D(int method, int watershed, boolean preview) {
		try{
			gui.setStatus(preview?"Previewing ":"Segmenting with "+SliceSegmenter.METHODS[method]);
			
			ImagePlus imp = IJ.getImage();
			ImageStack stack = imp.getImageStack();
			Calibration cal = imp.getCalibration();
			int C = imp.getNChannels();
			int Z = imp.getNSlices();
			int T = imp.getNFrames();

			if(nucleusC>C){
				IJ.error("No C"+nucleusC+" in "+imp.getTitle());
				return;
			}

			double cellRpx = cellR/cal.pixelWidth;
			double cytoExpRpx = cytoExpR/cal.pixelWidth;
			double minAreaPx = Math.PI*(cellRpx*cellRpx) / 2.0; //px²
			double maxAreaPx = minAreaPx * 8;
			
			int nThreads = Runtime.getRuntime().availableProcessors() - 1;
			
			
			int startZ = 1;
			int endZ = Z;
			int startT = 1;
			int endT = T;
			if(preview){
				startZ = imp.getSlice();
				endZ = startZ;
				startT = imp.getFrame();
				endT = startT;
			}
			
			Overlay ol = new Overlay();
			ResultsTable rt = new ResultsTable();
			rt.showRowNumbers(false);
			
			for(int t=startT;t<=endT;t++){
				
				int currentCellHash = getCellHash(imp, cellR, nucleusC, t, method, watershed, startZ, endZ);
				boolean doSeg = true;
				if(!preview && currentCellHash==cellHash){
					doSeg = false;	//if the results of the current parameters for this frame are already in ArrayList<Cell> cells, don't segment again
				}
	//System.out.println(doSeg+" because "+preview+" and "+currentCellHash+" : "+cellHash);
				cellHash = currentCellHash;
				
				int currentRoiHash = getRoiHash(imp, nucleusC, t);
				if(sliceRois==null||roiHash!=currentRoiHash){
					sliceRois = organoidVolume(false, t);
					roiHash = currentRoiHash;
				}
				for(Roi sliceRoi:sliceRois){
					if(sliceRoi==null){
						//System.out.println("null Roi");
						continue;
					}
					sliceRoi.setStrokeColor(Color.BLUE);
					sliceRoi.setStrokeWidth(2);
					ol.add(sliceRoi);
				}
				
				if(doSeg){	//segment cells if they don't already exist with the same hash
					ExecutorService executor = Executors.newFixedThreadPool(nThreads);
					ArrayList<SliceSegmenter> sslist = new ArrayList<SliceSegmenter>();

					for(int z=startZ;z<=endZ;z++){
						ImageProcessor ip = imp.getImageStack().getProcessor(imp.getStackIndex(nucleusC,z,t));			
						SliceSegmenter ss;
						switch(method){
							case SliceSegmenter.LAPLACIAN_ZC:
								ss = new LaplacianZCSegmenter(ip, z, minAreaPx, watershed);
								break;
							case SliceSegmenter.DOG:
								ss = new DoGSegmenter(ip, z, minAreaPx, watershed);
								break;
							case SliceSegmenter.HKM:
								ss = new HKMSegmenter(ip, z, minAreaPx, maxAreaPx, watershed);
								break;
							case SliceSegmenter.ALT:
								ss = new ALTSegmenter(ip, z, cellRpx/2.0, minAreaPx, watershed);
								break;
							default:
								throw new IllegalArgumentException("Unknown segmentation method: "+method);
						}
						
						sslist.add(ss);
						try{
							executor.submit(ss);
						} catch (RejectedExecutionException re) {
							System.out.print(re.toString()+"\nExecutor Rejected "+ss.toString()+"\n"+Arrays.toString(re.getStackTrace()).replace(",","\n"));
						}
					}
					gui.setStatus("Executing "+sslist.size()+" segmenters"+(T>1?" (T"+t+")":"")+" on "+nThreads+" threads...");
					executor.shutdown();
					try {
						executor.awaitTermination(7L, TimeUnit.DAYS);
					} catch (InterruptedException ie) {
						System.out.println(ie.toString());
					}
					
					ImagePlus nucimp = new Duplicator().run(imp, nucleusC,nucleusC, 1,Z, t,t);
					StackStatistics stackStats = new StackStatistics(nucimp);
					nucimp.close();
					int threshi = new AutoThresholder().getThreshold(AutoThresholder.Method.Huang, stackStats.histogram );
					double minStackThreshold = ((threshi/255d) * (stackStats.max-stackStats.min)) + stackStats.min;		//minimum required nucleus mean
					
					
					ArrayList<Roi> roiList = new ArrayList<Roi>();
					
					
					for(SliceSegmenter ss : sslist){
						Roi[] rois = ss.getRois();
						if(rois==null) continue;
						Roi sliceRoi = null;
						if(sliceRois!=null&&ss!=null&&sliceRois[ss.getZ()]!=null){
							sliceRoi = sliceRois[ss.getZ()];
						}
						else{
							continue;
						}
						for(Roi roi:rois){
							ImageProcessor ip = stack.getProcessor(imp.getStackIndex(nucleusC,ss.getZ(),t));
							ip.setRoi(roi);
							ImageStatistics ipStats = ip.getStatistics();
							ImageStatistics roiStats = roi.getStatistics();
							
							if(C>1||T>1){
								roi.setPosition(0, ss.getZ(), t);
							}
							else{
								roi.setPosition(ss.getZ());
							}
							
							//exclude if not inside signal volume or below stack minimum threshold
							if( !sliceRoi.contains((int)(roiStats.xCentroid), (int)(roiStats.yCentroid)) || ipStats.mean<minStackThreshold){
								if(preview){
									roi.setStrokeColor(Color.RED);
									ol.add(roi);
								}
								continue;
							}
	
							
							double perim = roi.getLength();
							double area = roiStats.area;
							double circ = Math.min( 1.0, 4*Math.PI*(area/(perim*perim)) );
							double minCirc = 0.2;
							if(area>=minAreaPx && area<=maxAreaPx && circ>=minCirc){
								roiList.add( roi );
							}
							else{
								if(preview){
									roi.setStrokeColor(Color.RED);
									ol.add(roi);
								}
							}
							gui.setStatus( "Got "+roiList.size()+" ROIs" );
						}
					}
					
					/*if(true){
						Overlay test = new Overlay();
						for(Roi roi : roiList){
							test.add(roi);
						}
						imp.setOverlay(test);
						return;
					}*/
					
					gui.setStatus( "Reconstructing "+roiList.size()+" ROIs..." );
					
					cells = new CellVolumiser(cal, cellR).get3DCells(roiList, gui);
					
					gui.setStatus("Got "+cells.size()+" cells"+(T>1?" T"+t:"") );
					
					excludeRois = new ShapeRoi[Z+1];	// areas to exclude from surrounding cytoplasm measurements in each slice
					for(int z=1;z<=Z;z++){
						gui.setStatus( "Making filter "+IJ.d2s((z/(float)Z)*100, 0)+" %"+(T>1?" T"+t:"") );
						ShapeRoi ex = new ShapeRoi(new Roi(0,0,imp.getWidth(),imp.getHeight()));
						if(sliceRois[z]!=null){
							ex = ex.not(new ShapeRoi(sliceRois[z]));	// area not in the slice Roi
						}
						
						for(Cell cell : cells){
							for(Roi nuc : cell.rois){
								if(nuc.getZPosition()==z){
									ex = ex.or( new ShapeRoi(nuc) );	//area inside a nucleus
								}
							}
						}
						
						excludeRois[z] = ex;
					}

				}
				
				ChannelPanel cp = gui.getChannelPanel();
				
				ShapeRoi[][] membraneRois = new ShapeRoi[C+1][Z+1];	//get whole field membrane Rois for each slice
				for(int chan=1;chan<=C;chan++){
					if(cp.doMembrane(chan)){
						
						double sigma = cellR/5.0;
						double sigmaPx = sigma/cal.pixelWidth;
						
						ImageStack rawStack = imp.getImageStack();
						ImageStack membraneStack = new ImageStack(imp.getWidth(),imp.getHeight());
						for(int z=1;z<=Z;z++){
							gui.setStatus("Mapping membrane C"+chan+" "+IJ.d2s((z/(float)Z)*100, 0)+" %"+(T>1?" T"+t:""));
							ImageProcessor ip = rawStack.getProcessor( imp.getStackIndex(chan,z,t) ).duplicate();
							ImageProcessor sub = ip.duplicate();
							ip.blurGaussian(sigmaPx);
							sub.blurGaussian(1.4*sigmaPx);
							ip.copyBits(sub, 0,0, Blitter.SUBTRACT);
							membraneStack.addSlice("Z"+z, ip);
						}
						
						ImageStatistics stats = new StackStatistics( new ImagePlus("wrapper",membraneStack) );
						int ti = new AutoThresholder().getThreshold(AutoThresholder.Method.Triangle, stats.histogram );
						double thresh = ((ti/255d) * (stats.max-stats.min)) + stats.min;
						for(int z=1;z<=Z;z++){
							ImageProcessor ip = membraneStack.getProcessor(z);
							ip.setThreshold(thresh, 9999999, ImageProcessor.NO_LUT_UPDATE);
							Roi sliceMembranes = new ThresholdToSelection().convert(ip);
							membraneRois[chan][z] = new ShapeRoi(sliceMembranes);
						}
						
					}
				}
				
				if(!preview){
					colours = generateColours(cells.size());
				}
				
				for(int ci=0;ci<cells.size();ci++){
					gui.setStatus("Measuring cell "+ci+"/"+cells.size()+(T>1?" T"+t:""));
					Cell cell = cells.get(ci);
					
					cell.setColour( preview?Color.GREEN:colours[ci] );
					for(Roi roi:cell.rois){
						roi.setStrokeColor(cell.colour);
						ol.add(roi);
						if(!preview){
							Rectangle rect = roi.getBounds();
							TextRoi label = new TextRoi(rect.x+rect.width/2, rect.y+12, ""+ci, LABELFONT);
							label.setStrokeColor(cell.colour);
							//label.setPosition(0,roi.getZPosition(),t);
							if(C>1||T>1) label.setPosition(0,roi.getZPosition(),t);
							else label.setPosition(roi.getPosition());
							ol.add(label);
						}
					}
					
					if(!preview && cell.rois.length>0){
					
						int row = rt.getCounter();
						rt.setValue("Cell", row, ci);
						P3D centroid = cell.centroid;
						rt.setValue("X", row, centroid.x);
						rt.setValue("Y", row, centroid.y);
						rt.setValue("Z", row, centroid.z);
						rt.setValue("T", row, (t-1)*cal.frameInterval);
						rt.setValue("Volume (\u00b5m\u00b3)", row, cell.getValue(Cell.VOLUME));

						Roi bigRoi = null;
						double maxArea = Double.NEGATIVE_INFINITY;
						for(int i=0;i<cell.rois.length;i++){	//get biggest ROI for 2D morphology
							Roi roi = cell.rois[i];
							double area = roi.getStatistics().area;
							if(area>maxArea){
								bigRoi = roi;
								maxArea = area;
							}
						}
						
						EllipseFitter ef = new EllipseFitter();
						ImageProcessor bigMask = bigRoi.getMask();
						ef.fit(bigMask, null);
						ef.makeRoi(bigMask);
						rt.setValue("Aspect Ratio", row, ef.minor/ef.major);
						
						double perim = bigRoi.getLength();
						double area = bigRoi.getStatistics().area;
						double circ = Math.min( 1.0, 4*Math.PI*(area/(perim*perim)) );
						rt.setValue("Circularity", row, circ);
						
						ShapeRoi[] surround = new ShapeRoi[cell.rois.length];
						int minZ = Integer.MAX_VALUE;
						int maxZ = Integer.MIN_VALUE;
						
						for(int c=1;c<=C;c++){	//measure this cell volume in all channels
							double nucleusSum = 0;
							long nucleusN = 0;
							double surroundSum = 0;
							long surroundN = 0;
							double membraneSum = 0;
							long membraneN = 0;
							for(int i=0;i<cell.rois.length;i++){
								Roi roi = cell.rois[i];
								int z = roi.getZPosition();
								if(z<minZ)	minZ = z;
								if(z>maxZ)	maxZ = z;
								maxZ = Math.max(maxZ, z);
								ImageProcessor ip = stack.getProcessor(imp.getStackIndex(c,z,t));
								ip.setRoi(roi);
								ImageStatistics nucleusStats = ip.getStatistics();
								nucleusSum += nucleusStats.mean*nucleusStats.pixelCount;
								nucleusN += nucleusStats.pixelCount;
								
								if(cp.doCytoplasm(c) && surround[i]==null){	//make Roi once for all channels
									Roi big = RoiEnlarger.enlarge(roi, cytoExpRpx);
									surround[i] = new ShapeRoi(big);
	//								surround[i].setPosition(0,z,t);
									if(excludeRois[z]!=null){
										surround[i] = surround[i].not(excludeRois[z]);	//excludeRois are the area outside the organoid and all nuclei in this slice
									}
									
									ShapeRoi surroundShow = surround[i].or( new ShapeRoi(roi) );	//show outer boundary only, not excluded nucleus inner boundary
									if(C>1||T>1) surroundShow.setPosition(0,z,t);
									else surroundShow.setPosition(roi.getPosition());
									surroundShow.setStroke( DOTTED_STROKE );
									surroundShow.setStrokeColor( cell.colour );
									ol.add(surroundShow);
								}
								
								ip.setRoi(surround[i]);
								ImageStatistics surroundStats = ip.getStatistics();
								surroundSum += surroundStats.mean*surroundStats.pixelCount;
								surroundN += surroundStats.pixelCount;
								
							}
							
							if(!preview && cp.doCytoplasm(c)){
								Arrays.sort( surround, new Comparator<Roi>(){	//put surrounding Rois in Z order
									public int compare(Roi A, Roi B){
										return new Integer(A.getZPosition()).compareTo(B.getZPosition());
									}
								} );
								for(int outZ : new int[]{minZ-1,maxZ+1}){		//measure axially adjacent slices
									if(outZ>0&&outZ<=Z){
										Roi outer = outZ==minZ-1?surround[0]:surround[surround.length-1];
										outer = RoiEnlarger.enlarge( outer, (-cellR/2f)/cal.pixelWidth );	//use shrunk version of adjacent surrounding Roi
										outer = new ShapeRoi(outer).not(excludeRois[outZ]);
										if(outer.getLength()<=0){
											continue;
										}
										
										ImageProcessor ip = stack.getProcessor(imp.getStackIndex(c,outZ,t));
										ip.setRoi(outer);
										ImageStatistics surroundStats = ip.getStatistics();
										surroundSum += surroundStats.mean*surroundStats.pixelCount;
										surroundN += surroundStats.pixelCount;
										
										//outer.setPosition( 0,outZ,t );
										if(C>1||T>1) outer.setPosition(0,outZ,t);
										else outer.setPosition(outZ);
										outer.setStroke( DOTTED_STROKE );
										outer.setStrokeColor( cell.colour );
										ol.add(outer);
									}
								}
							}
							
							if(cp.doMembrane(c)){
								Roi[] cellMembraneRois = new Roi[cell.rois.length];	//surrounding membrane for this cell
								for(int r=0;r<cell.rois.length;r++){
									ShapeRoi roi = new ShapeRoi(cell.rois[r]);
									int z = cell.rois[r].getZPosition();
									
									ShapeRoi expArea = new ShapeRoi( RoiEnlarger.enlarge(roi, cellRpx) );
									//area = area.not(roi);	//exclude nucleus area
									
									Roi mem = expArea.and(membraneRois[c][z]);
									
									mem.setStroke( MEMBRANE_STROKE );
									mem.setStrokeColor(cell.colour.darker());
									mem.setPosition(c, z, t);

									cellMembraneRois[r] = mem;
									
								}
								for(Roi mr:cellMembraneRois){
									if(mr==null||mr.getLength()<=0) continue;
									ol.add(mr);	//add to the main Overlay
									ImageProcessor measure = imp.getStack().getProcessor( imp.getStackIndex(c, mr.getZPosition(), t) );
									measure.setRoi(mr);
									ImageStatistics membraneStats = measure.getStatistics();
									membraneSum += membraneStats.mean*membraneStats.pixelCount;
									membraneN += membraneStats.pixelCount;
								}

							}
							
							if(cp.doNucleus(c)){
								double nucleusMean = nucleusSum/(float)nucleusN;
								rt.setValue("C"+c+" Nucleus Mean", row, nucleusMean);
								cell.setValue(Cell.NUCLEUS_MEAN, c, nucleusMean);
							}
							if(cp.doCytoplasm(c)){
								double surroundMean = surroundSum/(float)surroundN;
								rt.setValue("C"+c+" Cytoplasm Mean", row, surroundMean);
								cell.setValue(Cell.SURROUNDING_MEAN, c, surroundMean);
							}
							if(cp.doMembrane(c)){
								double membraneMean = membraneSum/(float)membraneN;
								rt.setValue("C"+c+" Membrane Mean", row, membraneMean);
								cell.setValue(Cell.MEMBRANE_MEAN, c, membraneMean);
							}
						}
	
					}
				}
				
				if(!preview){
					AnalysisPanel ap = gui.getAnalysisPanel();
					ap.run(cells, cellR, imp, ol, rt, t);
				}
				
			}
			if(!preview&&rt.getCounter()>0){
				rt.show("OAK_"+imp.getTitle()+"_R="+IJ.d2s(cellR,2)+"_"+SliceSegmenter.METHODS[method]);
				new TargetTable(imp, rt);
			}

			imp.setOverlay(ol);
			
			if( !preview&&Render3D.getInstance()!=null ){	//if a Render3D was made, display it now
				Render3D.getInstance().show();
			}
			
		//}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		}catch(Exception e){IJ.log(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}

	private Color[] generateColours(int n){	//generate n equally spaced colours
		Color[] col = new Color[n];
		for(int c=0;c<n;c++){
			float h = c/(float)n;
			if(c%2==0){		//alternate to make adjacent cell colours more different
				h = 1f-h;
			}
			float s = 1.0f;
			float v = 1.0f;
			col[c] = Color.getHSBColor(h,s,v);
		}
		return col;
	}
	
	public static void main(String[] arg){
		
		boolean render3Dtest = false;
		if(render3Dtest){
			if( arg.length>0 && arg[0].length()>0){
				if(arg[0].matches("-[0-9\\.]+")){
					long mem = (long) (Double.valueOf(arg[0].replaceAll("-", ""))*1E9);
					Render3D.test(mem);
				}
			}
			else{
				Render3D.test();
			}
			return;
		}
		
		ImageJ.main(arg);
		
		//ImagePlus img = new ImagePlus("E:\\Vanesa\\29.7.19 PEG SN Trial 1 Soft Sox.lif - well 2 glycerol Series004.tif");
		//ImagePlus img = new ImagePlus("E:\\Vanesa\\29.7.19 PEG SN Trial 1 Stiff Sox.lif - TileScan 003_Series004.tif");
		//ImagePlus img = new ImagePlus("E:\\Vanesa\\29.7.19 PEG SN Trial 1 Soft Sox.lif - well 2 glycerol Series004-2.tif");
		ImagePlus img = new ImagePlus("E:\\Vanesa\\29.7.19 PEG SN Trial 1 Stiff Sox.lif - TileScan 003_Series004_small.tif");
		//ImagePlus img = new ImagePlus("E:\\Vanesa\\High sample rate Z 2.tif");
		
		//ImagePlus img = new ImagePlus("E:\\Chufan\\ERK Control SC-Org 2000nM Dye3hours _Airyscan Processing.czi - ERK Control SC-Org 2000nM Dye3hours _Airyscan Processing #1-crop.tif");
		//ImagePlus img = new ImagePlus("E:\\Chufan\\ERK Control SC-Org 2000nM Dye3hours _Airyscan Processing.czi - ERK Control SC-Org 2000nM Dye3hours _Airyscan Processing #2-cropT.tif");
		//ImagePlus img = new ImagePlus("E:\\Chufan\\ERK Control SC-Org 2000nM Dye3hours _Airyscan Processing.czi - ERK Control SC-Org 2000nM Dye3hours _Airyscan Processing #5-2T.tif");
		//ImagePlus img = new ImagePlus("E:\\Chufan\\small_5D_test.tif");
		
		//ImagePlus img = new ImagePlus("E:\\Prof Emma\\Shuyu_40x_11pcw_HDBRL12873_Sox9-2-ECAD_4_crop.tif");
		
		//ImagePlus img = new ImagePlus("E:\\Patricia\\Without beads\\CA9_NDRG1_org1.czi - CA9_NDRG1_org1 #1 thin.tif");
		//ImagePlus img = new ImagePlus("E:\\Patricia\\Without beads\\CA9_NDRG1_org1 again.czi - CA9_NDRG1_org1 again #3.tif");
		//ImagePlus img = new ImagePlus("E:\\Patricia\\Without beads\\CA9_NDRG1_org1.czi - CA9_NDRG1_org1 #1 verysmall.tif");
		//ImagePlus img = new ImagePlus("E:\\Patricia\\With beads\\CA9_NDRG1_org3_beads1-50000.czi - CA9_NDRG1_org3_beads1-50000 #3-1_crop.tif");
		//ImagePlus img = new ImagePlus("E:\\Patricia\\Without beads\\CA9_NDRG1_org1.czi - CA9_NDRG1_org1 #1 crop.tif");

		

		
		final ImagePlus image = HyperStackConverter.toHyperStack(img, img.getNChannels(), img.getNSlices(), img.getNFrames());
		image.setDisplayMode(IJ.GRAYSCALE);
		image.setPosition(1, (int)(img.getNSlices()/2f), 1);
		image.show();
		
		new OAK().run();
		

		
	}

}
