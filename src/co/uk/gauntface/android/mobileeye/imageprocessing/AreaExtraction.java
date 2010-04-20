package co.uk.gauntface.android.mobileeye.imageprocessing;

import android.util.Log;

public class AreaExtraction
{
	private static final int COLOR_REGION = 255;
	private static final int NO_COLOR_REGION = -1;
	
	private static int[] mPixels;
	private static int mCenterPixelIndex;
	private static int[] mTopLeft;
	private static int[] mBottomRight;
	private static int mImgWidth;
	private static int mImgHeight;
	
	public static ImagePackage getExtraction(ImagePackage imgPackage)
	{
		mPixels = imgPackage.getRegionGroupPixels();
		
		mImgWidth = imgPackage.getImgWidth();
		mImgHeight = imgPackage.getImgHeight();
		
		int[] regionPixels = imgPackage.getRegionGroupPixels();
		RegionGroup regionGroup = imgPackage.getRegionGroup();
		Pair weightedCenter = regionGroup.getWeightedCenter();
		
		//int centerPointX = regionGroup.getTopLeftX() + ((int) (regionGroup.getBottomRightX() - regionGroup.getTopLeftX()) / 2);
		//int centerPointY = regionGroup.getTopLeftY() + ((int) (regionGroup.getBottomRightY() - regionGroup.getTopLeftY()) / 2);
		
		int centerPointX = weightedCenter.getArg1();
		int centerPointY = weightedCenter.getArg2();
		
		Log.d("mobileeye", "Traditional center (x,y): ("+centerPointX+","+centerPointY+")");
		Log.d("mobileeye", "Weighted center (x,y): ("+weightedCenter.getArg1()+","+weightedCenter.getArg2()+")");
		
		mCenterPixelIndex = (centerPointY * mImgWidth) + centerPointX;
		
		mTopLeft = new int[]{centerPointX, centerPointY};
		mBottomRight = new int[]{centerPointX, centerPointY};
		
		boolean areaFullyExpanded = false;
		
		boolean expandUp = true;
		boolean expandLeft = true;
		boolean expandRight = true;
		boolean expandDown = true;
		
		int numberOfAcceptableErrorPixels = 1;
		
		while(areaFullyExpanded == false)
		{
			int xSpread = mBottomRight[0] - mTopLeft[0];
			int ySpread = mBottomRight[1] - mTopLeft[1];
			//Log.v(Singleton.TAG, "ySpread = " + ySpread);
			
			if(expandUp == true)
			{
				int tempTopY = mTopLeft[1];
				
				if(tempTopY > 0)
				{
					// Can move up
					tempTopY = tempTopY - 1;
					
					int newPixelOffset = (tempTopY * mImgWidth) + mTopLeft[0];
					int incorrectPixelCount = 0;
					
					for(int i = 0; i <= xSpread; i++)
					{
						if(mPixels[newPixelOffset + i] != mPixels[mCenterPixelIndex])
						{
							if(incorrectPixelCount > numberOfAcceptableErrorPixels)
							{
								expandUp = false;
								tempTopY = mTopLeft[1];
								break;
							}
							else
							{
								incorrectPixelCount = incorrectPixelCount + 1;
							}
						}
					}
					
					if(expandUp == true)
					{
						mTopLeft[1] = tempTopY;
					}
				}
				else
				{
					expandUp = false;
				}
			}
			
			if(expandDown == true)
			{
				int tempBottomY = mBottomRight[1];
				if(mBottomRight[1] < (mImgHeight - 1))
				{
					// Can move down
					tempBottomY = tempBottomY + 1;
					
					int newPixelOffset = (tempBottomY * mImgWidth) + mTopLeft[0];
					int incorrectPixelCount = 0;
					
					for(int i = 0; i <= xSpread; i++)
					{
						if(mPixels[newPixelOffset + i] != mPixels[mCenterPixelIndex])
						{
							if(incorrectPixelCount > numberOfAcceptableErrorPixels)
							{
								expandDown = false;
								tempBottomY = mBottomRight[1];
								break;
							}
							else
							{
								incorrectPixelCount = incorrectPixelCount + 1;
							}
						}
					}
					
					if(expandDown == true)
					{
						mBottomRight[1] = tempBottomY;
					}
				}
				else
				{
					expandDown = false;
				}
			}
			
			if(expandLeft == true)
			{
				int tempTopX = mTopLeft[0];
				if(mTopLeft[0] > 0)
				{
					// Can move up
					tempTopX = tempTopX - 1;
					
					int yOffset = (mTopLeft[1] * mImgWidth) + tempTopX;
					int incorrectPixelCount = 0;
					
					for(int i = 0; i <= ySpread; i++)
					{
						if(mPixels[yOffset] != mPixels[mCenterPixelIndex])
						{
							if(incorrectPixelCount > numberOfAcceptableErrorPixels)
							{
								expandLeft = false;
								tempTopX = mTopLeft[0];
								break;
							}
							else
							{
								incorrectPixelCount = incorrectPixelCount + 1;
							}
						}
						yOffset = yOffset + mImgWidth;
					}
					
					if(expandLeft == true)
					{
						mTopLeft[0] = tempTopX;
					}
				}
				else
				{
					expandLeft = false;
				}
			}
			
			if(expandRight == true)
			{
				int tempBottomX = mBottomRight[0];
				if(mBottomRight[0] < (mImgWidth - 1))
				{
					// Can move up
					tempBottomX = tempBottomX + 1;
					
					int yOffset = (mTopLeft[1] * mImgWidth) + tempBottomX;
					int incorrectPixelCount = 0;
					
					for(int i = 0; i <= ySpread; i++)
					{
						if(mPixels[yOffset] != mPixels[mCenterPixelIndex])
						{
							if(incorrectPixelCount > numberOfAcceptableErrorPixels)
							{
								tempBottomX = mBottomRight[0];
								expandRight = false;
								break;
							}
							else
							{
								incorrectPixelCount = incorrectPixelCount + 1; 
							}
						}
						yOffset = yOffset + mImgWidth;
					}
					
					if(expandRight == true)
					{
						mBottomRight[0] = tempBottomX;
					}
				}
				else
				{
					expandRight = false;
				}
			}
			
			if(expandUp == false && expandDown == false &&
					expandLeft == false && expandRight == false)
			{
				areaFullyExpanded = true;
			}
		}
		
		//Pair maxGroup = imgPackage.getPixelGroups().get(0);
		int[] areaPixels = new int[regionPixels.length];
		
		int yOffset  = 0;
		for(int y = 0; y < mImgHeight; y++)
		{
			for(int x = 0; x < mImgWidth; x++)
			{
				if(y >= mTopLeft[1] && y <= mBottomRight[1] && x >= mTopLeft[0] && x <= mBottomRight[0])
				{
					areaPixels[yOffset+x] = COLOR_REGION;
				}
				else
				{
					areaPixels[yOffset+x] = NO_COLOR_REGION;
				}
			}
			yOffset = yOffset + mImgWidth;
		}
		
		imgPackage.setAreaExtractionPixels(areaPixels);
		imgPackage.setExtractionArea(new RegionGroup(mTopLeft[0], mTopLeft[1], mBottomRight[0], mBottomRight[1]));
		
		return imgPackage;
	}
}
