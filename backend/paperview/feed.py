import asyncio
from datetime import datetime, timezone
import json
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
from .errors import ServiceError
from .models import Feed
from .sources import identifiers
from .storage import uid

class FeedService:
    def __init__(self,store,knowledge,sources,personalizer):
        self.store,self.knowledge,self.sources,self.personalizer=store,knowledge,sources,personalizer
        self.now=lambda:datetime.now(timezone.utc)
    def day(self,tz):
        try: return self.now().astimezone(ZoneInfo(tz)).date()
        except (ZoneInfoNotFoundError,ValueError): raise ServiceError('invalid_timezone','Unbekannte Zeitzone.') from None
    def latest(self,tz):
        day=self.day(tz)
        with self.store.connect() as db:
            row=db.execute("SELECT feed FROM days WHERE state='success' ORDER BY day DESC LIMIT 1").fetchone()
        return Feed.model_validate_json(row[0]) if row else Feed(date=str(day),status='empty')
    async def generate(self,tz,options):
        day=self.day(tz); now=self.now().timestamp(); owner=uid()
        with self.store.transaction() as db:
            r=db.execute('SELECT * FROM days WHERE day=?',(str(day),)).fetchone()
            if r and r['state']=='success': return Feed.model_validate_json(r['feed'])
            if r and r['state']=='running' and r['lease']>now: raise ServiceError('feed_busy','Die Tagesausgabe wird bereits erstellt.',409)
            if r and (r['retry']>now or r['attempts']>=3): raise ServiceError('feed_cooldown','Erneuter Versuch nach Wartezeit oder am nächsten Tag möglich.',429)
            attempts=(r['attempts'] if r else 0)+1
            db.execute('INSERT OR REPLACE INTO days VALUES(?,?,?,?,?,?,?)',(str(day),'running',owner,now+600,attempts,0,None))
        try:
            # Ten model calls maximum, each bounded; whole run below lease lifetime.
            async with asyncio.timeout(480):
                profile=self.knowledge.profile()
                known = [k.name for k in self.knowledge.all() if k.status == 'confirmed']
                search_profile = profile.model_copy(update={'focus_areas': list(dict.fromkeys(profile.focus_areas + known))[:10]})
                papers=await self.sources.search(search_profile,day)
                from .knowledge import words
                topics = words(' '.join(known + profile.interests + profile.focus_areas))
                papers.sort(key=lambda p: (len(topics & words(p.title+' '+p.abstract)), p.published_at), reverse=True)
                with self.store.connect() as db: seen={r[0] for r in db.execute('SELECT identifier FROM seen')}
                selected=[p for p in papers if not set(identifiers(p)) & seen and p.abstract.strip()][:profile.paper_count]
                items=[await self.personalizer.explain(p,profile,options) for p in selected]
                feed=Feed(date=str(day),status='success',items=items)
                with self.store.transaction() as db:
                    current=db.execute('SELECT owner FROM days WHERE day=?',(str(day),)).fetchone()
                    if not current or current[0]!=owner: raise ServiceError('feed_busy','Eine andere Erstellung hat die Tagesausgabe übernommen.',409)
                    for p in selected:
                        db.execute('INSERT OR REPLACE INTO objects VALUES(?,?,?)',('papers',p.id,p.model_dump_json()))
                        for id in identifiers(p): db.execute('INSERT OR IGNORE INTO seen VALUES(?)',(id,))
                    db.execute("UPDATE days SET state='success',feed=?,lease=0 WHERE day=? AND owner=?",(feed.model_dump_json(),str(day),owner))
                return feed
        except BaseException as exc:
            with self.store.connect() as db:
                db.execute("UPDATE days SET state='failed',retry=?,lease=0 WHERE day=? AND owner=?",(self.now().timestamp()+300,str(day),owner))
            if isinstance(exc,TimeoutError): raise ServiceError('feed_timeout','Zeitlimit bei der Tagesausgabe erreicht.',504) from None
            raise
