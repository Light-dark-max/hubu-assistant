const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const script=fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/assets/www/grades-reader.js'),'utf8');
const headers=['序号','开课学期','课程编号','课程名称','成绩','成绩标识','学分','总学时','绩点','补重学期','考核方式','考试性质','课程属性','课程性质'];
const data=['1','2025-2026-2','TEST001','测试课程','中','','2','32','','','考查','正常考试','必修','专业课'];
function parse(rows,body,columns=headers){const table={rows:[columns,...rows].map(row=>({cells:row.map(textContent=>({textContent}))}))};return JSON.parse(JSON.stringify(vm.runInNewContext(script,{document:{querySelector:s=>s==='#dataList'?table:null,body:{textContent:body}}})));}
let r=parse([data],'查询条件：开课时间【2025-2026-2】');
assert.equal(r.stage,'result');assert.equal(r.rows[0].score,'中');assert.equal(r.rows[0].gpa,'');assert.equal(r.rows[0].credits,'2');assert.deepEqual(r.summary,{});
r=parse([data],'查询条件：全部 所修门数:1 所修总学分:2 平均学分绩点:0 平均成绩:75');
assert.equal(r.term,'');assert.equal(r.summary.gpa,'0');assert.equal(r.summary.average,'75');
assert.equal(parse([data],'查询条件：开课时间【2024-2025-1】').error,'incomplete-table');
assert.equal(parse([data],'查询条件：全部 所修门数:2').error,'incomplete-table');
assert.equal(parse([data.slice(0,8)],'查询条件：全部').error,'incomplete-table');
assert.equal(parse([data],'查询条件：全部',headers.map(h=>h==='学分'?'其他':h)).error,'columns-changed');
assert.equal(parse([data],'学校登录').error,'condition-unavailable');
r=parse([['未查询到数据']],'查询条件：开课时间【2026-2027-1】');assert.equal(r.rows.length,0);assert.equal(r.term,'2026-2027-1');
assert.equal(parse([],'查询条件：全部').error,'incomplete-table');
console.log('Grades parser: term isolation, textual scores, blank GPA, official summary, empty results and incomplete data checks passed.');
