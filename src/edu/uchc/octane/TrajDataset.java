//FILE:          TrajDataset.java
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

import ij.IJ;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Stack;
import java.util.Vector;

public class TrajDataset{
	
	private static final long serialVersionUID = -4434499638684956916L;
	
	Vector<Trajectory> trajectories_;
	SmNode [][] nodes_; //nodes_[frame][offset]

	LinkedList<Integer> [] backwardBonds_;
	LinkedList<Integer> [] forwardBonds_;
	LinkedList<Double> [] bondLengths_;
	boolean [] isTrackedParticle_; 
	
	LinkedList<Trajectory> activeTracks_;

	double threshold_;
	double threshold2_;
	int maxBlinking_;

	int curFrame_;

	public TrajDataset() {
		trajectories_ = new Vector<Trajectory>();
	}

//	Vector<Trajectory> getTrajectories() {
//		if (trajs_ == null || trajs_.size() == 0) {
//			return null;
//		} else 
//			return trajs_;
//	}

	public Trajectory getTrjectoryByIndex(int i) {
		return trajectories_.get(i);
	}

	public int getSize() {
		return trajectories_.size();
	}

	void rebuildNodes() {
		if (trajectories_ == null || trajectories_.size() == 0) 
			return;
		
		ArrayList<ArrayList<SmNode>> framelist = new ArrayList<ArrayList<SmNode>>();
		for (int i = 0; i < trajectories_.size(); i++) {
			Trajectory t = trajectories_.get(i);
			for (int j = 0; j < t.size(); j++) {
				SmNode n = t.get(j);
				while (n.frame > framelist.size()) {
					framelist.add(new ArrayList<SmNode>());
				}
				framelist.get(n.frame-1).add(n);
			}
		}
		
		nodes_ = new SmNode[framelist.size()][];
		for (int i = 0 ; i < nodes_.length; i++) {
			nodes_[i] = (SmNode[]) framelist.get(i).toArray();
		}
	}
	
	public void reTrack() {
		rebuildNodes();
		doTracking();
	}
	
	//	public void writePositionsToText(File file) throws IOException {
//		BufferedWriter bw = new BufferedWriter(new FileWriter(file));
//		for (int i = 0; i < nodes_.size(); i ++ ) {
//				SmNode s = nodes_.get(i);
//				bw.write(String.format("%f, %f, %d, %f\n", s.x, s.y, s.frame, s.reserved));				
//		}
//		bw.close();		
//	}

	public void writeTrajectoriesToText(File file)throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(file));
		for (int i = 0; i < trajectories_.size(); i ++ ) {
			for (int j = 0; j < trajectories_.get(i).size(); j++) {
				SmNode s = trajectories_.get(i).get(j);
				bw.write(String.format("%f, %f, %d, %f, %d\n", s.x, s.y, s.frame, s.reserved, i));				
			}
		}
		bw.close(); 		
	}

	public void saveDataset(File file) throws IOException {
		ObjectOutputStream out;
		BufferedOutputStream fs;
		fs = new BufferedOutputStream(new FileOutputStream(file));
		out = new ObjectOutputStream(fs);
		out.writeObject(trajectories_);
		out.close();
		fs.close();
	}

	static public TrajDataset loadDataset(File file) throws IOException, ClassNotFoundException {
		ObjectInputStream in;
		FileInputStream fs;
		TrajDataset dataset = new TrajDataset();
		fs = new FileInputStream(file);
		in = new ObjectInputStream(fs);
		Vector<Trajectory> trajectories = (Vector<Trajectory>) in.readObject();
		dataset.trajectories_ = trajectories;
		in.close();
		fs.close();
		return dataset;
	}

	static public TrajDataset importDatasetFromPositionsText(File file) throws IOException {
		BufferedReader br;
		String line;
		ArrayList<SmNode[]> nodes = new ArrayList<SmNode[]>(); 
		br = new BufferedReader(new FileReader(file));
		int curFrame = 0;
		ArrayList<SmNode> curFrameNodes = null;
		while (null != (line = br.readLine())) {
			SmNode node = new SmNode(line);
			while (node.frame > curFrame) {
				if (curFrameNodes != null) {
					nodes.add(curFrameNodes.toArray(new SmNode[curFrameNodes.size()]));
				}
				curFrameNodes = new ArrayList<SmNode>();
				curFrame ++;
			}
			curFrameNodes.add(node);
		}
		nodes.add(curFrameNodes.toArray(new SmNode[curFrameNodes.size()]));
		br.close();
		
		return createDatasetFromNodes((SmNode[][]) nodes.toArray());
	}
	
	static public TrajDataset importDatasetFromTrajectoriesText(File file) throws IOException {
		
		TrajDataset dataset;
		
		Trajectory oneTraj = new Trajectory();
		int cur_cnt = -1;
		String line;
		BufferedReader br = new BufferedReader(new FileReader(file));

		dataset = new TrajDataset();

		while (null != (line = br.readLine())) {
			int c = line.lastIndexOf(',');
			int cnt = Integer.parseInt(line.substring(c + 1).trim());
			if (cur_cnt < cnt) {
				oneTraj = new Trajectory();
				dataset.trajectories_.add(oneTraj);
				cur_cnt = cnt;
			}
			SmNode node = new SmNode(line.substring(0, c)); 
			oneTraj.add(node);
			//dataset.nodes_.add(node);
		}

		br.close();
		assert (dataset.trajectories_.size() > 0);	
		
		return dataset;
	}
	
	static public TrajDataset createDatasetFromNodes(SmNode[][] nodes) {
		TrajDataset dataset;
		dataset = new TrajDataset();
		dataset.nodes_ = nodes;
		dataset.doTracking();
		return dataset;		
	}
	
	void clusterAndOptimize(int seed) {
		LinkedList<Integer> headList = new LinkedList<Integer>();
		LinkedList<Integer> tailList = new LinkedList<Integer>();

		headList.add(seed);
		ListIterator<Integer> itHead = headList.listIterator();
		ListIterator<Integer> itTail = tailList.listIterator();
		itHead.next();

		while ( itHead.hasPrevious()) {
			while (itHead.hasPrevious()) {
				int trackIdx = itHead.previous();
				for (int j = 0; j < forwardBonds_[trackIdx].size(); j ++) {
					int tail = forwardBonds_[trackIdx].get(j);
					if ( ! tailList.contains(tail)) {
						itTail.add(tail); // surprising, this add _before_ the cursor.
					}
				}
			}
			//lastHeadListEnd = headList.size();	
			
			while (itTail.hasPrevious()) {
				int posIdx = itTail.previous();
				for (int j = 0; j < backwardBonds_[posIdx].size(); j++) {
					int head = backwardBonds_[posIdx].get(j);
					if (! headList.contains(head)) {
						itHead.add(head);
					}
				}
			}
			//lastTailListEnd = tailList.size();
		}
		
		optimizeSubnetwork(headList, tailList);
	}

	void optimizeSubnetwork(LinkedList<Integer> headList, LinkedList<Integer> tailList) {
		double bestDistanceSum = 1e20;
		int curBondIdx = -1;
		double curDistanceSum = 0;
		Stack<Integer> stack = new Stack<Integer>();
		Stack<Integer> occupiedTails = new Stack<Integer>();
		Stack<Double> distanceStack = new Stack<Double>();
		Stack<Integer> stack_c = null;
		int trackIdx; 
		int tail;
		double nextBondLength;

		while (true) {
			trackIdx = headList.get(stack.size());

			// try next possible bond		
			if (curBondIdx  < forwardBonds_[trackIdx].size()) { 
				curBondIdx ++ ;

				// test if this is a good bond
				if (curBondIdx == forwardBonds_[trackIdx].size()) { //special case, no bonding
					if (curDistanceSum + threshold_ >= bestDistanceSum) {
						continue;
					}
					//curDistanceSum += threshold_;
					nextBondLength = threshold_;
					tail = -1;
				} else {
					tail = forwardBonds_[trackIdx].get(curBondIdx);
					if (occupiedTails.contains(tail)) {
						continue; //fail
					}

					if (curDistanceSum + bondLengths_[trackIdx].get(curBondIdx) >= bestDistanceSum) {
						continue; //fail
					}
					//curDistanceSum += bondLengths_.get(trackIdx).get(curBondIdx);
					nextBondLength = bondLengths_[trackIdx].get(curBondIdx);
				}

				//looks ok, push to stack
				if (stack.size() < headList.size()-1) {
					stack.push(curBondIdx);
					occupiedTails.push(tail);
					distanceStack.push(curDistanceSum);
					curBondIdx = -1;
					curDistanceSum += nextBondLength;
				} else { // unless this is the last element
					bestDistanceSum = curDistanceSum + nextBondLength;
					stack.push(curBondIdx);
					stack_c = (Stack<Integer>) stack.clone();
					stack.pop();
				}

			} else if (stack.size() > 0) {
				curBondIdx = stack.pop();
				occupiedTails.pop();
				curDistanceSum = distanceStack.pop();
			} else { // finished here
				break;
			}

		} //while

		// got best route. creating new bonds
		for (int i = 0; i < stack_c.size(); i ++) {			
			trackIdx = headList.get(i);
			int bondIdx = stack_c.get(i);
			if (bondIdx < forwardBonds_[trackIdx].size()) {
				int bondTo = forwardBonds_[trackIdx].get(bondIdx);
				SmNode n = nodes_[curFrame_][bondTo];
				activeTracks_.get(trackIdx).add(n); //might be slow if tracks_ is too big
				isTrackedParticle_[bondTo] = true;
			} 
			forwardBonds_[trackIdx].clear();
		}
	}


	void buildAllPossibleBonds() {
		forwardBonds_ = (LinkedList<Integer> []) new LinkedList[activeTracks_.size()];
		bondLengths_ = (LinkedList<Double> []) new LinkedList[activeTracks_.size()];
		backwardBonds_ = (LinkedList<Integer> []) new LinkedList[nodes_[curFrame_].length];
		isTrackedParticle_ = new boolean[nodes_[curFrame_].length];
		
		for ( int i = 0; i < activeTracks_.size(); i ++) {
			forwardBonds_[i] = new LinkedList<Integer>();
			bondLengths_[i] = new LinkedList<Double>();
		}
		for ( int i = 0; i < nodes_[curFrame_].length; i ++) {
			backwardBonds_[i] = new LinkedList<Integer>();
		}

		// calculated all possible bonds
		ListIterator <Trajectory> it = activeTracks_.listIterator();
		while( it.hasNext() ) {
			int id = it.nextIndex();
			SmNode trackHead = it.next().lastElement();
			if (trackHead != null) { // only for active tracks
				for (int j = 0; j < nodes_[curFrame_].length; j ++) {
					double d = trackHead.distance2(nodes_[curFrame_][j]);
					if (d <= threshold2_) { // don't miss the = sign
						forwardBonds_[id].add(j);
						bondLengths_[id].add(d);
						backwardBonds_[j].add(id);
					} 
				}
			}
		}
	}

	void trivialBonds() {
		// search all trivial bonds
		for (int i = 0; i < forwardBonds_.length; i++ ) {
			if (forwardBonds_[i].size() == 1) {
				int bondTo = forwardBonds_[i].getFirst();
				if (backwardBonds_[bondTo].size() == 1) {
					// trivial bond
					forwardBonds_[i].clear();
					SmNode n = nodes_[curFrame_][bondTo];
					activeTracks_.get(i).add(n);
					isTrackedParticle_[bondTo] = true;
				}
			} else {
				for (int j = 0; j < forwardBonds_[i].size(); j++) {
					bondLengths_[i].set(j, Math.sqrt(bondLengths_[i].get(j)));
				}
			}
		}
	} // TrivialBonds()

	public void doTracking() {
		threshold_ = Prefs.trackerMaxDsp_;
		threshold2_ = threshold_ * threshold_;
		maxBlinking_ = Prefs.trackerMaxBlinking_;
		
		activeTracks_ = new LinkedList<Trajectory>();
		trajectories_ = new Vector<Trajectory>();

		//initial track # = first frame particle #  
		for (int i = 0; i < nodes_[0].length; i ++ ) {
			Trajectory t;
			t = new Trajectory();
			t.add(nodes_[0][i]);
			activeTracks_.add(t);
		}
		
		curFrame_ = 1;
		while (curFrame_ < nodes_.length) {
			buildAllPossibleBonds();
			trivialBonds();

			for ( int i = 0; i < forwardBonds_.length; i ++) {
				if (forwardBonds_[i].size() > 0) {
					clusterAndOptimize(i);
				}
			}

			//remove all tracks that has been lost for too long
			//int firstPos = xytData_.getFirstOfFrame(curFrame_);
			Iterator<Trajectory> it = activeTracks_.iterator(); 
			while ( it.hasNext() ) {
				Trajectory track = it.next();
				if (track.lastElement() != null) {
					int frame = track.lastElement().frame;
					if (curFrame_ - frame > maxBlinking_) {
						it.remove();
						trajectories_.add(track);
					} 
				}
			}

			//add new particles into the track list
			for (int i = 0; i < nodes_[curFrame_].length; i++) {
				if (!isTrackedParticle_[i]) {
					Trajectory t;
					t = new Trajectory();
					t.add(nodes_[curFrame_][i]);
					activeTracks_.add(t);
				}
			}

			curFrame_ ++;
			if ((curFrame_ / 100) * 100 == curFrame_ ) {
				IJ.log("Frame " + curFrame_ + ", " 
						+ activeTracks_.size() + " active tracks, " 
						+ trajectories_.size() + " stopped tracks.");
			}
		} //while

		// add all tracks to stoppedTracks list
		Iterator<Trajectory> it = activeTracks_.iterator(); 
		while (it.hasNext()) {
			Trajectory track = it.next();
			trajectories_.add(track);
		}
		activeTracks_.clear();
		activeTracks_ = null;
		nodes_ = null;

	} //doTracking

}
