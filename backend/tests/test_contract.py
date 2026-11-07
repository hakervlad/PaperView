import asyncio
from datetime import datetime, timezone, timedelta
from io import BytesIO
import httpx
import pytest
from reportlab.pdfgen import canvas
from pypdf import PdfWriter

pytestmark = pytest.mark.asyncio

@pytest.fixture
def env(tmp_path):
    from paperview.app import create_app
    from paperview.config import Settings
    app = create_app(Settings(data_dir=tmp_path, app_token='test'))
    return app

async def client(app, secure=False):
    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='https://test' if secure else 'http://test', headers={'Authorization':'Bearer test'})

def pdf(text='Bayesian inference uses probability distributions.'):
    b=BytesIO(); c=canvas.Canvas(b); c.drawString(50,750,text); c.save(); return b.getvalue()

async def test_auth(env):
    async with await client(env) as c:
        assert (await c.get('/health')).json()=={'ok':True}
        for route in ['/v1/profile','/v1/knowledge','/v1/documents','/v1/feed','/docs','/openapi.json']:
            r=await c.get(route,headers={'Authorization':''})
            assert r.status_code in (401,404)
        env.state.settings.app_token=''
        assert (await c.get('/v1/profile')).status_code==401

async def test_profile_knowledge(env):
    async with await client(env) as c:
        p=(await c.get('/v1/profile')).json(); p['familiar_topics']=['Bayes',' bayes ']
        assert (await c.put('/v1/profile',json=p)).status_code==200
        ks=(await c.get('/v1/knowledge')).json(); assert len(ks)==1 and ks[0]['status']=='confirmed'
        k=ks[0]; r=await c.put('/v1/knowledge/'+k['id'],json={'name':'Bayesian inference','description':'Known','status':'learning'})
        assert r.json()['name']=='Bayesian inference'
        assert (await c.delete('/v1/knowledge/'+k['id'])).status_code==204
        p['lookback_days']=1; assert (await c.put('/v1/profile',json=p)).status_code==422

async def test_pdf_errors_and_consent(env):
    async with await client(env) as c:
        for data,code in [(b'', 'pdf_empty'),(b'%PDF broken','pdf_malformed'),(b'x'* (env.state.settings.max_upload+1),'upload_too_large')]:
            r=await c.post('/v1/documents',files={'file':('x.pdf',data,'application/pdf')})
            assert r.status_code>=400 and r.json()['error']['code']==code
        b=BytesIO(); w=PdfWriter(); w.add_blank_page(100,100); w.write(b)
        assert (await c.post('/v1/documents',files={'file':('x.pdf',b.getvalue())})).json()['error']['code']=='pdf_no_text'
        r=await c.post('/v1/documents',files={'file':('../../private.pdf',pdf())}); assert r.status_code==200
        d=r.json(); assert d['filename']=='private.pdf' and d['concepts']==[]
        assert (await c.post(f"/v1/documents/{d['id']}/analyze",json={'consent':False})).status_code==400
        assert (await c.post(f"/v1/documents/{d['id']}/analyze",json={'consent':True})).json()['error']['code']=='llm_missing_key'
        assert (await c.get('/v1/documents/'+d['id'])).json()['status']=='extracted'

async def test_candidate_confirmation_deletion(env):
    async def model(*args):
        return {'concepts':[{'name':'Bayes','description':'Probability inference','pages':[1],'connections':[]},{'name':'bayes','description':'Duplicate','pages':[1],'connections':[]}]}
    env.state.llm.complete=model
    async with await client(env,True) as c:
        d=(await c.post('/v1/documents',files={'file':('x.pdf',pdf())})).json()
        d=(await c.post('/v1/documents/'+d['id']+'/analyze',json={'consent':True})).json()
        assert len(d['concepts'])==1
        assert env.state.knowledge.retrieve('Bayes')==[]
        k=d['concepts'][0]
        await c.put('/v1/knowledge/'+k['id'],json={'name':k['name'],'description':k['description'],'status':'confirmed'})
        assert env.state.knowledge.retrieve('Bayes')[0].id==k['id']
        await c.delete('/v1/documents/'+d['id'])
        ks=(await c.get('/v1/knowledge')).json(); assert len(ks)==1 and ks[0]['document_ids']==[]

async def test_key_cleartext(env):
    async with await client(env) as c:
        for extra in [{},{'X-Forwarded-Proto':'https'}]:
            r=await c.post('/v1/llm/test',headers={'X-LLM-Key':'secret',**extra})
            assert r.json()['error']['code']=='https_required'

async def test_feed_reuse_failure_concurrency(env):
    calls=[]
    async def search(*args):
        calls.append(1); await asyncio.sleep(.03); return []
    env.state.sources.search=search
    async with await client(env) as c:
        assert (await c.get('/v1/feed')).json()['status']=='empty'; assert not calls
        results=await asyncio.gather(*[c.post('/v1/feed/generate',json={'timezone':'Europe/Berlin'}) for _ in range(3)])
        assert len(calls)==1
        good=next(r.json() for r in results if r.status_code==200)
        assert good['status']=='success' and good['items']==[]
        assert (await c.post('/v1/feed/generate',json={'timezone':'Europe/Berlin'})).json()==good
        env.state.feed.now=lambda:datetime.now(timezone.utc)+timedelta(days=1)
        async def fail(*args):
            from paperview.errors import ServiceError
            raise ServiceError('source_unavailable','Quellen nicht erreichbar.',503)
        env.state.sources.search=fail
        assert (await c.post('/v1/feed/generate',json={'timezone':'Europe/Berlin'})).status_code==503
        assert (await c.get('/v1/feed')).json()['date']==good['date']
        assert (await c.post('/v1/feed/generate',json={'timezone':'Europe/Berlin'})).status_code==429


async def test_deleted_explicit_topic_is_not_resurrected(env):
    async with await client(env) as c:
        p=(await c.get('/v1/profile')).json(); p['familiar_topics']=['Bayes']
        await c.put('/v1/profile',json=p)
        concept=(await c.get('/v1/knowledge')).json()[0]
        await c.put('/v1/knowledge/'+concept['id'],json={'name':'Bayesian inference','description':'Updated','status':'confirmed'})
        await c.delete('/v1/knowledge/'+concept['id'])
        p=(await c.get('/v1/profile')).json()
        assert p['familiar_topics']==[]
        p['bachelor_subject']='Biologie'
        await c.put('/v1/profile',json=p)
        assert (await c.get('/v1/knowledge')).json()==[]


async def test_demoted_topic_not_reconfirmed_by_profile_save(env):
    async with await client(env) as c:
        p=(await c.get('/v1/profile')).json(); p['familiar_topics']=['Bayes']
        await c.put('/v1/profile',json=p)
        concept=(await c.get('/v1/knowledge')).json()[0]
        await c.put('/v1/knowledge/'+concept['id'],json={'name':'Bayes','description':'Noch lernen','status':'learning'})
        p=(await c.get('/v1/profile')).json()
        assert p['familiar_topics']==[]
        await c.put('/v1/profile',json=p)
        assert (await c.get('/v1/knowledge')).json()[0]['status']=='learning'
