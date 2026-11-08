import asyncio
from datetime import date
import httpx
import pytest
from paperview.models import Paper, Profile

pytestmark=pytest.mark.asyncio

def paper(**kw):
    return Paper(id='p1',title='Bayes for biology',authors=['A'],abstract='Bayes estimates uncertainty.',published_at='2026-11-01',field='biology',source='arxiv',url='https://arxiv.org/abs/2611.00001',arxiv_id='2611.00001',review_status='preprint',**kw)

async def test_sources_dedupe():
    from paperview.sources import dedupe
    a=paper(doi='10.1/ABC'); b=a.model_copy(update={'id':'p2','doi':'https://doi.org/10.1/abc','source':'openalex'})
    assert len(dedupe([a,b]))==1
    assert len(dedupe([a,a.model_copy(update={'id':'p3','doi':None})]))==1

@pytest.mark.parametrize('status',[429,503])
async def test_source_failures(tmp_path,status):
    from paperview.sources import Sources
    from paperview.storage import Store
    from paperview.config import Settings
    from paperview.errors import ServiceError
    s=Sources(Store(tmp_path),Settings(data_dir=tmp_path),httpx.MockTransport(lambda r:httpx.Response(status)))
    with pytest.raises(ServiceError): await s.search(Profile(),date(2026,11,7))

async def test_sources_success_empty(tmp_path):
    from paperview.sources import Sources
    from paperview.storage import Store
    from paperview.config import Settings
    def handle(r):
        if r.url.host=='api.openalex.org':
            assert 'from_publication_date' in r.url.params['filter']
            return httpx.Response(200,json={'results':[]})
        assert 'submittedDate' in r.url.params['search_query']
        return httpx.Response(200,text='<feed xmlns="http://www.w3.org/2005/Atom"/>')
    s=Sources(Store(tmp_path),Settings(data_dir=tmp_path),httpx.MockTransport(handle))
    assert await s.search(Profile(),date(2026,11,7))==[]

async def test_personalization_actual_ids(tmp_path):
    from paperview.storage import Store
    from paperview.knowledge import KnowledgeService
    from paperview.personalization import Personalizer
    from paperview.errors import ServiceError
    k=KnowledgeService(Store(tmp_path)); known=k.add('Bayes',status='confirmed'); k.add('biology',status='candidate')
    captured=[]
    class Model:
        async def complete(self,options,instruction,data):
            captured.append(data)
            return dict(summary='Abstract: uncertainty.',concept_ids=[known.id],new_insight='Estimation',connection='Bayes',relevance='Biology',takeaway='Uncertainty')
    p=Personalizer(k,Model())
    item=await p.explain(paper(),Profile(bachelor_subject='Biologie'),None)
    assert item.prior_knowledge[0].id==known.id and item.source_url==paper().url and item.abstract_based
    assert captured[0]['profile']['bachelor_subject']=='Biologie'
    k.delete(known.id)
    with pytest.raises(ServiceError): await p.explain(paper(),Profile(),None)

@pytest.mark.parametrize('status,code',[(401,'llm_invalid_key'),(402,'llm_insufficient_quota'),(429,'llm_rate_limit'),(404,'llm_model_unavailable'),(302,'llm_unavailable'),(200,'llm_invalid_json')])
async def test_llm_http(monkeypatch,status,code):
    from paperview.llm import LLMService
    from paperview.config import Settings
    from paperview.errors import ServiceError
    async def dns(host): return ['8.8.8.8']
    monkeypatch.setattr('paperview.llm.public_addresses',dns)
    llm=LLMService(Settings(),httpx.MockTransport(lambda r:httpx.Response(status,json={})))
    with pytest.raises(ServiceError) as e: await llm.complete(('secret','model','https://api.openai.com/v1'),'test',{})
    assert e.value.code==code

@pytest.mark.parametrize('url',['http://api.openai.com/v1','https://localhost/v1','https://api.openai.com:444/v1','https://api.openai.com@127.0.0.1/v1'])
async def test_provider_url_allowlist(url):
    from paperview.llm import LLMService
    from paperview.config import Settings
    from paperview.errors import ServiceError
    with pytest.raises(ServiceError) as e:
        await LLMService(Settings()).complete(('test','model',url),'test',{})
    assert e.value.code=='provider_forbidden'

async def test_no_prior_connection_is_explicit(tmp_path):
    from paperview.storage import Store
    from paperview.knowledge import KnowledgeService
    from paperview.personalization import Personalizer
    class Model:
        async def complete(self,*args):
            return dict(title='Neue Methode',summary='Noch kein direkter Wissensbezug; zunächst Grundlagen.',concept_ids=[],new_insight='Grundlagen aus dem Abstract.',connection='',relevance='Einführung',takeaway='Ein neues Thema.')
    item=await Personalizer(KnowledgeService(Store(tmp_path)),Model()).explain(paper(),Profile(),None)
    assert item.prior_knowledge==[] and item.connection=='' and item.title=='Neue Methode'

async def test_different_confirmed_profiles_produce_different_contexts(tmp_path):
    from paperview.storage import Store
    from paperview.knowledge import KnowledgeService
    from paperview.personalization import Personalizer
    class ContextModel:
        async def complete(self, options, instruction, data):
            known=data['confirmed_knowledge']
            return dict(summary='Du kennst '+known[0]['name'],concept_ids=[known[0]['id']],new_insight='Testaussage aus Abstract',connection='Verbindung mit '+known[0]['name'],relevance='Testkontext',takeaway='Neue Einsicht')
    a=KnowledgeService(Store(tmp_path/'a')); b=KnowledgeService(Store(tmp_path/'b'))
    a.add('Bayes',status='confirmed');b.add('API',status='confirmed')
    source=paper().model_copy(update={'abstract':'Bayes probability API interfaces'})
    first=await Personalizer(a,ContextModel()).explain(source,Profile(),None)
    second=await Personalizer(b,ContextModel()).explain(source,Profile(),None)
    assert first.summary!=second.summary
    assert [k.name for k in first.prior_knowledge]==['Bayes']
    assert [k.name for k in second.prior_knowledge]==['API']
