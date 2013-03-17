package edu.uchc.octane;

import java.util.prefs.Preferences;

import ij.gui.GenericDialog;

public class DspHistogramParameters {
	final static String STEPSIZE_KEY = "stepSize";
	final static String HISTOGRAM_BINS_KEY = "histogramBins";
	final static String HISTOGRAM_MIN_KEY = "histogramMin";
	final static String HISTOGRAM_MAX_KEY = "histogramMax";
	
	private static Preferences prefs_ = GlobalPrefs.getRoot().node(DspHistogramParameters.class.getName());
	
	public static int stepSize_ = prefs_.getInt(STEPSIZE_KEY, 1);
	public static int histogramBins_ = prefs_.getInt(HISTOGRAM_BINS_KEY, 20);
	public static double dspHistogramMin_ = prefs_.getDouble(HISTOGRAM_MIN_KEY, 0);
	public static double dspHistogramMax_ = prefs_.getDouble(HISTOGRAM_MAX_KEY, 10);
	
	public static boolean showDialog() { 
		GenericDialog gd = new GenericDialog("Step Size Input");
		gd.addNumericField("Step Size: ", stepSize_, 0);
		gd.addNumericField("Number of bins:", histogramBins_, 0);
		gd.addNumericField("Minimal value:", dspHistogramMin_, 0);
		gd.addNumericField("Maximal value:", dspHistogramMax_, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		
		stepSize_ = (int) gd.getNextNumber();
		histogramBins_ = (int) gd.getNextNumber();
		dspHistogramMin_ = gd.getNextNumber();
		dspHistogramMax_ = gd.getNextNumber();
		
		prefs_.putInt(STEPSIZE_KEY, stepSize_);
		prefs_.putInt(HISTOGRAM_BINS_KEY, histogramBins_);
		prefs_.putDouble(HISTOGRAM_MIN_KEY, dspHistogramMin_);
		prefs_.putDouble(HISTOGRAM_MAX_KEY, dspHistogramMax_);
		
		return true;
		
	}

}
