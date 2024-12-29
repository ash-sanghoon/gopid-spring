package com.infocz.util.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.infocz.util.conf.Config;

import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

@Component
public class CTypeTitleExtractor {

	@Autowired
	private Config config;

	private static final int BASE_HEADER_HEIGHT = 25;  // 300 DPI에서의 높이

	private int SEARCH_WIDTH; 
	private int STEP_SIZE_WIDTH;

	private int SEARCH_HEIGHT;
	private int STEP_SIZE_HEIGHT;

	public CTypeTitleExtractor() throws IOException {
	}

	// "DRAWING", "N°"
	//"SH.N°"
	public List<String> extract(List<String> alter, BufferedImage image) {
		Tesseract tesseract = config.getTesseract();
		SEARCH_WIDTH = (int)(BASE_HEADER_HEIGHT * 20); // 15자 검색범위
		STEP_SIZE_WIDTH = (int)(SEARCH_WIDTH / 3); // 검색 이동 간격
		SEARCH_HEIGHT = (int)(BASE_HEADER_HEIGHT * 2); // 2줄 범위
		STEP_SIZE_HEIGHT = (int)(SEARCH_HEIGHT / 3); // 검색 이동 간격
		String drawingNumber = findHeaderAndValue(image, new String[] { "DWG.", "NO." }, tesseract);

		if (drawingNumber == null) return alter;
		String[] ll = drawingNumber.split("-");
		
		return List.of(ll[0] + "-" + ll[1], ll[2]);
	}

	public String findHeaderAndValue(BufferedImage fullImage, String[] headerTexts, Tesseract tesseract) {
		tesseract.setVariable("tessedit_pageseg_mode", "7"); // word

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

							ImageIO.write(valueImage, "png", new File(config.getDebugFilePath()+"/"+firstWord.getText()+"_result.png"));
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

        SpringApplication app = new SpringApplication(CTypeTitleExtractor.class);
        
        // 환경변수나 시스템 프로퍼티로 모드 결정
        String mode = System.getProperty("app.mode", "web");  // 기본값은 web
        if ("standalone".equals(mode)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(args);

        CTypeTitleExtractor cTypeTitleExtractor = context.getBean(CTypeTitleExtractor.class);

		BufferedImage image = ImageIO.read(new File("D:/pgm_data/test2/921e42f2-ae00-485e-b9e4-05bceab6b594"));
		System.out.println(cTypeTitleExtractor.extract(List.of("a", "b"), image));
	}
}