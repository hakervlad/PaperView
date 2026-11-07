"""Short SQLite transactions; network awaits never hold a transaction."""
import json
import os
import sqlite3
from contextlib import contextmanager
from uuid import uuid4
from .errors import ServiceError


def uid():
    return uuid4().hex

class Store:
    def __init__(self, directory):
        directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(directory, 0o700)
        self.path = directory / 'paperview.sqlite3'
        with self.connect() as db:
            db.executescript('''
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS objects(kind TEXT, id TEXT, data TEXT NOT NULL, PRIMARY KEY(kind,id));
            CREATE TABLE IF NOT EXISTS days(day TEXT PRIMARY KEY, state TEXT, owner TEXT, lease REAL, attempts INTEGER, retry REAL, feed TEXT);
            CREATE TABLE IF NOT EXISTS seen(identifier TEXT PRIMARY KEY);
            CREATE TABLE IF NOT EXISTS limits(name TEXT PRIMARY KEY, next REAL);
            ''')
        os.chmod(self.path, 0o600)

    @contextmanager
    def connect(self):
        db=sqlite3.connect(self.path, timeout=5, isolation_level=None)
        db.row_factory=sqlite3.Row
        try:
            yield db
        finally:
            db.close()

    @contextmanager
    def transaction(self):
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            try:
                yield db
                db.commit()
            except BaseException:
                db.rollback()
                raise

    def get(self, kind, id, default=None):
        with self.connect() as db:
            row=db.execute('SELECT data FROM objects WHERE kind=? AND id=?',(kind,id)).fetchone()
        return json.loads(row[0]) if row else default

    def require(self, kind, id):
        data=self.get(kind,id)
        if data is None:
            raise ServiceError('not_found','Eintrag nicht gefunden.',404)
        return data

    def all(self, kind):
        with self.connect() as db:
            return [json.loads(r[0]) for r in db.execute('SELECT data FROM objects WHERE kind=? ORDER BY rowid',(kind,))]

    def put(self, kind, id, data):
        with self.connect() as db:
            db.execute('INSERT OR REPLACE INTO objects VALUES(?,?,?)',(kind,id,json.dumps(data)))

    def delete(self, kind, id):
        with self.connect() as db:
            db.execute('DELETE FROM objects WHERE kind=? AND id=?',(kind,id))
