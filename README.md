# gopid-spring
graph oriented p&amp;id
 push 테스트
# 도면파일 도면이미지, 심볼이미지 관리 
- 원본도면파일은 PDF 만 고려
- 원본심볼파일 원본생성은 고려대상제외
- 도면PDF 저장시 페이지별 도면이미지파일 분리생성, 페이지별 썸네일 생성관리
- 도면이미지 분리생성 : PDFBox 이용
- 썸네일 생성은 성능고려 : PDFBox 과거버전


# API 목록
파일보기요청 : /apis/files/view/IDXXXXXXX
  - 응답 : file 내용, response-header : mimeType(Tika로 즉시 생성), 

임시업로드파일생성요청 : /apis/files/tempupload
  - 조건 : POST Multipart
  - 응답 : {fileIdd:UUID}

파일생성요청 : /apis/files/upload
  - 조건 : POST Multipart
  - 응답 : {fileIdd:UUID}

파일다운로드요청 : /apis/files/download/IDXXXXXX
  - 응답 : file 내용, response-header : content-disposition : attachment;filename

프로젝트목록요청 : /project/list
  - 조건 : {고객명:"clientName1", 프로젝트명:"projectName1", 표준명:"standard1", 산업유형:"industryType1"}
  - 응답 : {프로젝트ID:"projectId1", }

도면목록요청 : /blueprint/list
 - 조건 : {프로젝트:"projectId",프로젝트명:"projectName1",도면ID:"blueprintId1",도면명:"blueprintName1"}
 - 응답 : [{도면ID:"blueprintId1",도면명:"blueprintName1",프로젝트ID:"proejctId1",프로젝트명:"projectName1",등록자:"registor",등록일시:"registDate"
          ,[{시트ID:"sheetId1",시트명:"sheetName1",시트파일ID:"sheetFileId1",썸네일파일ID:"thumbnailFileId1"}]
          ,도면파일ID:UUID,도면파일명파일메타:{fileMaeta1-jsonobject}}]

도면저장요청 : /blueprint/save
 - 조건 : {도면ID:"blueprindId1",도면명:"blueprindName1",프로젝트ID:"proejctId1",프로젝트명:"projectName1",파일명:"fileName",파일ID}
 - 응답 :

도면상세요청 : /blueprint/detail/UUID

심볼목록 : /symbol/list?standard=*&projectId=&projectName=*&id=*
심볼목록응답 : 심볼ID, 표준명












