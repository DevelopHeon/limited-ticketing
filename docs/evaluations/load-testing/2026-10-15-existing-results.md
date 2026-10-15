# 기존 구매 대기 부하 측정 기록

**정리일: 2026-10-15. 기존 로컬 summary를 읽어 정리한 보고서이며 새 부하 테스트를 실행한 결과가 아니다.** 문서 정리 전 README의 1,000건 수락·약 580ms와 다른 실행의 600건 수행·401건 drop·약 4.89초 기록을 아래 원본에서 확인했다.

## 출처와 재현 한계

| 항목 | 확인 상태 |
|---|---|
| 원본 위치 | 로컬 `load-test/k6/results/`의 아래 JSON 3개, Git 제외 대상 |
| 문서 정리 시 코드 revision | `b545618252b58b41e2107828409418bc3fb215cb` |
| 측정에 사용한 코드 revision | 미확인, 문서 정리 시 revision과 동일하다고 간주하지 않음 |
| 실행 시각·머신·앱 설정·실제 VU 설정 | manifest 부재로 정확한 조건 미확인; 파일명은 원본 식별자로만 사용 |
| 입력 회원·재고·판매 ID 및 최종 DB 검증 결과 | 실행별 연결 근거 미확인 |
| 원본 보존 | 파일 내용을 변경하지 않고 SHA-256으로 식별 |

원본은 현재 작업 환경에서만 확인할 수 있다. 이 보고서만 checkout하면 JSON이 제공되는 것은 아니다. 후속 측정은 [실행 가이드](../../performance/load-testing.md)에 따라 별도 원본과 코드 revision, 환경, 파라미터, 최종 DB 검증 결과를 함께 남긴다.

## 확인한 지표

| 원본 파일 | 수행 iterations | accepted entries | dropped iterations | 진입 지연 p95 |
|---|---:|---:|---:|---:|
| `waiting-room-load-20260811154604732.json` | 1000 | 1000 | 0 | 579.76ms |
| `waiting-room-load-20260811161123797.json` | 600 | 600 | 401 | 4885.69ms |
| `waiting-room-smoke-20260811154604732.json` | 10 | 10 | 1 | 1358.56ms |

세 summary의 `waiting_room_key_failures`와 `waiting_room_unexpected_responses`는 모두 0이다. p99는 이 원본에 저장되어 있지 않다. accepted는 대기 진입 수락 지표이며 최종 구매 성공 건수가 아니다.

1,000건 실행은 수락 1,000건·drop 0건이지만 진입 p95 약 580ms로 당시 README의 목표 500ms를 초과했다. 다른 Load 실행은 600건 수행·401건 drop과 p95 약 4.89초를 기록했다. drop의 원인을 VU 부족이나 서버 병목 하나로 단정할 정보는 부족하다. Smoke도 drop 1건이 있어 무조건 통과한 기준 결과로 취급하지 않는다.

이 결과만으로 직접 예약보다 빨라졌다는 인과관계, 5,000/10,000건 처리 능력, 재고 보존이나 최종 구매 정합성을 입증하지 않는다. 비교 조건을 맞춘 재측정과 SQL 검증이 필요하다. 남은 인수 기준은 [로드맵](../../plans/roadmap.md#다음-검증)에 유지한다.

## 원본 식별자

| 파일 | SHA-256 |
|---|---|
| `waiting-room-load-20260811154604732.json` | `44ad9fc2b9d48fa3fa3bbb23615df8577ce1f39122a7f3cc4dd2fda896d832c3` |
| `waiting-room-load-20260811161123797.json` | `c0505c5958d3fb5bf1793b70c10acccc4fa583aa709b7dd019c3761b2d2ecd91` |
| `waiting-room-smoke-20260811154604732.json` | `5606416acf6303561acec1e7a1eaa5cc16129e6f0880ec1561b0b70e84030a21` |
