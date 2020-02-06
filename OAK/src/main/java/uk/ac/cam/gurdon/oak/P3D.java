package uk.ac.cam.gurdon.oak;

public class P3D{
	public double x,y,z;
	
	public P3D(double x, double y, double z){
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public double distance(P3D other){
		return Math.sqrt( ((other.x-x)*(other.x-x))+((other.y-y)*(other.y-y))+((other.z-z)*(other.z-z)) );
	}
	
	@Override
	public int hashCode(){
		return (int) (x*7+ y*23 +z*37);
	}
	
	@Override
	public boolean equals(Object other){
		if(!(other instanceof P3D)) return false;
		return hashCode() == other.hashCode();
	}
	
}