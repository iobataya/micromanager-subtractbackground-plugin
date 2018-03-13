package org.micromanager.subtractbackground;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MMException;

/**
 * Extensions to org.micromanager.utils.ImageUtils
 * 
 * @author sunada
 */
public class ImageUtils2 extends ImageUtils{
   
   public static ImageProcessor subtractImageProcessorsWithOffset(ImageProcessor proc1, ImageProcessor proc2, int offset)
           throws MMException {
	      int w1 = proc1.getWidth();
	      int w2 = proc2.getWidth();
      if ((proc1.getWidth() != proc2.getWidth())
              || (proc1.getHeight() != proc2.getHeight())) {
         throw new MMException("Error: Images are of unequal size, "+String.valueOf(w1)+
        		 ","+String.valueOf(w2)+
        		 ",ROI: "+proc1.getRoi().toString()+
        		 ","+proc2.getRoi().toString());
      }
      try {
         if (proc1 instanceof ByteProcessor && proc2 instanceof ByteProcessor) {
            return subtractByteProcessorsWithOffset((ByteProcessor) proc1, (ByteProcessor) proc2, offset);
         } else if (proc1 instanceof ShortProcessor && proc2 instanceof ShortProcessor) {
            return subtractShortProcessorsWithOffset((ShortProcessor) proc1, (ShortProcessor) proc2, offset);
         } else if (proc1 instanceof ShortProcessor && proc2 instanceof ByteProcessor) {
            return subtractShortByteProcessorsWithOffset((ShortProcessor) proc1, (ByteProcessor) proc2, offset);
         } else {
             throw new MMException("Types of images to be subtracted were not compatible");
         }
      } catch (ClassCastException ex) {
         throw new MMException("Types of images to be subtracted were not compatible");
      }
   }
   
   private static ByteProcessor subtractByteProcessorsWithOffset(ByteProcessor proc1, ByteProcessor proc2, int offset) {
      return new ByteProcessor(proc1.getWidth(), proc1.getHeight(),
              subtractPixelArraysWithOffset((byte []) proc1.getPixels(), (byte []) proc2.getPixels(), offset),
              null);
   }
   
   private static ShortProcessor subtractShortByteProcessorsWithOffset(ShortProcessor proc1, ByteProcessor proc2, int offset) {
      return new ShortProcessor(proc1.getWidth(), proc1.getHeight(),
              subtractPixelArraysWithOffset((short []) proc1.getPixels(), (byte []) proc2.getPixels(), offset),
              null);
   }
   
   private static ShortProcessor subtractShortProcessorsWithOffset(ShortProcessor proc1, ShortProcessor proc2, int offset) {
      return new ShortProcessor(proc1.getWidth(), proc1.getHeight(),
              subtractPixelArraysWithOffset((short []) proc1.getPixels(), (short []) proc2.getPixels(), offset),
              null);
   }
   
   public static byte[] subtractPixelArraysWithOffset(byte[] array1, byte[] array2, int offset) {
      int l = array1.length;
      byte[] result = new byte[l];
      for (int i=0;i<l;++i) {
         result[i] = toByte(unsignedValue(array1[i]) - unsignedValue(array2[i]) + offset);
      }
      return result;
   }
   
   public static short[] subtractPixelArraysWithOffset(short[] array1, short[] array2, int offset) {
      int l = array1.length;
      short[] result = new short[l];
      for (int i=0;i<l;++i) {
         result[i] = toShort(unsignedValue(array1[i]) - unsignedValue(array2[i]) + offset);
      }
      return result;
   }
   
   public static short[] subtractPixelArraysWithOffset(short[] array1, byte[] array2, int offset) {
      int l = array1.length;
      short[] result = new short[l];
      for (int i=0;i<l;++i) {
         result[i] = toShort(unsignedValue(array1[i]) - unsignedValue(array2[i]) + offset);
      }
      return result;
   }
   
   public static byte toByte(int value) {
      return (byte) Math.max(0, Math.min(255, value));
   }
   
   public static short toShort(int value) {
      return (short) Math.max(0, Math.min(65535, value));
   }
   
}
