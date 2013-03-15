//FILE:          OctaneWindowControl.java
//PROJECT:       Octane
//-----------------------------------------------------------------------------
//
// AUTHOR:       Ji Yu, jyu@uchc.edu, 1/16/11
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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.HistogramWindow;
import ij.gui.ImageCanvas;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.FileInfo;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;


/**
 * Controller of the the octane window.
 */
public class OctaneWindowControl implements ClipboardOwner{

	ImagePlus imp_ = null;
	TrajDataset dataset_ = null;
	protected String path_;
	protected Animator animator_ = null;	
	OctaneWindow frame_ = null;

	//double [] drift_x_; 
	//double [] drift_y_;
	//boolean useFiducial_;
	
	public enum IFSType {GaussianSpot, LineOverlay, SquareOverlay};

	/**
	 * Constructor
	 *
	 * @param imp the image
	 */
	public OctaneWindowControl(ImagePlus imp) {
		super();
		
		path_ = null;
		imp_ = imp;
		FileInfo fi = imp.getOriginalFileInfo();
		if (fi != null) {
			path_ = fi.directory; 
		} 
		
		imp.getWindow().addWindowListener(new WindowAdapter() {
			boolean isVisible;
			@Override
			public void windowIconified(WindowEvent e) {
				isVisible = frame_.isVisible();
				frame_.setVisible(false);
			}
			
			@Override
			public void windowDeiconified(WindowEvent e) {
				frame_.setVisible(isVisible);
			}
			
			@Override
			public void windowClosed(WindowEvent e) {
				frame_.dispose();
			}
		});
		
		imp.getCanvas().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 ) {
					findMolecule();
				}
			}
		});
	}
	
	/**
	 * Load dataset from default disk location
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ClassNotFoundException the class not found exception
	 */
	public void setup() throws IOException, ClassNotFoundException {
		loadDataset();
		createWindow();
	}

	/**
	 * Setup window using provided dataset
	 *
	 * @param data the dataset
	 */
	public void setup(TrajDataset data) {
		dataset_ = data;
		createWindow();
	}

	/**
	 * Setup window from positions of molecules.
	 *
	 * @param nodes positions of molecules
	 */
	public void setup(SmNode[][] nodes) {
		dataset_ = TrajDataset.createDatasetFromNodes(nodes);
		createWindow();
	}

	protected void createWindow() {
		frame_ = new OctaneWindow();
		frame_.setController(this);
		frame_.setVisible(true);
	}

	/**
	 * Returns the window object.
	 *
	 * @return the window
	 */
	public OctaneWindow getWindow() {
		return frame_;
	}

	/**
	 * Gets the dataset.
	 *
	 * @return the data
	 */
	public TrajDataset getData() {
		return dataset_;
	}

	protected void selectTrajectoriesWithinRoi() {
		Roi roi = imp_.getRoi();
		if (roi == null) {
			return;
		}
		if (roi instanceof PointRoi && ((PointRoi) roi).getNCoordinates() == 1) {
			int frame = imp_.getFrame();
			int x = ((PointRoi) roi).getXCoordinates() [0];
			int y = ((PointRoi) roi).getYCoordinates() [0];
			findMolecule(x,y,frame);
		} else {
			boolean firstSel = true;
			for (int i = 0; i < dataset_.getSize(); i++) {
				Trajectory t = dataset_.getTrajectoryByIndex(i);
				if (!t.deleted) {
					for (int j = 0; j< t.size(); j++) {
						if (roi.contains( (int)t.get(j).x, (int)t.get(j).y)) {
							if (firstSel) {
								frame_.selectTrajectoryByIndex(i);
								firstSel = false;
							} else {
								frame_.addTrajectoriesToSelection(i);
							}
							break;
						}
					}
				}
			}
		}
	}
	
	protected void copySelectedTrajectories() {
		StringBuilder buf = new StringBuilder();
		int [] selected = frame_.getTrajsTable().getSelectedTrajectories();
		Trajectory traj;
		for (int i = 0; i < selected.length; i++) {
			traj = dataset_.getTrajectoryByIndex(selected[i]);
			for (int j = 0; j < traj.size(); j++) {
				buf.append(String.format("%10.4f, %10.4f, %10d, %5d%n", traj.get(j).x, traj.get(j).y, traj.get(j).frame, i));
			}
		}
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		StringSelection contents = new StringSelection(buf.toString());
		clipboard.setContents(contents, this);		
	}

	protected void findMolecule(int x, int y, int f) {
		int index = 0;
		int fi = 0;
		boolean found = false;
		int lastIndex = dataset_.getSize();
		while (!found && index < lastIndex) {
			Trajectory t = dataset_.getTrajectoryByIndex(index);
			if (t != null && t.size() > 0 && t.get(0).frame <= f && t.get(t.size()-1).frame >= f) {
				fi = f - t.get(0).frame;
				if (fi >= t.size()) { 
					fi = t.size() - 1;
				}
				while (t.get(fi).frame > f) {
					fi --;
				}
				if (t.get(fi).frame == f) {
					if ( Math.abs(t.get(fi).x - x) < 2.5 && Math.abs(t.get(fi).y - y) < 2.5) {
						found = true;
					}
				}
			}
			index ++;
		}
		if (found) {
			frame_.selectTrajectoryAndNodeByIndex(index - 1 , fi);
		}		
	}
	
	protected void findMolecule() {
		ImageCanvas canvas = imp_.getCanvas();
		Point p = canvas.getCursorLoc();
		int frame = imp_.getSlice();
		
		findMolecule(p.x, p.y, frame);
	}

	protected void drawOverlay() {
		if (!Prefs.showOverlay_) {
			imp_.setOverlay(null);
			return;
		}
		GeneralPath path = new GeneralPath();
		for (int i = 0; i < dataset_.getSize(); i ++) {
			Trajectory v = dataset_.getTrajectoryByIndex(i);
			if ( v.marked ) {
				path.append(new Arc2D.Double(v.get(0).x-0.15,v.get(0).y,0.3,0.3,0,360,Arc2D.OPEN), false);
				path.moveTo(v.get(0).x, v.get(0).y);
				for (int j = 1; j < v.size(); j++) {
					path.lineTo(v.get(j).x, v.get(j).y);
				}
				path.append(new Rectangle2D.Double(v.get(v.size()-1).x-0.15,v.get(v.size()-1).y-0.15,0.3,0.3), false);
			}
		}
		imp_.setOverlay(path, Color.yellow, new BasicStroke(1f));			
	}

	protected void drawBox() {
		SmNode node = frame_.getNodesTable().getCurrentNode();
		int x,y;
		if (node != null && imp_ != null) {
			imp_.setSlice(node.frame);
			x = (int) Math.round(node.x);
			y = (int) Math.round(node.y);
			imp_.setRoi(x-5,y-5,11,11);
			ImageCanvas canvas = imp_.getCanvas();
			Rectangle r = canvas.getSrcRect();
			int sx = canvas.screenX(x);
			int sy = canvas.screenY(y);
			if (sx < 4 || sx > r.width - 5 || sy < 4 || sy > r.height - 5) {
				int nx = Math.max(x - r.width/2, 0);
				int ny = Math.max(y - r.height/2, 0);
				if (nx + r.width > imp_.getWidth()) {
					nx = imp_.getWidth() - r.width;
				}
				if (ny + r.height > imp_.getHeight()){
					ny = imp_.getHeight() - r.height;
				}
				canvas.setSourceRect(new Rectangle(nx, ny, r.width, r.height));
				imp_.updateAndDraw();
			}
		}
	}

//	/**
//	 * Construct ifs stack.
//	 */
//	protected void constructIFS() {
//		Palm.IFSType [] typeList = {
//				Palm.IFSType.SPOT,
//				Palm.IFSType.LINE,
//				Palm.IFSType.SQUARE,
//		};
//
//		GenericDialog dlg = new GenericDialog("Construct IFS");
//		String[] items = { "Spot", "Line", "Square"};
//		dlg.addChoice("IFS Type", items, "Spot");
//		dlg.addNumericField("Scale Factor", Prefs.IFSScaleFactor_, 0);
//		dlg.addNumericField("PSF width", Prefs.palmPSDWidth_, 3);
//
//		dlg.showDialog();
//		if (dlg.wasCanceled())
//			return;
//		
//		Palm palm = new Palm(dataset_);
//	
//		int [] selected = frame_.getTrajsTable().getSelectedTrajectoriesOrAll();
//		
//		palm.constructIFS(Palm.IFSType.SPOT, imp_, selected);
//	}

	/**
	 * Construct PALM image.
	 */
	protected void constructPalm() {
		Palm.PalmType [] typeList = {
				Palm.PalmType.AVERAGE,
				Palm.PalmType.HEAD,
				Palm.PalmType.TAIL,
				Palm.PalmType.ALLPOINTS,
				Palm.PalmType.STACK
		};
		GenericDialog dlg = new GenericDialog("Construct PALM");
		String[] items = { "Average", "Head", "Tail", "All Points", "Stack"};
		dlg.addChoice("PALM Type", items, "Average");
		dlg.addNumericField("Scale Factor", Prefs.palmScaleFactor_, 0);
		dlg.addNumericField("PSF width", Prefs.palmPSDWidth_, 3);

		dlg.showDialog();
		if (dlg.wasCanceled())
			return;

		Palm palm = new Palm(dataset_);
		
		int palmType = dlg.getNextChoiceIndex();
		Prefs.palmScaleFactor_ = dlg.getNextNumber();
		Prefs.palmPSDWidth_ = dlg.getNextNumber();

		// palm.setCorrectDrift(dlg.getNextBoolean());
		
		int [] selected = frame_.getTrajsTable().getSelectedTrajectoriesOrAll();
		palm.constructPalm(typeList[palmType], imp_, selected);
	}

	/**
	 * Construct mobility map.
	 */
	public void constructMobilityMap() {
		FlowAnalysis fa = new FlowAnalysis(dataset_);
		fa.constructMobilityMap(imp_, frame_.getTrajsTable().getSelectedTrajectoriesOrAll());
	}
	
	/**
	 * Gets the Imagej image.
	 *
	 * @return the imageplus
	 */
	public ImagePlus getImp() {
		return imp_;
	}

	/**
	 * Select all (un)marked trajectory in the trajectory table.
	 *
	 * @param b select marked if true, unmarked if false
	 */
	public void selectMarked(boolean b) {
		frame_.selectTrajectoryByIndex(-1); //clear selection
		for (int i = 0; i < dataset_.getSize(); i++) {
			if (dataset_.getTrajectoryByIndex(i).marked == b) {
				frame_.addTrajectoriesToSelection(i);
			}
		}
	}

	/**
	 * Rebuild trajectories.
	 */
	public void rebuildTrajectories(){
		dataset_.reTrack();
		frame_.getTrajsTable().setData(dataset_);
	}

	/**
	 * Show trajectory length histogram.
	 */
	public void showLengthHistogram() {
		int [] selected = frame_.getTrajsTable().getSelectedTrajectoriesOrAll();
		short [] d = new short[selected.length];
		
		int min = 10000;
		int max = -1;
		for (int i = 0; i < selected.length; i++) {
			Trajectory t = dataset_.getTrajectoryByIndex(selected[i]);
			d[i] = (short) (t.getLength());
			if (d[i] > max) {
				max = d[i];
			}
			if (d[i]< min) {
				min = d[i];
			}
		}
		ShortProcessor ip = new ShortProcessor(1, d.length, d, null);
		ImagePlus imp = new ImagePlus("",ip);
		HistogramWindow hw = new HistogramWindow("Trajectory Length Histogram", imp, max-min);
		hw.setVisible(true);
		imp.close();
	} 

	/**
	 * Show fitting residue histogram.
	 */
	public void showResidueHistogram() {
		int [] selected = frame_.getTrajsTable().getSelectedTrajectoriesOrAll();
		//ArrayList<Double> d = new ArrayList<Double>();
		int numOfNodes = 0;
		for ( int i= 0; i < selected.length; i++) {
			numOfNodes += dataset_.getTrajectoryByIndex(selected[i]).size();
		}
		double [] d = new double[numOfNodes];
		int cnt = 0;
		for ( int i= 0; i < selected.length; i++) {
			Iterator<SmNode> itr = dataset_.getTrajectoryByIndex(selected[i]).iterator();
			while (itr.hasNext()) {
				d[cnt++] = itr.next().residue;
			}
		}
		FloatProcessor ip = new FloatProcessor(1, d.length, d);
		ImagePlus imp = new ImagePlus("", ip);
		HistogramWindow hw = new HistogramWindow("Residue Histogram", imp, Prefs.histogramBins_);
		hw.setVisible(true);
		imp.close();		
	}

	/**
	 * Show displacement histogram.
	 * 
	 * @param stepSize the stepsize for calculating the displacement
	 */
	public void showDisplacementHistogram(int stepSize) {
		int [] selected = frame_.getTrajsTable().getSelectedTrajectoriesOrAll();
		ArrayList<Double> dl = new ArrayList<Double>();
		for (int i = 0; i < selected.length; i++) {
			Trajectory t = dataset_.getTrajectoryByIndex(selected[i]);
			for (int j = 0; j < t.size() - stepSize; j++) {
				int k = j + 1;
				int frame = t.get(j).frame;
				while ( k < t.size()) {
					if (t.get(k).frame - frame < stepSize) {
						k++;
					} else if (t.get(k).frame - frame == stepSize) {
						dl.add(t.get(j).distance(t.get(k)));
						break;
					} else {
						break;
					}
				}
			}
			//IJ.showProgress(i, selected.length);
		}
		double [] d = new double[dl.size()];
		for (int i = 0; i < dl.size(); i ++) {
			d[i] = dl.get(i).doubleValue();
		}
		if (d.length <= 1) {
			IJ.showMessage("Not enough data point. Stepsize too large?");
			return;
		}
		FloatProcessor ip = new FloatProcessor(1, d.length, d);
		ImagePlus imp = new ImagePlus("", ip);
		HistogramWindow hw = new HistogramWindow("Displacement Histogram", imp, Prefs.histogramBins_, 
				Prefs.dspHistogramMin_, Prefs.dspHistogramMax_);
		hw.setVisible(true);
		imp.close();
	}

	/**
	 * Show directional displacement histogram.
	 * 
	 * @param stepSize the stepsize for calculating the displacement
	 * @param dir direction of the displacement in degrees. 0 is up
	 */
	public void showDirectionalDisplacementHistogram(int stepSize, double dir) {
		double [] v = new double[2];
		v[0] = Math.sin(dir * Math.PI / 180);
		v[1] = - Math.cos(dir * Math.PI / 180);
		int [] selected = frame_.getTrajsTable().getSelectedTrajectoriesOrAll();
		ArrayList<Double> dl = new ArrayList<Double>();
		for (int i = 0; i < selected.length; i++) {
			Trajectory t = dataset_.getTrajectoryByIndex(selected[i]);
			for (int j = 0; j < t.size() - stepSize; j++) {
				int k = j + 1;
				int frame = t.get(j).frame;
				while ( k < t.size()) {
					if (t.get(k).frame - frame < stepSize) {
						k++;
					} else if (t.get(k).frame - frame == stepSize) {
						dl.add((t.get(j).x - t.get(k).x)*v[0] + (t.get(j).y-t.get(k).y)*v[1]);
						break;
					} else {
						break;
					}
				}
			}
			IJ.showProgress(i, selected.length);
		}
		double [] d = new double[dl.size()];
		for (int i = 0; i < dl.size(); i ++) {
			d[i] = dl.get(i).doubleValue();
		}
		if (d.length <= 1) {
			IJ.showMessage("Not enough data point. Stepsize too large?");
			return;
		}
		FloatProcessor ip = new FloatProcessor(1, d.length, d);
		ImagePlus imp = new ImagePlus("", ip);
		HistogramWindow hw = new HistogramWindow("Directional Displacement Histogram", imp, Prefs.histogramBins_);
		hw.setVisible(true);
		imp.close();
	}
	
	/**
	 * Show mean square displacement.
	 */
	public void showMSD(int maxSteps) {
		int [] selected = frame_.getTrajsTable().getSelectedTrajectoriesOrAll();
		ArrayList<SummaryStatistics> stat = new ArrayList<SummaryStatistics>();
		for (int i = 0; i < selected.length; i ++) {
			Trajectory t = dataset_.getTrajectoryByIndex(selected[i]);
			for (int j = 0; j < t.size()-1; j++) {
				int frame = t.get(j).frame;
				for (int k = j + 1; k < t.size(); k++) {
					int deltaframe = t.get(k).frame - frame;
					if (deltaframe <= maxSteps) {
						while (deltaframe > stat.size()) {
							stat.add(new SummaryStatistics());
						}
						stat.get(deltaframe - 1).addValue(t.get(j).distance2(t.get(k)));
					}
				}
			}
			IJ.showProgress(i, selected.length);
		}
		double [] x = new double [stat.size() + 1];
		double [] y = new double [stat.size() + 1];
		double [] e = new double [stat.size() + 1];
		x[0] = 0;
		y[0] = 0;
		if ( stat.size()>0 ) {
			for (int i = 0 ; i < stat.size(); i++) {
				x[i+1] = 1.0 + i;
				y[i+1] = stat.get(i).getMean();
				e[i+1] = stat.get(i).getStandardDeviation() / Math.sqrt(stat.get(i).getN());
			}
			Plot plotWin = new Plot("MSD Plot", "T/T-frame", "MSD (pixel^2)", x, y);
			plotWin.addPoints(x, y, Plot.BOX);
			plotWin.addErrorBars(e);
			plotWin.show();
		} 
	}

	/**
	 * Animate the current trajectory.
	 */
	public void animate() {
		if (animator_ == null) {
			animator_ = new Animator(imp_);
			animator_.setLoop(true);
		}
		
		int index= frame_.getTrajsTable().getSelectedTrajectoryIndex();
		if (index >=0) {
			animator_.animate(dataset_.getTrajectoryByIndex(index));
		}
		
	}

	/**
	 * Stop the animation
	 */
	public void stopAnimation() {
		if (animator_ == null )
			return;
		
		animator_.stopAnimation();
	}
			
	/**
	 * Delete all selected trajectories from trajectory table.
	 */
	public void deleteSelectedTrajectories() {
		int [] selected = frame_.getTrajsTable().getSelectedTrajectories();
		for (int i = 0; i < selected.length; i ++) {
			dataset_.getTrajectoryByIndex(selected[i]).deleted = true;
		}
	}

	/**
	 * Returns the default pathname for saving the dataset.
	 *
	 * @return the pathname
	 */
	protected String defaultSaveFilename() {
		final String s = path_ + File.separator + imp_.getTitle() + ".dataset";
		return s;
	}

	/**
	 * Save the dataset to disk.
	 *
	 * @param pathname the pathname
	 */
	public void saveDataset(String pathname) {
		try {
			File file = new File(pathname);
			dataset_.saveDataset(file);
		} catch (IOException e) {
			IJ.showMessage("IOError: Failed to save file.");
		}
		
	}

	/**
	 * Save the dataset using the default pathname (imagetitle + .dataset).
	 */
	public void saveDataset() {
		saveDataset(defaultSaveFilename());
	}

	/**
	 * Load dataset from disk.
	 *
	 * @param pathname the pathname
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ClassNotFoundException the class not found exception
	 */
	public void loadDataset(String pathname) throws IOException, ClassNotFoundException {
		File file = new File(pathname);
		dataset_ = TrajDataset.loadDataset(file);
	}

	/**
	 * Load dataset from disk at default save location.
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws ClassNotFoundException the class not found exception
	 */
	public void loadDataset() throws IOException, ClassNotFoundException {
		loadDataset(defaultSaveFilename());
	}
	
	/* (non-Javadoc)
	 * @see java.awt.datatransfer.ClipboardOwner#lostOwnership(java.awt.datatransfer.Clipboard, java.awt.datatransfer.Transferable)
	 */
	@Override
	public void lostOwnership(Clipboard arg0, Transferable arg1) {
		// 
	}

	/**
	 * Export dataset to text.
	 * Each molecule occupy one line: x, y, frame, residue
	 * 
	 * @param file the file
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void exportNodes(File file) throws IOException {
		dataset_.writePositionsToText(file);		
	}

	/**
	 * Export selected trajectories to text.
	 * Each molecule occupy one line: x, y, frame, height, trackIdx
	 * 
	 * @param file the file
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void exportTrajectories(File file) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(file));
		int [] selected = frame_.getTrajsTable().getSelectedTrajectories();
		Trajectory traj;
		for (int i = 0; i < selected.length; i++) {
			traj = dataset_.getTrajectoryByIndex(selected[i]);
			for (int j = 0; j < traj.size(); j++) {
				SmNode n = traj.get(j);
				bw.append(n.toString());
				bw.append(", " + i + "\n");
			}
		}
		bw.close();
	}

	/**
	 * Plot intensity transients of selected trajectories
	 * 
	 */
	public void plotTransients() {
		
	}

	public void computeDrift() {
		int [] selected = frame_.getTrajsTable().getSelectedTrajectories();
		dataset_.estimateDrift(selected);
		double [] dx = dataset_.getDriftX();
		double [] dy = dataset_.getDriftY();
		double [] dz = dataset_.getDriftZ();
		double [] f = new double [dx.length];
		for (int i = 0; i < f.length; i++) {
			f[i] = i;
		}
		Plot plotWinX = new Plot("X-Drift", "T/T-frame", "Drift (pixel)", f, dx);
		//plotWin.addPoints(x, y, Plot.BOX);
		//plotWin.addErrorBars(e);
		plotWinX.show();
		Plot plotWinY = new Plot("Y-Drift", "T/T-frame", "Drift (pixel)", f, dy);
		plotWinY.show();
		Plot plotWinZ = new Plot("Z-Drift", "T/T-frame", "Drift (pixel)", f, dz);
		plotWinZ.show();		
	}
}
