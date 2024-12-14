package com.infocz.gopid.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import lombok.extern.log4j.Log4j2;

@RestController
@Log4j2
public class UploadController {

    @Value("${com.infocz.upload.temp.path}") // application 의 properties 의 변수
    private String uploadPath;
    
	@GetMapping("/files/{filename:.+}")
	@ResponseBody
	public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws MalformedURLException, FileNotFoundException {

		Path filePath = Paths.get(uploadPath).resolve(filename);
		Resource file = new UrlResource(filePath.toUri());
		if(!(file.exists() || file.isReadable())) {
			throw new FileNotFoundException("Could not read file: " + filename);
		}
		if (file == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"" + file.getFilename() + "\"").body(file);
	}

	@GetMapping("/get-image-dynamic-type")
	@ResponseBody
	public ResponseEntity<InputStreamResource> getImageDynamicType(@RequestParam("jpg") boolean jpg) {
	    MediaType contentType = jpg ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG;
	    InputStream in = jpg ?
	      getClass().getResourceAsStream("/com/baeldung/produceimage/image.jpg") :
	      getClass().getResourceAsStream("/com/baeldung/produceimage/image.png");
	    return ResponseEntity.ok()
	      .contentType(contentType)
	      .body(new InputStreamResource(in));
	}
	
    /*파일 업로드, 업로드 결과 반환*/
    @PostMapping("/upload")
    public List<HashMap> uploadFile(MultipartFile[] uploadFiles) throws SAXException, TikaException, FileNotFoundException, IOException {

    	List<HashMap> list = new ArrayList<HashMap>();
        for (MultipartFile uploadFile: uploadFiles) {

            // 실제 파일 이름 IE나 Edge는 전체 경로가 들어오므로 => 바뀐 듯 ..
            String orginalName = uploadFile.getOriginalFilename();
            assert orginalName != null;
            String fileName = orginalName.substring(orginalName.lastIndexOf("\\") + 1);

            log.info("fileName: "+fileName);

            // 날짜 폴더 생성
            String folderPath = makeFolder();

            // UUID
            String uuid = UUID.randomUUID().toString();

            // 저장할 파일 이름 중간에 "_"를 이용해서 구현
            String saveName = uploadPath + File.separator + folderPath + File.separator + uuid + "_" + fileName;

            Path savePath = Paths.get(saveName);

            try {
                uploadFile.transferTo(savePath); // 실제 이미지 저장
            } catch (IOException e) {
                e.printStackTrace();
            }
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("fileName", fileName);
            map.put("uuid", uuid);
            map.put("folderPath", folderPath);
            map.put("metadata", extractMetadata(savePath));
            list.add(map);
        }
        return list;

    }
    
    private HashMap<String, String> extractMetadata(Path savePath) throws FileNotFoundException, IOException, SAXException, TikaException {

        // 메타데이터 저장
        Metadata metadata = new Metadata();

        // 파서 생성 및 분석
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        
        try (FileInputStream inputstream = new FileInputStream(savePath.toFile())) {
            parser.parse(inputstream, handler, metadata, new ParseContext());
        }

        HashMap<String, String> map = new HashMap<String, String>();
        // 메타데이터 출력
        String[] metadataNames = metadata.names();

        for (String name : metadataNames) {
        	map.put(name, metadata.get(name));
            System.out.printf("%s: %s\n", name, metadata.get(name));
        }
        return map;
    }

    /*날짜 폴더 생성*/
    private String makeFolder() {

        String str = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        String folderPath = str.replace("/", File.separator);

        // make folder --------
        File uploadPathFolder = new File(uploadPath, folderPath);

        if(!uploadPathFolder.exists()) {
            boolean mkdirs = uploadPathFolder.mkdirs();
            log.info("-------------------makeFolder------------------");
            log.info("uploadPathFolder.exists(): "+uploadPathFolder.exists());
            log.info("mkdirs: "+mkdirs);
        }

        return folderPath;

    }

}