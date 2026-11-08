import json
import ssl
import httpx
import pytest
from paperview.config import Settings
from paperview.errors import ServiceError
from paperview.llm import LLMService

pytestmark = pytest.mark.asyncio

@pytest.mark.parametrize('status,provider_code,expected', [
    (401, 'invalid_api_key', 'llm_invalid_key'),
    (403, None, 'llm_forbidden'),
    (402, None, 'llm_insufficient_quota'),
    (429, 'insufficient_quota', 'llm_insufficient_quota'),
    (429, 'rate_limit_exceeded', 'llm_rate_limit'),
    (400, 'unsupported_parameter', 'llm_configuration'),
    (404, 'model_not_found', 'llm_model_unavailable'),
    (503, None, 'llm_server_error'),
])
async def test_provider_error_is_classified_without_leaking_body(monkeypatch, status, provider_code, expected):
    async def dns(host): return ['8.8.8.8']
    monkeypatch.setattr('paperview.llm.public_addresses', dns)
    response = {'error': {'code': provider_code, 'message': 'PRIVATE_PROVIDER_TEXT key=secret'}}
    llm = LLMService(Settings(), httpx.MockTransport(lambda r: httpx.Response(status, json=response)))
    with pytest.raises(ServiceError) as error:
        await llm.complete(('fixture-key', 'fixture-model', 'https://api.openai.com/v1'), 'test', {})
    assert error.value.code == expected
    assert 'PRIVATE_PROVIDER_TEXT' not in error.value.message
    assert 'secret' not in error.value.message

@pytest.mark.parametrize('host', ['api.openai.com', 'api.deepseek.com'])
async def test_model_payload_uses_supported_token_parameter(monkeypatch, host):
    async def dns(host): return ['8.8.8.8']
    monkeypatch.setattr('paperview.llm.public_addresses', dns)
    def handle(request):
        payload = json.loads(request.content)
        if host == 'api.openai.com':
            assert 'max_completion_tokens' in payload
            assert 'max_tokens' not in payload
            assert 'temperature' not in payload
        else:
            assert payload['max_tokens'] == 3000
        assert request.headers['Authorization'] == 'Bearer fixture-key'
        assert request.headers['Host'] == host
        return httpx.Response(200, json={'choices': [{'message': {'content': '{"ok":true}'}}]})
    llm = LLMService(Settings(allowed_hosts=(host,)), httpx.MockTransport(handle))
    assert await llm.complete(('fixture-key', 'fixture-model', f'https://{host}/v1'), 'test', {}) == {'ok': True}

@pytest.mark.parametrize('failure,code', [(httpx.ConnectError('private'), 'llm_unavailable'), (httpx.ReadTimeout('private'), 'llm_timeout')])
async def test_provider_transport_error(monkeypatch, failure, code):
    async def dns(host): return ['8.8.8.8']
    monkeypatch.setattr('paperview.llm.public_addresses', dns)
    def handle(request): raise failure
    with pytest.raises(ServiceError) as error:
        await LLMService(Settings(), httpx.MockTransport(handle)).complete(('fixture','fixture','https://api.openai.com/v1'), 'test', {})
    assert error.value.code == code

async def test_tls_verification_failure_is_explicit(monkeypatch):
    async def dns(host): return ['8.8.8.8']
    monkeypatch.setattr('paperview.llm.public_addresses', dns)
    def handle(request):
        raise httpx.ConnectError('private host') from ssl.SSLCertVerificationError('private details')
    with pytest.raises(ServiceError) as error:
        await LLMService(Settings(), httpx.MockTransport(handle)).complete(('fixture','fixture','https://api.openai.com/v1'), 'test', {})
    assert error.value.code == 'llm_tls'
    assert 'private' not in error.value.message
