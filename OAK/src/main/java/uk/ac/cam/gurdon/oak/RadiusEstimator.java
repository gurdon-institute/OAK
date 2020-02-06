package uk.ac.cam.gurdon.oak;

import ij.ImagePlus;


public class RadiusEstimator {

	public enum Method {
		Profiles, Autocorrelation;
	}

	public static void run(ImagePlus image, Method method) throws Exception {
		switch(method){
			case Profiles:
				SizeProfile.run(image);
				break;
			case Autocorrelation:
				AutoCorrelator.calculate(image);
				break;
			default:
				throw new Exception("Unknown radius estimation method: "+method);
		}
		
	}

}
