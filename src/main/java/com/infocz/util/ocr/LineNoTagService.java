package com.infocz.util.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

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

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;

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
        
        lineNoTagService.lineno("5b44ad57-495b-4731-bf14-e671bc7c4e39");
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
    public List<BboxRect> lineno(String runId) throws IOException{

        Map<String, Object> runInfo = getRunInfo(runId);
		Session session = cypherExecutor.getSession();
		
        // 이미지파일을 테서렉트로 ocr처리
        Tesseract tesseract = config.getTesseract();
        // 원본이미지
		//BufferedImage fullImage = prepare1200DPIImage(runInfo);
        BufferedImage fullImage = ImageIO.read(new File(config.getDebugFilePath()+"reserve/A_1200_001.png"));
        
        LinenoFinder linenoFinder = buildLinenoFinder(runInfo);
        List<LinenoResult> linenoResultList = linenoFinder.findAllLineno(fullImage, tesseract);
        for(LinenoResult result : linenoResultList) {
        	System.out.println(result);
        }
		return null;
    }
    
    private LinenoFinder buildLinenoFinder(Map<String, Object> runInfo) {
    	String drawingTypeCode = (String)runInfo.get("drawing_no_pattern"); // 도면 유형 코드
    	
        double rectMargin = 5;           // 도면이미지의 탐색대상 외곽마진 비율
        int charHeight = config.getMinCharH(drawingTypeCode);              // lineno 문자의 크기 (pixel)
        int linenoChars = 15;             // lineno 구성 문자 갯수
        int charWidth = (int)(charHeight * 0.8);               // lineno 문자의 좌우길이 (pixel)
        double linenoMarginRatioY = 0.1;   // 탐색 window Y축 마진 비율
        double linenoMarginRatioX = 0.3;   // 탐색 window X축 마진 비율
        double slidingRatioX = 0.3;        // X축 탐색Window 이동비율
        double slidingRatioY = 0.3;        // Y축 탐색Window 이동비율
        double acceptMinSizeRatio = 0.9;   // 인정 최소크기 비율
        double acceptMaxSizeRatio = 1.1;   // 인정 최대크기 비율
        double linenoLengthMinRatio = 0.8;
        double linenoLengthMaxRatio = 1.2;
        
        if("A".equals(drawingTypeCode)) {
        	rectMargin = 5;
        }
        
        LinenoFinder finder = new LinenoFinder(rectMargin, charHeight, charWidth, linenoChars,
                linenoMarginRatioY, linenoMarginRatioX,
                drawingTypeCode, slidingRatioX, slidingRatioY,
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
