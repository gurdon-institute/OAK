package uk.ac.cam.gurdon.oak.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

	
public class RenderControl extends JComponent implements ActionListener, ChangeListener{
		private static final long serialVersionUID = -7145262133041400858L;
		private static final Dimension DIM = new Dimension(25,25);
		
		public Color colour;
		public int ci;
		public double gamma;
		
		private ActionListener actionListener;
		private ChangeListener changeListener;
		private JCheckBox tickBox;
		private CButton button;
		private JSlider gammaSlider;
		
		
		private class CButton extends JButton{
			private static final long serialVersionUID = -5278011971762274927L;

			private CButton(){
				super();
			}
			
			@Override
			public void paintComponent(Graphics g1d){
				super.paintComponent(g1d);
				Graphics2D g = (Graphics2D) g1d;
				g.setColor(colour);
				g.fillRect(0,0,getWidth(),getHeight());
			}
			
			@Override
			public Dimension getPreferredSize(){
				return DIM;
			}
		}
		
		public RenderControl(int ci, String label, boolean sel, Color colour){
			super();
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			this.ci = ci;
			this.colour = colour;
			
			button = new CButton();
			button.addActionListener(this);
			
			tickBox = new JCheckBox("", sel);
			tickBox.addActionListener(this);

			gammaSlider = new JSlider(1, 100, 50);
			gammaSlider.addChangeListener(this);

			KPanel kp = new KPanel(tickBox, button, 40, label);
			kp.setLayout(new FlowLayout(FlowLayout.LEFT, 2,2));
			add(kp);
			add(new KPanel("\u0263", gammaSlider));
			add(Box.createVerticalStrut(40));
		}
		
		public boolean isSelected(){
			return tickBox.isSelected();
		}
		
		public void setSelected(boolean sel){
			tickBox.setSelected(sel);
		}
		
		public void setActionListener(ActionListener listen){
			this.actionListener = listen;
		}
		
		public void setChangeListener(ChangeListener listen){
			this.changeListener = listen;
		}

		@Override
		public void actionPerformed(ActionEvent ae) {
			if(ae.getSource()==button) {
				Color choose = JColorChooser.showDialog(this, "Colour", colour);
				if(choose!=null) colour = choose;
			}
			ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "RenderControl event for C"+ci);
			actionListener.actionPerformed(event);
		}

		@Override
		public void stateChanged(ChangeEvent ce) {
			int gammai = gammaSlider.getValue();
			gamma = (Math.log(gammai/101.0) / Math.log(0.5));
			ChangeEvent event = new ChangeEvent(this);
			changeListener.stateChanged(event);
		}
		
		
		


}
