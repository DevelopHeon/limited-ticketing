import json
import tempfile
import unittest
from unittest.mock import patch

from baseline_environment import Environment
from pathlib import Path

from baseline_results import RunArtifacts, evaluate_results, observations_complete


def summary():
    return {"metrics": {
        "iterations": {"values": {"count": 10}},
        "dropped_iterations": {"values": {"count": 0}},
        "checks": {"values": {"rate": 1}},
        "baseline_accepted": {"values": {"count": 10}},
        "baseline_reserved": {"values": {"count": 0}},
        "baseline_sold_out": {"values": {"count": 0}},
        "baseline_unexpected": {"values": {"count": 0}},
        "baseline_request_duration": {"values": {"p(95)": 100, "p(99)": 200}},
    }}


def verification():
    return {"purchases": {"PENDING_PAYMENT": 3},
            "queue": {"RESERVED": 3, "SOLD_OUT": 7},
            "inventory": {"available": 0, "reserved": 3, "sold": 0, "total": 3},
            "mismatchedConnections": 0, "duplicateConnections": 0,
            "duplicatePurchases": 0, "foreignMembers": 0,
            "redis": {"waiting": 0, "processing": 0},
            "completionSeconds": {"count": 10, "p95": 5}}


def evaluate(data=None, db=None, observations=True):
    return evaluate_results("waiting", 10, 3, data if data is not None else summary(),
                            db if db is not None else verification(), observations)


class BaselineResultTest(unittest.TestCase):
    def test_mismatched_api_database_is_rejected_before_cleanup(self):
        """API와 DB가 다른 환경을 가리키면 같은 숫자 ID라도 외부 구매를 취소하지 않는다."""
        with patch.dict('os.environ', {'DATABASE_URL': 'postgresql://test:test@localhost/test'}):
            environment = Environment('http://localhost', 'http://localhost', 'test')
        fixture = {'runId': 'owned', 'saleProductId': 1, 'members': [{'memberId': 7}]}
        snapshot = {'queue': {}, 'pendingPurchases': [{'purchaseId': 2, 'memberId': 7}]}
        with patch.object(environment, 'http', return_value=(200, {'data': {'saleProductId': 1, 'productName': 'foreign'}})):
            with self.assertRaisesRegex(RuntimeError, 'API and database fixture'):
                environment.cleanup(fixture, snapshot)

    def test_only_one_run_can_use_a_database(self):
        """같은 전용 DB를 동시에 측정해 서로의 부하가 섞이는 실행을 거부한다."""
        with tempfile.TemporaryDirectory() as folder, patch('tempfile.gettempdir', return_value=folder):
            with patch.dict('os.environ', {'DATABASE_URL': 'postgresql://test:test@localhost/test'}):
                first = Environment('http://localhost', 'http://localhost', 'test')
                second = Environment('http://localhost', 'http://localhost', 'test')
            with first.exclusive_run():
                with self.assertRaisesRegex(RuntimeError, 'already running'):
                    with second.exclusive_run():
                        pass
            with second.exclusive_run():
                pass

    def test_up_alone_does_not_prove_observability(self):
        """scrape UP만 있고 업무·자원 시계열이 없으면 관측 완료로 판정하지 않는다."""
        point = {'status': 'success', 'data': {'result': [{'values': [[1, '1']]}]}}
        complete = {name: point for name in ('up', 'httpCount', 'httpBuckets', 'heap', 'threads', 'hikari')}
        self.assertTrue(observations_complete(complete, 0))
        self.assertFalse(observations_complete({'up': point}, 0))
        self.assertFalse(observations_complete(complete, 1))
        complete['up'] = {'status': 'success', 'data': {'result': [{'values': [[1, '0']]}]}}
        self.assertFalse(observations_complete(complete, 0))

    def test_fast_acceptance_does_not_hide_slow_completion(self):
        """접수는 빨라도 최종 결과가 60초 목표를 넘으면 지연 목표 미달을 보고한다."""
        db = verification()
        db['completionSeconds']['p95'] = 83
        result = evaluate(db=db)
        self.assertEqual(result['latency'], 'FAIL')
        self.assertEqual(result['integrity'], 'PASS')

    def test_extra_iterations_fail_even_when_request_counts_match(self):
        """목표 HTTP 수만 맞고 빈 iteration이 추가된 실험은 유입 조건 실패로 남긴다."""
        data = summary()
        data['metrics']['iterations']['values']['count'] = 11
        self.assertEqual(evaluate(data)['workload'], 'FAIL')

    def test_cleanup_accepts_cancel_no_content(self):
        """구매 취소의 정상 204 응답을 실패로 오인하지 않고 실험을 정리한다."""
        with patch.dict('os.environ', {'DATABASE_URL': 'postgresql://test:test@localhost/test'}):
            environment = Environment('http://localhost', 'http://localhost', 'test')
        fixture = {'runId': 'owned', 'saleProductId': 1, 'members': [{'memberId': 7}]}
        snapshot = {'queue': {}, 'pendingPurchases': [{'purchaseId': 2, 'memberId': 7}]}
        with patch.object(environment, 'http', side_effect=[(200, {'data': {'saleProductId': 1, 'productName': 'baseline-owned', 'status': 'OPEN'}}), (204, None), (200, {})]):
            self.assertEqual(environment.cleanup(fixture, snapshot)['status'], 'PASS')

    def test_cleanup_rejects_foreign_member(self):
        """실험 소유 회원이 아닌 구매는 정리 대상으로 취소하지 않는다."""
        with patch.dict('os.environ', {'DATABASE_URL': 'postgresql://test:test@localhost/test'}):
            environment = Environment('http://localhost', 'http://localhost', 'test')
        with self.assertRaisesRegex(RuntimeError, 'outside the fixture'):
            environment.cleanup({'members': [{'memberId': 7}]}, {'queue': {}, 'pendingPurchases': [{'purchaseId': 2, 'memberId': 8}]})

    def test_expected_reservations_and_sold_out_are_valid(self):
        """수락 요청과 예약 및 품절 수량이 원장과 일치하면 정확성을 통과한다."""
        result = evaluate()
        self.assertEqual(result["workload"], "PASS")
        self.assertEqual(result["integrity"], "PASS")

    def test_dropped_iterations_fail_workload(self):
        """발생기가 보내지 못한 요청을 서버의 정상 처리로 계산하지 않는다."""
        data = summary()
        data["metrics"]["dropped_iterations"]["values"]["count"] = 1
        self.assertEqual(evaluate(data)["workload"], "FAIL")

    def test_unexpected_responses_fail_workload(self):
        """품절 이외의 오류를 정상 업무 결과로 숨기지 않는다."""
        data = summary()
        data["metrics"]["baseline_unexpected"]["values"]["count"] = 1
        self.assertEqual(evaluate(data)["workload"], "FAIL")

    def test_quantity_mismatch_fails_integrity(self):
        """예약 재고가 실제 구매 수와 다르면 총합이 맞아도 실패한다."""
        db = verification()
        db["inventory"] = {"available": 1, "reserved": 2, "sold": 0, "total": 3}
        self.assertEqual(evaluate(db=db)["integrity"], "FAIL")

    def test_duplicate_or_wrong_purchase_link_fails_integrity(self):
        """대기 결과가 잘못된 구매를 가리키면 수량이 같아도 실패한다."""
        for field in ("mismatchedConnections", "duplicateConnections", "foreignMembers", "duplicatePurchases"):
            with self.subTest(field=field):
                db = verification()
                db[field] = 1
                self.assertEqual(evaluate(db=db)["integrity"], "FAIL")

    def test_active_requests_are_incomplete(self):
        """drain 제한 뒤 미처리 요청을 완료된 결과로 보고하지 않는다."""
        db = verification()
        db["queue"] = {"RESERVED": 3, "SOLD_OUT": 6, "PROCESSING": 1}
        self.assertEqual(evaluate(db=db)["integrity"], "INCOMPLETE")

    def test_incomplete_summary_is_not_a_success(self):
        """실행이 중단되어 summary가 비어 있으면 성공으로 기본 처리하지 않는다."""
        self.assertEqual(evaluate({})["workload"], "INCOMPLETE")

    def test_latency_failure_does_not_hide_integrity(self):
        """지연 목표 미달과 업무 정확성 결과를 분리해 보고한다."""
        data = summary()
        data["metrics"]["baseline_request_duration"]["values"]["p(95)"] = 700
        result = evaluate(data)
        self.assertEqual(result["latency"], "FAIL")
        self.assertEqual(result["integrity"], "PASS")

    def test_missing_observations_are_incomplete(self):
        """관측 누락을 정상적인 영 지표로 바꾸지 않는다."""
        self.assertEqual(evaluate(observations=False)["observability"], "INCOMPLETE")

    def test_existing_run_is_not_overwritten(self):
        """동일 출력 경로를 다시 사용해 이전 실험을 덮어쓸 수 없다."""
        with tempfile.TemporaryDirectory() as folder:
            with RunArtifacts(Path(folder), "same", {"flow": "waiting"}):
                pass
            with self.assertRaises(FileExistsError):
                RunArtifacts(Path(folder), "same", {})

    def test_timeout_preserves_partial_artifacts(self):
        """시간 초과로 종료되어도 시작 정보와 원본 및 hash가 남는다."""
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(TimeoutError):
                with RunArtifacts(Path(folder), "timeout", {"flow": "waiting"}) as run:
                    (run.path / "k6.stdout.log").write_text("partial output")
                    raise TimeoutError("drain deadline")
            data = json.loads((Path(folder) / "timeout/manifest.json").read_text())
            self.assertEqual(data["status"], "INCOMPLETE")
            self.assertIn("k6.stdout.log", data["artifacts"])
            self.assertEqual(len(data["artifacts"]["k6.stdout.log"]["sha256"]), 64)
            self.assertNotIn("manifest.json", data["artifacts"])


if __name__ == "__main__":
    unittest.main()
