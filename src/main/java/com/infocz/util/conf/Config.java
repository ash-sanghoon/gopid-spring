package com.infocz.util.conf;

import java.awt.Rectangle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import net.sourceforge.tess4j.Tesseract;

@Component
public class Config {

    @Value("${com.infocz.upload.temp.path}") // application 의 properties 의 변수
    private String uploadPath;

    @Value("${com.infocz.debug.file.path}") // application 의 properties 의 변수
    private String debugFilePath;

	@Value("${project.a.font.300dpi.height}")
	private int aCharHeight;
	
	@Value("${project.b.font.300dpi.height}")
	private int bCharHeight;
	
	@Value("${project.c.font.300dpi.height}")
	private int cCharHeight;
	
	@Value("${project.d.font.300dpi.height}")
	private int dCharHeight;
	
	public final static int USE_DPI = 1200;
	public final static int BASE_DPI = 300;
	
	public String getUploadPath() {
		return uploadPath;
	}
	
	public Tesseract getTesseract() {
		Tesseract tesseract = new Tesseract();
		tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata"); // Tesseract 설치 경로
		tesseract.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-._/ \"");
		tesseract.setLanguage("eng"); // 언어 설정
		
		
//		tesseract.setVariable("textord_min_linesize", "47");  // 52 - (52 * 0.1)
//		tesseract.setVariable("textord_max_linesize", "57");  // 52 + (52 * 0.1)

		// 노이즈 제거 설정 약간 강화, 노이즈는 검출결과 필터링에 사용되므로 , 적용이 어려움
//		tesseract.setVariable("textord_noise_sizelimit", "1.3");    // 1.0에서 0.3 증가
//		tesseract.setVariable("textord_noise_normratio", "2.4");    // 2.2에서 0.2 증가
//		tesseract.setVariable("textord_noise_snr", "0.2");          // 0.1에서 0.1 증가
		
		// 페이지 분할 모드 설정
		tesseract.setVariable("tessedit_pageseg_mode", "7");

		// 디버그 레벨 설정
		tesseract.setVariable("debug_level", "0");
		tesseract.setVariable("debug_file", "/dev/null");

		return tesseract;
	}
	
	public String getDebugFilePath() {
		return debugFilePath;
	}
	public int getMaxCharH(String drawingPattern) {
		return getMinCharH(drawingPattern) * 3;
	}
	
	public int getMinCharH(String drawingPattern) {
		if("A".equals(drawingPattern)) {
			return aCharHeight * USE_DPI / BASE_DPI;
		}else if("B".equals(drawingPattern)) {
			return bCharHeight * USE_DPI / BASE_DPI;
		}else if("C".equals(drawingPattern)) {
			return cCharHeight * USE_DPI / BASE_DPI;
		}else if("D".equals(drawingPattern)) {
			return dCharHeight * USE_DPI / BASE_DPI;
		}
		return 13 * USE_DPI / BASE_DPI;
	}
	
	@Value("${project.a.legend.coordinate}")
	private String aLegend;
	
	@Value("${project.a.legend.coordinate}")
	private String bLegend;
	
	@Value("${project.a.legend.coordinate}")
	private String cLegend;
	
	@Value("${project.a.legend.coordinate}")
	private String dLegend;
	
	public Rectangle getLegendArea(String drawingPattern) {
		String[] parts = "1,1,1,1".split(",");
		if("A".equals(drawingPattern)) {
			parts = aLegend.split(",");
		}else if("B".equals(drawingPattern)) {
			parts = bLegend.split(",");
		}else if("C".equals(drawingPattern)) {
			parts = cLegend.split(",");
		}else if("D".equals(drawingPattern)) {
			parts = dLegend.split(",");
		}
        return new Rectangle(
                Integer.parseInt(parts[0]) * USE_DPI / BASE_DPI,
                Integer.parseInt(parts[1]) * USE_DPI / BASE_DPI,
                Integer.parseInt(parts[2]) * USE_DPI / BASE_DPI,
                Integer.parseInt(parts[3]) * USE_DPI / BASE_DPI
            );
	}
}
