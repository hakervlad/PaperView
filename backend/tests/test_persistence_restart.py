"""Real SQLite persistence across a reconstructed server (no in-memory fallback)."""
import httpx
import pytest
from paperview.app import create_app
from paperview.config import Settings
from paperview.models import Paper
from test_contract import pdf

@pytest.mark.asyncio
async def test_profile_pdf_knowledge_and_feed_survive_server_restart(tmp_path):
    settings = Settings(data_dir=tmp_path, app_token='restart-fixture')
    app = create_app(settings)
    async def model(options, instruction, data):
        if isinstance(data, list):
            return {'concepts': [{'name': 'Bayes', 'description': 'Wahrscheinlichkeit bei Biomarkern', 'pages': [1], 'connections': []}]}
        known = data['confirmed_knowledge']
        return dict(summary='Du kennst Bayes. Das Testabstract ergänzt Biomarker.', concept_ids=[x['id'] for x in known], new_insight='Bayes bei Biomarkern', connection='Dein Bayes-Vorwissen erklärt Unsicherheit.', relevance='Bekannte Statistik anwenden.', takeaway='Biomarker mit Bayes auswerten.')
    async def search(*args):
        return [Paper(id='restart-paper', title='Bayes Biomarker', abstract='Bayes probability biomarkers', authors=['Fixture'], published_at='2026-11-07', field='Wissenschaft', source='fixture', url='https://example.org/paper', review_status='unknown')]
    app.state.llm.complete=model; app.state.sources.search=search
    headers={'Authorization': 'Bearer restart-fixture'}
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='https://fixture', headers=headers) as c:
        profile={'bachelor_subject': 'Informatik', 'familiar_topics': ['Statistik']}
        assert (await c.put('/v1/profile', json=profile)).status_code == 200
        document=(await c.post('/v1/documents', files={'file': ('fixture.pdf', pdf())})).json()
        candidate=(await c.post(f"/v1/documents/{document['id']}/analyze", json={'consent': True})).json()['concepts'][0]
        assert candidate['status'] == 'candidate'
        assert (await c.put('/v1/knowledge/'+candidate['id'], json={k:candidate[k] for k in ('name','description')} | {'status':'confirmed'})).status_code == 200
        feed=(await c.post('/v1/feed/generate', json={'timezone':'Europe/Berlin'})).json()
        assert candidate['id'] in [x['id'] for x in feed['items'][0]['prior_knowledge']]
        saved=(await c.post('/v1/papers/restart-paper/understood')).json()
    restarted=create_app(settings)
    assert restarted.state.store.path == app.state.store.path
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=restarted), base_url='https://fixture', headers=headers) as c:
        assert (await c.get('/v1/profile')).json()['bachelor_subject'] == 'Informatik'
        assert (await c.get('/v1/documents')).json()[0]['id'] == document['id']
        knowledge=(await c.get('/v1/knowledge')).json()
        assert {candidate['id'],saved['id']} <= {x['id'] for x in knowledge}
        assert (await c.get('/v1/feed')).json()['items'] == feed['items']
        assert (await c.post('/v1/papers/restart-paper/understood')).json()['id'] == saved['id']
        assert len((await c.get('/v1/knowledge')).json()) == len(knowledge)
        assert saved['id'] in {x.id for x in restarted.state.knowledge.retrieve('Biomarker')}
