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

public class CTypeTitleExtractor {
	
    private static final int BASE_DPI = 300;
    private static final int BASE_HEADER_HEIGHT = 25;  // 300 DPI에서의 높이

	private int SEARCH_WIDTH; 
	private int STEP_SIZE_WIDTH;

	private int SEARCH_HEIGHT;
	private int STEP_SIZE_HEIGHT;

	public CTypeTitleExtractor(int dpi) throws IOException {
		SEARCH_WIDTH = (int)(BASE_HEADER_HEIGHT * dpi / BASE_DPI * 20); // 15자 검색범위
		STEP_SIZE_WIDTH = (int)(SEARCH_WIDTH / 3); // 검색 이동 간격
		SEARCH_HEIGHT = (int)(BASE_HEADER_HEIGHT * dpi / BASE_DPI * 2); // 2줄 범위
		STEP_SIZE_HEIGHT = (int)(SEARCH_HEIGHT / 3); // 검색 이동 간격
	}

	// "DRAWING", "N°"
	//"SH.N°"
	public List<String> extract(List<String> alter, BufferedImage image, Tesseract tesseract) {
		String drawingNumber = findHeaderAndValue(image, new String[] { "DWG.", "NO." }, tesseract);

		if (drawingNumber == null) return alter;
		String[] ll = drawingNumber.split("-");
		
		return List.of(ll[0] + "-" + ll[1], ll[2]);
	}

	public String findHeaderAndValue(BufferedImage fullImage, String[] headerTexts, Tesseract tesseract) {

		// 검색 시작 영역 제한
		int startX = (int) (fullImage.getWidth() * 0.8);
		int startY = (int) (fullImage.getHeight() * 0.9);

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

						System.out.println("x:"+x+" y:"+y+"firstWord:"+firstWord+"secondWord:"+secondWord);
						if (firstWord.getText().trim().equals(headerTexts[0]) && secondWord.getText().trim().equals(headerTexts[1])) {
							// 두 단어를 포함하는 통합 헤더 영역 생성
							Rectangle headerRect = new Rectangle(
									x + Math.min(firstWord.getBoundingBox().x, secondWord.getBoundingBox().x),
									y + Math.min(firstWord.getBoundingBox().y, secondWord.getBoundingBox().y),
									Math.max(secondWord.getBoundingBox().x + secondWord.getBoundingBox().width,
											firstWord.getBoundingBox().x + firstWord.getBoundingBox().width)
											- Math.min(firstWord.getBoundingBox().x, secondWord.getBoundingBox().x),
									Math.min(firstWord.getBoundingBox().height, secondWord.getBoundingBox().height));

							// 값 영역 계산 (헤더보다 좌우로 1/10씩 더 크고, 높이는 3배)
							int valueMarginWidth = headerRect.width / 10;
							Rectangle valueRect = new Rectangle(
									headerRect.x + valueMarginWidth + headerRect.width,
									headerRect.y, 
									headerRect.width * 2,
									headerRect.height
							);
							
							// 값 영역 추출 및 텍스트 인식
							BufferedImage valueImage = fullImage.getSubimage(valueRect.x, valueRect.y, valueRect.width, valueRect.height);

							ImageIO.write(valueImage, "png", new File("test/"+firstWord.getText()+"_result.png"));
							return tesseract.doOCR(valueImage).trim();
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
		BufferedImage image = ImageIO.read(new File("D:/pgm_data/test2/ac0c0f65-693f-4224-b8b7-602f0bc9a17f"));
		System.out.println(new CTypeTitleExtractor(DPI).extract(List.of("a", "b"), image, tesseract));
	}
}