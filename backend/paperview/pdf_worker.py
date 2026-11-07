"""Isolated parser: fixed input path, resource limits, bounded JSON output, no logs."""
import json
import resource
import sys
from pypdf import PdfReader

def main():
    resource.setrlimit(resource.RLIMIT_AS,(512*1024*1024,512*1024*1024))
    resource.setrlimit(resource.RLIMIT_CPU,(10,10))
    path,max_pages,max_text=sys.argv[1],int(sys.argv[2]),int(sys.argv[3])
    try:
        reader=PdfReader(path,strict=True)
        if reader.is_encrypted: return {'error':'pdf_encrypted'}
        if len(reader.pages)>max_pages: return {'error':'pdf_pages'}
        pages=[]; total=0
        for n,page in enumerate(reader.pages,1):
            text=page.extract_text() or ''; total+=len(text)
            if total>max_text: return {'error':'pdf_text_limit'}
            pages.append({'page':n,'text':text})
        if not any(p['text'].strip() for p in pages): return {'error':'pdf_no_text'}
        return {'pages':pages}
    except Exception:
        return {'error':'pdf_malformed'}

if __name__=='__main__':
    print(json.dumps(main()))
