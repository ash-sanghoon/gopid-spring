package com.infocz.util.ocr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infocz.util.conf.Config;
import com.infocz.util.neo4j.CypherExecutor;

import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

@SpringBootApplication
@ComponentScan(basePackages = {"com.infocz"})
@Service
public class BboxTagService {
	
	@Autowired
	private Config config;
	
	private static final Logger log = LoggerFactory.getLogger(BboxTagService.class);

	public static void main(String[] args) throws IOException {
        SpringApplication app = new SpringApplication(BboxTagService.class);
        
        // 환경변수나 시스템 프로퍼티로 모드 결정
        String mode = System.getProperty("app.mode", "web");  // 기본값은 web
        if ("standalone".equals(mode)) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.setWebApplicationType(WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(args);

        BboxTagService bboxTagService = context.getBean(BboxTagService.class);
        
        bboxTagService.tagBbox("5b44ad57-495b-4731-bf14-e671bc7c4e39");
	}
	
	@Autowired
	private CypherExecutor cypherExecutor;
	
	private Map<String, Object> getRunInfo(String runId) {
		Session session = cypherExecutor.getSession();
		return session.run("""
				MATCH (r:Run {uuid:$uuid}) <- [:HAS_RUN] - (d:Drawing) <- [:HAS_DRAWING] - (p:Project),
				      (f:File {uuid:d.file_uuid})
				RETURN p.drawing_no_pattern AS drawing_no_pattern,
				       f.path AS file_path 
			""", Map.of("uuid", runId)).single().asMap();
	}
	
    @Transactional
    public List<BboxRect> tagBbox(String runId) throws IOException{

        log.info("start");
        Map<String, Object> runInfo = getRunInfo(runId);
        int minCharH = config.getMinCharH((String)runInfo.get("drawing_no_pattern"));
		Session session = cypherExecutor.getSession();
    	// bbox 좌표 목록 가져오기
		List<BboxRect> bboxList = getBboxRectList(runId);
        // 이미지파일을 테서렉트로 ocr처리
        Tesseract tesseract = config.getTesseract();
        // 원본이미지
		BufferedImage fullImage = prepare1200DPIImage(runInfo);
		
		for(BboxRect bbox:bboxList) {
	        if(log.isDebugEnabled()) {
	        	if(!"Z9".equals(bbox.text)) continue;
	        }
			List<BboxRect> neighborBbox = bbox.findRectWithinDistance(20 * minCharH, bboxList);
			List<Word> foundWords = findBboxTexts(fullImage, bbox, neighborBbox, tesseract);
			
			if (foundWords == null) continue;
			String text = foundWords.stream()
			    //.filter(word -> word.getConfidence() > 0)  
			    .map(word -> word.getText().trim())
			    .collect(Collectors.joining(" "))
			    .trim();
			if (text.isEmpty()) continue;

			session.run("""
					MATCH (r:Run {uuid:$uuid})  - [:CONTAINS] -> (b:Bbox {id:$bboxId})
					SET   b.text = $text
					""", Map.of("uuid", runId, "text", text, "bboxId", bbox.id));
		}
        log.info("end");

        return bboxList;
    }

    private String getDebugFileName(String bboxIndex, int areaIndex, int totalAreas, int windowIndex, int wordIndex) {
        return String.format(config.getDebugFilePath()+"%s_%dW%d_%03d_%03d.png",
            bboxIndex,
            areaIndex,
            totalAreas,
            windowIndex,
            wordIndex);
    }

    private void saveDebugImage(BufferedImage fullImage, Rectangle cropArea, String fileName) throws IOException {
        if (!log.isDebugEnabled()) return;
        
        BufferedImage croppedImage = fullImage.getSubimage(
            cropArea.x,
            cropArea.y,
            Math.min(cropArea.width, fullImage.getWidth() - cropArea.x),
            Math.min(cropArea.height, fullImage.getHeight() - cropArea.y)
        );
        ImageIO.write(croppedImage, "png", new File(fileName));
    }

    private List<Word> findBboxTexts(BufferedImage fullImage, BboxRect currentBbox, List<BboxRect> neighborhood, Tesseract tesseract) throws IOException {
        List<Rectangle> searchAreas = new ArrayList<>();
        List<Word> allWords = new ArrayList<>();
        
        if(log.isDebugEnabled()) {
            log.debug("bbox:" + currentBbox);
            // bbox 원본 이미지 저장
            Rectangle bboxRect = new Rectangle(
                currentBbox.left, currentBbox.top,
                currentBbox.right - currentBbox.left,
                currentBbox.bottom - currentBbox.top
            );
            saveDebugImage(fullImage, bboxRect, getDebugFileName(currentBbox.id, 0, 0, 0, 0));
        }

        // bbox 중심점
        int centerX = (currentBbox.left + currentBbox.right) / 2;
        int centerY = (currentBbox.top + currentBbox.bottom) / 2;
        
        // 내부/외부 영역 생성 코드...

        // 1. bbox 내부 탐색이 활성화된 경우에만 내부 영역 추가
        if (currentBbox.innerSearch) {
            // 1-0. bbox 내부 (외곽 제외)
            int innerMarginX = (int)((currentBbox.right - currentBbox.left) * (currentBbox.innerRatio / 10.0));
            int innerMarginY = (int)((currentBbox.bottom - currentBbox.top) * (currentBbox.innerRatio / 10.0));
            searchAreas.add(new Rectangle(
                currentBbox.left + innerMarginX,
                currentBbox.top + innerMarginY,
                currentBbox.right - currentBbox.left - (2 * innerMarginX),
                currentBbox.bottom - currentBbox.top - (2 * innerMarginY)
            ));
        }
        
        // 외부 영역은 outerSearch가 true이고 outerRatio가 0보다 큰 경우에만 추가
        if (currentBbox.outerSearch) {
            // 모든 방향에서 공통으로 사용할 값들
            int searchHeight = (int)(currentBbox.outerLines * 2 * currentBbox.minCharH);  // 줄 수로 높이 결정
            int outerMarginX = (int)((currentBbox.right - currentBbox.left) * (currentBbox.outerRatio / 10.0));
            int outerMarginY = (int)((currentBbox.bottom - currentBbox.top) * (currentBbox.outerRatio / 10.0));
            int bboxWidth = currentBbox.right - currentBbox.left;  // bbox 자체의 너비
            int bboxHeight = currentBbox.bottom - currentBbox.top;  // bbox 자체의 높이
            
            // 1-1. 오른쪽 영역
            int rightSearchWidth = (int)(currentBbox.outerLeftRightChars * currentBbox.minCharH);
            int rightSearchHeight = Math.max(searchHeight, bboxHeight);  // bbox 높이와 비교하여 큰 값 사용
            searchAreas.add(new Rectangle(
                currentBbox.right - outerMarginX,
                centerY - (rightSearchHeight / 2),
                rightSearchWidth,
                rightSearchHeight
            ));
            
            // 1-2. 왼쪽 영역
            int leftSearchWidth = (int)(currentBbox.outerLeftRightChars * currentBbox.minCharH);
            int leftSearchHeight = Math.max(searchHeight, bboxHeight);  // bbox 높이와 비교하여 큰 값 사용
            searchAreas.add(new Rectangle(
                currentBbox.left - leftSearchWidth,
                centerY - (leftSearchHeight / 2),
                leftSearchWidth,
                leftSearchHeight
            ));
            
            // 1-3. 위쪽 영역
            int topSearchWidth = Math.max((int)(currentBbox.outerTopBottomChars * currentBbox.minCharH), bboxWidth);
            searchAreas.add(new Rectangle(
                centerX - (topSearchWidth / 2),
                currentBbox.top - searchHeight,  // outerMarginY 제거
                topSearchWidth,
                searchHeight + outerMarginY      // 높이에만 outerMarginY 포함 (bbox와 겹치는 부분)
            ));

            // 1-4. 아래쪽 영역
            int bottomSearchWidth = Math.max((int)(currentBbox.outerTopBottomChars * currentBbox.minCharH), bboxWidth);
            searchAreas.add(new Rectangle(
                centerX - (bottomSearchWidth / 2),
                currentBbox.bottom,
                bottomSearchWidth,
                searchHeight + outerMarginY      // 높이에만 outerMarginY 포함 (bbox와 겹치는 부분)
            ));
        }

        // searchAreas 생성 완료 후 전체 검색 영역 이미지 저장
        if(log.isDebugEnabled() && !searchAreas.isEmpty()) {
            // 모든 searchAreas를 포함하는 최소/최대 좌표 계산
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            
            for (Rectangle area : searchAreas) {
                minX = Math.min(minX, area.x);
                minY = Math.min(minY, area.y);
                maxX = Math.max(maxX, area.x + area.width);
                maxY = Math.max(maxY, area.y + area.height);
            }
            
            // 확장된 영역 이미지 저장
            Rectangle expandRect = new Rectangle(
                minX,
                minY,
                maxX - minX,
                maxY - minY
            );
            saveDebugImage(fullImage, expandRect, getDebugFileName(currentBbox.id, 0, 0, 0, 1));
        }

        // 각 영역별 텍스트 탐색
        int areaIndex = 0;
        for (Rectangle area : searchAreas) {
            areaIndex++;
            try {
                if(log.isDebugEnabled()) {
                    // 각 탐색 영역의 전체 이미지 저장
                    saveDebugImage(fullImage, area, getDebugFileName(currentBbox.id, areaIndex, searchAreas.size(), 0, 0));
                }
                
                // Tesseract PSM 모드 설정
                tesseract.setPageSegMode(currentBbox.psmMode);
                
                // 슬라이딩 윈도우 탐색
                int halfLineHeight = (int)(currentBbox.minCharH * currentBbox.windowSlideHeight);
                int windowHeight = (int)(currentBbox.minCharH * currentBbox.windowHeight);
                int maxSteps = (int)Math.ceil((double)(area.height - windowHeight) / halfLineHeight) + 1;
                
                int windowIndex = 0;
                for (int i = 0; i < maxSteps; i++) {
                    windowIndex++;
                    int subY = area.y + (i * halfLineHeight);
                    int subHeight = Math.min(windowHeight, area.y + area.height - subY);
                    
                    Rectangle windowRect = new Rectangle(
                        area.x,
                        subY,
                        Math.min(area.width, fullImage.getWidth() - area.x),
                        Math.min(subHeight, fullImage.getHeight() - subY)
                    );
                    
                    if(log.isDebugEnabled()) {
                        saveDebugImage(fullImage, windowRect, 
                            getDebugFileName(currentBbox.id, areaIndex, searchAreas.size(), windowIndex, 0));
                    }

                    // OCR 수행
                    BufferedImage croppedWindow = fullImage.getSubimage(
                        windowRect.x, windowRect.y, windowRect.width, windowRect.height);
                    List<Word> words = tesseract.getWords(croppedWindow, TessPageIteratorLevel.RIL_WORD);
                    
                    if(log.isDebugEnabled()) {
                        log.debug("bbox:" + currentBbox + " window:" + windowIndex + " words:" + words);
                    }
                    
                    // 좌표 보정 및 크기 필터링
                    int wordIndex = 0;
                    for (Word word : words) {
                        Rectangle bbox = word.getBoundingBox();
                        
                        // 크기 필터링
                        if (bbox.height < currentBbox.minCharH * currentBbox.acceptMinSizeRatio ||
                            bbox.height > currentBbox.minCharH * currentBbox.acceptMaxSizeRatio) {
                            continue;
                        }
                        
                        wordIndex++;
                        if(log.isDebugEnabled()) {
                            Rectangle wordRect = new Rectangle(
                                windowRect.x + bbox.x,
                                windowRect.y + bbox.y,
                                bbox.width,
                                bbox.height
                            );
                            saveDebugImage(fullImage, wordRect,
                                getDebugFileName(currentBbox.id, areaIndex, searchAreas.size(), windowIndex, wordIndex));
                            log.debug("bbox:" + currentBbox + " window:" + windowIndex + 
                                    " word:" + wordIndex + " text:" + word.getText());
                        }
                        
                        bbox.x += area.x;
                        bbox.y += subY;
                        allWords.add(word);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing search area: " + e.getMessage(), e);
                continue;
            }
        }


        // 3-1. 현재 bbox와의 거리가 다른 bbox들보다 가까운 word만 필터링
        List<Word> nearestWords = allWords.stream()
            .filter(word -> {
                Point2D wordCenter = new Point2D.Double(
                    word.getBoundingBox().getCenterX(),
                    word.getBoundingBox().getCenterY()
                );
                double distToCurrent = currentBbox.getMinDistance(wordCenter);
                
                return neighborhood.stream()
                    .allMatch(otherBbox -> 
                        otherBbox.id.equals(currentBbox.id) || 
                        distToCurrent <= otherBbox.getMinDistance(wordCenter)
                    );
            })
            .collect(Collectors.toList());
        
        // 3-2. 영역이 50% 이상 겹치는 word들 중 confidence가 높은 것만 선택
        List<Word> filteredWords = new ArrayList<>(nearestWords);
        for (int i = 0; i < filteredWords.size(); i++) {
            Word word1 = filteredWords.get(i);
            if (word1 == null) continue;  // 이미 제거된 word
            
            Rectangle bbox1 = word1.getBoundingBox();
            double area1 = bbox1.width * bbox1.height;
            
            for (int j = i + 1; j < filteredWords.size(); j++) {
                Word word2 = filteredWords.get(j);
                if (word2 == null) continue;  // 이미 제거된 word
                
                Rectangle bbox2 = word2.getBoundingBox();
                double area2 = bbox2.width * bbox2.height;
                
                // 두 영역의 교집합 계산
                Rectangle intersection = bbox1.intersection(bbox2);
                if (!intersection.isEmpty()) {
                    double intersectionArea = intersection.width * intersection.height;
                    
                    // 각 영역 대비 교집합 비율 계산
                    double ratio1 = intersectionArea / area1;
                    double ratio2 = intersectionArea / area2;
                    
                    // 둘 중 하나라도 50% 이상 겹치면 confidence가 낮은 것을 제거
                    if (ratio1 >= 0.5 || ratio2 >= 0.5) {
                        if (word1.getConfidence() >= word2.getConfidence()) {
                            filteredWords.set(j, null);  // word2 제거
                        } else {
                            filteredWords.set(i, null);  // word1 제거
                            break;  // word1이 제거되었으므로 더 이상 비교할 필요 없음
                        }
                    }
                }
            }
        }
        
        return filteredWords.stream()
            .filter(word -> word != null)
            .collect(Collectors.toList());
    }
    
    private BufferedImage prepare1200DPIImage(Map<String, Object> runInfo) throws IOException {
    	
    	String filePath = (String)runInfo.get("file_path"); 
    	String drawing_no_pattern = (String)runInfo.get("drawing_no_pattern");
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
	        PDFRenderer pdfRenderer = new PDFRenderer(document);
	        
	    	BufferedImage image = pdfRenderer.renderImageWithDPI(0, Config.USE_DPI, ImageType.BINARY);
	
	        Graphics2D g2d = image.createGraphics();
	        g2d.setColor(Color.white);
	        g2d.fill(config.getLegendArea(drawing_no_pattern));
	
	        // Graphics2D 객체 해제
	        g2d.dispose();
	        BufferedImage glyphImage = ImageUtil.preprocessOnlyGlyph(image, config.getMaxCharH(drawing_no_pattern));
	    	ImageIO.write(glyphImage, "PNG", new File(config.getDebugFilePath() + drawing_no_pattern+"_1200_001.png"));
	    	return glyphImage;
        }
    }
    
    private List<BboxRect> getBboxRectList(String runId) {
    	// bbox 좌표 목록 가져오기
		Session session = cypherExecutor.getSession();
        List<BboxRect> list = new ArrayList<BboxRect>();
        Result result = session.run("""
					MATCH (d:Drawing) - [:HAS_RUN] -> (r:Run {uuid:$uuid})
					MATCH (r) - [:CONTAINS] -> (b:Bbox) 
					MATCH (b) - [:BELONG_TO] -> (s:Symbol)
					return b.top_x * d.width * 4 AS top_x,
			        b.top_y * d.height  * 4 AS top_y,
			        b.bottom_x * d.width  * 4 AS bottom_x,
			        b.bottom_y * d.height  * 4 AS bottom_y,
			        b.id AS id,
			        b.text as text,
					s.name AS label
        		""", Map.of("uuid", runId));
        for(Map<String, Object> bbox: result.list(Record::asMap)) {
        	String id = (String)bbox.get("id");
        	String label = (String)bbox.get("label");
        	String text = (String)bbox.get("text");
        	Integer topX = ((Double)bbox.get("top_x")).intValue();     // left
        	Integer topY = ((Double)bbox.get("top_y")).intValue();     // top
        	Integer bottomX = ((Double)bbox.get("bottom_x")).intValue();   // right 
        	Integer bottomY = ((Double)bbox.get("bottom_y")).intValue();   // bottom
        	BboxRect bboxRect = new BboxRect(id, label, text, topX, topY, bottomX, bottomY);
        	list.add(bboxRect);
        }
        return list;
    }
    
    class BboxRect {
    	public final String id, label, text;
    	public final int left, top, right, bottom;
    	public final Point2D center;
    	
    	public double windowSlideHeight = 0.4; //halfLineHeight
    	public double windowHeight = 1.5; // windowHeight
    	
    	public String tessWhiteList = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._/ \"";
    	public double innerRatio = 0; // bbox의 외곽선 경계 늘이기 줄이기
    	public double outerRatio = 0;

    	public double outerTopBottomChars = 5; // 위아래 영역 좌우 글자영역 수
    	public double outerLeftRightChars = 2.5; // 좌우 영역의 좌우 글자영역 수
    	public double outerLines = 1.5;   // 외부 검색 영역의 줄 수 

    	public boolean innerSearch = true; // 텍스트검색여부
    	public boolean outerSearch = false;
    	public double acceptMinSizeRatio = 0.9;
    	public double acceptMaxSizeRatio = 1.1;
    	
    	//public int psmMode = 7; // LINE
    	public int psmMode = 8; // WORD
    	
    	public double minCharH = getMinCharH(13); // 영역별 폰트 크기
    	
    	public static int USE_DPI = 1200;
    	public static int DEFAULT_DPI = 300;
    	
    	public static double getMinCharH(double areaCharH) {
    		return areaCharH * USE_DPI / DEFAULT_DPI; 
    	}
        
        public String toString() {
        	return ""+label+", "+left+", "+top+", "+right+", "+bottom;
        }
        
        public BboxRect(String id, String label, String text, int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.text = text;
            this.right = right;
            this.bottom = bottom;
            this.label = label;
            this.id = id;
            this.center = new Point2D.Double(
                    (left + right) / 2,
                    (top + bottom) / 2
                );
            if(label.contains("valve")) {
            	outerRatio = 3.5;
            	innerSearch = false;
            	outerSearch = true;
            	outerTopBottomChars = 6;
            	outerLeftRightChars = 6;
            }else if(label.contains("arrow")) {
            	innerSearch = false;
            	outerSearch = false;
            }else if(label.contains("reducer")) {
            	innerSearch = false;
            	outerSearch = true;
            	outerLeftRightChars = 10;
            }else if(label.contains("drum")) {
            	innerSearch = true;
            	outerSearch = false;
            	acceptMaxSizeRatio = 3;
            	minCharH = getMinCharH(26);
            	innerRatio = 0.5;
            }else if(label.contains("from_to")) {
            	innerRatio = 0.1; // bbox의 외곽선 경계 늘이기 줄이기
            	innerSearch = true;
            	outerSearch = false;
            }else if(label.contains("scarecrow")) {
            	innerSearch = true;
            	outerSearch = true;
            	outerRatio = 3.5;
            }else {
            	psmMode = 7;
            }
        }
        
        public double getMinDistance(Point2D point) {
            if (containsPoint(point)) return 0;
            
            double dx = 0;
            if (point.getX() < left) dx = left - point.getX();
            else if (point.getX() > right) dx = point.getX() - right;
            
            double dy = 0;
            if (point.getY() < top) dy = top - point.getY();
            else if (point.getY() > bottom) dy = point.getY() - bottom;
            
            return (dx > 0 && dy > 0) ? Math.sqrt(dx*dx + dy*dy) : Math.max(dx, dy);
        }
        
        public boolean containsPoint(Point2D point) {
            return point.getX() >= left && point.getX() <= right && 
                   point.getY() >= top && point.getY() <= bottom;
        }
        
        // 단순 순차 탐색 방식
        public List<BboxRect> findRectWithinDistance(double maxDistance, List<BboxRect> rectangles) {
            return rectangles.stream()
                .filter(rect -> rect.getMinDistance(center) <= maxDistance)
                .collect(Collectors.toList());
        }
    }
}
