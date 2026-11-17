const {test}=require('node:test');
const assert=require('node:assert/strict');
const display=require('../app/src/main/assets/www/task-display.js');
test('empty state follows login and sync status, not just task count',()=>{
  assert.equal(display.emptyState({},false).login,true);
  for(const tasksSyncStatus of ['success','partial','refreshing','error']){
    const result=display.emptyState({tasksLoggedIn:true,tasksSyncStatus},false);
    assert.equal(result.login,false);
    assert.doesNotMatch(result.copy,/登录学习通后/);
  }
  assert.equal(display.emptyState({tasksLoggedIn:false,tasksSyncStatus:'login'},false).login,true);
  assert.equal(display.emptyState({tasksLoggedIn:true},true).title,'当前筛选没有待办');
  assert.equal(display.emptyState({},false,true).login,false);
});
test('hidden messages stay suppressed across refresh and restore locally',()=>{
  const d={tasks:[{id:'1',kind:'notice',title:'通知'}]};
  const r=display.change({}, {type:'task',key:'notice:1',hidden:true});
  assert.equal(display.apply(d,r).tasks.length,0);
  assert.equal(display.apply(JSON.parse(JSON.stringify(d)),r).tasksSuppressed[0].hiddenReason,'手动隐藏');
  assert.equal(display.apply(d,display.change(r,{type:'task',key:'notice:1',hidden:false})).tasks.length,1);
  assert.equal(d.tasks.length,1);
});
test('old notices, unknown dates and manual course overrides',()=>{
  const d={tasksCourses:[{id:'c',name:'旧课',old:true}],tasks:[{id:'1',kind:'notice',oldNotice:true},{id:'2',kind:'notice'},{id:'3',kind:'homework',courseId:'c'}]};
  assert.deepEqual(display.apply(d,{}).tasks.map(t=>t.id),['2']);
  let r=display.change({}, {type:'oldNotices',hidden:false});
  r=display.change(r,{type:'course',id:'c',mode:'include'});
  assert.equal(display.apply(d,r).tasks.length,3);
  r=display.change(r,{type:'task',key:'notice:2',hidden:true});
  assert.equal(display.apply(d,display.change(r,{type:'restore'})).tasks.length,3);
});
