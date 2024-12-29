package com.infocz.util.ocr;

import java.awt.Rectangle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.infocz.gopid.StandAloneApp;

import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

@Component
public class BTypeTitleExtractor {
	
    @Value("${com.infocz.upload.debug.path}") // application 의 properties 의 변수
    private String debugPath;

    private static final int BASE_DPI = 300;
    private static final int BASE_HEADER_HEIGHT = 32;  // 300 DPI에서의 높이

	private int SEARCH_WIDTH; 
	private int STEP_SIZE_WIDTH;

	private int SEARCH_HEIGHT;
	private int STEP_SIZE_HEIGHT;
	
	public BTypeTitleExtractor() throws IOException {
		
	}

	// "DRAWING", "N°"
	//"SH.N°"
	public List<String> extract(List<String> alter, BufferedImage image, Tesseract tesseract, int dpi) {
		SEARCH_WIDTH = (int)(BASE_HEADER_HEIGHT * dpi / BASE_DPI * 15); // 15자 검색범위
		STEP_SIZE_WIDTH = (int)(SEARCH_WIDTH / 3); // 검색 이동 간격
		SEARCH_HEIGHT = (int)(BASE_HEADER_HEIGHT * dpi / BASE_DPI * 2); // 2줄 범위
		STEP_SIZE_HEIGHT = (int)(SEARCH_HEIGHT / 3); // 검색 이동 간격
		String drawingNumber = findHeaderAndValue(image, new String[] { "DRAWING", "N" }, tesseract);
		String sheetNumber = findHeaderAndValue(image, new String[] { "SH.N" }, tesseract);

		if (drawingNumber == null || sheetNumber == null) return alter;

		return List.of(drawingNumber, sheetNumber);
	}

	public String findHeaderAndValue(BufferedImage fullImage, String[] headerTexts, Tesseract tesseract) {
		tesseract.setVariable("tessedit_pageseg_mode", "7"); // word

		// 검색 시작 영역 제한
		int startX = (int) (fullImage.getWidth() * 15.0 / 16.0); // 오른쪽 1/6 영역
		int startY = (int) (fullImage.getHeight() * 7.0 / 8.0); // 아래쪽 1/9 영역

		try {
			// 헤더 찾기
			for (int y = startY; y < fullImage.getHeight() - SEARCH_HEIGHT; y += STEP_SIZE_HEIGHT) {
				for (int x = startX; x < fullImage.getWidth() - SEARCH_WIDTH; x += STEP_SIZE_WIDTH) {

					BufferedImage croppedImage = fullImage.getSubimage(x, y, SEARCH_WIDTH, SEARCH_HEIGHT);
					List<Word> words = tesseract.getWords(croppedImage, TessPageIteratorLevel.RIL_WORD);

					if(headerTexts.length == 1) {
						for (int i = 0; i < words.size(); i++) {
							Word firstWord = words.get(i);
							System.out.println("x:"+x+" y:"+y+"firstWord:"+firstWord);
							if (firstWord.getText().trim().equals(headerTexts[0])) {
								// 값 영역 추출 및 텍스트 인식
								Rectangle rect = firstWord.getBoundingBox();
								int valueMarginWidth = rect.width / 3;   // SH.N 이 아래 칸 값 보다 작아서 더 크게
								BufferedImage valueImage = fullImage.getSubimage(
										x + rect.x - valueMarginWidth, 
										y + rect.y + (int)(rect.height * 1.5), 
										rect.width + (valueMarginWidth * 2), 
										rect.height * 3);

								ImageIO.write(valueImage, "png", new File(debugPath+"/"+firstWord.getText()+"_result.png"));
								return tesseract.doOCR(valueImage).trim();
							}
						}
					}else {
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
								Rectangle valueRect = new Rectangle(headerRect.x - valueMarginWidth, // 왼쪽으로 1/10 확장
										headerRect.y + headerRect.height * 2, // 헤더 바로 아래
										headerRect.width + (valueMarginWidth * 2), // 양쪽으로 1/10씩 확장
										headerRect.height * 3 // 높이는 3배
								);
								
								// 값 영역 추출 및 텍스트 인식
								BufferedImage valueImage = fullImage.getSubimage(valueRect.x, valueRect.y, valueRect.width, valueRect.height);

								ImageIO.write(valueImage, "png", new File(debugPath+"/"+firstWord.getText()+"_result.png"));
								return tesseract.doOCR(valueImage).trim();
							}
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

        SpringApplication app = new SpringApplication(StandAloneApp.class);
        
        // 환경변수나 시스템 프로퍼티로 모드 결정
        String mode = System.getProperty("app.mode", "web");  // 기본값은 web
        if ("standalone".equals(mode)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(args);

        BTypeTitleExtractor bTypeTitleExtractor = context.getBean(BTypeTitleExtractor.class);

		int DPI = 300;
		Tesseract tesseract = new Tesseract();
		tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata"); // Tesseract 설치 경로
		tesseract.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._/ ");
		BufferedImage image = ImageIO.read(new File("D:/pgm_data/test2/921e42f2-ae00-485e-b9e4-05bceab6b594"));
		System.out.println(bTypeTitleExtractor.extract(List.of("a", "b"), image, tesseract, DPI));
	}
}