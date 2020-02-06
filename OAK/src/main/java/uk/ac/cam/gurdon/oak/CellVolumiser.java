package uk.ac.cam.gurdon.oak;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import ij.gui.Roi;
import ij.measure.Calibration;
import uk.ac.cam.gurdon.oak.gui.OAK_GUI;

public class CellVolumiser {
	
	private static final int MAXITS = 100;
	
	private double pixW, pixD, joinR;
	

	public CellVolumiser(Calibration cal, double joinR){
		this.joinR = joinR;
		this.pixW = cal.pixelWidth;
		this.pixD = cal.pixelDepth;
	}
	
	private P3D getCoord(Roi r){
		Rectangle rect = r.getBounds();
		double rx = ( rect.x+(rect.width/2) ) * pixW;
		double ry = ( rect.y+(rect.height/2) ) * pixW;
		double rz = r.getZPosition() * pixD;
		return new P3D(rx, ry, rz);
	}
	
	/*	K-means to cluster ROIs into Cells
	 */
	public ArrayList<Cell> get3DCells(ArrayList<Roi> roiList, OAK_GUI gui){
		try{
	
			int[] partClusterIndex = new int[roiList.size()];
			P3D[] partCoords = new P3D[roiList.size()];
			//ArrayList<P3D> centroids = new ArrayList<P3D>();
			HashMap<Integer, P3D> centroids = new HashMap<Integer, P3D>();
			for(int i=0;i<roiList.size();i++){
				partClusterIndex[i] = i;
				partCoords[i] = getCoord(roiList.get(i));
				centroids.put(i, partCoords[i]);
			}
			
			double zf = 0.2;	//give z coordinates less weight in all distance calculations
			
			boolean done = false;
			int its = 0;
			while(!done && its<MAXITS){
				gui.setStatus("Reconstructing "+roiList.size()+" ROIs - "+its+" iterations...");
				done = true;
				its ++;
				//assign roiList to closest centroids
				for(int p=0;p<roiList.size();p++){
					P3D partCoord = partCoords[p];
					double minCost = Double.POSITIVE_INFINITY;
					int mini = -1;
					for(int c=0;c<centroids.size();c++){
						if(centroids.get(c) == null){
							continue;
						}
						double dist = Math.sqrt( Math.pow(partCoord.x-centroids.get(c).x,2) + Math.pow(partCoord.y-centroids.get(c).y,2)+ zf*Math.pow(partCoord.z-centroids.get(c).z,2) );
						if(dist<minCost && dist<joinR){
							minCost = dist;
							mini = c;
						}
					}
					if(mini != -1 && partClusterIndex[p] != mini){
						partClusterIndex[p] = mini;
						done = false;
					}
				}
				//recalculate centroids
				//ArrayList<P3D> keepC = new ArrayList<P3D>();
				for(int c=0;c<centroids.size();c++){
					if(centroids.get(c) == null){
						continue;
					}
					double cx=0d; double cy=0d; double cz=0d; int n=0;
					for(int i=0;i<roiList.size();i++){
						if(partClusterIndex[i] == c){
							P3D partCoord = getCoord(roiList.get(i));
							cx += partCoord.x;
							cy += partCoord.y;
							cz += partCoord.z;
							n++;
						}
					}
					if(n==0){
						//centroids.remove(c);
						centroids.put(c, null);
						done = false;
					}
					else{
						//keepC.add( new P3D(cx/n, cy/n, cz/n) );
						P3D old = centroids.get(c);
						centroids.put(c, new P3D(cx/n, cy/n, cz/n));
						//int a = keepC.size()-1;
						
						double moved = Math.sqrt( Math.pow(old.x-centroids.get(c).x,2) + Math.pow(old.y-centroids.get(c).y,2) + zf*Math.pow(old.z-centroids.get(c).z,2) );
						if(moved>pixW){		//movement greater than the smallest distance between adjacent pixels -> not converged
							done = false;
						}
					}
				}
				//centroids = new ArrayList<P3D>(keepC);
				
				//merge centroids closer than joinR
				//keepC = new ArrayList<P3D>();
				for(int i1=0;i1<centroids.size();i1++){
					P3D c1 = centroids.get(i1);
					if(c1==null){continue;}
					double volx = c1.x;
					double voly = c1.y;
					double volz = c1.z;
					float n = 1;
					for(int i2=i1+1;i2<centroids.size();i2++){
						P3D c2 = centroids.get(i2);
						if( c2==null ){
							continue;
						}
						double dist = Math.sqrt( Math.pow(c1.x-c2.x,2) + Math.pow(c1.y-c2.y,2) + zf*Math.pow(c1.z-c2.z,2) );
						if(dist<joinR){
							volx += c2.x;
							voly += c2.y;
							volz += c2.z;
							n++;
							//centroids.remove(i2);
							centroids.put(i2, null);
							done = false;
						}
					}
					//keepC.add( new P3D(volx/n, voly/n, volz/n) );
					centroids.put(i1, new P3D(volx/n, voly/n, volz/n));
				}
				//centroids = new ArrayList<P3D>(keepC);
				
				
				
			}
			if(its>=MAXITS){
				System.out.println("getVolumes did not converge in "+MAXITS+" iterations");
			}
			
			//create objects from clusters
			ArrayList<Cell> cells = new ArrayList<Cell>();
			for(int c : centroids.keySet()){
				if(centroids.get(c)==null) continue;
				double volume = 0d;
				ArrayList<Roi> rois = new ArrayList<Roi>();
				for(int i=0;i<roiList.size();i++){
					if(partClusterIndex[i] == c){
						double area = roiList.get(i).getStatistics().area;
						volume += area;
						rois.add(roiList.get(i));
					}
				}
				volume *= pixW * pixW * pixD;
				
				Roi[] roiArr = rois.toArray(new Roi[rois.size()]);
				Cell cell = new Cell(centroids.get(c), roiArr);
				cell.setValue(Cell.VOLUME, volume);
				cells.add(cell);
			}
	
			return cells;
		}catch(Exception e){System.out.print(e.toString()+"\n~~~~~\n"+Arrays.toString(e.getStackTrace()).replace(",","\n"));}
		return null;
	}
	
}
