package com.infocz.util.ocr;

import net.sourceforge.tess4j.Tesseract;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.util.*;

public class LinenoFinder {
    // 멤버 변수
    private double rectMargin;           // 도면이미지의 탐색대상 외곽마진 비율
    private int charHeight;              // lineno 문자의 높이 (pixel)
    private int charWidth;               // lineno 문자의 좌우길이 (pixel)
    private int linenoChars;             // lineno 구성 문자 갯수
    private double linenoMarginRatioY;   // 탐색 window Y축 마진 비율
    private double linenoMarginRatioX;   // 탐색 window X축 마진 비율
    private String drawingTypeCode;      // 도면 유형 코드 (A, B, C, D)
    private double slidingRatioX;        // X축 탐색Window 이동비율
    private double slidingRatioY;        // Y축 탐색Window 이동비율
    private double acceptMinSizeRatio;   // 인정 최소크기 비율
    private double acceptMaxSizeRatio;   // 인정 최대크기 비율
    private double linenoLengthMinRatio; // 예상 lineno의 최소길이 비율
    private double linenoLengthMaxRatio; // 예상 lineno의 최대길이 비율

    // 생성자
    public LinenoFinder(double rectMargin, int charHeight, int charWidth, int linenoChars,
                       double linenoMarginRatioY, double linenoMarginRatioX,
                       String drawingTypeCode, double slidingRatioX, double slidingRatioY,
                       double acceptMinSizeRatio, double acceptMaxSizeRatio,
                       double linenoLengthMinRatio, double linenoLengthMaxRatio) {
        this.rectMargin = rectMargin;
        this.charHeight = charHeight;
        this.charWidth = charWidth;
        this.linenoChars = linenoChars;
        this.linenoMarginRatioY = linenoMarginRatioY;
        this.linenoMarginRatioX = linenoMarginRatioX;
        if (!isValidDrawingTypeCode(drawingTypeCode)) {
            throw new IllegalArgumentException("DrawingTypeCode must be one of: A, B, C, D");
        }
        this.drawingTypeCode = drawingTypeCode;
        this.slidingRatioX = slidingRatioX;
        this.slidingRatioY = slidingRatioY;
        this.acceptMinSizeRatio = acceptMinSizeRatio;
        this.acceptMaxSizeRatio = acceptMaxSizeRatio;
        this.linenoLengthMinRatio = linenoLengthMinRatio;
        this.linenoLengthMaxRatio = linenoLengthMaxRatio;
    }

    // 메인 메소드: lineno 찾기
    public List<LinenoResult> findAllLineno(BufferedImage image, Tesseract tesseract) {
        // 1. 탐색 대상 영역 계산
        Rectangle searchArea = calculateSearchArea(image);
        
        // 2. 윈도우 기반 탐색 및 결과 수집
        List<LinenoResult> preliminaryResults = scanWithSlidingWindow(image, tesseract, searchArea);
        
        // 3. 결과 머지 (겹치는 영역 처리)
        List<LinenoResult> mergedResults = mergeResults(preliminaryResults);
        
        // 4. lineno 조립 및 포맷 검증
        return assembleAndValidateLineno(mergedResults);
    }

    private Rectangle calculateSearchArea(BufferedImage image) {
        int margin = (int)(Math.min(image.getWidth(), image.getHeight()) * rectMargin);
        return new Rectangle(
            margin,
            margin,
            image.getWidth() - 2 * margin,
            image.getHeight() - 2 * margin
        );
    }

    private List<LinenoResult> scanWithSlidingWindow(BufferedImage image, Tesseract tesseract, 
                                                   Rectangle searchArea) {
        int windowWidth = (int)(charWidth * linenoChars * (1 + 2 * linenoMarginRatioX));
        int windowHeight = (int)(charHeight * (1 + 2 * linenoMarginRatioY));
        List<LinenoResult> results = new ArrayList<>();

        for (int y = searchArea.y; y <= searchArea.y + searchArea.height - windowHeight; 
             y += (int)(charHeight * slidingRatioY)) {
            for (int x = searchArea.x; x <= searchArea.x + searchArea.width - windowWidth; 
                 x += (int)(charWidth * slidingRatioX)) {
                
                BufferedImage window = image.getSubimage(x, y, windowWidth, windowHeight);
                List<LinenoResult> windowResults = performOCR(window, tesseract, x, y);
                
                for (LinenoResult result : windowResults) {
                    if (isValidSize(result)) {
                        results.add(result);
                    }
                }
            }
        }
        return results;
    }

    private List<LinenoResult> performOCR(BufferedImage window, Tesseract tesseract, 
                                        int offsetX, int offsetY) {
        List<LinenoResult> results = new ArrayList<>();
        try {
            String text = tesseract.doOCR(window);
            if (!text.trim().isEmpty()) {
                results.add(new LinenoResult(text.trim(), 
                    new Rectangle(offsetX, offsetY, window.getWidth(), window.getHeight())));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    private boolean isValidSize(LinenoResult result) {
        double height = result.getBounds().getHeight();
        return height >= charHeight * acceptMinSizeRatio && 
               height <= charHeight * acceptMaxSizeRatio;
    }

    private List<LinenoResult> mergeResults(List<LinenoResult> results) {
        List<LinenoResult> merged = new ArrayList<>();
        for (LinenoResult result : results) {
            boolean shouldAdd = true;
            for (int i = merged.size() - 1; i >= 0; i--) {
                if (hasOverlap(result, merged.get(i))) {
                    if (result.getConfidence() > merged.get(i).getConfidence()) {
                        merged.remove(i);
                    } else {
                        shouldAdd = false;
                    }
                }
            }
            if (shouldAdd) {
                merged.add(result);
            }
        }
        return merged;
    }

    private List<LinenoResult> assembleAndValidateLineno(List<LinenoResult> mergedResults) {
        List<List<LinenoResult>> wordGroups = groupNearbyWords(mergedResults);
        List<LinenoResult> finalResults = new ArrayList<>();

        for (List<LinenoResult> group : wordGroups) {
            if (isValidLinenoGroup(group)) {
                String assembledText = assembleLinenoText(group);
                if (isValidFormat(assembledText)) {
                    Rectangle bounds = calculateGroupBounds(group);
                    finalResults.add(new LinenoResult(assembledText, bounds));
                }
            }
        }

        return finalResults;
    }

    private List<List<LinenoResult>> groupNearbyWords(List<LinenoResult> words) {
        List<List<LinenoResult>> groups = new ArrayList<>();
        Set<LinenoResult> processed = new HashSet<>();

        for (LinenoResult word : words) {
            if (processed.contains(word)) continue;

            List<LinenoResult> currentGroup = new ArrayList<>();
            currentGroup.add(word);
            processed.add(word);

            for (LinenoResult otherword : words) {
                if (processed.contains(otherword)) continue;
                if (isNearby(word, otherword) && !hasOverlap(word, otherword)) {
                    currentGroup.add(otherword);
                    processed.add(otherword);
                }
            }

            if (isValidLinenoLength(calculateGroupLength(currentGroup))) {
                groups.add(currentGroup);
            }
        }

        return groups;
    }

    private boolean isNearby(LinenoResult word1, LinenoResult word2) {
        double maxDistance = charWidth * 2; // 적절한 거리 임계값 설정
        Rectangle r1 = word1.getBounds();
        Rectangle r2 = word2.getBounds();
        
        double centerY1 = r1.getCenterY();
        double centerY2 = r2.getCenterY();
        
        return Math.abs(centerY1 - centerY2) < charHeight 
            && Math.abs(r1.getMaxX() - r2.getMinX()) < maxDistance;
    }

    private String assembleLinenoText(List<LinenoResult> group) {
        // 왼쪽에서 오른쪽으로 정렬
        group.sort((a, b) -> Double.compare(a.getBounds().getX(), b.getBounds().getX()));
        
        StringBuilder result = new StringBuilder();
        LinenoResult prevWord = null;
        
        for (LinenoResult word : group) {
            if (prevWord != null) {
                int spaceCount = calculateSpaceCount(prevWord, word);
                result.append(" ".repeat(spaceCount));
            }
            result.append(word.getText());
            prevWord = word;
        }
        
        return result.toString();
    }

    private int calculateSpaceCount(LinenoResult prev, LinenoResult current) {
        double distance = current.getBounds().getX() - 
                         (prev.getBounds().getX() + prev.getBounds().getWidth());
        return (int) Math.round(distance / charWidth);
    }

    private boolean isValidLinenoGroup(List<LinenoResult> group) {
        double length = calculateGroupLength(group);
        return isValidLinenoLength(length) && !hasOverlappingWords(group);
    }

    private double calculateGroupLength(List<LinenoResult> group) {
        if (group.isEmpty()) return 0;
        
        double minX = group.stream()
            .mapToDouble(w -> w.getBounds().getX())
            .min()
            .getAsDouble();
        double maxX = group.stream()
            .mapToDouble(w -> w.getBounds().getX() + w.getBounds().getWidth())
            .max()
            .getAsDouble();
            
        return maxX - minX;
    }

    private boolean isValidLinenoLength(double length) {
        double expectedMinLength = linenoChars * charWidth * linenoLengthMinRatio;
        double expectedMaxLength = linenoChars * charWidth * linenoLengthMaxRatio;
        return length >= expectedMinLength && length <= expectedMaxLength;
    }

    private boolean hasOverlappingWords(List<LinenoResult> group) {
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                if (hasOverlap(group.get(i), group.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasOverlap(LinenoResult r1, LinenoResult r2) {
        Rectangle rect1 = r1.getBounds();
        Rectangle rect2 = r2.getBounds();
        
        Rectangle intersection = rect1.intersection(rect2);
        if (intersection.isEmpty()) return false;
        
        double overlapArea = intersection.getWidth() * intersection.getHeight();
        double area1 = rect1.getWidth() * rect1.getHeight();
        double area2 = rect2.getWidth() * rect2.getHeight();
        
        return (overlapArea / area1 >= 0.5) || (overlapArea / area2 >= 0.5);
    }

    private Rectangle calculateGroupBounds(List<LinenoResult> group) {
        int minX = group.stream()
            .mapToInt(w -> w.getBounds().x)
            .min()
            .getAsInt();
        int minY = group.stream()
            .mapToInt(w -> w.getBounds().y)
            .min()
            .getAsInt();
        int maxX = group.stream()
            .mapToInt(w -> w.getBounds().x + w.getBounds().width)
            .max()
            .getAsInt();
        int maxY = group.stream()
            .mapToInt(w -> w.getBounds().y + w.getBounds().height)
            .max()
            .getAsInt();
            
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    private static boolean isValidDrawingTypeCode(String code) {
        return "A".equals(code) || "B".equals(code) || "C".equals(code) || "D".equals(code);
    }

    protected boolean isValidFormat(String text) {
        // 도면 유형별 포맷 검증 로직
        switch (drawingTypeCode) {
            case "A":
                return isTypeAFormat(text);
            case "B":
                return isTypeBFormat(text);
            case "C":
                return isTypeCFormat(text);
            case "D":
                return isTypeDFormat(text);
            default:
                return false;
        }
    }

    // 도면 유형별 포맷 검증 메소드들
    private boolean isTypeAFormat(String text) {
        return true; // 실제 구현 필요
    }

    private boolean isTypeBFormat(String text) {
        return true; // 실제 구현 필요
    }

    private boolean isTypeCFormat(String text) {
        return true; // 실제 구현 필요
    }

    private boolean isTypeDFormat(String text) {
        return true; // 실제 구현 필요
    }
}

class LinenoResult {
    private String text;
    private Rectangle bounds;
    private double confidence;

    public LinenoResult(String text, Rectangle bounds) {
        this.text = text;
        this.bounds = bounds;
        this.confidence = 0.0; // OCR 결과에서 신뢰도 설정 필요
    }

    public String getText() { return text; }
    public Rectangle getBounds() { return bounds; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}