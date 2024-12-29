package com.infocz.util.ocr;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;


@SpringBootApplication
@ComponentScan(basePackages = {"com.infocz"})
@Service
public class ZDEL_BboxTaggingService {
//    @Value("${com.infocz.parser.dpi}") // application 의 properties 의 변수
//    private String dpi;
//
//	private static final int BASE_DPI = 300;
//    private static final int BASE_HEADER_HEIGHT = 12;  // 300 DPI에서의 최소길이
//
//    public static void main(String[] args) throws IOException {
//        SpringApplication app = new SpringApplication(StandAloneApp.class);
//        
//        // 환경변수나 시스템 프로퍼티로 모드 결정
//        String mode = System.getProperty("app.mode", "web");  // 기본값은 web
//        if ("standalone".equals(mode)) {
//            app.setWebApplicationType(WebApplicationType.NONE);
//        }
//        app.setWebApplicationType(WebApplicationType.NONE);
//        ConfigurableApplicationContext context = app.run(args);
//
//        ZDEL_BboxTaggingService bboxTaggingService = context.getBean(ZDEL_BboxTaggingService.class);
//        
//        bboxTaggingService.tagBbox("");
//    }
//    
//	@Autowired
//	private CypherExecutor cypherExecutor;
//
////    if (width < minSize || height < minSize) {
////        int expandSize = (int)(Math.max(width, height) * 0.5);  // 더 큰 쪽의 50%만큼 확장
////        
////        // 확장된 좌표 계산 (이미지 경계 고려)
////        topX = Math.max(0, topX - expandSize);
////        topY = Math.max(0, topY - expandSize);
////        bottomX = Math.min(fullImage.getWidth(), bottomX + expandSize);
////        bottomY = Math.min(fullImage.getHeight(), bottomY + expandSize);
////        
////        // 새로운 크기 계산
////        width = bottomX - topX;
////        height = bottomY - topY;
////    }
//    @Transactional
//    public List<Map<String, Object>> tagBbox(String runId) throws IOException{
//    	
//    	runId = "ff2cf25a-3d36-41fc-a2b5-be931fbdee29";
//    	// DPI에 따른 최소 크기 계산
//    	int minSize = BASE_HEADER_HEIGHT * Integer.parseInt(dpi) / BASE_DPI; 
//    	
//    	// bbox 좌표 목록 가져오기
//		Session session = cypherExecutor.getSession();
//        Result result = session.run("""
//					MATCH (d:Drawing) - [:HAS_RUN] -> (r:Run {uuid:$uuid})
//					MATCH (r) - [:CONTAINS] -> (b:Bbox) 
//					MATCH (b) - [:BELONG_TO] -> (s:Symbol)
//					return b.top_x * d.width AS top_x,
//			        b.top_y * d.height AS top_y,
//			        b.bottom_x * d.width AS bottom_x,
//			        b.bottom_y * d.height AS bottom_y,
//			        b.id AS id,
//					s.name AS class
//        		""", Map.of("uuid", runId));
//        List<Map<String, Object>> bboxList = result.list(Record::asMap);
//        
//        // 이미지파일을 테서렉트로 ocr처리
//		Tesseract tesseract = new Tesseract();
//		tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata"); // Tesseract 설치 경로
//		tesseract.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._/ ");
//		BufferedImage fullImage = ImageIO.read(new File("D:/pgm_data/test2/e19a37ef-0d8b-4c62-b308-c37269ef7d34"));
//
//		for(Map<String, Object> bbox : bboxList) {
//			String bboxId = (String)bbox.get("id");
//			Integer topX = ((Double)bbox.get("top_x")).intValue();
//			Integer topY = ((Double)bbox.get("top_y")).intValue();
//			Integer bottomX = ((Double)bbox.get("bottom_x")).intValue();
//			Integer bottomY = ((Double)bbox.get("bottom_y")).intValue();
//			int width = bottomX - topX;
//			int height = bottomY - topY;
//			
//			String bboxClass = (String)bbox.get("class");
//			if(width * height < 170) { // arrow 등의 최소영역 심볼에 텍스트 인식 없음
//				continue;
//			}else if(bboxClass.contains("arrow")) {
//				continue;
//			}
//	    	if(topX > 1440 && topX < 1480){
//	    		if(topY > 1570 && topY < 1640) {
//	    			System.out.println("");
//	    		}
//	    	}
//
//			List<Word> foundWords = null;
//			if(bboxClass.contains("valve")) {
//			    foundWords = searchValveText(fullImage, bbox, minSize, tesseract);
//			} else {
//		    	if(topX > 2400 && topX < 2550){
//		    		if(topY > 3050 && topY < 3100) {
//		    			System.out.println("");
//		    		}
//		    	}
//			    BufferedImage croppedImage = fullImage.getSubimage(topX, topY, width, height);
//			    List<Word> words = tesseract.getWords(croppedImage, TessPageIteratorLevel.RIL_WORD);
//			    if (words == null || words.size() == 0) continue;
//			    ImageIO.write(croppedImage, "png", new File("test/"+bboxId+"_result.png"));
//			    foundWords = words;
//			}
//
//			if (foundWords == null) continue;
//			String tag = foundWords.stream()
//			    .filter(word -> word.getConfidence() > 50)  
//			    .map(word -> word.getText().trim())
//			    .collect(Collectors.joining(" "))
//			    .trim();
//			if (tag.isEmpty()) continue;
//
//			System.out.println("bboxId:"+bboxId+ " tag:"+tag +" x:"+topX+" y:"+topY+" width:"+width);
//			if(!"".equals(tag)) {
//				session.run("""
//						MATCH (r:Run {uuid:$uuid})  - [:CONTAINS] -> (b:Bbox {id:$bboxId})
//						SET   b.text = $tag
//						""", Map.of("uuid", runId, "tag", tag, "bboxId", bboxId));
//			}
//		}
//        return null;
//    }
//    
//    /**
//     * 벨브 주변의 텍스트를 탐색하는 함수
//     */
//    private List<Word> searchValveText(
//            BufferedImage fullImage,
//            Map<String, Object> bbox,
//            int minSize,
//            Tesseract tesseract) throws IOException {
//        
//        // bbox 정보 추출
//        String bboxId = (String)bbox.get("id");
//        Integer topX = ((Double)bbox.get("top_x")).intValue();
//        Integer topY = ((Double)bbox.get("top_y")).intValue();
//        Integer bottomX = ((Double)bbox.get("bottom_x")).intValue();
//        Integer bottomY = ((Double)bbox.get("bottom_y")).intValue();
//        int width = bottomX - topX;
//        int height = bottomY - topY;
//        List<Word> foundWords = null;
//        
//        double ratio = (double)width / height;
//        
//        if(ratio > 1.4) {
//            foundWords = searchVerticalText(fullImage, topX, topY, width, height, minSize, tesseract, bboxId);
//        }
//        else if(ratio < 1/1.4) {
//            foundWords = searchHorizontalText(fullImage, topX, topY, width, height, minSize, tesseract, bboxId);
//        }
//        else {
//            foundWords = searchSquareText(fullImage, topX, topY, width, height, minSize, tesseract, bboxId);
//        }
//        
//        return foundWords;
//    }
//    /**
//     * 좌표 정보를 포함하는 Word 래퍼 클래스
//     */
//    private static class WordWithLocation {
//        Word word;
//        Rectangle bounds;
//        
//        public WordWithLocation(Word word, Rectangle bounds) {
//            this.word = word;
//            this.bounds = bounds;
//        }
//    }
//
//    /**
//     * Words 병합 처리 함수
//     */
//    private List<Word> mergeOverlappingWords(List<WordWithLocation> wordsList) {
//        if (wordsList == null || wordsList.isEmpty()) return null;
//        
//        List<WordWithLocation> mergedList = new ArrayList<>();
//        Set<Integer> processedIndices = new HashSet<>();
//        
//        // 첫 번째 word 기준 처리
//        List<WordWithLocation> firstGroup = new ArrayList<>();
//        firstGroup.add(wordsList.get(0));
//        for (int i = 1; i < wordsList.size(); i++) {
//            if (hasOverlap(wordsList.get(0).bounds, wordsList.get(i).bounds)) {
//                firstGroup.add(wordsList.get(i));
//                processedIndices.add(i);
//            }
//        }
//        mergedList.add(selectBestWord(firstGroup));
//        
//        // 마지막 word 기준 처리
//        List<WordWithLocation> lastGroup = new ArrayList<>();
//        WordWithLocation lastWord = wordsList.get(wordsList.size() - 1);
//        lastGroup.add(lastWord);
//        for (int i = 0; i < wordsList.size() - 1; i++) {
//            if (!processedIndices.contains(i) && hasOverlap(lastWord.bounds, wordsList.get(i).bounds)) {
//                lastGroup.add(wordsList.get(i));
//                processedIndices.add(i);
//            }
//        }
//        if (lastGroup.size() > 1) {
//            mergedList.add(selectBestWord(lastGroup));
//        }
//        
//        // 나머지 words 처리
//        for (int i = 0; i < wordsList.size(); i++) {
//            if (!processedIndices.contains(i)) {
//                mergedList.add(wordsList.get(i));
//            }
//        }
//        
//        return mergedList.stream().map(wl -> wl.word).collect(Collectors.toList());
//    }
//
//    /**
//     * 두 영역이 겹치는지 확인
//     */
//    private boolean hasOverlap(Rectangle r1, Rectangle r2) {
//        Rectangle intersection = r1.intersection(r2);
//        return intersection.width * intersection.height > 0;
//    }
//
//    /**
//     * 가장 높은 confidence를 가진 word 선택
//     */
//    private WordWithLocation selectBestWord(List<WordWithLocation> words) {
//        return words.stream()
//            .max(Comparator.comparingDouble(w -> w.word.getConfidence()))
//            .orElse(words.get(0));  // 모두 0이면 첫 번째 선택
//    }
//
//    private List<Word> searchVerticalText(
//            BufferedImage fullImage,
//            int topX,
//            int topY,
//            int width,
//            int height,
//            int minSize,
//            Tesseract tesseract,
//            String bboxId) throws IOException {
//            
//        int expandY = (int)(height * 1.5);
//        int vertExpX = topX;
//        int vertExpY = Math.max(0, topY - expandY);
//        int vertExpWidth = width;
//        int vertExpHeight = Math.min(fullImage.getHeight() - vertExpY, height + (expandY * 2));
//        
//        BufferedImage verticalExpandedAreaImage = fullImage.getSubimage(vertExpX, vertExpY, vertExpWidth, vertExpHeight);
//        ImageIO.write(verticalExpandedAreaImage, "png", new File("test/"+bboxId+"_vertical_expanded.png"));
//        
//        List<WordWithLocation> foundWordsWithLocation = new ArrayList<>();
//        int foundCount = 0;
//        
//        for (int y = 0; y <= verticalExpandedAreaImage.getHeight() - (minSize * 2); y += minSize / 2) {
//            BufferedImage croppedImage = verticalExpandedAreaImage.getSubimage(
//                0, y, vertExpWidth, Math.min(minSize * 2, verticalExpandedAreaImage.getHeight() - y));
//            List<Word> words = tesseract.getWords(croppedImage, TessPageIteratorLevel.RIL_WORD);
//            
//            if (isValidWords(words, new Rectangle(0, topY - vertExpY, width, height))) {
//                String safeText = words.get(0).getText().replaceAll("[\\\\/:*?\"<>|]", "");
//                ImageIO.write(croppedImage, "png", new File("test/"+bboxId+"_"+safeText+"_vertical_result_"+(++foundCount)+".png"));
//                
//                // Word의 실제 위치 계산하여 저장
//                for (Word word : words) {
//                    Rectangle wordBounds = word.getBoundingBox();
//                    wordBounds.translate(0, y);  // y 좌표 조정
//                    foundWordsWithLocation.add(new WordWithLocation(word, wordBounds));
//                }
//            }
//        }
//        
//        return mergeOverlappingWords(foundWordsWithLocation);
//    }
//
//    private List<Word> searchHorizontalText(
//            BufferedImage fullImage,
//            int topX,
//            int topY,
//            int width,
//            int height,
//            int minSize,
//            Tesseract tesseract,
//            String bboxId) throws IOException {
//            
//        int expandX = (int)(width * 1.5);
//        int horizExpX = Math.max(0, topX - expandX);
//        int horizExpY = topY;
//        int horizExpWidth = Math.min(fullImage.getWidth() - horizExpX, width + (expandX * 2));
//        int horizExpHeight = height;
//        
//        BufferedImage horizontalExpandedAreaImage = fullImage.getSubimage(
//            horizExpX, horizExpY, horizExpWidth, horizExpHeight);
//        ImageIO.write(horizontalExpandedAreaImage, "png", new File("test/"+bboxId+"_horizontal_expanded.png"));
//        
//        List<WordWithLocation> foundWordsWithLocation = new ArrayList<>();
//        int foundCount = 0;
//        
//        for (int y = 0; y <= horizontalExpandedAreaImage.getHeight() - (minSize * 2); y += minSize / 2) {
//            BufferedImage croppedImage = horizontalExpandedAreaImage.getSubimage(
//                0, y, horizExpWidth, Math.min(minSize * 2, horizontalExpandedAreaImage.getHeight() - y));
//            List<Word> words = tesseract.getWords(croppedImage, TessPageIteratorLevel.RIL_WORD);
//            
//            if (isValidWords(words, new Rectangle(topX - horizExpX, 0, width, height))) {
//                String safeText = words.get(0).getText().replaceAll("[\\\\/:*?\"<>|]", "");
//                ImageIO.write(croppedImage, "png", new File("test/"+bboxId+"_"+safeText+"_horizontal_result_"+(++foundCount)+".png"));
//                
//                for (Word word : words) {
//                    Rectangle wordBounds = word.getBoundingBox();
//                    wordBounds.translate(0, y);
//                    foundWordsWithLocation.add(new WordWithLocation(word, wordBounds));
//                }
//            }
//        }
//        
//        return mergeOverlappingWords(foundWordsWithLocation);
//    }
//
//    private List<Word> searchSquareText(
//            BufferedImage fullImage,
//            int topX,
//            int topY,
//            int width,
//            int height,
//            int minSize,
//            Tesseract tesseract,
//            String bboxId) throws IOException {
//            
//        int expandSize = Math.max(width, height);
//        int squareExpX = Math.max(0, topX - expandSize);
//        int squareExpY = Math.max(0, topY - expandSize);
//        int squareExpWidth = Math.min(fullImage.getWidth() - squareExpX, width + (expandSize * 2));
//        int squareExpHeight = Math.min(fullImage.getHeight() - squareExpY, height + (expandSize * 2));
//        
//        BufferedImage expandedAreaImage = fullImage.getSubimage(
//            squareExpX, squareExpY, squareExpWidth, squareExpHeight);
//        ImageIO.write(expandedAreaImage, "png", new File("test/"+bboxId+"_square_expanded.png"));
//        
//        List<Word> words = tesseract.getWords(expandedAreaImage, TessPageIteratorLevel.RIL_WORD);
//        List<WordWithLocation> foundWordsWithLocation = new ArrayList<>();
//        int foundCount = 0;
//
//        if (words != null && words.size() > 0) {
//            Rectangle originalBbox = new Rectangle(topX - squareExpX, topY - squareExpY, width, height);
//            
//            for (Word word : words) {
//                Rectangle wordBox = word.getBoundingBox();
//                Rectangle intersection = originalBbox.intersection(wordBox);
//                if (intersection.width * intersection.height <= 0.1 * (wordBox.width * wordBox.height)) {
//                    foundWordsWithLocation.add(new WordWithLocation(word, wordBox));
//                    
//                    String safeText = word.getText().replaceAll("[\\\\/:*?\"<>|]", "");
//                    BufferedImage wordImage = expandedAreaImage.getSubimage(
//                        wordBox.x, wordBox.y, wordBox.width, wordBox.height);
//                    ImageIO.write(wordImage, "png", 
//                        new File("test/"+bboxId+"_"+safeText+"_square_result_"+(++foundCount)+".png"));
//                }
//            }
//        }
//        
//        return mergeOverlappingWords(foundWordsWithLocation);
//    }
//    /**
//     * 발견된 단어들이 유효한지 검사 (겹침 체크)
//     */
//    private boolean isValidWords(List<Word> words, Rectangle originalBbox) {
//        if (words == null || words.isEmpty()) {
//            return false;
//        }
//        
//        for (Word word : words) {
//            Rectangle wordBox = word.getBoundingBox();
//            Rectangle intersection = originalBbox.intersection(wordBox);
//            if (intersection.width * intersection.height > 0.1 * (wordBox.width * wordBox.height)) {
//                return false;
//            }
//        }
//        
//        return true;
//    }
}