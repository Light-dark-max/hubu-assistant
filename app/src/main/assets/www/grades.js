(function(root,factory){
  'use strict';
  if(typeof module==='object'&&module.exports)module.exports=factory();
  else root.Grades=factory();
})(typeof window!=='undefined'?window:this,function(){
  'use strict';
  function normalize(value){return String(value==null?'':value).replace(/[\s\u00a0]+/g,'');}
  function publicElective(row){
    return [row.attribute,row.category].some(function(v){return /^(公选|公选课|公共选修|公共选修课)$/.test(normalize(v));});
  }
  function decimal(value){
    var text=normalize(value);
    return /^\d+(?:\.\d+)?$/.test(text)&&Number.isFinite(+text)?+text:null;
  }
  function weightedAverage(rows){
    var out={average:null,count:0,credits:0,publicCount:0,scoreSkipped:0,creditSkipped:0,unknownCount:0},sum=0;
    (rows||[]).forEach(function(row){
      if(publicElective(row)){out.publicCount++;return;}
      if(!normalize(row.attribute)&&!normalize(row.category)){out.unknownCount++;return;}
      var score=decimal(row.score),credits=decimal(row.credits);
      if(score===null||score>100){out.scoreSkipped++;return;}
      if(credits===null||credits<=0){out.creditSkipped++;return;}
      sum+=score*credits;out.credits+=credits;out.count++;
    });
    if(out.credits>0)out.average=sum/out.credits;
    return out;
  }
  return {publicElective:publicElective,weightedAverage:weightedAverage};
});
