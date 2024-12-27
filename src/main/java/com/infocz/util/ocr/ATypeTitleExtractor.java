package com.infocz.util.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

public class ATypeTitleExtractor {
	
    private static final int BASE_DPI = 300;
    private static final int BASE_HEADER_HEIGHT = 14;  // 300 DPI에서의 높이
    
	private int SEARCH_WIDTH; 
	private int STEP_SIZE_WIDTH;

	private int SEARCH_HEIGHT;
	private int STEP_SIZE_HEIGHT;

	// 검색 시작 영역 제한
	private double ratioX = 16.0 / 18.0; // 오른쪽 1/6 영역
	private double  ratioY = 25.0 / 27.0; // 아래쪽 1/9 영역

	public ATypeTitleExtractor(int dpi) throws IOException {
		SEARCH_WIDTH = BASE_HEADER_HEIGHT * dpi / BASE_DPI * 20; // 40자 검색범위
		STEP_SIZE_WIDTH = (int)(SEARCH_WIDTH / 3); // 검색 이동 간격
		SEARCH_HEIGHT = BASE_HEADER_HEIGHT * dpi / BASE_DPI * 2; // 2줄 범위
		STEP_SIZE_HEIGHT = (int)(SEARCH_HEIGHT / 3); // 검색 이동 간격
	}

	public List<String> extract(List<String> alter, BufferedImage image, Tesseract tesseract) {
		String drawingNumber = findHeaderAndValue(image, new String[] { "DRAWING", "NUMBER" }, tesseract);
		String sheetNumber = findHeaderAndValue(image, new String[] { "SHT.", "NO." }, tesseract);

		if (drawingNumber == null || sheetNumber == null) return alter;

		return List.of(drawingNumber, sheetNumber);
	}

	private String findHeaderAndValue(BufferedImage fullImage, String[] headerTexts, Tesseract tesseract) {
		int startX = (int)(fullImage.getWidth() * ratioX);
		int startY = (int)(fullImage.getHeight() * ratioY);
		try {
			// 헤더 찾기
			for (int y = startY; y < fullImage.getHeight() - SEARCH_HEIGHT; y += STEP_SIZE_HEIGHT) {
				for (int x = startX; x < fullImage.getWidth() - SEARCH_WIDTH; x += STEP_SIZE_WIDTH) {
					BufferedImage croppedImage = fullImage.getSubimage(x, y, SEARCH_WIDTH, SEARCH_HEIGHT);
					List<Word> words = tesseract.getWords(croppedImage, TessPageIteratorLevel.RIL_WORD);

					// 두 단어 연속으로 찾기 (예: "SHT." "NO.")
					for (int i = 0; i < words.size() - 1; i++) {
						Word firstWord = words.get(i);
						Word secondWord = words.get(i + 1);
						System.out.println("x:"+x+" y:"+y+"firstWord:"+firstWord+" secondWord:" + secondWord);
						if (firstWord.getText().trim().equals(headerTexts[0]) && secondWord.getText().trim().equals(headerTexts[1])) {

							// 두 단어를 포함하는 통합 헤더 영역 생성
							Rectangle headerRect = new Rectangle(
									x + firstWord.getBoundingBox().x,
									y + firstWord.getBoundingBox().y,
									secondWord.getBoundingBox().x + secondWord.getBoundingBox().width - firstWord.getBoundingBox().x,
									firstWord.getBoundingBox().height);

							// 값 영역 계산 (헤더보다 좌우로 1/10씩 더 크고, 높이는 3배)
							int valueMarginWidth = headerRect.width / 5;
							Rectangle valueRect = new Rectangle(
									headerRect.x - valueMarginWidth, // 왼쪽으로 1/10 확장
									headerRect.y + (int)(headerRect.height * 2), // 헤더 바로 아래
									headerRect.width + (valueMarginWidth * 2), // 양쪽으로 1/10씩 확장
									headerRect.height * 3 // 높이는 3배
							);
							
							// 값 영역 추출 및 텍스트 인식
							BufferedImage valueImage = fullImage.getSubimage(valueRect.x, valueRect.y, valueRect.width, valueRect.height);

							// 디버깅을 위해 잘라낸 이미지 저장
							ImageIO.write(valueImage, "png", new File("test/"+firstWord.getText()+"_result.png"));
							String value = tesseract.doOCR(valueImage).trim();

							return value;
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
	
	public static void main(String[] args) throws IOException {

		int DPI = 300;
		Tesseract tesseract = new Tesseract();
		tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata"); // Tesseract 설치 경로
		tesseract.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._/ ");
		BufferedImage image = ImageIO.read(new File("D:/pgm_data/test2/921e42f2-ae00-485e-b9e4-05bceab6b594"));
		System.out.println(new ATypeTitleExtractor(DPI).extract(List.of("a", "b"), image, tesseract));
	}
}