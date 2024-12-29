package com.infocz.gopid.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.Tika;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infocz.util.conf.Config;
import com.infocz.util.neo4j.CypherExecutor;
import com.infocz.util.ocr.TypeTitleExtractor;

@Service
public class ProjectService {

	@Autowired
	private Config config;

    @Autowired
    private TypeTitleExtractor titleExtractor;
    
	@Autowired
	private CypherExecutor cypherExecutor;
	
	@Autowired
	private DrawingService drawingService;

	@Transactional
	public List<Map<String, Object>> projectList(HashMap<String, Object> reqMap) {

    	Session session = cypherExecutor.getSession();
	    // timestamp()를 milliseconds로 받아서 datetime으로 변환 후 포맷팅

	    Result result = session.run("""
	    		MATCH (p:Project) WHERE COALESCE(p.project_name, '') CONTAINS $projectName 
	            RETURN p.uuid as uuid, 
	    		       p.project_name as project_name, 
	    		       p.company as company,
	    		       p.country as country, 
	    		       p.standard as standard,
	    		       p.line_no_pattern, 
	    		       p.drawing_no_pattern,
	    		       50 AS progress,
	                   apoc.date.format(p.last_update_date, 'ms', 'yyyy-MM-dd HH:mm:ss', 'Asia/Seoul') as last_update_date
	    		""", reqMap);
		return result.list(Record::asMap);
	}
    
    // 최초 생성
    @Transactional
    public Map<String, Object> projectCreate(HashMap<String, Object> map) {
    	Session session = cypherExecutor.getSession();
    	return session.run(""" 
	        		CREATE (p:Project {uuid: $uuid}) 
	        		RETURN p.uuid AS uuid
	        		""",
        		Map.of("uuid", UUID.randomUUID().toString())
        		).single().asMap();
	}
    
    @SuppressWarnings("unchecked")
	@Transactional
	private void saveProjectInfo(HashMap<String, Object> projectMap) {
        Session session = cypherExecutor.getSession();
		session.run( """ 
				MATCH (p:Project {uuid: $uuid})  
				SET 	p.project_name = $project_name, 
						p.country = $country,   
						p.company = $company, 
						p.drawing_no_pattern = $drawing_no_pattern, 
						p.line_no_pattern = $line_no_pattern, 
						p.standard = $standard,
    				    p.last_update_date = timestamp()
						RETURN p
				""", projectMap);
						
		List<Map<String, Object>> drawingList = (List<Map<String, Object>>)projectMap.get("drawings");
        for(Map<String, Object> drawing : drawingList) {
        	session.run(""" 
				MATCH (d:Drawing {uuid: $uuid})
				SET   d.drawing_no = $drawing_no,
				      d.sheet_no = $sheet_no
	        	""" , drawing);
        }
    }

    @SuppressWarnings("unchecked")
	@Transactional
	public Map<String, Object> projectSave(HashMap<String, Object> projectMap) throws IOException {
    	Session session = cypherExecutor.getSession();
		
		saveProjectInfo(projectMap);

        for(Map<String, Object> fileMap : (List<Map<String, Object>>)projectMap.get("files")) {
            Result result = session.run("""
            		MATCH (f:File {uuid:$uuid}) 
            		RETURN f.drawing_created AS drawing_created, 
            			   f.path AS path
            		""", fileMap);
            if (!result.hasNext()) continue;
            Record recordfile = result.next();
            String drawingCreated = recordfile.get("drawing_created").asString();
            if("Y".equals(drawingCreated)) continue; // 도면 파일 생성 완료 다음 sheet 처리
            
    		session.run("""
    				MATCH (f:File {uuid:$uuid}) SET f.drawing_created = 'Y'
    				""", fileMap);

    		Resource file = new UrlResource(Paths.get(config.getUploadPath()).resolve(recordfile.get("path").asString()).toUri());
    		
    		String mediaType = new Tika().detect(file.getInputStream());
    	    if(mediaType.endsWith("pdf")){
    		    PDDocument document = Loader.loadPDF(file.getFile());
    	        PDFRenderer pdfRenderer = new PDFRenderer(document);
    	        int pageCount = document.getNumberOfPages();

    	        for (int page = 0; page < pageCount; page++) {
    	            // PDF 페이지를 렌더링하여 BufferedImage로 변환
    	            BufferedImage image = pdfRenderer.renderImageWithDPI(page, Config.BASE_DPI, ImageType.RGB);
    	            saveDrawingInfo(projectMap, image, page, fileMap);
    	        }
    	    }else if(mediaType.startsWith("image/")) {
    	    	BufferedImage image = ImageIO.read(file.getFile());
    	    	saveDrawingInfo(projectMap, image, 1, fileMap);
    	    }else {
    	    	throw new RuntimeException("cannot proc mediatype " + mediaType);
    	    }
        }
        return projectMap;
	}
	
	private void saveDrawingInfo(HashMap<String, Object> projectMap, BufferedImage image, int page, Map<String, Object> fileMap) throws IOException {
		String projectUuid = (String)projectMap.get("uuid");
		String drawing_no_pattern  = (String)projectMap.get("drawing_no_pattern");
    	Session session = cypherExecutor.getSession();
        HashMap<String, Object> drawingMap = new HashMap<String, Object>();
        drawingMap.put("projectUuid", projectUuid);

        String drawingUuid = UUID.randomUUID().toString();
        // PNG 파일 이름 지정
        String drawingFilePath = config.getUploadPath() + "/" + drawingUuid;

        // BufferedImage를 PNG 파일로 저장
        ImageIO.write(image, "PNG", new File(drawingFilePath));

        // 썸네일 이미지 생성 (width 300 픽셀)
        double scaleFactor = 300.0 / image.getWidth();
        int thumbnailHeight = (int)(image.getHeight() * scaleFactor);
        
        BufferedImage thumbnailImage = new BufferedImage(300, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
        thumbnailImage.getGraphics().drawImage(
        		image.getScaledInstance(300, thumbnailHeight, java.awt.Image.SCALE_SMOOTH), 
            0, 0, null
        );
        String thumnbnailUuid = UUID.randomUUID().toString();
        // 이미지 저장
        ImageIO.write(thumbnailImage, "png", new File(config.getUploadPath() + "/" + thumnbnailUuid));
        
        System.out.println("Page " + (page + 1) + " saved as " + drawingFilePath);
        
		List<String> infos = titleExtractor.extract(image, drawing_no_pattern, List.of("Not Found", "000"));

        drawingMap.put("uuid", drawingUuid);
        drawingMap.put("drawing_no", infos.get(0));
        drawingMap.put("sheet_no", infos.get(1));
        drawingMap.put("thumnbnail_uuid", thumnbnailUuid);
        drawingMap.put("width", image.getWidth());
        drawingMap.put("height", image.getHeight());
        drawingMap.put("file_uuid", fileMap.get("uuid"));
        drawingMap.put("file_page_no", String.format("%03d", page+1));
        
        // 파일기준으로 Drawing을 생성 - DrawingName = 파일명
		session.run(""" 
				CREATE (d:Drawing {
							uuid: 			$uuid, 
							drawing_no:		$drawing_no, 
							sheet_no:		$sheet_no, 
							width:			$width, 
							height:			$height,
							file_uuid:		$file_uuid, 
							file_page_no:	$file_page_no, 
							thumnbnail_uuid:$thumnbnail_uuid 
						})
				WITH  d
				MATCH (p:Project {uuid: $projectUuid})
        		CREATE (p)-[:HAS_DRAWING]->(d)
				""", drawingMap);
	}
	
	@Transactional
	public Map<String, Object> projectDetail(String projectId) {
	    Map<String, Object> paramMap = Map.of("uuid", projectId);

	    Map<String, Object> projectDetail = new HashMap<String, Object>();
	    projectDetail.put("country", "");
	    projectDetail.put("company", "");
	    projectDetail.put("country", "");
	    projectDetail.put("standard", "");
	    projectDetail.put("project_name", "");
	    projectDetail.put("line_no_pattern", "");
	    projectDetail.put("drawing_no_pattern", "");
	    projectDetail.put("files", new ArrayList<Map<String, Object>>());
	    projectDetail.put("drawings", new ArrayList<Map<String, Object>>());

	    Session session = cypherExecutor.getSession();
	    Result resultProject = session.run("""
	    		MATCH (p:Project {uuid:$uuid}) 
	            RETURN p.uuid AS uuid, 
	    		       p.project_name AS project_name, 
	    		       p.company AS company,
	    		       p.country AS country, 
	    		       p.standard AS standard,
	    		       p.line_no_pattern AS line_no_pattern, 
	    		       p.drawing_no_pattern AS drawing_no_pattern,
	    		       50 AS progress,
	                   apoc.date.format(p.last_update_date, 'ms', 'yyyy-MM-dd HH:mm:ss', 'Asia/Seoul') as last_update_date

	    	""", paramMap);
	    

	    projectDetail.putAll(resultProject.next().asMap());
	    projectDetail.put("drawings", drawingService.getDrawingList(projectId));

	    Result resultFiles = session.run("""
    		MATCH  (f:File) <- [] - (n:Project {uuid:$uuid})
            RETURN f.uuid as uuid, f.name as name, 
                   f.drawing_created as drawing_created
    		""", paramMap);
	    
	    if(resultFiles.hasNext()) {
	        projectDetail.put("files", resultFiles.list(record -> record.asMap()));
	    }
	    return projectDetail;
	}
}