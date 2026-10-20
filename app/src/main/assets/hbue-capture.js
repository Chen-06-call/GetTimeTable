(function install(win) {
  'use strict';
  try {
    if (win.location.hostname !== '218.197.82.133') return;
    if (!win.__weekdayInstalled) {
      win.__weekdayInstalled = true;
      win.__weekdayCourses = [];
      win.__weekdayDirty = false;
      var sequence = 0;
      function key() {
        return ['xnm','xqm'].map(function(id) {var el=win.document.getElementById(id); return el ? el.value : '';}).join('|');
      }
      win.__weekdayKey = key;
      win.document.addEventListener('change', function(e) {
        if (e.target.id === 'xnm' || e.target.id === 'xqm') {win.__weekdayDirty=true; win.__weekdayCourses=[];}
      }, true);
      function list(data) {
        if (Array.isArray(data)) return data;
        if (!data || typeof data !== 'object') return null;
        if (Array.isArray(data.kbList)) return data.kbList;
        if (data.data) { var nested=list(data.data); if (nested) return nested; }
        if (data.result) { var result=list(data.result); if (result) return result; }
        if (Array.isArray(data.rows)) return data.rows;
        return null;
      }
      function day(value) {
        var v=String(value||'').trim(), m=v.match(/[1-7]/);
        if(m)return m[0];
        if(!v)return '';
        var i='一二三四五六日天'.indexOf(v.replace(/^星期|^周/,'').charAt(0));
        return i<0?'':String(i+1).replace('8','7');
      }
      function capture(data, sentKey, serial) {
        var rows=list(data);
        if (!rows || !rows.length || sentKey !== key()) return;
        win.__weekdayCourseKey = sentKey;
        win.__weekdayDirty = false;
        win.__weekdayPending = false;
        win.__weekdayCaptured = true;
        win.__weekdayCourses = rows.slice(0,5000).map(function(r) {
          return {name:r.kcmc||r.kcmcText||r.kcmcName||r.name||'',day:day(r.xqj||r.xq||r.day),sections:String(r.jcs||r.jc||r.sections||''),weeks:String(r.zcd||r.zc||r.weeks||''),teacher:r.xm||r.jsxm||r.teacher||'',room:r.cdmc||r.cd||r.room||'',note:r.bz||r.note||''};
        });
      }
      var proto=win.XMLHttpRequest.prototype, send=proto.send, open=proto.open;
      proto.open=function(method,url){this.__weekdayUrl=String(url);return open.apply(this,arguments);};
      proto.send=function(){
        var courseRequest=/\/kbcx\/[^?#]*(?:xskbcx|kbcx|kb)[^?#]*/i.test(this.__weekdayUrl||'');
        if (courseRequest) {
          var sentKey=key(), serial=++sequence;win.__weekdayPending=true;
          this.addEventListener('load',function(){try {
            var data=(!this.responseType||this.responseType==='text')?JSON.parse(this.responseText):this.response;
            if(courseRequest)capture(data,sentKey,serial);
          }catch(e){}});
        }
        return send.apply(this,arguments);
      };
      if(win.fetch){var original=win.fetch;win.fetch=function(input){
        var requestUrl=String(input&&input.url||input), tracked=/\/kbcx\/[^?#]*(?:xskbcx|kbcx|kb)[^?#]*/i.test(requestUrl), sentKey=key(),serial=tracked?++sequence:0;
        if(tracked)win.__weekdayPending=true;
        return original.apply(this,arguments).then(function(response){if(tracked)response.clone().json().then(function(data){capture(data,sentKey,serial);}).catch(function(){});return response;});
      };}
    }
    for(var i=0;i<win.frames.length&&i<16;i++)install(win.frames[i]);
  }catch(e){}
})(window);
