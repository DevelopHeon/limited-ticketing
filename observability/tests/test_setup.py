import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path


class ObservabilitySecretSetupTest(unittest.TestCase):
    def setUp(self):
        source = Path(__file__).resolve().parents[2] / 'scripts/setup-observability.local.sh'
        self.assertTrue(source.is_file(), 'Local secret setup script is missing')
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.script = self.root / 'scripts/setup-observability.local.sh'
        self.script.parent.mkdir()
        shutil.copy2(source, self.script)
        self.secret = self.root / '.local/observability/secrets/grafana_admin_password'

    def run_setup(self):
        return subprocess.run(['bash', str(self.script)], cwd=self.root.parent,
                              capture_output=True, text=True)

    def test_generates_private_secret_without_printing_it(self):
        """첫 실행은 비밀번호를 생성하고 소유자만 읽도록 보호하며 출력에 노출하지 않는다."""
        result = self.run_setup()
        self.assertEqual(result.returncode, 0, result.stderr)
        password = self.secret.read_text().strip()
        self.assertGreaterEqual(len(password), 32)
        self.assertEqual(stat.S_IMODE(self.secret.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(self.secret.parent.stat().st_mode), 0o700)
        self.assertNotIn(password, result.stdout + result.stderr)

    def test_existing_password_survives_repeated_setup(self):
        """준비 명령을 반복해도 기존 로그인 비밀번호를 바꾸지 않고 파일 권한만 보정한다."""
        self.secret.parent.mkdir(parents=True)
        self.secret.write_text('existing-test-password\n')
        self.secret.chmod(0o644)
        for _ in range(2):
            result = self.run_setup()
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertNotIn('existing-test-password', result.stdout + result.stderr)
        self.assertEqual(self.secret.read_text(), 'existing-test-password\n')
        self.assertEqual(stat.S_IMODE(self.secret.stat().st_mode), 0o600)

    def test_empty_existing_secret_is_rejected_without_replacing_it(self):
        """기존 비밀 파일이 비어 있으면 새 암호로 덮어쓰지 않고 명확하게 실패한다."""
        self.secret.parent.mkdir(parents=True)
        self.secret.touch()
        result = self.run_setup()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Empty', result.stderr)
        self.assertEqual(self.secret.read_bytes(), b'')


if __name__ == '__main__':
    unittest.main()
