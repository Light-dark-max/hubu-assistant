(function () {
  'use strict';
  var native = !!window.EnergyApp, state, selectedWeek = 0, range = 7, timer, confirmAction, lastFocus;
  var $ = function (id) { return document.getElementById(id); };
  var text = function (id, value) { $(id).textContent = value == null ? '' : value; };
  var days = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'];
  var colors = ['#bd76b2','#4cabc7','#cd9b3f','#b7a346','#d28d73','#ab75c3','#62a78b','#5479bd'];
  var demoCache, editor, editorFocus, selectedGradeTerm = null, agendaDay = new Date().getDay() || 7;
  var timetableView = window.matchMedia('(max-width:450px)').matches ? 'agenda' : 'week';
  var feeChart = UsageChart($('fees-chart'), $('usage-popup'));
  var taskSeenCache={};
  function updateTaskBadge(d,markSeen){
    var scope='hubu-task-seen:'+JSON.stringify([state.mode==='demo'?'demo':'live',(d.tasksAccountId||'local')+':'+(d.tasksStorageEpoch||0)]);
    var seen=taskSeenCache[scope];
    if(!seen){try{seen=JSON.parse(localStorage.getItem(scope)||'[]')}catch(e){seen=[]}if(!Array.isArray(seen))seen=[];taskSeenCache[scope]=seen;}
    if(markSeen){seen=TaskBadge.pending(d.tasks,Date.now()).map(TaskBadge.key);taskSeenCache[scope]=seen;try{localStorage.setItem(scope,JSON.stringify(seen))}catch(e){}}
    var count=TaskBadge.unread(d.tasks,seen,Date.now()).length;
    text('nav-task-count',count>99?'99+':count);$('nav-task-count').hidden=!count;
  }
  function pad(n) { return String(n).padStart(2,'0'); }
  function iso(d) { return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate()); }
  function taskDate(value,now){return TaskDates.parse(value,now);}
  function stamp(time) { var d=taskDate(time);return d?(d.getMonth()+1)+'/'+d.getDate()+' '+pad(d.getHours())+':'+pad(d.getMinutes()):'尚未查询'; }
  function number(v) { return v!=null&&v!==''&&Number.isFinite(Number(v))?Number(v).toFixed(2):'— —'; }
  function demoData() {
    var start=new Date();start.setDate(start.getDate()-((start.getDay()+6)%7)-49);
    var courses=[], names=['热力学与统计力学','物理学史','量子力学','薄膜物理与技术','电子线路','思想政治理论'];
    [[2,1,2,0],[3,1,2,1],[1,3,4,2],[2,3,4,3],[4,3,4,4],[5,3,4,0],[4,5,6,2],[5,5,6,5],[1,7,8,4],[4,7,8,3],[1,9,10,1]].forEach(function(x,i){courses.push({weekday:x[0],sections:pad(x[1])+'-'+pad(x[2]),name:names[x[3]],weeks:'1-16',teacher:'示例教师',room:'示例教学楼-'+(201+i)})});
    var history=[], airHistory=[];
    for(var n=13;n>=0;n--){var d=new Date();d.setDate(d.getDate()-n);d.setHours(18,30,0,0);history.push({energy:String(51.22+n*3.5+(n%3)*.3),money:'29.09',time:d.getTime()});airHistory.push({energy:String(36.21+n*1.7+(n%2)*.2),money:'20.57',time:d.getTime()});}
    var demoEpoch;try{demoEpoch=+sessionStorage.getItem('hubu-demo-epoch');if(!demoEpoch){demoEpoch=Date.now();sessionStorage.setItem('hubu-demo-epoch',demoEpoch)}}catch(e){demoEpoch=Date.now()}var now=demoEpoch;
    var sample={lighting:{energy:'51.22',money:'29.09',time:now},aircon:{energy:'36.21',money:'20.57',time:now},water:{money:'13.377',time:now},room:'示例宿舍 · A301',campus:'示例校区',timetable:courses,term:'示例学期',termStart:iso(start),timetableNotes:['示例实验课 · 时间待安排'],timeMode:'示例作息',timetableSlots:[{sections:[1,2],start:'08:00',end:'09:40'},{sections:[3,4],start:'09:55',end:'11:35'},{sections:[5,6],start:'14:00',end:'15:40'},{sections:[7,8],start:'15:55',end:'17:35'},{sections:[9,10,11],start:'19:00',end:'21:35'}],history:history,airHistory:airHistory,tasksLastSync:now,tasks:[{id:'demo-hw-1',kind:'homework',course:'量子力学',title:'第三章课后作业',deadline:new Date(now+1000*60*60*5).toISOString(),status:'未完成',detail:'提交前请检查计算题步骤'},{id:'demo-exam-1',kind:'exam',course:'热力学与统计力学',title:'期中考试',deadline:new Date(now+1000*60*60*27).toISOString(),status:'未开始',detail:'线上考试 · 90 分钟'},{id:'demo-notice-1',kind:'notice',course:'课程通知',title:'物理学史课程通知',deadline:'',status:'未读',detail:'老师发布了新的课程安排',publishedAt:new Date(now).toISOString()},{id:'demo-old-notice',kind:'notice',course:'旧课程',title:'2024 年课程安排通知',deadline:'',status:'未读',publishedAt:'2024-10-10T09:00:00+08:00',oldNotice:true}]};
    sample.tasksCourses=[{id:'demo-current',name:'量子力学',period:'当前学期 · 示例',included:true},{id:'demo-old',name:'旧课程',period:'2024年秋季 · 开课 2024-09-01',old:true,included:false}];sample.tasksCurrentTerm='当前学期';sample.tasksCourseScanComplete=true;return sample;
  }
  function initial() {
    if(native){try{return JSON.parse(window.EnergyApp.getState())}catch(e){}}
    var saved={};try{saved=JSON.parse(localStorage.getItem('hubu-campus-preview')||'{}')}catch(e){}
    return {mode:new URLSearchParams(location.search).get('demo')==='1'?'demo':saved.mode||'empty',threshold:saved.threshold==null?10:saved.threshold,termStart:saved.termStart||'',lighting:{},aircon:{},water:{},timetable:saved.timetable||[],term:saved.term||'',timetableRevision:saved.timetableRevision||0,tasksDisplayRules:saved.tasksDisplayRules||{},tasks:saved.tasks||[],tasksLastSync:saved.tasksLastSync||0,tasksReminderEnabled:saved.tasksReminderEnabled!==false,tasksReminderStart:saved.tasksReminderStart||'24, 6, 1',tasksReminderEnd:saved.tasksReminderEnd||'24, 1',tasksLoggedIn:false,native:false};
  }
  function data() {var d=state.mode==='demo'?(demoCache||(demoCache=demoData())):state;return native&&state.mode!=='demo'?d:TaskDisplay.apply(d,state.tasksDisplayRules||{});}
  function toast(value) { text('toast',value);$('toast').hidden=false;clearTimeout(timer);timer=setTimeout(function(){$('toast').hidden=true},3000); }
  function modal(title,message,confirm,callback) { lastFocus=document.activeElement;text('modal-title',title);text('modal-text',message);text('modal-confirm',confirm||'知道了');$('modal-cancel').hidden=!callback;confirmAction=callback;$('modal').hidden=false;$('modal-confirm').focus(); }
  function closeModal() { $('modal').hidden=true;if(lastFocus)lastFocus.focus(); }
  function persist() { localStorage.setItem('hubu-campus-preview',JSON.stringify({mode:state.mode,threshold:state.threshold,termStart:state.termStart,timetable:state.timetable,term:state.term,timetableRevision:state.timetableRevision,tasksDisplayRules:state.tasksDisplayRules||{},tasks:state.tasks,tasksLastSync:state.tasksLastSync,tasksReminderEnabled:state.tasksReminderEnabled,tasksReminderStart:state.tasksReminderStart,tasksReminderEnd:state.tasksReminderEnd})); }
  function action(name,value) {
    feeChart.close();
    if(native){window.EnergyApp.action(name,String(value==null?'':value));return;}
    if(name==='demo'||name==='live'){state.mode=name==='demo'?'demo':'empty';selectedWeek=0;persist();render();return;}
    if(name==='threshold'){state.threshold=+value;persist();render();toast('提醒阈值已保存');return;}
    if(name==='tasksLogin'){if(!native){modal('登录学习通','安装安卓应用后，会在官方学习通页面完成登录。登录信息只保存在学习通 WebView 会话中。','知道了');return;}window.EnergyApp.action('tasksLogin','');return;}
    if(name==='tasksSync'){if(!native){state.tasksLastSync=Date.now();persist();render();toast('预览模式：已刷新示例待办');return;}window.EnergyApp.action('tasksSync','');return;}
    if(name==='termStart'){state.termStart=value;persist();selectedWeek=0;render();toast('学期日期已保存');return;}
    if(name==='clear'){state.mode='empty';state.termStart='';state.timetable=[];state.term='';state.gradeTerms=[];state.gradeResults={};state.tasks=[];state.tasksLastSync=0;state.timetableRevision=(state.timetableRevision||0)+1;persist();render();return;}
    if(name==='tasksClear'||name==='tasksLogout'){state.tasks=[];state.tasksLastSync=0;persist();render();toast('学习通待办缓存已清除');return;}
    if(name==='tasksWidget'){modal('学习通待办小组件','安卓应用中可添加学习通待办小组件，仅显示上次同步缓存，不在后台联网。');return;}
    if(name==='widget'){modal('今日课表小组件','安卓应用中可添加今日课程列表，点击小组件直接打开完整课表。课表导入后离线使用。');return;}
    modal(name==='timetable'?'导入学期课表':'安卓应用功能',name==='timetable'?'安装应用后，登录教务系统并选择学期、周次「全部」。导入一次即可本机查看一学期，之后可在本机编辑调课和课程颜色。':'浏览器只预览界面。安卓应用的总刷新依次查询照明、空调、水费，并分别保存结果。');
  }
  window.openPage=function(name){if(!document.getElementById('page-'+name))return;feeChart.close();document.querySelectorAll('.page').forEach(function(p){p.hidden=p.id!=='page-'+name});document.querySelectorAll('button[data-page]').forEach(function(b,index){var active=b.dataset.page===((name==='fees'||name==='widgets')?'home':name);b.classList.toggle('active',active);if(active){b.setAttribute('aria-current','page');document.querySelector('.nav-lens').style.transform='translateX('+(index*100)+'%)';}else b.removeAttribute('aria-current')});text('page-title',{home:'今天',tasks:'学习通待办',widgets:'桌面小组件',fees:'费用',timetable:'课表',grades:'成绩',settings:'设置'}[name]);document.body.dataset.screen=name;if(name==='tasks')updateTaskBadge(data(),true);window.scrollTo(0,0);};
  function taskKindLabel(kind){return kind==='homework'?'作业':kind==='exam'?'考试':'通知';}
  function taskTimeLabel(value){if(!value)return '时间未提供';var d=taskDate(value);if(!d)return '时间未提供';var delta=d.getTime()-Date.now(),hours=Math.ceil(delta/3600000);if(delta<=0)return '已过期';if(delta<3600000)return '1 小时内';if(hours<24)return '还有 '+hours+' 小时';return '还有 '+Math.ceil(hours/24)+' 天';}
  function runTaskReminders(d){
    if(state.mode==='demo'||!d.tasksAccountId||!d.tasksLastSync||!d.tasksLoggedIn||!d.tasksBaselineReady||d.tasksSyncStatus==='refreshing'||d.tasksForeground===false||document.hidden)return;
    var key='hubu-reminder:'+d.tasksAccountId+':'+(d.tasksStorageEpoch||0),previous;
    try{previous=JSON.parse(localStorage.getItem(key)||'null')}catch(e){previous=null}
    try{
      var result=TaskReminders.evaluate(d.tasks,previous,{enabled:d.tasksReminderEnabled!==false,homework:d.tasksReminderStart||'24, 6, 1',exam:d.tasksReminderEnd||'24, 1'},Date.now());
      localStorage.setItem(key,JSON.stringify(result.record));
      if(result.alerts.length)toast('学习通：'+result.alerts.length+' 项待办需要留意 · '+result.alerts[0].title);
    }catch(e){}
  }
  function renderTasks(d){
    var tasks=d.tasks||[], now=Date.now(), pending=TaskBadge.pending(tasks,now), counts={all:pending.length,homework:0,exam:0,notice:0};pending.forEach(function(t){counts[t.kind]=(counts[t.kind]||0)+1;});
    updateTaskBadge(d,false);runTaskReminders(d);
    var busy=d.tasksSyncStatus==='refreshing';$('tasks-refresh').disabled=busy;$('tasks-refresh').querySelector('span').textContent=busy?'正在同步…':'刷新待办';text('tasks-feedback',d.tasksSyncMessage||'');$('tasks-feedback').hidden=!d.tasksSyncMessage;$('tasks-feedback').className='feedback '+(d.tasksSyncStatus==='partial'?'error':d.tasksSyncStatus||'idle');
    text('home-task-count',pending.length?pending.length+' 项待办':'暂无待办');text('home-task-sync',d.tasksLastSync?'上次同步 '+stamp(d.tasksLastSync):'尚未同步');
    var preview=$('home-task-preview');preview.replaceChildren();
    pending.slice().sort(function(a,b){return (a.deadline||'9999').localeCompare(b.deadline||'9999')}).slice(0,2).forEach(function(t){var row=document.createElement('div');row.className='task-mini-row';var dot=document.createElement('i');dot.className='task-dot '+t.kind;var copy=document.createElement('span');copy.textContent=t.title;var time=document.createElement('small');time.textContent=t.kind==='notice'?'未读':taskTimeLabel(t.deadline);row.append(dot,copy,time);preview.appendChild(row);});
    if(!pending.length){var empty=document.createElement('p');empty.className='task-mini-empty';empty.textContent='打开助手后同步作业、考试与通知';preview.appendChild(empty);}
    var urgent=pending.filter(function(t){var date=taskDate(t.deadline,now),remaining=date?date.getTime()-now:0;return (t.kind==='homework'||t.kind==='exam')&&remaining>0&&remaining<=86400000}).sort(function(a,b){var ad=taskDate(a.deadline,now),bd=taskDate(b.deadline,now);return (ad?ad.getTime():Infinity)-(bd?bd.getTime():Infinity)}),nearest=urgent[0];
    $('home-task-alert').hidden=!urgent.length;if(nearest){text('home-task-alert-title','24 小时内有 '+urgent.length+' 项待办');text('home-task-alert-copy',nearest.title+' · '+taskTimeLabel(nearest.deadline)+(nearest.kind==='exam'?'开考':'截止'));}
    $('tasks-priority').hidden=!nearest;if(nearest){text('tasks-priority-title',nearest.title);text('tasks-priority-copy',(nearest.course||'学习通')+' · '+taskTimeLabel(nearest.deadline)+(nearest.kind==='exam'?'开考':'截止'));}
    ['all','homework','exam','notice'].forEach(function(k){text('task-filter-'+k+'-count',counts[k]||'');});
    var filter=document.querySelector('.task-filters button.selected'),list=$('tasks-list');filter=filter?filter.dataset.taskFilter:'all';list.replaceChildren();var shown=filter==='all'?pending:pending.filter(function(t){return t.kind===filter});
    shown.slice().sort(function(a,b){return (a.deadline||'9999').localeCompare(b.deadline||'9999')}).forEach(function(t){var card=document.createElement('article');card.className='task-card '+t.kind+(t===nearest?' task-urgent':'');var top=document.createElement('div');top.className='task-card-top';var badge=document.createElement('span');badge.className='task-badge '+t.kind;badge.textContent=taskKindLabel(t.kind);var time=document.createElement('strong');time.textContent=t.kind==='notice'?'未读':taskTimeLabel(t.deadline);top.append(badge,time);var title=document.createElement('h3');title.textContent=t.title;var meta=document.createElement('p');meta.textContent=(t.course||'学习通')+' · '+(t.status||'待处理');card.append(top,title,meta);if(t.deadline){var exact=document.createElement('small');exact.className='task-exact-time';exact.textContent=(t.kind==='exam'?'开考时间 ':'截止时间 ')+stamp(t.deadline);card.appendChild(exact);}if(t.detail){var detail=document.createElement('small');detail.textContent=t.detail;card.append(detail);}if(t.publishedAt){var published=document.createElement('small');published.textContent='发布时间 '+String(t.publishedAt).slice(0,10)+' '+stamp(t.publishedAt).split(' ')[1];card.append(published);}var hide=document.createElement('button');hide.className='text-button task-hide';hide.textContent='隐藏此消息';hide.setAttribute('aria-label','隐藏：'+t.title);hide.onclick=function(){setTaskDisplay({type:'task',key:TaskDisplay.key(t),hidden:true});};card.append(hide);list.appendChild(card);});
    var empty=TaskDisplay.emptyState(d,pending.length>0,state.mode==='demo');
    $('tasks-empty').hidden=shown.length>0;$('tasks-list').hidden=!shown.length;$('tasks-empty').querySelector('h3').textContent=empty.title;$('tasks-empty').querySelector('p').textContent=empty.copy;$('tasks-login-entry').hidden=!!d.tasksLoggedIn||state.mode==='demo';
    text('tasks-last-sync',d.tasksLastSync?'上次同步 '+stamp(d.tasksLastSync):'尚未同步');text('tasks-heading',pending.length?pending.length+' 项待办':'今天没有紧急待办');text('tasks-subheading',pending.length?'按截止时间排序，完成状态会在下次同步后更新。':'打开助手后会更新作业、考试和通知。');
    var logged=!!state.tasksLoggedIn;$('tasks-logout').hidden=!logged;d.tasksReminderEnabled=state.tasksReminderEnabled;d.tasksReminderStart=state.tasksReminderStart;d.tasksReminderEnd=state.tasksReminderEnd;text('tasks-login-state',logged?'已连接':'未连接');text('tasks-account-copy',logged?'学习通可以长期保持登录；每次打开助手更新待办。':'学习通登录后可长期保持登录，每次打开助手更新作业、考试和未读通知。');text('tasks-login-button',logged?'重新登录学习通':'登录学习通');if(!document.activeElement.closest('.task-settings')){$('tasks-reminder-enabled').checked=d.tasksReminderEnabled!==false;$('tasks-reminder-start').value=d.tasksReminderStart||'24, 6, 1';$('tasks-reminder-end').value=d.tasksReminderEnd||'24, 1';}
    renderTaskManagement(d);renderWidgetPreviews(d,pending);
  }
  function setTaskDisplay(change){
    if(native&&state.mode!=='demo'){action('tasksVisibility',JSON.stringify(change));return;}
    state.tasksDisplayRules=TaskDisplay.change(state.tasksDisplayRules,change);persist();renderTasks(data());toast('显示设置已保存');
  }
  function renderTaskManagement(d){
    if(document.activeElement.closest('#tasks-management select'))return;
    var courses=d.tasksCourses||[],suppressed=d.tasksSuppressed||[];
    text('tasks-hidden-count',suppressed.length?supressedLabel(suppressed.length):'课程范围 · 隐藏消息');
    text('tasks-period-note',(d.tasksCurrentTerm||'当前学期')+' · '+(d.tasksCourseScanComplete?'已完成首次扫描，后续跳过往期课程':'下一次完整扫描会识别课程时期'));
    $('tasks-hide-old-notices').checked=d.tasksHideOldNotices!==false;
    text('tasks-course-count',courses.filter(function(c){return c.included}).length+' / '+courses.length+' 门同步');
    var list=$('tasks-course-list');list.replaceChildren();
    courses.forEach(function(c){var row=document.createElement('div');row.className='task-course-row';var copy=document.createElement('div'),name=document.createElement('strong'),period=document.createElement('small');name.textContent=c.name;period.textContent=(c.period||'时期未知，暂保留')+' · '+(c.included?'同步中':'已排除');copy.append(name,period);var select=document.createElement('select');select.setAttribute('aria-label',c.name+'同步范围');[['auto','自动判断'],['include','继续同步'],['exclude','排除此课']].forEach(function(o){var option=document.createElement('option');option.value=o[0];option.textContent=o[1];select.append(option);});select.value=c.mode||'auto';select.onchange=function(){setTaskDisplay({type:'course',id:c.id,mode:select.value});};row.append(copy,select);list.append(row);});
    if(!courses.length){var note=document.createElement('p');note.className='small-note';note.textContent='同步后显示课程及开课时期。';list.append(note);}
    text('tasks-suppressed-count',suppressed.length+' 条');list=$('tasks-suppressed-list');list.replaceChildren();
    suppressed.forEach(function(t){var row=document.createElement('div');row.className='task-suppressed-row';var copy=document.createElement('div'),name=document.createElement('strong'),reason=document.createElement('small');name.textContent=t.title;reason.textContent=t.hiddenReason+(t.publishedAt?' · '+t.publishedAt.slice(0,10):'');copy.append(name,reason);row.append(copy);
      if(t.hiddenReason==='手动隐藏'){var restore=document.createElement('button');restore.className='text-button';restore.textContent='恢复';restore.onclick=function(){setTaskDisplay({type:'task',key:TaskDisplay.key(t),hidden:false});};row.append(restore);}list.append(row);});
    if(!suppressed.length){var empty=document.createElement('p');empty.className='small-note';empty.textContent='没有隐藏的消息。';list.append(empty);}
    $('tasks-restore-hidden').hidden=!suppressed.some(function(t){return t.hiddenReason==='手动隐藏'});
  }
  function supressedLabel(n){return '已收起 '+n+' 条';}
  function renderWidgetPreviews(d,pending){
    var now=new Date(),courses=Schedule.today(d.timetable||[],d.termStart,now),week=Schedule.currentWeek(d.termStart,now);
    text('widget-preview-day',days[now.getDay()||7]);text('widget-preview-week',week>0?'第 '+week+' 周':'学期课表');
    var box=$('widget-course-preview');box.replaceChildren();
    courses.slice(0,3).forEach(function(c,index){var row=courseRow(c,d);row.classList.add('widget-course-row');var accents=['#19a8dc','#d9af16','#ec59b8','#b969df'];var original=/^#[0-9a-f]{6}$/i.test(c.color||'')?c.color:accents[index%4];row.style.setProperty('--course-accent',original);row.style.setProperty('--course-tint',Schedule.colorStyle(original).background);box.appendChild(row);});
    if(!courses.length){var empty=document.createElement('p');empty.className='widget-empty';empty.textContent=(d.timetable||[]).length?'今天没有已安排的课程':'导入课表后显示今日课程';box.appendChild(empty);}
    box=$('widget-tasks-preview');box.replaceChildren();text('widget-task-count',pending.length+' 项待办');
    pending.slice().sort(function(a,b){return (a.deadline||'9999').localeCompare(b.deadline||'9999')}).slice(0,3).forEach(function(t){var row=document.createElement('div');row.className='widget-task-row '+t.kind;var icon=document.createElement('span');icon.className='widget-task-kind';icon.textContent=taskKindLabel(t.kind);var copy=document.createElement('div'),title=document.createElement('strong'),meta=document.createElement('small');title.textContent=t.title;meta.textContent=(t.course||'学习通')+' · '+(t.status||'待处理');copy.append(title,meta);var time=document.createElement('b');time.textContent=t.kind==='notice'?'未读':taskTimeLabel(t.deadline);row.append(icon,copy,time);box.appendChild(row);});
    if(!pending.length){var empty=document.createElement('p');empty.className='widget-empty';empty.textContent='暂无待办 · 打开助手后同步';box.appendChild(empty);}
    text('widget-task-cache',d.tasksLastSync?'更新于 '+stamp(d.tasksLastSync)+' · 本机缓存':'尚未同步学习通');
  }
  function renderGrades() {
    var demo=state.mode==='demo', terms=demo?[{value:'',label:'全部学期'},{value:'2025-2026-2',label:'2025-2026-2'},{value:'2025-2026-1',label:'2025-2026-1'}]:state.gradeTerms||[], select=$('grade-term'), busy=state.gradeStatus==='refreshing';
    if(selectedGradeTerm===null)selectedGradeTerm='';
    select.replaceChildren();terms.forEach(function(t){var o=document.createElement('option');o.value=t.value;o.textContent=t.label||t.value;select.appendChild(o);});
    if(!terms.length){var placeholder=document.createElement('option');placeholder.textContent='先获取学期列表';placeholder.value='';select.appendChild(placeholder);}
    if(!terms.some(function(t){return t.value===selectedGradeTerm}))selectedGradeTerm=terms.length?terms[0].value:'';
    select.value=selectedGradeTerm;select.disabled=busy||!terms.length;
    $('grade-query').disabled=state.syncStatus==='refreshing'||!terms.length;$('grade-terms-refresh').disabled=state.syncStatus==='refreshing';
    text('grade-terms-refresh',terms.length?'更新学期':'获取学期');text('grade-query',busy?'查询中…':'查询成绩');$('grade-query').setAttribute('aria-busy',String(busy));
    $('grade-feedback').hidden=!demo&&!state.gradeMessage;text('grade-feedback',demo?'示例成绩 · 以下均为虚构数据':state.gradeMessage||'');$('grade-feedback').className='feedback '+(state.gradeStatus||'idle');
    var result=(state.gradeResults||{})[selectedGradeTerm||'all'];
    if(demo) {var rows=[{term:'2025-2026-2',name:'示例高等数学',score:'88',credits:'4',gpa:'',attribute:'必修',examType:'正常考试'},{term:'2025-2026-2',name:'示例大学英语',score:'良',credits:'2',gpa:'',attribute:'必修',examType:'正常考试'},{term:'2025-2026-2',name:'示例公共选修课',score:'60',credits:'2',gpa:'',attribute:'公选',category:'公共选修课',examType:'正常考试'},{term:'2025-2026-1',name:'示例物理实验',score:'91',credits:'2',gpa:'',attribute:'专选',examType:'正常考试'}].filter(function(r){return !selectedGradeTerm||r.term===selectedGradeTerm});result={rows:rows,time:Date.now(),summary:{count:String(rows.length)}};}
    var list=$('grade-list'), summary=$('grade-summary');list.replaceChildren();summary.replaceChildren();summary.hidden=!result;
    text('grade-time',result?(demo?'虚构示例 · ':'本机保存 · ')+(selectedGradeTerm||'全部学期')+' · 查询于 '+stamp(result.time):'');
    $('grade-empty').hidden=!!result&&!!result.rows.length;text('grade-empty-title',result?'本学期暂无已发布成绩':'还没有成绩记录');text('grade-empty-copy',result?'学校已返回查询结果，目前没有成绩。':terms.length?'选择学期后点击「查询成绩」。':'点击「获取学期」，在应用内登录学校教务系统。');
    $('grade-weighted').hidden=!result;
    if(!result)return;
    var weighted=Grades.weightedAverage(result.rows),skipped=[];
    text('grade-weighted-value',weighted.average===null?'—':weighted.average.toFixed(2));
    text('grade-weighted-scope',(selectedGradeTerm||'全部学期')+' · 计入 '+weighted.count+' 门 / '+Number(weighted.credits.toFixed(6))+' 学分 · 已排除公选课 '+weighted.publicCount+' 门');
    if(weighted.scoreSkipped)skipped.push('文字、空白或非百分制成绩 '+weighted.scoreSkipped+' 门');
    if(weighted.creditSkipped)skipped.push('学分缺失或无效 '+weighted.creditSkipped+' 门');
    if(weighted.unknownCount)skipped.push('课程属性和性质均缺失 '+weighted.unknownCount+' 门');
    text('grade-weighted-skipped',(weighted.average===null?'暂无可计算的非公选课。':'')+(skipped.length?'另有未计入：'+skipped.join('；')+'。当前均分仅覆盖已计入课程。':''));
    $('grade-weighted-skipped').hidden=weighted.average!==null&&!skipped.length;
    [['count','课程数'],['credits','所修学分'],['gpa','平均学分绩点'],['average','平均成绩']].forEach(function(p){var v=(result.summary||{})[p[0]];if(v==null)return;var block=document.createElement('div'),strong=document.createElement('strong'),label=document.createElement('small');strong.textContent=v;label.textContent=p[1];block.append(strong,label);summary.appendChild(block);});
    result.rows.forEach(function(r){var card=document.createElement('article');card.className='panel grade-card';var head=document.createElement('div');head.className='grade-heading';var title=document.createElement('h3');title.textContent=r.name;var score=document.createElement('strong');score.className='grade-score'+(/^\d+(\.\d+)?$/.test(r.score)&&+r.score<60?' low':'');score.textContent=r.score||'未提供';head.append(title,score);var info=document.createElement('p');info.className='small-note';info.textContent=r.term+' · '+(r.credits||'—')+' 学分 · 绩点 '+(r.gpa||'—')+' · '+(r.attribute||r.category||'属性未提供')+(Grades.publicElective(r)?'（不计入均分）':'');card.append(head,info);
      var details=document.createElement('details'),toggle=document.createElement('summary'),dl=document.createElement('dl');toggle.textContent='课程详情';[['code','课程编号'],['mark','成绩标识'],['hours','总学时'],['retakeTerm','补重学期'],['assessment','考核方式'],['examType','考试性质'],['attribute','课程属性'],['category','课程性质']].forEach(function(p){if(!r[p[0]])return;var dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=p[1];dd.textContent=r[p[0]];dl.append(dt,dd);});details.append(toggle,dl);card.appendChild(details);list.appendChild(card);});
  }
  function courseRow(c,d) { var row=document.createElement('div');row.className='course-row';var clock=document.createElement('div');clock.className='course-clock';clock.textContent=Schedule.time(c,d.timetableSlots,false);var end=document.createElement('span');end.textContent=Schedule.time(c,d.timetableSlots,true);clock.appendChild(end);var stripe=document.createElement('i');stripe.style.backgroundColor=Schedule.courseColor(c);var copy=document.createElement('div');copy.className='course-copy';var title=document.createElement('strong');title.textContent=c.name;var details=document.createElement('small');details.textContent='第'+c.sections+'节 · '+(c.room||'教室未提供')+' · '+(c.teacher||'教师未提供');copy.append(title,details);row.append(clock,stripe,copy);return row; }
  function renderToday(d) {
    var now=new Date(),week=Schedule.currentWeek(d.termStart,now),courses=Schedule.today(d.timetable,d.termStart,now),list=$('today-courses');list.replaceChildren();text('today-week',week>0?'第 '+week+' 周':'');text('today-date',(now.getMonth()+1)+'月'+now.getDate()+'日 '+days[now.getDay()||7]);
    text('today-count',courses.length?courses.length+' 门':'');
    if(!courses.length){var p=document.createElement('p');p.className='empty-message';p.textContent=!(d.timetable||[]).length?'导入课表，让今天的安排一目了然':!week?'设置学期日期后显示今日课程':week<0?'学期尚未开始':'今天没有已安排的课程';list.appendChild(p);}else courses.forEach(function(c){list.appendChild(courseRow(c,d));});
  }
  function renderTimetable(d) {
    var courses=d.timetable||[],week=Schedule.currentWeek(d.termStart,new Date());
    if(!selectedWeek)selectedWeek=week>0&&week<=30?week:1;
    var select=$('week-select');if(!select.options.length)for(var n=1;n<=30;n++){var option=document.createElement('option');option.value=n;option.textContent='第 '+n+' 周';select.appendChild(option);}select.value=selectedWeek;
    text('term-badge',courses.length?(d.term||'已导入学期')+' · 本机保存':'未导入');text('timetable-import',courses.length?'重新导入 ↗':'导入学期课表 ↗');
    $('term-setup').hidden=!!d.termStart;$('timetable-scroll').hidden=!courses.length||timetableView!=='week';$('timetable-agenda').hidden=!courses.length||timetableView!=='agenda';$('timetable-empty').hidden=!!courses.length;
    $('view-agenda').setAttribute('aria-pressed',String(timetableView==='agenda'));$('view-week').setAttribute('aria-pressed',String(timetableView==='week'));
    var grid=$('timetable-grid');grid.replaceChildren();var first=Schedule.parseDate(d.termStart);
    function cell(cls,column,row,content){var el=document.createElement('div');el.className=cls;el.style.gridColumn=String(column);el.style.gridRow=String(row);if(content!=null)el.textContent=content;grid.appendChild(el);return el;}
    cell('grid-head',1,1,'节');for(var day=1;day<=7;day++){var header=cell('grid-head',day+1,1,days[day]);if(first){var date=new Date(first);date.setDate(date.getDate()+(selectedWeek-1)*7+day-1);var small=document.createElement('small');small.textContent=(date.getMonth()+1)+'/'+date.getDate();header.appendChild(small);if(iso(date)===iso(new Date()))header.classList.add('current');}}
    var maxSection=Math.max(11,Math.min(16,Math.max.apply(null,courses.map(function(c){return Math.max.apply(null,Schedule.sections(c))}))));grid.style.gridTemplateRows='48px repeat('+maxSection+',70px)';
    for(var section=1;section<=maxSection;section++){var period=cell('grid-period',1,section+1,section);(d.timetableSlots||[]).forEach(function(slot){var ns=slot.sections||[];if(ns[0]===section&&slot.start){var time=document.createElement('small');time.textContent=slot.start;period.appendChild(time);}});for(var col=2;col<=8;col++)cell('grid-cell',col,section+1);}
    var occupied={};courses.forEach(function(c,index){if(!Schedule.inWeek(c.weeks,selectedWeek))return;var ns=Schedule.sections(c);if(!ns.length)return;var start=ns[0],end=ns[ns.length-1],key=c.weekday+':'+start,button=document.createElement('button'),colors=Schedule.colorStyle(Schedule.courseColor(c));button.className='grid-course';button.style.gridColumn=String(c.weekday+1);button.style.gridRow=(start+1)+' / '+(end+2);button.style.backgroundColor=colors.background;button.style.color=colors.foreground;if(occupied[key]){button.style.marginTop='34px';button.style.zIndex='2';}occupied[key]=true;var strong=document.createElement('strong');strong.textContent=c.name;var small=document.createElement('small');small.textContent='@'+(c.room||'教室待定')+'\n'+(c.teacher||'');small.style.whiteSpace='pre-line';button.append(strong,small);button.onclick=function(){openEditor(index)};grid.appendChild(button);});
    text('timetable-note',courses.length?'共 '+courses.length+' 条学期安排 · '+(d.timeMode||'作息以学校页面为准')+'。日期按设置的第一周换算，节假日调课以学校通知为准。单节时间未确认时仅显示节次。':'无需每天更新，导入一次保存一学期。');
    var notes=d.timetableNotes||[];$('notes-card').hidden=!notes.length;var box=$('timetable-notes');box.replaceChildren();notes.forEach(function(n){var p=document.createElement('p');p.className='notes-line';p.textContent=n;box.appendChild(p);});
    renderAgenda(d,first);
  }
  function renderAgenda(d,first) {
    var strip=$('agenda-days');strip.replaceChildren();
    for(var day=1;day<=7;day++)(function(value){var button=document.createElement('button'),small=document.createElement('small');button.type='button';button.textContent=days[value].slice(1);button.setAttribute('aria-pressed',String(value===agendaDay));button.setAttribute('aria-label',days[value]);if(first){var date=new Date(first);date.setDate(date.getDate()+(selectedWeek-1)*7+value-1);small.textContent=date.getDate();}else small.textContent='—';button.appendChild(small);button.onclick=function(){agendaDay=value;renderAgenda(d,first)};strip.appendChild(button);})(day);
    var shown=(d.timetable||[]).map(function(course,index){return {course:course,index:index}}).filter(function(item){return +item.course.weekday===agendaDay&&Schedule.inWeek(item.course.weeks,selectedWeek)}).sort(function(a,b){return Schedule.sections(a.course)[0]-Schedule.sections(b.course)[0]});
    text('agenda-date',days[agendaDay]);text('agenda-count',shown.length+' 门课程');var list=$('agenda-courses');list.replaceChildren();
    shown.forEach(function(item){var button=document.createElement('button');button.type='button';button.className='agenda-course';button.appendChild(courseRow(item.course,d));button.onclick=function(){openEditor(item.index)};list.appendChild(button);});
    if(!shown.length){var empty=document.createElement('p');empty.className='empty-message';empty.textContent='这一天没有已安排的课程';list.appendChild(empty);}
  }
  function usage(history,count) {
    var map={},now=new Date();now.setHours(0,0,0,0);var oldest=new Date(now);oldest.setDate(oldest.getDate()-count+1);
    var rows=(history||[]).filter(function(s){return Number.isFinite(+s.energy)&&Number.isFinite(+s.time)}).slice().sort(function(a,b){return a.time-b.time});
    for(var i=1;i<rows.length;i++){var a=rows[i-1],b=rows[i],delta=+a.energy-+b.energy;if(delta<0||b.time<=a.time)continue;var start=Math.floor((a.time-new Date(a.time).getTimezoneOffset()*60000)/86400000),end=Math.floor((b.time-new Date(b.time).getTimezoneOffset()*60000)/86400000),span=Math.max(1,end-start);if(span>90)continue;for(var j=0;j<span;j++){var date=new Date(b.time);date.setHours(0,0,0,0);date.setDate(date.getDate()-j);if(date<oldest||date>now)continue;var key=iso(date);if(!map[key])map[key]={value:0,estimated:false};map[key].value+=delta/span;map[key].estimated=map[key].estimated||span>1;}}
    return map;
  }
  function chart(target,d,count){
    var l=usage(d.history,count),a=usage(d.airHistory,count),rows=[],now=new Date(),max=1,total=0,samples=0,interactive=target==='fees-chart';
    for(var i=count-1;i>=0;i--){var date=new Date(now);date.setDate(date.getDate()-i);var key=iso(date),lv=l[key],av=a[key],value=(lv?lv.value:0)+(av?av.value:0);max=Math.max(max,value);total+=value;if(lv||av)samples++;rows.push({date:date,l:lv,a:av});}
    var el=$(target);if(interactive)feeChart.close();el.replaceChildren();
    rows.forEach(function(row,index){
      var col=document.createElement(interactive?'button':'div');col.className='chart-column';if(interactive)col.type='button';
      var space=document.createElement('div');space.className='chart-space';
      [['l','bar'],['a','bar air-bar']].forEach(function(pair){var point=row[pair[0]];if(!point)return;var bar=document.createElement('i');bar.className=pair[1]+(point.estimated?' estimated':'');bar.style.height=(point.value/max*100)+'%';if(!interactive)bar.title=(row.date.getMonth()+1)+'/'+row.date.getDate()+' '+(pair[0]==='l'?'照明':'空调')+' '+point.value.toFixed(2)+'度'+(point.estimated?'（估算）':'');space.appendChild(bar);});
      if(!row.l&&!row.a){var blank=document.createElement('i');blank.className='no-sample';space.appendChild(blank);}
      var label=document.createElement('small');label.textContent=count<=7||index===0||index===rows.length-1||index===Math.floor(rows.length/2)?(count>7?row.date.getMonth()+1+'/':'')+row.date.getDate():'';col.append(space,label);el.appendChild(col);
    });
    if(interactive)feeChart.setRows(rows);
    return {total:total,samples:samples,l:l,a:a};
  }
  function estimate(current,map){var values=Object.keys(map).map(function(k){return map[k].value});var average=values.reduce(function(a,b){return a+b},0)/values.length;return current.energy!=null&&average>0?'按近期记录约可用 '+(+current.energy/average).toFixed(1)+' 天':'积累查询记录后估算可用天数';}
  function renderHistory(d){var box=$('history-list');box.replaceChildren();var all=[];[['照明',d.history],['空调',d.airHistory]].forEach(function(pair){(pair[1]||[]).forEach(function(s){all.push({name:pair[0],value:s});});});all.sort(function(a,b){return b.value.time-a.value.time});if(!all.length){var p=document.createElement('p');p.className='small-note';p.textContent='还没有成功查询记录。';box.appendChild(p);}all.slice(0,20).forEach(function(item){var row=document.createElement('div');row.className='history-row';row.textContent=item.name+' '+number(item.value.energy)+' 度 · ¥ '+number(item.value.money);var time=document.createElement('span');time.textContent=stamp(item.value.time);row.appendChild(time);box.appendChild(row);});}
  function render(){
    var d=data(),l=d.lighting||d.current||{},a=d.aircon||{},w=d.water||{},demo=state.mode==='demo',refreshing=state.syncStatus==='refreshing';
    $('demo-banner').hidden=!demo;$('demo-button').hidden=demo;
    text('home-lighting',number(l.energy));text('home-air',number(a.energy));text('home-water',w.money==null?'— —':String(w.money));
    text('lighting-energy',number(l.energy));text('lighting-money',number(l.money));text('air-energy',number(a.energy));text('air-money',number(a.money));text('water-money',w.money==null?'— —':String(w.money));
    [['lighting',l],['air',a],['water',w]].forEach(function(pair){var key=pair[0]==='air'?'aircon':pair[0];text('home-'+pair[0]+'-state',demo?'示例':(state.feeStates||{})[key]||(pair[1].time?stamp(pair[1].time):'尚未查询'));text(pair[0]+'-time',demo?'虚构示例':pair[1].time?'查询于 '+stamp(pair[1].time):'尚未查询');});
    text('home-room',(d.campus||'湖北大学')+(d.room?' · '+d.room:''));
    text('header-room',d.room||'尚未设置');
    ['sync-feedback','fee-feedback'].forEach(function(id){$(id).hidden=!state.syncMessage;text(id,state.syncMessage||'');$(id).className='feedback '+(state.syncStatus||'idle');});
    document.querySelectorAll('[data-action]').forEach(function(b){if(b.dataset.action.indexOf('tasks')!==0)b.disabled=refreshing});$('sync-button').querySelector('span').textContent=refreshing?'正在依次查询…':state.loginRequired?'重新登录后查询费用':'刷新照明、空调和水费';$('sync-button').setAttribute('aria-busy',String(refreshing));$('home-refresh').classList.toggle('refreshing',refreshing);
    var threshold=state.threshold==null?10:state.threshold,low=[];if(l.energy!=null&&+l.energy<threshold)low.push('照明');if(a.energy!=null&&+a.energy<threshold)low.push('空调');$('low-warning').hidden=!low.length;text('low-warning',(demo?'示例提示：':'')+low.join('、')+'低于 '+threshold+' 度，请留意余额。');
    var seven=chart('home-chart',d,7);text('seven-summary',seven.samples?'有记录的日期合计 '+seven.total.toFixed(2)+' 度 · '+seven.samples+' 天有样本':'还没有足够记录，成功查询后逐步生成用电趋势');
    var usageData=chart('fees-chart',d,range);text('usage-summary',usageData.samples?'样本合计 '+usageData.total.toFixed(2)+' 度 · '+usageData.samples+' 天有数据':'暂无可计算的相邻查询记录');text('lighting-estimate',estimate(l,seven.l));text('air-estimate',estimate(a,seven.a));
    $('threshold').value=threshold;$('term-start').value=state.termStart||'';text('term-info',(d.term||'尚未导入课表')+(d.timetableImported?' · 导入于 '+stamp(d.timetableImported):''));renderToday(d);renderTimetable(d);renderHistory(d);renderGrades();renderTasks(d);
  }
  function editorError(message) { text('editor-error',message);$('editor-error').hidden=!message; }
  function editorBusy(busy) { editor.busy=busy;['course-save','course-delete','editor-close'].forEach(function(id){$(id).disabled=busy}); }
  function closeEditor() { if(editor&&editor.busy)return;$('course-editor').hidden=true;document.body.style.overflow='';editor=null;if(editorFocus&&editorFocus.isConnected)editorFocus.focus();else $('course-add').focus(); }
  function chooseColor(value) { $('course-color').value=value;text('course-color-value',value);var style=Schedule.colorStyle(value),preview=$('course-color-preview');preview.style.backgroundColor=style.background;preview.style.color=style.foreground;preview.style.borderColor=value;document.querySelectorAll('.color-swatch').forEach(function(b){b.setAttribute('aria-pressed',String(b.dataset.color===value))}); }
  function changeScope() { var current=$('course-scope').value==='week';if(current){editor.allWeeks=$('course-weeks').value;editor.allPattern=$('course-pattern').value;$('course-weeks').value=String(editor.week);$('course-pattern').value='all';}else{$('course-weeks').value=editor.allWeeks||'1-16';$('course-pattern').value=editor.allPattern;}$('course-weeks').readOnly=current;$('course-pattern').disabled=current;text('course-pattern-note',current?'仅当前周不设置单双周；如需设置，请将修改范围切换为「这条安排的全部周次」。':'可填 1–30 周；区间用短横线，不连续周次用逗号。单双周按学期周次筛选。');text('scope-note',current?'只调整第 '+editor.week+' 周，其他周保留原安排。':'修改当前这条安排的所有所填周次；同名课程的其他安排保持各自设置。'); }
  function openEditor(index) {
    var d=data(),c=index>=0?d.timetable[index]:null,ns=c?Schedule.sections(c):[1,2];
    var pattern={weeks:c?c.weeks:'1-16',pattern:'all'};try{pattern=Schedule.weekPattern(pattern.weeks)}catch(e){}
    editorFocus=document.activeElement;editor={index:index,original:c?JSON.parse(JSON.stringify(c)):null,expected:JSON.stringify(d.timetable||[]),revision:state.timetableRevision||0,term:state.term||'',demo:state.mode==='demo',week:selectedWeek,allWeeks:pattern.weeks,allPattern:pattern.pattern,busy:false};
    text('editor-title',c?'编辑课程':'添加课程');text('course-save',c?'保存修改':'添加课程');$('course-delete').hidden=!c;
    $('course-name').value=c?c.name:'';$('course-room').value=c?c.room||'':'';$('course-teacher').value=c?c.teacher||'':'';$('course-weekday').value=c?c.weekday:(new Date().getDay()||7);$('course-start').value=ns[0];$('course-end').value=ns[ns.length-1];
    $('course-weeks').value=editor.allWeeks;$('course-pattern').value=editor.allPattern;$('course-scope').value=c?'week':'all';changeScope();chooseColor(Schedule.courseColor(c||{name:''}));editorError('');editorBusy(false);$('course-editor').hidden=false;document.body.style.overflow='hidden';$('editor-title').setAttribute('tabindex','-1');$('editor-title').focus();document.querySelector('#course-editor .editor-card').scrollTop=0;
  }
  function saveCourse(remove) {
    if(!editor||editor.busy)return;
    try {
      var start=+$('course-start').value,end=+$('course-end').value;if(!remove&&start>end)throw new Error('结束节次不能早于开始节次');
      var ns=[];for(var n=start;n<=end;n++)ns.push(pad(n));
      if((state.mode==='demo')!==editor.demo)throw new Error('模式已切换，请关闭编辑后重试');
      var d=data();if(JSON.stringify(d.timetable||[])!==editor.expected)throw new Error('课表已有变化，请关闭编辑后重试');
      var next=Schedule.editCourses(d.timetable||[],{index:editor.index,original:editor.original,scope:$('course-scope').value,week:editor.week,remove:remove,course:{name:$('course-name').value,teacher:$('course-teacher').value,room:$('course-room').value,weekday:+$('course-weekday').value,sections:ns.join('-'),weeks:remove?'':Schedule.applyWeekPattern($('course-weeks').value,$('course-pattern').value),color:$('course-color').value}});
      editorError('');
      if(editor.demo){demoCache.timetable=next;render();closeEditor();toast('示例课表已调整，不影响真实课表');return;}
      if(native){editorBusy(true);window.EnergyApp.action('timetableEdit',JSON.stringify({expected:editor.expected,revision:editor.revision,term:editor.term,next:next}));return;}
      var previous=state.timetable;state.timetable=next;try{persist()}catch(e){state.timetable=previous;throw new Error('本机存储失败，请重试');}render();closeEditor();toast('课表已保存');
    }catch(e){editorError(e.message||'保存失败，请重试');}
  }
  window.courseEditResult=function(result){if(!editor)return;editorBusy(false);if(result.ok){closeEditor();toast('课表已保存，今日课程和小组件已同步');}else editorError(result.message||'保存失败，请重试');};
  for(var section=1;section<=16;section++)['course-start','course-end'].forEach(function(id){var option=document.createElement('option');option.value=section;option.textContent='第 '+section+' 节';$(id).appendChild(option)});
  colors.forEach(function(color,index){var b=document.createElement('button');b.type='button';b.className='color-swatch';b.dataset.color=color;b.style.backgroundColor=color;b.setAttribute('aria-label','预设颜色 '+(index+1)+' '+color);b.onclick=function(){chooseColor(color)};$('course-swatches').appendChild(b);});
  $('course-add').onclick=function(){openEditor(-1)};$('editor-close').onclick=closeEditor;$('course-scope').onchange=changeScope;$('course-color').oninput=function(){chooseColor(this.value)};
  $('course-weeks').onchange=function(){if(!/[单双]/.test(this.value))return;try{var pattern=Schedule.weekPattern(this.value);this.value=pattern.weeks;$('course-pattern').value=pattern.pattern;}catch(e){}};
  $('course-form').onsubmit=function(e){e.preventDefault();saveCourse(false)};
  $('course-delete').onclick=function(){modal('删除课程？',$('course-scope').value==='week'?'只删除第 '+editor.week+' 周这门课程的这次安排，其他周保留。':'删除这条安排的全部周次。','删除',function(){saveCourse(true)})};
  window.receiveState=function(value){var changed=state&&state.term!==value.term;state=value;if(changed)selectedWeek=0;render();};
  document.querySelectorAll('[data-page],[data-go]').forEach(function(b){b.onclick=function(){window.openPage(b.dataset.page||b.dataset.go)}});
  document.querySelectorAll('[data-action]').forEach(function(b){b.onclick=function(){action(b.dataset.action)}});
  document.querySelectorAll('[data-task-filter]').forEach(function(b){b.onclick=function(){document.querySelectorAll('[data-task-filter]').forEach(function(n){n.classList.toggle('selected',n===b);n.setAttribute('aria-selected',String(n===b))});renderTasks(data());};});
  ['timetable-import','empty-import','settings-import'].forEach(function(id){$(id).onclick=function(){if((data().timetable||[]).length)modal('重新导入课表？','导入成功后会替换本机课表，包括手动调整的课程和颜色。','继续导入',function(){action('timetable')});else action('timetable')}});
  ['widget-button','settings-widget'].forEach(function(id){$(id).onclick=function(){window.openPage('widgets')}});
  ['demo-button','settings-demo'].forEach(function(id){$(id).onclick=function(){action('demo');window.openPage('home')}});
  $('exit-demo').onclick=function(){action('live')};
  $('library-button').onclick=function(){
    if(!native){modal('图书馆预约 · 微信','预约页面需要微信授权。安卓应用可复制预约链接并打开微信；在微信「文件传输助手」粘贴发送，点击链接后预约。');return;}
    modal('图书馆预约 · 微信','预约页面需要在微信内打开。点击后会复制预约链接并打开微信，请在「文件传输助手」粘贴发送，再点击链接进入预约。','复制链接并打开微信',function(){action('libraryWechat')});
  };
  $('grade-term').onchange=function(){selectedGradeTerm=this.value;renderGrades();};
  $('grade-query').onclick=function(){if(state.mode==='demo'){toast('当前为虚构示例，退出示例后可查询真实成绩');return;}if(native)action('grades',selectedGradeTerm||'');else modal('应用内成绩查询','安装安卓应用后，在这里选择学期并查询。首次登录会在内置教务页面完成，成功后自动返回成绩页。');};
  $('grade-terms-refresh').onclick=function(){if(state.mode==='demo'){toast('示例学期列表不访问学校');return;}if(native)action('gradeTerms');else modal('应用内教务登录','安卓应用会读取学校提供的真实学期列表，登录后自动返回这里选择学期。');};
  $('week-select').onchange=function(){selectedWeek=+$('week-select').value;renderTimetable(data());};
  $('view-agenda').onclick=function(){timetableView='agenda';renderTimetable(data())};$('view-week').onclick=function(){timetableView='week';renderTimetable(data())};
  $('prev-week').onclick=function(){selectedWeek=Math.max(1,selectedWeek-1);renderTimetable(data())};$('next-week').onclick=function(){selectedWeek=Math.min(30,selectedWeek+1);renderTimetable(data())};$('week-now').onclick=function(){selectedWeek=0;renderTimetable(data())};
  document.querySelectorAll('[data-range]').forEach(function(b){b.onclick=function(){range=+b.dataset.range;document.querySelectorAll('[data-range]').forEach(function(n){n.classList.toggle('selected',n===b)});render();}});
  $('term-form').onsubmit=function(e){e.preventDefault();var value=$('term-start').value,date=Schedule.parseDate(value);if(!date||date.getDay()!==1){toast('请选择第一周的周一日期');return;}action('termStart',value);};
  $('threshold-form').onsubmit=function(e){e.preventDefault();var value=$('threshold').value,n=+value;if(value===''||!Number.isFinite(n)||n<0||n>1000){toast('请输入 0–1000 之间的数值');return;}action('threshold',n);};
  $('tasks-reminder-save').onclick=function(){try{var hw=TaskReminders.hours($('tasks-reminder-start').value).join(', '),exam=TaskReminders.hours($('tasks-reminder-end').value).join(', '),enabled=$('tasks-reminder-enabled').checked;if(native){action('tasksReminder',JSON.stringify({enabled:enabled,homework:hw,exam:exam}));return;}state.tasksReminderEnabled=enabled;state.tasksReminderStart=hw;state.tasksReminderEnd=exam;persist();render();toast('应用内提醒设置已保存');}catch(e){toast('请输入 0–720 小时内的正数，逗号分隔，最多 8 个时间');}};
  $('tasks-logout').onclick=function(){modal('退出学习通？','将清除学习通会话和本机待办，不影响教务及费用登录。','退出登录',function(){action('tasksLogout')});};
  $('tasks-clear').onclick=function(){modal('清除学习通待办？','清除本机待办缓存，学习通会话保留；下次打开或手动刷新会重新同步。','清除缓存',function(){action('tasksClear')});};
  $('tasks-hide-old-notices').onchange=function(){setTaskDisplay({type:'oldNotices',hidden:this.checked});};
  $('tasks-restore-hidden').onclick=function(){setTaskDisplay({type:'restore'});};
  $('clear-button').onclick=function(){modal('清空本机记录？','将删除本机保存的费用、查询记录、课表、成绩和学习通待办。学校登录状态会保留。','清空记录',function(){action('clear')});};
  $('modal-confirm').onclick=function(){var callback=confirmAction;closeModal();if(callback)callback();};$('modal-cancel').onclick=closeModal;
  document.addEventListener('keydown',function(e){if($('modal').hidden){if($('course-editor').hidden)return;if(e.key==='Escape'){e.preventDefault();closeEditor();}if(e.key==='Tab'){var controls=Array.from($('course-editor').querySelectorAll('button,input,select')).filter(function(n){return !n.disabled&&n.getClientRects().length}),first=controls[0],last=controls[controls.length-1];if(e.shiftKey&&(document.activeElement===first||!controls.includes(document.activeElement))){e.preventDefault();last.focus();}else if(!e.shiftKey&&(document.activeElement===last||!controls.includes(document.activeElement))){e.preventDefault();first.focus();}}return;}if(e.key==='Escape')closeModal();if(e.key==='Tab'){var a=$('modal-cancel'),b=$('modal-confirm');if(a.hidden){e.preventDefault();b.focus();}else if(e.shiftKey&&document.activeElement===a){e.preventDefault();b.focus();}else if(!e.shiftKey&&document.activeElement===b){e.preventDefault();a.focus();}}});
  state=initial();render();window.openPage('home');
  setInterval(function(){if(!document.hidden)renderTasks(data())},30000);
  document.addEventListener('visibilitychange',function(){if(!document.hidden)renderTasks(data())});
})();
