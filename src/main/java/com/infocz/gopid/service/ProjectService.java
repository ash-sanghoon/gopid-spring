package com.infocz.gopid.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import com.infocz.util.neo4j.CypherExecutor;

@Service
public class ProjectService {

    @Value("${com.infocz.upload.temp.path}") // application 의 properties 의 변수
    private String uploadPath;

	@Autowired
	private CypherExecutor cypherExecutor;
	
	@Autowired
	private DrawingService drawingService;

    public List<Map<String, Object>> projectList(HashMap<String, Object> reqMap) {
    	
        String query = "MATCH (p:Project) WHERE p.name CONTAINS $projectName RETURN p";

        Function<?, ?> mapper = (record -> ((Record) record).get("p").asNode().asMap());
        List<Map<String, Object>> list = cypherExecutor.execCyphers(query, reqMap, mapper);
        
        // 진행률 조회
        // 등록된 도면수  와 완성된 도면수의 비 * 100
    	//List<Map<String, Object>> drawingMap = getDrawingList((String)map.get("uuid"));
        List<Map<String, Object>> updatedList = list.stream()
        	    .map(map -> {
        	        // 수정 가능한 Map으로 복사
        	        Map<String, Object> modifiableMap = new HashMap<>(map);
        	        modifiableMap.put("progress", 50);
        	        return modifiableMap;
        	    })
        	    .collect(Collectors.toList());
		return updatedList;
    }
    
    public List<Map<String, Object>> projectListForTest1(HashMap<String, Object> reqMap) {
    	
        String query = "MATCH (n:Project) RETURN n LIMIT 25";

        Function<?, ?> mapper = (record -> ((Record) record).get("n").asNode().asMap());
        List<Map<String, Object>> list = cypherExecutor.execCyphers(query, reqMap, mapper);
		return list;
    }

    public List<Map<String, Object>> projectListForTest2(HashMap<String, Object> reqMap) {
    	
    	String q1 = "MATCH (p:Standard)-[r]->(d:Category) RETURN p, r, d ";
    	var result1 = cypherExecutor.getDriver().executableQuery(q1)
    		    .withConfig(QueryConfig.builder().withDatabase("samsung-ena").build()).withParameters(new HashMap<String, Object>())
    		    .execute();

		// Loop through results and do something with them
		var records = result1.records();
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		for(Record r : records) {
			Map<String, Object> p = r.get("p").asNode().asMap();
			Map<String, ?> rel = r.get("r").asRelationship().asMap();
			Map<String, ?> d = r.get("d").asNode().asMap();
			HashMap<String, Object> fullMap = new HashMap<String, Object>();
			p.forEach((key, val) -> fullMap.put("Standard-"+key, val));
			rel.forEach((key, val) -> fullMap.put("Relation-"+key, val));
			d.forEach((key, val) -> fullMap.put("Category-"+key, val));
			list.add(fullMap);
		}

		return list;
    }
    
    public Map<String, Object> projectCreate(HashMap<String, Object> map) {
		String createQ = "MERGE (p:Project {uuid: $uuid}) RETURN p";
        String projectUuid = UUID.randomUUID().toString();
        map.put("uuid", projectUuid);
        Function<?, ?> mapper = (record -> ((Record) record).get("p").asNode().asMap());
        List<Map<String, Object>> list = cypherExecutor.execCyphers(createQ, map, mapper);
        Map<String, Object> projectMap = new HashMap<>(list.get(0));
		return projectMap;
	}

	public Map<String, Object> projectSave(HashMap<String, Object> map) throws IOException {
		String projectUuid = (String)map.get("uuid");
		String createQ = "MATCH (p:Project {uuid: $uuid}) " + 
						"SET p.name = $projectName, " + 
						"p.country = $country, " +  
						"p.company = $company, " +
						"p.standard = $standard " +
						"RETURN p";
		//String createQ = "MERGE (f:Project {projectName: $projectName, uuid: $uuid, nation: $nation, company: $company, standard: $standard}) RETURN f";
        Function<?, ?> mapper = (record -> ((Record) record).get("p").asNode().asMap());
        List<Map<String, Object>> list = cypherExecutor.execCyphers(createQ, map, mapper);
        
        // Drawing 파일 PDF --  또는 image
        List<Map<String, Object>> files = (List<Map<String, Object>>)map.get("files");
        if(files.size() < 1) return list.get(0);

        // 파일이 있는 경우 - 
        HashMap<String, Object> drawingMap = new HashMap<String, Object>();
        drawingMap.put("projectUuid", projectUuid);
        for(Map<String, Object> fileMap : files) {
            try (Session session = cypherExecutor.getDriver().session(SessionConfig.builder().withDatabase(cypherExecutor.getDatabase()).build())) {
            	try (Transaction tx = session.beginTransaction()) {
		            Result result = tx.run("MATCH (a:File {uuid:$uuid}) RETURN a.drawingCreated AS drawingCreated, a.path AS filePath", fileMap);
		            if (!result.hasNext()) {
		            	continue;
		            }
		            Record recordfile = result.next();
		            String drawingCreated = recordfile.get("drawingCreated").asString(); // 도면 파일 생성 완료
		            if("Y".equals(drawingCreated)) continue;
		    		String updateFile = "MATCH (f:File {uuid:$uuid}) SET f.drawingCreated = 'Y' ";
			        cypherExecutor.execCyphers(updateFile, map, mapper);

		            String pdfFilePath = recordfile.get("filePath").asString(); // pdf 인경우
		    		Path filePath = Paths.get(uploadPath).resolve(pdfFilePath);
		    		Resource file = new UrlResource(filePath.toUri());
		    		
		    	    Detector detector = new DefaultDetector();
		    	    Metadata metadata = new Metadata();
		    	    org.apache.tika.mime.MediaType mediaType = detector.detect(file.getInputStream(), metadata);
		    	    if("pdf".equals(mediaType.getSubtype())){
			    	    
		        	    PDDocument document = Loader.loadPDF(file.getFile());
		                PDFRenderer pdfRenderer = new PDFRenderer(document);
		                int pageCount = document.getNumberOfPages();
	
		                for (int page = 0; page < pageCount; page++) {
		                    // PDF 페이지를 렌더링하여 BufferedImage로 변환
		                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
		                    String drawingUuid = UUID.randomUUID().toString();
		                    // PNG 파일 이름 지정
		                    String drawingFilePath = uploadPath + "/" + drawingUuid;
	
		                    // BufferedImage를 PNG 파일로 저장
		                    ImageIO.write(image, "PNG", new File(drawingFilePath));
	
		                    // 썸네일 이미지 생성 (width 200 픽셀)
		                    double scaleFactor = 200.0 / image.getWidth();
		                    int thumbnailHeight = (int)(image.getHeight() * scaleFactor);
		                    
		                    BufferedImage thumbnailImage = new BufferedImage(200, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
		                    thumbnailImage.getGraphics().drawImage(
		                    		image.getScaledInstance(200, thumbnailHeight, java.awt.Image.SCALE_SMOOTH), 
		                        0, 0, null
		                    );
		                    String thumnbnailUuid = UUID.randomUUID().toString();
		                    // 이미지 저장
		                    ImageIO.write(thumbnailImage, "png", new File(uploadPath + "/" + thumnbnailUuid));
		                    
		                    System.out.println("Page " + (page + 1) + " saved as " + drawingFilePath);
		                    drawingMap.put("drawingUuid", drawingUuid);
		                    drawingMap.put("drawingName", FilenameUtils.removeExtension((String)fileMap.get("fileName")) + "-" + String.format("%03d", page+1));
		                    drawingMap.put("thumnbnailUuid", thumnbnailUuid);
		                    drawingMap.put("width", image.getWidth());
		                    drawingMap.put("height", image.getHeight());
	
		            		String createDrawingNode = "MERGE (p:Drawing {uuid: $drawingUuid, name:$drawingName, thumnbnailUuid:$thumnbnailUuid, width:$width, height:$height}) RETURN p";
		                    // 파일기준으로 Drawing을 생성 - DrawingName = 파일명
		                    tx.run(createDrawingNode, drawingMap);
		                    
		                    String createRelationPaD = "MATCH (a:Drawing {uuid: $drawingUuid}), (b:Project {uuid: $projectUuid}) MERGE (b)-[r:HAS_DRAWING]->(a) RETURN a, b, r ";
		                    tx.run(createRelationPaD, drawingMap);
		                }
		    	    }
                    tx.commit();
            	}
            }
        }
        
		return list.get(0);
	}

    @SuppressWarnings("deprecation")
	private void procFile(String projectUuid, String fileUuid, String fileName, String seq) throws IOException {
        String drawingUuid = UUID.randomUUID().toString();
        HashMap<String, Object> drawingMap = new HashMap<String, Object>();
        drawingMap.put("uuid", drawingUuid);
        drawingMap.put("drawingName", fileName + "-" + seq);
        drawingMap.put("fileUuid", fileUuid);
        drawingMap.put("projectUuid", projectUuid);

        try (Session session = cypherExecutor.getDriver().session(SessionConfig.builder().withDatabase(cypherExecutor.getDatabase()).build())) {
        	try (Transaction tx = session.beginTransaction()) {
        		String createQ = "MERGE (p:Drawing {uuid: $uuid, name:$drawingName}) RETURN p";
                // 파일기준으로 Drawing을 생성 - DrawingName = 파일명
                tx.run(createQ, drawingMap);
                String createRelationDaF = "MATCH (a:Drawing {uuid: $uuid}), (b:File {uuid: $fileUuid}) MERGE (a)-[r:HAS_FILE]->(b) RETURN a ";
                // Drawing을 프로젝트와 연결, 파일과 연결
                tx.run(createRelationDaF, drawingMap);
                
                String createRelationPaD = "MATCH (a:Drawing {uuid: $uuid}), (b:Project {uuid: $projectUuid}) MERGE (b)-[r:HAS_DRAWING]->(a) RETURN a, b, r ";
                tx.run(createRelationPaD, drawingMap);
                
                Result result = tx.run("MATCH (a:FILE {uuid:$fileUuid}) RETURN a.path AS filePath");
                if (result.hasNext()) {
                	// error
                	throw new RuntimeException("File Not Found");
                }
                
                String pdfFilePath = result.next().get("filePath").asString(); // pdf 인경우

        	    PDDocument document = Loader.loadPDF(new File(pdfFilePath));
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                int pageCount = document.getNumberOfPages();

                for (int page = 0; page < pageCount; page++) {
                    // PDF 페이지를 렌더링하여 BufferedImage로 변환
                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);

                    String imageUuid = UUID.randomUUID().toString();
                    // PNG 파일 이름 지정
                    String imageFileName = uploadPath + "/" + imageUuid;

                    // BufferedImage를 PNG 파일로 저장
                    ImageIO.write(image, "PNG", new File(imageFileName));

                    System.out.println("Page " + (page + 1) + " saved as " + imageFileName);
                }

                // 트랜잭션 커밋
                tx.commit();
	        }
	    }
	        
	        // Drawing 파일이 PDF 인경우 이를 SHEET 단위 이미지 파일로 분리 300 DPI
	        // 이미지 파일별로 Sheet 노드 생성 Sheet 명 = Drawing명 + 순번
	        // Sheet노드를 프로젝트와 연결, 파일과 연결
    }

	public Map<String, Object> projectDetail(String projectId) {
		HashMap paramMap = new HashMap();
		paramMap.put("uuid", projectId);
		
		Map<String, Object> projectDetail = new HashMap();
		projectDetail.put("country", "");
		projectDetail.put("company", "");
		projectDetail.put("country", "");
		projectDetail.put("standard", "");
		projectDetail.put("projectName", "");
		projectDetail.put("files", new ArrayList());
		projectDetail.put("drawings", new ArrayList());
        try (Session session = cypherExecutor.getDriver().session(SessionConfig.builder().withDatabase(cypherExecutor.getDatabase()).build())) {
        	try (Transaction tx = session.beginTransaction()) {
	            Result resultProject = tx.run("MATCH (n:Project {uuid:$uuid}) RETURN n", paramMap);
	            if (!resultProject.hasNext()) {
	            	System.out.println("Project Not found.");
	            	throw new RuntimeException("Project Not found.");
	            }
	            projectDetail.putAll(resultProject.next().get("n").asNode().asMap());
	            projectDetail.put("drawings", drawingService.getDrawingList(projectId));
	            
	            Result resultFiles = tx.run("MATCH (f:File) <- [] - (n:Project {uuid:$uuid})  RETURN f ", paramMap);
	            if(resultFiles.hasNext()) {
	            	projectDetail.put("files", resultFiles.list(record -> ((Record) record).get("f").asNode().asMap()));
	            }
        	}
        }
		return projectDetail;
	}
}