package uk.ac.cam.gurdon.oak.gui;

import java.awt.Dimension;
import java.awt.Toolkit;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;

public class HelpFrame {
	
	private static JFrame frame;
	private static String help =
	  "<html>"
	+ "<body style='font-family:sans-serif;font-size:14pt;'>"
	+ "<h2>Organoid Analysis Kit</h2>"
	+ "<i> - by Richard Butler, Gurdon Institute Imaging Facility.</i>"
	+ "<br>This plugin is a kit of methods for volume mapping, segmentation and analysis of organoid images taken on widefield, confocal or light-sheet microscopes."
	
	+ "<br><br><h3>Nucleus Channel</h3>"
	+ "The channel to use for nucleus segmentation. Nuclei and surrounding volumes will be measured in all channels."
	
	+ "<br><br><h3>Segmentation Method</h3>"
	+ "Methods for detection of cells."
	+ "<br><b>Laplacian zero-crossings</b> - detects closed contours along intensity gradient edges."
	+ "<br><b>DoG</b> - Difference of Gaussians blob detector, finds blobs within a frequency range based on the cell radius."
	+ "<br><b>HKM</b> - Hierarchical Agglomerative K-Means, finds objects at multiple intensity levels."
	+ "<br><b>Adaptive Local Thresholding</b> - calculates a threshold at each pixel position using Phansalkar's method to classify signal and background."
	
	+ "<br><br><h3>Watershed</h3>"
	+ "Methods for additional watershed segmentation of detected cells."
	+ "<br><b>None</b> - no watershed segmentation."
	+ "<br><b>Mild</b> - separate only at highly concave features (tolerance 5.0)."
	+ "<br><b>Standard</b> - the standard ImageJ watershed (tolerance 0.5)."
	+ "<br><b>Strong</b> - aggressive segmentation at concave boundary positions (tolerance 0.05)."
	+ "<br><b>Irregular</b> - filters division lines to allow irregular objects where the watershed would oversegment based on the cell radius."
	
	+ "<br><br><h3>Nucleus Radius</h3>"
	+ "The radius of objects to detect, applied to all segmentation methods to determine parameters and filter results."

	+ "<br><br><h3>Estimate Nucleus Radius</h3>"
	+ "Methods to automatically predict a cell radius to be used for segmentation or volume map cell count estimates."
	+ "<br><b>Profiles</b> - takes multiple line profiles for each slice of the current channel and frame, uses peak frequency of the profile derivative to estimate cell size."
	+ "<br><b>Autocorrelation</b> - calculates the autocorrelation function for the current slice, gives Half Width at Half Maximum and power spectral density frequency as estimates of cell size."
	
	+ "<br><br><h3>Cytoplasm Radius</h3>"
	+ "The radius around nuclei to measure as cytoplasm."
	
	+ "<br><br><h3>Max Depth Correction Factor</h3>"
	+ "The maximum correction factor to apply to z-slice intensity levels. Correction scales all slices to match the intensity of the brightest one. A maximum of 1.0 means no correction will be applied."
	
	+ "<br><br><h3>Channel Measurement Options</h3>"
	+ "The features of interest for each channel to be measured, added to the results table and passed to analysis methods."
	
	+ "<br><br><h3>Analysis Methods</h3>"
	+ "Use the + button to add an analysis method and the - button to remove the last method in the list."
	+ "<br><b>Positive Calls</b> - makes +/-ve calls for the selected feature based on the Z-score threshold set."
	+ "<br><b>Positive Sub-Population</b> - makes +/-ve calls and runs analysis of the positive sub-population spatial distribution. Calculates clustering coefficients to quantify separation of positive and negative cells into sub-populations, and overall polarity of the selected feature."
	+ "<br><b>Intensity Distribution</b> - plots a relative frequency histogram for the selected feature."
	+ "<br><b>Correlation</b> - calculates Pearson's correlation coefficient by cell for the selected features."
	+ "<br><b>3D Render</b> - generates an interactive 3D render of the image with nucleus markers showing relative intensity of the selected features."
	
	+ "<br><br><h3>Buttons</h3>"
	+ "<b>O</b> - toggle display of the overlay on the current image."
	+ "<br><b>Run</b> - full segmentation and measurement in 3D. Cells are assigned a unique index and colour. Double-click the results table to target the cell in the image."
	+ "<br><b>Volume Map</b> - maps organoid volume only with no cell segmentation. Uses cell radius to estimate cell count from the volume."
	+ "<br><b>Preview</b> - segments only the current slice, shows accepted 2D objects in green and rejected candidates in red."
	+ "<br>"
	+ "<br><i style='font-size:10pt;'>Copyright 2019,2020 Richard Butler. OAK is released under the GNU General Public License v3.</i>"
	+ "</body>"
	+ "</html>";
	
	public static void display(JFrame parent){
		if(frame==null){
			frame = new JFrame("OAK Help"){
				private static final long serialVersionUID = 877293072246213770L;
				final Dimension display = Toolkit.getDefaultToolkit().getScreenSize();
				private Dimension DIM = new Dimension(display.width/2, display.height/2);
				
				@Override
				public Dimension getPreferredSize(){
					return DIM;
				}
			};
			frame.setIconImage( Toolkit.getDefaultToolkit().getImage(frame.getClass().getResource("logo_icon.gif")) );
			JEditorPane ed = new JEditorPane("text/html", help);
			ed.setEditable(false);
			JScrollPane scroll = new JScrollPane(ed);
			frame.add(scroll);
		}
		frame.pack();
		frame.setLocationRelativeTo(parent);
		frame.setVisible(true);
	}
	
}
