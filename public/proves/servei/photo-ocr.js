import {interpretSchedule} from './schedule-ocr.js?v=8';

function cropCell(source,cell){
 const box=cell.rect,canvas=document.createElement('canvas'),scale=Math.max(1,Math.min(3,160/box.height));
 canvas.width=Math.ceil(box.width*scale)+24;canvas.height=Math.ceil(box.height*scale)+24;
 const context=canvas.getContext('2d');context.fillStyle='#fff';context.fillRect(0,0,canvas.width,canvas.height);
 context.imageSmoothingEnabled=true;context.imageSmoothingQuality='high';
 context.drawImage(source,box.x,box.y,box.width,box.height,12,12,box.width*scale,box.height*scale);
 return canvas;
}

// All attempts stay local; the caller saves only the complete validated result.
export async function readServiceImage(worker,canvas,{validate,onProgress=()=>{},makeCrop=cropCell,fallbackCanvas=canvas}={}){
 let lastError;
 for(const mode of [3,6,11]){
  try{
   onProgress(mode===3?'Llegint el servei…':'Revisant la foto i les columnes…');
   await worker.setParameters({tessedit_pageseg_mode:mode,tessedit_char_whitelist:''});
   const source=mode===3?canvas:fallbackCanvas;
   const {data}=await worker.recognize(source);
   let result=interpretSchedule(data,{width:source.width,height:source.height});
   // Keep the fast capture path. Block layout is the photo fallback, before any cell repairs.
   if(mode!==3&&result.repairs.length&&result.repairs.length<=10){
    const overrides={};let remaining=10;
    for(let round=0;round<2&&result.repairs.length&&remaining>0;round++){
    for(const cell of result.repairs.slice(0,remaining)){
     if(!(cell.rect.width>0&&cell.rect.height>0))continue;
     remaining--;
     onProgress('Revisant la fila '+(cell.row+1)+' de la foto…');
     await worker.setParameters({tessedit_pageseg_mode:round===0?7:8,tessedit_char_whitelist:cell.whitelist});
     const crop=makeCrop(source,cell),read=await worker.recognize(crop);
     (overrides[cell.row]??={})[cell.col]=String(read.data.text||'').replace(/\s/g,'');
    }
    result=interpretSchedule(data,{width:source.width,height:source.height},overrides);
    }
   }
   if(result.repairs.length)throw Error('No s’han pogut validar totes les caselles de l’horari. Comprova que la imatge inclogui la taula sencera.');
   const parsed=validate?validate(result.text):null;
   if(source!==canvas){
    const sx=canvas.width/source.width,sy=canvas.height/source.height;
    result.rowBounds=result.rowBounds.map(b=>({x:b.x*sx,y:b.y*sy,width:b.width*sx,height:b.height*sy}));
    result.imageSize={width:canvas.width,height:canvas.height};
   }
   return {result,parsed};
  }catch(error){lastError=error;}
 }
 throw lastError||Error('No s’ha pogut interpretar la foto.');
}

