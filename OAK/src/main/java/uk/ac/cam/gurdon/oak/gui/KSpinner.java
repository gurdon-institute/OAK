package uk.ac.cam.gurdon.oak.gui;

import java.awt.Dimension;

import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

class KSpinner extends JSpinner{
	private static final long serialVersionUID = -8997848843950051168L;

	private Dimension dim = new Dimension(60, 20);
	
	KSpinner(int value, int min, int max, int step){
		super();
		SpinnerNumberModel model = new SpinnerNumberModel(value, min, max, step);
		setModel(model);
		setEditor(new JSpinner.NumberEditor(this, "#"));
		dim.width = 20 + (""+max).length()*10;
	}
	
	KSpinner(double value, double min, double max, double step){
		super();
		SpinnerNumberModel model = new SpinnerNumberModel(value, min, max, step);
		setModel(model);
		setEditor(new JSpinner.NumberEditor(this, "#0.0#"));
		dim.width = 20 + (""+max).length()*10;
	}
	
	public Dimension getPreferredSize(){
		return dim;
	}
	
}