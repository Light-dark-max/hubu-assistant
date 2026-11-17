const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname,'../app/src/main/assets/www/reader.js'),'utf8');

// Fixture follows the school page's adm-input → nested empty wrappers → labelled group.
function input(label, value, options={}) {
  const field={type:'text',value,...options,getClientRects(){return this.hidden?[]:[{}];}};
  let parent={innerText:label,querySelectorAll:()=>[field],parentElement:null};
  for(let i=0;i<4;i++) parent={innerText:'',querySelectorAll:()=>[field],parentElement:parent};
  field.parentElement=parent;
  return field;
}
function read(fields, textFields=[]) {
  const result=vm.runInNewContext(source,{
    document:{querySelectorAll:selector=>selector==='input'?fields:textFields},
    getComputedStyle:el=>({visibility:el.visibility||'visible'})
  });
  return JSON.parse(JSON.stringify(result));
}
const core=()=>[input('校区','示例校区'),input('宿舍号','测试宿舍 A301'),input('存款电量','42.80度',{disabled:true}),input('存款余额','24.40元',{disabled:true})];
test('reads disabled values and matches by label rather than input position',()=>{
  const fields=core().reverse();fields.unshift(input('充值金额','8888'));
  const out=read([...fields,input('补助电量','5.00度'),input('补助余额','2.85元')]);
  assert.deepEqual(out,{campus:'示例校区',room:'测试宿舍 A301',energy:'42.80',money:'24.40',subsidyEnergy:'5.00',subsidyMoney:'2.85'});
});
test('accepts zero and negative balances; leaves unavailable subsidies unknown',()=>{
  const out=read([input('宿舍号','A301'),input('存款电量','0度'),input('存款余额','-1.25元'),input('补助电量','未查到电表')]);
  assert.equal(out.energy,'0');assert.equal(out.money,'-1.25');assert.equal(out.subsidyEnergy,undefined);assert.equal(out.error,undefined);
});
test('rejects missing room or invalid numbers instead of caching partial results',()=>{
  assert.equal(read([input('存款电量','42.80度'),input('存款余额','24.40元')]).error,'incomplete-fields');
  assert.equal(read([input('宿舍号','A301'),input('存款电量','NaN'),input('存款余额','24元')]).error,'incomplete-fields');
});
test('ignores hidden inputs and passwords',()=>{
  const out=read([...core(),input('存款电量','999度',{hidden:true}),input('存款余额','111元',{type:'password'}),input('存款电量','666度',{visibility:'hidden'})]);
  assert.equal(out.energy,'42.80');assert.equal(out.money,'24.40');assert.equal(out.error,undefined);
});
test('refuses conflicting visible meters',()=>{
  assert.equal(read([...core(),input('存款电量','999度')]).error,'ambiguous-fields');
});
test('supports labelled text-only fields and commas',()=>{
  const textField=(innerText)=>({innerText,getClientRects:()=>[{}],querySelector:()=>null});
  const out=read([],['宿舍号：A301','存款电量：1,234.50 度','存款余额：704.80 元'].map(textField));
  assert.equal(out.energy,'1234.50');assert.equal(out.money,'704.80');assert.equal(out.room,'A301');assert.equal(out.error,undefined);
});
