package com.neuronrobotics.imageprovider;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import javafx.scene.image.Image;

public class OpenCVImageConversionFactory {
	/**
	 * Converts/writes a Mat into a BufferedImage.
	 * 
	 * @param matrix
	 *            Mat of type CV_8UC3 or CV_8UC1
	 * @return BufferedImage of type TYPE_3BYTE_BGR or TYPE_BYTE_GRAY
	 */
	public static BufferedImage matToBufferedImage(org.opencv.core.Mat matrix) {
		int cols = matrix.cols();
		int rows = matrix.rows();
		int elemSize = (int) matrix.elemSize();
		byte[] data = new byte[cols * rows * elemSize];
		int type;
		matrix.get(0, 0, data);
		switch (matrix.channels()) {
		case 1:
			type = BufferedImage.TYPE_BYTE_GRAY;
			break;
		case 3:
			type = BufferedImage.TYPE_3BYTE_BGR;
			// bgr to rgb
			byte b;
			for (int i = 0; i < data.length; i = i + 3) {
				b = data[i];
				data[i] = data[i + 2];
				data[i + 2] = b;
			}
			break;
		default:
			return null;
		}
		BufferedImage image2 = new BufferedImage(cols, rows, type);
		image2.getRaster().setDataElements(0, 0, cols, rows, data);
		return image2;
	}
	

	public static void deepCopy(org.opencv.core.Mat src, BufferedImage dest) {
		Graphics g = dest.createGraphics();
		g.drawImage(matToBufferedImage(src), 0, 0, null);
	}
	
	/**
	 * Converts/writes a Mat into a BufferedImage.
	 * 
	 * @param matrix
	 *            Mat of type CV_8UC3 or CV_8UC1
	 * @return BufferedImage of type TYPE_3BYTE_BGR or TYPE_BYTE_GRAY
	 */
	public static Image matToJfxImage(org.opencv.core.Mat matrix) {

		return AbstractImageProvider.getJfxImage(matToBufferedImage( matrix) ) ;
	}
	
	public static void bufferedImageToMat(BufferedImage input, org.opencv.core.Mat output){
		org.opencv.core.Mat mb;

		byte[] tmpByteArray = ((DataBufferByte) input.getRaster().getDataBuffer()).getData();
		mb = new org.opencv.core.Mat(input.getHeight(),input.getWidth(),16); //8uc3
	    mb.put(0, 0, tmpByteArray);
	    mb.copyTo(output);
	}
}
