(function () {
  'use strict';
  function clean(s) { return (s || '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim(); }
  var select = document.querySelector('#kksj');
  if (select) return {stage:'query', terms:Array.prototype.map.call(select.options, function(o){return {value:o.value,label:clean(o.textContent)};}).filter(function(o){return o.value==='' || /^\d{4}-\d{4}-[12]$/.test(o.value);})};
  var table = document.querySelector('#dataList');
  if (!table || !table.rows.length) return {error:'table-unavailable'};
  var headers = Array.prototype.map.call(table.rows[0].cells, function(c){return clean(c.textContent);});
  var columns = {'开课学期':'term','课程编号':'code','课程名称':'name','成绩':'score','成绩标识':'mark','学分':'credits','总学时':'hours','绩点':'gpa','补重学期':'retakeTerm','考核方式':'assessment','考试性质':'examType','课程属性':'attribute','课程性质':'category'};
  if (['开课学期','课程名称','成绩','学分','绩点'].some(function(h){return headers.indexOf(h)<0;})) return {error:'columns-changed'};
  var body = clean(document.body.textContent), condition = body.match(/查询条件[：:]\s*(?:开课时间【([^】]+)】|(全部))/);
  if (!condition) return {error:'condition-unavailable'};
  var term = condition[1] || '', rows = [], invalid = false, empty = false;
  Array.prototype.slice.call(table.rows, 1).forEach(function(row){
    var cells = Array.prototype.slice.call(row.cells);
    if (cells.length===1 && /未查询到数据/.test(cells[0].textContent)) {empty=true;return;}
    if (cells.length!==headers.length) {invalid=true;return;}
    var out={};headers.forEach(function(h,i){if(columns[h])out[columns[h]]=clean(cells[i].textContent);});
    if (!out.name || !/^\d{4}-\d{4}-[12]$/.test(out.term) || (term && out.term!==term)) {invalid=true;return;}
    rows.push(out);
  });
  if (invalid || (!rows.length && !empty) || (empty && rows.length)) return {error:'incomplete-table'};
  // Official statistics are preserved as text, never recomputed from guessed grading rules.
  var summary={};[['所修门数','count'],['所修总学分','credits'],['平均学分绩点','gpa'],['平均成绩','average']].forEach(function(p){var m=body.match(new RegExp(p[0]+'\\s*[:：]\\s*(\\d+(?:\\.\\d+)?)'));if(m)summary[p[1]]=m[1];});
  if (summary.count!=null && +summary.count!==rows.length) return {error:'incomplete-table'};
  return {stage:'result',term:term,rows:rows,summary:summary};
})()
