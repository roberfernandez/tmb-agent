import test from 'node:test';
import assert from 'node:assert/strict';
import { imageGestures } from '../src/image-gestures.js';
test('pinch anchors the image and transitions to one-finger panning', () => {
  const handlers = {};
  const viewport = { style:{},scrollLeft:0,scrollTop:0,
    getBoundingClientRect:()=>({left:0,top:0}),
    addEventListener:(type,fn)=>{handlers[type]=fn;} };
  const image={};let zoom=1;
  imageGestures(viewport,image,()=>zoom,value=>{zoom=Math.max(1,Math.min(6,value));});
  const touch=(x,y)=>({clientX:x,clientY:y});
  const send=(type,touches)=>handlers[type]({touches,preventDefault(){}});
  send('touchstart',[touch(50,100),touch(150,100)]);
  send('touchmove',[touch(0,100),touch(200,100)]);
  assert.equal(zoom,2);assert.equal(viewport.scrollLeft,100);assert.equal(viewport.scrollTop,100);
  send('touchend',[touch(200,100)]);
  send('touchmove',[touch(170,80)]);
  assert.equal(viewport.scrollLeft,130);assert.equal(viewport.scrollTop,120);
  send('touchend',[]);
  send('touchstart',[touch(100,100)]);send('touchmove',[touch(80,70)]);
  assert.equal(viewport.scrollLeft,150);assert.equal(viewport.scrollTop,150);
  assert.equal(viewport.style.touchAction,'none');assert.equal(image.draggable,false);
});
