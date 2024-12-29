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
public class DTypeTitleExtractor {
	
    @Value("${com.infocz.upload.debug.path}") // application 의 properties 의 변수
    private String debugPath;
	
    private static final int BASE_DPI = 300;
    private static final int BASE_HEADER_HEIGHT = 24;  // 300 DPI에서의 높이

	private int SEARCH_WIDTH; 
	private int STEP_SIZE_WIDTH;

	private int SEARCH_HEIGHT;
	private int STEP_SIZE_HEIGHT;

	public DTypeTitleExtractor() throws IOException {
	}

	// "DRAWING", "N°"
	//"SH.N°"
	public List<String> extract(List<String> alter, BufferedImage image, Tesseract tesseract, int dpi) {
		SEARCH_WIDTH = (int)(BASE_HEADER_HEIGHT * dpi / BASE_DPI * 12); // 15자 검색범위
		STEP_SIZE_WIDTH = (int)(SEARCH_WIDTH / 3); // 검색 이동 간격
		SEARCH_HEIGHT = (int)(BASE_HEADER_HEIGHT * dpi / BASE_DPI * 2); // 2줄 범위
		STEP_SIZE_HEIGHT = (int)(SEARCH_HEIGHT / 3); // 검색 이동 간격
		String drawingNumber = findHeaderAndValue(image, new String[] { "OWNER" }, tesseract);

		if (drawingNumber == null) return alter;
		String[] ll = drawingNumber.split("-");
		try {
			List out = List.of(ll[4] + "-" + ll[5], ll[6]);
			return out;
		}catch(Exception e) {
			return alter;
		}
	}

	public String findHeaderAndValue(BufferedImage fullImage, String[] headerTexts, Tesseract tesseract) {
		tesseract.setVariable("tessedit_pageseg_mode", "7"); // word

		// 검색 시작 영역 제한
		int startX = (int) (fullImage.getWidth() * 0.74);
		int startY = (int) (fullImage.getHeight() * 0.962);

		try {
			// 헤더 찾기
			for (int y = startY; y < fullImage.getHeight() - SEARCH_HEIGHT; y += STEP_SIZE_HEIGHT) {
				for (int x = startX; x < fullImage.getWidth() - SEARCH_WIDTH; x += STEP_SIZE_WIDTH) {

					BufferedImage croppedImage = fullImage.getSubimage(x, y, SEARCH_WIDTH, SEARCH_HEIGHT);
					List<Word> words = tesseract.getWords(croppedImage, TessPageIteratorLevel.RIL_WORD);
					for (int i = 0; i < words.size(); i++) {
						Word firstWord = words.get(i);
						System.out.println("x:"+x+" y:"+y+"firstWord:"+firstWord);
						if (firstWord.getText().trim().equals(headerTexts[0])) {
							// 값 영역 추출 및 텍스트 인식
							Rectangle rect = firstWord.getBoundingBox();
							int valueMarginWidth = rect.width;   // SH.N 이 아래 칸 값 보다 작아서 더 크게
							BufferedImage valueImage = fullImage.getSubimage(
									x + rect.x + valueMarginWidth * 2, 
									y + rect.y - valueMarginWidth / 5, 
									rect.width * 13, 
									rect.height * 3);

							ImageIO.write(valueImage, "png", new File(debugPath+"/"+firstWord.getText()+"_result.png"));
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

        SpringApplication app = new SpringApplication(StandAloneApp.class);
        
        // 환경변수나 시스템 프로퍼티로 모드 결정
        String mode = System.getProperty("app.mode", "web");  // 기본값은 web
        if ("standalone".equals(mode)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(args);

        DTypeTitleExtractor dTypeTitleExtractor = context.getBean(DTypeTitleExtractor.class);

		int DPI = 300;
		Tesseract tesseract = new Tesseract();
		tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata"); // Tesseract 설치 경로
		tesseract.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._/ ");
		BufferedImage image = ImageIO.read(new File("D:/pgm_data/test2/921e42f2-ae00-485e-b9e4-05bceab6b594"));
		System.out.println(dTypeTitleExtractor.extract(List.of("a", "b"), image, tesseract, DPI));
	}
}