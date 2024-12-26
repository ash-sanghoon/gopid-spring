package com.infocz.util.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;

public class StandardTitleBlockExtractor {
	public static class DrawingInfo {
       public Rectangle headerLocation;  // 헤더 텍스트의 위치
       public String value;             // 찾은 값
       
       public DrawingInfo(Rectangle location, String value) {
           this.headerLocation = location;
           this.value = value;
       }
	}
   
	private final BufferedImage image;
	private final Tesseract tesseract;

	private static final int SEARCH_WIDTH = 300; // 검색 영역 너비
	private static final int SEARCH_HEIGHT = 50; // 검색 영역 높이
	private static final int STEP_SIZE = 70; // 검색 이동 간격

//	private static final int SEARCH_WIDTH = 500; // 검색 영역 너비
//	private static final int SEARCH_HEIGHT = 300; // 검색 영역 높이
//	private static final int STEP_SIZE = 100; // 검색 이동 간격
	public static class TitleBlockInfo {
		public final String drawingNumber;
		public final String sheetNumber;
		public final Rectangle drawingNumberLocation;
		public final Rectangle sheetNumberLocation;

		public TitleBlockInfo(String drawingNumber, String sheetNumber, Rectangle drawingNumberLocation,
				Rectangle sheetNumberLocation) {
			this.drawingNumber = drawingNumber;
			this.sheetNumber = sheetNumber;
			this.drawingNumberLocation = drawingNumberLocation;
			this.sheetNumberLocation = sheetNumberLocation;
		}

		@Override
		public String toString() {
			return String.format("Drawing Number: %s, Sheet Number: %s", drawingNumber, sheetNumber);
		}
	}

	public StandardTitleBlockExtractor(String imagePath) throws IOException {
		this.image = ImageIO.read(new File(imagePath));
		this.tesseract = new Tesseract();
		tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata"); // Tesseract 설치 경로

		this.tesseract.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._/ ");
	}

	public TitleBlockInfo extract(String alterDrawing, String alterSheet) {
		DrawingInfo drawingNumberInfo = findHeaderAndValue(image, new String[] { "DRAWING", "NUMBER" });
		DrawingInfo sheetNumberInfo = findHeaderAndValue(image, new String[] { "SHT.", "NO." });

		if (drawingNumberInfo == null || sheetNumberInfo == null) {
			return new TitleBlockInfo(alterDrawing,alterSheet,null, null);
		}

		return new TitleBlockInfo(drawingNumberInfo.value, sheetNumberInfo.value, drawingNumberInfo.headerLocation,
				sheetNumberInfo.headerLocation);
	}

	public DrawingInfo findHeaderAndValue(BufferedImage fullImage, String[] headerTexts) {

		// 검색 시작 영역 제한
		int startX = (int) (fullImage.getWidth() * 5.0 / 6.0); // 오른쪽 1/6 영역
		int startY = (int) (fullImage.getHeight() * 8.0 / 9.0); // 아래쪽 1/9 영역

		try {
			// 헤더 찾기
			int z = 0;
			for (int y = startY; y < fullImage.getHeight() - SEARCH_HEIGHT; y += STEP_SIZE) {
				for (int x = startX; x < fullImage.getWidth() - SEARCH_WIDTH; x += STEP_SIZE) {
					BufferedImage croppedImage = fullImage.getSubimage(x, y, SEARCH_WIDTH, SEARCH_HEIGHT);
					List<Word> words = tesseract.getWords(croppedImage, TessPageIteratorLevel.RIL_WORD);

					// 두 단어 연속으로 찾기 (예: "SHT." "NO.")
					for (int i = 0; i < words.size() - 1; i++) {
						Word firstWord = words.get(i);
						Word secondWord = words.get(i + 1);
						if (firstWord.getText().trim().equals("SHT.")) {
							System.out.println("KKKK");
						}
						if (firstWord.getText().trim().equals(headerTexts[0])) {
							System.out.println(z +" BBBBBBBB firstWord:"+firstWord);
							System.out.println(z +" BBBBBBBB secondWord:"+secondWord);

				            // 디버깅을 위해 잘라낸 이미지 저장
				            ImageIO.write(croppedImage, "png", new File("test/"+(z++)+"cropped_"+firstWord.getText()+".png"));
						}

						if (firstWord.getText().trim().equals(headerTexts[0])
								&& secondWord.getText().trim().equals(headerTexts[1])) {

							// 두 단어를 포함하는 통합 헤더 영역 생성
							Rectangle headerRect = new Rectangle(
									x + Math.min(firstWord.getBoundingBox().x, secondWord.getBoundingBox().x),
									y + Math.min(firstWord.getBoundingBox().y, secondWord.getBoundingBox().y),
									Math.max(secondWord.getBoundingBox().x + secondWord.getBoundingBox().width,
											firstWord.getBoundingBox().x + firstWord.getBoundingBox().width)
											- Math.min(firstWord.getBoundingBox().x, secondWord.getBoundingBox().x),
									Math.max(firstWord.getBoundingBox().height, secondWord.getBoundingBox().height));

							// 값 영역 계산 (헤더보다 좌우로 1/10씩 더 크고, 높이는 3배)
							int valueMarginWidth = headerRect.width / 10;
							Rectangle valueRect = new Rectangle(headerRect.x - valueMarginWidth, // 왼쪽으로 1/10 확장
									headerRect.y + headerRect.height * 2, // 헤더 바로 아래
									headerRect.width + (valueMarginWidth * 2), // 양쪽으로 1/10씩 확장
									headerRect.height * 3 // 높이는 3배
							);

							// 값 영역 추출 및 텍스트 인식
							BufferedImage valueImage = fullImage.getSubimage(valueRect.x, valueRect.y, valueRect.width,
									valueRect.height);

							// 디버깅을 위해 잘라낸 이미지 저장
							ImageIO.write(valueImage, "png", new File("test/"+firstWord.getText()+"_result.png"));
							String value = tesseract.doOCR(valueImage).trim();

							return new DrawingInfo(headerRect, value);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
	
	   public static void main(String[] args) {
	       try {
	    	   
	           StandardTitleBlockExtractor extractor = new StandardTitleBlockExtractor("D:/pgm_data/test2/7f29a299-0d9c-45fe-991b-5e96709dcb0f");

	           //StandardTitleBlockExtractor extractor = new StandardTitleBlockExtractor("D:/pgm_data/test2/4c991f16-d612-46d5-8016-549b2b883ce8");
	           //StandardTitleBlockExtractor extractor = new StandardTitleBlockExtractor("D:/pgm_data/test2/77bd65f7-c2cd-4e38-8d32-492a265d46d1");
	           TitleBlockInfo info = extractor.extract("test", "bbb");
	           System.out.println(info);
	           
	       } catch (Exception e) {
	           e.printStackTrace();
	       }
	   }
}