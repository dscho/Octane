//FILE:          NelderMeadRefiner.java
//PROJECT:       Octane
//-----------------------------------------------------------------------------
//
// AUTHOR:       Ji Yu, jyu@uchc.edu, 3/30/11
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

import java.util.Arrays;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.DifferentiableMultivariateVectorialFunction;
import org.apache.commons.math.analysis.MultivariateMatrixFunction;
import org.apache.commons.math.analysis.MultivariateRealFunction;
import org.apache.commons.math.ConvergenceException;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.commons.math.optimization.VectorialPointValuePair;
import org.apache.commons.math.optimization.direct.NelderMead;
import org.apache.commons.math.optimization.general.GaussNewtonOptimizer;

import ij.process.ImageProcessor;

public class NelderMeadRefiner implements SubPixelRefiner, MultivariateRealFunction {
	
	static final int kernelSize_ = 3;
	static final double defaultH_ = 200.0;
	static final double sigma2_ = 1.73;
	
	int blocks_;

	int x0_,y0_;
	ImageProcessor ip_;
	double [] parameters_; 
	double residue_;
	double bg_ ;
	boolean zeroBg_;
	
	public NelderMeadRefiner() {
		this(false);
	}
	
	public NelderMeadRefiner(boolean b) {
		zeroBg_ = b;
		if (b) 
			parameters_ = new double[3];
		else
			parameters_ = new double[4];
	}

	public void setImageData(ImageProcessor ip){
		ip_ = ip;
		bg_ = ip.getAutoThreshold();		
	}

	double gauss(double x) {
		return Math.exp(- x*x/sigma2_);
	}

	@Override
	public int refine(double x, double y) {
		//int w = 1 + 2 * kernelSize_;
		if (x < kernelSize_) {
			x0_ = kernelSize_;
		} else if (x >= ip_.getWidth() - kernelSize_) {
			x0_ = ip_.getWidth() - kernelSize_ - 1;
		} else { 
			x0_ = (int) x;
		}
		parameters_[0] = x0_ + .5 - x;

		if (y < kernelSize_) {
			y0_ = kernelSize_;
		} else if (y >= ip_.getHeight() - kernelSize_) {
			y0_ = ip_.getHeight() - kernelSize_ - 1;
		} else { 
			y0_ = (int) y;
		}
		parameters_[1] = y0_ + .5 - y;

		if (! zeroBg_)
			parameters_[3] = bg_;

		try {
			fit();
		} catch (ConvergenceException e) {
			return -1;
		} catch (Exception e) {
//			System.out.println(e.getMessage() + e.toString());
			return -2;
		}

		// report
//		double hw = 0.5 + kernelSize_;
//		for (int xi = - kernelSize_; xi <= kernelSize_; xi++) {
//			for (int yi = - kernelSize_; yi <= kernelSize_; yi++) {
//				double xp = Math.sin(parameters_[0]) * hw;
//				double yp = Math.sin(parameters_[1]) * hw;
//				System.out.printf("%3d(%5f)\t", ip_.get(x0_ + xi, y0_ + yi), parameters_[2] * gauss(xp + xi) * gauss( yp + yi) + parameters_[3]);
//			}
//			System.out.println();
//		}
//		System.out.println();
		
		return 0;
	}
	
	void fit() throws ConvergenceException, FunctionEvaluationException, IllegalArgumentException {
		NelderMead nm = new NelderMead();
		parameters_[2] = ip_.get(x0_, y0_) - bg_;

		RealPointValuePair vp = nm.optimize(this, GoalType.MINIMIZE, parameters_);
		parameters_ = vp.getPoint();
		residue_ = vp.getValue() / parameters_[2] / parameters_[2]; // normalized to H^2
	}

	@Override
	public double getXOut() {
		return (x0_ + .5 - parameters_[0]);
	}

	@Override
	public double getYOut() {
		return (y0_ + .5 - parameters_[1]);
	}

	@Override
	public double getHeightOut() {
		return parameters_[2];
	}

	@Override
	public double getResidue() {
		return residue_ ;
	}

	@Override
	public double value(double[] p) throws FunctionEvaluationException,IllegalArgumentException {
		double xp = p[0];
		double yp = p[1];
		double h = p[2];
		double bg = zeroBg_?0:p[3];
		double r = 0;
		if (h < 0) {
			return 1e10;
		}
		
		for (int xi = - kernelSize_; xi <= kernelSize_; xi++) {
			for (int yi = - kernelSize_; yi <= kernelSize_; yi++) {
				double g = gauss(xp + xi)* gauss(yp + yi);
				double v = (double)(ip_.get(x0_ + xi , y0_ + yi)) - bg - h*g;
				r += v * v;
			}
		}

		return r;
	}
}
