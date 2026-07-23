---
name: ISSUE_TEMPLATE
about: 기본 이슈 템플릿
title: ''
labels: ''
assignees: ''

---

## 💡 작업 배경 및 핵심 로직 (What & Why)

> 무엇을 변경했고, 왜 그렇게 설계했는지 핵심을 짚어주세요.
> 
- RDBMS 비관적 락을 사용할 경우 응답 지연이 발생하여, Redis의 Pub/Sub을 활용한 Redisson 분산 락으로 동시성 제어 방식을 변경했습니다.

## 🧪 테스트 결과 (Proof)

> 면접관(또는 리뷰어)이 코드가 정상 작동함을 믿을 수 있는 근거를 남겨주세요.
> 
- [ ]  Postman API 응답 200 OK 캡처 첨부
- [ ]  JMeter 1,000건 동시 요청 시 데드락 발생 없음 확인 (성능 지표 캡처)

## 🔗 연관 이슈(선택)
