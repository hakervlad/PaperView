from pydantic import ValidationError
from .errors import ServiceError
from .models import Explanation, FeedItem
from .storage import uid

class Personalizer:
    def __init__(self,knowledge,llm): self.knowledge,self.llm=knowledge,llm
    async def explain(self,paper,profile,options):
        known=self.knowledge.retrieve(paper.title+' '+paper.abstract)
        data={'paper':{'title':paper.title,'abstract':paper.abstract[:12000]},'profile':profile.model_dump(exclude={'familiar_topics'}),'confirmed_knowledge':[k.model_dump() for k in known]}
        result=await self.llm.complete(options,'Schema: {"title":str,"summary":str,"concept_ids":[str],"new_insight":str,"connection":str,"relevance":str,"takeaway":str}. Ein verständlicher deutscher Titel und eine Kurzfassung von ungefähr 60 bis 120 Wörtern. Aussagen ausschließlich aus dem Abstract. Keine unbegründeten Aussagen über persönliche Fähigkeiten. Analogien und Unsicherheiten kennzeichnen. Unbekannte Begriffe erklären. Ohne Wissensbezug notwendige Grundlagen erklären und fehlenden Bezug offen benennen. concept_ids nur aus confirmed_knowledge. Ohne passende Konzepte concept_ids=[] und connection="". Keine Aussagen über tatsächliches Vorwissen außerhalb referenzierter IDs. Erkläre passend zum Profil.',data)
        try:
            e=Explanation.model_validate(result)
            ids={k.id for k in known}
            if not set(e.concept_ids)<=ids or (not e.concept_ids and e.connection.strip()): raise ValueError()
            if not e.summary.strip() or not e.new_insight.strip(): raise ValueError()
        except (ValidationError,ValueError): raise ServiceError('llm_invalid_json','Modellantwort enthält ungültige Wissensbezüge oder Aussagen.',502) from None
        return FeedItem(id=uid(),paper_id=paper.id,title=e.title or paper.title,summary='Auf Grundlage des Abstracts: '+e.summary,prior_knowledge=[k for k in known if k.id in e.concept_ids],new_insight=e.new_insight,connection=e.connection,relevance=e.relevance,takeaway=e.takeaway,abstract_based=True,source_url=paper.url,published_at=paper.published_at,category=paper.field)
