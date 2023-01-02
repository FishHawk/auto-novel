import{g as K,h as sn,b as un,j as dn,c as cn,u as xe,r as te,N as hn,V as fn,k as vn,o as Ce,l as mn,f as P,m as Pe}from"./provider.ec926eef.js";import{j as Ae,a0 as pn,v as z,d as D,y as a,a1 as gn,n as w,l as S,M as c,Y as bn,S as Fe,U as ve,_ as yn,a2 as re,$ as wn,J as xn,r as C,A as me,p as M,X as U,q as Cn,s as Te,o as Pn,B as Sn,O as Se,I as Mn,N as zn,x as An,F as Fn,V as Me,R as he}from"./index.b447288a.js";const Tn={name:"en-US",global:{undo:"Undo",redo:"Redo",confirm:"Confirm",clear:"Clear"},Popconfirm:{positiveText:"Confirm",negativeText:"Cancel"},Cascader:{placeholder:"Please Select",loading:"Loading",loadingRequiredMessage:t=>`Please load all ${t}'s descendants before checking it.`},Time:{dateFormat:"yyyy-MM-dd",dateTimeFormat:"yyyy-MM-dd HH:mm:ss"},DatePicker:{yearFormat:"yyyy",monthFormat:"MMM",dayFormat:"eeeeee",yearTypeFormat:"yyyy",monthTypeFormat:"yyyy-MM",dateFormat:"yyyy-MM-dd",dateTimeFormat:"yyyy-MM-dd HH:mm:ss",quarterFormat:"yyyy-qqq",clear:"Clear",now:"Now",confirm:"Confirm",selectTime:"Select Time",selectDate:"Select Date",datePlaceholder:"Select Date",datetimePlaceholder:"Select Date and Time",monthPlaceholder:"Select Month",yearPlaceholder:"Select Year",quarterPlaceholder:"Select Quarter",startDatePlaceholder:"Start Date",endDatePlaceholder:"End Date",startDatetimePlaceholder:"Start Date and Time",endDatetimePlaceholder:"End Date and Time",startMonthPlaceholder:"Start Month",endMonthPlaceholder:"End Month",monthBeforeYear:!0,firstDayOfWeek:6,today:"Today"},DataTable:{checkTableAll:"Select all in the table",uncheckTableAll:"Unselect all in the table",confirm:"Confirm",clear:"Clear"},LegacyTransfer:{sourceTitle:"Source",targetTitle:"Target"},Transfer:{selectAll:"Select all",unselectAll:"Unselect all",clearAll:"Clear",total:t=>`Total ${t} items`,selected:t=>`${t} items selected`},Empty:{description:"No Data"},Select:{placeholder:"Please Select"},TimePicker:{placeholder:"Select Time",positiveText:"OK",negativeText:"Cancel",now:"Now"},Pagination:{goto:"Goto",selectionSuffix:"page"},DynamicTags:{add:"Add"},Log:{loading:"Loading"},Input:{placeholder:"Please Input"},InputNumber:{placeholder:"Please Input"},DynamicInput:{create:"Create"},ThemeEditor:{title:"Theme Editor",clearAllVars:"Clear All Variables",clearSearch:"Clear Search",filterCompName:"Filter Component Name",filterVarName:"Filter Variable Name",import:"Import",export:"Export",restore:"Reset to Default"},Image:{tipPrevious:"Previous picture (\u2190)",tipNext:"Next picture (\u2192)",tipCounterclockwise:"Counterclockwise",tipClockwise:"Clockwise",tipZoomOut:"Zoom out",tipZoomIn:"Zoom in",tipClose:"Close (Esc)",tipOriginalSize:"Zoom to original size"}},kn=Tn;function fe(t){return function(){var o=arguments.length>0&&arguments[0]!==void 0?arguments[0]:{},r=o.width?String(o.width):t.defaultWidth,v=t.formats[r]||t.formats[t.defaultWidth];return v}}function H(t){return function(o,r){var v=r!=null&&r.context?String(r.context):"standalone",m;if(v==="formatting"&&t.formattingValues){var l=t.defaultFormattingWidth||t.defaultWidth,d=r!=null&&r.width?String(r.width):l;m=t.formattingValues[d]||t.formattingValues[l]}else{var s=t.defaultWidth,f=r!=null&&r.width?String(r.width):t.defaultWidth;m=t.values[f]||t.values[s]}var u=t.argumentCallback?t.argumentCallback(o):o;return m[u]}}function q(t){return function(o){var r=arguments.length>1&&arguments[1]!==void 0?arguments[1]:{},v=r.width,m=v&&t.matchPatterns[v]||t.matchPatterns[t.defaultMatchWidth],l=o.match(m);if(!l)return null;var d=l[0],s=v&&t.parsePatterns[v]||t.parsePatterns[t.defaultParseWidth],f=Array.isArray(s)?Rn(s,function(p){return p.test(d)}):_n(s,function(p){return p.test(d)}),u;u=t.valueCallback?t.valueCallback(f):f,u=r.valueCallback?r.valueCallback(u):u;var h=o.slice(d.length);return{value:u,rest:h}}}function _n(t,o){for(var r in t)if(t.hasOwnProperty(r)&&o(t[r]))return r}function Rn(t,o){for(var r=0;r<t.length;r++)if(o(t[r]))return r}function Dn(t){return function(o){var r=arguments.length>1&&arguments[1]!==void 0?arguments[1]:{},v=o.match(t.matchPattern);if(!v)return null;var m=v[0],l=o.match(t.parsePattern);if(!l)return null;var d=t.valueCallback?t.valueCallback(l[0]):l[0];d=r.valueCallback?r.valueCallback(d):d;var s=o.slice(m.length);return{value:d,rest:s}}}var Wn={lessThanXSeconds:{one:"less than a second",other:"less than {{count}} seconds"},xSeconds:{one:"1 second",other:"{{count}} seconds"},halfAMinute:"half a minute",lessThanXMinutes:{one:"less than a minute",other:"less than {{count}} minutes"},xMinutes:{one:"1 minute",other:"{{count}} minutes"},aboutXHours:{one:"about 1 hour",other:"about {{count}} hours"},xHours:{one:"1 hour",other:"{{count}} hours"},xDays:{one:"1 day",other:"{{count}} days"},aboutXWeeks:{one:"about 1 week",other:"about {{count}} weeks"},xWeeks:{one:"1 week",other:"{{count}} weeks"},aboutXMonths:{one:"about 1 month",other:"about {{count}} months"},xMonths:{one:"1 month",other:"{{count}} months"},aboutXYears:{one:"about 1 year",other:"about {{count}} years"},xYears:{one:"1 year",other:"{{count}} years"},overXYears:{one:"over 1 year",other:"over {{count}} years"},almostXYears:{one:"almost 1 year",other:"almost {{count}} years"}},$n=function(o,r,v){var m,l=Wn[o];return typeof l=="string"?m=l:r===1?m=l.one:m=l.other.replace("{{count}}",r.toString()),v!=null&&v.addSuffix?v.comparison&&v.comparison>0?"in "+m:m+" ago":m};const En=$n;var Bn={full:"EEEE, MMMM do, y",long:"MMMM do, y",medium:"MMM d, y",short:"MM/dd/yyyy"},In={full:"h:mm:ss a zzzz",long:"h:mm:ss a z",medium:"h:mm:ss a",short:"h:mm a"},Ln={full:"{{date}} 'at' {{time}}",long:"{{date}} 'at' {{time}}",medium:"{{date}}, {{time}}",short:"{{date}}, {{time}}"},Vn={date:fe({formats:Bn,defaultWidth:"full"}),time:fe({formats:In,defaultWidth:"full"}),dateTime:fe({formats:Ln,defaultWidth:"full"})};const Nn=Vn;var On={lastWeek:"'last' eeee 'at' p",yesterday:"'yesterday at' p",today:"'today at' p",tomorrow:"'tomorrow at' p",nextWeek:"eeee 'at' p",other:"P"},jn=function(o,r,v,m){return On[o]};const Un=jn;var Hn={narrow:["B","A"],abbreviated:["BC","AD"],wide:["Before Christ","Anno Domini"]},qn={narrow:["1","2","3","4"],abbreviated:["Q1","Q2","Q3","Q4"],wide:["1st quarter","2nd quarter","3rd quarter","4th quarter"]},Kn={narrow:["J","F","M","A","M","J","J","A","S","O","N","D"],abbreviated:["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"],wide:["January","February","March","April","May","June","July","August","September","October","November","December"]},Xn={narrow:["S","M","T","W","T","F","S"],short:["Su","Mo","Tu","We","Th","Fr","Sa"],abbreviated:["Sun","Mon","Tue","Wed","Thu","Fri","Sat"],wide:["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"]},Yn={narrow:{am:"a",pm:"p",midnight:"mi",noon:"n",morning:"morning",afternoon:"afternoon",evening:"evening",night:"night"},abbreviated:{am:"AM",pm:"PM",midnight:"midnight",noon:"noon",morning:"morning",afternoon:"afternoon",evening:"evening",night:"night"},wide:{am:"a.m.",pm:"p.m.",midnight:"midnight",noon:"noon",morning:"morning",afternoon:"afternoon",evening:"evening",night:"night"}},Jn={narrow:{am:"a",pm:"p",midnight:"mi",noon:"n",morning:"in the morning",afternoon:"in the afternoon",evening:"in the evening",night:"at night"},abbreviated:{am:"AM",pm:"PM",midnight:"midnight",noon:"noon",morning:"in the morning",afternoon:"in the afternoon",evening:"in the evening",night:"at night"},wide:{am:"a.m.",pm:"p.m.",midnight:"midnight",noon:"noon",morning:"in the morning",afternoon:"in the afternoon",evening:"in the evening",night:"at night"}},Zn=function(o,r){var v=Number(o),m=v%100;if(m>20||m<10)switch(m%10){case 1:return v+"st";case 2:return v+"nd";case 3:return v+"rd"}return v+"th"},Gn={ordinalNumber:Zn,era:H({values:Hn,defaultWidth:"wide"}),quarter:H({values:qn,defaultWidth:"wide",argumentCallback:function(o){return o-1}}),month:H({values:Kn,defaultWidth:"wide"}),day:H({values:Xn,defaultWidth:"wide"}),dayPeriod:H({values:Yn,defaultWidth:"wide",formattingValues:Jn,defaultFormattingWidth:"wide"})};const Qn=Gn;var er=/^(\d+)(th|st|nd|rd)?/i,tr=/\d+/i,nr={narrow:/^(b|a)/i,abbreviated:/^(b\.?\s?c\.?|b\.?\s?c\.?\s?e\.?|a\.?\s?d\.?|c\.?\s?e\.?)/i,wide:/^(before christ|before common era|anno domini|common era)/i},rr={any:[/^b/i,/^(a|c)/i]},ar={narrow:/^[1234]/i,abbreviated:/^q[1234]/i,wide:/^[1234](th|st|nd|rd)? quarter/i},or={any:[/1/i,/2/i,/3/i,/4/i]},ir={narrow:/^[jfmasond]/i,abbreviated:/^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)/i,wide:/^(january|february|march|april|may|june|july|august|september|october|november|december)/i},lr={narrow:[/^j/i,/^f/i,/^m/i,/^a/i,/^m/i,/^j/i,/^j/i,/^a/i,/^s/i,/^o/i,/^n/i,/^d/i],any:[/^ja/i,/^f/i,/^mar/i,/^ap/i,/^may/i,/^jun/i,/^jul/i,/^au/i,/^s/i,/^o/i,/^n/i,/^d/i]},sr={narrow:/^[smtwf]/i,short:/^(su|mo|tu|we|th|fr|sa)/i,abbreviated:/^(sun|mon|tue|wed|thu|fri|sat)/i,wide:/^(sunday|monday|tuesday|wednesday|thursday|friday|saturday)/i},ur={narrow:[/^s/i,/^m/i,/^t/i,/^w/i,/^t/i,/^f/i,/^s/i],any:[/^su/i,/^m/i,/^tu/i,/^w/i,/^th/i,/^f/i,/^sa/i]},dr={narrow:/^(a|p|mi|n|(in the|at) (morning|afternoon|evening|night))/i,any:/^([ap]\.?\s?m\.?|midnight|noon|(in the|at) (morning|afternoon|evening|night))/i},cr={any:{am:/^a/i,pm:/^p/i,midnight:/^mi/i,noon:/^no/i,morning:/morning/i,afternoon:/afternoon/i,evening:/evening/i,night:/night/i}},hr={ordinalNumber:Dn({matchPattern:er,parsePattern:tr,valueCallback:function(o){return parseInt(o,10)}}),era:q({matchPatterns:nr,defaultMatchWidth:"wide",parsePatterns:rr,defaultParseWidth:"any"}),quarter:q({matchPatterns:ar,defaultMatchWidth:"wide",parsePatterns:or,defaultParseWidth:"any",valueCallback:function(o){return o+1}}),month:q({matchPatterns:ir,defaultMatchWidth:"wide",parsePatterns:lr,defaultParseWidth:"any"}),day:q({matchPatterns:sr,defaultMatchWidth:"wide",parsePatterns:ur,defaultParseWidth:"any"}),dayPeriod:q({matchPatterns:dr,defaultMatchWidth:"any",parsePatterns:cr,defaultParseWidth:"any"})};const fr=hr;var vr={code:"en-US",formatDistance:En,formatLong:Nn,formatRelative:Un,localize:Qn,match:fr,options:{weekStartsOn:0,firstWeekContainsDate:1}};const mr=vr,pr={name:"en-US",locale:mr},gr=pr;function br(t){const{mergedLocaleRef:o,mergedDateLocaleRef:r}=Ae(pn,null)||{},v=z(()=>{var l,d;return(d=(l=o==null?void 0:o.value)===null||l===void 0?void 0:l[t])!==null&&d!==void 0?d:kn[t]});return{dateLocaleRef:z(()=>{var l;return(l=r==null?void 0:r.value)!==null&&l!==void 0?l:gr}),localeRef:v}}const yr=D({name:"Eye",render(){return a("svg",{xmlns:"http://www.w3.org/2000/svg",viewBox:"0 0 512 512"},a("path",{d:"M255.66 112c-77.94 0-157.89 45.11-220.83 135.33a16 16 0 0 0-.27 17.77C82.92 340.8 161.8 400 255.66 400c92.84 0 173.34-59.38 221.79-135.25a16.14 16.14 0 0 0 0-17.47C428.89 172.28 347.8 112 255.66 112z",fill:"none",stroke:"currentColor","stroke-linecap":"round","stroke-linejoin":"round","stroke-width":"32"}),a("circle",{cx:"256",cy:"256",r:"80",fill:"none",stroke:"currentColor","stroke-miterlimit":"10","stroke-width":"32"}))}}),wr=D({name:"EyeOff",render(){return a("svg",{xmlns:"http://www.w3.org/2000/svg",viewBox:"0 0 512 512"},a("path",{d:"M432 448a15.92 15.92 0 0 1-11.31-4.69l-352-352a16 16 0 0 1 22.62-22.62l352 352A16 16 0 0 1 432 448z",fill:"currentColor"}),a("path",{d:"M255.66 384c-41.49 0-81.5-12.28-118.92-36.5c-34.07-22-64.74-53.51-88.7-91v-.08c19.94-28.57 41.78-52.73 65.24-72.21a2 2 0 0 0 .14-2.94L93.5 161.38a2 2 0 0 0-2.71-.12c-24.92 21-48.05 46.76-69.08 76.92a31.92 31.92 0 0 0-.64 35.54c26.41 41.33 60.4 76.14 98.28 100.65C162 402 207.9 416 255.66 416a239.13 239.13 0 0 0 75.8-12.58a2 2 0 0 0 .77-3.31l-21.58-21.58a4 4 0 0 0-3.83-1a204.8 204.8 0 0 1-51.16 6.47z",fill:"currentColor"}),a("path",{d:"M490.84 238.6c-26.46-40.92-60.79-75.68-99.27-100.53C349 110.55 302 96 255.66 96a227.34 227.34 0 0 0-74.89 12.83a2 2 0 0 0-.75 3.31l21.55 21.55a4 4 0 0 0 3.88 1a192.82 192.82 0 0 1 50.21-6.69c40.69 0 80.58 12.43 118.55 37c34.71 22.4 65.74 53.88 89.76 91a.13.13 0 0 1 0 .16a310.72 310.72 0 0 1-64.12 72.73a2 2 0 0 0-.15 2.95l19.9 19.89a2 2 0 0 0 2.7.13a343.49 343.49 0 0 0 68.64-78.48a32.2 32.2 0 0 0-.1-34.78z",fill:"currentColor"}),a("path",{d:"M256 160a95.88 95.88 0 0 0-21.37 2.4a2 2 0 0 0-1 3.38l112.59 112.56a2 2 0 0 0 3.38-1A96 96 0 0 0 256 160z",fill:"currentColor"}),a("path",{d:"M165.78 233.66a2 2 0 0 0-3.38 1a96 96 0 0 0 115 115a2 2 0 0 0 1-3.38z",fill:"currentColor"}))}}),xr=D({name:"ChevronDown",render(){return a("svg",{viewBox:"0 0 16 16",fill:"none",xmlns:"http://www.w3.org/2000/svg"},a("path",{d:"M3.14645 5.64645C3.34171 5.45118 3.65829 5.45118 3.85355 5.64645L8 9.79289L12.1464 5.64645C12.3417 5.45118 12.6583 5.45118 12.8536 5.64645C13.0488 5.84171 13.0488 6.15829 12.8536 6.35355L8.35355 10.8536C8.15829 11.0488 7.84171 11.0488 7.64645 10.8536L3.14645 6.35355C2.95118 6.15829 2.95118 5.84171 3.14645 5.64645Z",fill:"currentColor"}))}}),Cr=gn("clear",a("svg",{viewBox:"0 0 16 16",version:"1.1",xmlns:"http://www.w3.org/2000/svg"},a("g",{stroke:"none","stroke-width":"1",fill:"none","fill-rule":"evenodd"},a("g",{fill:"currentColor","fill-rule":"nonzero"},a("path",{d:"M8,2 C11.3137085,2 14,4.6862915 14,8 C14,11.3137085 11.3137085,14 8,14 C4.6862915,14 2,11.3137085 2,8 C2,4.6862915 4.6862915,2 8,2 Z M6.5343055,5.83859116 C6.33943736,5.70359511 6.07001296,5.72288026 5.89644661,5.89644661 L5.89644661,5.89644661 L5.83859116,5.9656945 C5.70359511,6.16056264 5.72288026,6.42998704 5.89644661,6.60355339 L5.89644661,6.60355339 L7.293,8 L5.89644661,9.39644661 L5.83859116,9.4656945 C5.70359511,9.66056264 5.72288026,9.92998704 5.89644661,10.1035534 L5.89644661,10.1035534 L5.9656945,10.1614088 C6.16056264,10.2964049 6.42998704,10.2771197 6.60355339,10.1035534 L6.60355339,10.1035534 L8,8.707 L9.39644661,10.1035534 L9.4656945,10.1614088 C9.66056264,10.2964049 9.92998704,10.2771197 10.1035534,10.1035534 L10.1035534,10.1035534 L10.1614088,10.0343055 C10.2964049,9.83943736 10.2771197,9.57001296 10.1035534,9.39644661 L10.1035534,9.39644661 L8.707,8 L10.1035534,6.60355339 L10.1614088,6.5343055 C10.2964049,6.33943736 10.2771197,6.07001296 10.1035534,5.89644661 L10.1035534,5.89644661 L10.0343055,5.83859116 C9.83943736,5.70359511 9.57001296,5.72288026 9.39644661,5.89644661 L9.39644661,5.89644661 L8,7.293 L6.60355339,5.89644661 Z"}))))),Pr=w("base-clear",`
 flex-shrink: 0;
 height: 1em;
 width: 1em;
 position: relative;
`,[S(">",[c("clear",`
 font-size: var(--n-clear-size);
 height: 1em;
 width: 1em;
 cursor: pointer;
 color: var(--n-clear-color);
 transition: color .3s var(--n-bezier);
 display: flex;
 `,[S("&:hover",`
 color: var(--n-clear-color-hover)!important;
 `),S("&:active",`
 color: var(--n-clear-color-pressed)!important;
 `)]),c("placeholder",`
 display: flex;
 `),c("clear, placeholder",`
 position: absolute;
 left: 50%;
 top: 50%;
 transform: translateX(-50%) translateY(-50%);
 `,[bn({originalTransform:"translateX(-50%) translateY(-50%)",left:"50%",top:"50%"})])])]),pe=D({name:"BaseClear",props:{clsPrefix:{type:String,required:!0},show:Boolean,onClear:Function},setup(t){return Fe("-base-clear",Pr,ve(t,"clsPrefix")),{handleMouseDown(o){o.preventDefault()}}},render(){const{clsPrefix:t}=this;return a("div",{class:`${t}-base-clear`},a(yn,null,{default:()=>{var o,r;return this.show?a("div",{key:"dismiss",class:`${t}-base-clear__clear`,onClick:this.onClear,onMousedown:this.handleMouseDown,"data-clear":!0},K(this.$slots.icon,()=>[a(re,{clsPrefix:t},{default:()=>a(Cr,null)})])):a("div",{key:"icon",class:`${t}-base-clear__placeholder`},(r=(o=this.$slots).placeholder)===null||r===void 0?void 0:r.call(o))}}))}}),Sr=D({name:"InternalSelectionSuffix",props:{clsPrefix:{type:String,required:!0},showArrow:{type:Boolean,default:void 0},showClear:{type:Boolean,default:void 0},loading:{type:Boolean,default:!1},onClear:Function},setup(t,{slots:o}){return()=>{const{clsPrefix:r}=t;return a(wn,{clsPrefix:r,class:`${r}-base-suffix`,strokeWidth:24,scale:.85,show:t.loading},{default:()=>t.showArrow?a(pe,{clsPrefix:r,show:t.showClear,onClear:t.onClear},{placeholder:()=>a(re,{clsPrefix:r,class:`${r}-base-suffix__arrow`},{default:()=>K(o.default,()=>[a(xr,null)])})}):null})}}}),ke=xn("n-input");function Mr(t){let o=0;for(const r of t)o++;return o}function ne(t){return t===""||t==null}function zr(t){const o=C(null);function r(){const{value:l}=t;if(!(l!=null&&l.focus)){m();return}const{selectionStart:d,selectionEnd:s,value:f}=l;if(d==null||s==null){m();return}o.value={start:d,end:s,beforeText:f.slice(0,d),afterText:f.slice(s)}}function v(){var l;const{value:d}=o,{value:s}=t;if(!d||!s)return;const{value:f}=s,{start:u,beforeText:h,afterText:p}=d;let x=f.length;if(f.endsWith(p))x=f.length-p.length;else if(f.startsWith(h))x=h.length;else{const W=h[u-1],A=f.indexOf(W,u-1);A!==-1&&(x=A+1)}(l=s.setSelectionRange)===null||l===void 0||l.call(s,x,x)}function m(){o.value=null}return me(t,m),{recordCursor:r,restoreCursor:v}}const ze=D({name:"InputWordCount",setup(t,{slots:o}){const{mergedValueRef:r,maxlengthRef:v,mergedClsPrefixRef:m,countGraphemesRef:l}=Ae(ke),d=z(()=>{const{value:s}=r;return s===null||Array.isArray(s)?0:(l.value||Mr)(s)});return()=>{const{value:s}=v,{value:f}=r;return a("span",{class:`${m.value}-input-word-count`},sn(o.default,{value:f===null||Array.isArray(f)?"":f},()=>[s===void 0?d.value:`${d.value} / ${s}`]))}}}),Ar=w("input",`
 max-width: 100%;
 cursor: text;
 line-height: 1.5;
 z-index: auto;
 outline: none;
 box-sizing: border-box;
 position: relative;
 display: inline-flex;
 border-radius: var(--n-border-radius);
 background-color: var(--n-color);
 transition: background-color .3s var(--n-bezier);
 font-size: var(--n-font-size);
 --n-padding-vertical: calc((var(--n-height) - 1.5 * var(--n-font-size)) / 2);
`,[c("input, textarea",`
 overflow: hidden;
 flex-grow: 1;
 position: relative;
 `),c("input-el, textarea-el, input-mirror, textarea-mirror, separator, placeholder",`
 box-sizing: border-box;
 font-size: inherit;
 line-height: 1.5;
 font-family: inherit;
 border: none;
 outline: none;
 background-color: #0000;
 text-align: inherit;
 transition:
 -webkit-text-fill-color .3s var(--n-bezier),
 caret-color .3s var(--n-bezier),
 color .3s var(--n-bezier),
 text-decoration-color .3s var(--n-bezier);
 `),c("input-el, textarea-el",`
 -webkit-appearance: none;
 scrollbar-width: none;
 width: 100%;
 min-width: 0;
 text-decoration-color: var(--n-text-decoration-color);
 color: var(--n-text-color);
 caret-color: var(--n-caret-color);
 background-color: transparent;
 `,[S("&::-webkit-scrollbar, &::-webkit-scrollbar-track-piece, &::-webkit-scrollbar-thumb",`
 width: 0;
 height: 0;
 display: none;
 `),S("&::placeholder",`
 color: #0000;
 -webkit-text-fill-color: transparent !important;
 `),S("&:-webkit-autofill ~",[c("placeholder","display: none;")])]),M("round",[U("textarea","border-radius: calc(var(--n-height) / 2);")]),c("placeholder",`
 pointer-events: none;
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 overflow: hidden;
 color: var(--n-placeholder-color);
 `,[S("span",`
 width: 100%;
 display: inline-block;
 `)]),M("textarea",[c("placeholder","overflow: visible;")]),U("autosize","width: 100%;"),M("autosize",[c("textarea-el, input-el",`
 position: absolute;
 top: 0;
 left: 0;
 height: 100%;
 `)]),w("input-wrapper",`
 overflow: hidden;
 display: inline-flex;
 flex-grow: 1;
 position: relative;
 padding-left: var(--n-padding-left);
 padding-right: var(--n-padding-right);
 `),c("input-mirror",`
 padding: 0;
 height: var(--n-height);
 overflow: hidden;
 visibility: hidden;
 position: static;
 white-space: pre;
 pointer-events: none;
 `),c("input-el",`
 padding: 0;
 height: var(--n-height);
 line-height: var(--n-height);
 `,[S("+",[c("placeholder",`
 display: flex;
 align-items: center; 
 `)])]),U("textarea",[c("placeholder","white-space: nowrap;")]),c("eye",`
 transition: color .3s var(--n-bezier);
 `),M("textarea","width: 100%;",[w("input-word-count",`
 position: absolute;
 right: var(--n-padding-right);
 bottom: var(--n-padding-vertical);
 `),M("resizable",[w("input-wrapper",`
 resize: vertical;
 min-height: var(--n-height);
 `)]),c("textarea-el, textarea-mirror, placeholder",`
 height: 100%;
 padding-left: 0;
 padding-right: 0;
 padding-top: var(--n-padding-vertical);
 padding-bottom: var(--n-padding-vertical);
 word-break: break-word;
 display: inline-block;
 vertical-align: bottom;
 box-sizing: border-box;
 line-height: var(--n-line-height-textarea);
 margin: 0;
 resize: none;
 white-space: pre-wrap;
 `),c("textarea-mirror",`
 width: 100%;
 pointer-events: none;
 overflow: hidden;
 visibility: hidden;
 position: static;
 white-space: pre-wrap;
 overflow-wrap: break-word;
 `)]),M("pair",[c("input-el, placeholder","text-align: center;"),c("separator",`
 display: flex;
 align-items: center;
 transition: color .3s var(--n-bezier);
 color: var(--n-text-color);
 white-space: nowrap;
 `,[w("icon",`
 color: var(--n-icon-color);
 `),w("base-icon",`
 color: var(--n-icon-color);
 `)])]),M("disabled",`
 cursor: not-allowed;
 background-color: var(--n-color-disabled);
 `,[c("border","border: var(--n-border-disabled);"),c("input-el, textarea-el",`
 cursor: not-allowed;
 color: var(--n-text-color-disabled);
 text-decoration-color: var(--n-text-color-disabled);
 `),c("placeholder","color: var(--n-placeholder-color-disabled);"),c("separator","color: var(--n-text-color-disabled);",[w("icon",`
 color: var(--n-icon-color-disabled);
 `),w("base-icon",`
 color: var(--n-icon-color-disabled);
 `)]),w("input-word-count",`
 color: var(--n-count-text-color-disabled);
 `),c("suffix, prefix","color: var(--n-text-color-disabled);",[w("icon",`
 color: var(--n-icon-color-disabled);
 `),w("internal-icon",`
 color: var(--n-icon-color-disabled);
 `)])]),U("disabled",[c("eye",`
 display: flex;
 align-items: center;
 justify-content: center;
 color: var(--n-icon-color);
 cursor: pointer;
 `,[S("&:hover",`
 color: var(--n-icon-color-hover);
 `),S("&:active",`
 color: var(--n-icon-color-pressed);
 `)]),S("&:hover",[c("state-border","border: var(--n-border-hover);")]),M("focus","background-color: var(--n-color-focus);",[c("state-border",`
 border: var(--n-border-focus);
 box-shadow: var(--n-box-shadow-focus);
 `)])]),c("border, state-border",`
 box-sizing: border-box;
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 pointer-events: none;
 border-radius: inherit;
 border: var(--n-border);
 transition:
 box-shadow .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 `),c("state-border",`
 border-color: #0000;
 z-index: 1;
 `),c("prefix","margin-right: 4px;"),c("suffix",`
 margin-left: 4px;
 `),c("suffix, prefix",`
 transition: color .3s var(--n-bezier);
 flex-wrap: nowrap;
 flex-shrink: 0;
 line-height: var(--n-height);
 white-space: nowrap;
 display: inline-flex;
 align-items: center;
 justify-content: center;
 color: var(--n-suffix-text-color);
 `,[w("base-loading",`
 font-size: var(--n-icon-size);
 margin: 0 2px;
 color: var(--n-loading-color);
 `),w("base-clear",`
 font-size: var(--n-icon-size);
 `,[c("placeholder",[w("base-icon",`
 transition: color .3s var(--n-bezier);
 color: var(--n-icon-color);
 font-size: var(--n-icon-size);
 `)])]),S(">",[w("icon",`
 transition: color .3s var(--n-bezier);
 color: var(--n-icon-color);
 font-size: var(--n-icon-size);
 `)]),w("base-icon",`
 font-size: var(--n-icon-size);
 `)]),w("input-word-count",`
 pointer-events: none;
 line-height: 1.5;
 font-size: .85em;
 color: var(--n-count-text-color);
 transition: color .3s var(--n-bezier);
 margin-left: 4px;
 font-variant: tabular-nums;
 `),["warning","error"].map(t=>M(`${t}-status`,[U("disabled",[w("base-loading",`
 color: var(--n-loading-color-${t})
 `),c("input-el, textarea-el",`
 caret-color: var(--n-caret-color-${t});
 `),c("state-border",`
 border: var(--n-border-${t});
 `),S("&:hover",[c("state-border",`
 border: var(--n-border-hover-${t});
 `)]),S("&:focus",`
 background-color: var(--n-color-focus-${t});
 `,[c("state-border",`
 box-shadow: var(--n-box-shadow-focus-${t});
 border: var(--n-border-focus-${t});
 `)]),M("focus",`
 background-color: var(--n-color-focus-${t});
 `,[c("state-border",`
 box-shadow: var(--n-box-shadow-focus-${t});
 border: var(--n-border-focus-${t});
 `)])])]))]),Fr=w("input",[M("disabled",[c("input-el, textarea-el",`
 -webkit-text-fill-color: var(--n-text-color-disabled);
 `)])]),Tr=Object.assign(Object.assign({},Te.props),{bordered:{type:Boolean,default:void 0},type:{type:String,default:"text"},placeholder:[Array,String],defaultValue:{type:[String,Array],default:null},value:[String,Array],disabled:{type:Boolean,default:void 0},size:String,rows:{type:[Number,String],default:3},round:Boolean,minlength:[String,Number],maxlength:[String,Number],clearable:Boolean,autosize:{type:[Boolean,Object],default:!1},pair:Boolean,separator:String,readonly:{type:[String,Boolean],default:!1},passivelyActivated:Boolean,showPasswordOn:String,stateful:{type:Boolean,default:!0},autofocus:Boolean,inputProps:Object,resizable:{type:Boolean,default:!0},showCount:Boolean,loading:{type:Boolean,default:void 0},allowInput:Function,renderCount:Function,onMousedown:Function,onKeydown:Function,onKeyup:Function,onInput:[Function,Array],onFocus:[Function,Array],onBlur:[Function,Array],onClick:[Function,Array],onChange:[Function,Array],onClear:[Function,Array],countGraphemes:Function,status:String,"onUpdate:value":[Function,Array],onUpdateValue:[Function,Array],textDecoration:[String,Array],attrSize:{type:Number,default:20},onInputBlur:[Function,Array],onInputFocus:[Function,Array],onDeactivate:[Function,Array],onActivate:[Function,Array],onWrapperFocus:[Function,Array],onWrapperBlur:[Function,Array],internalDeactivateOnEnter:Boolean,internalForceFocus:Boolean,internalLoadingBeforeSuffix:Boolean,showPasswordToggle:Boolean}),Rr=D({name:"Input",props:Tr,setup(t){const{mergedClsPrefixRef:o,mergedBorderedRef:r,inlineThemeDisabled:v,mergedRtlRef:m}=Cn(t),l=Te("Input","-input",Ar,vn,t,o);un&&Fe("-input-safari",Fr,o);const d=C(null),s=C(null),f=C(null),u=C(null),h=C(null),p=C(null),x=C(null),W=zr(x),A=C(null),{localeRef:_e}=br("Input"),X=C(t.defaultValue),Re=ve(t,"value"),F=dn(Re,X),I=cn(t),{mergedSizeRef:ae,mergedDisabledRef:$,mergedStatusRef:De}=I,E=C(!1),L=C(!1),T=C(!1),V=C(!1);let oe=null;const ie=z(()=>{const{placeholder:e,pair:n}=t;return n?Array.isArray(e)?e:e===void 0?["",""]:[e,e]:e===void 0?[_e.value.placeholder]:[e]}),We=z(()=>{const{value:e}=T,{value:n}=F,{value:i}=ie;return!e&&(ne(n)||Array.isArray(n)&&ne(n[0]))&&i[0]}),$e=z(()=>{const{value:e}=T,{value:n}=F,{value:i}=ie;return!e&&i[1]&&(ne(n)||Array.isArray(n)&&ne(n[1]))}),le=xe(()=>t.internalForceFocus||E.value),Ee=xe(()=>{if($.value||t.readonly||!t.clearable||!le.value&&!L.value)return!1;const{value:e}=F,{value:n}=le;return t.pair?!!(Array.isArray(e)&&(e[0]||e[1]))&&(L.value||n):!!e&&(L.value||n)}),se=z(()=>{const{showPasswordOn:e}=t;if(e)return e;if(t.showPasswordToggle)return"click"}),N=C(!1),Be=z(()=>{const{textDecoration:e}=t;return e?Array.isArray(e)?e.map(n=>({textDecoration:n})):[{textDecoration:e}]:["",""]}),ge=C(void 0),Ie=()=>{var e,n;if(t.type==="textarea"){const{autosize:i}=t;if(i&&(ge.value=(n=(e=A.value)===null||e===void 0?void 0:e.$el)===null||n===void 0?void 0:n.offsetWidth),!s.value||typeof i=="boolean")return;const{paddingTop:b,paddingBottom:y,lineHeight:g}=window.getComputedStyle(s.value),k=Number(b.slice(0,-2)),_=Number(y.slice(0,-2)),R=Number(g.slice(0,-2)),{value:O}=f;if(!O)return;if(i.minRows){const j=Math.max(i.minRows,1),ce=`${k+_+R*j}px`;O.style.minHeight=ce}if(i.maxRows){const j=`${k+_+R*i.maxRows}px`;O.style.maxHeight=j}}},Le=z(()=>{const{maxlength:e}=t;return e===void 0?void 0:Number(e)});Pn(()=>{const{value:e}=F;Array.isArray(e)||de(e)});const Ve=Sn().proxy;function Y(e){const{onUpdateValue:n,"onUpdate:value":i,onInput:b}=t,{nTriggerFormInput:y}=I;n&&P(n,e),i&&P(i,e),b&&P(b,e),X.value=e,y()}function J(e){const{onChange:n}=t,{nTriggerFormChange:i}=I;n&&P(n,e),X.value=e,i()}function Ne(e){const{onBlur:n}=t,{nTriggerFormBlur:i}=I;n&&P(n,e),i()}function Oe(e){const{onFocus:n}=t,{nTriggerFormFocus:i}=I;n&&P(n,e),i()}function je(e){const{onClear:n}=t;n&&P(n,e)}function Ue(e){const{onInputBlur:n}=t;n&&P(n,e)}function He(e){const{onInputFocus:n}=t;n&&P(n,e)}function qe(){const{onDeactivate:e}=t;e&&P(e)}function Ke(){const{onActivate:e}=t;e&&P(e)}function Xe(e){const{onClick:n}=t;n&&P(n,e)}function Ye(e){const{onWrapperFocus:n}=t;n&&P(n,e)}function Je(e){const{onWrapperBlur:n}=t;n&&P(n,e)}function Ze(){T.value=!0}function Ge(e){T.value=!1,e.target===p.value?Z(e,1):Z(e,0)}function Z(e,n=0,i="input"){const b=e.target.value;if(de(b),e instanceof InputEvent&&!e.isComposing&&(T.value=!1),t.type==="textarea"){const{value:g}=A;g&&g.syncUnifiedContainer()}if(oe=b,T.value)return;W.recordCursor();const y=Qe(b);if(y)if(!t.pair)i==="input"?Y(b):J(b);else{let{value:g}=F;Array.isArray(g)?g=[g[0],g[1]]:g=["",""],g[n]=b,i==="input"?Y(g):J(g)}Ve.$forceUpdate(),y||Me(W.restoreCursor)}function Qe(e){const{countGraphemes:n,maxlength:i,minlength:b}=t;if(n){let g;if(i!==void 0&&(g===void 0&&(g=n(e)),g>Number(i))||b!==void 0&&(g===void 0&&(g=n(e)),g<Number(i)))return!1}const{allowInput:y}=t;return typeof y=="function"?y(e):!0}function et(e){Ue(e),e.relatedTarget===d.value&&qe(),e.relatedTarget!==null&&(e.relatedTarget===h.value||e.relatedTarget===p.value||e.relatedTarget===s.value)||(V.value=!1),G(e,"blur"),x.value=null}function tt(e,n){He(e),E.value=!0,V.value=!0,Ke(),G(e,"focus"),n===0?x.value=h.value:n===1?x.value=p.value:n===2&&(x.value=s.value)}function nt(e){t.passivelyActivated&&(Je(e),G(e,"blur"))}function rt(e){t.passivelyActivated&&(E.value=!0,Ye(e),G(e,"focus"))}function G(e,n){e.relatedTarget!==null&&(e.relatedTarget===h.value||e.relatedTarget===p.value||e.relatedTarget===s.value||e.relatedTarget===d.value)||(n==="focus"?(Oe(e),E.value=!0):n==="blur"&&(Ne(e),E.value=!1))}function at(e,n){Z(e,n,"change")}function ot(e){Xe(e)}function it(e){je(e),t.pair?(Y(["",""]),J(["",""])):(Y(""),J(""))}function lt(e){const{onMousedown:n}=t;n&&n(e);const{tagName:i}=e.target;if(i!=="INPUT"&&i!=="TEXTAREA"){if(t.resizable){const{value:b}=d;if(b){const{left:y,top:g,width:k,height:_}=b.getBoundingClientRect(),R=14;if(y+k-R<e.clientX&&e.clientX<y+k&&g+_-R<e.clientY&&e.clientY<g+_)return}}e.preventDefault(),E.value||be()}}function st(){var e;L.value=!0,t.type==="textarea"&&((e=A.value)===null||e===void 0||e.handleMouseEnterWrapper())}function ut(){var e;L.value=!1,t.type==="textarea"&&((e=A.value)===null||e===void 0||e.handleMouseLeaveWrapper())}function dt(){$.value||se.value==="click"&&(N.value=!N.value)}function ct(e){if($.value)return;e.preventDefault();const n=b=>{b.preventDefault(),Pe("mouseup",document,n)};if(Ce("mouseup",document,n),se.value!=="mousedown")return;N.value=!0;const i=()=>{N.value=!1,Pe("mouseup",document,i)};Ce("mouseup",document,i)}function ht(e){var n;switch((n=t.onKeydown)===null||n===void 0||n.call(t,e),e.key){case"Escape":ue();break;case"Enter":ft(e);break}}function ft(e){var n,i;if(t.passivelyActivated){const{value:b}=V;if(b){t.internalDeactivateOnEnter&&ue();return}e.preventDefault(),t.type==="textarea"?(n=s.value)===null||n===void 0||n.focus():(i=h.value)===null||i===void 0||i.focus()}}function ue(){t.passivelyActivated&&(V.value=!1,Me(()=>{var e;(e=d.value)===null||e===void 0||e.focus()}))}function be(){var e,n,i;$.value||(t.passivelyActivated?(e=d.value)===null||e===void 0||e.focus():((n=s.value)===null||n===void 0||n.focus(),(i=h.value)===null||i===void 0||i.focus()))}function vt(){var e;!((e=d.value)===null||e===void 0)&&e.contains(document.activeElement)&&document.activeElement.blur()}function mt(){var e,n;(e=s.value)===null||e===void 0||e.select(),(n=h.value)===null||n===void 0||n.select()}function pt(){$.value||(s.value?s.value.focus():h.value&&h.value.focus())}function gt(){const{value:e}=d;(e==null?void 0:e.contains(document.activeElement))&&e!==document.activeElement&&ue()}function bt(e){if(t.type==="textarea"){const{value:n}=s;n==null||n.scrollTo(e)}else{const{value:n}=h;n==null||n.scrollTo(e)}}function de(e){const{type:n,pair:i,autosize:b}=t;if(!i&&b)if(n==="textarea"){const{value:y}=f;y&&(y.textContent=(e!=null?e:"")+`\r
`)}else{const{value:y}=u;y&&(e?y.textContent=e:y.innerHTML="&nbsp;")}}function yt(){Ie()}const ye=C({top:"0"});function wt(e){var n;const{scrollTop:i}=e.target;ye.value.top=`${-i}px`,(n=A.value)===null||n===void 0||n.syncUnifiedContainer()}let Q=null;Se(()=>{const{autosize:e,type:n}=t;e&&n==="textarea"?Q=me(F,i=>{!Array.isArray(i)&&i!==oe&&de(i)}):Q==null||Q()});let ee=null;Se(()=>{t.type==="textarea"?ee=me(F,e=>{var n;!Array.isArray(e)&&e!==oe&&((n=A.value)===null||n===void 0||n.syncUnifiedContainer())}):ee==null||ee()}),Mn(ke,{mergedValueRef:F,maxlengthRef:Le,mergedClsPrefixRef:o,countGraphemesRef:ve(t,"countGraphemes")});const xt={wrapperElRef:d,inputElRef:h,textareaElRef:s,isCompositing:T,focus:be,blur:vt,select:mt,deactivate:gt,activate:pt,scrollTo:bt},Ct=zn("Input",m,o),we=z(()=>{const{value:e}=ae,{common:{cubicBezierEaseInOut:n},self:{color:i,borderRadius:b,textColor:y,caretColor:g,caretColorError:k,caretColorWarning:_,textDecorationColor:R,border:O,borderDisabled:j,borderHover:ce,borderFocus:Pt,placeholderColor:St,placeholderColorDisabled:Mt,lineHeightTextarea:zt,colorDisabled:At,colorFocus:Ft,textColorDisabled:Tt,boxShadowFocus:kt,iconSize:_t,colorFocusWarning:Rt,boxShadowFocusWarning:Dt,borderWarning:Wt,borderFocusWarning:$t,borderHoverWarning:Et,colorFocusError:Bt,boxShadowFocusError:It,borderError:Lt,borderFocusError:Vt,borderHoverError:Nt,clearSize:Ot,clearColor:jt,clearColorHover:Ut,clearColorPressed:Ht,iconColor:qt,iconColorDisabled:Kt,suffixTextColor:Xt,countTextColor:Yt,countTextColorDisabled:Jt,iconColorHover:Zt,iconColorPressed:Gt,loadingColor:Qt,loadingColorError:en,loadingColorWarning:tn,[he("padding",e)]:nn,[he("fontSize",e)]:rn,[he("height",e)]:an}}=l.value,{left:on,right:ln}=mn(nn);return{"--n-bezier":n,"--n-count-text-color":Yt,"--n-count-text-color-disabled":Jt,"--n-color":i,"--n-font-size":rn,"--n-border-radius":b,"--n-height":an,"--n-padding-left":on,"--n-padding-right":ln,"--n-text-color":y,"--n-caret-color":g,"--n-text-decoration-color":R,"--n-border":O,"--n-border-disabled":j,"--n-border-hover":ce,"--n-border-focus":Pt,"--n-placeholder-color":St,"--n-placeholder-color-disabled":Mt,"--n-icon-size":_t,"--n-line-height-textarea":zt,"--n-color-disabled":At,"--n-color-focus":Ft,"--n-text-color-disabled":Tt,"--n-box-shadow-focus":kt,"--n-loading-color":Qt,"--n-caret-color-warning":_,"--n-color-focus-warning":Rt,"--n-box-shadow-focus-warning":Dt,"--n-border-warning":Wt,"--n-border-focus-warning":$t,"--n-border-hover-warning":Et,"--n-loading-color-warning":tn,"--n-caret-color-error":k,"--n-color-focus-error":Bt,"--n-box-shadow-focus-error":It,"--n-border-error":Lt,"--n-border-focus-error":Vt,"--n-border-hover-error":Nt,"--n-loading-color-error":en,"--n-clear-color":jt,"--n-clear-size":Ot,"--n-clear-color-hover":Ut,"--n-clear-color-pressed":Ht,"--n-icon-color":qt,"--n-icon-color-hover":Zt,"--n-icon-color-pressed":Gt,"--n-icon-color-disabled":Kt,"--n-suffix-text-color":Xt}}),B=v?An("input",z(()=>{const{value:e}=ae;return e[0]}),we,t):void 0;return Object.assign(Object.assign({},xt),{wrapperElRef:d,inputElRef:h,inputMirrorElRef:u,inputEl2Ref:p,textareaElRef:s,textareaMirrorElRef:f,textareaScrollbarInstRef:A,rtlEnabled:Ct,uncontrolledValue:X,mergedValue:F,passwordVisible:N,mergedPlaceholder:ie,showPlaceholder1:We,showPlaceholder2:$e,mergedFocus:le,isComposing:T,activated:V,showClearButton:Ee,mergedSize:ae,mergedDisabled:$,textDecorationStyle:Be,mergedClsPrefix:o,mergedBordered:r,mergedShowPasswordOn:se,placeholderStyle:ye,mergedStatus:De,textAreaScrollContainerWidth:ge,handleTextAreaScroll:wt,handleCompositionStart:Ze,handleCompositionEnd:Ge,handleInput:Z,handleInputBlur:et,handleInputFocus:tt,handleWrapperBlur:nt,handleWrapperFocus:rt,handleMouseEnter:st,handleMouseLeave:ut,handleMouseDown:lt,handleChange:at,handleClick:ot,handleClear:it,handlePasswordToggleClick:dt,handlePasswordToggleMousedown:ct,handleWrapperKeydown:ht,handleTextAreaMirrorResize:yt,getTextareaScrollContainer:()=>s.value,mergedTheme:l,cssVars:v?void 0:we,themeClass:B==null?void 0:B.themeClass,onRender:B==null?void 0:B.onRender})},render(){var t,o;const{mergedClsPrefix:r,mergedStatus:v,themeClass:m,type:l,countGraphemes:d,onRender:s}=this,f=this.$slots;return s==null||s(),a("div",{ref:"wrapperElRef",class:[`${r}-input`,m,v&&`${r}-input--${v}-status`,{[`${r}-input--rtl`]:this.rtlEnabled,[`${r}-input--disabled`]:this.mergedDisabled,[`${r}-input--textarea`]:l==="textarea",[`${r}-input--resizable`]:this.resizable&&!this.autosize,[`${r}-input--autosize`]:this.autosize,[`${r}-input--round`]:this.round&&l!=="textarea",[`${r}-input--pair`]:this.pair,[`${r}-input--focus`]:this.mergedFocus,[`${r}-input--stateful`]:this.stateful}],style:this.cssVars,tabindex:!this.mergedDisabled&&this.passivelyActivated&&!this.activated?0:void 0,onFocus:this.handleWrapperFocus,onBlur:this.handleWrapperBlur,onClick:this.handleClick,onMousedown:this.handleMouseDown,onMouseenter:this.handleMouseEnter,onMouseleave:this.handleMouseLeave,onCompositionstart:this.handleCompositionStart,onCompositionend:this.handleCompositionEnd,onKeyup:this.onKeyup,onKeydown:this.handleWrapperKeydown},a("div",{class:`${r}-input-wrapper`},te(f.prefix,u=>u&&a("div",{class:`${r}-input__prefix`},u)),l==="textarea"?a(hn,{ref:"textareaScrollbarInstRef",class:`${r}-input__textarea`,container:this.getTextareaScrollContainer,triggerDisplayManually:!0,useUnifiedContainer:!0,internalHoistYRail:!0},{default:()=>{var u,h;const{textAreaScrollContainerWidth:p}=this,x={width:this.autosize&&p&&`${p}px`};return a(Fn,null,a("textarea",Object.assign({},this.inputProps,{ref:"textareaElRef",class:[`${r}-input__textarea-el`,(u=this.inputProps)===null||u===void 0?void 0:u.class],autofocus:this.autofocus,rows:Number(this.rows),placeholder:this.placeholder,value:this.mergedValue,disabled:this.mergedDisabled,maxlength:d?void 0:this.maxlength,minlength:d?void 0:this.minlength,readonly:this.readonly,tabindex:this.passivelyActivated&&!this.activated?-1:void 0,style:[this.textDecorationStyle[0],(h=this.inputProps)===null||h===void 0?void 0:h.style,x],onBlur:this.handleInputBlur,onFocus:W=>this.handleInputFocus(W,2),onInput:this.handleInput,onChange:this.handleChange,onScroll:this.handleTextAreaScroll})),this.showPlaceholder1?a("div",{class:`${r}-input__placeholder`,style:[this.placeholderStyle,x],key:"placeholder"},this.mergedPlaceholder[0]):null,this.autosize?a(fn,{onResize:this.handleTextAreaMirrorResize},{default:()=>a("div",{ref:"textareaMirrorElRef",class:`${r}-input__textarea-mirror`,key:"mirror"})}):null)}}):a("div",{class:`${r}-input__input`},a("input",Object.assign({type:l==="password"&&this.mergedShowPasswordOn&&this.passwordVisible?"text":l},this.inputProps,{ref:"inputElRef",class:[`${r}-input__input-el`,(t=this.inputProps)===null||t===void 0?void 0:t.class],style:[this.textDecorationStyle[0],(o=this.inputProps)===null||o===void 0?void 0:o.style],tabindex:this.passivelyActivated&&!this.activated?-1:void 0,placeholder:this.mergedPlaceholder[0],disabled:this.mergedDisabled,maxlength:d?void 0:this.maxlength,minlength:d?void 0:this.minlength,value:Array.isArray(this.mergedValue)?this.mergedValue[0]:this.mergedValue,readonly:this.readonly,autofocus:this.autofocus,size:this.attrSize,onBlur:this.handleInputBlur,onFocus:u=>this.handleInputFocus(u,0),onInput:u=>this.handleInput(u,0),onChange:u=>this.handleChange(u,0)})),this.showPlaceholder1?a("div",{class:`${r}-input__placeholder`},a("span",null,this.mergedPlaceholder[0])):null,this.autosize?a("div",{class:`${r}-input__input-mirror`,key:"mirror",ref:"inputMirrorElRef"},"\xA0"):null),!this.pair&&te(f.suffix,u=>u||this.clearable||this.showCount||this.mergedShowPasswordOn||this.loading!==void 0?a("div",{class:`${r}-input__suffix`},[te(f["clear-icon-placeholder"],h=>(this.clearable||h)&&a(pe,{clsPrefix:r,show:this.showClearButton,onClear:this.handleClear},{placeholder:()=>h,icon:()=>{var p,x;return(x=(p=this.$slots)["clear-icon"])===null||x===void 0?void 0:x.call(p)}})),this.internalLoadingBeforeSuffix?null:u,this.loading!==void 0?a(Sr,{clsPrefix:r,loading:this.loading,showArrow:!1,showClear:!1,style:this.cssVars}):null,this.internalLoadingBeforeSuffix?u:null,this.showCount&&this.type!=="textarea"?a(ze,null,{default:h=>{var p;return(p=f.count)===null||p===void 0?void 0:p.call(f,h)}}):null,this.mergedShowPasswordOn&&this.type==="password"?a("div",{class:`${r}-input__eye`,onMousedown:this.handlePasswordToggleMousedown,onClick:this.handlePasswordToggleClick},this.passwordVisible?K(f["password-visible-icon"],()=>[a(re,{clsPrefix:r},{default:()=>a(yr,null)})]):K(f["password-invisible-icon"],()=>[a(re,{clsPrefix:r},{default:()=>a(wr,null)})])):null]):null)),this.pair?a("span",{class:`${r}-input__separator`},K(f.separator,()=>[this.separator])):null,this.pair?a("div",{class:`${r}-input-wrapper`},a("div",{class:`${r}-input__input`},a("input",{ref:"inputEl2Ref",type:this.type,class:`${r}-input__input-el`,tabindex:this.passivelyActivated&&!this.activated?-1:void 0,placeholder:this.mergedPlaceholder[1],disabled:this.mergedDisabled,maxlength:d?void 0:this.maxlength,minlength:d?void 0:this.minlength,value:Array.isArray(this.mergedValue)?this.mergedValue[1]:void 0,readonly:this.readonly,style:this.textDecorationStyle[1],onBlur:this.handleInputBlur,onFocus:u=>this.handleInputFocus(u,1),onInput:u=>this.handleInput(u,1),onChange:u=>this.handleChange(u,1)}),this.showPlaceholder2?a("div",{class:`${r}-input__placeholder`},a("span",null,this.mergedPlaceholder[1])):null),te(f.suffix,u=>(this.clearable||u)&&a("div",{class:`${r}-input__suffix`},[this.clearable&&a(pe,{clsPrefix:r,show:this.showClearButton,onClear:this.handleClear},{icon:()=>{var h;return(h=f["clear-icon"])===null||h===void 0?void 0:h.call(f)},placeholder:()=>{var h;return(h=f["clear-icon-placeholder"])===null||h===void 0?void 0:h.call(f)}}),u]))):null,this.mergedBordered?a("div",{class:`${r}-input__border`}):null,this.mergedBordered?a("div",{class:`${r}-input__state-border`}):null,this.showCount&&l==="textarea"?a(ze,null,{default:u=>{var h;const{renderCount:p}=this;return p?p(u):(h=f.count)===null||h===void 0?void 0:h.call(f,u)}}):null)}});export{xr as C,Sr as N,Rr as _,br as u};
