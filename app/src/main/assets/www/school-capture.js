(function () {
  'use strict';
  if (window.__hubuCapture) return;
  // Observe only the school's own successful account requests. Never copy auth headers.
  var store = window.__hubuCapture = {};
  function kind(url) {
    try {
      var u = new URL(url, location.href);
      if (u.origin !== 'https://zhhq.hubu.edu.cn') return '';
      if (u.pathname === '/api/room/my/1') return 'lighting';
      if (u.pathname === '/api/room/my/2') return 'aircon';
      if (/^\/api\/water\/get\/1\d{10}\/?$/.test(u.pathname)) return 'water';
    } catch (e) {}
    return '';
  }
  function receive(k, status, value) {
    if (!k) return;
    if (status === 401 || status === 403) { store[k] = {auth: true}; return; }
    if (status < 200 || status >= 300) { store[k] = {error: 'network'}; return; }
    try {
      var envelope = typeof value === 'string' ? JSON.parse(value) : value;
      if (!envelope || envelope.code !== 0) { store[k] = {error: 'business'}; return; }
      var d = envelope.data;
      if (!d || typeof d !== 'object') return;
      // Store only fields needed by this assistant, excluding telephone/account IDs.
      if (k === 'water') store[k] = {data: {money: d.accountMoney, givenMoney: d.accountGivenMoney, projectName: d.projectName}};
      else {
        var suffix = k === 'lighting' ? 'zm' : 'kt';
        store[k] = {data: {energy: d.balance && d.balance[suffix], money: d.money && d.money[suffix], subsidyEnergy: d.bz_balance && d.bz_balance[suffix], subsidyMoney: d.bz_money && d.bz_money[suffix], room: d.room, campus: d.area_str}};
      }
    } catch (e) { store[k] = {error: 'response-format'}; }
  }
  var open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) { this.__hubuKind = kind(url); return open.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function () {
    var xhr = this, k = xhr.__hubuKind;
    if (k) xhr.addEventListener('load', function () {
      try { receive(k, xhr.status, xhr.responseType === 'json' ? xhr.response : xhr.responseText); } catch (e) {}
    });
    return send.apply(this, arguments);
  };
  if (window.fetch) {
    var fetch = window.fetch;
    window.fetch = function (input) {
      var k = kind(typeof input === 'string' ? input : input.url);
      return fetch.apply(this, arguments).then(function (res) {
        if (k) res.clone().text().then(function (s) { receive(k, res.status, s); }).catch(function () {});
        return res;
      });
    };
  }
})();
