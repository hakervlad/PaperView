"""Read-only export of the historical server DB for the Android SAF importer.

The archive contains personal document text. Keep it private, transfer over a
trusted encrypted channel/USB, and never commit it. Keys/settings are excluded.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sqlite3

KINDS=('profile','knowledge','documents','chunks','evidence','papers')

def export_database(database: Path, output: Path):
    database=Path(database).resolve(); output=Path(output)
    if not database.is_file(): raise FileNotFoundError(database)
    with sqlite3.connect(database.as_uri()+'?mode=ro',uri=True) as db:
        db.row_factory=sqlite3.Row
        db.execute('BEGIN')
        archive={'format':'paperview-local-v1','exported_at':datetime.now(timezone.utc).isoformat(),
                 'objects':{kind:[json.loads(r[0]) for r in db.execute('SELECT data FROM objects WHERE kind=? ORDER BY id',(kind,))] for kind in KINDS},
                 'days':[dict(r) for r in db.execute('SELECT * FROM days ORDER BY day')],
                 'seen':[r[0] for r in db.execute('SELECT identifier FROM seen ORDER BY identifier')]}
        db.rollback()
    # Fail rather than overwriting any existing backup; restrict access from creation.
    descriptor=os.open(output,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    try:
        with os.fdopen(descriptor,'w',encoding='utf-8') as target:
            json.dump(archive,target,ensure_ascii=False);target.flush();os.fsync(target.fileno())
    except BaseException:
        output.unlink(missing_ok=True);raise

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('database',type=Path);parser.add_argument('output',type=Path)
    args=parser.parse_args();export_database(args.database,args.output)
    print('Private archive exported. Original database unchanged; no keys included.')
