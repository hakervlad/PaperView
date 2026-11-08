import importlib.util
import json
from pathlib import Path
import pytest
from paperview.storage import Store


def exporter():
    spec=importlib.util.spec_from_file_location('export_local',Path(__file__).parents[1]/'export_local.py')
    module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module.export_database


def test_export_is_consistent_private_read_only_and_excludes_secrets(tmp_path):
    store=Store(tmp_path/'source')
    store.put('profile','single',{'bachelor_subject':'Informatik'})
    store.put('knowledge','k1',{'id':'k1','name':'Bayes'})
    store.put('settings','single',{'api_key':'fixture-SECRET'})
    before=store.path.read_bytes()
    output=tmp_path/'archive.json'
    exporter()(store.path,output)
    data=json.loads(output.read_text())
    assert data['format']=='paperview-local-v1'
    assert data['objects']['profile'][0]['bachelor_subject']=='Informatik'
    assert data['objects']['knowledge'][0]['id']=='k1'
    assert 'SECRET' not in output.read_text()
    assert output.stat().st_mode & 0o777 == 0o600
    assert before==store.path.read_bytes()
    with pytest.raises(FileExistsError): exporter()(store.path,output)


def test_missing_database_does_not_create_a_database(tmp_path):
    missing=tmp_path/'missing.db'
    export=exporter()
    with pytest.raises(FileNotFoundError): export(missing,tmp_path/'archive.json')
    assert not missing.exists()
