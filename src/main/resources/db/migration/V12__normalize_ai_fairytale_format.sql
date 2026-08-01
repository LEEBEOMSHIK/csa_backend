-- ai_fairytales.format 값 정규화.
--
-- format은 클라이언트가 보낸 값을 검증 없이 저장해 왔다(AiFairytaleService). 그 결과
-- 코드가 실제로 쓰는 'slide'/'video' 외에 대문자 레거시 값('IMAGE')이 한 건 섞여 있고,
-- 관리자 대시보드의 형식 분포에서 별개 항목으로 잡혔다.
--
-- 앱 로직은 "video가 아니면 슬라이드"로 동작하므로("video".equals(format)),
-- video가 아닌 모든 값은 의미상 이미 slide다. 이를 데이터에 반영한다.
-- 재유입 방지는 AiFairytaleService.normalizeFormat이 담당한다(거부가 아니라 정규화 --
-- 이 값들은 아직 설치되어 있는 구버전 앱이 보내던 것이라 400으로 막으면 생성이 깨진다).
update front.ai_fairytales
set format = case when lower(format) = 'video' then 'video' else 'slide' end
where format is null
   or format not in ('slide', 'video');
