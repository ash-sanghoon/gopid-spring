package com.infocz.util.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infocz.gopid.StandAloneApp;
import com.infocz.util.neo4j.CypherExecutor;

import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;


@SpringBootApplication
@ComponentScan(basePackages = {"com.infocz"})
@Service
public class BboxTaggingService {
    @Value("${com.infocz.parser.dpi}") // application 의 properties 의 변수
    private String dpi;

	private static final int BASE_DPI = 300;
    private static final int BASE_HEADER_HEIGHT = 12;  // 300 DPI에서의 최소길이

    public static void main(String[] args) throws IOException {
        SpringApplication app = new SpringApplication(StandAloneApp.class);
        
        // 환경변수나 시스템 프로퍼티로 모드 결정
        String mode = System.getProperty("app.mode", "web");  // 기본값은 web
        if ("standalone".equals(mode)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(args);

        BboxTaggingService bboxTaggingService = context.getBean(BboxTaggingService.class);
        
        bboxTaggingService.tagBbox("");
    }
	@Autowired
	private CypherExecutor cypherExecutor;

//    if (width < minSize || height < minSize) {
//        int expandSize = (int)(Math.max(width, height) * 0.5);  // 더 큰 쪽의 50%만큼 확장
//        
//        // 확장된 좌표 계산 (이미지 경계 고려)
//        topX = Math.max(0, topX - expandSize);
//        topY = Math.max(0, topY - expandSize);
//        bottomX = Math.min(fullImage.getWidth(), bottomX + expandSize);
//        bottomY = Math.min(fullImage.getHeight(), bottomY + expandSize);
//        
//        // 새로운 크기 계산
//        width = bottomX - topX;
//        height = bottomY - topY;
//    }
    @Transactional
    public List<Map<String, Object>> tagBbox(String runId) throws IOException{
    	
    	runId = "b3dc67ff-03c2-4dbf-a659-4ab345fafa85";
    	// DPI에 따른 최소 크기 계산
    	int minSize = BASE_HEADER_HEIGHT * Integer.parseInt(dpi) / BASE_DPI; 
    	
    	// bbox 좌표 목록 가져오기
		Session session = cypherExecutor.getSession();
        Result result = session.run("""
					MATCH (d:Drawing) - [:HAS_RUN] -> (r:Run {uuid:$uuid})
					MATCH (r) - [:CONTAINS] -> (b:Bbox) 
					MATCH (b) - [:BELONG_TO] -> (s:Symbol)
					return b.top_x * d.width AS top_x,
			        b.top_y * d.height AS top_y,
			        b.bottom_x * d.width AS bottom_x,
			        b.bottom_y * d.height AS bottom_y,
			        b.id AS id,
					s.name AS class
        		""", Map.of("uuid", runId));
        List<Map<String, Object>> bboxList = result.list(Record::asMap);
        
        // 이미지파일을 테서렉트로 ocr처리
		Tesseract tesseract = new Tesseract();
		tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata"); // Tesseract 설치 경로
		tesseract.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._/ ");
		BufferedImage fullImage = ImageIO.read(new File("D:/pgm_data/test2/4ea011cc-4f25-4c6e-9ac0-d67f0deae03c"));

		for(Map<String, Object> bbox : bboxList) {
			String bboxId = (String)bbox.get("id");
			Integer topX = ((Double)bbox.get("top_x")).intValue();
			Integer topY = ((Double)bbox.get("top_y")).intValue();
			Integer bottomX = ((Double)bbox.get("bottom_x")).intValue();
			Integer bottomY = ((Double)bbox.get("bottom_y")).intValue();
			int width = bottomX - topX;
			int height = bottomY - topY;
			
			String bboxClass = (String)bbox.get("class");
			if(width * height < 170) { // arrow 등의 최소영역 심볼에 텍스트 인식 없음
				continue;
			}else if(bboxClass.contains("arrow")) {
				continue;
			}

			List<Word> foundWords = null;
			if(bboxClass.contains("valve")) {
			    foundWords = searchValveText(fullImage, bbox, minSize, tesseract);
			} else {
			    BufferedImage croppedImage = fullImage.getSubimage(topX, topY, width, height);
			    List<Word> words = tesseract.getWords(croppedImage, TessPageIteratorLevel.RIL_WORD);
			    if (words == null || words.size() == 0) continue;
			    ImageIO.write(croppedImage, "png", new File("test/"+bboxId+"_result.png"));
			    foundWords = words;
			}

			if (foundWords == null) continue;
			String tag = foundWords.stream()
			    .filter(word -> word.getConfidence() > 50)  
			    .map(word -> word.getText().trim())
			    .collect(Collectors.joining(" "))
			    .trim();
			if (tag.isEmpty()) continue;

			System.out.println("bboxId:"+bboxId+ " tag:"+tag +" x:"+topX+" y:"+topY+" width:"+width);
			if(!"".equals(tag)) {
				session.run("""
						MATCH (r:Run {uuid:$uuid})  - [:CONTAINS] -> (b:Bbox {id:$bboxId})
						SET   b.tag = $tag
						""", Map.of("uuid", runId, "tag", tag, "bboxId", bboxId));
			}
		}
        return null;
    }
    
    /**
     * 벨브 주변의 텍스트를 탐색하는 함수
     */
    private List<Word> searchValveText(
            BufferedImage fullImage,
            Map<String, Object> bbox,
            int minSize,
            Tesseract tesseract) throws IOException {
        
        // bbox 정보 추출
        String bboxId = (String)bbox.get("id");
        Integer topX = ((Double)bbox.get("top_x")).intValue();
        Integer topY = ((Double)bbox.get("top_y")).intValue();
        Integer bottomX = ((Double)bbox.get("bottom_x")).intValue();
        Integer bottomY = ((Double)bbox.get("bottom_y")).intValue();
        int width = bottomX - topX;
        int height = bottomY - topY;
        List<Word> foundWords = null;
        
        double ratio = (double)width / height;
        
        if(ratio > 1.4) {
            foundWords = searchVerticalText(fullImage, topX, topY, width, height, minSize, tesseract, bboxId);
        }
        else if(ratio < 1/1.4) {
            foundWords = searchHorizontalText(fullImage, topX, topY, width, height, minSize, tesseract, bboxId);
        }
        else {
            foundWords = searchSquareText(fullImage, topX, topY, width, height, minSize, tesseract, bboxId);
        }
        
        return foundWords;
    }

    /**
     * 가로 방향 벨브의 위아래로 텍스트 탐색
     */
    private List<Word> searchVerticalText(
            BufferedImage fullImage,
            int topX,
            int topY,
            int width,
            int height,
            int minSize,
            Tesseract tesseract,
            String bboxId) throws IOException {
            
        int expandY = (int)(height * 1.5);
        int vertExpX = topX;
        int vertExpY = Math.max(0, topY - expandY);
        int vertExpWidth = width;
        int vertExpHeight = Math.min(fullImage.getHeight() - vertExpY, height + (expandY * 2));
        
        BufferedImage verticalExpandedAreaImage = fullImage.getSubimage(vertExpX, vertExpY, vertExpWidth, vertExpHeight);
        ImageIO.write(verticalExpandedAreaImage, "png", new File("test/"+bboxId+"_vertical_expanded.png"));
        
        for (int y = 0; y <= verticalExpandedAreaImage.getHeight() - (minSize * 2); y += minSize / 2) {
            BufferedImage croppedImage = verticalExpandedAreaImage.getSubimage(
                0, y, vertExpWidth, Math.min(minSize * 2, verticalExpandedAreaImage.getHeight() - y));
            List<Word> words = tesseract.getWords(croppedImage, TessPageIteratorLevel.RIL_WORD);
            
            if (isValidWords(words, new Rectangle(0, topY - vertExpY, width, height))) {
                ImageIO.write(croppedImage, "png", new File("test/"+bboxId+"_"+words.get(0).getText()+"_vertical_result.png"));
                return words;
            }
        }
        
        return null;
    }

    /**
     * 세로 방향 벨브의 좌우로 텍스트 탐색
     */
    private List<Word> searchHorizontalText(
            BufferedImage fullImage,
            int topX,
            int topY,
            int width,
            int height,
            int minSize,
            Tesseract tesseract,
            String bboxId) throws IOException {
            
        int expandX = (int)(width * 1.5);
        int horizExpX = Math.max(0, topX - expandX);
        int horizExpY = topY;
        int horizExpWidth = Math.min(fullImage.getWidth() - horizExpX, width + (expandX * 2));
        int horizExpHeight = height;
        
        BufferedImage horizontalExpandedAreaImage = fullImage.getSubimage(
            horizExpX, horizExpY, horizExpWidth, horizExpHeight);
        ImageIO.write(horizontalExpandedAreaImage, "png", new File("test/"+bboxId+"_expanded.png"));
        
        for (int x = 0; x <= horizontalExpandedAreaImage.getWidth() - minSize; x += minSize / 2) {
            BufferedImage croppedImage = horizontalExpandedAreaImage.getSubimage(
                x, 0, Math.min(minSize, horizontalExpandedAreaImage.getWidth() - x), horizExpHeight);
            List<Word> words = tesseract.getWords(croppedImage, TessPageIteratorLevel.RIL_WORD);
            
            if (isValidWords(words, new Rectangle(topX - horizExpX, 0, width, height))) {
                ImageIO.write(croppedImage, "png", new File("test/"+bboxId+"_"+words.get(0).getText()+"_result.png"));
                return words;
            }
        }
        
        return null;
    }

    /**
     * 정사각형 벨브 주변 전체 영역 텍스트 탐색
     */
    private List<Word> searchSquareText(
            BufferedImage fullImage,
            int topX,
            int topY,
            int width,
            int height,
            int minSize,
            Tesseract tesseract,
            String bboxId) throws IOException {
            
        int expandSize = Math.max(width, height);
        int squareExpX = Math.max(0, topX - expandSize);
        int squareExpY = Math.max(0, topY - expandSize);
        int squareExpWidth = Math.min(fullImage.getWidth() - squareExpX, width + (expandSize * 2));
        int squareExpHeight = Math.min(fullImage.getHeight() - squareExpY, height + (expandSize * 2));
        
        BufferedImage expandedAreaImage = fullImage.getSubimage(
            squareExpX, squareExpY, squareExpWidth, squareExpHeight);
        ImageIO.write(expandedAreaImage, "png", new File("test/"+bboxId+"_square_expanded.png"));
        
        List<Word> words = tesseract.getWords(expandedAreaImage, TessPageIteratorLevel.RIL_WORD);
        
        if (words != null && words.size() > 0) {
            Rectangle originalBbox = new Rectangle(topX - squareExpX, topY - squareExpY, width, height);
            List<Word> nonOverlappingWords = new ArrayList<>();
            
            for (Word word : words) {
                Rectangle wordBox = word.getBoundingBox();
                Rectangle intersection = originalBbox.intersection(wordBox);
                if (intersection.width * intersection.height <= 0.1 * (wordBox.width * wordBox.height)) {
                    nonOverlappingWords.add(word);
                }
            }
            
            if (!nonOverlappingWords.isEmpty()) {
                ImageIO.write(expandedAreaImage, "png", new File("test/"+bboxId+"_"+nonOverlappingWords.get(0).getText()+"_square_result.png"));
                return nonOverlappingWords;
            }
        }
        
        return null;
    }

    private boolean isValidWords(List<Word> words, Rectangle originalBbox) {
        if (words == null || words.isEmpty()) {
            return false;
        }
        
        for (Word word : words) {
            Rectangle wordBox = word.getBoundingBox();
            Rectangle intersection = originalBbox.intersection(wordBox);
            if (intersection.width * intersection.height > 0.1 * (wordBox.width * wordBox.height)) {
                return false;
            }
        }
        
        return true;
    }}