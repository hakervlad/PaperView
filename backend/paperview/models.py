from typing import Literal, Annotated
from pydantic import BaseModel, ConfigDict, Field, StringConstraints

Text = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]
Long = Annotated[str, StringConstraints(max_length=4000)]
class Model(BaseModel):
    model_config = ConfigDict(extra='forbid')
class Profile(Model):
    bachelor_subject: str = Field(default='', max_length=200)
    focus_areas: list[Text] = Field(default_factory=list, max_length=30)
    interests: list[Text] = Field(default_factory=list, max_length=30)
    familiar_topics: list[Text] = Field(default_factory=list, max_length=100)
    paper_count: int = Field(default=5, ge=5, le=10)
    lookback_days: int = Field(default=7, ge=7, le=30)
class KnowledgeUpdate(Model):
    name: Text
    description: Long
    status: Literal['confirmed','candidate','learning']
class Knowledge(KnowledgeUpdate):
    id: str
    connections: list[str] = Field(default_factory=list)
    source_paper_id: str | None = None
    document_ids: list[str] = Field(default_factory=list)
class Document(Model):
    id: str
    filename: str
    status: str
    message: str | None = None
    concepts: list[Knowledge] = Field(default_factory=list)
class Paper(Model):
    id: str
    doi: str | None = None
    arxiv_id: str | None = None
    title: str
    authors: list[str]
    abstract: str
    published_at: str
    field: str
    source: str
    url: str
    pdf_url: str | None = None
    review_status: str
class FeedItem(Model):
    id: str
    paper_id: str
    title: str
    summary: str
    prior_knowledge: list[Knowledge]
    new_insight: str
    connection: str
    relevance: str
    takeaway: str
    abstract_based: bool = True
    source_url: str
    published_at: str
    category: str
class Feed(Model):
    date: str
    status: str
    items: list[FeedItem] = Field(default_factory=list)
    message: str | None = None
    next_retry_at: str | None = None
class Consent(Model):
    consent: bool
class Generate(Model):
    timezone: str = 'Europe/Berlin'
class Concept(Model):
    name: Text
    description: Long
    pages: list[int] = Field(min_length=1, max_length=50)
    connections: list[Text] = Field(default_factory=list, max_length=20)
class Extraction(Model):
    concepts: list[Concept] = Field(max_length=30)
class Explanation(Model):
    title: Text | None = None
    summary: Long
    concept_ids: list[str] = Field(max_length=8)
    new_insight: Long
    connection: Long
    relevance: Long
    takeaway: Long
