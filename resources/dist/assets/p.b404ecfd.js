import{n as q,d as ro,S as Do,U as Ro,r as U,D as Bo,V as Fo,y as w,l as f,L as Io,W as ho,J as Eo,K as Wo,p as R,M as C,X as uo,Y as Go,s as X,j as ko,v as O,q as io,N as Oo,R as r,Q as oo,x as lo,Z as Mo,_ as jo,$ as _o}from"./index.b447288a.js";import{i as No,b as Lo,u as Vo,c as Ko,d as bo,r as xo,e as Qo,f as qo,t as fo}from"./provider.ec926eef.js";const Xo=q("base-wave",`
 position: absolute;
 left: 0;
 right: 0;
 top: 0;
 bottom: 0;
 border-radius: inherit;
`),Yo=ro({name:"BaseWave",props:{clsPrefix:{type:String,required:!0}},setup(o){Do("-base-wave",Xo,Ro(o,"clsPrefix"));const s=U(null),b=U(!1);let d=null;return Bo(()=>{d!==null&&window.clearTimeout(d)}),{active:b,selfRef:s,play(){d!==null&&(window.clearTimeout(d),b.value=!1,d=null),Fo(()=>{var c;(c=s.value)===null||c===void 0||c.offsetHeight,b.value=!0,d=window.setTimeout(()=>{b.value=!1,d=null},1e3)})}}},render(){const{clsPrefix:o}=this;return w("div",{ref:"selfRef","aria-hidden":!0,class:[`${o}-base-wave`,this.active&&`${o}-base-wave--active`]})}}),{cubicBezierEaseInOut:k}=Io;function Uo({duration:o=".2s",delay:s=".1s"}={}){return[f("&.fade-in-width-expand-transition-leave-from, &.fade-in-width-expand-transition-enter-to",{opacity:1}),f("&.fade-in-width-expand-transition-leave-to, &.fade-in-width-expand-transition-enter-from",`
 opacity: 0!important;
 margin-left: 0!important;
 margin-right: 0!important;
 `),f("&.fade-in-width-expand-transition-leave-active",`
 overflow: hidden;
 transition:
 opacity ${o} ${k},
 max-width ${o} ${k} ${s},
 margin-left ${o} ${k} ${s},
 margin-right ${o} ${k} ${s};
 `),f("&.fade-in-width-expand-transition-enter-active",`
 overflow: hidden;
 transition:
 opacity ${o} ${k} ${s},
 max-width ${o} ${k},
 margin-left ${o} ${k},
 margin-right ${o} ${k};
 `)]}function V(o){return ho(o,[255,255,255,.16])}function eo(o){return ho(o,[0,0,0,.12])}const Ao=Eo("n-button-group"),Jo={paddingTiny:"0 6px",paddingSmall:"0 10px",paddingMedium:"0 14px",paddingLarge:"0 18px",paddingRoundTiny:"0 10px",paddingRoundSmall:"0 14px",paddingRoundMedium:"0 18px",paddingRoundLarge:"0 22px",iconMarginTiny:"6px",iconMarginSmall:"6px",iconMarginMedium:"6px",iconMarginLarge:"6px",iconSizeTiny:"14px",iconSizeSmall:"18px",iconSizeMedium:"18px",iconSizeLarge:"20px",rippleDuration:".6s"},Zo=o=>{const{heightTiny:s,heightSmall:b,heightMedium:d,heightLarge:c,borderRadius:v,fontSizeTiny:m,fontSizeSmall:I,fontSizeMedium:M,fontSizeLarge:j,opacityDisabled:_,textColor2:y,textColor3:N,primaryColorHover:u,primaryColorPressed:$,borderColor:K,primaryColor:B,baseColor:i,infoColor:F,infoColorHover:S,infoColorPressed:t,successColor:l,successColorHover:g,successColorPressed:e,warningColor:z,warningColorHover:T,warningColorPressed:W,errorColor:H,errorColorHover:p,errorColorPressed:G,fontWeight:E,buttonColor2:Q,buttonColor2Hover:D,buttonColor2Pressed:a,fontWeightStrong:A}=o;return Object.assign(Object.assign({},Jo),{heightTiny:s,heightSmall:b,heightMedium:d,heightLarge:c,borderRadiusTiny:v,borderRadiusSmall:v,borderRadiusMedium:v,borderRadiusLarge:v,fontSizeTiny:m,fontSizeSmall:I,fontSizeMedium:M,fontSizeLarge:j,opacityDisabled:_,colorOpacitySecondary:"0.16",colorOpacitySecondaryHover:"0.22",colorOpacitySecondaryPressed:"0.28",colorSecondary:Q,colorSecondaryHover:D,colorSecondaryPressed:a,colorTertiary:Q,colorTertiaryHover:D,colorTertiaryPressed:a,colorQuaternary:"#0000",colorQuaternaryHover:D,colorQuaternaryPressed:a,color:"#0000",colorHover:"#0000",colorPressed:"#0000",colorFocus:"#0000",colorDisabled:"#0000",textColor:y,textColorTertiary:N,textColorHover:u,textColorPressed:$,textColorFocus:u,textColorDisabled:y,textColorText:y,textColorTextHover:u,textColorTextPressed:$,textColorTextFocus:u,textColorTextDisabled:y,textColorGhost:y,textColorGhostHover:u,textColorGhostPressed:$,textColorGhostFocus:u,textColorGhostDisabled:y,border:`1px solid ${K}`,borderHover:`1px solid ${u}`,borderPressed:`1px solid ${$}`,borderFocus:`1px solid ${u}`,borderDisabled:`1px solid ${K}`,rippleColor:B,colorPrimary:B,colorHoverPrimary:u,colorPressedPrimary:$,colorFocusPrimary:u,colorDisabledPrimary:B,textColorPrimary:i,textColorHoverPrimary:i,textColorPressedPrimary:i,textColorFocusPrimary:i,textColorDisabledPrimary:i,textColorTextPrimary:B,textColorTextHoverPrimary:u,textColorTextPressedPrimary:$,textColorTextFocusPrimary:u,textColorTextDisabledPrimary:y,textColorGhostPrimary:B,textColorGhostHoverPrimary:u,textColorGhostPressedPrimary:$,textColorGhostFocusPrimary:u,textColorGhostDisabledPrimary:B,borderPrimary:`1px solid ${B}`,borderHoverPrimary:`1px solid ${u}`,borderPressedPrimary:`1px solid ${$}`,borderFocusPrimary:`1px solid ${u}`,borderDisabledPrimary:`1px solid ${B}`,rippleColorPrimary:B,colorInfo:F,colorHoverInfo:S,colorPressedInfo:t,colorFocusInfo:S,colorDisabledInfo:F,textColorInfo:i,textColorHoverInfo:i,textColorPressedInfo:i,textColorFocusInfo:i,textColorDisabledInfo:i,textColorTextInfo:F,textColorTextHoverInfo:S,textColorTextPressedInfo:t,textColorTextFocusInfo:S,textColorTextDisabledInfo:y,textColorGhostInfo:F,textColorGhostHoverInfo:S,textColorGhostPressedInfo:t,textColorGhostFocusInfo:S,textColorGhostDisabledInfo:F,borderInfo:`1px solid ${F}`,borderHoverInfo:`1px solid ${S}`,borderPressedInfo:`1px solid ${t}`,borderFocusInfo:`1px solid ${S}`,borderDisabledInfo:`1px solid ${F}`,rippleColorInfo:F,colorSuccess:l,colorHoverSuccess:g,colorPressedSuccess:e,colorFocusSuccess:g,colorDisabledSuccess:l,textColorSuccess:i,textColorHoverSuccess:i,textColorPressedSuccess:i,textColorFocusSuccess:i,textColorDisabledSuccess:i,textColorTextSuccess:l,textColorTextHoverSuccess:g,textColorTextPressedSuccess:e,textColorTextFocusSuccess:g,textColorTextDisabledSuccess:y,textColorGhostSuccess:l,textColorGhostHoverSuccess:g,textColorGhostPressedSuccess:e,textColorGhostFocusSuccess:g,textColorGhostDisabledSuccess:l,borderSuccess:`1px solid ${l}`,borderHoverSuccess:`1px solid ${g}`,borderPressedSuccess:`1px solid ${e}`,borderFocusSuccess:`1px solid ${g}`,borderDisabledSuccess:`1px solid ${l}`,rippleColorSuccess:l,colorWarning:z,colorHoverWarning:T,colorPressedWarning:W,colorFocusWarning:T,colorDisabledWarning:z,textColorWarning:i,textColorHoverWarning:i,textColorPressedWarning:i,textColorFocusWarning:i,textColorDisabledWarning:i,textColorTextWarning:z,textColorTextHoverWarning:T,textColorTextPressedWarning:W,textColorTextFocusWarning:T,textColorTextDisabledWarning:y,textColorGhostWarning:z,textColorGhostHoverWarning:T,textColorGhostPressedWarning:W,textColorGhostFocusWarning:T,textColorGhostDisabledWarning:z,borderWarning:`1px solid ${z}`,borderHoverWarning:`1px solid ${T}`,borderPressedWarning:`1px solid ${W}`,borderFocusWarning:`1px solid ${T}`,borderDisabledWarning:`1px solid ${z}`,rippleColorWarning:z,colorError:H,colorHoverError:p,colorPressedError:G,colorFocusError:p,colorDisabledError:H,textColorError:i,textColorHoverError:i,textColorPressedError:i,textColorFocusError:i,textColorDisabledError:i,textColorTextError:H,textColorTextHoverError:p,textColorTextPressedError:G,textColorTextFocusError:p,textColorTextDisabledError:y,textColorGhostError:H,textColorGhostHoverError:p,textColorGhostPressedError:G,textColorGhostFocusError:p,textColorGhostDisabledError:H,borderError:`1px solid ${H}`,borderHoverError:`1px solid ${p}`,borderPressedError:`1px solid ${G}`,borderFocusError:`1px solid ${p}`,borderDisabledError:`1px solid ${H}`,rippleColorError:H,waveOpacity:"0.6",fontWeight:E,fontWeightStrong:A})},oe={name:"Button",common:Wo,self:Zo},ee=oe,re=f([q("button",`
 margin: 0;
 font-weight: var(--n-font-weight);
 line-height: 1;
 font-family: inherit;
 padding: var(--n-padding);
 height: var(--n-height);
 font-size: var(--n-font-size);
 border-radius: var(--n-border-radius);
 color: var(--n-text-color);
 background-color: var(--n-color);
 width: var(--n-width);
 white-space: nowrap;
 outline: none;
 position: relative;
 z-index: auto;
 border: none;
 display: inline-flex;
 flex-wrap: nowrap;
 flex-shrink: 0;
 align-items: center;
 justify-content: center;
 user-select: none;
 -webkit-user-select: none;
 text-align: center;
 cursor: pointer;
 text-decoration: none;
 transition:
 color .3s var(--n-bezier),
 background-color .3s var(--n-bezier),
 opacity .3s var(--n-bezier),
 border-color .3s var(--n-bezier);
 `,[R("color",[C("border",{borderColor:"var(--n-border-color)"}),R("disabled",[C("border",{borderColor:"var(--n-border-color-disabled)"})]),uo("disabled",[f("&:focus",[C("state-border",{borderColor:"var(--n-border-color-focus)"})]),f("&:hover",[C("state-border",{borderColor:"var(--n-border-color-hover)"})]),f("&:active",[C("state-border",{borderColor:"var(--n-border-color-pressed)"})]),R("pressed",[C("state-border",{borderColor:"var(--n-border-color-pressed)"})])])]),R("disabled",{backgroundColor:"var(--n-color-disabled)",color:"var(--n-text-color-disabled)"},[C("border",{border:"var(--n-border-disabled)"})]),uo("disabled",[f("&:focus",{backgroundColor:"var(--n-color-focus)",color:"var(--n-text-color-focus)"},[C("state-border",{border:"var(--n-border-focus)"})]),f("&:hover",{backgroundColor:"var(--n-color-hover)",color:"var(--n-text-color-hover)"},[C("state-border",{border:"var(--n-border-hover)"})]),f("&:active",{backgroundColor:"var(--n-color-pressed)",color:"var(--n-text-color-pressed)"},[C("state-border",{border:"var(--n-border-pressed)"})]),R("pressed",{backgroundColor:"var(--n-color-pressed)",color:"var(--n-text-color-pressed)"},[C("state-border",{border:"var(--n-border-pressed)"})])]),R("loading","cursor: wait;"),q("base-wave",`
 pointer-events: none;
 top: 0;
 right: 0;
 bottom: 0;
 left: 0;
 animation-iteration-count: 1;
 animation-duration: var(--n-ripple-duration);
 animation-timing-function: var(--n-bezier-ease-out), var(--n-bezier-ease-out);
 `,[R("active",{zIndex:1,animationName:"button-wave-spread, button-wave-opacity"})]),No&&"MozBoxSizing"in document.createElement("div").style?f("&::moz-focus-inner",{border:0}):null,C("border, state-border",`
 position: absolute;
 left: 0;
 top: 0;
 right: 0;
 bottom: 0;
 border-radius: inherit;
 transition: border-color .3s var(--n-bezier);
 pointer-events: none;
 `),C("border",{border:"var(--n-border)"}),C("state-border",{border:"var(--n-border)",borderColor:"#0000",zIndex:1}),C("icon",`
 margin: var(--n-icon-margin);
 margin-left: 0;
 height: var(--n-icon-size);
 width: var(--n-icon-size);
 max-width: var(--n-icon-size);
 font-size: var(--n-icon-size);
 position: relative;
 flex-shrink: 0;
 `,[q("icon-slot",`
 height: var(--n-icon-size);
 width: var(--n-icon-size);
 position: absolute;
 left: 0;
 top: 50%;
 transform: translateY(-50%);
 display: flex;
 align-items: center;
 justify-content: center;
 `,[Go({top:"50%",originalTransform:"translateY(-50%)"})]),Uo()]),C("content",`
 display: flex;
 align-items: center;
 flex-wrap: nowrap;
 min-width: 0;
 `,[f("~",[C("icon",{margin:"var(--n-icon-margin)",marginRight:0})])]),R("block",`
 display: flex;
 width: 100%;
 `),R("dashed",[C("border, state-border",{borderStyle:"dashed !important"})]),R("disabled",{cursor:"not-allowed",opacity:"var(--n-opacity-disabled)"})]),f("@keyframes button-wave-spread",{from:{boxShadow:"0 0 0.5px 0 var(--n-ripple-color)"},to:{boxShadow:"0 0 0.5px 4.5px var(--n-ripple-color)"}}),f("@keyframes button-wave-opacity",{from:{opacity:"var(--n-wave-opacity)"},to:{opacity:0}})]),te=Object.assign(Object.assign({},X.props),{color:String,textColor:String,text:Boolean,block:Boolean,loading:Boolean,disabled:Boolean,circle:Boolean,size:String,ghost:Boolean,round:Boolean,secondary:Boolean,tertiary:Boolean,quaternary:Boolean,strong:Boolean,focusable:{type:Boolean,default:!0},keyboard:{type:Boolean,default:!0},tag:{type:String,default:"button"},type:{type:String,default:"default"},dashed:Boolean,renderIcon:Function,iconPlacement:{type:String,default:"left"},attrType:{type:String,default:"button"},bordered:{type:Boolean,default:!0},onClick:[Function,Array],nativeFocusBehavior:{type:Boolean,default:!Lo}}),po=ro({name:"Button",props:te,setup(o){const s=U(null),b=U(null),d=U(!1),c=Vo(()=>!o.quaternary&&!o.tertiary&&!o.secondary&&!o.text&&(!o.color||o.ghost||o.dashed)&&o.bordered),v=ko(Ao,{}),{mergedSizeRef:m}=Ko({},{defaultSize:"medium",mergedSize:t=>{const{size:l}=o;if(l)return l;const{size:g}=v;if(g)return g;const{mergedSize:e}=t||{};return e?e.value:"medium"}}),I=O(()=>o.focusable&&!o.disabled),M=t=>{var l;I.value||t.preventDefault(),!o.nativeFocusBehavior&&(t.preventDefault(),!o.disabled&&I.value&&((l=s.value)===null||l===void 0||l.focus({preventScroll:!0})))},j=t=>{var l;if(!o.disabled&&!o.loading){const{onClick:g}=o;g&&qo(g,t),o.text||(l=b.value)===null||l===void 0||l.play()}},_=t=>{switch(t.key){case"Enter":if(!o.keyboard)return;d.value=!1}},y=t=>{switch(t.key){case"Enter":if(!o.keyboard||o.loading){t.preventDefault();return}d.value=!0}},N=()=>{d.value=!1},{inlineThemeDisabled:u,mergedClsPrefixRef:$,mergedRtlRef:K}=io(o),B=X("Button","-button",re,ee,o,$),i=Oo("Button",K,$),F=O(()=>{const t=B.value,{common:{cubicBezierEaseInOut:l,cubicBezierEaseOut:g},self:e}=t,{rippleDuration:z,opacityDisabled:T,fontWeight:W,fontWeightStrong:H}=e,p=m.value,{dashed:G,type:E,ghost:Q,text:D,color:a,round:A,circle:to,textColor:L,secondary:vo,tertiary:ao,quaternary:go,strong:Co}=o,mo={"font-weight":Co?H:W};let x={"--n-color":"initial","--n-color-hover":"initial","--n-color-pressed":"initial","--n-color-focus":"initial","--n-color-disabled":"initial","--n-ripple-color":"initial","--n-text-color":"initial","--n-text-color-hover":"initial","--n-text-color-pressed":"initial","--n-text-color-focus":"initial","--n-text-color-disabled":"initial"};const J=E==="tertiary",co=E==="default",n=J?"default":E;if(D){const h=L||a;x={"--n-color":"#0000","--n-color-hover":"#0000","--n-color-pressed":"#0000","--n-color-focus":"#0000","--n-color-disabled":"#0000","--n-ripple-color":"#0000","--n-text-color":h||e[r("textColorText",n)],"--n-text-color-hover":h?V(h):e[r("textColorTextHover",n)],"--n-text-color-pressed":h?eo(h):e[r("textColorTextPressed",n)],"--n-text-color-focus":h?V(h):e[r("textColorTextHover",n)],"--n-text-color-disabled":h||e[r("textColorTextDisabled",n)]}}else if(Q||G){const h=L||a;x={"--n-color":"#0000","--n-color-hover":"#0000","--n-color-pressed":"#0000","--n-color-focus":"#0000","--n-color-disabled":"#0000","--n-ripple-color":a||e[r("rippleColor",n)],"--n-text-color":h||e[r("textColorGhost",n)],"--n-text-color-hover":h?V(h):e[r("textColorGhostHover",n)],"--n-text-color-pressed":h?eo(h):e[r("textColorGhostPressed",n)],"--n-text-color-focus":h?V(h):e[r("textColorGhostHover",n)],"--n-text-color-disabled":h||e[r("textColorGhostDisabled",n)]}}else if(vo){const h=co?e.textColor:J?e.textColorTertiary:e[r("color",n)],P=a||h,Z=E!=="default"&&E!=="tertiary";x={"--n-color":Z?oo(P,{alpha:Number(e.colorOpacitySecondary)}):e.colorSecondary,"--n-color-hover":Z?oo(P,{alpha:Number(e.colorOpacitySecondaryHover)}):e.colorSecondaryHover,"--n-color-pressed":Z?oo(P,{alpha:Number(e.colorOpacitySecondaryPressed)}):e.colorSecondaryPressed,"--n-color-focus":Z?oo(P,{alpha:Number(e.colorOpacitySecondaryHover)}):e.colorSecondaryHover,"--n-color-disabled":e.colorSecondary,"--n-ripple-color":"#0000","--n-text-color":P,"--n-text-color-hover":P,"--n-text-color-pressed":P,"--n-text-color-focus":P,"--n-text-color-disabled":P}}else if(ao||go){const h=co?e.textColor:J?e.textColorTertiary:e[r("color",n)],P=a||h;ao?(x["--n-color"]=e.colorTertiary,x["--n-color-hover"]=e.colorTertiaryHover,x["--n-color-pressed"]=e.colorTertiaryPressed,x["--n-color-focus"]=e.colorSecondaryHover,x["--n-color-disabled"]=e.colorTertiary):(x["--n-color"]=e.colorQuaternary,x["--n-color-hover"]=e.colorQuaternaryHover,x["--n-color-pressed"]=e.colorQuaternaryPressed,x["--n-color-focus"]=e.colorQuaternaryHover,x["--n-color-disabled"]=e.colorQuaternary),x["--n-ripple-color"]="#0000",x["--n-text-color"]=P,x["--n-text-color-hover"]=P,x["--n-text-color-pressed"]=P,x["--n-text-color-focus"]=P,x["--n-text-color-disabled"]=P}else x={"--n-color":a||e[r("color",n)],"--n-color-hover":a?V(a):e[r("colorHover",n)],"--n-color-pressed":a?eo(a):e[r("colorPressed",n)],"--n-color-focus":a?V(a):e[r("colorFocus",n)],"--n-color-disabled":a||e[r("colorDisabled",n)],"--n-ripple-color":a||e[r("rippleColor",n)],"--n-text-color":L||(a?e.textColorPrimary:J?e.textColorTertiary:e[r("textColor",n)]),"--n-text-color-hover":L||(a?e.textColorHoverPrimary:e[r("textColorHover",n)]),"--n-text-color-pressed":L||(a?e.textColorPressedPrimary:e[r("textColorPressed",n)]),"--n-text-color-focus":L||(a?e.textColorFocusPrimary:e[r("textColorFocus",n)]),"--n-text-color-disabled":L||(a?e.textColorDisabledPrimary:e[r("textColorDisabled",n)])};let no={"--n-border":"initial","--n-border-hover":"initial","--n-border-pressed":"initial","--n-border-focus":"initial","--n-border-disabled":"initial"};D?no={"--n-border":"none","--n-border-hover":"none","--n-border-pressed":"none","--n-border-focus":"none","--n-border-disabled":"none"}:no={"--n-border":e[r("border",n)],"--n-border-hover":e[r("borderHover",n)],"--n-border-pressed":e[r("borderPressed",n)],"--n-border-focus":e[r("borderFocus",n)],"--n-border-disabled":e[r("borderDisabled",n)]};const{[r("height",p)]:so,[r("fontSize",p)]:yo,[r("padding",p)]:Po,[r("paddingRound",p)]:So,[r("iconSize",p)]:$o,[r("borderRadius",p)]:To,[r("iconMargin",p)]:wo,waveOpacity:zo}=e,Ho={"--n-width":to&&!D?so:"initial","--n-height":D?"initial":so,"--n-font-size":yo,"--n-padding":to||D?"initial":A?So:Po,"--n-icon-size":$o,"--n-icon-margin":wo,"--n-border-radius":D?"initial":to||A?so:To};return Object.assign(Object.assign(Object.assign(Object.assign({"--n-bezier":l,"--n-bezier-ease-out":g,"--n-ripple-duration":z,"--n-opacity-disabled":T,"--n-wave-opacity":zo},mo),x),no),Ho)}),S=u?lo("button",O(()=>{let t="";const{dashed:l,type:g,ghost:e,text:z,color:T,round:W,circle:H,textColor:p,secondary:G,tertiary:E,quaternary:Q,strong:D}=o;l&&(t+="a"),e&&(t+="b"),z&&(t+="c"),W&&(t+="d"),H&&(t+="e"),G&&(t+="f"),E&&(t+="g"),Q&&(t+="h"),D&&(t+="i"),T&&(t+="j"+bo(T)),p&&(t+="k"+bo(p));const{value:a}=m;return t+="l"+a[0],t+="m"+g[0],t}),F,o):void 0;return{selfElRef:s,waveElRef:b,mergedClsPrefix:$,mergedFocusable:I,mergedSize:m,showBorder:c,enterPressed:d,rtlEnabled:i,handleMousedown:M,handleKeydown:y,handleBlur:N,handleKeyup:_,handleClick:j,customColorCssVars:O(()=>{const{color:t}=o;if(!t)return null;const l=V(t);return{"--n-border-color":t,"--n-border-color-hover":l,"--n-border-color-pressed":eo(t),"--n-border-color-focus":l,"--n-border-color-disabled":t}}),cssVars:u?void 0:F,themeClass:S==null?void 0:S.themeClass,onRender:S==null?void 0:S.onRender}},render(){const{mergedClsPrefix:o,tag:s,onRender:b}=this;b==null||b();const d=xo(this.$slots.default,c=>c&&w("span",{class:`${o}-button__content`},c));return w(s,{ref:"selfElRef",class:[this.themeClass,`${o}-button`,`${o}-button--${this.type}-type`,`${o}-button--${this.mergedSize}-type`,this.rtlEnabled&&`${o}-button--rtl`,this.disabled&&`${o}-button--disabled`,this.block&&`${o}-button--block`,this.enterPressed&&`${o}-button--pressed`,!this.text&&this.dashed&&`${o}-button--dashed`,this.color&&`${o}-button--color`,this.secondary&&`${o}-button--secondary`,this.loading&&`${o}-button--loading`,this.ghost&&`${o}-button--ghost`],tabindex:this.mergedFocusable?0:-1,type:this.attrType,style:this.cssVars,disabled:this.disabled,onClick:this.handleClick,onBlur:this.handleBlur,onMousedown:this.handleMousedown,onKeyup:this.handleKeyup,onKeydown:this.handleKeydown},this.iconPlacement==="right"&&d,w(Mo,{width:!0},{default:()=>xo(this.$slots.icon,c=>(this.loading||this.renderIcon||c)&&w("span",{class:`${o}-button__icon`,style:{margin:Qo(this.$slots.default)?"0":""}},w(jo,null,{default:()=>this.loading?w(_o,{clsPrefix:o,key:"loading",class:`${o}-icon-slot`,strokeWidth:20}):w("div",{key:"icon",class:`${o}-icon-slot`,role:"none"},this.renderIcon?this.renderIcon():c)})))}),this.iconPlacement==="left"&&d,this.text?null:w(Yo,{ref:"waveElRef",clsPrefix:o}),this.showBorder?w("div",{"aria-hidden":!0,class:`${o}-button__border`,style:this.customColorCssVars}):null,this.showBorder?w("div",{"aria-hidden":!0,class:`${o}-button__state-border`,style:this.customColorCssVars}):null)}}),ce=po,ue=po,ne=q("h",`
 font-size: var(--n-font-size);
 font-weight: var(--n-font-weight);
 margin: var(--n-margin);
 transition: color .3s var(--n-bezier);
 color: var(--n-text-color);
`,[f("&:first-child",{marginTop:0}),R("prefix-bar",{position:"relative",paddingLeft:"var(--n-prefix-width)"},[R("align-text",{paddingLeft:0},[f("&::before",{left:"calc(-1 * var(--n-prefix-width))"})]),f("&::before",`
 content: "";
 width: var(--n-bar-width);
 border-radius: calc(var(--n-bar-width) / 2);
 transition: background-color .3s var(--n-bezier);
 left: 0;
 top: 0;
 bottom: 0;
 position: absolute;
 `),f("&::before",{backgroundColor:"var(--n-bar-color)"})])]),se=Object.assign(Object.assign({},X.props),{type:{type:String,default:"default"},prefix:String,alignText:Boolean}),Y=o=>ro({name:`H${o}`,props:se,setup(s){const{mergedClsPrefixRef:b,inlineThemeDisabled:d}=io(s),c=X("Typography","-h",ne,fo,s,b),v=O(()=>{const{type:I}=s,{common:{cubicBezierEaseInOut:M},self:{headerFontWeight:j,headerTextColor:_,[r("headerPrefixWidth",o)]:y,[r("headerFontSize",o)]:N,[r("headerMargin",o)]:u,[r("headerBarWidth",o)]:$,[r("headerBarColor",I)]:K}}=c.value;return{"--n-bezier":M,"--n-font-size":N,"--n-margin":u,"--n-bar-color":K,"--n-bar-width":$,"--n-font-weight":j,"--n-text-color":_,"--n-prefix-width":y}}),m=d?lo(`h${o}`,O(()=>s.type[0]),v,s):void 0;return{mergedClsPrefix:b,cssVars:d?void 0:v,themeClass:m==null?void 0:m.themeClass,onRender:m==null?void 0:m.onRender}},render(){var s;const{prefix:b,alignText:d,mergedClsPrefix:c,cssVars:v,$slots:m}=this;return(s=this.onRender)===null||s===void 0||s.call(this),w(`h${o}`,{class:[`${c}-h`,`${c}-h${o}`,this.themeClass,{[`${c}-h--prefix-bar`]:b,[`${c}-h--align-text`]:d}],style:v},m)}}),be=Y("1"),xe=Y("2");Y("3");Y("4");Y("5");Y("6");const ie=q("p",`
 box-sizing: border-box;
 transition: color .3s var(--n-bezier);
 margin: var(--n-margin);
 font-size: var(--n-font-size);
 line-height: var(--n-line-height);
 color: var(--n-text-color);
`,[f("&:first-child","margin-top: 0;"),f("&:last-child","margin-bottom: 0;")]),le=Object.assign(Object.assign({},X.props),{depth:[String,Number]}),he=ro({name:"P",props:le,setup(o){const{mergedClsPrefixRef:s,inlineThemeDisabled:b}=io(o),d=X("Typography","-p",ie,fo,o,s),c=O(()=>{const{depth:m}=o,I=m||"1",{common:{cubicBezierEaseInOut:M},self:{pFontSize:j,pLineHeight:_,pMargin:y,pTextColor:N,[`pTextColor${I}Depth`]:u}}=d.value;return{"--n-bezier":M,"--n-font-size":j,"--n-line-height":_,"--n-margin":y,"--n-text-color":m===void 0?N:u}}),v=b?lo("p",O(()=>`${o.depth||""}`),c,o):void 0;return{mergedClsPrefix:s,cssVars:b?void 0:c,themeClass:v==null?void 0:v.themeClass,onRender:v==null?void 0:v.onRender}},render(){var o;return(o=this.onRender)===null||o===void 0||o.call(this),w("p",{class:[`${this.mergedClsPrefix}-p`,this.themeClass],style:this.cssVars},this.$slots)}});export{be as N,ue as X,he as _,xe as a,ce as b,ee as c,Zo as s};
