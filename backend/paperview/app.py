import hmac
from contextlib import asynccontextmanager
from fastapi import FastAPI, APIRouter, Depends, Request, UploadFile, File, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException
from .config import Settings
from .errors import ServiceError
from .storage import Store
from .models import Profile, Knowledge, KnowledgeUpdate, Document, Paper, Feed, Consent, Generate
from .knowledge import KnowledgeService
from .llm import LLMService
from .pdf import PDFService
from .sources import Sources
from .personalization import Personalizer
from .feed import FeedService

def create_app(settings=None):
    settings=settings or Settings()
    app=FastAPI(docs_url=None,redoc_url=None,openapi_url=None)
    store=Store(settings.data_dir); knowledge=KnowledgeService(store); llm=LLMService(settings)
    sources=Sources(store,settings); pdf=PDFService(store,settings,knowledge,llm)
    feed=FeedService(store,knowledge,sources,Personalizer(knowledge,llm))
    for name,value in locals().copy().items():
        if name in ('settings','store','knowledge','llm','sources','pdf','feed'): setattr(app.state,name,value)

    @app.exception_handler(ServiceError)
    async def service_error(request,exc):
        return JSONResponse({'error':{'code':exc.code,'message':exc.message}},status_code=exc.status)
    @app.exception_handler(RequestValidationError)
    async def validation_error(request,exc):
        return JSONResponse({'error':{'code':'validation_error','message':'Ungültige Eingabe. Bitte Felder und Werte prüfen.'}},status_code=422)
    @app.exception_handler(HTTPException)
    async def http_error(request,exc):
        return JSONResponse({'error':{'code':'http_error','message':'Anfrage nicht möglich.'}},status_code=exc.status_code)
    @app.exception_handler(Exception)
    async def internal_error(request,exc):
        return JSONResponse({'error':{'code':'internal_error','message':'Interner Fehler. Bitte später erneut versuchen.'}},status_code=500)

    async def auth(request:Request):
        token=settings.app_token
        if not token or not hmac.compare_digest(request.headers.get('authorization','').encode(), ('Bearer '+token).encode()):
            raise ServiceError('unauthorized','Gültiges App-Token erforderlich.',401)

    def options(request:Request):
        secure=request.url.scheme=='https'
        peer=request.client.host if request.client else ''
        if peer in settings.trusted_proxies and request.headers.get('x-forwarded-proto')=='https': secure=True
        key=request.headers.get('x-llm-key')
        if key is not None and not secure: raise ServiceError('https_required','Ein eigener Modellschlüssel darf nur über HTTPS übertragen werden.')
        if not secure and peer not in ('127.0.0.1','::1','testclient'):
            raise ServiceError('https_required','Modellzugriff benötigt HTTPS oder lokalen Loopback-Zugriff.')
        return (key if key is not None else settings.llm_key,request.headers.get('x-llm-model',settings.llm_model),request.headers.get('x-llm-base-url',settings.llm_base_url))

    @app.get('/health')
    async def health(): return {'ok':True}
    router=APIRouter(prefix='/v1',dependencies=[Depends(auth)])
    @router.get('/profile',response_model=Profile)
    async def get_profile(): return knowledge.profile()
    @router.put('/profile',response_model=Profile)
    async def put_profile(p:Profile): return knowledge.set_profile(p)
    @router.get('/knowledge',response_model=list[Knowledge])
    async def get_knowledge(): return knowledge.all()
    @router.put('/knowledge/{id}',response_model=Knowledge)
    async def put_knowledge(id:str,body:KnowledgeUpdate): return knowledge.update(id,body)
    @router.delete('/knowledge/{id}',status_code=204)
    async def delete_knowledge(id:str): knowledge.delete(id); return Response(status_code=204)
    @router.get('/documents',response_model=list[Document])
    async def get_documents(): return [pdf.document(d['id']) for d in store.all('documents')]
    @router.post('/documents',response_model=Document)
    async def post_document(file:UploadFile=File(...)): return await pdf.upload(file)
    @router.get('/documents/{id}',response_model=Document)
    async def get_document(id:str): return pdf.document(id)
    @router.post('/documents/{id}/analyze',response_model=Document)
    async def analyze(id:str,body:Consent,request:Request): return await pdf.analyze(id,body.consent,options(request))
    @router.delete('/documents/{id}',status_code=204)
    async def delete_document(id:str): knowledge.remove_document(id); return Response(status_code=204)
    @router.get('/papers/{id}',response_model=Paper)
    async def get_paper(id:str): return store.require('papers',id)
    @router.post('/papers/{id}/understood',response_model=Knowledge)
    async def understood(id:str): return knowledge.understood(Paper.model_validate(store.require('papers',id)))
    @router.get('/feed',response_model=Feed)
    async def get_feed(timezone:str='Europe/Berlin'): return feed.latest(timezone)
    @router.post('/feed/generate',response_model=Feed)
    async def generate(body:Generate,request:Request): return await feed.generate(body.timezone,options(request))
    @router.post('/llm/test')
    async def test_llm(request:Request):
        result=await llm.complete(options(request),'Gib exakt {"ok":true} zurück.',{})
        if result!={'ok':True}: raise ServiceError('llm_invalid_json','Ungültige Modellantwort.',502)
        return {'ok':True,'message':'Modellverbindung erfolgreich.'}
    @app.get('/openapi.json', dependencies=[Depends(auth)], include_in_schema=False)
    async def schema(): return app.openapi()
    app.include_router(router)
    return app
