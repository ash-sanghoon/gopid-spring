package com.infocz.util.ocr;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class ImageUtil {
	static {
       // Java 12 이상에서는 loadLocally() 사용
       nu.pattern.OpenCV.loadLocally();
	}
	public static BufferedImage rotateImage(BufferedImage image, int degrees) {
	    // BufferedImage -> Mat 변환
	    Mat source = bufferedImageToMat(image);
	    
	    // 이미지 중심점 계산
	    org.opencv.core.Point center = new org.opencv.core.Point(source.cols() / 2.0, source.rows() / 2.0);
	    
	    // 회전 행렬 생성
	    Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, degrees, 1.0);
	    
	    // 회전된 이미지의 크기 계산
	    org.opencv.core.Size size;
	    if (Math.abs(degrees) == 90 || Math.abs(degrees) == 270) {
	        size = new org.opencv.core.Size(source.rows(), source.cols());
	    } else {
	        size = new org.opencv.core.Size(source.cols(), source.rows());
	    }
	    
	    // 회전 적용
	    Mat rotated = new Mat();
	    Imgproc.warpAffine(source, rotated, rotationMatrix, size, 
	        Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new org.opencv.core.Scalar(255));
	    
	    // Mat -> BufferedImage 변환
	    return matToBufferedImage(rotated);
	}
	
	public static BufferedImage preprocessOnlyGlyph(BufferedImage originalImage, int maxSize) {
		// BufferedImage -> Mat 변환
		Mat mat = bufferedImageToMat(originalImage);
		
		// OpenCV 처리
		Mat processedMat = preprocessOnlyGlyph(mat, maxSize);
		
		// Mat -> BufferedImage 변환
		BufferedImage resultImage = matToBufferedImage(processedMat);

		return resultImage;
	}
	
	public static Mat preprocessOnlyGlyph(Mat source, int maxSize) {
	    Mat processed = new Mat();
	    
	    // 이미지 반전 (흰색 배경, 검은색 텍스트로 변환)
	    Core.bitwise_not(source, processed);
	    
	    Mat labels = new Mat();
	    Mat stats = new Mat();
	    Mat centroids = new Mat();
	    
	    Imgproc.connectedComponentsWithStats(
	        processed, labels, stats, centroids, 4
	    );
	    
	    // 결과 이미지 생성
	    Mat result = Mat.zeros(processed.size(), processed.type());
	    
	    // 컴포넌트 필터링
	    for (int i = 1; i < stats.rows(); i++) {
	        int height = (int) stats.get(i, Imgproc.CC_STAT_HEIGHT)[0];
	        int width = (int) stats.get(i, Imgproc.CC_STAT_WIDTH)[0];
	        
	        if (height < maxSize && width < maxSize) {
	            int x = (int) stats.get(i, Imgproc.CC_STAT_LEFT)[0];
	            int y = (int) stats.get(i, Imgproc.CC_STAT_TOP)[0];
	            
	            Mat roi = processed.submat(y, y + height, x, x + width);
	            roi.copyTo(result.submat(y, y + height, x, x + width));
	        }
	    }
	    
	    // 결과 이미지를 다시 반전 (원래대로 되돌림)
	    Core.bitwise_not(result, result);
	    
	    return result;
	}
	
	public static Mat bufferedImageToMat(BufferedImage image) {
	    // 이진 이미지의 경우를 위한 처리
	    if (image.getType() == BufferedImage.TYPE_BYTE_BINARY) {
	        // 먼저 TYPE_BYTE_GRAY로 변환
	        BufferedImage grayImage = new BufferedImage(
	            image.getWidth(), 
	            image.getHeight(), 
	            BufferedImage.TYPE_BYTE_GRAY
	        );
	        Graphics2D g2d = grayImage.createGraphics();
	        g2d.drawImage(image, 0, 0, null);
	        g2d.dispose();
	        image = grayImage;
	    }
	    
	    byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
	    Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC1);
	    mat.put(0, 0, data);
	    
	    return mat;
	}

	public static BufferedImage matToBufferedImage(Mat mat) {
	    // 단일 채널(그레이스케일/이진) 이미지 처리
	    if (mat.channels() != 1) {
	        throw new IllegalArgumentException("입력 Mat은 단일 채널이어야 합니다.");
	    }
	    
	    int type = BufferedImage.TYPE_BYTE_GRAY;
	    BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
	    
	    byte[] data = new byte[mat.cols() * mat.rows()];
	    mat.get(0, 0, data);
	    
	    // 이미지 데이터 직접 설정
	    DataBufferByte buffer = (DataBufferByte) image.getRaster().getDataBuffer();
	    byte[] targetData = buffer.getData();
	    System.arraycopy(data, 0, targetData, 0, data.length);
	    
	    return image;
	}

}