(function () {
  'use strict';
  var table = document.querySelector('#kbtable');
  if (!table) return {error: 'table-unavailable'};
  var weekSelect = document.querySelector('#zc');
  if (weekSelect && weekSelect.value !== '') return {error: 'select-all-weeks'};
  var out = [], notes = [], slots = [], seen = {};
  function trim(s) { return (s || '').replace(/\u00a0/g, ' ').trim(); }
  function plain(node) {
    var copy = node.cloneNode(true);
    Array.prototype.forEach.call(copy.querySelectorAll('br'), function (b) { b.replaceWith('\n'); });
    return copy.textContent;
  }
  Array.prototype.forEach.call(table.rows, function (row) {
    var cells = Array.prototype.filter.call(row.children, function (c) { return c.tagName === 'TD'; });
    var head = row.querySelector('th');
    if (cells.length === 1 && head && /备注/.test(head.textContent)) {
      trim(plain(cells[0])).split(/[;；\n]+/).map(trim).filter(Boolean).forEach(function (n) { notes.push(n); });
      return;
    }
    if (cells.length !== 7) return;
    var rowTime = head && trim(plain(head)).match(/(\d{2}:\d{2})\s*[-－—~]\s*(\d{2}:\d{2})/);
    var slot = {start: rowTime ? rowTime[1] : '', end: rowTime ? rowTime[2] : '', sections: []};
    cells.forEach(function (cell, day) {
      Array.prototype.forEach.call(cell.querySelectorAll('div.kbcontent'), function (detail) {
        // Detailed blocks may be hidden. Read nodes and BRs instead of innerText.
        var clone = detail.cloneNode(true);
        Array.prototype.forEach.call(clone.querySelectorAll('hr'), function (hr) { hr.replaceWith('\n---COURSE---\n'); });
        plain(clone).split('---COURSE---').forEach(function (unused, index) {
          // Split the DOM as well, retaining field titles for each course.
          var groups = [[]];
          Array.prototype.forEach.call(detail.childNodes, function (node) {
            if (node.nodeName === 'HR') groups.push([]); else groups[groups.length - 1].push(node);
          });
          if (!groups[index]) return;
          var block = document.createElement('div');
          groups[index].forEach(function (node) { block.appendChild(node.cloneNode(true)); });
          var nameParts = [];
          for (var node = block.firstChild; node; node = node.nextSibling) {
            if (node.nodeName === 'BR' || (node.nodeType === 1 && node.hasAttribute('title'))) break;
            nameParts.push(node.textContent);
          }
          var name = trim(nameParts.join('')), fields = {};
          Array.prototype.forEach.call(block.querySelectorAll('[title]'), function (f) { fields[f.getAttribute('title')] = trim(f.textContent); });
          var schedule = fields['周次(节次)'] || '', match = schedule.match(/\[([^\]]+)节\]/);
          if (!name || !match) return;
          var sections = (match[1].match(/\d+/g) || []).map(Number), weekText = schedule.replace(/\[.*$/, '').replace(/[（(]周[）)]/g, '');
          if (!sections.length || sections.some(function (n) { return n < 1 || n > 16; })) return;
          sections.forEach(function (n) { if (slot.sections.indexOf(n) < 0) slot.sections.push(n); });
          var c = {weekday: day + 1, name: name, teacher: fields['老师'] || '', room: fields['教室'] || '', weeks: trim(weekText), sections: match[1], schedule: schedule};
          var key = JSON.stringify(c);
          if (!seen[key]) { seen[key] = true; out.push(c); }
        });
      });
    });
    if (slot.sections.length) { slot.sections.sort(function (a,b) { return a-b; }); slots.push(slot); }
  });
  var term = document.querySelector('#xnxq01id'), mode = document.querySelector('#kbjcmsid');
  return {courses: out, notes: notes, slots: slots, term: term ? term.value : '', timeMode: mode && mode.selectedOptions.length ? trim(mode.selectedOptions[0].textContent) : ''};
})()
