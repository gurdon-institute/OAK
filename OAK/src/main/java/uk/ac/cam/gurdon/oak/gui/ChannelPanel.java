package uk.ac.cam.gurdon.oak.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import uk.ac.cam.gurdon.oak.Cell;

public class ChannelPanel extends JPanel implements ImageListener{
	private static final long serialVersionUID = -814518398624097041L;

	private JFrame main;
	private ArrayList<ChannelOptions> list;
	
	
	
	private class ChannelOptions{
		private int channel;
		private JLabel label;
		private TickBox nucleusTick, cytoplasmTick, membraneTick;
		
		private ChannelOptions(int channel, JPanel target){
			this.channel = channel;

			label = new JLabel(""+channel, JLabel.CENTER);
			nucleusTick = new TickBox(Prefs.get("OAK.ChannelPanel.nucleusTick_C"+channel, true));
			cytoplasmTick =	new TickBox(Prefs.get("OAK.ChannelPanel.cytoplasmTick_C"+channel, true));
			membraneTick = new TickBox(Prefs.get("OAK.ChannelPanel.membraneTick_C"+channel, false));

			target.add(label);
			target.add(nucleusTick);
			target.add(cytoplasmTick);
			target.add(membraneTick);
		}
		
		private ArrayList<String> getKeys(){
			ArrayList<String> keys = new ArrayList<String>();
			if(nucleusTick.isSelected()) keys.add( Cell.NUCLEUS_MEAN+"-C"+channel );
			if(cytoplasmTick.isSelected()) keys.add( Cell.SURROUNDING_MEAN+"-C"+channel );
			if(membraneTick.isSelected()) keys.add( Cell.MEMBRANE_MEAN+"-C"+channel );
			
			Prefs.set("OAK.ChannelPanel.nucleusTick", nucleusTick.isSelected());
			Prefs.set("OAK.ChannelPanel.cytoplasmTick", cytoplasmTick.isSelected());
			Prefs.set("OAK.ChannelPanel.membraneTick", membraneTick.isSelected());
			
			return keys;
		}
		
		private void removeFrom(ChannelPanel parent){
			parent.remove(label);
			parent.remove(nucleusTick);
			parent.remove(cytoplasmTick);
			parent.remove(membraneTick);
		}
		
	}
	
	private class TickBox extends JToggleButton{	//centered JToggleButton to be shown in grid without strange alignment of JCheckBox caused by label
		private static final long serialVersionUID = 5759219355611350435L;

		
		TickBox(boolean sel){
			super();
			setBorder(BorderFactory.createEmptyBorder());
			setSelected(sel);
		}
		
		@Override
		public void paintComponent(Graphics g1d){
			super.paintComponent(g1d);
			Graphics2D g = (Graphics2D) g1d;
			Rectangle bounds = getBounds();
			int midX = bounds.width/2;
			int midY = bounds.height/2;
			
			g.setColor(getBackground());
			g.fillRect(0,0, bounds.width,bounds.height);
			
			int r = 7;
			int rr = 2*r;
			
			g.setColor( isEnabled()?Color.WHITE:Color.LIGHT_GRAY );
			g.fillRect(midX-r, midY-r, rr,rr);
			g.setColor(Color.BLACK);
			g.drawRect(midX-r, midY-r, rr,rr);
			
			if(isSelected()){
				g.setStroke(new BasicStroke(2f));
				g.setColor(isEnabled()?Color.BLACK:Color.GRAY);
				
				g.drawLine( midX-r, midY-r, midX+r, midY+r );
				g.drawLine( midX+r, midY-r, midX-r, midY+r );
			}
		}
		
	}
	
	public ChannelPanel(JFrame main){
		super();
		this.main = main;
		//setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		setLayout( new GridLayout(0,4, 10,5) );
		int al = JLabel.CENTER;
		add(new JLabel("Channel", al));
		add(new JLabel("Nucleus", al));
		add(new JLabel("Cytoplasm", al));
		add(new JLabel("Membrane", al));
		makePanels();
	}
	
	private void makePanels(){
		int C = 4;	//default to 4 channels
		if(WindowManager.getImageCount()!=0){ 
			ImagePlus imp = IJ.getImage();
			C = imp.getNChannels();
		}
		
		if(list!=null&&list.size()>0){
			for(ChannelOptions co:list){
				co.removeFrom(this);
			}
		}
		
		list = new ArrayList<ChannelOptions>();
		for(int c=1;c<=C;c++){
			ChannelOptions co = new ChannelOptions(c, this);
			list.add(co);
			//add(co);
		}
	}
	
	public boolean doNucleus(int c){
		if(list.size()<c) return true;
		ChannelOptions op = list.get(c-1);
		Prefs.set("OAK.ChannelPanel.nucleusTick_C"+c, op.nucleusTick.isSelected());
		return op.nucleusTick.isSelected();
	}
	public boolean doCytoplasm(int c){
		if(list.size()<c) return true;
		ChannelOptions op = list.get(c-1);
		Prefs.set("OAK.ChannelPanel.cytoplasmTick_C"+c, op.cytoplasmTick.isSelected());
		return op.cytoplasmTick.isSelected();
	}
	public boolean doMembrane(int c){
		if(list.size()<c) return true;
		ChannelOptions op = list.get(c-1);
		Prefs.set("OAK.ChannelPanel.membraneTick_C"+c, op.membraneTick.isSelected());
		return op.membraneTick.isSelected();
	}
	
	public ArrayList<String> getMeasurementKeys(){
		ArrayList<String> keys = new ArrayList<String>();
		for(ChannelOptions co:list){
			keys.addAll( co.getKeys() );
		}
		return keys;
	}
	
	@Override
	public void imageOpened(ImagePlus imp) {
		makePanels();
		main.pack();
	}

	@Override
	public void imageClosed(ImagePlus imp) {
		makePanels();
		main.pack();
	}

	@Override
	public void imageUpdated(ImagePlus imp) {}
	
}
