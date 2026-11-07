import asyncio
import json
import os
from pathlib import Path
import sys
from pydantic import ValidationError
from .errors import ServiceError
from .models import Document, Extraction
from .storage import uid
from .knowledge import norm

PDF_ERRORS={'pdf_encrypted':'Verschlüsselte PDF bitte zuerst entschlüsseln.', 'pdf_pages':'PDF enthält zu viele Seiten.', 'pdf_text_limit':'PDF enthält zu viel Text.', 'pdf_no_text':'Kein extrahierbarer Text. Für gescannte PDFs ist OCR erforderlich.', 'pdf_malformed':'PDF ist beschädigt oder ungültig.', 'pdf_timeout':'Zeitlimit der PDF-Verarbeitung erreicht.'}
class PDFService:
    def __init__(self,store,settings,knowledge,llm): self.store,self.settings,self.knowledge,self.llm=store,settings,knowledge,llm
    def document(self,id):
        d=self.store.require('documents',id)
        return Document(**d,concepts=[k for k in self.knowledge.all() if id in k.document_ids])
    async def upload(self,file):
        id=uid(); path=self.settings.data_dir/(id+'.pdf'); total=0
        try:
            with open(path,'xb') as out:
                os.chmod(path,0o600)
                while chunk:=await file.read(65536):
                    total+=len(chunk)
                    if total>self.settings.max_upload: raise ServiceError('upload_too_large','PDF überschreitet das Größenlimit.',413)
                    out.write(chunk)
            if not total: raise ServiceError('pdf_empty','Die PDF-Datei ist leer.')
            proc=await asyncio.create_subprocess_exec(sys.executable,str(Path(__file__).with_name('pdf_worker.py')),str(path),str(self.settings.max_pages),str(self.settings.max_text),stdout=asyncio.subprocess.PIPE,stderr=asyncio.subprocess.DEVNULL)
            try:
                output,_=await asyncio.wait_for(proc.communicate(),self.settings.pdf_timeout)
            except (TimeoutError,asyncio.CancelledError) as exc:
                proc.kill(); await proc.communicate()
                if isinstance(exc,asyncio.CancelledError): raise
                raise ServiceError('pdf_timeout',PDF_ERRORS['pdf_timeout']) from None
            try: result=json.loads(output)
            except ValueError: result={'error':'pdf_malformed'}
            if 'error' in result: raise ServiceError(result['error'],PDF_ERRORS[result['error']])
            chunks=[]
            for p in result['pages']:
                for start in range(0,len(p['text']),2000):
                    chunks.append({'id':uid(),'document_id':id,'page':p['page'],'text':p['text'][start:start+2000]})
            if len(chunks)>self.settings.max_chunks: raise ServiceError('pdf_text_limit',PDF_ERRORS['pdf_text_limit'])
            for x in chunks: self.store.put('chunks',x['id'],x)
            filename=Path((file.filename or 'document.pdf').replace('\\','/')).name[:200]
            self.store.put('documents',id,{'id':id,'filename':filename,'status':'extracted','message':None})
            return self.document(id)
        finally:
            path.unlink(missing_ok=True); await file.close()
    async def analyze(self,id,consent,options):
        d=self.document(id)
        if not consent: raise ServiceError('consent_required','Externe Analyse benötigt Ihre ausdrückliche Zustimmung.')
        if d.status=='analyzed': return d
        chunks=[x for x in self.store.all('chunks') if x['document_id']==id]
        # Explicitly bounded external context; evidence pages must occur in the sent subset.
        context=[{'page':x['page'],'text':x['text']} for x in chunks[:12]]
        answer=await self.llm.complete(options,'Schema: {"concepts":[{"name":str,"description":str,"pages":[int],"connections":[concept_name]}]}. Maximal 30 Begriffe. Nur im Text belegte Begriffe mit Seitenbeleg extrahieren. Keine Bestätigung von Vorwissen.',context)
        try:
            parsed=Extraction.model_validate(answer)
            allowed={x['page'] for x in context}
            names={norm(c.name) for c in parsed.concepts}
            for c in parsed.concepts:
                if not set(c.pages)<=allowed or any(norm(n) not in names for n in c.connections): raise ValueError()
        except (ValidationError,ValueError): raise ServiceError('llm_invalid_json','Ungültige Begriffe oder Seitenbelege in Modellantwort.',502) from None
        mapping={}
        for c in parsed.concepts:
            k=self.knowledge.add(c.name,c.description,document=id); mapping[norm(c.name)]=k.id
            e={'id':uid(),'document_id':id,'concept_id':k.id,'pages':c.pages}
            self.store.put('evidence',e['id'],e)
        for c in parsed.concepts:
            k=next(k for k in self.knowledge.all() if k.id==mapping[norm(c.name)])
            k.connections=sorted(set(k.connections)|{mapping[norm(n)] for n in c.connections if norm(n)!=norm(c.name)})
            self.store.put('knowledge',k.id,k.model_dump())
        self.store.put('documents',id,{'id':id,'filename':d.filename,'status':'analyzed','message':'Begriffe sind unbestätigte Kandidaten. Nur ein begrenzter Textauszug wurde analysiert.'})
        return self.document(id)
