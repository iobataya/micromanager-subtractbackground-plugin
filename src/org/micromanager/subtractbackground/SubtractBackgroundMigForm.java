///////////////////////////////////////////////////////////////////////////////
//FILE:          SubtractBackgroundMigForm.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     SubtractBackgroundMigForm plugin
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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.micromanager.MMStudio;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.ReportingUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;

/**
 *
 */
@SuppressWarnings("serial")
public class SubtractBackgroundMigForm extends MMDialog {
	private MMDialog mcsPluginWindow;
	private final ScriptInterface gui_;
	private final mmcorej.CMMCore mmc_;
	private final Preferences prefs_;
	private final SubtractBackgroundProcessor processor_;
	private static SpinnerNumberModel spinnerOffsetModel_;
	private static SpinnerNumberModel spinnerAverageModel_;
	private final String[] IMAGESUFFIXES = { "tif", "tiff", "jpg", "png" };
	private String backgroundFileName_;
	private String statusMessage_;
	private final Font fontSmall_;
	private final Font fontSmallBold_;
	private final JCheckBox chkEnable_;
	private final Dimension buttonSize_;
	private final JLabel statusLabel_;
	private final JButton snapButton_;
	private final JTextField textBG;

	private static final String LABEL_BACKGROUND = "Background Image";
	private static final String LABEL_EXECUTE = "Subtract BG from acquired image ?";
	private static final String LABEL_AVR = "BG averaging count:";
	private static final String LABEL_OFFSET = "+ Offset (%): ";
	private static final String LABEL_NONE = "None";
	private static final String PREF_OFFSET = "OffsetValue";
	private static final String PREF_ENABLE = "UseSubtractBG";
	private static final String PREF_BG_PATH = "BackgroundFileName";
	private static final String PREF_AVR_COUNT = "AverageAccumCount";
	private static final String ERR_SUBTRACTION = "Failed to set background image";

	/**
	 * entry point to test
	 * 
	 * @param arg
	 */
	public static void main(String[] arg) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			MMStudio mmStudio = new MMStudio(false);
			SubtractBackgroundProcessor processor = new SubtractBackgroundProcessor();
			processor.setApp(mmStudio);
			processor.makeConfigurationGUI();
			mmStudio.getAcquisitionEngine().getImageProcessors().add(processor);
		} catch (Exception e) {
			ReportingUtils.logError(e);
		}
	}

	/**
	 * Creates new form SubtractBG form
	 * 
	 * @param processor
	 * @param gui
	 */
	public SubtractBackgroundMigForm(SubtractBackgroundProcessor processor, ScriptInterface gui) {
		processor_ = processor;
		gui_ = gui;
		mmc_ = gui_.getMMCore();
		prefs_ = this.getPrefsNode();
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				dispose();
			}
		});
		fontSmall_ = new Font("Arial", Font.PLAIN, 12);
		fontSmallBold_ = new Font("Arial", Font.BOLD, 14);
		buttonSize_ = new Dimension(70, 21);
		loadAndRestorePosition(100, 100, 350, 250);
		mcsPluginWindow = this;
		this.setLayout(new MigLayout("flowx, fill, insets 8"));
		this.setTitle(SubtractBackground.menuName);

		// Enabling this processor
		statusLabel_ = new JLabel(" ");
		chkEnable_ = new JCheckBox();
		chkEnable_.setText(LABEL_EXECUTE);
		chkEnable_.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				processor_.setEnabled(chkEnable_.isSelected());
				prefs_.putBoolean(PREF_ENABLE, chkEnable_.isSelected());
				statusLabel_.setText(" ");
			}
		});
		chkEnable_.setSelected(prefs_.getBoolean(PREF_ENABLE, true));
		add(chkEnable_, "span 3, wrap");
		add(statusLabel_, "span 3, wrap");

		// Button BG
		snapButton_ = new JButton("Snap BG and set");
		snapButton_.setFont(fontSmallBold_);
		snapButton_.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				saveAndSetBackgroundImage();
			}
		});
		add(snapButton_,"wrap");

		// Background image setting
		JLabel darkImageLabel = new JLabel("BG Image:");
		darkImageLabel.setFont(fontSmall_);

		textBG = new JTextField();
		textBG.setFont(fontSmall_);
		textBG.setText(prefs_.get(PREF_BG_PATH, ""));
		textBG.setHorizontalAlignment(JTextField.RIGHT);
		textBG.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				processBackgroundImage(textBG.getText());
			}
		});
		textBG.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent fe) {
			}

			@Override
			public void focusLost(FocusEvent fe) {
				processBackgroundImage(textBG.getText());
			}
		});
		textBG.setText(processBackgroundImage(textBG.getText()));
		
		// Select BG file
		final JButton btnBG = mcsButton(buttonSize_, fontSmall_);
		btnBG.setText("...");
		btnBG.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				File f = FileDialogs.openFile(mcsPluginWindow, LABEL_BACKGROUND,
						new FileDialogs.FileType("MMAcq", LABEL_BACKGROUND, backgroundFileName_, true, IMAGESUFFIXES));
				if (f != null) {
					processBackgroundImage(f.getAbsolutePath());
					textBG.setText(backgroundFileName_);
				}
			}
		});
		add(darkImageLabel);
		add(textBG, "span 2, growx");
		add(btnBG, "wrap");

		// Averager spinner
		JLabel lblAvr = new JLabel(LABEL_AVR);
		lblAvr.setFont(fontSmall_);

		final JSpinner avrSpinner = new JSpinner();
		avrSpinner.setFont(fontSmall_);
		try {
			double avrCount = prefs_.getDouble(PREF_AVR_COUNT, 64);
			spinnerAverageModel_ = new SpinnerNumberModel(avrCount, 1, 1000, 1);
		} catch (IllegalArgumentException e) {
			spinnerAverageModel_ = new SpinnerNumberModel(64, 1, 1000, 1);
			prefs_.putDouble(PREF_AVR_COUNT, 64);
		}

		avrSpinner.setModel(spinnerAverageModel_);
		avrSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(javax.swing.event.ChangeEvent evt) {
				double v = (Double) avrSpinner.getValue();
				prefs_.putDouble(PREF_AVR_COUNT, v);
			}
		});
		add(lblAvr);
		add(avrSpinner, "growx, wrap");

		// Offset spinner
		JLabel offsetLabel = new JLabel(LABEL_OFFSET);
		offsetLabel.setFont(fontSmall_);
		add(offsetLabel);

		final JSpinner offsetSpinner = new JSpinner();
		offsetSpinner.setFont(fontSmall_);
		try {
			double offsetPercent = prefs_.getDouble(PREF_OFFSET, processor_.getOffset());
			spinnerOffsetModel_ = new SpinnerNumberModel(offsetPercent, 0, 100, 0.5);
		} catch (IllegalArgumentException e) {
			spinnerOffsetModel_ = new SpinnerNumberModel(processor_.getOffset(), 0, 100, 0.5);
		}
		offsetSpinner.setModel(spinnerOffsetModel_);
		processor_.setOffset((Double)offsetSpinner.getValue());
		offsetSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(javax.swing.event.ChangeEvent evt) {
				double v = (Double) offsetSpinner.getValue();
				processor_.setOffset(v);
				prefs_.putDouble(PREF_OFFSET, v);
			}
		});
		add(offsetSpinner, "growx, wrap");
	}

	@Override
	public void dispose() {
		super.dispose();
		processor_.setMyFrameToNull();
	}

	public final Font getButtonFont() {
		return fontSmall_;
	}

	public final Dimension getButtonDimension() {
		return buttonSize_;
	}

	public final JButton mcsButton(Dimension buttonSize, Font font) {
		JButton button = new JButton();
		button.setPreferredSize(buttonSize);
		button.setMinimumSize(buttonSize);
		button.setFont(font);
		button.setMargin(new Insets(0, 0, 0, 0));

		return button;
	}

	public synchronized void setStatus(final String status) {
		statusMessage_ = status;
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				// update the statusLabel from this thread
				if (status != null) {
					statusLabel_.setText(status);
				}
			}
		});
	}

	public synchronized String getStatus() {
		String status = statusMessage_;
		statusMessage_ = null;
		return status;
	}

	/**
	 * Processes background image Return filename if successful, empty string
	 * otherwise
	 * 
	 * @param fileName
	 * @return fileName
	 */
	private String processBackgroundImage(String fileName) {
		if (LABEL_NONE.equals(fileName)) {
			fileName = "";
		}
		if (!fileName.equals("")) {
			ij.io.Opener opener = new ij.io.Opener();
			ImagePlus ip = opener.openImage(fileName);
			if (ip != null) {
				processor_.setBackgroundImage(ip.getProcessor());
				backgroundFileName_ = fileName;
				prefs_.put(PREF_BG_PATH, backgroundFileName_);
				return backgroundFileName_;
			}
		}
		ReportingUtils.showMessage(ERR_SUBTRACTION);
		return "";
	}

	public void updateProcessorEnabled(boolean enabled) {
		chkEnable_.setSelected(enabled);
		prefs_.putBoolean(PREF_ENABLE, enabled);
	}

	/**
	 * Turn off processors, save and average background images, turn on processors.
	 */
	public void saveAndSetBackgroundImage() {
		// Disable enabled data processors
		gui_.enableLiveMode(false);

		List<DataProcessor<TaggedImage>> enabledProcessors = new ArrayList<DataProcessor<TaggedImage>>();
		for (DataProcessor<TaggedImage> dp : gui_.getImageProcessorPipeline()) {
			if (dp.getIsEnabled() == true) {
				enabledProcessors.add(dp);
				dp.setEnabled(false);
			}
		}
		try {
			// try first image.
			TaggedImage bg = mmc_.getLastTaggedImage();
			String type = MDUtils.getPixelType(bg.tags);
			if (!(type.equals("GRAY8") || type.equals("GRAY16"))) {
				ReportingUtils.showError("Image format error. Nothing saved.");
				return;
			}
			int ijType = type.equals("GRAY16") ? ImagePlus.GRAY16 : ImagePlus.GRAY8;
			int count = (int) prefs_.getDouble(PREF_AVR_COUNT, 1);
			int height = MDUtils.getHeight(bg.tags);
			int width = MDUtils.getWidth(bg.tags);
			int pixelCount = height * width;
			int[] sum = new int[pixelCount];

			for (int i = 0; i < count; i++) {
				mmc_.snapImage();
				TaggedImage newTimage = mmc_.getLastTaggedImage();
				ImageProcessor newIP = ImageUtils.makeProcessor(newTimage);
				for (int j = 0; j < pixelCount; j++) {
					sum[j] += (int) newIP.get(j);
				}
			}
			ImageProcessor averagedImp;
			if (ijType == ImagePlus.GRAY8) {
				byte[] averaged = new byte[pixelCount];
				for (int j = 0; j < pixelCount; j++) {
					averaged[j] = (byte) (sum[j] / count);
				}
				averagedImp = new ByteProcessor(width, height);
				averagedImp.setPixels(averaged);
			} else {
				short[] averaged = new short[pixelCount];
				for (int j = 0; j < pixelCount; j++) {
					averaged[j] = (short) (sum[j] / count);
				}
				averagedImp = new ShortProcessor(width, height);
				averagedImp.setPixels(averaged);
			}

			File file = new File(backgroundFileName_);
			String dir = file.getAbsoluteFile().getParent();
			Timestamp ts = new Timestamp(System.currentTimeMillis());
			String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(ts);
			File newFile = new File(dir, timeStamp + "-BG" + String.valueOf(count) + ".tiff");
			IJ.saveAs(new ImagePlus("BG", averagedImp), "tiff", newFile.getAbsolutePath());

			String openedFile = processBackgroundImage(newFile.getAbsolutePath());
			if (openedFile.equals(newFile.getAbsolutePath())) {
				textBG.setText(backgroundFileName_);
			}

			ReportingUtils.logMessage(openedFile + " was saved.");

		} catch (

		Exception ex) {
			ReportingUtils.logError("Couldnt get tagged image on rising at Digital1");
		}
		// Enable disabled data processor
		for (DataProcessor<TaggedImage> dp : enabledProcessors) {
			dp.setEnabled(true);
		}
		gui_.enableLiveMode(true);
	}
}
