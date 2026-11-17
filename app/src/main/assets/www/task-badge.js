(function(root){
  'use strict';
  function date(value,now){return root.TaskDates?root.TaskDates.parse(value,now):new Date(value);}
  function key(task){
    return JSON.stringify([task.kind||'',task.id||[task.course||'',task.title||''],task.deadline||'']);
  }
  function pending(tasks,now){
    return (tasks||[]).filter(function(t){var parsed=date(t.deadline,now),deadline=parsed&&parsed.getTime();return t.status!=='已完成'&&(!t.deadline||!parsed||!Number.isFinite(deadline)||deadline>now)});
  }
  function unread(tasks,seen,now){
    return pending(tasks,now).filter(function(t){
      var parsed=date(t.deadline,now),remaining=(parsed?parsed.getTime():NaN)-now;
      var urgent=(t.kind==='homework'||t.kind==='exam')&&remaining>0&&remaining<3600000;
      return urgent||seen.indexOf(key(t))<0;
    });
  }
  var api={key:key,pending:pending,unread:unread};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.TaskBadge=api;
})(typeof window!=='undefined'?window:this);
