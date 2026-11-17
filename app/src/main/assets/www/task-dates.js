(function(root,factory){var api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.TaskDates=api;})(typeof window!=='undefined'?window:this,function(){
  'use strict';
  function parse(value,now){
    if(!value)return null;var d=new Date(value),current=now==null?Date.now():now;
    if(!Number.isFinite(d.getTime()))return null;
    var delta=d.getTime()-current;
    if(delta>300*86400000&&delta<430*86400000){var corrected=new Date(d.getTime());corrected.setFullYear(corrected.getFullYear()-1);var correctedDelta=corrected.getTime()-current;if(correctedDelta>0&&correctedDelta<120*86400000)d=corrected;}
    return d;
  }
  return {parse:parse};
});
