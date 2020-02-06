package uk.ac.cam.gurdon.oak.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JButton;
import javax.swing.JColorChooser;

import clearvolume.transferf.TransferFunction;
import clearvolume.transferf.TransferFunctions;


public class KColourPicker extends JButton {
	private static final long serialVersionUID = -7145262133041400858L;
	private static final Dimension DIM = new Dimension(50,50);
	
	Color colour;
	
	
	public KColourPicker(Color colour){
		super("?");
		this.colour = colour;
	}

	public TransferFunction getTransferFunction() {
		colour = JColorChooser.showDialog(this, "Colour", colour);
		repaint();
		return TransferFunctions.getGradientForColor(colour);
	}
	
	@Override
	public Dimension getPreferredSize(){
		return DIM;
	}
	
	@Override
	public void paintComponent(Graphics g1d){
		super.paintComponent(g1d);
		Graphics2D g = (Graphics2D) g1d;
		//Rectangle rect = getBounds();
		g.setColor(colour);
		g.fillRect(0,0,getWidth(),getHeight());
	}



	
}
