(function () {
  'use strict';
  // Read visible labelled values, including disabled inputs, without touching login/payment controls.
  var labels = { '校区': 'campus', '宿舍号': 'room', '存款电量': 'energy', '存款余额': 'money', '补助电量': 'subsidyEnergy', '补助余额': 'subsidyMoney' };
  var result = {}, conflicts = {};
  function clean(text) { return (text || '').replace(/[\s：:]/g, ''); }
  function visible(el) { return !!el.getClientRects().length && getComputedStyle(el).visibility !== 'hidden'; }
  function save(label, value) {
    var key = labels[label]; value = value.trim();
    if (key !== 'room' && key !== 'campus') {
      value = value.replace(/[\s,，]/g, '').replace(/(?:kWh|千瓦时|度|元)$/i, '');
      if (!/^-?\d{1,9}(?:\.\d{1,8})?$/.test(value)) return;
    }
    if (!value) return;
    if (Object.prototype.hasOwnProperty.call(result, key) && result[key] !== value) conflicts[key] = true;
    result[key] = value;
  }
  Array.prototype.forEach.call(document.querySelectorAll('input'), function (input) {
    if (!visible(input) || input.type === 'password' || input.type === 'hidden') return;
    var group = input.parentElement;
    for (var i = 0; group && i < 8; i++, group = group.parentElement) {
      var label = clean(group.innerText);
      if (labels[label] && group.querySelectorAll('input').length === 1) { save(label, input.value); break; }
      if (group.querySelectorAll('input').length > 1) break;
    }
  });
  Array.prototype.forEach.call(document.querySelectorAll('.adm-form-item,.van-field,.van-cell,.energy-field'), function(group) {
    if (!visible(group) || group.querySelector('input')) return;
    var text = clean(group.innerText);
    Object.keys(labels).forEach(function(label) {
      if (text.indexOf(label) === 0 && text.length < 100) save(label, text.slice(label.length));
    });
  });
  if (Object.keys(conflicts).length) result.error = 'ambiguous-fields';
  if (!result.room || result.energy == null || result.money == null) result.error = 'incomplete-fields';
  return result;
})()
