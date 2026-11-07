"""Explicit test-only loopback server: never a production fallback."""
import os
from datetime import datetime, timezone, timedelta
from pathlib import Path
from fastapi import Depends
from paperview.app import create_app
from paperview.config import Settings
from paperview.models import Paper
from paperview.errors import ServiceError

def create_mock_app():
    if os.getenv('PAPERVIEW_TEST_SERVER') != '1':
        raise RuntimeError('Test server must be explicitly enabled.')
    app=create_app(Settings(data_dir=Path(os.environ['PAPERVIEW_DATA_DIR']),app_token='integration-test-token'))
    calls=[]
    async def model(options,instruction,data):
        if isinstance(data,list):
            return {'concepts':[{'name':'Bayes','description':'Wahrscheinlichkeit und Unsicherheit für Biomarker.','pages':[1],'connections':[]}]}
        if not data: return {'ok':True}
        known=data['confirmed_knowledge']; calls.append([k['id'] for k in known])
        names=', '.join(k['name'] for k in known)
        summary=f'Du hast {names} ausdrücklich bestätigt. Das Beispielpaper untersucht, wie sich dieses bekannte Prinzip auf die Auswertung von Biomarkern übertragen lässt. Die gespeicherten Grundlagen helfen dabei, die Rolle von Unsicherheit in der neuen Methode einzuordnen. Die neue Information liegt in der Anwendung auf einen anderen Datentyp. Daraus folgt noch keine bestätigte klinische Wirksamkeit. Diese Erklärung basiert ausschließlich auf dem Testabstract; weitergehende Ergebnisse werden nicht behauptet.'
        return dict(summary=summary,concept_ids=[k['id'] for k in known],new_insight='Das Testabstract beschreibt Bayes für Biomarker.',connection='Dein bestätigtes Bayes-Wissen erklärt den Umgang mit Unsicherheit bei Biomarkern.' if known else '',relevance='Bekannte Statistik wird auf Biomarker angewendet.',takeaway='Biomarker verwenden Bayes zur Darstellung von Unsicherheit.')
    day=[0]
    async def search(*args):
        if not app.state.knowledge.retrieve('Bayes Biomarker'): raise ServiceError('profile_required','Bitte zuerst Vorwissen bestätigen.',400)
        return [Paper(id='mock-paper-'+str(day[0]),doi='10.1000/mock-'+str(day[0]),title='Bayes für Biomarker '+str(day[0]),authors=['Testautor'],abstract='Bayes probability estimates biomarker uncertainty.',published_at='2026-11-07',field='Wissenschaft',source='mock-test',url='https://example.org/mock-paper-'+str(day[0]),review_status='unknown')]
    app.state.llm.complete=model;app.state.sources.search=search
    # Test control is private to this factory and does not exist in production app.
    from fastapi import Request
    @app.post('/test/next-day')
    async def advance(request:Request):
        if request.headers.get('Authorization')!='Bearer integration-test-token': raise ServiceError('unauthorized','Nicht autorisiert.',401)
        day[0]+=1;app.state.feed.now=lambda:datetime.now(timezone.utc)+timedelta(days=day[0])
        return {'day':day[0]}
    @app.get('/test/model-calls')
    async def captured(request:Request):
        if request.headers.get('Authorization')!='Bearer integration-test-token': raise ServiceError('unauthorized','Nicht autorisiert.',401)
        return calls
    return app
