package com.infocz.util.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.Word;
import javax.imageio.ImageIO;
import java.io.File;

import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.util.*;
import java.util.stream.Collectors;

public class LinenoFinder {
    private static final Logger log = LoggerFactory.getLogger(LinenoFinder.class);

    // 멤버 변수
    private double rectMargin;
    private int charHeight;
    private int charWidth;
    private int linenoChars;
    private double linenoMarginRatioY;
    private String drawingTypeCode;
    private double slidingRatioY;
    private double acceptMinSizeRatio;
    private double acceptMaxSizeRatio;
    private double linenoLengthMinRatio;
    private double linenoLengthMaxRatio;

    // 생성자
    public LinenoFinder(double rectMargin, int charHeight, int charWidth, int linenoChars,
                       double linenoMarginRatioY, String drawingTypeCode, double slidingRatioY,
                       double acceptMinSizeRatio, double acceptMaxSizeRatio,
                       double linenoLengthMinRatio, double linenoLengthMaxRatio) {
        if (!isValidDrawingTypeCode(drawingTypeCode)) {
            throw new IllegalArgumentException("DrawingTypeCode must be one of: A, B, C, D");
        }
        this.rectMargin = rectMargin;
        this.charHeight = charHeight;
        this.charWidth = charWidth;
        this.linenoChars = linenoChars;
        this.linenoMarginRatioY = linenoMarginRatioY;
        this.drawingTypeCode = drawingTypeCode;
        this.slidingRatioY = slidingRatioY;
        this.acceptMinSizeRatio = acceptMinSizeRatio;
        this.acceptMaxSizeRatio = acceptMaxSizeRatio;
        this.linenoLengthMinRatio = linenoLengthMinRatio;
        this.linenoLengthMaxRatio = linenoLengthMaxRatio;
    }

    private boolean isValidDrawingTypeCode(String code) {
        return Arrays.asList("A", "B", "C", "D").contains(code);
    }

    // 메인 메소드: lineno 찾기
    public List<LinenoResult> findAllLineno(BufferedImage image, Tesseract tesseract) {
        // 1. 탐색 영역 계산
        Rectangle searchArea = calculateSearchArea(image);
        
        // 2. 윈도우 크기 계산
        int windowHeight = (int)(charHeight * (1 + 2 * linenoMarginRatioY));
        int slidingStep = (int)(windowHeight * slidingRatioY);
        
        // 3. 슬라이딩 윈도우로 탐색
        List<Word> allWords = new ArrayList<>();
        for (int y = searchArea.y; y < searchArea.y + searchArea.height - windowHeight; y += slidingStep) {
        	//if( y < 1580 || y > 1950) { continue;}
            Rectangle window = new Rectangle(
                searchArea.x, y, 
                searchArea.width, windowHeight
            );
            
            BufferedImage windowImage = image.getSubimage(
                window.x, window.y, 
                window.width, window.height
            );
            
            // OCR 실행
            tesseract.setPageSegMode(7); // Line
            List<Word> windowWords = tesseract.getWords(windowImage, TessPageIteratorLevel.RIL_WORD);
            try {
            //    log.info("y:" + y + " height : " + windowHeight + " words:" + windowWords);
            //    ImageIO.write(windowImage, "png", new File("D:/pgm_data/debug/abc/ZZ_" + String.format("%05d", y) + ".png"));
            } catch(Exception e) {
                System.out.println("image error");
            }
            
            // 4. 크기 필터링
            List<Word> validWords = filterWordsBySize(windowWords, window);
            allWords.addAll(validWords);
        }
        
        // 5. 결과 병합
        List<Word> mergedWords = mergeOverlappingWords(allWords);
        
        // 6. 조합 생성
        List<WordCombination> combinations = generateCombinations(mergedWords);
        
        // 7, 8. 텍스트 조립 및 포맷 검증
        List<LinenoResult> results = new ArrayList<>();
        for (WordCombination combination : combinations) {
            String assembledText = assembleText(combination);
            if (isValidFormat(assembledText)) {
                results.add(new LinenoResult(
                    assembledText,
                    combination.getBoundingBox(),
                    combination.getConfidence()
                ));
            }
        }
        
        return results;
    }

    private Rectangle calculateSearchArea(BufferedImage image) {
        int margin = (int)(Math.min(image.getWidth(), image.getHeight()) * rectMargin);
        return new Rectangle(
            margin, margin,
            image.getWidth() - 2 * margin,
            image.getHeight() - 2 * margin
        );
    }

    private List<Word> filterWordsBySize(List<Word> words, Rectangle window) {
        return words.stream()
            .filter(word -> {
                Rectangle bbox = word.getBoundingBox();
                double height = bbox.getHeight();
                return height >= charHeight * acceptMinSizeRatio &&
                       height <= charHeight * acceptMaxSizeRatio;
            })
            .map(word -> {
                // 전체 이미지 좌표계로 변환
                Rectangle bbox = word.getBoundingBox();
                bbox.translate(window.x, window.y);
                return word;
            })
            .collect(Collectors.toList());
    }

    private List<Word> mergeOverlappingWords(List<Word> words) {
        List<Word> result = new ArrayList<>();
        List<Word> sorted = new ArrayList<>(words);
        sorted.sort((w1, w2) -> {
            int yCompare = Double.compare(w1.getBoundingBox().getY(), w2.getBoundingBox().getY());
            if (yCompare != 0) return yCompare;
            return Double.compare(w1.getBoundingBox().getX(), w2.getBoundingBox().getX());
        });

        for (Word word : sorted) {
            boolean merged = false;
            for (int i = 0; i < result.size(); i++) {
                if (calculateOverlap(word.getBoundingBox(), result.get(i).getBoundingBox()) > 0.3) {
                    Rectangle wordBox = word.getBoundingBox();
                    Rectangle existingBox = result.get(i).getBoundingBox();
                    
                    // 너비 차이가 30% 이내인지 확인
                    double widthDiffRatio = Math.abs(wordBox.width - existingBox.width) / (double)Math.max(wordBox.width, existingBox.width);
                    
                    if (widthDiffRatio <= 0.3) {
                        // 너비가 비슷한 경우 confidence로 판단
                        if (word.getConfidence() > result.get(i).getConfidence()) {
                            result.set(i, word);
                        }
                    } else {
                        // 너비 차이가 30%를 초과하는 경우 더 큰 것을 선택
                        if (wordBox.width > existingBox.width) {
                            result.set(i, word);
                        }
                    }
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(word);
            }
        }
        return result;
    }

    private double calculateOverlap(Rectangle r1, Rectangle r2) {
        int overlapX = Math.max(0, Math.min(r1.x + r1.width, r2.x + r2.width) - Math.max(r1.x, r2.x));
        int overlapY = Math.max(0, Math.min(r1.y + r1.height, r2.y + r2.height) - Math.max(r1.y, r2.y));
        double overlapArea = overlapX * overlapY;
        double minArea = Math.min(r1.width * r1.height, r2.width * r2.height);
        return overlapArea / minArea;
    }

    private List<WordCombination> generateCombinations(List<Word> words) {
        List<WordCombination> combinations = new ArrayList<>();
        
        // Y축 기준으로 그룹화 (바운딩 박스의 수직 오버랩 확인)
        Map<Integer, List<Word>> wordsByY = new HashMap<>();
        
        // 모든 단어들을 검사
        for (Word word : words) {
            Rectangle bbox = word.getBoundingBox();
            int wordCenterY = bbox.y + bbox.height / 2;
            
            // 이 단어가 들어갈 수 있는 그룹 찾기
            boolean foundGroup = false;
            for (Map.Entry<Integer, List<Word>> entry : wordsByY.entrySet()) {
                Word firstWordInGroup = entry.getValue().get(0);
                Rectangle firstBbox = firstWordInGroup.getBoundingBox();
                int groupCenterY = firstBbox.y + firstBbox.height / 2;
                
                // 중심점 Y좌표 차이가 문자 높이의 25% 이내인 경우 같은 그룹으로 간주
                if (Math.abs(wordCenterY - groupCenterY) <= charHeight * 0.1) {
                    entry.getValue().add(word);
                    foundGroup = true;
                    break;
                }
            }
            
            // 적절한 그룹을 찾지 못했다면 새 그룹 생성
            if (!foundGroup) {
                List<Word> newGroup = new ArrayList<>();
                newGroup.add(word);
                wordsByY.put(wordsByY.size(), newGroup);
            }
        }
        
        // 각 그룹 내에서 X축 기준으로 정렬하고 조합 생성
        for (List<Word> row : wordsByY.values()) {
            // X축 순서로 정렬
            row.sort(Comparator.comparingDouble(w -> w.getBoundingBox().getX()));
            
            // 조합 생성
            for (int start = 0; start < row.size(); start++) {
                List<Word> currentCombination = new ArrayList<>();
                Word startWord = row.get(start);
                currentCombination.add(startWord);
                
                double minLength = linenoChars * charWidth * linenoLengthMinRatio;
                double maxLength = linenoChars * charWidth * linenoLengthMaxRatio;
                
                // 초기 조합의 길이는 첫 단어의 너비
                double totalWidth = startWord.getBoundingBox().getWidth();
                
                if (totalWidth >= minLength && totalWidth <= maxLength) {
                    combinations.add(new WordCombination(new ArrayList<>(currentCombination)));
                }
                
                // 다음 단어들을 조합에 추가
                for (int next = start + 1; next < row.size(); next++) {
                    Word nextWord = row.get(next);
                    
                    // 이전 단어와의 수직 위치 차이 확인
                    Rectangle prevBbox = currentCombination.get(currentCombination.size()-1).getBoundingBox();
                    Rectangle nextBbox = nextWord.getBoundingBox();
                    int prevCenterY = prevBbox.y + prevBbox.height / 2;
                    int nextCenterY = nextBbox.y + nextBbox.height / 2;
                    
                    // 수직 위치 차이가 너무 크면 조합 중단
                    if (Math.abs(nextCenterY - prevCenterY) > charHeight * 0.25) {
                        break;
                    }
                    
                    // 단어 사이의 간격 계산
                    double gap = nextBbox.getX() - (prevBbox.getX() + prevBbox.getWidth());
                    
                    // 새로운 총 너비 계산
                    double newTotalWidth = totalWidth + gap + nextBbox.getWidth();
                    
                    // maxLength를 초과하면 이 시작점에서의 조합 생성을 중단
                    if (newTotalWidth > maxLength) {
                        break;
                    }
                    
                    currentCombination.add(nextWord);
                    totalWidth = newTotalWidth;
                    
                    // 조합의 길이가 허용 범위 내에 있으면 결과에 추가
                    if (totalWidth >= minLength && totalWidth <= maxLength) {
                        combinations.add(new WordCombination(new ArrayList<>(currentCombination)));
                    }
                }
            }
        }
        
        log.debug("Generated {} combinations", combinations.size());
         return combinations;
    }

    private String assembleText(WordCombination combination) {
        StringBuilder result = new StringBuilder();
        List<Word> words = combination.getWords();
        
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) {
                // 이전 단어와 현재 단어 사이의 거리 계산
                double gap = words.get(i).getBoundingBox().getX() -
                           (words.get(i-1).getBoundingBox().getX() + words.get(i-1).getBoundingBox().getWidth());
                
                // 공백 문자 수 계산 (최소 0 보장)
                int spaces = Math.max(0, (int)(gap / charWidth));
                if (spaces > 0) {
                    result.append(" ".repeat(spaces));
                }
                log.debug("Gap between words: {} pixels, Adding {} spaces", gap, spaces);
            }
            result.append(words.get(i).getText());
        }
        
        return result.toString();
    }

    protected boolean isValidFormat(String text) {
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

    private boolean isTypeAFormat(String text) {
        String pattern = "\\d{1,2}\"-[A-Z]{1,2}-\\d{4}-[0-9A-Z]{5}";
        return text.matches(pattern);
    }

    private boolean isTypeBFormat(String text) {
        //1"-0- -1CS2 (ET)
        // 숫자문자{1,3}"-숫자문자{1,2}-임의문자{1,4}-숫자문자{4}공백{1}임의문자{1-3}
        String pattern = "[0-9A-Z/]{1,4}\"-[0-9A-Z]{1,2}-[\\s\\S]{1,4}-[0-9A-Z]{1,5}(\\s[\\S]{1,3})?";
        if(text.matches(pattern)) {
        	System.out.println("통과:"+text);
        	return true;
        } 
        System.out.println("거부:"+text);
        return false;
    }
    
    private boolean isTypeCFormat(String text) {
    	String pattern = "[0-9/]{1,3}\"-[0-9A-Z]{1,2}-\\s-[0-9A-Z]{1,5}-\\s(-[\\s\\S./()]{1,8})?";
        if(text.matches(pattern)) {
        	System.out.println("통과:"+text);
        	return true;
        } 
        System.out.println("거부:"+text);
        return false;
    }

    private boolean isTypeDFormat(String text) {
        // 구현 예정
        return false;
    }

    // 결과를 담기 위한 내부 클래스
    public static class LinenoResult {
        private final String text;
        private final Rectangle boundingBox;
        private final float confidence;

        public LinenoResult(String text, Rectangle boundingBox, float confidence) {
            this.text = text;
            this.boundingBox = boundingBox;
            this.confidence = confidence;
        }

        public String getText() { return text; }
        public Rectangle getBoundingBox() { return boundingBox; }
        public float getConfidence() { return confidence; }
    }

    // Word 조합을 관리하기 위한 내부 클래스
    private static class WordCombination {
        private final List<Word> words;

        public WordCombination(List<Word> words) {
            this.words = words;
        }

        public List<Word> getWords() { return words; }

        public Rectangle getBoundingBox() {
            if (words.isEmpty()) return new Rectangle();
            
            Rectangle result = new Rectangle(words.get(0).getBoundingBox());
            for (int i = 1; i < words.size(); i++) {
                result.add(words.get(i).getBoundingBox());
            }
            return result;
        }

        public float getConfidence() {
            return (float) words.stream()
                .mapToDouble(Word::getConfidence)
                .average()
                .orElse(0.0);
        }
    }
    
    public static void main(String[] arg) {
    	String pattern = "[0-9/]{1,3}\"-[0-9A-Z]{1,2}-\\s-[0-9A-Z]{1,5}-\\s(-[\\s\\S./()]{1,8})?";

    	String[] tests = {
    	    "1/\"-FO- -AB31F- -(1.8/65)",
    	    "1/\"-FO- -AB31F-",
    	    "1/\"-FO- -AB31F- -(165)" 
    	};

    	for (String test : tests) {
    	    System.out.println(test + " : " + test.matches(pattern));
    	    // 매칭 실패시 어느 부분에서 실패하는지 확인
    	    for (int i = 1; i <= test.length(); i++) {
    	        String partial = test.substring(0, i);
    	        System.out.println(i + ": " + partial);
    	    }
    	}
    }

}