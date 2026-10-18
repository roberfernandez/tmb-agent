"""Offline OCR sidecars. Never modifies source PDFs or the published search index.

Requires PyMuPDF and Tesseract 5 with spa/cat models. Example:
python scripts/ocr-convenis.py --pdf-dir ../../work/convenis-pdfs \
  --models ../../work/ocr-convenis/tessdata --output ../../work/ocr-convenis/results
Run without --sample to process every page of the six scanned documents.
"""
import argparse
import concurrent.futures
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import pymupdf
from PIL import Image

IDS = ('c1999', 'c2002', 'c2008', 'p2012', 'p2013', 'p2014')

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def read_recognition(base):
    text=base.with_suffix('.txt').read_text(encoding='utf-8').strip()
    with base.with_suffix('.tsv').open(encoding='utf-8',newline='') as file:
        words=[row for row in csv.DictReader(file,delimiter='\t',quoting=csv.QUOTE_NONE)
               if row.get('text','').strip() and float(row['conf'])>=0]
    weights=sum(len(row['text']) for row in words)
    confidence=round(sum(float(row['conf'])*len(row['text']) for row in words)/weights,1) if weights else 0
    return text,len(words),confidence

def recognize(job):
    pdf, page, output, executable, models, signature = job
    folder = Path(output) / Path(pdf).stem
    folder.mkdir(parents=True, exist_ok=True)
    base = folder / f'page-{page:03}'
    result_file = base.with_suffix('.json')
    if result_file.exists():
        previous = json.loads(result_file.read_text(encoding='utf-8'))
        if previous.get('signature') == signature and (previous.get('meanConfidence',0) >= 85 or previous.get('qualityChecked')):
            return previous
    with pymupdf.open(pdf) as document:
        image = document[page-1].get_pixmap(dpi=300, colorspace=pymupdf.csGRAY)
        image.save(base.with_suffix('.png'))
    environment = dict(os.environ, OMP_THREAD_LIMIT='1')
    rotation = 0
    # Use the already installed orientation model; only rotate the OCR input, never the PDF.
    orientation = subprocess.run([executable,str(base.with_suffix('.png')),'stdout','-l','osd','--psm','0'],
                                 capture_output=True,text=True,timeout=30,env=environment)
    angle = re.search(r'Rotate: (\d+)',orientation.stdout)
    confidence = re.search(r'Orientation confidence: ([\d.]+)',orientation.stdout)
    if angle and confidence and float(confidence[1]) >= 2:
        rotation = int(angle[1])
    input_image = base.with_suffix('.png')
    if rotation:
        input_image=folder/f'page-{page:03}-upright.png'
        with Image.open(base.with_suffix('.png')) as image:
            image.rotate(-rotation,expand=True).save(input_image)
    command = [executable, str(input_image), str(base), '--tessdata-dir',
               models, '-l', 'spa+cat', '--oem', '1', '--psm', '3', '-c', 'tessedit_create_txt=1', '-c', 'tessedit_create_tsv=1']
    subprocess.run(command, check=True, capture_output=True, timeout=180, env=environment)
    text,word_count,confidence=read_recognition(base)
    segmentation=3
    if confidence<85:
        alternative=folder/f'page-{page:03}-psm6'
        retry=command.copy();retry[2]=str(alternative);retry[retry.index('--psm')+1]='6'
        subprocess.run(retry,check=True,capture_output=True,timeout=180,env=environment)
        other_text,other_count,other_confidence=read_recognition(alternative)
        if other_confidence>confidence:
            text,word_count,confidence=other_text,other_count,other_confidence
            segmentation=6
    result = dict(id=Path(pdf).stem, page=page, text=re.sub(r'\s+', ' ', text),
                  wordCount=word_count, meanConfidence=confidence, signature=signature,
                  ocr=True, engine='Tesseract 5.5.0', languages='spa+cat', dpi=300)
    result.update(rotation=rotation,orientationChecked=True,qualityChecked=True,segmentation=segmentation)
    result_file.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pdf-dir', required=True, type=Path)
    parser.add_argument('--models', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--tesseract', default='C:/Program Files/Tesseract-OCR/tesseract.exe')
    parser.add_argument('--workers', type=int, default=3)
    parser.add_argument('--sample', action='store_true')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    models = {name:sha(args.models/f'{name}.traineddata') for name in ('spa','cat')}
    jobs=[]; pdf_hashes={}
    sample_pages={'c1999':3,'c2002':2,'c2008':3,'p2012':1,'p2013':1,'p2014':1}
    for id in IDS:
        pdf=(args.pdf_dir/f'{id}.pdf').resolve(); pdf_hashes[id]=sha(pdf)
        with pymupdf.open(pdf) as document:
            pages=[sample_pages[id]] if args.sample else range(1,len(document)+1)
            signature=hashlib.sha256(json.dumps([pdf_hashes[id],models,300,'spa+cat','psm3']).encode()).hexdigest()
            jobs.extend((str(pdf),page,str(args.output.resolve()),args.tesseract,str(args.models.resolve()),signature) for page in pages)
    results=[]
    with concurrent.futures.ProcessPoolExecutor(max_workers=args.workers) as pool:
        for result in pool.map(recognize,jobs):
            results.append(result)
            print(f"{len(results)}/{len(jobs)} {result['id']} p{result['page']}: {result['wordCount']} words; confidence {result['meanConfidence']}",flush=True)
    report=dict(sample=args.sample,models=models,pdfHashes=pdf_hashes,
                pages=sorted(results,key=lambda r:(r['id'],r['page'])))
    target=args.output/('sample-report.json' if args.sample else 'report.json')
    target.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'Saved {target}',flush=True)

if __name__=='__main__':
    main()
