# gopid-spring
graph oriented p&amp;id

# 도면파일 도면이미지, 심볼이미지 관리 
원본도면파일은 PDF 만 고려
원본심볼파일 원본생성은 고려대상제외
도면PDF 저장시 페이지별 도면이미지파일 분리생성, 페이지별 썸네일 생성관리
도면이미지 분리생성 : PDFBox 이용
썸네일 생성은 성능고려 : PDFBox 과거버전


# API 목록
파일요청 : /filestorage/IDXXXXXXX
  - 응답 : mimeType은 Tika로 즉시 생성, file 내용

파일저장요청 : /filestorage/save
  - 조건 : POST Multipart
  - 응답 : {fileIdd:UUID}
  
도면목록요청 : /blueprint/list
 - 조건 : {프로젝트:"projectId",프로젝트명:"projectName1",도면ID:"blueprintId1",도면명:"blueprintName1"}
 - 응답 : [{도면ID:"blueprintId1",도면명:"blueprintName1",프로젝트ID:"proejctId1",프로젝트명:"projectName1",등록자:"registor",등록일시:"registDate"
 -        ,[{시트ID:"sheetId1",시트명:"sheetName1",시트파일ID:"sheetFileId1",썸네일파일ID:"thumbnailFileId1"}]
 -        ,도면파일UUID:UUID,파일메타:{fileMaeta1-jsonobject}}]

도면저장요청 : /blueprint/save
 - 조건 : {도면ID:"blueprindId1",도면명:"blueprindName1",프로젝트ID:"proejctId1",프로젝트명:"projectName1",파일명:"fileName",파일ID}
 - 응답 :

도면상세요청 : /blueprint/detail/UUID

심볼목록 : /symbol/list?standard=*&projectId=&projectName=*&id=*
심볼목록응답 : 심볼ID, 표준명






