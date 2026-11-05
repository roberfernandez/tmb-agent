import {test} from 'node:test';
import assert from 'node:assert/strict';
import {interpretSchedule} from '../public/proves/servei/schedule-ocr.js';
import {readServiceImage} from '../public/proves/servei/photo-ocr.js';

const labels=['HI','EI','VI','T/E','HF','EF','VF'];
function fixture({tilt=0,missing=[],broken=false,lines=true}={}){
 const word=(text,col,y)=>({text,confidence:95,bbox:{x0:80+col*120,y0:y+col*tilt,x1:120+col*120,y1:y+col*tilt+24}});
 const header=labels.flatMap((text,i)=>missing.includes(i)?[]:[word(text,i,50)]);
 const values=[['10:10','413','','','10:20','413',''],['10:20','413','','TRA',broken?'10:93':'10:23','417',''],['10:23','417','2',broken?'1s':'16','10:32','413','2']];
 const body=values.map((row,i)=>row.flatMap((value,col)=>value?[word(value,col,200+i*100)]:[]));
 const all=[header,...body];return {words:all.flat(),...(lines?{lines:all.map(words=>({words}))}:{})};
}
const expected='10:10|10:20|EST|413|413|||\n10:20|10:23|TRA|413|417|||\n10:23|10:32|CON|417|413|416|2|2';

test('straight captures retain the existing service and row hitboxes',()=>{
 const result=interpretSchedule(fixture({lines:false}),{width:1000,height:800});
 assert.equal(result.text,expected);assert.equal(result.rowBounds.length,3);assert.equal(result.repairs.length,0);
 assert.ok(result.rowBounds.every(b=>b.width>0&&b.height>0&&b.y>=0));
});

test('photographed sloping rows follow OCR line geometry rather than equal Y bands',()=>{
 const result=interpretSchedule(fixture({tilt:6}),{width:1000,height:800});
 assert.equal(result.text,expected);assert.equal(result.count,3);
});

test('pipe characters in headers and two unreadable labels are recoverable from ordered columns',()=>{
 const data=fixture({missing:[2,4],tilt:3});data.words.find(w=>w.text==='EI').text='E|';
 assert.equal(interpretSchedule(data,{width:1000,height:800}).text,expected);
});

test('fewer than five header anchors cannot invent the table structure',()=>{
 assert.throws(()=>interpretSchedule(fixture({missing:[0,2,4]})),/capçalera/);
});

test('bad time and train cells identify bounded crops and accept independent OCR corrections',()=>{
 const data=fixture({broken:true,tilt:5}),size={width:1000,height:800};
 const result=interpretSchedule(data,size);
 assert.deepEqual(result.repairs.map(c=>[c.row,c.col]),[[1,4],[2,3]]);
 assert.ok(result.repairs.every(c=>c.rect.x>=0&&c.rect.width>0&&c.rect.height>0));
 assert.equal(interpretSchedule(data,size,{1:{4:'10:23'},2:{3:'16'}}).text,expected);
});

test('valid-looking but overlapping times also request independent cell reads',()=>{
 const data=fixture();data.lines[2].words.find(w=>w.text==='10:23').text='19:23';
 const cells=interpretSchedule(data).repairs.map(c=>[c.row,c.col]);
 assert.deepEqual(cells,[[1,4],[2,0]]);
});

test('photo fallback retries layout then just the two doubtful cells and scales highlights back',async()=>{
 const calls=[],parameters=[],original={width:2000,height:1600},fallback={width:1000,height:800};
 const worker={async setParameters(p){parameters.push(p)},async recognize(source){calls.push(source);return {data:source===original?{}:source===fallback?fixture({broken:true,tilt:5}):{text:source.cell.col===4?'10:23':'16'}}}};
 const {result,parsed}=await readServiceImage(worker,original,{fallbackCanvas:fallback,makeCrop:(source,cell)=>({source,cell}),validate:text=>{assert.equal(text,expected);return text.split('\n')}});
 assert.equal(calls.length,4);assert.equal(parsed.length,3);
 assert.deepEqual(parameters.map(p=>p.tessedit_pageseg_mode),[3,6,7,7]);
 assert.ok(calls.slice(2).every(c=>c.source===fallback));
 assert.equal(result.rowBounds[0].x,interpretSchedule(fixture()).rowBounds[0].x*2);
 assert.deepEqual(result.imageSize,{width:2000,height:1600});
});

test('fast capture path does not run photo retries and unreadable inputs never reach save validation',async()=>{
 let reads=0,validated=0;
 const worker={async setParameters(){},async recognize(){reads++;return {data:fixture()}}};
 await readServiceImage(worker,{width:1000,height:800},{validate:()=>validated++});assert.equal(reads,1);assert.equal(validated,1);
 validated=0;worker.recognize=async()=>({data:{}});
 await assert.rejects(readServiceImage(worker,{width:1000,height:800},{validate:()=>validated++}),/columnes/);
 assert.equal(validated,0);
});


test('train codes above 416 are accepted in short and full form',()=>{
 for(const [code,train] of [['17','417'],['417','417'],['22','422'],['422','422'],['99','499']]){
  const data=fixture();
  data.words.find(w=>w.text==='16').text=code;
  const result=interpretSchedule(data,{width:1000,height:800});
  assert.equal(result.text,expected.replace('|416|','|'+train+'|'));
  assert.deepEqual(result.repairs,[]);
 }
});
