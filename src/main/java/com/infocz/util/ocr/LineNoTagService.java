package com.infocz.util.ocr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.infocz.util.ocr.BboxTagService.BboxRect;
import com.infocz.util.ocr.LinenoFinder.LinenoResult;

import net.sourceforge.tess4j.Tesseract;

@SpringBootApplication
@ComponentScan(basePackages = {"com.infocz"})
@Service
public class LineNoTagService {
	
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

        LineNoTagService lineNoTagService = context.getBean(LineNoTagService.class);
        
        //  A -001
        //lineNoTagService.lineno("e3290c64-4323-42c1-bab7-c061d94343da");
        
        // B-002 
        // lineNoTagService.lineno("8e4a1105-b066-4246-a086-79d95698844a");
        
        // C-006 
        lineNoTagService.lineno("b5fe036f-a76a-4bdf-8281-e85b6f627d11");
        
	}
	
	@Autowired
	private CypherExecutor cypherExecutor;
	
	private Map<String, Object> getRunInfo(String runId) {
		Session session = cypherExecutor.getSession();
		return session.run("""
				MATCH (r:Run {uuid:$uuid}) <- [:HAS_RUN] - (d:Drawing) <- [:HAS_DRAWING] - (p:Project),
				      (f:File {uuid:d.file_uuid})
				RETURN p.drawing_no_pattern AS drawing_no_pattern,
				       f.path AS file_path ,
				       d.file_page_no AS file_page_no
			""", Map.of("uuid", runId)).single().asMap();
	}
	
	@Transactional
	public List<BboxRect> lineno(String runId) throws IOException {
	    log.info("start");
	    Map<String, Object> runInfo = getRunInfo(runId);
	    Session session = cypherExecutor.getSession();

	    // 이미지파일을 테서렉트로 ocr처리
	    Tesseract tesseract = config.getTesseract();
	    // 원본이미지
	    BufferedImage fullImage = prepare1200DPIImage(runInfo);
    	ImageIO.write(fullImage, "PNG", new File(config.getDebugFilePath() + "reserve/" + ((String)runInfo.get("drawing_no_pattern"))+"_1200_001.png"));

	    LinenoFinder linenoFinder = buildLinenoFinder(runInfo);
	    List<LinenoResult> linenoResultListHorizon = linenoFinder.findAllLineno(fullImage, tesseract);

	    List<EdgeInfo> edgeList = getEdgeList(runId, (String)runInfo.get("drawing_no_pattern"));

	    log.info("edgeList:"+edgeList.size());
	    // 수평 텍스트 처리
	    processLinenoResults(linenoResultListHorizon, edgeList, runId, session, false, fullImage);

	    // 이미지 시계방향 90도 회전 (OpenCV 사용)
	    BufferedImage fullImageVertical = ImageUtil.rotateImage(fullImage, 90);
	    
	    // 회전된 이미지로 OCR 수행
	    List<LinenoResult> linenoResultListVertical = linenoFinder.findAllLineno(fullImageVertical, tesseract);
	    log.info("edgeList:"+edgeList.size());

	    // 수직 텍스트 처리
	    processLinenoResults(linenoResultListVertical, edgeList, runId, session, true, fullImage);
	    
	    log.info("end");
	    return null;
	}

	/**
	 * LinenoResult 리스트를 처리하여 edge를 업데이트합니다.
	 */
	private void processLinenoResults(List<LinenoResult> results, List<EdgeInfo> edgeList, 
	                                String runId, Session session, boolean needsCoordinateTransform,
	                                BufferedImage originalImage) {
	    for(LinenoResult result : results) {
	        Rectangle bbox = result.getBoundingBox();
	        int centerX, centerY;
	        
	        if (needsCoordinateTransform) {
	            // 회전된 좌표를 원본 좌표계로 변환
	            Rectangle originalBbox = transformRotatedCoordinates(bbox, originalImage);
	            centerX = originalBbox.x + originalBbox.width / 2;
	            centerY = originalBbox.y + originalBbox.height / 2;
	        } else {
	            centerX = bbox.x + bbox.width / 2;
	            centerY = bbox.y + bbox.height / 2;
	        }

	        EdgeInfo closestEdge = findClosestEdge(centerX, centerY, edgeList);

	        if (closestEdge != null) {
	            session.run("""
	                MATCH (r:Run {uuid:$uuid}) - [:CONTAINS] -> (bbox:Bbox)
	                MATCH (bbox) - [c:CONNECTS_TO {id:$edgeId}] - ()
	                SET c.line_no = $lineno
	                """,
	                Map.of("uuid", runId, "edgeId", closestEdge.id, "lineno", result.getText())
	            );

	            log.debug("Updated edge {} with line_no: {}", closestEdge.id, result.getText());
	        } else {
	            log.debug("There is no edge");
	        }
	    }
	}

	/**
	 * 90도 회전된 이미지에서의 좌표를 원본 좌표계로 변환합니다.
	 */
	private Rectangle transformRotatedCoordinates(Rectangle rect, BufferedImage originalImage) {
	    // 시계방향 90도 회전된 이미지에서의 좌표를 원본 좌표계로 변환
	    // (x, y) -> (y, H-x)의 역변환: (x, y) -> (H-y, x)
	    return new Rectangle(
	        originalImage.getHeight() - (rect.y + rect.height),  // 새로운 x
	        rect.x,                                             // 새로운 y
	        rect.height,                                        // width와 height 교환
	        rect.width
	    );
	}

	/**
	 * 주어진 좌표에서 가장 가까운 edge를 찾습니다.
	 */
	private EdgeInfo findClosestEdge(int x, int y, List<EdgeInfo> edges) {
	    if (edges == null || edges.isEmpty()) {
	        return null;
	    }
	    
	    EdgeInfo closestEdge = null;
	    double minDistance = Double.MAX_VALUE;
	    
	    for (EdgeInfo edge : edges) {
	        // edge의 시작점과 끝점으로부터의 최단거리 계산
	        double distance = calculatePointToLineDistance(
	            x, y,
	            edge.source_x, edge.source_y,
	            edge.target_x, edge.target_y
	        );
	        
	        if (distance < minDistance) {
	            minDistance = distance;
	            closestEdge = edge;
	        }
	    }
	    
	    return closestEdge;
	}

	/**
	 * 점과 선분 사이의 최단 거리를 계산합니다.
	 */
	private double calculatePointToLineDistance(
	    int px, int py,
	    int x1, int y1,
	    int x2, int y2
	) {
	    // 선분의 길이 제곱
	    double lineLength2 = Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2);
	    
	    if (lineLength2 == 0) {
	        // 시작점과 끝점이 같은 경우, 점과의 직선거리 반환
	        return Math.sqrt(Math.pow(px - x1, 2) + Math.pow(py - y1, 2));
	    }
	    
	    // 선분 위의 최단거리 점의 위치를 나타내는 비율 t
	    double t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / lineLength2;
	    
	    if (t < 0) {
	        // 최단거리 점이 선분의 시작점 앞에 있는 경우
	        return Math.sqrt(Math.pow(px - x1, 2) + Math.pow(py - y1, 2));
	    }
	    if (t > 1) {
	        // 최단거리 점이 선분의 끝점 뒤에 있는 경우
	        return Math.sqrt(Math.pow(px - x2, 2) + Math.pow(py - y2, 2));
	    }
	    
	    // 선분 위의 최단거리 점 계산
	    double nearestX = x1 + t * (x2 - x1);
	    double nearestY = y1 + t * (y2 - y1);
	    
	    // 점과 최단거리 점 사이의 거리 반환
	    return Math.sqrt(Math.pow(px - nearestX, 2) + Math.pow(py - nearestY, 2));
	}
    
    private BufferedImage prepare1200DPIImage(Map<String, Object> runInfo) throws IOException {
    	
    	String filePath = (String)runInfo.get("file_path"); 
    	String drawing_no_pattern = (String)runInfo.get("drawing_no_pattern");
    	int pageNo = Integer.parseInt((String)runInfo.get("file_page_no")) - 1;
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
	        PDFRenderer pdfRenderer = new PDFRenderer(document);
	        
	    	BufferedImage image = pdfRenderer.renderImageWithDPI(pageNo, config.getUseDPI(drawing_no_pattern), ImageType.BINARY);
	
	        Graphics2D g2d = image.createGraphics();
	        g2d.setColor(Color.white);
	        g2d.fill(config.getLegendArea(drawing_no_pattern));
	
	        // Graphics2D 객체 해제
	        g2d.dispose();
	        BufferedImage glyphImage = ImageUtil.preprocessOnlyGlyph(image, config.getMaxCharH(drawing_no_pattern));
	    	return glyphImage;
        }
    }

    private List<EdgeInfo> getEdgeList(String runId, String drawingPattern) {

		Session session = cypherExecutor.getSession();
        List<EdgeInfo> list = new ArrayList<EdgeInfo>();
        Result result = session.run("""
				MATCH (d:Drawing) - [:HAS_RUN] -> (r:Run {uuid:$uuid})
				MATCH (r:Run)-[:CONTAINS]->(bbox:Bbox)
				WITH bbox, d
				OPTIONAL MATCH (bbox)-[:CONNECTS_TO]-(connected)
				WHERE connected:Bbox OR connected:Joint  
				WITH bbox, d, COLLECT(connected) + bbox AS all_nodes
				UNWIND all_nodes AS n1
				UNWIND all_nodes AS n2
				MATCH (n1)-[r:CONNECTS_TO]-(n2)
				WHERE COALESCE(r.state, '') <> 'del'
				AND COALESCE(n1.state, '') <> 'del'
				AND COALESCE(n2.state, '') <> 'del'
				WITH n1, n2, r, d,
				    CASE 
				        WHEN 'Joint' IN LABELS(n1) THEN n1.x
				        ELSE (n1.top_x + n1.bottom_x)/2
				    END * d.width AS source_x,
				    CASE 
				        WHEN 'Joint' IN LABELS(n1) THEN n1.y
				        ELSE (n1.top_y + n1.bottom_y)/2
				    END * d.height AS source_y,
				    CASE 
				        WHEN 'Joint' IN LABELS(n2) THEN n2.x
				        ELSE (n2.top_x + n2.bottom_x)/2
				    END * d.width AS target_x,
				    CASE 
				        WHEN 'Joint' IN LABELS(n2) THEN n2.y
				        ELSE (n2.top_y + n2.bottom_y)/2
				    END * d.height AS target_y
				RETURN DISTINCT 
				    n1.id AS source,
				    n2.id AS target,
				    r.id as id,
				    r.line_no AS line_no,
				    source_x,
				    source_y,
				    target_x,
				    target_y
        		""", Map.of("uuid", runId));
        for(Map<String, Object> bbox: result.list(Record::asMap)) {
        	String id = (String)bbox.get("id");
        	String line_no = (String)bbox.get("line_no");
        	Integer source_x = ((Double)bbox.get("source_x")).intValue() * config.getDPIRatio(drawingPattern);     // left
        	Integer source_y = ((Double)bbox.get("source_y")).intValue() * config.getDPIRatio(drawingPattern);     // top
        	Integer target_x = ((Double)bbox.get("target_x")).intValue() * config.getDPIRatio(drawingPattern);   // right 
        	Integer target_y = ((Double)bbox.get("target_y")).intValue() * config.getDPIRatio(drawingPattern);   // bottom
        	EdgeInfo edgeInfo = new EdgeInfo(id, line_no, source_x, source_y, target_x, target_y);
        	list.add(edgeInfo);
        }
        return list;
    	

    }
    
    class EdgeInfo {
    	public final String id, line_no;
    	public int source_x, source_y, target_x, target_y;
        
        public EdgeInfo(String id, String line_no, int source_x, int source_y, int target_x, int target_y) {
			this.id = id;
			this.line_no = line_no;
			this.source_x = source_x;
			this.source_y = source_y;
			this.target_x = target_x;
			this.target_y = target_y;
        }
    }

    private LinenoFinder buildLinenoFinder(Map<String, Object> runInfo) {
    	String drawingTypeCode = (String)runInfo.get("drawing_no_pattern"); // 도면 유형 코드
    	
        double rectMargin = 0.05;           // 도면이미지의 탐색대상 외곽마진 비율
        int charHeight = config.getMinCharH(drawingTypeCode);              // lineno 문자의 크기 (pixel)
        int linenoChars = 15;             // lineno 구성 문자 갯수
        int charWidth = (int)(charHeight * 0.8);               // lineno 문자의 좌우길이 (pixel)
        double linenoMarginRatioY = 0.4;   // 탐색 window Y축 마진 비율
        double slidingRatioY = 0.2;        // Y축 탐색Window 이동비율
        double acceptMinSizeRatio = 0.8;   // 인정 최소크기 비율
        double acceptMaxSizeRatio = 1.4;   // 인정 최대크기 비율
        double linenoLengthMinRatio = 0.8;
        double linenoLengthMaxRatio = 1.5;
        
        if("A".equals(drawingTypeCode)) {
        	rectMargin = 0.05;
        }
        if("B".equals(drawingTypeCode)) {
        	rectMargin = 0.01;
        	linenoChars = 12;
        	charWidth = (int)(charHeight * 0.7);
            acceptMinSizeRatio = 0.7;   // 인정 최소크기 비율
            acceptMaxSizeRatio = 1.3;   // 인정 최대크기 비율
        }
        if("C".equals(drawingTypeCode)) {
        	rectMargin = 0.01;
        	linenoChars = 25;
        	charWidth = (int)(charHeight * 0.7);
            acceptMinSizeRatio = 0.7;   // 인정 최소크기 비율
            acceptMaxSizeRatio = 1.3;   // 인정 최대크기 비율
            linenoLengthMinRatio = 0.4;
            linenoLengthMaxRatio = 1.7;
        }
        
        LinenoFinder finder = new LinenoFinder(rectMargin, charHeight, charWidth, linenoChars,
                linenoMarginRatioY, 
                drawingTypeCode, slidingRatioY,
                acceptMinSizeRatio, acceptMaxSizeRatio,
                linenoLengthMinRatio, linenoLengthMaxRatio
        		);
        return finder;
    }
    
    private String getDebugFileName(String bboxIndex, int areaIndex, int totalAreas, int windowIndex, int wordIndex) {
        return String.format(config.getDebugFilePath()+"%s_%dW%d_%03d_%03d.png",
            bboxIndex,
            areaIndex,
            totalAreas,
            windowIndex,
            wordIndex);
    }
}
