/* Content hierarchy: date, then degree values, then estimated/missing labels.
   Reuse the app's ink/muted/teal, 12/16/24 spacing and 16px auxiliary radius.
   This controller only presents the existing daily usage calculation. */
(function () {
  'use strict';
  window.UsageChart = function (chart, popup) {
    var rows = [], selected = -1, frame = 0;
    function buttons() { return chart.querySelectorAll('.chart-column'); }
    function dateLabel(date) { return date.getFullYear()+'年'+(date.getMonth()+1)+'月'+date.getDate()+'日'; }
    function value(point) { return point ? point.value.toFixed(2)+' 度'+(point.estimated?'（估算）':'') : '暂无数据'; }
    function describe(row) {
      var total = row.l && row.a ? '合计' : '已知用量小计';
      var amount = row.l || row.a ? ((row.l?row.l.value:0)+(row.a?row.a.value:0)).toFixed(2)+' 度' : '暂无数据';
      if ((row.l&&row.l.estimated)||(row.a&&row.a.estimated)) amount+='（估算）';
      return [dateLabel(row.date), '照明：'+value(row.l), '空调：'+value(row.a), total+'：'+amount];
    }
    function close() {
      popup.hidden=true;selected=-1;
      buttons().forEach(function(button){button.classList.remove('usage-selected');button.setAttribute('aria-pressed','false');button.removeAttribute('aria-describedby');});
    }
    function position() {
      if(selected<0)return;
      var button=buttons()[selected],rect=button.getBoundingClientRect(),plot=chart.getBoundingClientRect();
      var viewport=window.visualViewport, leftEdge=viewport?viewport.offsetLeft:0, topEdge=viewport?viewport.offsetTop:0;
      var width=viewport?viewport.width:document.documentElement.clientWidth, bottom=topEdge+(viewport?viewport.height:window.innerHeight);
      var nav=document.querySelector('.bottom-nav');if(nav)bottom=Math.min(bottom,nav.getBoundingClientRect().top);
      if(plot.bottom<topEdge+8 || plot.top>bottom-8){close();return;}
      popup.style.maxWidth=Math.max(0,width-24)+'px';
      popup.style.maxHeight=Math.max(0,bottom-topEdge-16)+'px';
      var box=popup.getBoundingClientRect(),anchor=rect.bottom-22;
      button.querySelectorAll('.bar').forEach(function(bar){if(bar.getBoundingClientRect().height>0)anchor=Math.min(anchor,bar.getBoundingClientRect().top);});
      popup.style.left=Math.max(leftEdge+12,Math.min(rect.left+rect.width/2-box.width/2,leftEdge+width-box.width-12))+'px';
      popup.style.top=Math.max(topEdge+8,Math.min(anchor-box.height-12,bottom-box.height-8))+'px';
    }
    function select(index, focus) {
      if(!rows.length)return;
      index=Math.max(0,Math.min(rows.length-1,index));close();selected=index;
      var all=buttons(),button=all[index];
      all.forEach(function(b,i){b.tabIndex=i===index?0:-1;});
      button.classList.add('usage-selected');button.setAttribute('aria-pressed','true');button.setAttribute('aria-describedby',popup.id);
      popup.replaceChildren();describe(rows[index]).forEach(function(line,i){var node=document.createElement(i===0?'strong':'span');node.textContent=line;popup.appendChild(node);});
      popup.hidden=false;
      if(focus)button.focus({preventScroll:true});
      position();
    }
    chart.addEventListener('click',function(event){
      var all=buttons();if(!all.length)return;
      var target=event.target.closest('.chart-column'),index=0;
      if(target)index=Array.prototype.indexOf.call(all,target);
      else {var nearest=Infinity;all.forEach(function(button,i){var rect=button.getBoundingClientRect(),distance=Math.abs(event.clientX-rect.left-rect.width/2);if(distance<nearest){nearest=distance;index=i;}});}
      select(index,false);
    });
    chart.addEventListener('keydown',function(event){
      var button=event.target.closest('.chart-column');if(!button)return;
      var index=Array.prototype.indexOf.call(buttons(),button),next=index;
      if(event.key==='ArrowLeft')next--;else if(event.key==='ArrowRight')next++;
      else if(event.key==='Home')next=0;else if(event.key==='End')next=rows.length-1;
      else if(event.key==='Escape'){close();event.preventDefault();return;}else return;
      event.preventDefault();select(next,true);
    });
    document.addEventListener('pointerdown',function(event){if(!chart.contains(event.target))close();});
    document.addEventListener('click',function(event){if(!chart.contains(event.target))close();});
    document.addEventListener('focusin',function(event){if(!chart.contains(event.target))close();});
    function updatePosition(){if(selected>=0&&!frame)frame=requestAnimationFrame(function(){frame=0;position();});}
    window.addEventListener('scroll',updatePosition,true);window.addEventListener('resize',updatePosition);
    if(window.visualViewport){window.visualViewport.addEventListener('resize',updatePosition);window.visualViewport.addEventListener('scroll',updatePosition);}
    return {
      close:close,
      setRows:function(next){
        close();rows=next;
        buttons().forEach(function(button,i){button.tabIndex=i===rows.length-1?0:-1;button.setAttribute('aria-label',describe(rows[i]).join('，'));button.setAttribute('aria-pressed','false');});
      }
    };
  };
})();
