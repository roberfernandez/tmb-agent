"""Merge reviewed OCR sidecars into the existing index; preserve direct PDF text.
Usage: python scripts/index-convenis-ocr.py --report PATH_TO_report.json
"""
import argparse
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
# Visually checked blank leaves. Keep PDF page numbers rather than renumbering results.
BLANKS={'p2013':[4],'p2014':[3]}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report',required=True,type=Path)
    args=parser.parse_args()
    report=json.loads(args.report.read_text(encoding='utf-8'))
    if report.get('sample') or len(report['pages'])!=93:
        raise ValueError('Expected the complete 93-page report')
    index=ROOT/'public/assets/convenis/index.json'
    docs=json.loads(index.read_text(encoding='utf-8'))
    audit=dict(engine='Tesseract 5.5.0',languages=['spa','cat'],dpi=300,
               modelHashes=report['models'],date='2026-10-18',
               note='OCR confidence is an engine signal, not measured accuracy. Samples visually checked; not a full transcription review.',
               visualSamples={'c1999':[3,8,23,25],'c2002':[2],'c2008':[3,24],'p2012':[1,15],'p2013':[1,4],'p2014':[1,3]},documents=[])
    for doc in docs:
        if doc['id'] not in report['pdfHashes']:continue
        pages=sorted([p for p in report['pages'] if p['id']==doc['id']],key=lambda p:p['page'])
        if [p['page'] for p in pages]!=list(range(1,doc['pageCount']+1)):
            raise ValueError('Missing or duplicate page in '+doc['id'])
        skipped=BLANKS.get(doc['id'],[])
        doc['pages']=[dict(page=p['page'],text=p['text'],ocr=True,
                           readingDifficulty=p['meanConfidence']<85)
                      for p in pages if p['page'] not in skipped and p['text']]
        doc['ocr']=dict(engine=audit['engine'],languages=audit['languages'],dpi=300,
                        processedPages=len(pages),indexedPages=len(doc['pages']),blankPages=skipped,
                        sourceSha256=report['pdfHashes'][doc['id']])
        audit['documents'].append(dict(id=doc['id'],sourceSha256=doc['ocr']['sourceSha256'],blankPages=skipped,
            pages=[{k:p.get(k,0) for k in ('page','wordCount','meanConfidence','rotation','segmentation')} for p in pages]))
        if doc['id']=='p2013':
            doc.update(title='Annex acord FMB · 2013–2014',kind='Acord',
                       note='Annex a l’acta de 05/12/2012. L’arxiu sindical l’agrupa amb les pròrrogues; la capçalera del PDF identifica aquest annex.')
        if doc['id']=='p2012':
            doc['note']='El PDF inclou també el text del XXV conveni 2008–2011 a partir de la pàgina 8. Les coincidències d’aquesta part no són canvis de la pròrroga.'
            for page in doc['pages']:
                if page['page']>=8:page['sectionLabel']='Text annex: XXV · 2008–2011'
    index.write_text(json.dumps(docs,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    (ROOT/'docs/convenis-ocr-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Indexed',sum(len(d['pages']) for d in docs if d.get('ocr')),'OCR pages in',len(audit['documents']),'documents')

if __name__=='__main__':main()
