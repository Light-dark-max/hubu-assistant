(function(root){
  'use strict';
  function date(value,now){return root.TaskDates?root.TaskDates.parse(value,now):new Date(value);}
  function hours(value){
    var list=String(value).replace(/，/g,',').split(',').map(function(v){if(!v.trim())throw Error('empty');return Number(v.trim())});
    if(!list.length||list.length>8||list.some(function(n){return !Number.isFinite(n)||n<=0||n>720}))throw Error('range');
    return Array.from(new Set(list)).sort(function(a,b){return b-a});
  }
  function evaluate(tasks,previous,settings,now){
    var fresh=!previous,record={initialized:true,known:[],fired:{},newPending:[]},alerts=[],quietDate=new Date(now),quiet=quietDate.getHours()>=23||quietDate.getHours()<7;
    previous=previous||{known:[],fired:{}};
    (tasks||[]).forEach(function(t){
      var key=JSON.stringify([t.kind,t.id||[t.course,t.title],t.deadline||'']),parsed=date(t.deadline,now),remaining=(parsed?parsed.getTime():NaN)-now;
      if(t.status==='已完成'||(t.deadline&&remaining<=0))return;
      record.known.push(key);record.fired[key]=(previous.fired[key]||[]).slice();
      var timed=(t.kind==='homework'||t.kind==='exam')&&Number.isFinite(remaining)&&remaining>0;
      var thresholds=timed?hours(t.kind==='exam'?settings.exam:settings.homework):[],crossed=thresholds.filter(function(h){return remaining<=h*3600000&&record.fired[key].indexOf(h)<0});
      var urgent=timed&&remaining<3600000,unseen=previous.known.indexOf(key)<0||(previous.newPending||[]).indexOf(key)>=0;
      if(fresh){record.fired[key]=thresholds.filter(function(h){return remaining<=h*3600000});return;}
      if(!settings.enabled){record.fired[key]=Array.from(new Set(record.fired[key].concat(crossed)));return;}
      if(quiet&&!urgent){if(unseen)record.newPending.push(key);return;}
      if(crossed.length||unseen){alerts.push(t);record.fired[key]=Array.from(new Set(record.fired[key].concat(crossed)));}
    });
    return {record:record,alerts:alerts};
  }
  var api={hours:hours,evaluate:evaluate};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.TaskReminders=api;
})(typeof window!=='undefined'?window:this);
