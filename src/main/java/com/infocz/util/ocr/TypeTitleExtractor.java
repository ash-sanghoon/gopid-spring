package com.infocz.util.ocr;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import net.sourceforge.tess4j.Tesseract;

@Component
public class TypeTitleExtractor {
	
    @Value("${com.infocz.parser.dpi}") // application 의 properties 의 변수
    private String dpi;

	private final Tesseract tesseract;
	
	@Autowired
	private ATypeTitleExtractor aTypeTitleExtractor;
	@Autowired
	private BTypeTitleExtractor bTypeTitleExtractor;
	@Autowired
	private CTypeTitleExtractor cTypeTitleExtractor;
	@Autowired
	private DTypeTitleExtractor dTypeTitleExtractor;
	
	public TypeTitleExtractor() throws IOException {
		this.tesseract = new Tesseract();
		this.tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata"); // Tesseract 설치 경로
		this.tesseract.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._/ ");
	}

	public List<String> extract(BufferedImage image, String type, List<String> alter) throws IOException {
		int DPI = Integer.parseInt(dpi);
		if("A".equals(type)) {
			return aTypeTitleExtractor.extract(alter, image, tesseract, DPI);
		}else if("B".equals(type)) {
			return bTypeTitleExtractor.extract(alter, image, tesseract, DPI);
		}else if("C".equals(type)) {
			return cTypeTitleExtractor.extract(alter, image, tesseract, DPI);
		}else if("D".equals(type)) {
			return dTypeTitleExtractor.extract(alter, image, tesseract, DPI);
		}
		return null;
	}

}