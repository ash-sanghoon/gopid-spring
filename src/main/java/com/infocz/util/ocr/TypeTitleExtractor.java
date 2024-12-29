package com.infocz.util.ocr;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.infocz.util.conf.Config;

import net.sourceforge.tess4j.Tesseract;

@Component
public class TypeTitleExtractor {

	@Autowired
	private ATypeTitleExtractor aTypeTitleExtractor;
	@Autowired
	private BTypeTitleExtractor bTypeTitleExtractor;
	@Autowired
	private CTypeTitleExtractor cTypeTitleExtractor;
	@Autowired
	private DTypeTitleExtractor dTypeTitleExtractor;
	
	public TypeTitleExtractor() {
	}

	public List<String> extract(BufferedImage image, String type, List<String> alter) throws IOException {
		if("A".equals(type)) {
			return aTypeTitleExtractor.extract(alter, image);
		}else if("B".equals(type)) {
			return bTypeTitleExtractor.extract(alter, image);
		}else if("C".equals(type)) {
			return cTypeTitleExtractor.extract(alter, image);
		}else if("D".equals(type)) {
			return dTypeTitleExtractor.extract(alter, image);
		}
		return null;
	}

}