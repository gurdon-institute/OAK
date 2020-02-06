package uk.ac.cam.gurdon.oak.analysis;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.jogamp.newt.awt.NewtCanvasAWT;

import clearvolume.renderer.ClearVolumeRendererInterface;
import clearvolume.renderer.cleargl.overlay.Overlay;
import clearvolume.renderer.factory.ClearVolumeRendererFactory;
import clearvolume.transferf.TransferFunctions;
import coremem.enums.NativeTypeEnum;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.HyperStackConverter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import uk.ac.cam.gurdon.oak.Cell;
import uk.ac.cam.gurdon.oak.P3D;
import uk.ac.cam.gurdon.oak.gui.RenderControl;

public class Render3D {
	private static final Color[] COLOUR_DEFAULTS = new Color[]{Color.RED,Color.GREEN,Color.BLUE,Color.MAGENTA,Color.YELLOW,Color.CYAN,Color.WHITE,Color.PINK,Color.ORANGE};
	private static Render3D instance;
	private static ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(1);
	static{
		EMPTY_BUFFER.put((byte) 0);
	}
	
	private Timer displayTimer;
	private long start;
	
	private DisplayGui gui;
	private ClearVolumeRendererInterface renderer;
	private HashMap<CT, ByteBuffer> buffers;
	private int sizeXb, sizeYb, sizeZb, C;
	private String keyA, keyB;
	private ImagePlus imp;
	private Calibration cal;
	
	
	private class CT{
		int t,c;
		
		private CT(int t, int c){
			this.t = t;
			this.c = c;
		}
		
		@Override
		public boolean equals(Object other){
			if(other==null||!(other instanceof CT)) return false;
			return hashCode()==other.hashCode();
		}
		
		@Override
		public int hashCode(){
			return new Integer(t).hashCode() + 13 * new Integer(c).hashCode();
		}
		
		@Override
		public String toString(){
			return "CT [T"+t+", C"+c+"]";
		}
		
	}
	
	private class DisplayGui extends JFrame implements ChangeListener, ActionListener{
		private static final long serialVersionUID = -3008649327475072084L;
		
		private JSlider qualitySlider, frameSlider;
		private RenderControl[] renderControls;
		private JButton resetButton;
		private JLabel timeLabel;
		private int frame;
		
		
		private DisplayGui(){
			super(imp.getTitle(), renderer.getNewtCanvasAWT().getGraphicsConfiguration());	//put on same GraphicsDevice as canvas to avoid IllegalArgumentException: adding a container to a container on a different GraphicsDevice
			this.frame = 1;	//start displaying 1
			
			setIconImage( Toolkit.getDefaultToolkit().getImage(getClass().getResource("logo_icon.gif")) );
			setLayout(new BorderLayout());
		
			NewtCanvasAWT canvas = renderer.getNewtCanvasAWT();
			add( canvas, BorderLayout.CENTER );
			
			JPanel rightPanel = new JPanel();
			JPanel pan = new JPanel();
			pan.setLayout( new BoxLayout(pan, BoxLayout.Y_AXIS) );

			renderControls = new RenderControl[C+2];
			for(int c=0;c<C+2;c++){
				
				String label;
				boolean show = true;
				if(c==0) label = keyA;
				else if(c==1) label = keyB;
				else{ 
					label = "Channel "+(c-1);
					show = false;
				}
				renderControls[c] = new RenderControl(c, label, show, COLOUR_DEFAULTS[c]);
				renderControls[c].setActionListener(this);
				renderControls[c].setChangeListener(this);
				pan.add(renderControls[c]);

			}
			rightPanel.add(pan);
			add(rightPanel, BorderLayout.EAST);
			
			JPanel controlPanel = new JPanel();
			controlPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 2,2));
			
			int T = imp.getNFrames();
			frameSlider = new JSlider(1, T, 1);
			frameSlider.setMajorTickSpacing(1);
			frameSlider.setPaintTicks(true);
			frameSlider.setPaintLabels(true);
			frameSlider.setSnapToTicks(true);
			frameSlider.addChangeListener(this);
			if(T>1){
				controlPanel.add(new JLabel("Frame:"));
				controlPanel.add(frameSlider);
				timeLabel = new JLabel(IJ.d2s((frame-1)*cal.frameInterval,2)+" "+cal.getTimeUnit());
				controlPanel.add(timeLabel);
			}
			
			controlPanel.add(Box.createHorizontalStrut(20));
			
			qualitySlider = new JSlider(0, 100, 50);
			qualitySlider.addChangeListener(this);
			controlPanel.add(new JLabel("Quality:"));
			controlPanel.add(qualitySlider);
			
			controlPanel.add(Box.createHorizontalStrut(20));
			
			resetButton = new JButton("Reset View");
			resetButton.addActionListener(this);
			controlPanel.add(resetButton);
			
			add( controlPanel, BorderLayout.SOUTH );

			addWindowListener(new WindowAdapter(){
				public void windowClosing(WindowEvent we){
					try{
						renderer.close();	//closes the renderer and releases all resources
						dispose();
					}catch(Exception e){System.out.println("closing: "+e);}
				}
			});

		}
		
		private void display(){
			pack();
			setLocationRelativeTo(null);
			setVisible(true);
			
			//----------------------------------------------------- setting volume data AFTER displaying the canvas is much faster ------------------------
			for(RenderControl ctrl:renderControls){
				if(ctrl.isSelected()){
					setRenderData(frame, ctrl.ci, ctrl.isSelected(), ctrl.colour);
				}
			}

			Collection<Overlay> overlays = renderer.getOverlays();
			for(Overlay overlay:overlays){
				if(overlay.getName().equals("box") && overlay.isDisplayed()){	//switch off 3D box Overlay, doesn't work well with non-cubic voxels
					overlay.setDisplayed(false);								//can't use renderer.toggleBoxDisplay() because the renderer may already have been displayed and toggled
				}
			}

		}
		
		private void setRenderData(int frame, int channel, boolean visible, Color colour){
			
			System.setErr(new ErrorMonitor(true));	//monitor error stream
			try{
				displayTimer = new Timer();
				long timeLimit = 10000;
				start = System.currentTimeMillis();
				TimerTask displayTime = new TimerTask(){
					public void run(){
						if(System.currentTimeMillis()-start>=timeLimit){
							int op = JOptionPane.showConfirmDialog(null, "Display buffer rendering seems slow.\nCancel?", "Cancel?", JOptionPane.YES_NO_OPTION);
							if(op==JOptionPane.YES_OPTION){
								displayTimer.cancel();
								for(RenderControl ctrl:renderControls){
									renderer.setVolumeDataBuffer( ctrl.ci, EMPTY_BUFFER, 1,1,1 );
									ctrl.setSelected(false);
								}
							}
							else{
								start = System.currentTimeMillis();
							}
						}
					}
				};
				displayTimer.scheduleAtFixedRate(displayTime, 1000,1000);
				
				if(visible){
					CT bufferKey = new CT(frame,channel);
					if(!buffers.containsKey(bufferKey)){
						System.out.println("no buffer for key: "+bufferKey);
						System.setErr(System.err);
						return;
					}
					ByteBuffer bb = buffers.get(bufferKey);
					renderer.setVolumeDataBuffer(channel, bb, sizeXb, sizeYb, sizeZb, cal.pixelWidth, cal.pixelHeight, cal.pixelDepth);
					renderer.setTransferFunction(channel, TransferFunctions.getGradientForColor(colour));

					renderer.setLayerVisible(channel, true);
				}
				else{
					renderer.setLayerVisible(channel, false);
					if(channel>0){	//FIXME: voxel size is lost if channel 0 is cleared even when set for other channels
						renderer.setVolumeDataBuffer( channel, EMPTY_BUFFER, 1,1,1 );	//remove buffers when not displayed to free GPU memory
					}
				}
				renderer.notifyChangeOfVolumeRenderingParameters();
				
				displayTimer.cancel();
				
			}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
			 finally{ System.setErr(System.err); }	//reset standard stream
		}
		
		/*jogamp doesn't throw Exceptions and prints Errors and Exceptions to System.err instead. This class is for monitoring the error stream and optionally suppressing the output.*/
		private  class ErrorMonitor extends PrintStream{
			private boolean print;
			
			private ErrorMonitor(boolean print){
				super(System.err);
				this.print = print;
			}
			
			@Override
			public void println(String str){
				if(str.matches("^OpenCL error.*")){
					String[] line = str.split(" ");
					int errorCode = Integer.valueOf(line[2]);
					if(errorCode==-4){	//-4 is OpenClException.CL_MEM_OBJECT_ALLOCATION_FAILURE
						displayTimer.cancel();
						
						SwingUtilities.invokeLater(new Runnable(){public void run(){	//show error on the EDT
							JOptionPane.showMessageDialog(gui, str+"\nProbably caused by too much volume data being displayed. Try showing fewer channels.", "GPU Memory Allocation Failure", JOptionPane.ERROR_MESSAGE);
						}});
						
						SwingWorker<Object, Void> worker = new SwingWorker<Object, Void>(){	//clear all volume buffers off the EDT
							public Object doInBackground(){
								try{
									for(RenderControl ctrl:renderControls){
										renderer.setVolumeDataBuffer( ctrl.ci, EMPTY_BUFFER, 1,1,1 );
										ctrl.setSelected(false);
									}
								}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
								return null;
							}
						};
						worker.execute();
						
					}
				}
				else if(print){
					super.println(str);
				}
			}
			
		}
		
		@Override
		public void stateChanged(ChangeEvent ce) {
			Object src = ce.getSource();
			if(src==qualitySlider){
				int quali = qualitySlider.getValue();
				double quality = quali/100.0;
				for(int i=0;i<renderer.getNumberOfRenderLayers();i++){
					renderer.setQuality(i, quality);
				}
			}
			else if(src instanceof RenderControl){
				RenderControl ctrl = (RenderControl) src;
				renderer.setGamma(ctrl.ci, ctrl.gamma);
			}
			else if(src==frameSlider){
				int current = frameSlider.getValue();
				if(frame == current) return;
				frame = current;
				for(RenderControl ctrl: renderControls){
					if(ctrl.isSelected()){
						setRenderData(frame, ctrl.ci, ctrl.isSelected(), ctrl.colour);
					}
				}
				timeLabel.setText(IJ.d2s((frame-1)*cal.frameInterval,2)+" "+cal.getTimeUnit());
			}

		}

		@Override
		public void actionPerformed(ActionEvent ae) {
			Object src = ae.getSource();
			if(src==resetButton){
				renderer.resetRotationTranslation();
			}
			else if(src instanceof RenderControl){
				RenderControl ctrl = (RenderControl) src;
				setRenderData(frame, ctrl.ci, ctrl.isSelected(), ctrl.colour);
			}
		}
		
	}
	
	public Render3D(String keyA, String keyB, ImagePlus imp){
		try{

			this.keyA = keyA;
			this.keyB = keyB;
			this.imp = imp;
			this.C = imp.getNChannels();
			this.cal = imp.getCalibration();
			
			Dimension display = Toolkit.getDefaultToolkit().getScreenSize();
			final int windowSize = Math.min(display.width/2, display.height/2);
			final int textureSize = 768;
			final int nLayers = C+2;		//number of render layers
	
			// obtain the best renderer, this usually means picking the best supported GPU programming framwork such as CUDA or OpenCL or GLSL on the most performant GPU installed.
			renderer = ClearVolumeRendererFactory.newBestRenderer("ClearVolumeRenderer", windowSize, windowSize, NativeTypeEnum.Byte, textureSize, textureSize, nLayers, true);
			
			buffers = new HashMap<CT, ByteBuffer>();
		}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
	public static Render3D getInstance(String keyA, String keyB, ImagePlus imp){
		if(instance==null||instance.renderer==null||!keyA.equals(instance.keyA)||!keyB.equals(instance.keyB)||imp!=instance.imp){
			instance = new Render3D(keyA, keyB, imp);
		}
		return instance;
	}
	
	public static Render3D getInstance(){
		return instance;
	}
	
	public void show(){
		if(renderer==null||buffers==null||buffers.size()==0) return;
		if(gui!=null) gui.dispose();
		gui = new DisplayGui();
		gui.display();
	}
	
	public void makeBuffers(ArrayList<Cell> cells, double cellR, int t) {
		try{

			final int radiusX = (int) Math.round(cellR/cal.pixelWidth);
			final int radiusY = (int) Math.round(cellR/cal.pixelHeight);
			final int radiusZ = (int) Math.round(cellR/cal.pixelDepth);
			final int sqRadius = radiusX*radiusX;
			final int border = 2*radiusX;
			
			// volume dimensions:
			final int sizeX = imp.getWidth();
			final int sizeY = imp.getHeight();
			final int sizeZ = imp.getNSlices();
			sizeXb = sizeX + (2*border);
			sizeYb = sizeY + (2*border);
			sizeZb = sizeZ + (2*border);
			
			// size of the buffer in bytes (8-bit)
			BigInteger nBytes = BigInteger.valueOf(sizeXb).multiply(BigInteger.valueOf(sizeYb)).multiply(BigInteger.valueOf(sizeZb));
			if(nBytes.compareTo(BigInteger.valueOf(Integer.MAX_VALUE))>=0){
				JOptionPane.showMessageDialog(null, imp+" is too big for Render3D, try a smaller image.\nSize = "+nBytes+" bytes, max = "+Integer.MAX_VALUE,
													"Buffer Overflow", JOptionPane.ERROR_MESSAGE);
				return;
			}
			
			double[] valuesA = cells.stream().mapToDouble( c->c.getValue(keyA) ).map( v->Double.isNaN(v)?0.0:v ).toArray();
			double[] valuesB = cells.stream().mapToDouble( c->c.getValue(keyB) ).map( v->Double.isNaN(v)?0.0:v ).toArray();

			final double minA = Arrays.stream(valuesA).min().getAsDouble();
			final double maxA = Arrays.stream(valuesA).max().getAsDouble();
			final double minB = Arrays.stream(valuesB).min().getAsDouble();
			final double maxB = Arrays.stream(valuesB).max().getAsDouble();

			
			byte[] volumeDataA = new byte[nBytes.intValue()];
			byte[] volumeDataB = new byte[nBytes.intValue()];
			for(int i=0;i<cells.size();i++){
				Cell cell = cells.get(i);
				int x = (int) Math.round(cell.centroid.x / cal.pixelWidth) + border;
				int y =  sizeY - (int) Math.round(cell.centroid.y / cal.pixelHeight) + border;
				int z =  sizeZ - (int) Math.round(cell.centroid.z / cal.pixelDepth) + border;
				
				byte biteA = (byte) (((valuesA[i]-minA)/(maxA-minA)) * 192.0 + 63);
				byte biteB = (byte) (((valuesB[i]-minB)/(maxB-minB)) * 192.0 + 63);
				
				for(int rx=-radiusX;rx<=radiusX;rx++){
					for(int ry=-radiusY;ry<=radiusY;ry++){
						for(int rz=-radiusZ;rz<=radiusZ;rz++){
							double sqDist =  rx*rx + ry*ry + rz*rz;
							if(sqDist>sqRadius) continue;	//discretised spheroid
							int index = index1D( x+rx, y+ry, z+rz, sizeXb, sizeYb, sizeZb );
							volumeDataA[index] = biteA;
							volumeDataB[index] = biteB;
						}
					}
				}

			}
			
			boolean direct = true;	//use direct ByteBuffers for native I/O off the heap
			
			if(direct){
				ByteBuffer bbA = ByteBuffer.allocateDirect(nBytes.intValue());
				bbA.put(volumeDataA);
				ByteBuffer bbB = ByteBuffer.allocateDirect(nBytes.intValue());
				bbB.put(volumeDataB);
				buffers.put(new CT(t,0), bbA);
				buffers.put(new CT(t,1), bbB);
			}
			else{
				buffers.put(new CT(t,0), ByteBuffer.wrap(volumeDataA));
				buffers.put(new CT(t,1), ByteBuffer.wrap(volumeDataB));
			}
			
			ImageStack stack = imp.getStack();
			for(int c=1;c<=imp.getNChannels();c++){
				
				float[] values = new float[nBytes.intValue()];
				double min = Double.POSITIVE_INFINITY;
				double max = Double.NEGATIVE_INFINITY;
				for(int z=0;z<sizeZ;z++){
					ImageProcessor ip = stack.getProcessor(imp.getStackIndex(c,z+1,t));
					for(int y=0;y<sizeY;y++){
						for(int x=0;x<sizeX;x++){
							int xi = x + border;
							int yi = sizeY - y + border;
							int zi = sizeZ - z + border;
							int index = index1D( xi, yi, zi, sizeXb, sizeYb, sizeZb );
							float value = ip.getf(x,y);
							values[index] = value;
							min = Math.min(min, value);
							max = Math.max(max, value);
						}
					}
				}
				
				byte[] volumeDataI = new byte[nBytes.intValue()];
				double scale = 255.0/(max-min);
				for(int i=0;i<nBytes.intValue();i++){
					volumeDataI[i] = (byte) ( values[i] * scale );
				}
				
				CT key = new CT(t,c+1);
				
				if(direct){
					ByteBuffer bbI = ByteBuffer.allocateDirect(nBytes.intValue());
					bbI.put(volumeDataI);
					buffers.put(new CT(t,c+1), bbI );
				}
				else{
					buffers.put(key, ByteBuffer.wrap(volumeDataI) );
				}
			}
		
		}catch(OutOfMemoryError oom){
			JOptionPane.showMessageDialog(null, oom+"\nAllocate more heap space using java -Xmx<size>");
			renderer.close();
		}
		catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}

	private static int index1D(int x, int y, int z, int sizeX, int sizeY, int sizeZ){
		int bpv = 1;	//bytes per voxel, 1 for byte data
		int i1d = bpv * (x + sizeX * y + sizeX * sizeY * z) + 1;
		return i1d;
	}

	public static void test(){
		long allocatedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		long presumableFreeMemory = Runtime.getRuntime().maxMemory() - allocatedMemory;	//method by Prof. Christian Fries
		long use = (long) (presumableFreeMemory / 4.0);
		test(use);
	}
	
	public static void test(long memoryToUse){
		try{
		int C = 4;
		int T = 1;
		
		BigInteger mpc = BigInteger.valueOf((long) (memoryToUse/(float)(C+2)));		//memory per channel
		
		int W = (int) Math.cbrt(mpc.doubleValue());
		int H = W;
		int Z = W;

		BigInteger needed = BigInteger.valueOf(W).multiply(BigInteger.valueOf(H)).multiply(BigInteger.valueOf(Z)).multiply(BigInteger.valueOf(C+2));
		
		System.out.println("\n~~~~~~~~~~~~~~~~~~~~~~");
		System.out.println("Render3D test:");
		System.out.println("Memory available = "+IJ.d2s( memoryToUse/1E9, 3)+" GB");
		System.out.println("Test data size = "+IJ.d2s( needed.longValue()/1E9, 3)+" GB ("+W+" * "+H+" * "+Z+" * ("+C+"+2))");
		
		Calibration cal = new Calibration();
		cal.pixelWidth = 1.0;
		cal.pixelHeight = 1.0;
		cal.pixelDepth = W/Z;
		ImageStack stack = new ImageStack(W,H);
		double maxR2 = (Z/2d)*(Z/2d);
		for(int z=1;z<=Z;z++){
			System.out.print("\rGenerating test data "+IJ.d2s((z/(float)Z)*100,2)+"%");	//single line output in any terminal but eclipse
			for(int c=1;c<=C;c++){
				byte zb = (byte) z;
				ByteProcessor bp = new ByteProcessor(W,H);
				for(int x=0;x<W;x++){
					byte xb = (byte) x;
					for(int y=0;y<H;y++){
						byte yb = (byte) y;
						byte value = 0;
						if(c==1) value = (byte) (xb ^ yb ^ zb);	//byte cubes
						else if(c==2) value = (byte) ((x%50<2&&y%50<2)?128:0);	//lines
						else if(c==3) value = (byte) (y%100<2?128:0);	//planes
						else if(c==4){ 	//inverse byte cubes in spheroid
							double r2 = Math.pow(x-W/2.0,2)+Math.pow(y-W/2.0,2)+Math.pow(z-Z/2.0,2);
							if(r2<maxR2) value = (byte) ( W-xb ^ H-yb ^ Z-zb );
						}
						bp.set(x,y, value);
					}
				}
				stack.addSlice("C"+c+"_Z"+z, bp);
			}
		}
		System.out.println("\n~~~~~~~~~~~~~~~~~~~~~~");
		
		ImagePlus fakeImage = new ImagePlus("OAKTest", stack);
		fakeImage = HyperStackConverter.toHyperStack(fakeImage, C, Z, T);
		fakeImage.setCalibration(cal);
		fakeImage.setDisplayMode(IJ.GRAYSCALE);
		
		/*if(true){
			fakeImage.show();
			return;
		}*/
		
		ArrayList<Cell> fakeCells = new ArrayList<Cell>();
		Random random = new Random();
		for(int i=0;i<12;i++){
			int x = random.nextInt(W);
			int y = random.nextInt(H);
			int z = random.nextInt(Z);
			double a = random.nextDouble()*255;
			double b = random.nextDouble()*255;
			
			Cell fc = new Cell( new P3D(x,y,z), new Roi[0] );
			fc.setValue("testA", a);
			fc.setValue("testB", b);
			fakeCells.add( fc );
		}
		
		Render3D r3d = Render3D.getInstance("testA", "testB", fakeImage);
		r3d.makeBuffers(fakeCells, 10.0, 1);
		r3d.show();
		if(r3d.gui==null){
			System.exit(0);
		}
		else{
			r3d.gui.addWindowListener(new WindowAdapter(){
				@Override
				public void windowClosing(WindowEvent we){
					System.exit(0);
				}
			});
		}
		
		}catch(OutOfMemoryError oom){
			JOptionPane.showMessageDialog(null, oom+"\nwhile running test with "+IJ.d2s( memoryToUse/1E9, 3)+" GB of data."+
													"\nAllocate more heap space using java -Xmx<size> or use a smaller test dataset size."
										 );
		}
		catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
	}
	
}
