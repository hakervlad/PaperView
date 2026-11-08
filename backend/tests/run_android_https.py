"""Opt-in real-phone TLS integration. Never touches the personal application/data.

Run from backend venv, with JAVA_HOME and ANDROID_HOME configured:
    python backend/tests/run_android_https.py
Requires one connected/unlocked ADB device, openssl, and the Android build tools.
Paper discovery and model output are explicit fixtures; HTTP, TLS, SQLite and PDF are real.
"""
import base64
import os
from pathlib import Path
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import time
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[2]
APP = 'com.example.aiknowledgefeed.regression'
TEST_APP = APP + '.test'


def run(*args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True, **kwargs)


def main():
    adb = shutil.which('adb') or str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb')
    devices = run(adb, 'devices', capture_output=True, text=True).stdout.splitlines()[1:]
    if len([d for d in devices if d.endswith('\tdevice')]) != 1:
        raise RuntimeError('Exactly one authorized ADB device is required.')
    run(ROOT / 'gradlew', ':app:assembleDebug', ':app:assembleDebugAndroidTest', '-PpaperViewDeviceTests=true', '--console=plain', '--max-workers=2', cwd=ROOT)
    with tempfile.TemporaryDirectory(prefix='paperview-https-regression-') as temporary:
        p = Path(temporary)
        os.chmod(p, 0o700)
        run('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', p/'ca.key', '-out', p/'ca.pem', '-days', '2', '-subj', '/CN=PaperView temporary test CA', '-addext', 'basicConstraints=critical,CA:TRUE', capture_output=True)
        run('openssl', 'req', '-newkey', 'rsa:2048', '-nodes', '-keyout', p/'server.key', '-out', p/'server.csr', '-subj', '/CN=localhost', capture_output=True)
        (p/'ext').write_text('subjectAltName=DNS:localhost,IP:127.0.0.1\nbasicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\n')
        run('openssl', 'x509', '-req', '-in', p/'server.csr', '-CA', p/'ca.pem', '-CAkey', p/'ca.key', '-CAcreateserial', '-out', p/'server.pem', '-days', '2', '-extfile', p/'ext', capture_output=True)
        certificate = ssl.PEM_cert_to_DER_cert((p/'ca.pem').read_text())
        ca = base64.b64encode(certificate).decode()
        with socket.socket() as s:
            s.bind(('127.0.0.1', 0)); port = s.getsockname()[1]
        url = f'https://localhost:{port}/'
        environment = dict(os.environ, PAPERVIEW_TEST_SERVER='1', PAPERVIEW_DATA_DIR=str(p/'data'), PYTHONPATH=os.pathsep.join([str(ROOT/'backend'), str(ROOT/'backend/tests')]), LLM_API_KEY='')
        command = [sys.executable, '-m', 'uvicorn', 'mock_server:create_mock_app', '--factory', '--host', '127.0.0.1', '--port', str(port), '--ssl-keyfile', str(p/'server.key'), '--ssl-certfile', str(p/'server.pem'), '--no-access-log', '--no-proxy-headers']
        server = None
        reverse = False
        installed = False
        def stop():
            nonlocal server
            if server is not None:
                server.terminate()
                try: server.wait(timeout=10)
                except subprocess.TimeoutExpired: server.kill(); server.wait()
                server = None
        def start():
            nonlocal server
            server = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            context = ssl.create_default_context(cafile=str(p/'ca.pem'))
            for _ in range(100):
                if server.poll() is not None: raise RuntimeError('Test backend failed to start.')
                try:
                    with urlopen(url+'health', context=context, timeout=1) as response:
                        if response.status == 200: return
                except OSError: pass
                time.sleep(0.1)
            raise RuntimeError('Test backend startup timed out.')
        def verify(phase):
            result = run(adb, 'shell', 'am', 'instrument', '-w', '-e', 'class', 'com.example.aiknowledgefeed.BackendDeviceIntegrationTest', '-e', 'backend_url', url, '-e', 'backend_test_ca', ca, '-e', 'backend_phase', phase, TEST_APP+'/androidx.test.runner.AndroidJUnitRunner', capture_output=True, text=True, timeout=180)
            print(result.stdout)
            if 'OK (1 test)' not in result.stdout: raise RuntimeError(f'Device integration failed: {phase}')
        try:
            start()
            run(adb, 'reverse', f'tcp:{port}', f'tcp:{port}'); reverse = True
            installed = True
            run(adb, 'install', '-r', ROOT/'app/build/outputs/apk/debug/app-debug.apk')
            run(adb, 'install', '-r', ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
            verify('create')
            run(adb, 'shell', 'am', 'force-stop', APP)
            stop(); start()
            verify('restart')
            print('PASS: verified HTTPS, PDF, persistent knowledge and next-day personalization on device (mock model/source).')
        finally:
            stop()
            if reverse: run(adb, 'reverse', '--remove', f'tcp:{port}')
            if installed:
                subprocess.run([adb, 'uninstall', TEST_APP], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                subprocess.run([adb, 'uninstall', APP], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

if __name__ == '__main__':
    main()
