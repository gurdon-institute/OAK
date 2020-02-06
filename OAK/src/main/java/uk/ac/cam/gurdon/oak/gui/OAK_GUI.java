package uk.ac.cam.gurdon.oak.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.border.BevelBorder;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import uk.ac.cam.gurdon.oak.OAK;
import uk.ac.cam.gurdon.oak.RadiusEstimator;
import uk.ac.cam.gurdon.oak.RadiusEstimator.Method;
import uk.ac.cam.gurdon.oak.segment2d.SliceSegmenter;


public class OAK_GUI extends JFrame implements ActionListener{
	private static final long serialVersionUID = 7886684043605079140L;

	private OAK parent;
	
	private static final Integer[] CHANNELS = new Integer[]{1,2,3,4,5,6};
	private JComboBox<Integer> nucleusCombo;//, membraneCombo;
	private JComboBox<RadiusEstimator.Method> radiusMethodCombo;
	private KSpinner radiusSpinner, maxCorrectionSpinner, cytoExpSpinner;
	private JComboBox<String> methodCombo;
	private JComboBox<String> watershedCombo;
	private StatusBar statusBar;
	private AnalysisPanel ap;
	private ChannelPanel cp;

	private KButton radiusButton, volumeButton, runButton, previewButton, overlayButton, helpButton;
	
	
	private class StatusBar extends JPanel{
		private static final long serialVersionUID = 3429794231015354136L;
		
		private Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
		private Color BACKGROUND = new Color(192,192,192);
		private Color FOREGROUND = Color.BLACK;
		
		private JLabel label;
		
		private StatusBar(){
			super(new FlowLayout(FlowLayout.CENTER, 0,0));
			setBackground(BACKGROUND);
			setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			label = new JLabel("Hello "+System.getProperty("user.name").split(" ")[0]);
			label.setFont(FONT);
			label.setBorder(null);
			label.setBackground(BACKGROUND);
			label.setForeground(FOREGROUND);
			add(label);
		}
		
		public synchronized void setText(String txt){
			label.setText(txt);
		}
		
	}
	
	public OAK_GUI(OAK parent){
		super("OAK");
		this.parent = parent;
		
		setIconImage( Toolkit.getDefaultToolkit().getImage(getClass().getResource("logo_icon.gif")) );
		setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		
		nucleusCombo = new JComboBox<Integer>(CHANNELS);
		
		nucleusCombo.setSelectedIndex( (int) Prefs.get("Organoidist.nucleusC", 1)-1 );
		
		radiusSpinner = new KSpinner(Prefs.get("Organoidist.cellR",5.0), 1.0, 50.0, 0.1);
		cytoExpSpinner = new KSpinner(Prefs.get("Organoidist.cytoExp",1.0), 0.5, 50.0, 0.1);
		radiusButton = new KButton("Cell Radius", this);
		methodCombo = new JComboBox<String>(SliceSegmenter.METHODS);
		methodCombo.setSelectedIndex( (int) Prefs.get("Organoidist.methodi", 0) );
		
		radiusMethodCombo = new JComboBox<RadiusEstimator.Method>(RadiusEstimator.Method.values());
		radiusMethodCombo.setSelectedIndex( (int) Prefs.get("Organoidist.radiusMethodi", 0) );
		
		watershedCombo = new JComboBox<String>(SliceSegmenter.WATERSHEDS);
		watershedCombo.setSelectedIndex( (int) Prefs.get("Organoidist.watershedi", 0) );
		
		maxCorrectionSpinner = new KSpinner(4.0, 1.0, 20.0, 0.1);
		
		cp = new ChannelPanel(this);
		ImagePlus.addImageListener(cp);
		addWindowListener(new WindowAdapter(){
			@Override
			public void windowClosed(WindowEvent we){
				ImagePlus.removeImageListener(cp);
			}
		});
		
		volumeButton = new KButton("Volume Map", this);
		runButton = new KButton("Run", this);
		previewButton = new KButton("Preview", this);
		overlayButton = new KButton("O", this);
		helpButton = new KButton("?", this);
		
		statusBar = new StatusBar();
		
		ap = new AnalysisPanel(this);
		
		add(Box.createVerticalStrut(10));
		add( new KPanel("Nucleus Channel:", nucleusCombo) );
		add( new KPanel("Segmentation Method:", methodCombo) );
		add( new KPanel("Watershed:", watershedCombo) );

		add( new KPanel("Nucleus Radius:", radiusSpinner, "\u00B5m") );
		add( new KPanel("Estimate Nucleus Radius: ", radiusMethodCombo, radiusButton) );
		
		add( new KPanel("Cytoplasm Radius:", cytoExpSpinner, "\u00B5m") );
		add(Box.createVerticalStrut(10));
		add( new KPanel("Max Depth Correction Factor :", maxCorrectionSpinner) );
		add(Box.createVerticalStrut(10));
		add(Box.createVerticalStrut(10));
		add(new KPanel(cp));
		add(new KPanel(ap));
		add(Box.createVerticalStrut(10));
		add( new KPanel(helpButton, overlayButton, 30, runButton, volumeButton, 10, previewButton ) );
		add(Box.createVerticalStrut(10));
		add(statusBar);
	}
	
	public void display(){
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}
	
	public void setStatus(String txt){
		statusBar.setText(txt);
	}
	
	private void setEnabled(Component comp, boolean en){
		if(!(comp instanceof JLabel) && !(comp instanceof JFrame)){	//keep JLabels legible and don't disable JFrame
			comp.setEnabled(en);
		}
		if (comp instanceof Container) {
	        for (Component c : ((Container) comp).getComponents()) {
	        	setEnabled(c, en);
	        }
	    }
	}
	
	public AnalysisPanel getAnalysisPanel(){
		return ap;
	}
	
	public ChannelPanel getChannelPanel(){
		return cp;
	}
	
	@Override
	public void actionPerformed(ActionEvent ae) {
		final OAK_GUI finalThis = this;
		setEnabled(finalThis,false);
		SwingWorker<Object, Void> worker = new SwingWorker<Object, Void>(){
			public Object doInBackground(){
				try{
					KButton src = (KButton) ae.getSource();
					int nucleusC = (Integer) nucleusCombo.getSelectedItem();
					//int membraneC = (Integer) membraneCombo.getSelectedItem();
					int methodi = methodCombo.getSelectedIndex();
					double cellR = (double) radiusSpinner.getValue();
					double cytoExpR = (double) cytoExpSpinner.getValue();
					double maxCorrection = (double) maxCorrectionSpinner.getValue();
					int watershedi = watershedCombo.getSelectedIndex();
					parent.setNucleusC(nucleusC);
					parent.setCellRadius(cellR);
					parent.setCytoplasmExpRadius(cytoExpR);
					parent.setMaxCorrectionFactor(maxCorrection);
					
					Prefs.set("Organoidist.nucleusC", nucleusC);
					Prefs.set("Organoidist.cellR", cellR);
					Prefs.set("Organoidist.cytoExpR", cytoExpR);
					Prefs.set("Organoidist.methodi", methodi);
					Prefs.set("Organoidist.radiusMethodi", radiusMethodCombo.getSelectedIndex());
					Prefs.set("Organoidist.watershedi", watershedi);
					
					if(src==radiusButton){
						RadiusEstimator.Method radiusMethod = (Method) radiusMethodCombo.getSelectedItem();
						statusBar.setText("Estimating radius by "+radiusMethod.name()+"...");
						RadiusEstimator.run( IJ.getImage(), radiusMethod );
						statusBar.setText("Radius estimate complete");
					}
					else if(src==volumeButton){
						parent.organoidVolume(true);
						statusBar.setText("volume map complete");
					}
					else if(src==runButton){
						parent.segmentCells3D(methodi, watershedi, false);
						statusBar.setText("Run complete");
					}
					else if(src==previewButton){
						parent.segmentCells3D(methodi, watershedi, true);
						statusBar.setText("Preview complete");
					}
					else if(src==overlayButton){
						ImagePlus imp = IJ.getImage();
						boolean hide = !imp.getHideOverlay();
						imp.setHideOverlay( hide );
					}
					else if(src==helpButton){
						HelpFrame.display(finalThis);
					}

					setEnabled(finalThis, true);
					
				}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
				return null;
			}
		};
		worker.execute();
	}
	
}
