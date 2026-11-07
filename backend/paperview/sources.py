import asyncio
from datetime import date, timedelta
import hashlib
import json
import re
import time
from urllib.parse import urlsplit
from xml.etree import ElementTree as ET
import httpx
from .errors import ServiceError
from .models import Paper

def doi(value):
    return re.sub(r'^(https?://(dx\.)?doi.org/|doi:\s*)','',value or '',flags=re.I).strip().lower() or None

def arxiv(value):
    m=re.search(r'(\d{4}\.\d{4,5}|[a-z-]+(?:\.[A-Z]{2})?/\d{7})(?:v\d+)?',value or '',re.I)
    return m.group(1).lower() if m else None

def identifiers(p):
    return [x for x in [('doi:'+doi(p.doi)) if p.doi else None,('arxiv:'+arxiv(p.arxiv_id)) if arxiv(p.arxiv_id) else None] if x] or ['title:'+re.sub(r'\W+','',p.title.casefold())]

def dedupe(papers):
    groups=[]
    for p in papers:
        keys=set(identifiers(p)); matches=[g for g in groups if keys & g[0]]
        if matches:
            first=matches[0]; first[0].update(keys)
            # Carry identifiers across sources for durable seen detection.
            first[1].doi=first[1].doi or p.doi; first[1].arxiv_id=first[1].arxiv_id or p.arxiv_id
            for g in matches[1:]: first[0].update(g[0]); groups.remove(g)
        else: groups.append([keys,p])
    return [g[1] for g in groups]

def pid(value): return hashlib.sha256(value.encode()).hexdigest()[:32]
def safe_url(value):
    u=urlsplit(value or '')
    return value if u.scheme=='https' and u.hostname and not u.username and not u.password else None

def abstract(index):
    if not isinstance(index,dict): return ''
    pairs=sorted((n,w) for w,ns in index.items() for n in ns if isinstance(n,int) and 0<=n<10000)
    return ' '.join(w for _,w in pairs)[:16000]

class Sources:
    def __init__(self,store,settings,transport=None): self.store,self.settings,self.transport=store,settings,transport
    async def request(self,url,params,headers=None):
        try:
            async with httpx.AsyncClient(transport=self.transport,timeout=20,follow_redirects=False,trust_env=False) as c:
                async with c.stream('GET',url,params=params,headers=headers) as r:
                    if r.status_code==429: raise ServiceError('source_rate_limit','Quellenlimit erreicht. Bitte später erneut versuchen.',503)
                    if r.status_code!=200: raise ServiceError('source_unavailable','Wissenschaftliche Quelle derzeit nicht verfügbar.',503)
                    b=bytearray()
                    async for chunk in r.aiter_bytes():
                        b.extend(chunk)
                        if len(b)>4*1024*1024: raise ValueError()
                    return bytes(b)
        except (httpx.HTTPError,ValueError): raise ServiceError('source_unavailable','Quellenantwort nicht verfügbar oder ungültig.',503) from None
    async def openalex(self,query,start,end):
        params={'filter':f'from_publication_date:{start},to_publication_date:{end}','per_page':30,'sort':'publication_date:desc'}
        if query: params['search']=query
        headers={'Authorization':'Bearer '+self.settings.openalex_key} if self.settings.openalex_key else {}
        data=json.loads(await self.request('https://api.openalex.org/works',params,headers)); papers=[]
        for w in data['results']:
            loc=w.get('primary_location') or {}; ids=w.get('ids') or {}
            url=safe_url(loc.get('landing_page_url')) or safe_url(w.get('doi')) or safe_url(w['id'])
            a=arxiv(' '.join(str(x.get('landing_page_url') or '') for x in w.get('locations',[])))
            papers.append(Paper(id=pid(w['id']),doi=doi(w.get('doi')),arxiv_id=a,title=w['title'][:500],authors=[x['author']['display_name'] for x in w.get('authorships',[])][:100],abstract=abstract(w.get('abstract_inverted_index')),published_at=w['publication_date'],field=(w.get('primary_topic') or {}).get('display_name',''),source='openalex',url=url or w['id'],pdf_url=safe_url(loc.get('pdf_url')),review_status='unknown'))
        return papers
    async def arxiv(self,query,start,end):
        # SQLite gate shared by processes: reserve start slot, extend until request completes.
        owner=False
        while not owner:
            now=time.time()
            with self.store.transaction() as db:
                row=db.execute("SELECT next FROM limits WHERE name='arxiv'").fetchone()
                wait=max(0,row[0]-now) if row else 0
                if wait==0:
                    db.execute("INSERT OR REPLACE INTO limits VALUES('arxiv',?)",(now+60,)); owner=True
            if not owner: await asyncio.sleep(min(wait,3))
        try:
            terms=[w for w in re.findall(r'\w+',query) if w.upper() not in ('OR','AND')][:12]
            search=('('+' OR '.join('all:'+w for w in terms)+') AND ') if terms else ''
            search+=f'submittedDate:[{start:%Y%m%d}0000 TO {end:%Y%m%d}2359]'
            raw=await self.request('https://export.arxiv.org/api/query',{'search_query':search,'max_results':30,'sortBy':'submittedDate','sortOrder':'descending'})
            if b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper(): raise ValueError()
            root=ET.fromstring(raw); ns={'a':'http://www.w3.org/2005/Atom','x':'http://arxiv.org/schemas/atom'}; out=[]
            for e in root.findall('a:entry',ns):
                get=lambda name:(e.findtext(name,default='',namespaces=ns)).strip()
                aid=arxiv(get('a:id'))
                if not aid: raise ValueError()
                out.append(Paper(id=pid('arxiv:'+aid),doi=doi(get('x:doi')),arxiv_id=aid,title=' '.join(get('a:title').split()),authors=[x.findtext('a:name',namespaces=ns) or '' for x in e.findall('a:author',ns)],abstract=get('a:summary')[:16000],published_at=get('a:published')[:10],field=(e.find('a:category',ns).get('term','') if e.find('a:category',ns) is not None else ''),source='arxiv',url='https://arxiv.org/abs/'+aid,pdf_url='https://arxiv.org/pdf/'+aid,review_status='preprint'))
            return out
        finally:
            with self.store.connect() as db: db.execute("UPDATE limits SET next=? WHERE name='arxiv'",(time.time()+3,))
    async def search(self,profile,today):
        start=today-timedelta(days=profile.lookback_days)
        query=' '.join([profile.bachelor_subject,*profile.focus_areas,*profile.interests])[:300]
        try:
            # Both must succeed: outage must not be persisted as an empty issue.
            papers=await self.openalex(query,start,today)+await self.arxiv(query,start,today)
            return dedupe([p for p in papers if start<=date.fromisoformat(p.published_at[:10])<=today])
        except (ValueError,KeyError,TypeError,ET.ParseError):
            raise ServiceError('source_invalid','Ungültige Antwort der wissenschaftlichen Quelle.',503) from None
