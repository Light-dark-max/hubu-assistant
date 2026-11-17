const {test}=require('node:test');const assert=require('node:assert/strict');const dates=require('../app/src/main/assets/www/task-dates.js');
const now=Date.parse('2026-10-30T18:36:00+08:00');
test('corrects apparent next-year assignment and preserves real cross-year dates',()=>{
  const near=dates.parse('2027-11-08T13:26:00+08:00',now);assert.equal(near.getFullYear(),2026);assert.equal(near.getMonth(),10);assert.equal(near.getDate(),8);
  const cross=dates.parse('2027-01-02T14:00:00+08:00',Date.parse('2026-12-30T12:00:00+08:00'));assert.equal(cross.getFullYear(),2027);
  const far=dates.parse('2027-11-08T13:26:00+08:00',Date.parse('2026-01-01T12:00:00+08:00'));assert.equal(far.getFullYear(),2027);
});
test('the same correction is used by pending and reminder consumers',()=>{
  const due=dates.parse('2027-11-08T13:26:00+08:00',now);assert.equal(Math.ceil((due-now)/86400000),9);
});
