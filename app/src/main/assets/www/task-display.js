(function(root,factory){var api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.TaskDisplay=api;})(typeof window!=='undefined'?window:this,function(){
  'use strict';
  function key(t){return t.kind+':'+t.id;}
  function apply(d,rules){
    var out=Object.assign({},d),hidden=rules.hidden||{},modes=rules.courseModes||{};
    out.tasks=[];out.tasksSuppressed=[];out.tasksHideOldNotices=rules.hideOldNotices!==false;
    out.tasksCourses=(d.tasksCourses||[]).map(function(c){var copy=Object.assign({},c);copy.mode=modes[c.id]||'auto';copy.included=copy.mode==='include'||(copy.mode!=='exclude'&&!c.old);return copy;});
    (d.tasks||[]).forEach(function(t){var c=out.tasksCourses.find(function(c){return c.id===t.courseId||(!t.courseId&&c.name===t.course)}),reason=hidden[key(t)]?'手动隐藏':t.oldNotice&&out.tasksHideOldNotices?'往期通知':c&&!c.included?'已排除课程':'';
      if(reason)out.tasksSuppressed.push(Object.assign({},t,{hiddenReason:reason}));else out.tasks.push(t);
    });return out;
  }
  function change(rules,a){rules=JSON.parse(JSON.stringify(rules||{}));if(a.type==='task'){rules.hidden=rules.hidden||{};if(a.hidden)rules.hidden[a.key]=true;else delete rules.hidden[a.key];}else if(a.type==='course'){rules.courseModes=rules.courseModes||{};rules.courseModes[a.id]=a.mode;}else if(a.type==='oldNotices')rules.hideOldNotices=a.hidden;else if(a.type==='restore')delete rules.hidden;return rules;}
  function emptyState(d,hasPending,preview){
    var connected=!!d.tasksLoggedIn||!!preview,busy=d.tasksSyncStatus==='refreshing';
    if(hasPending)return {title:'当前筛选没有待办',copy:'这一类暂时没有待处理事项。',login:false};
    if(connected){
      if(busy)return {title:'正在同步学习通待办',copy:'正在读取作业、考试和通知，请稍候。',login:false};
      if(d.tasksSyncStatus==='partial'||d.tasksSyncStatus==='error')return {title:'暂未获取到待办',copy:'本轮有部分数据未更新，请查看上方同步提示后重试。',login:false};
      return {title:'当前没有待办',copy:'已连接学习通。已完成、过期或隐藏的事项不在这里显示。',login:false};
    }
    return {title:'还没有学习通待办',copy:'登录学习通后，作业、考试和未读通知会出现在这里。',login:true};
  }
  return {key:key,apply:apply,change:change,emptyState:emptyState};
});
