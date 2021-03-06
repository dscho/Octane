//FILE:          Trajectory.java
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

import java.util.Vector;

/**
 * Represents a trajectory.
 * It is a vector.
 */
public class Trajectory extends Vector<SmNode> {

	private static final long serialVersionUID = 8187158272341013922L;

	private double stepSize2_ = -1;
	private double maxDisplacement_ = -1;

	/** Whether it is marked. */
	protected boolean marked = false;

	/** Whether it is already deleted. */
	protected boolean deleted = false;

	/** A text note. */
	protected String note = null;

	//private int timeDelay_ = 4;


	/**
	 * Gets the length of the trajectory.
	 *
	 * @return the length
	 */
	public int getLength() {
		return get(size() - 1).frame - get(0).frame + 1;
	}

	/**
	 * Gets the average stepsize^2 of the trajectory
	 * 
	 * @return Average stepsize^2
	 */
	public double getAvgSquareStepSize(int delay) {
		if (size() < delay )
			return -1;
		if (stepSize2_ >= 0)
			return stepSize2_;
		stepSize2_ = 0;
		int cnt = 0;
		for (int i = 0; i < size() - delay; i++) {
			int j = i + delay;
			while (get(j).frame - get(i).frame > delay) {
				j--;
			}
			if (get(j).frame - get(i).frame == delay) {
				stepSize2_ += get(j).distance2(get(i));
				cnt ++;
			}
		}
		if (cnt > 0) 
			stepSize2_ /= cnt;
		else 
			return -1;
		return stepSize2_;
	}

	/**
	 * Gets largest displacement in the trajectory
	 * 
	 * @return Max displacement
	 */
	public double getMaxDisplament() {
		if (size() < 2)
			return -1;
		if (maxDisplacement_ >= 0)
			return maxDisplacement_;

		for (int i = 0; i < size()-1; i++) {
			for ( int j = 0; j < size(); j++) {
				double d = get(j).distance2(get(i));
				if (d > maxDisplacement_) {
					maxDisplacement_ = d;
				}
			}
		}
		if (maxDisplacement_ > 0) 
			maxDisplacement_ = Math.sqrt(maxDisplacement_);
		return maxDisplacement_;
	}
	
	/**
	 * Notify data change
	 * 
	 */
	public void dataChanged() {
		maxDisplacement_ = -1;
	}
	
	/**
	 * @return note of current trajectory
	 */
	public String getNote() {
		return note;
	}
	
//	/**
//	 * @param s New note
//	 */
//	public void setNote(String s) {
//		note = s;
//	}
	
	/**
	 * @return true if current note is empty
	 */
	public boolean isNoteEmpty() {
		if (note == null || note.trim().isEmpty()) {
			return true;
		} else {
			return false;
		}		
	}
}
