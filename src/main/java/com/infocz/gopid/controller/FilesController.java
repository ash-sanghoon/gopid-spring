package com.infocz.gopid.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import com.infocz.util.neo4j.CypherExecutor;

import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
@RequestMapping("/api/files")
public class FilesController {
	@Autowired
	private CypherExecutor cypherExecutor;

    @Value("${com.infocz.upload.temp.path}") // application 의 properties 의 변수
    private String uploadPath;
    
	@GetMapping("/download/{filename:.+}")
	@ResponseBody
	public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws MalformedURLException, FileNotFoundException {

		System.out.println("serveFile");
		Path filePath = Paths.get(uploadPath).resolve(filename);
		Resource file = new UrlResource(filePath.toUri());
		if(!(file.exists() || file.isReadable())) {
			throw new FileNotFoundException("Could not read file: " + filename);
		}
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"" + file.getFilename() + "\"").body(file);
	}

	@GetMapping("/view/{filename:.+}")
	@ResponseBody
	public ResponseEntity<Resource> getImageDynamicType(@PathVariable String filename) throws IOException {
		System.out.println("getImageDynamicType");
		Path filePath = Paths.get(uploadPath).resolve(filename);
		Resource file = new UrlResource(filePath.toUri());
		if(!(file.exists() || file.isReadable())) {
			throw new FileNotFoundException("Could not read file: " + filename);
		}
		MediaType mediaType = MediaType.valueOf(detectDocType(file.getInputStream()).toString());
	    return ResponseEntity.ok().contentType(mediaType).body(file);
	}
	
	public static org.apache.tika.mime.MediaType detectDocType(InputStream stream) throws IOException {
	  
	    Detector detector = new DefaultDetector();
	    Metadata metadata = new Metadata();

	    org.apache.tika.mime.MediaType mediaType = detector.detect(stream, metadata);
	    return mediaType;
	}
	
    /*파일 업로드, 업로드 결과 반환  */
    @SuppressWarnings("rawtypes")
	@PostMapping("/upload")
    public List<HashMap> uploadFile(MultipartFile[] uploadFiles, String projectUuid) throws SAXException, TikaException, FileNotFoundException, IOException {

		System.out.println("uploadFile");
    	List<HashMap> list = new ArrayList<HashMap>();
        for (MultipartFile uploadFile: uploadFiles) {

            String orginalName = uploadFile.getOriginalFilename();
            String fileName = orginalName.substring(orginalName.lastIndexOf("\\") + 1);
            String uuid = UUID.randomUUID().toString();
            String saveName = uploadPath + File.separator + UUID.randomUUID().toString();

            Path savePath = Paths.get(saveName);

            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("fileName", fileName);
            map.put("uuid", uuid);
            map.put("path", savePath.toString());
            map.put("projectUuid", projectUuid);

            try (Session session = cypherExecutor.getDriver().session(SessionConfig.builder().withDatabase(cypherExecutor.getDatabase()).build())) {
            	try (Transaction tx = session.beginTransaction()) {
                    String fileQ = "MERGE (f:File {name: $fileName, uuid:$uuid, path:$path}) RETURN f";
                    // 파일기준으로 Drawing을 생성 - DrawingName = 파일명
                    tx.run(fileQ, map);
                    String createRelationDaF = "MATCH (a:Project {uuid: $projectUuid}), (b:File {uuid: $uuid}) MERGE (a)-[r:HAS_FILE]->(b) RETURN a ";
                    // Drawing을 프로젝트와 연결, 파일과 연결
                    tx.run(createRelationDaF, map);
                    uploadFile.transferTo(savePath); 
                    new UrlResource(savePath.toUri());
                    map.put("contentType", detectDocType(new UrlResource(savePath.toUri()).getInputStream()).toString());
                    list.add(map);
                    tx.commit();
            	}
            }
        }
        return list;
    }
//    
//    /*날짜 폴더 생성*/
//    private String makeFolder() {
//
//        String str = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
//
//        String folderPath = str.replace("/", File.separator);
//
//        // make folder --------
//        File uploadPathFolder = new File(uploadPath, folderPath);
//
//        if(!uploadPathFolder.exists()) {
//            boolean mkdirs = uploadPathFolder.mkdirs();
//            log.info("-------------------makeFolder------------------");
//            log.info("uploadPathFolder.exists(): "+uploadPathFolder.exists());
//            log.info("mkdirs: "+mkdirs);
//        }
//
//        return folderPath;
//
//    }

}