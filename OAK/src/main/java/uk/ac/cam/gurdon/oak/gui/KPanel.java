package uk.ac.cam.gurdon.oak.gui;

import java.awt.Color;
import java.awt.FlowLayout;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class KPanel extends JPanel{
	private static final long serialVersionUID = 4853851720085083335L;
	
	public KPanel(Object... comps){
		super();
		setLayout(new FlowLayout(FlowLayout.CENTER, 2, 2));
		if(comps.length==0){
			add(Box.createVerticalStrut(20));
		}
		else{
			for(Object obj : comps){
				if(obj instanceof JComponent){
					add((JComponent)obj);
				}
				else if(obj instanceof String){
					add(new JLabel((String)obj));
				}
				else if(obj instanceof Integer){
					add(Box.createHorizontalStrut((int) obj));
				}
				else if(obj instanceof Color){
					setBackground((Color) obj);
				}
			}
		}
	}
	
}