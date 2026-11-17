// Packaged UI verification. Localhost only; never refreshes school fees.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'../app/src/main/assets/www');
(async()=>{
 const server=http.createServer((req,res)=>{const file=path.join(root,new URL(req.url,'http://localhost').pathname==='/'?'index.html':new URL(req.url,'http://localhost').pathname);if(!file.startsWith(root)||!fs.existsSync(file)){res.writeHead(404).end();return;}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript; charset=utf-8':file.endsWith('.css')?'text/css; charset=utf-8':'text/html; charset=utf-8');res.end(fs.readFileSync(file));});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;
 try{
  browser=await chromium.launch({headless:true,executablePath:process.env.BROWSER_PATH||(fs.existsSync('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')?'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe':undefined)});
  const page=await browser.newPage({viewport:{width:375,height:900}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));await page.route('**/*',r=>r.request().url().startsWith('http://127.0.0.1:')?r.continue():r.abort());
  await page.goto(`http://127.0.0.1:${server.address().port}/?demo=1`);
  assert.equal(await page.title(),'湖北大学助手');assert.match(await page.locator('.footnote').textContent(),/测试版 v0\.8\s/);
  const problems=[];
  for(const width of [1280,375,320]){
   await page.setViewportSize({width,height:900});
   for(const view of ['home','timetable','grades','tasks','fees','settings']){
    if(view==='fees'){await page.locator('button[data-page="home"]').click();await page.getByRole('button',{name:'查看费用详情',exact:true}).click();}else await page.locator(`button[data-page="${view}"]`).click();await page.waitForTimeout(400);
    if(view==='timetable')await page.locator('#view-agenda').click();
    const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
    const clipped=await page.locator('#page-'+view).evaluate(el=>[...el.querySelectorAll('button,input,select,strong')].filter(n=>n.getClientRects().length).filter(n=>{const r=n.getBoundingClientRect();return r.left<0||r.right>innerWidth+1}).map(n=>n.id||n.className));
    if(overflow||clipped.length)problems.push({width,view,overflow,clipped});
    if(width===375)await page.screenshot({path:path.resolve(__dirname,`../预览-0.7-${{home:'首页',timetable:'课表',grades:'成绩',tasks:'待办',fees:'费用',settings:'设置'}[view]}.png`),fullPage:true});
   }
  }
  await page.setViewportSize({width:375,height:900});await page.locator('button[data-page="timetable"]').click();await page.locator('#view-agenda').click();await page.locator('#week-select').selectOption('8');
  await page.locator('#agenda-days button').filter({hasText:'二'}).click();assert.ok(await page.locator('.agenda-course').count()>0);
  await page.locator('.agenda-course').first().click();assert.ok(await page.locator('#course-editor').isVisible());await page.locator('#course-room').fill('日程编辑回归');await page.locator('#course-save').click();assert.match(await page.locator('#agenda-courses').textContent(),/日程编辑回归/);
  await page.locator('#view-week').click();assert.match(await page.locator('#timetable-grid').textContent(),/日程编辑回归/);
  await page.locator('button[data-page="home"]').click();await page.locator('#library-button').click();assert.ok(await page.locator('#modal').isVisible());await page.locator('#modal-confirm').click();
  assert.equal(await page.locator('#home-water').textContent(),'13.377');assert.deepEqual(errors,[]);assert.deepEqual(problems,[]);
  console.log(JSON.stringify({liquidUI:'passed',widths:[1280,375,320],pages:6,agendaEdit:'passed',feeRefreshExecuted:false}));
 }finally{if(browser)await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1});
