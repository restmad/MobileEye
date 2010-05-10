package co.uk.gauntface.android.mobileeye;

import java.util.ArrayList;

import co.uk.gauntface.android.mobileeye.imageprocessing.AreaExtraction;
import co.uk.gauntface.android.mobileeye.imageprocessing.ImagePackage;
import co.uk.gauntface.android.mobileeye.imageprocessing.Pair;
import co.uk.gauntface.android.mobileeye.imageprocessing.Peak;
import co.uk.gauntface.android.mobileeye.imageprocessing.QuickSegment;
import co.uk.gauntface.android.mobileeye.imageprocessing.QuickSegmentFactory;
import co.uk.gauntface.android.mobileeye.imageprocessing.RegionGroup;
import co.uk.gauntface.android.mobileeye.imageprocessing.Utility;
import co.uk.gauntface.android.mobileeye.imageprocessing.YUVPixel;
import android.graphics.Bitmap;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Message;
import android.text.format.Time;
import android.util.Log;

public class ImageProcessingThread extends Thread
{
	private double ROTATE_LEFT_RIGHT_MAX = 20.66;
	private int WEIGHT_IN_FAVOUR_LIGHT = 255;
	private int WEIGHT_IN_FAVOUR_DARK = 0;
	
	private Size mImageSize;
	private byte[] mData;
	private boolean mLogData;
	
	private static final int SCALE_DOWN_FACTOR = 3;
	private static final long STABLE_AREA_PERIOD = 3;
	private static final long MAX_MARKER_SETUP_TIME = 20;
	
	public ImageProcessingThread(Size imageSize, byte[] data, boolean logData)
	{
		mImageSize = imageSize;
		mData = data;
		mLogData = logData;
	}
	
	public void run()
	{
		if(Singleton.getApplicationState() != Singleton.STATE_INIT_APP)
		{
			mData = mData.clone();
			
			// Get image pixels
			YUVPixel yuvPixel = getYUVPixels(SCALE_DOWN_FACTOR);
			
			int weightInFavour;
			// Segment the image (Needs to take into account the state
			if(Singleton.getApplicationState() != Singleton.STATE_PROJECTING_MARKERS)
			{
				weightInFavour = WEIGHT_IN_FAVOUR_LIGHT;
			}
			else
			{
				weightInFavour = WEIGHT_IN_FAVOUR_DARK;
			}
			QuickSegment quickSegment = QuickSegmentFactory.getQuickSegment();
			ImagePackage imgPackage = quickSegment.segmentImage(yuvPixel.getPixels(), 
					mLogData,
					yuvPixel.getImgWidth(),
					yuvPixel.getImgHeight(),
					weightInFavour);
			
			// segmentImage returns null when there is no extractable region
			if(imgPackage != null)
			{
				handleSegmentedImage(yuvPixel, imgPackage);
			}
		}
	}

	/**
	 * This function takes into account the state of the application and will extract the correct number
	 * of pixels accordingly
	 * 
	 * The scaleDownFactor is a variable which control how many times the original image should be reduced
	 * (i.e. 2 = 1/2, 3 = 1/3)
	 * 
	 * @param scaleDownFactor
	 * @return
	 */
	private YUVPixel getYUVPixels(int scaleDownFactor)
	{
		int targetWidth = mImageSize.width;
		int targetHeight = mImageSize.height;
		boolean targetSizeScaled = false;
		int topOffset = 0;
		int leftOffset = 0;
		
		if(Singleton.getApplicationState() == Singleton.STATE_TEST_SUITABLE_PROJECTION)
		{
			RegionGroup r = Singleton.getLastProjectedArea();
			targetWidth = r.getBottomRightX() - r.getTopLeftX();
			targetHeight = r.getBottomRightY() - r.getTopLeftY();
			targetSizeScaled = true;
			topOffset = r.getTopLeftY();
			leftOffset = r.getTopLeftX();
		}
		
		return new YUVPixel(mData, mImageSize.width, mImageSize.height, leftOffset, topOffset, targetWidth, targetHeight, scaleDownFactor, targetSizeScaled);
	}
	
	/**
	 * This function will handle the actions to take for each state after the image has been segmented
	 * @param imgPackage
	 */
	private void handleSegmentedImage(YUVPixel yuvPixel, ImagePackage imgPackage)
	{
		if(Singleton.getApplicationState() == Singleton.STATE_FINDING_AREA)
		{
			imgPackage = findArea(imgPackage);
		}
		else if(Singleton.getApplicationState() == Singleton.STATE_TEST_SUITABLE_PROJECTION)
		{
			imgPackage = testSuitableProjectionArea(yuvPixel, imgPackage);
		}
		else if(Singleton.getApplicationState() == Singleton.STATE_SETTING_UP_MARKERS)
		{
			settingUpMarkers(imgPackage);
		}
		else if(Singleton.getApplicationState() == Singleton.STATE_PROJECTING_MARKERS)
		{
			projectingMarkers(yuvPixel, imgPackage);
		}
		
		if(mLogData == true)
		{
			logData(yuvPixel, imgPackage);
		}
		
		Message msg = CameraWrapper.mHandler.obtainMessage();
		msg.arg1 = CameraActivity.DRAW_IMAGE_PROCESSING;
		
		CameraWrapper.mHandler.dispatchMessage(msg);
	}

	/**
	 * This function find's the projectable area in the scene when the state
	 * of the application is finding an area (No previous data to use)
	 * @param imgPackage
	 * @return
	 */
	private ImagePackage findArea(ImagePackage imgPackage)
	{
		imgPackage = AreaExtraction.getExtraction(imgPackage);
		RegionGroup areaExtraction = imgPackage.getExtractionArea();
		
		if(foundGoodArea(areaExtraction, imgPackage.getImgWidth(), imgPackage.getImgHeight()) == true)
		{
			Singleton.setLastProjectedArea(imgPackage.getExtractionArea());
			Singleton.setLastProjectedImgWidth(imgPackage.getImgWidth());
			Singleton.setLastProjectedImgHeight(imgPackage.getImgHeight());
			Singleton.setLastProjectedAreaAverage(imgPackage.getAveragePixelValue());
			
			/**
			 * State Changed to setting up the projection
			 */
			Singleton.setApplicationState(Singleton.STATE_TEST_SUITABLE_PROJECTION);
		}
		
		//Bitmap b = Utility.renderBitmap(imgPackage.getRegionGroupPixels(), imgPackage.getImgWidth(), imgPackage.getImgHeight(), true);
		Bitmap b = Utility.renderBitmap(imgPackage.getAreaExtractionPixels(), imgPackage.getImgWidth(), imgPackage.getImgHeight(), true, 127);
		Singleton.updateImageView = b;
		
		return imgPackage;
	}
	
	private ImagePackage testSuitableProjectionArea(YUVPixel yuvPixel, ImagePackage imgPackage)
	{
		RegionGroup lastExtraction = Singleton.getLastProjectedArea();
		imgPackage.setRegionGroup(lastExtraction);
		imgPackage.setAveragePixelValue(yuvPixel.getAveragePixelValue());
		imgPackage.setExtractionArea(lastExtraction);
		double prevAvg = Singleton.getLastProjectedAreaAverage();
		
		long stableAreaCount = Singleton.getStatePeriodSecs(System.nanoTime());
		
		if(stableAreaCount > STABLE_AREA_PERIOD)
		{
			if(Singleton.hasVoiceCommandBeenSent() == false)
			{
				Pair center = getCenterPoint(lastExtraction);
				
				double rotateLeftRight = getRotationHorizontal(center);
				
				Message msg = CameraWrapper.mHandler.obtainMessage();
				msg.arg1 = CameraActivity.ROTATE_PROJECTOR_VIEW;
				
				Bundle data = new Bundle();
				data.putDouble(CameraActivity.ROTATE_LEFT_RIGHT_KEY, rotateLeftRight);
				data.putDouble(CameraActivity.ROTATE_UP_DOWN_KEY, 0);
				
				msg.setData(data);
				
				CameraWrapper.mHandler.dispatchMessage(msg);
				
				Singleton.voiceCommandSent();
				
				Singleton.setApplicationState(Singleton.STATE_SETTING_UP_MARKERS);
			}
		}
		else if((averagesApproximatelyMatch(prevAvg, yuvPixel.getAveragePixelValue())) == false)
		{
			imgPackage = AreaExtraction.getExtraction(imgPackage);
			RegionGroup newAreaExtraction = imgPackage.getExtractionArea();
			
			if(newAreaExtraction.getRegionSize() >= (lastExtraction.getRegionSize() * 0.7))
			{
				//stableAreaCount = stableAreaCount + 1;
				//increaseStableCount();
				
				int newTopLeftX = lastExtraction.getTopLeftX() + newAreaExtraction.getTopLeftX();
				int newTopLeftY = lastExtraction.getTopLeftY() + newAreaExtraction.getTopLeftY();
				
				int newBottomRightX = newTopLeftX + newAreaExtraction.getBottomRightX();
				int newBottomRightY = newTopLeftY + newAreaExtraction.getBottomRightY();
				
				RegionGroup r = new RegionGroup(newTopLeftX, newTopLeftY, newBottomRightX, newBottomRightY);
				Singleton.setLastProjectedArea(r);
				Singleton.setLastProjectedAreaAverage(yuvPixel.getAveragePixelValue());
			}
			else
			{
				Singleton.setApplicationState(Singleton.STATE_FINDING_AREA);
				
				Message msg = CameraWrapper.mHandler.obtainMessage();
				msg.arg1 = CameraActivity.APPLICATION_STATE_CHANGED;
				
				CameraWrapper.mHandler.dispatchMessage(msg);
			}
		}
		
		return imgPackage;
	}

	private void settingUpMarkers(ImagePackage imgPackage)
	{
		long elapsedTime = Singleton.getStatePeriodSecs(System.nanoTime());
		
		if(elapsedTime > MAX_MARKER_SETUP_TIME)
		{
			Message msg = Message.obtain();
			msg.arg1 = CameraActivity.SHOW_TOAST;
			
			Bundle data = msg.getData();
			data.putString(CameraActivity.TOAST_STRING_KEY, "Timeout on setting up markers");
			
			msg.setData(data);
			CameraWrapper.mHandler.dispatchMessage(msg);
			
			Singleton.setApplicationState(Singleton.STATE_FINDING_AREA);
		}
	}
	
	private ImagePackage projectingMarkers(YUVPixel yuvPixel, ImagePackage imgPackage)
	{
		RegionGroup lastExtraction = Singleton.getLastProjectedArea();
		imgPackage.setRegionGroup(lastExtraction);
		imgPackage.setAveragePixelValue(yuvPixel.getAveragePixelValue());
		imgPackage.setExtractionArea(lastExtraction);
		double prevAvg = Singleton.getLastProjectedAreaAverage();
		
		Singleton.setLastProjectedAreaAverage(yuvPixel.getAveragePixelValue());
		
		imgPackage = AreaExtraction.getExtraction(imgPackage);
		RegionGroup areaExtraction = imgPackage.getExtractionArea();
		
		if(foundGoodArea(areaExtraction, imgPackage.getImgWidth(), imgPackage.getImgHeight()) == true)
		{
			Singleton.setLastProjectedArea(imgPackage.getExtractionArea());
			Singleton.setLastProjectedImgWidth(imgPackage.getImgWidth());
			Singleton.setLastProjectedImgHeight(imgPackage.getImgHeight());
			Singleton.setLastProjectedAreaAverage(imgPackage.getAveragePixelValue());
			
			/**
			 * State Changed to setting up the projection
			 */
			Singleton.setApplicationState(Singleton.STATE_TEST_SUITABLE_PROJECTION);
		}
		
		//Bitmap b = Utility.renderBitmap(imgPackage.getRegionGroupPixels(), imgPackage.getImgWidth(), imgPackage.getImgHeight(), true);
		Bitmap b = Utility.renderBitmap(imgPackage.getAreaExtractionPixels(), imgPackage.getImgWidth(), imgPackage.getImgHeight(), true, 127);
		Singleton.updateImageView = b;
		
		return imgPackage;
	}
	
	/*******************************************************************************************************************************************************
	 * Utility Functions
	 *******************************************************************************************************************************************************/
	private boolean averagesApproximatelyMatch(double prevAvg, double averagePixelValue)
	{
		if(Math.abs(prevAvg - averagePixelValue) < 8)
		{
			return true;
		}
		//Log.d("mobileeye", "BAD BAD BAD AVG");
		//Log.d("mobileeye", "prevAvg = "+prevAvg+" newAvg = "+averagePixelValue+" Average Match - " + Math.abs(prevAvg - averagePixelValue));
		return false;
	}

	private boolean foundGoodArea(RegionGroup areaExtraction, int imgWidth, int imgHeight)
	{
		if(areaExtraction.getRegionSize() > ((imgWidth * imgHeight) * 0.2))
		{
			return true;
		}
		return false;
	}

	private void logData(YUVPixel yuvPixel, ImagePackage imgPackage)
	{
		Time time = new Time();
		time.setToNow();
		
		String currentTime = time.format("%Y%m%d%H%M%S");
		currentTime = currentTime + "_";
		
		Utility.setFilePrePend(currentTime);
		
		Bitmap temp = Utility.renderBitmap(yuvPixel.getPixels(),
				yuvPixel.getImgWidth(),
				yuvPixel.getImgHeight(),
				true,
				255);
		Utility.saveImageToSDCard(temp, "1_B&W.png");
		
		String s = new String();
		int[] hist = imgPackage.getHistogram();
		for(int i = 0; i < hist.length; i++)
		{
			s = s + hist[i]+"\n";
		}
		
		Utility.saveTextToSDCard(s, "2_HistogramData.txt");
		
		ArrayList<Peak> pixelGroups = imgPackage.getInitPixelGroups();
		s = "<Min> <Peak> <Max> <PeakSize>\n";
		for(int i = 0; i < pixelGroups.size(); i++)
		{
			Peak p = pixelGroups.get(i);
			s = s + p.getMinIndex()+" "+p.getPeakIndex()+" "+p.getMaxIndex()+" "+p.getPeakSize()+"\n";
		}
		
		Utility.saveTextToSDCard(s, "3_InitPixelGroups.txt");
		
		Peak[] finalPixelGroups = imgPackage.getFinalPixelGroups();
		s = "<Min> <Peak> <Max> <PeakSize>\n";
		for(int i = 0; i < finalPixelGroups.length; i++)
		{
			Peak p = finalPixelGroups[i];
			s = s + p.getMinIndex()+" "+p.getPeakIndex()+" "+p.getMaxIndex()+" "+p.getPeakSize()+"\n";
		}
		
		Utility.saveTextToSDCard(s, "4_FinalPixelGroups.txt");
		
		Peak usedPixelGroup = imgPackage.getUsedPixelGroup();
		s = "<Min> <Peak> <Max> <PeakSize>\n";
		s = s + usedPixelGroup.getMinIndex() + " " + usedPixelGroup.getPeakIndex() + " " + usedPixelGroup.getMaxIndex() + " " + usedPixelGroup.getPeakSize();
		
		Utility.saveTextToSDCard(s, "5_UsedPixelGroup.txt");
		
		temp = Utility.renderBitmap(imgPackage.getRegionGroupPixels(),
				imgPackage.getImgWidth(),
				imgPackage.getImgHeight(),
				true,
				255);
		Utility.saveImageToSDCard(temp, "6_Segment.png");
		
		RegionGroup r = imgPackage.getRegionGroup();
		s = "("+r.getTopLeftX()+","+r.getTopLeftY()+") ("+r.getBottomRightX()+","+r.getBottomRightY()+")\n";
		s = s + "center: ("+r.getWeightedCenter().getArg1()+","+r.getWeightedCenter().getArg2()+")";
		Utility.saveTextToSDCard(s, "7_FinalRegion");
		
		r = imgPackage.getExtractionArea();
		s = "("+r.getTopLeftX()+","+r.getTopLeftY()+") ("+r.getBottomRightX()+","+r.getBottomRightY()+")";
		Utility.saveTextToSDCard(s, "8_ExtractionArea");
		
		temp = Utility.renderBitmap(imgPackage.getAreaExtractionPixels(),
				imgPackage.getImgWidth(), imgPackage.getImgHeight(), true, 255);
		Utility.saveImageToSDCard(temp, "9_Area.png");
	}
	
	private Pair getCenterPoint(RegionGroup lastExtraction)
	{
		int centerX = lastExtraction.getTopLeftX() +
			((lastExtraction.getBottomRightX() - lastExtraction.getTopLeftX()) / 2);
		int centerY = lastExtraction.getTopLeftY() +
			((lastExtraction.getBottomRightY() - lastExtraction.getTopLeftY()) / 2);
	
		return new Pair(centerX, centerY);
	}
	
	private double getRotationHorizontal(Pair center)
	{
		double rLeftRight = 0;
		double rUpDown = 0;
		
		double halfImgWidth = Singleton.getLastProjectedImgWidth() / 2.0;
		
		// Offset to center of image = 0 degrees
		double relativeX = center.getArg1() - halfImgWidth;
		relativeX = relativeX / halfImgWidth;
		
		double rotateLeftRight = relativeX * ROTATE_LEFT_RIGHT_MAX;
		
		// Round up by ten then make int then divide by 10
		rotateLeftRight = rotateLeftRight * 10;
		int temp = (int) rotateLeftRight;
		rotateLeftRight = ((double) temp) / 10;
		
		return rotateLeftRight;
	}
}
