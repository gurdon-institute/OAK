package uk.ac.cam.gurdon.oak.gui;
import java.awt.Color;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.gui.OvalRoi;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.text.TextPanel;
import ij.text.TextWindow;


public class TargetTable{
private ImagePlus imp;
private Calibration cal;
private TextPanel textPanel;
private String[] head;
private double markR;

	public TargetTable(ImagePlus imp, ResultsTable results){
	try{
		this.imp = imp;
		cal = imp.getCalibration();
		markR = 20*cal.pixelWidth;
		String headStr = results.getColumnHeadings();	
		this.head = headStr.split("\\t");
		
		Window[] win = ImageWindow.getWindows();
		for(int w=0;w<win.length;w++){	//this is ridiculous but there is no sensible way to get the TextPanel from a ResultsTable other than the official "Results" one
			if(win[w] instanceof TextWindow){
				TextPanel tp = ((TextWindow)win[w]).getTextPanel();
				if(tp.getResultsTable()!=null && tp.getText().length()>0){
					this.textPanel = tp;
					break;
				}
			}
		}
		
		textPanel.addMouseListener(new MouseAdapter(){
			@Override
			public void mouseClicked(MouseEvent me){
				if(SwingUtilities.isLeftMouseButton(me) && me.getClickCount()==2){
					target();
				}
			}
		});
		
	}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}	
	}
	
	public void target(){
	try{
		if(textPanel==null){
			IJ.error("OAK Table", "No TextPanel.");
		}
		int start = textPanel.getSelectionStart();
		if(start==-1){
			IJ.error("OAK Table", "No row selected in table.");
			return;
		}
		else if(textPanel.getLine(start)==null){
			IJ.error("OAK Table", "Selected row "+start+" is null.");
			return;
		}
		else{
			String[] line = textPanel.getLine(start).split("\\t");
			double x = -1;	double y = -1;
			int c = imp.getChannel(); int z = imp.getSlice(); int t = imp.getFrame();
			for(int i=0;i<head.length;i++){
					 if(head[i].equals("X")){x = Double.valueOf(line[i])/cal.pixelWidth;}
				else if(head[i].equals("Y")){y = Double.valueOf(line[i])/cal.pixelWidth;}
				else if(head[i].equals("C")){c = (int)Math.round(Double.valueOf(line[i]));}
				else if(head[i].equals("Z")){z = (int)Math.round(Double.valueOf(line[i])/cal.pixelDepth);}
				else if(head[i].equals("T")){t = (int)Math.round(Double.valueOf(line[i])/cal.frameInterval);}
			}
			imp.setPosition(c, z+1, t+1);
			if(x>=0&&y>=0){
				final OvalRoi marker = new OvalRoi(  x-markR , y-markR , 2*markR , 2*markR );
				marker.setStrokeWidth(100);
				marker.setStrokeColor(Color.RED);
				imp.setRoi(marker);
				final Timer timer = new Timer();
				TimerTask task = new TimerTask(){
					private int count = 0;
					  public void run() {
						  if(count<100){
							  marker.setStrokeWidth(100-count);
							  imp.setRoi(marker);
						  }
						  else{
							  imp.killRoi();
							  timer.cancel();
						  }
						  count++;
					  }
				};
				long delay = 5;
				timer.schedule(task, delay, delay );
			}
		}
	}catch(Exception except){System.out.print("~~~~~"+except.toString()+"~~~~~\n"+Arrays.toString(except.getStackTrace()).replace(",","\n")+"\n~~~~~~~~~~");}
	}
}