//FILE:          ThresholdDialog.java
//PROJECT:       Octane
//-----------------------------------------------------------------------------
//
// AUTHOR:       Ji Yu, jyu@uchc.edu, 2/16/08
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES./**
//
package edu.uchc.octane;


import java.awt.AWTEvent;
import java.awt.Scrollbar;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.io.FileInfo;
import ij.process.ImageProcessor;

public class ThresholdDialog implements ImageListener {
	ImagePlus imp_;
	PeakFinder finder_;
	NonBlockingGenericDialog dlg_;
	
	public ThresholdDialog(ImagePlus imp) {
		imp_ = imp;

		finder_ = new PeakFinder();
		finder_.setRoi(imp_.getRoi());
		finder_.setImageProcessor(imp_.getProcessor());
		finder_.setThreshold(ImageProcessor.NO_THRESHOLD);
		
		dlg_ = new NonBlockingGenericDialog("Set Threshold:" + imp.getTitle());
	}

	public boolean openDialog() {
		dlg_.addSlider("Threshold", 0, 40000.0, finder_.getTolerance());

		Vector<Scrollbar> sliders = (Vector<Scrollbar>)dlg_.getSliders();
		Scrollbar slider = sliders.get(0);
		slider.setUnitIncrement(20); // default was 1 
		
		dlg_.addDialogListener(new DialogListener() {
			public boolean dialogItemChanged(GenericDialog gd, AWTEvent ev) {
				double tol = gd.getNextNumber();
				if (tol != finder_.getTolerance()) {
					finder_.setTolerance(tol);
					updateMaximum();
				}
				return true;	
			}
		});

		imp_.getWindow().addWindowListener(new WindowAdapter() {
			@Override
			public void windowIconified(WindowEvent e) {
					dlg_.setVisible(false);
			}
			
			@Override
			public void windowDeiconified(WindowEvent e) {
				if (dlg_.isDisplayable())
					dlg_.setVisible(true);
			}
			
			@Override
			public void windowClosed(WindowEvent e) {
				//dlg_.notify();
				dlg_.dispose();
			}
		});

		ImagePlus.addImageListener(this);
		updateMaximum();
		dlg_.showDialog();
		if (dlg_.wasOKed()) {
			return processStack();
		} else {
			return false;
		}
	}

	public void updateMaximum() {
		imp_.killRoi();
		finder_.findMaxima(imp_);
		finder_.markMaxima(imp_);
	}

	public boolean processStack() {
		String path_ = null;			

		imp_.killRoi();
		BufferedWriter writer = null;

		FileInfo fi = imp_.getOriginalFileInfo();
		path_ = fi.directory;
		String fn = path_ + "analysis" + File.separator + "positions";
		File file = new File(fn);
		int nFound = 0;
		int nMissed = 0;
		IJ.log(imp_.getTitle() + ": Processing");
		try {
			(new File(path_ + "analysis")).mkdirs();
			writer = new BufferedWriter(new FileWriter(file));
			ImageStack stack = imp_.getImageStack();
			for (int frame = 1; frame <= stack.getSize(); frame++) {
				if ((frame % 50) == 0) {
					IJ.log("Processed: "+ frame + "frames.");
				}
				IJ.showProgress(frame, stack.getSize());
				ImageProcessor ip = stack.getProcessor(frame);
				finder_.setImageProcessor(ip);
				nFound += finder_.findMaxima(imp_);
				nMissed += finder_.refineMaxima();
				finder_.saveMaxima(writer, frame);
			}
			writer.close();
		} catch (IOException e) {
			IJ.showMessage("IO error: " + e.getMessage());
			return false;
		}
		IJ.log(imp_.getTitle() + "- Tested:" + nFound + " Missed:" + nMissed);
		
		return true;
	}

	public void setImageProcessor(ImageProcessor ip) {
		if (dlg_.isVisible()) {
			finder_.setImageProcessor(ip);
			updateMaximum();
		}
	}

	@Override
	public void imageOpened(ImagePlus imp) {
	}

	@Override
	public void imageClosed(ImagePlus imp) {
	}

	@Override
	public void imageUpdated(ImagePlus imp) {
		if (imp == imp_) {
			if ( dlg_.isVisible() ) {
				finder_.setImageProcessor(imp.getProcessor());
				updateMaximum();				
			}
		}
	}

}
