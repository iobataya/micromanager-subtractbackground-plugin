///////////////////////////////////////////////////////////////////////////////
//FILE:          SubtractBackgroundProcessor.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     SubtractBackgroundProcessor plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Ikuo Obataya
//
// COPYRIGHT:    JPK Instruments AG, 2018
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
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.subtractbackground;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.api.DataProcessor;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import mmcorej.TaggedImage;

/**
 * 
 */
public class SubtractBackgroundProcessor extends DataProcessor<TaggedImage> {
	private SubtractBackgroundMigForm myFrame_;
	private ImageProcessor backgroundImage_;
	private static double offsetPercent_ = 0;

	private static final String MSG_DONE = "Subtracted.";
	private static final String ERR_ILLEGAL_TYPE = "Cannot subtract images other than 8 or 16 bit grayscale";
	private static final String ERR_NO_BG_IMAGE = "No background image specified.";
	private static final String ERR_FRAME_MISSING = "myFrame_ missing.";
	/**
	 * Executes subtraction
	 * 
	 * @param nextImage
	 *            - image to be processed
	 * @return - Transformed tagged image, otherwise a copy of the input
	 * @throws JSONException
	 * @throws MMScriptException
	 */
	public TaggedImage processTaggedImage(TaggedImage nextImage) throws JSONException, MMScriptException, Exception {
		JSONObject newTags = nextImage.tags;

		// Check pixel depth
		String type = MDUtils.getPixelType(newTags);
		if (!(type.equals("GRAY8") || type.equals("GRAY16"))) {
			ReportingUtils.logError(ERR_ILLEGAL_TYPE);
			setStatus(ERR_ILLEGAL_TYPE);
			return nextImage;
		}
		int ijType = type.equals("GRAY16") ? ImagePlus.GRAY16 : ImagePlus.GRAY8;

		// Check background image
		if (backgroundImage_ == null) {
			ReportingUtils.logError(ERR_NO_BG_IMAGE);
			setStatus(ERR_NO_BG_IMAGE);
			return nextImage;
		}

		// Actual offset value = signal * (max value) / 100
		int offsetValue = (ijType == ImagePlus.GRAY16) ? (int) (offsetPercent_ * 655.35)
				: (int) (offsetPercent_ * 2.56);
		ImageProcessor imp = ImageUtils.makeProcessor(nextImage);
		imp = ImageUtils2.subtractImageProcessorsWithOffset(imp, backgroundImage_, offsetValue);
		setStatus(MSG_DONE);
		return new TaggedImage(imp.getPixels(), newTags);
	}

	public synchronized void setBackgroundImage(ImageProcessor background) {
		backgroundImage_ = background;
	}

	public synchronized ImageProcessor getBackgroundImage() {
		return backgroundImage_;
	}

	public void setOffset(double offset) {
		offsetPercent_ = offset;
	}

	public double getOffset() {
		return offsetPercent_;
	}

	public void setMyFrameToNull() {
		myFrame_ = null;
	}

	private void setStatus(String status) {
		if(myFrame_!=null) {
			myFrame_.setStatus(status);
		}else {
			ReportingUtils.logDebugMessage(ERR_FRAME_MISSING);
		}
	}
	
	//
	// DataProcessor
	//

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		if (myFrame_ != null) {
			myFrame_.updateProcessorEnabled(enabled);
		}
	}
	/**
	 * Polls for tagged images, and processes them if their size and type matches
	 */
	@Override
	public void process() {
		try {
			TaggedImage nextImage = poll();
			if (nextImage != TaggedImageQueue.POISON) {
				try {
					produce(processTaggedImage(nextImage));
				} catch (Exception ex) {
					produce(nextImage);
					ReportingUtils.logError(ex);
					setStatus(ex.getMessage());
				}
			} else {
				// Must produce Poison (sentinel) image to terminate tagged image pipeline
				produce(nextImage);
			}
		} catch (Exception ex) {
			ReportingUtils.logError(ex.getMessage());
			setStatus(ex.getMessage());
		}
	}
	
	@Override
	public void makeConfigurationGUI() {
		if (myFrame_ == null) {
			myFrame_ = new SubtractBackgroundMigForm(this, gui_);
			gui_.addMMBackgroundListener(myFrame_);
		}
		myFrame_.setVisible(true);
	}

	@Override
	public void dispose() {
		if (myFrame_ != null) {
			myFrame_.dispose();
			myFrame_ = null;
		}
	}

}
