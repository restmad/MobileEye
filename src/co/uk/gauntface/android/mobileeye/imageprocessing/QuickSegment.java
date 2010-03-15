package co.uk.gauntface.android.mobileeye.imageprocessing;

import java.util.ArrayList;

import android.util.Log;

import co.uk.gauntface.android.mobileeye.Singleton;

public class QuickSegment
{
	private int MAX_PIXEL_VALUE = 255;
	private int BUCKET_RANGE = 4;
	
	private int AVG_WINDOW_SIZE = 1;
	private int AVG_LOOPS = 2;
	
	private boolean SHOULD_MERGE_GROUPS = true;
	
	private int HISTOGRAM_SPACING = 10;
	private int BUCKET_MIN_SIZE_THRESHOLD = 300;
	
	private int GROUPING_VARIATION = 40;
	
	private int GROUP_NO_COLOR = -1;
	private int GROUP_0_COLOR = 255;
	private int GROUP_1_COLOR = 120;
	private int GROUP_2_COLOR = 60;
	
	public QuickSegment()
	{
		
	}
	
	public int[] segmentImage(int[] pixels, boolean logHistogram)
	{
		int[] pixelBucket = new int[(int) Math.floor((MAX_PIXEL_VALUE + 1) / BUCKET_RANGE)];
		
		// TODO Change for precomputation for possible speed up (No Modulo Operation)
		boolean hasPreComp = false;
		int maxIndex = 0;
		for(int i = 0; i < pixels.length; i++)
		{
			if(hasPreComp == false)
			{
				int remainder = pixels[i] % BUCKET_RANGE;
				int index = (pixels[i] - remainder) / BUCKET_RANGE;
				
				if((index < pixelBucket.length) == false)
				{
					index = (pixelBucket.length - 1);
				}
				
				pixelBucket[index] = pixelBucket[index] + 1;
				
				if(pixelBucket[maxIndex] < pixelBucket[index])
				{
					maxIndex = index;
				}
			}
		}
		
		/**
		 * Output the histogram to SD Card if neccessary
		 */
		if(logHistogram == true)
		{
			String histogramOuput = new String();;
			
			for(int i = 0; i < pixelBucket.length; i++)
			{
				histogramOuput = histogramOuput + i +","+pixelBucket[i]+"\n";
			}
			
			Utility.saveTextToSDCard(histogramOuput, "hist.txt");
		}
		
		/**
		 * Average Results
		 */
		for(int i = 0; i < AVG_LOOPS; i++)
		{
			int[] newPixelBuckets = new int[pixelBucket.length];
			for(int j = 0; j < pixelBucket.length; j++)
			{
				double averagedValue = 0;
				int startWindowIndex = j - AVG_WINDOW_SIZE;
				int endWindowIndex = j + AVG_WINDOW_SIZE;
				for(int k = startWindowIndex; k <= endWindowIndex; k++)
				{
					if(k >= 0 && k < pixelBucket.length)
					{
						averagedValue = averagedValue + pixelBucket[k];
					}
				}
				averagedValue = averagedValue / ((2*AVG_WINDOW_SIZE) + 1);
				newPixelBuckets[j] = (int) averagedValue;
			}
			pixelBucket = newPixelBuckets;
		}
		
		/**
		 * Output the reduced histogram to SD Card if neccessary
		 */
		if(logHistogram == true)
		{
			String histogramOuput = new String();
			
			for(int i = 0; i < pixelBucket.length; i++)
			{
				histogramOuput = histogramOuput+pixelBucket[i]+"\n";
			}
			
			Utility.saveTextToSDCard(histogramOuput, "avghist.txt");
		}
		
		int[] groups = hillClimb(pixelBucket, maxIndex);
		
		ArrayList<Pair> groupValues = new ArrayList<Pair>();
		for(int i = (groups.length - 1); i >= 0; i--)
		{
			// TODO Make this span more of the histogram lowest 1, next one up 2, third one third
			int minGroupValue = groups[i];
			int maxGroupValue = groups[i];
			
			boolean findingMin = true;
			while(findingMin)
			{
				if(minGroupValue == 0 || pixelBucket[minGroupValue] < pixelBucket[minGroupValue - 1])
				{
					findingMin = false;
				}
				else
				{
					minGroupValue = minGroupValue - 1;
				}
			}
			
			boolean findingMax = true;
			while(findingMax)
			{
				if(maxGroupValue == (pixelBucket.length - 1) || pixelBucket[maxGroupValue] < pixelBucket[maxGroupValue + 1])
				{
					findingMax = false;
				}
				else
				{
					maxGroupValue = maxGroupValue + 1;
				}
			}
			
			if(SHOULD_MERGE_GROUPS == true)
			{
				for(int j = 0; j < i && j < groupValues.size(); j++)
				{
					Pair p = groupValues.get(j);
					if(minGroupValue < ((Number) p.getArg2()).intValue() && maxGroupValue > ((Number) p.getArg2()).intValue())
					{
						if(minGroupValue > ((Number) p.getArg1()).intValue())
						{
							minGroupValue = ((Number) p.getArg1()).intValue();
							groupValues.remove(j);
						}
					}
					else if(maxGroupValue > ((Number) p.getArg1()).intValue() && minGroupValue < ((Number) p.getArg1()).intValue())
					{
						if(maxGroupValue < ((Number) p.getArg2()).intValue())
						{
							maxGroupValue = ((Number) p.getArg2()).intValue();
							groupValues.remove(j);
						}
					}
				}
			}
			
			minGroupValue = minGroupValue * BUCKET_RANGE;
			maxGroupValue = (maxGroupValue * BUCKET_RANGE) + BUCKET_RANGE;

			Pair p = new Pair(minGroupValue, maxGroupValue);
			
			groupValues.add(p);
		}
		
		/**
		 * Output the peaks / groups to SD Card if neccessary
		 */
		if(logHistogram == true)
		{
			String histogramOuput = new String();;
			
			for(int i = 0; i < groups.length; i++)
			{
				histogramOuput = histogramOuput + i +","+groups[i]+"\n";
			}
			
			histogramOuput = histogramOuput+"\n\n\n\n";
			
			for(int i = 0; i < groupValues.size(); i++)
			{
				Pair p = groupValues.get(i);
				
				int minValue = ((Number)p.getArg1()).intValue();
				int maxValue = ((Number)p.getArg2()).intValue();
				
				histogramOuput = histogramOuput + i +" - ("+minValue+","+maxValue+")\n";
			}
			
			Utility.saveTextToSDCard(histogramOuput, "groups.txt");
		}
		
		int[] newPixels = new int[pixels.length];
		for(int i = 0; i < pixels.length; i++)
		{
			for(int j = 0; j < groupValues.size(); j++)
			{
				Pair groupValue = groupValues.get(j);
				//if(pixels[i] >= (groups[j] - GROUPING_VARIATION) && pixels[i] <= (groups[j] + GROUPING_VARIATION))
				//{
				if(pixels[i] >= ((Number)groupValue.getArg1()).intValue() && pixels[i] < ((Number)groupValue.getArg2()).intValue())
				{
					// The order is reversed to give GROUP_0_COLOR Prominence
					if(j == 0)
					{
						newPixels[i] = GROUP_0_COLOR;
						break;
					}
					else if(j == 1)
					{
						newPixels[i] = GROUP_1_COLOR;
						break;
					}
					else if(j == 2)
					{
						newPixels[i] = GROUP_2_COLOR;
						break;
					}
				}
				else if((j+1) == groups.length)
				{
					newPixels[i] = GROUP_NO_COLOR;
					break;
				}
			}
		}
		
		
		/**
		 * 
		 * OLD CODE - 11 March 2010
		 * 
		 */
		//int[] pixelBucket = new int[(MAX_PIXEL_VALUE+1)];
		
		// Bucket Sort the Pixels [Histogram Essentially]
		//for(int i = 0; i < pixels.length; i++)
		//{
		//	pixelBucket[pixels[i]] = pixelBucket[pixels[i]] + 1;
		//}
		
		/**
		 * Average and Reduce the data
		 */
		//int[] reducedPixelBucket = new int[(int)Math.floor(pixelBucket.length / REDUCE_AVG_WINDOW_HALF)];
		//int counter = 0;
		//int maxIndex = 0;
		//for(int i = REDUCE_AVG_WINDOW_HALF; i < MAX_PIXEL_VALUE; i = i + REDUCE_AVG_WINDOW_HALF)
		//{
//			double cumulativeValue = 0;
			//for(int j = i-REDUCE_AVG_WINDOW_HALF; j < i+REDUCE_AVG_WINDOW_HALF; j++)
			//{
//				if(j >= 0 && j < pixelBucket.length)
				//{
//					cumulativeValue = cumulativeValue + pixelBucket[j];
				//}
				//else
				//{
//					throw new RuntimeException("QuickSegment shouldn't be reaching out of bounds of pixel bucket");
				//}
			//}
			//cumulativeValue = cumulativeValue / (REDUCE_AVG_WINDOW_HALF * 2);
			//reducedPixelBucket[counter] = (int) cumulativeValue;
			
			//if(cumulativeValue > maxIndex)
			//{
				//maxIndex = counter;
			//}
			
			//counter = counter + 1;
		//}
		
		//Pair[] groupValues = new Pair[groups.length];
		//for(int i = (groups.length - 1); i > 0; i--)
		//{
			// TODO Make this span more of the histogram lowest 1, next one up 2, third one third
			//int minGroupValue = (groups[i] - 1) * REDUCE_AVG_WINDOW_HALF;
			//int maxGroupValue = ((groups[i] + 1) * REDUCE_AVG_WINDOW_HALF) + (2 * REDUCE_AVG_WINDOW_HALF);
			
			//groupValues[i] = new Pair(minGroupValue, maxGroupValue);
		//}
		
		return newPixels;
	}
	
	private int[] hillClimb(int[] data, int maxIndex)
	{
		ArrayList<Integer> maxPoints = new ArrayList<Integer>();
		
		// Make this dynamic
		int[] hillStartPoints = new int[]{0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, maxIndex};
		
		for(int i = 0; i < hillStartPoints.length; i++)
		{
			int currentPoint = hillStartPoints[i];
			boolean maximumFound = false;
			while(maximumFound == false)
			{
				if((currentPoint + 1) < data.length && data[currentPoint + 1] > data[currentPoint])
				{
					currentPoint = currentPoint + 1;
				}
				/**else if((currentPoint + 2) < data.length && data[currentPoint + 2] > data[currentPoint])
				{
					currentPoint = currentPoint + 2;
				}**/
				else if((currentPoint - 1) >= 0 && data[currentPoint - 1] > data[currentPoint])
				{
					currentPoint = currentPoint - 1;
				}
				/**else if((currentPoint - 2) >= 0 && data[currentPoint - 2] > data[currentPoint])
				{
					currentPoint = currentPoint - 2;
				}**/
				else
				{
					// Make sure small values don't get considered as a group
					if(data[currentPoint] > BUCKET_MIN_SIZE_THRESHOLD)
					{
						maxPoints.add(currentPoint);
					}
					maximumFound = true;
				}
			}
		}
		
		int group1 = -1;
		int group2 = -1;
		int group3 = -1;
		
		for(int i = 0; i < maxPoints.size(); i++)
		{
			int maxPoint = maxPoints.get(i);
			
			if(maxPoint != group1 && maxPoint != group2 && maxPoint != group3)
			{
				if(group1 == -1 || data[maxPoint] > data[group1])
				{
					group3 = group2;
					group2 = group1;
					group1 = maxPoint;
				}
				else if(group2 == -1 || data[maxPoint] > data[group2])
				{
					group3 = group2;
					group2 = maxPoint;
				}
				else if(group3 == -1 || data[maxPoint] > data[group3])
				{
					group3 = maxPoint;
				}
			}
		}
		
		if(group1 == -1)
		{
			return new int[]{};
		}
		else if(group2 == -1)
		{
			return new int[]{group1};
		}
		else if(group3 == -1)
		{
			return new int[]{group1, group2};
		}
		else
		{
			return new int[]{group1, group2, group3};
		}
	}
}