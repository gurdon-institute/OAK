package uk.ac.cam.gurdon.oak.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;

import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Overlay;
import ij.measure.ResultsTable;
import uk.ac.cam.gurdon.oak.Cell;
import uk.ac.cam.gurdon.oak.analysis.Correlation;
import uk.ac.cam.gurdon.oak.analysis.IntensityDistribution;
import uk.ac.cam.gurdon.oak.analysis.PositiveCaller;
import uk.ac.cam.gurdon.oak.analysis.PositiveSubpopulation;
import uk.ac.cam.gurdon.oak.analysis.Render3D;

public class AnalysisPanel extends JPanel implements ActionListener{
	private static final long serialVersionUID = 7665195634488268110L;

	private OAK_GUI gui;
	private JButton addButton, subButton;
	private ArrayList<Analysis> list;
	
	
	private static enum Method{
		POSITIVE_CALLS("Positive Calls"), SUBPOPULATION("Positive Sub-Population"),
		INTENSITY_DISTRIBUTION("Intensity Distribution"), CORRELATION("Correlation"), RENDER("3D Render");
		
		String name;
		Method(String name){
			this.name = name;
		}
		
		private static Method fromName(String str){
			for(Method method:values()){
				if(method.name.equals(str)) return method;
			}
			return null;
		}
		
		private static String[] names(){
			return Arrays.stream(values()).map( v->v.name ).toArray(String[]::new);
		}
	}
	
	private class Analysis extends JPanel{
		private static final long serialVersionUID = -5376682753536569050L;

		private JComboBox<String> methodCombo;
		private JComboBox<String> typeCombo, typeCombo2;
		private JSpinner chanSpinner, chanSpinner2, ZthreshSpinner;
		private JLabel ZthreshLabel, vsLabel, C2label;
		
		Method method;
		int channel, channel2;
		double Zthresh;
		String type, type2, key, key2;

		
		Analysis(){
			super();
			setLayout(new FlowLayout(FlowLayout.LEFT, 2,2));
			
			methodCombo = new JComboBox<String>( Method.names() );
			
			ItemListener controlDisplayer = new ItemListener(){
				@Override
				public void itemStateChanged(ItemEvent ie) {
					String item = (String) methodCombo.getSelectedItem();
					setComponentVisibility(item);
				}
			};
			
			typeCombo = new JComboBox<String>( new String[]{ Cell.NUCLEUS_MEAN, Cell.SURROUNDING_MEAN, Cell.MEMBRANE_MEAN } );
			chanSpinner = new KSpinner( 1, 1, 12, 1 );
			ZthreshLabel = new JLabel("+ve Z");
			ZthreshSpinner = new KSpinner( 1.5, 0.0, 5.0, 0.1 );
			
			typeCombo2 = new JComboBox<String>( new String[]{ Cell.NUCLEUS_MEAN, Cell.SURROUNDING_MEAN, Cell.MEMBRANE_MEAN } );
			chanSpinner2 = new KSpinner( 1, 1, 12, 1 );
			
			methodCombo.addItemListener( controlDisplayer );
			
			add(methodCombo);
			add(new JLabel("C"));
			add(chanSpinner);
			add(typeCombo);
			
			vsLabel = new JLabel("vs ");
			add(vsLabel);
			C2label = new JLabel("C");
			add(C2label);
			add(chanSpinner2);
			add(typeCombo2);
			
			add(Box.createHorizontalStrut(10));
			add(ZthreshLabel);
			add(ZthreshSpinner);
			
			setComponentVisibility((String)methodCombo.getSelectedItem());
		}
		
		Analysis(Method method, int chan, String type){
			this();
			if(method!=null)methodCombo.setSelectedItem(method.name);
			chanSpinner.setValue(chan);
			typeCombo.setSelectedItem(type);
		}
		
		Analysis(Method method, int chan, String type, int chan2, String type2){
			this();
			if(method!=null){
				methodCombo.setSelectedItem(method.name);
			}
			chanSpinner.setValue(chan);
			typeCombo.setSelectedItem(type);
			chanSpinner2.setValue(chan2);
			typeCombo2.setSelectedItem(type2);
		}
		
		public void setComponentVisibility(String item){
			boolean showZ = item.equals(Method.POSITIVE_CALLS.name) || item.equals(Method.SUBPOPULATION.name);
			ZthreshLabel.setVisible( showZ );
			ZthreshSpinner.setVisible( showZ );
			
			boolean showVs = item.equals(Method.CORRELATION.name);
			vsLabel.setVisible(showVs);
			
			boolean showC = item.equals(Method.CORRELATION.name) || item.equals(Method.RENDER.name);
			C2label.setVisible(showC);
			chanSpinner2.setVisible(showC);
			typeCombo2.setVisible(showC);
			
			gui.pack();
		}
		
		void parametersSelected(){
			method = Method.fromName( (String) methodCombo.getSelectedItem() );
			type = (String) typeCombo.getSelectedItem();
			channel = (int) chanSpinner.getValue();
			Zthresh = (double) ZthreshSpinner.getValue();
			key = type+"-C"+channel;
			
			type2 = (String) typeCombo2.getSelectedItem();
			channel2 = (int) chanSpinner2.getValue();
			key2 = type2+"-C"+channel2;
		}
		
		String getPrefsString(){
			parametersSelected();
			return method.name+","+key+","+key2+","+Zthresh;
		}
	}
	
	private class AButton extends JButton{
		private static final long serialVersionUID = 1376205833361553540L;
		private final Font font = new Font(Font.SANS_SERIF, Font.BOLD, 14);
		private final Insets insets = new Insets(0,0,0,0);
		private final Dimension dim = new Dimension(20,20);
		
		public AButton(String label){
			super(label);
			setFont(font);
			setMargin(insets);
			setFocusPainted(false);
		}
		
		@Override
		public Dimension getPreferredSize(){
			return dim;
		}
		
	}
	
	public AnalysisPanel(OAK_GUI gui){
		super();
		this.gui = gui;
		setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		
		add(Box.createHorizontalStrut(450));
		
		JPanel buttonPan = new JPanel();
		addButton = new AButton("+");
		addButton.addActionListener(this);
		subButton = new AButton("-");
		subButton.addActionListener(this);
		buttonPan.add( addButton );
		buttonPan.add( subButton );
		add(buttonPan);
		
		list = new ArrayList<Analysis>();

		loadPrefs();
		
	}
	
	private void savePrefs(){
		StringBuilder sb = new StringBuilder();
		for(int a=0;a<list.size();a++){
			if(a!=0) sb.append("\n");
			sb.append(list.get(a).getPrefsString());
		}
		Prefs.set("OAK.Analysis", sb.toString());
	}
	
	private void loadPrefs(){
		String str = Prefs.get("OAK.Analysis", "");
		String[] entries = str.split("\n");
		if(entries.length==0) return;
		for(String entry:entries){
			String[] mk = entry.split(",");
			//if(mk.length<=1) continue;
			Method method = Method.fromName(mk[0]);
			//if(method==null) continue;
			
			int channel = 1;
			String type = "Positive Calls";
			if(mk.length>1){
				String[] ct = mk[1].split("-C");
				type = ct[0];
				channel = Integer.valueOf(ct[1]);
			}
			
			Analysis an = null;
			if(mk.length>2){
				String[] ct2 = mk[2].split("-C");
				//if(ct.length<=1) continue;
				String type2 = ct2[0];
				int channel2 = Integer.valueOf(ct2[1]);
				an = new Analysis(method, channel, type, channel2, type2);
				if(mk.length>3){
					try{
						double Zthresh = Double.valueOf(mk[3]);
						an.ZthreshSpinner.setValue(Zthresh);
					}catch(NumberFormatException nfe){System.out.println("couldn't get Z threshold from "+mk[3]+" in "+entry);}
				}
			}
			else{
				an = new Analysis(method, channel, type);
			}
			
			//System.out.println("Load "+method+" "+channel+" "+type);
			
			add(an);
			list.add( an );
		}
	}
	
	public void run(ArrayList<Cell> cells, double cellR, ImagePlus imp, Overlay ol, ResultsTable rt, int t){
		savePrefs();
		if(cells.size()==0){
			return;
		}
		for(Analysis an:list){
			an.parametersSelected();
			if(!cells.get(0).hasKey(an.key)){
				JOptionPane.showMessageDialog(this, "No data for "+an.key2, "Error", JOptionPane.ERROR_MESSAGE);
				continue;
			}
			if(!cells.get(0).hasKey(an.key2)){
				JOptionPane.showMessageDialog(this, "No data for "+an.key, "Error", JOptionPane.ERROR_MESSAGE);
				continue;
			}
			gui.setStatus("Running "+an.method.name+" analysis on "+an.key+"...");
			if(an.method==Method.POSITIVE_CALLS){
				PositiveCaller.run(cells, an.key, an.Zthresh, rt);
			}
			else if(an.method==Method.SUBPOPULATION){
				PositiveSubpopulation.run(cells, an.key, an.Zthresh, rt);
			}
			else if(an.method==Method.INTENSITY_DISTRIBUTION){
				IntensityDistribution.run(cells, an.key);
			}
			else if(an.method==Method.CORRELATION){
				if(!cells.get(0).hasKey(an.key2)){
					JOptionPane.showMessageDialog(this, "No data for "+an.key2, "Error", JOptionPane.ERROR_MESSAGE);
					continue;
				}
				Correlation.run(cells, an.key, an.key2);
			}
			else if(an.method==Method.RENDER){
				gui.setStatus("Rendering...");
				Render3D.getInstance(an.key, an.key2, imp).makeBuffers(cells, cellR, t);
			}
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent ae) {
		JButton src = (JButton) ae.getSource();
		if(src==addButton){
			Analysis an = new Analysis();
			add(an);
			list.add(an);
		}
		else if(src==subButton){
			if(list.size()>0){
				Analysis an = list.get(list.size()-1);
				remove(an);
				list.remove(an);
			}
		}
		
		gui.pack();
		
	}
	
}
