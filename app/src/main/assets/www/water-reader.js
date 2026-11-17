(function () {
  'use strict';
  var out = {}, conflict = false;
  var labels = {'账户余额':'money','水费余额':'money','可用余额':'money','赠送余额':'givenMoney','赠送金额':'givenMoney','项目名称':'projectName'};
  function visible(e) { return !!e.getClientRects().length && getComputedStyle(e).visibility !== 'hidden'; }
  function clean(s) { return (s || '').replace(/[\s：:]/g, ''); }
  function save(label, value) {
    var key = labels[label]; if (!key) return;
    value = value.trim();
    if (key !== 'projectName') {
      value = value.replace(/[\s,，¥￥]/g, '').replace(/元$/, '');
      if (!/^-?\d{1,9}(?:\.\d{1,8})?$/.test(value)) return;
    }
    if (out[key] != null && out[key] !== value) conflict = true;
    out[key] = value;
  }
  Array.prototype.forEach.call(document.querySelectorAll('input'), function (input) {
    if (!visible(input) || input.type === 'password' || input.type === 'hidden') return;
    for (var group = input.parentElement, n = 0; group && n < 6; n++, group = group.parentElement) {
      if (group.querySelectorAll('input').length > 1) break;
      var label = clean(group.innerText);
      if (labels[label]) { save(label, input.value); break; }
    }
  });
  Array.prototype.forEach.call(document.querySelectorAll('.adm-form-item,.van-field,.van-cell,.water-field'), function (group) {
    if (!visible(group) || group.querySelector('input')) return;
    var value = clean(group.innerText);
    Object.keys(labels).forEach(function (label) { if (value.indexOf(label) === 0 && value.length < 100) save(label, value.slice(label.length)); });
  });
  if (conflict) return {error: 'ambiguous-fields'};
  if (out.money == null) out.error = 'incomplete-fields';
  return out;
})()
