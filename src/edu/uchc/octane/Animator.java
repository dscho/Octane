//FILE:          AnimationWindow.java
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

import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;

import java.util.Timer;
import java.util.TimerTask;

public class Animator {
	final long ANIMATIONDELAY_ = 100;
		
	private boolean loop_;
	private Timer animateTimer_;
	private ImagePlus imp_;

	public Animator(ImagePlus imp) {
		imp_ = imp;
	}
	
	class AnimateTimerTask extends TimerTask {
		private Trajectory trajectory_;
		private int firstFrame_;
		private int lastFrame_;
		private int curFrame_;
		private int curIndex_;

		private AnimateTimerTask(Trajectory trajectory) {
			trajectory_ = trajectory;
			firstFrame_ = trajectory.getFrame(0);
			lastFrame_ = trajectory.getFrame(trajectory.size() - 1);
			// IJ.write("First "+ firstFrame_ + " Last" + lastFrame_);
			curFrame_ = firstFrame_;
			curIndex_ = 0;
		}
		
		@Override
		public void run() {
			if (curFrame_ > lastFrame_) {
				if (!loop_) {
					stopAnimation();
					return;
				} else {
					curFrame_ = firstFrame_;
					curIndex_ = 0;
				}
			}

			synchronized (imp_) {
				imp_.setSlice(curFrame_);
			}
				
			if (Prefs.showIndicator_) {
				int x = (int) trajectory_.getX(curIndex_);
				int y = (int) trajectory_.getY(curIndex_);
				if (trajectory_.getFrame(curIndex_) == curFrame_) {
					if (Prefs.pointIndicator_) {
						imp_.setRoi(new PointRoi(x, y));
					} else {
						imp_.setRoi(new Roi(x - 3, y - 3, 7, 7));
					}
					curIndex_++;
				} else {
					imp_.killRoi();
				}
			}
			curFrame_++;
		}		
	}

	void setLoop(boolean loop) {
		loop_ = loop;
	}	

	public void animate(Trajectory traj) {
		stopAnimation();

		// roi_ = imp_.getRoi();
		animateTimer_ = new Timer();
		animateTimer_.schedule(new AnimateTimerTask(traj), ANIMATIONDELAY_, ANIMATIONDELAY_);
	}

	public void stopAnimation() {
		if (animateTimer_ != null) {
			animateTimer_.cancel();
			animateTimer_ = null;
			// imp_.setRoi(roi_);
		}
	}
}
