(function () {
  'use strict';
  var out={url:location.href,term:'',entries:[]},docs=0;
  function text(el){return el ? (el.innerText || el.textContent || '').trim() : '';}
  function list(data){
    if(Array.isArray(data))return data;
    if(!data||typeof data!=='object')return null;
    if(Array.isArray(data.kbList))return data.kbList;
    return list(data.data)||list(data.result)||(Array.isArray(data.rows)?data.rows:null);
  }
  function day(value){
    var v=String(value||'').trim();
    if(/^[1-7]$/.test(v))return v;
    var m=v.match(/^(?:星期|周)?([一二三四五六日天])$/);
    return m?String(Math.min(7,'一二三四五六日天'.indexOf(m[1])+1)):v;
  }
  function visit(win) {
    if (++docs>16) return;
    try {
      if(win.location.hostname!=='218.197.82.133')return;
      var doc=win.document, cached=win.__weekdayCourses;
      if(win.__weekdayDirty || (win.__weekdayCourseKey && win.__weekdayKey && win.__weekdayCourseKey!==win.__weekdayKey())){out.error='学期已改变，请点击网站的查询按钮，待课表刷新后再读取';return;}
      var xn=doc.querySelector('#xnm'),xq=doc.querySelector('#xqm');
      if(xn||xq) out.term=[xn&&xn.options?text(xn.options[xn.selectedIndex]):'',xq&&xq.options?text(xq.options[xq.selectedIndex]):''].filter(Boolean).join(' · ');
      if(Array.isArray(cached)&&(cached.length||win.__weekdayCaptured)) {out.entries=out.entries.concat(cached);}
      else {
        // Some ZF versions expose the last response as a global object.
        var data=win.kbData || win.kbxx || win.kbList;
        var rows=list(data);
        if(rows&&rows.length)rows.slice(0,5000).forEach(function(r){out.entries.push({name:r.kcmc||r.kcmcText||r.kcmcName||r.name||'',day:day(r.xqj||r.xq||r.day),sections:String(r.jcs||r.jc||r.sections||''),weeks:String(r.zcd||r.zc||r.weeks||''),teacher:r.xm||r.jsxm||r.teacher||'',room:r.cdmc||r.cd||r.room||'',note:r.bz||r.note||''});});
        else {
      var cards=doc.querySelectorAll('.timetable_con,.kbcontent,[data-kcmc]');
          Array.prototype.forEach.call(cards,function(card){
            if(out.entries.length>=5000)return;
            var cell=card.closest('td'),day=card.getAttribute('data-xqj')||'',id=cell?(cell.id||''):'';
            if(!day&&/^([1-7])[-_]/.test(id))day=id.charAt(0);
            if(!day&&cell) {var col=cell.cellIndex,table=cell.closest('table');if(table){var headers=table.querySelectorAll('thead tr:last-child th,thead tr:last-child td');var header=text(headers[col]);var m=header.match(/(?:星期|周)([一二三四五六日天])/);if(m)day=String('一二三四五六日天'.indexOf(m[1])+1).replace('8','7');}}
            var name=text(card.querySelector('.title,.kcmc,[title="课程名称"]'))||card.getAttribute('data-kcmc')||'';
            var teacher='',room='',weeks='',sections='';
            Array.prototype.forEach.call(card.querySelectorAll('[title]'),function(el){var label=el.getAttribute('title')||'',value=text(el).replace(/^[^:：]{1,8}[:：]/,'');if(/教师|老师/.test(label))teacher=value;if(/教室|场地|地点/.test(label))room=value;if(/周次/.test(label)){var w=value.match(/[\d,，、\-－～]+周(?:[（(][单双][）)])?/g);weeks=w?w.join(','):value;}if(/节次/.test(label)){var s=value.match(/[（(]([\d,，、\-－～]+)节[）)]|第?([\d,，、\-－～]+)节/);sections=s?(s[1]||s[2]):value;}});
            var body=text(card),lines=body.split(/\n/).map(function(s){return s.trim();}).filter(Boolean);
            if(!name&&lines.length)name=lines[0];
            if(!weeks){var wm=body.match(/[\d,，、\-－～]+周(?:[（(][单双][）)])?/g);if(wm)weeks=wm.join(',');}
            if(!sections){var sm=body.match(/[（(]([\d,，、\-－～]+)节[）)]|第?([\d,，、\-－～]+)节/);if(sm)sections=sm[1]||sm[2];}
            if(name)out.entries.push({name:name,day:day,sections:sections,weeks:weeks,teacher:teacher,room:room,note:''});
          });
        }
      }
      for(var i=0;i<win.frames.length;i++)visit(win.frames[i]);
    } catch(e) {}
  }
  visit(window);out.entries=out.entries.slice(0,5000);return JSON.stringify(out);
})();
