import json
import re
import unicodedata
from .models import Knowledge, Profile
from .storage import uid
from .errors import ServiceError

def norm(s):
    return ' '.join(unicodedata.normalize('NFKC',s).casefold().split())
def words(s):
    return set(re.findall(r'\w{3,}',norm(s)))

class KnowledgeService:
    def __init__(self, store): self.store=store
    def all(self): return [Knowledge.model_validate(x) for x in self.store.all('knowledge')]
    def add(self,name,description='',status='candidate',document=None,paper=None):
        existing=next((k for k in self.all() if norm(k.name)==norm(name)),None)
        k=existing or Knowledge(id=uid(),name=name,description=description,status=status)
        if status=='confirmed': k.status='confirmed'
        if document and document not in k.document_ids: k.document_ids.append(document)
        if paper: k.source_paper_id=paper
        self.store.put('knowledge',k.id,k.model_dump()); return k
    def profile(self): return Profile.model_validate(self.store.get('profile','single',{}))
    def set_profile(self,p):
        for topic in p.familiar_topics: self.add(topic,status='confirmed')
        self.store.put('profile','single',p.model_dump()); return p
    def update(self,id,update):
        k=Knowledge.model_validate(self.store.require('knowledge',id))
        if any(x.id!=id and norm(x.name)==norm(update.name) for x in self.all()):
            raise ServiceError('duplicate_concept','Dieser Begriff ist bereits vorhanden.',409)
        profile = self.profile()
        profile.familiar_topics = [update.name if norm(name) == norm(k.name) else name for name in profile.familiar_topics if norm(name) != norm(k.name) or update.status == 'confirmed']
        self.store.put('profile','single',profile.model_dump())
        k= k.model_copy(update=update.model_dump()); self.store.put('knowledge',id,k.model_dump()); return k
    def delete(self,id):
        removed = Knowledge.model_validate(self.store.require('knowledge',id))
        profile = self.profile()
        profile.familiar_topics = [name for name in profile.familiar_topics if norm(name) != norm(removed.name)]
        self.store.put('profile','single',profile.model_dump())
        self.store.delete('knowledge',id)
        for k in self.all():
            if id in k.connections:
                k.connections.remove(id); self.store.put('knowledge',k.id,k.model_dump())
        for d in self.store.all('evidence'):
            if d['concept_id']==id: self.store.delete('evidence',d['id'])
    def retrieve(self, query, relations=True):
        tokens=words(query)
        ranked=sorted([(len(tokens & words(k.name+' '+k.description)),k) for k in self.all() if k.status=='confirmed'],key=lambda p:p[0],reverse=True)
        found=[k for score,k in ranked if score>0][:6]
        if relations:
            ids={id for k in found for id in k.connections}
            found += [k for k in self.all() if k.id in ids and k.status=='confirmed' and k.id not in {x.id for x in found}][:2]
        return found[:8]
    def understood(self,paper):
        existing=next((k for k in self.all() if k.source_paper_id==paper.id),None)
        explanation = None
        with self.store.connect() as db:
            rows = db.execute("SELECT feed FROM days WHERE state='success' ORDER BY day DESC").fetchall()
        for row in rows:
            explanation = next((x for x in json.loads(row[0])['items'] if x['paper_id'] == paper.id), None)
            if explanation: break
        if explanation is None:
            raise ServiceError('explanation_missing','Bitte zuerst eine personalisierte Erklärung zu diesem Paper erstellen.',409)
        k = existing or self.add(paper.title[:200], explanation['takeaway'], status='confirmed', paper=paper.id)
        k.status = 'confirmed'
        k.connections = sorted(set(k.connections) | {x['id'] for x in explanation['prior_knowledge'] if self.store.get('knowledge',x['id'])})
        self.store.put('knowledge', k.id, k.model_dump())
        return k
    def remove_document(self,id):
        self.store.require('documents',id)
        for k in self.all():
            if id in k.document_ids:
                k.document_ids.remove(id)
                if not k.document_ids and k.status!='confirmed' and not k.source_paper_id: self.delete(k.id)
                else: self.store.put('knowledge',k.id,k.model_dump())
        for kind in ('chunks','evidence'):
            for x in self.store.all(kind):
                if x['document_id']==id: self.store.delete(kind,x['id'])
        self.store.delete('documents',id)
