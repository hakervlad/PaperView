import pytest
import httpx
from datetime import datetime, timezone, timedelta
from test_contract import pdf
from paperview.app import create_app
from paperview.config import Settings
from paperview.models import Paper

@pytest.mark.asyncio
async def test_pdf_to_next_day_personalized_feed(tmp_path):
    app = create_app(Settings(data_dir=tmp_path, app_token='test'))
    calls=[]
    async def model(options, instruction, data):
        if isinstance(data,list):
            return {'concepts':[{'name':'Bayes','description':'Probability and biomarkers','pages':[1],'connections':[]}]}
        known=data['confirmed_knowledge']; calls.append(known)
        return dict(summary='Du kennst '+', '.join(k['name'] for k in known)+'. Das Paper erweitert diese Konzepte.',concept_ids=[k['id'] for k in known],new_insight='Bayes unterstützt Biomarker.',connection='Bayes verbindet Unsicherheit mit Biomarkern.' if known else '',relevance='Methodische Erweiterung.',takeaway='Biomarker verwenden Bayes für Unsicherheit.')
    app.state.llm.complete=model
    day=[0]
    async def search(*args):
        return [Paper(id='p'+str(day[0]),title='Bayes Biomarker '+str(day[0]),authors=['A'],abstract='Bayes probability biomarkers',published_at='2026-11-07',field='Wissenschaft',source='test',url='https://example.org/p'+str(day[0]),review_status='unknown',doi='10.1000/'+str(day[0]))]
    app.state.sources.search=search
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='https://test',headers={'Authorization':'Bearer test'}) as c:
        assert (await c.get('/openapi.json')).status_code==200
        doc=(await c.post('/v1/documents',files={'file':('thesis.pdf',pdf())})).json()
        analyzed=(await c.post('/v1/documents/'+doc['id']+'/analyze',json={'consent':True})).json()
        concept=analyzed['concepts'][0]
        assert not app.state.knowledge.retrieve('Bayes')
        assert (await c.put('/v1/knowledge/'+concept['id'],json={'name':concept['name'],'description':concept['description'],'status':'confirmed'})).status_code==200
        f=(await c.post('/v1/feed/generate',json={'timezone':'Europe/Berlin'})).json()
        assert f['items'][0]['prior_knowledge'][0]['id']==concept['id']
        saved=(await c.post('/v1/papers/p0/understood')).json()
        assert saved['description']=='Biomarker verwenden Bayes für Unsicherheit.'
        assert concept['id'] in saved['connections']
        assert (await c.post('/v1/papers/p0/understood')).json()['id']==saved['id']
        day[0]=1
        app.state.feed.now=lambda: datetime.now(timezone.utc)+timedelta(days=1)
        assert (await c.post('/v1/feed/generate',json={'timezone':'Europe/Berlin'})).status_code==200
        assert saved['id'] in {k['id'] for k in calls[-1]}
