
/*****YTPRO*******
Author: Prateek Chaubey
Version: 3.9.8
URI: https://github.com/prateek-chaubey/YTPRO
Last Updated On: 1 May , 2026 , 19:25 IST
*/

window.ytproSabrDownload= async function() {
var ytproDownDiv=getDownloadElement();
ytproDownDiv.querySelector("#videoViewDiv").innerHTML="Loading...";

var videoId ="";
if(window.location.pathname.indexOf("shorts") > -1){
videoId=window.location.pathname.substr(8,window.location.pathname.length);
}
else{
videoId=new URLSearchParams(window.location.search).get("v");
}

if (!videoId) { window.Android?.showToast?.('No video ID found in URL.'); return; }

const { Innertube, Platform, Constants } = await import(
'https://cdn.jsdelivr.net/npm/youtubei.js@17.0.1/bundle/browser.min.js'
);
const { SabrStream } = await import('https://esm.sh/googlevideo@4.0.4/sabr-stream');
const { buildSabrFormat , EnabledTrackTypes } = await import('https://esm.sh/googlevideo@4.0.4/utils');
const { BG, buildURL, getHeaders } = await import('https://esm.sh/bgutils-js@3.2.0');

Platform.shim.eval = async (data, env) => {
const props = [];
if (env.n)   props.push(`n: exportedVars.nFunction("${env.n}")`);
if (env.sig) props.push(`sig: exportedVars.sigFunction("${env.sig}")`);
return new Function(`${data.output}\nreturn { ${props.join(', ')} }`)();
};

const cookies = window.Android?.getAllCookies?.('https://www.youtube.com') ?? '';

const yt = await Innertube.create({
cookie: cookies,
retrieve_player: true,
generate_session_locally: true,
fetch: async (input, init = {}) => {
const reqUrl = input instanceof Request ? input.url : input.toString();
const url    = new URL(reqUrl);
const method = init.method ?? (input instanceof Request ? input.method : 'GET');
const headers = new Headers();

if (input instanceof Request) input.headers.forEach((v, k) => headers.set(k, v));
if (init.headers) new Headers(init.headers).forEach((v, k) => headers.set(k, v));

headers.set('User-Agent', "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
headers.set('Sec-Ch-Ua', '"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"');
headers.set('Sec-Ch-Ua-Mobile', '?0');
headers.set('Sec-Ch-Ua-Platform', '"Windows"');

const playerId = Array.from(document.scripts)
.map(s => s.src.match(/player\/(.*?)\/player/))
.find(m => m)?.[1] || '4b0d80ee';

if (url.pathname === '/iframe_api') {
const mockedApiCode = `var scriptUrl = 'https:\\/\\/www.youtube.com\\/s\\/player\\/${playerId}\\/www-widgetapi.vflset\\/www-widgetapi.js';try{var ttPolicy=window.trustedTypes.createPolicy("youtube-widget-api",{createScriptURL:function(x){return x}});scriptUrl=ttPolicy.createScriptURL(scriptUrl)}catch(e){}var YT;if(!window["YT"])YT={loading:0,loaded:0};var YTConfig;if(!window["YTConfig"])YTConfig={"host":"https://www.youtube.com"};\nif(!YT.loading){YT.loading=1;(function(){var l=[];YT.ready=function(f){if(YT.loaded)f();else l.push(f)};window.onYTReady=function(){YT.loaded=1;var i=0;for(;i<l.length;i++)try{l[i]()}catch(e){}};YT.setConfig=function(c){var k;for(k in c)if(c.hasOwnProperty(k))YTConfig[k]=c[k]};var a=document.createElement("script");a.type="text/javascript";a.id="www-widgetapi-script";a.src=scriptUrl;a.async=true;var c=document.currentScript;if(c){var n=c.nonce||c.getAttribute("nonce");if(n)a.setAttribute("nonce",\nn)}var b=document.getElementsByTagName("script")[0];b.parentNode.insertBefore(a,b)})()};`;
return new Response(mockedApiCode, { status: 200, headers: { 'Content-Type': 'text/javascript' } });
}

if (url.pathname.startsWith('/s/player/')) {
url.hostname = 'www.youtube.com';
headers.delete('Cookie');
headers.set('Origin',  'https://www.youtube.com');
headers.set('Referer', 'https://www.youtube.com/');
} else {
if (url.hostname.includes('youtube.com')) url.hostname = 'm.youtube.com';
headers.set('Origin',  'https://m.youtube.com');
headers.set('Referer', 'https://m.youtube.com/');
if (cookies) headers.set('Cookie', cookies);
}

let body = init.body ?? null;
if (!body && input instanceof Request && method !== 'GET' && method !== 'HEAD') {
body = await input.arrayBuffer();
}
return fetch(url.toString(), { method, headers, body, credentials: 'omit' });
}
});

let placeholderPoToken = null;
try { placeholderPoToken = BG.PoToken.generatePlaceholder(videoId); } catch (e) {}

async function generateFullPoToken() {
try {
const challengeResponse = await yt.getAttestationChallenge('ENGAGEMENT_TYPE_UNBOUND');
const bg = challengeResponse.bg_challenge;

const challenge = {
interpreterUrl: {
privateDoNotAccessOrElseTrustedResourceUrlWrappedValue:
bg.interpreter_url.private_do_not_access_or_else_trusted_resource_url_wrapped_value,
},
interpreterHash:            bg.interpreter_hash,
program:                    bg.program,
globalName:                 bg.global_name,
clientExperimentsStateBlob: bg.client_experiments_state_blob,
};

const interpreterJsRes = await fetch(
`https:${challenge.interpreterUrl.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue}`
);
const interpreterJS = await interpreterJsRes.text();

new Function(interpreterJS)();
const bgClient = await BG.BotGuardClient.create({
program:    challenge.program,
globalName: challenge.globalName,
globalObj:  window,
});

const webPoSignalOutput = [];
const botguardResponse  = await bgClient.snapshot({ webPoSignalOutput });

const REQUEST_KEY       = 'O43z0dpjhgX20SCx4KAo';
const integrityTokenRes = await fetch(buildURL('GenerateIT'), {
method:  'POST',
headers: getHeaders(),
body:    JSON.stringify([REQUEST_KEY, botguardResponse]),
});
const [integrityToken, estimatedTtlSecs, mintRefreshThreshold, websafeFallbackToken] =
await integrityTokenRes.json();

if (!integrityToken) throw new Error('Empty integrity token');

const minter  = await BG.WebPoMinter.create(
{ integrityToken, estimatedTtlSecs, mintRefreshThreshold, websafeFallbackToken },
webPoSignalOutput
);

return await minter.mintAsWebsafeString(videoId);
} catch (e) {
console.error('[YTPRO] PoToken generation failed:', e);
return null;
}
}

const fullTokenPromise = generateFullPoToken();

const info = await yt.getBasicInfo(videoId, { client: 'WEB' });
const player = yt.session.player;
const streamingData = info.streaming_data;

if (!streamingData || !player) { 
window.Android?.showToast?.('No streaming data or player found.'); 
return; 
}

const safeTitle = info.basic_info.title.replace(/[\/\\?%*:|"<>]/g, '-');

const formatBytes = (bytes) => {
if (window.formatFileSize) return window.formatFileSize(bytes);
if (bytes === 0 || isNaN(bytes)) return "Unknown Size";
const k = 1024;
const sizes = ['Bytes', 'KB', 'MB', 'GB'];
const i = Math.floor(Math.log(bytes) / Math.log(k));
return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const cleanFormat = (f) => {
const durationSec = (f.approxDurationMs || f.approx_duration_ms || info.basic_info.duration * 1000 || 0) / 1000;
const bytes = f.contentLength ? parseInt(f.contentLength) : (f.bitrate ? Math.floor((f.bitrate * durationSec) / 8) : 0);
const mime = f.mimeType || f.mime_type || "";
const isWebm = mime.includes('webm');
const isMp4 = mime.includes('mp4');
const codec = mime.match(/codecs="(.*?)"/)?.[1] || "";

return {
itag: f.itag,
mimeType: mime,
container: isWebm ? 'webm' : (isMp4 ? 'mp4' : 'other'),
codec: codec,
qualityLabel: f.qualityLabel || f.quality_label || null,
bitrate: f.bitrate,
width: f.width,
hasVideo: !!f.width,
hasAudio: !!f.audioSampleRate || !!f.audio_sample_rate || mime.startsWith('audio/'),
languageId: f.language || f.audioTrack?.id || f.audio_track?.id || 'default',
languageName: f.audioTrack?.displayName || f.audio_track?.display_name || 'Default',
isDefaultAudio: f.audioTrack?.audioIsDefault || f.audio_track?.audio_is_default || (!f.audioTrack && !f.audio_track),
sizeBytes: bytes,
audioQuality:f.audio_quality || null,
audioTrackId:f.audio_track?.id,
sizeFormatted: formatBytes(bytes)
};
};

const rawFormats = streamingData.formats || [];
const rawAdaptive = streamingData.adaptive_formats || [];

const preMuxed = rawFormats.map(cleanFormat);
const adaptive = rawAdaptive.map(cleanFormat);

const videoOnly = adaptive.filter(f => f.hasVideo && !f.hasAudio);
const audioOnly = adaptive.filter(f => f.hasAudio && !f.hasVideo);

const muxableOptions = [];

const uniqueQualities = [...new Set(videoOnly.map(v => v.qualityLabel).filter(Boolean))]
.sort((a, b) => parseInt(b) - parseInt(a));

const uniqueLanguages = [];
const langMap = new Map();
audioOnly.forEach(a => {
if (!langMap.has(a.languageId)) {
langMap.set(a.languageId, { id: a.languageId, name: a.languageName, isDefault: a.isDefaultAudio });
uniqueLanguages.push(langMap.get(a.languageId));
}
});

uniqueQualities.forEach(quality => {
const vForQuality = videoOnly.filter(v => v.qualityLabel === quality && !v.codec.includes('av01'));

const mp4Video = vForQuality.filter(v => v.container === 'mp4').sort((a,b) => b.bitrate - a.bitrate)[0];
const webmVideo = vForQuality.filter(v => v.container === 'webm').sort((a,b) => b.bitrate - a.bitrate)[0];

uniqueLanguages.forEach(lang => {
const aForLang = audioOnly.filter(a => a.languageId === lang.id);

const mp4Audio = aForLang.filter(a => a.container === 'mp4').sort((a,b) => b.bitrate - a.bitrate)[0];
const webmAudio = aForLang.filter(a => a.container === 'webm').sort((a,b) => b.bitrate - a.bitrate)[0];

if (mp4Video && mp4Audio) {
muxableOptions.push({
type: 'muxable',
qualityLabel: quality,
language: lang.name,
languageId: lang.id,
isDefaultLanguage: lang.isDefault,
container: 'mp4',
totalBytes: mp4Video.sizeBytes + mp4Audio.sizeBytes,
totalSizeFormatted: formatBytes(mp4Video.sizeBytes + mp4Audio.sizeBytes),
videoItag: mp4Video.itag,
audioItag: mp4Audio.itag,
videoDetails: mp4Video,
audioDetails: mp4Audio
});
}

if (webmVideo && webmAudio) {
muxableOptions.push({
type: 'muxable',
qualityLabel: quality,
language: lang.name,
languageId: lang.id,
isDefaultLanguage: lang.isDefault,
container: 'webm',
totalBytes: webmVideo.sizeBytes + webmAudio.sizeBytes,
totalSizeFormatted: formatBytes(webmVideo.sizeBytes + webmAudio.sizeBytes),
videoItag: webmVideo.itag,
audioItag: webmAudio.itag,
videoDetails: webmVideo,
audioDetails: webmAudio
});
}
});
});

const ytproMediaData = {
title: info.basic_info.title,
videoId: videoId,
durationSec: info.basic_info.duration || 0,
categories: {
"muxable": muxableOptions,
"audioOnly": audioOnly,
"videoOnly": videoOnly
}
};

ytproDownDiv.insertAdjacentHTML('beforeend',`<style>#downytprodiv a{text-decoration:none;} #downytprodiv li{list-style:none; display:flex;align-items:center;justify-content:center;border-radius:25px;padding:8px;background:${d};margin:5px;margin-top:8px}
#downytprodiv select {
min-height: 20px;
width: auto;
border-radius: 25px;
border: 0;
padding:5px;
color:${c};
font-size:12px;
background:${d};
}
</style>`);

ytproDownDiv.querySelector("#videoViewDiv").innerHTML=`<label for="selectLang" style="margin-right:5px;">Language:</label>`;

var langList=document.createElement("select");
langList.setAttribute("id","selectLang")

uniqueLanguages.forEach(l=>{
var sl=document.createElement("option");
sl.textContent=l.name;
sl.value=l.id;
if (l.isDefault === true) {
sl.selected = true;
}
langList.appendChild(sl);
});

ytproDownDiv.querySelector("#videoViewDiv").appendChild(langList);

langList.addEventListener("change",(e)=>{
updateMuxFormats(e.target.value);
updateAudioOnlyFormats(e.target.value);
})

var createAndAppend=()=>{
var div=document.createElement("div");
ytproDownDiv.querySelector("#videoViewDiv").appendChild(div);
return div;
}

var muxedDiv=createAndAppend();
var audioOnlyDiv=createAndAppend();
var videoOnlyDiv=createAndAppend();

function updateMuxFormats(langId=uniqueLanguages.filter( arr => { return arr.isDefault;})[0].id){
muxedDiv.innerHTML="";
muxableOptions.forEach(mux =>{
if(mux.languageId != langId) return;

var formatLi=document.createElement("li");
Object.assign(formatLi.dataset,{
langId:mux.audioDetails.audioTrackId,
isWebm:mux.container == "webm",
audioItag:mux.audioItag,
videoItag:mux.videoItag
});

formatLi.innerHTML=`${downBtn}<span style="margin-left:10px;" >${mux.qualityLabel} | ${mux.container.toUpperCase()} | ${mux.totalSizeFormatted}</span>`;
muxedDiv.appendChild(formatLi);
});
}

function updateAudioOnlyFormats(langId=uniqueLanguages.filter( arr => { return arr.isDefault;})[0].id){
audioOnlyDiv.innerHTML="";
var formatDivider=document.createElement("li");

formatDivider.innerHTML=`
<span>Audio Only (${uniqueLanguages.filter( arr => { return arr.id==langId;})[0].name})</span> 
<span style="margin-left:10px;transform:rotate(180deg);"  >
<svg style="margin-top:5px" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="${c}"  viewBox="0 0 18 18">
<path fill-rule="evenodd" d="M1.646 4.646a.5.5 0 0 1 .708 0L8 10.293l5.646-5.647a.5.5 0 0 1 .708.708l-6 6a.5.5 0 0 1-.708 0l-6-6a.5.5 0 0 1 0-.708"/>
</svg>
</span>
`;
Object.assign(formatDivider.style,{
minHeight:"20px",
borderRadius:"5px",
background:"#0000"
})

audioOnlyDiv.appendChild(formatDivider);

formatDivider.addEventListener("click",()=>{
Array.from(formatDivider.parentElement.children).forEach((c,i)=>{
if(i == 0) {
c.children[1].style.transform = c.children[1].style.transform === "rotate(180deg)" ? "rotate(0deg)" : "rotate(180deg)";
return;
}
c.style.display = c.style.display === "none" ? "flex" : "none";
})
});

audioOnly.forEach(aud =>{
if(aud.languageId != langId) return;

var formatLi=document.createElement("li");
Object.assign(formatLi.dataset,{
langId:aud.audioTrackId,
isWebm:aud.container == "webm",
audioItag:aud.itag
});

formatLi.innerHTML=`${downBtn}<span style="margin-left:10px;">${aud.audioQuality.replaceAll("AUDIO_QUALITY_"," ")} | ${aud.sizeFormatted}`;
audioOnlyDiv.appendChild(formatLi);
});
}

function updateVideoOnlyFormats(){
videoOnlyDiv.innerHTML="";
var formatDivider=document.createElement("li");

formatDivider.innerHTML=`
<span>Video Only</span> 
<span style="margin-left:10px;transform:rotate(180deg);"  >
<svg style="margin-top:5px" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="${c}"  viewBox="0 0 18 18">
<path fill-rule="evenodd" d="M1.646 4.646a.5.5 0 0 1 .708 0L8 10.293l5.646-5.647a.5.5 0 0 1 .708.708l-6 6a.5.5 0 0 1-.708 0l-6-6a.5.5 0 0 1 0-.708"/>
</svg>
</span>
`;
Object.assign(formatDivider.style,{
minHeight:"20px",
borderRadius:"5px",
background:"#0000"
})

videoOnlyDiv.appendChild(formatDivider);

formatDivider.addEventListener("click",()=>{
Array.from(formatDivider.parentElement.children).forEach((c,i)=>{
if(i == 0) {
c.children[1].style.transform = c.children[1].style.transform === "rotate(180deg)" ? "rotate(0deg)" : "rotate(180deg)";
return;
}
c.style.display = c.style.display === "none" ? "flex" : "none";
})
});

videoOnly.forEach(vid =>{
var formatLi=document.createElement("li");
formatLi.dataset.videoItag=vid.itag;
formatLi.dataset.isWebm=vid.container == "webm";
formatLi.innerHTML=`${downBtn}<span style="margin-left:10px;" >${vid.qualityLabel} | ${vid.container.toUpperCase()} | ${vid.sizeFormatted}</span>`;
videoOnlyDiv.appendChild(formatLi);
});
}

function updateThumbnails(){
var div=ytproDownDiv.querySelector("#thumbViewDiv");
div.innerHTML="<style>.thu{height:80px;border-radius:5px;}.thu img{max-height:97%;max-width:70%;border-radius:10px;border:1px solid silver;}</style>";

var thumbs=info.basic_info.thumbnail;
thumbs.forEach(thumb=>{
div.innerHTML+=`<li class="thu" data-url="${thumb.url}" data-title="Thumbnail ${thumb.height} &#x2715; ${thumb.width} ${safeTitle} YTPRO.jpg" >
<img src="${thumb.url}"><br>
<span style="margin-left:30px;display:flex;align-items:center;justify-content:center;"  >${downBtn}<span style="margin-left:10px;"  >${thumb.height} &#x2715; ${thumb.width}
</span></span></li>`
})

div.addEventListener("click",(e)=>{
var el=e.target.closest("[data-url]");
if(!el) return;
Android.downvid(el.dataset.title,el.dataset.url,"image/jpg");
});
}

function updateCaptions(){
var div=ytproDownDiv.querySelector("#captionsViewDiv");
div.innerHTML=`<style>cp{width:100%;height:auto;padding-bottom:8px;}c{height:45px;width:50px;padding-top:5px;background:${d};border-radius:10px;margin-left:10px;display:block}</style>`;

var captions=info?.captions?.caption_tracks;
if(!captions) return div.innerHTML=`No Captions Found`;

var t=`Captions ${safeTitle} YTPRO`;
captions.forEach(cap=>{
cap.baseUrl = cap.base_url.replace("&fmt=srv3","");
div.innerHTML+=`
<span style="width:100px;text-align:left">${cap?.name?.text}</span> 
<br><br>
<div style="position:absolute;right:10px;display:flex">
<c data-url="${cap.baseUrl}&fmt=sbv" data-title="${t}" data-ext=".txt" >${downBtn} <br>.txt</c>
<c  data-url="${cap.baseUrl}&fmt=srt" data-title="${t}" data-ext=".srt" >${downBtn} <br>.srt</c>
<c  data-url="${cap.baseUrl}" data-title="${t}" data-ext=".xml"  >${downBtn} <br>.xml</c>
<c  data-url="${cap.baseUrl}&fmt=vtt" data-title="${t}" data-ext=".vtt" >${downBtn} <br>.vtt</c>
<c data-url="${cap.baseUrl}&fmt=srv1" data-title="${t}.srv1" >${downBtn} <br>.srv1</c><c  data-url="${cap.baseUrl}&fmt=ttml" data-title="${t}" data-ext=".ttml" >${downBtn} <br>.ttml</c></div>
<br>
<br><br>
<br><br>`;
});

div.addEventListener("click",(e)=>{
var el=e.target.closest("[data-url]");
if(!el) return;
Android.downvid(el.dataset.title+el.dataset.ext,el.dataset.url,"plain/text");
});
}

muxedDiv.addEventListener("click",(e)=>{
var el=e.target.closest("[data-audio-itag]");
if(!el) return;
downloadSABRStream(el.dataset.videoItag,el.dataset.audioItag,el.dataset.isWebm,el.dataset.langId,EnabledTrackTypes.VIDEO_AND_AUDIO);
});

audioOnlyDiv.addEventListener("click",(e)=>{
var el=e.target.closest("[data-audio-itag]");
if(!el) return;
downloadSABRStream(null,el.dataset.audioItag,el.dataset.isWebm,el.dataset.langId,EnabledTrackTypes.AUDIO_ONLY);
});

videoOnlyDiv.addEventListener("click",(e)=>{
var el=e.target.closest("[data-video-itag]");
if(!el) return;
downloadSABRStream(el.dataset.videoItag,null,el.dataset.isWebm,null,EnabledTrackTypes.VIDEO_ONLY);
});

if(info?.basic_info?.is_live || info?.basic_info?.is_live_content){
ytproDownDiv.querySelector("#videoViewDiv").innerHTML="Downloading live streams <br>aren't supported at the moment";
}else{
updateMuxFormats();
updateAudioOnlyFormats();
updateVideoOnlyFormats(); 
}
updateThumbnails();
updateCaptions();

async function extractSabrConfig(playerInfo) {
const url = await player.decipher(playerInfo.streaming_data?.server_abr_streaming_url);
const cfg = playerInfo.player_config
?.media_common_config
?.media_ustreamer_request_config
?.video_playback_ustreamer_config;
return { url, cfg };
}

const { url: serverAbrUrl, cfg: ustreamerConfig } = await extractSabrConfig(info);
if (!serverAbrUrl || !ustreamerConfig) {
window.Android?.showToast?.('Missing SABR config.');
return;
}

const rawUstreamerConfig = typeof ustreamerConfig === 'string' ? ustreamerConfig : JSON.stringify(ustreamerConfig);
const adaptiveFormats = streamingData.adaptive_formats ?? [];
const sabrFormats = adaptiveFormats.map(f => buildSabrFormat(f));

async function downloadSABRStream(videoItag,audioItag,isWebm,langId,enabledTrack){
if(!Android.isWebViewSupported?.() && false){
  Android.showToast("Please Update your WebView.");
  return;
}
Android.showToast("Download Started");

const containerExt = isWebm == "true" ? 'webm' : 'mp4';
const lowestAudio = audioOnly.sort((a, b) => (a.bitrate || 0) - (b.bitrate || 0))[0].itag;
const lowestVideo = adaptiveFormats
.filter(f => f.width)
.sort((a, b) => (a.bitrate || 0) - (b.bitrate || 0))[0].itag;

const trashSabrAudio = sabrFormats.filter(s=> s.itag==lowestAudio)[0];
const trashSabrVideo =sabrFormats.filter(s=> s.itag==lowestVideo)[0];

const targetSabrVideo=sabrFormats.filter(s=> s.itag==videoItag)[0] || trashSabrVideo;
var targetSabrAudio;

if(langId != "undefined"){
targetSabrAudio = sabrFormats.filter(s=> s.itag==audioItag && s.audioTrackId == langId)[0] || trashSabrAudio;
}else{
targetSabrAudio = sabrFormats.filter(s=> s.itag==audioItag)[0] || trashSabrAudio;
}

const sabrStream = new SabrStream({
videoId: videoId,
cpn: info.cpn, 
serverAbrStreamingUrl: serverAbrUrl,
videoPlaybackUstreamerConfig: rawUstreamerConfig,
formats: sabrFormats,
poToken: placeholderPoToken ?? undefined, 
clientInfo: {
clientName: 1, 
clientVersion: yt.session.context.client.clientVersion,
osName: 'Windows',
osVersion: '10.0',
},
durationMs: (info.basic_info.duration ?? 0) * 1000,
fetch: async (input, init = {}) => {
const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
return fetch(url, { ...init, mode: 'cors', credentials: 'include' });
},
});

sabrStream.on('reloadPlayerResponse', async () => {
try {
const freshInfo = await yt.getBasicInfo(videoId, { client: 'WEB' });
const { url: newUrl, cfg: newCfg } = await extractSabrConfig(freshInfo);
if (newUrl) sabrStream.setStreamingURL(newUrl);
if (newCfg) sabrStream.setUstreamerConfig(typeof newCfg === 'string' ? newCfg : JSON.stringify(newCfg));
} catch (e) {}
});

let isTokenApplied = false;
sabrStream.on('streamProtectionStatusUpdate', async (data) => {
if ((data.status === 2 || data.status === 3) && !isTokenApplied) {
isTokenApplied = true;
try {
const fullToken = await fullTokenPromise; 
if (fullToken) sabrStream.poToken = fullToken;
} catch (err) {}
}
});

const { videoStream ,audioStream} = await sabrStream.start({
preferMp4: !isWebm,  
preferH264: !isWebm, 
videoFormat: () => targetSabrVideo, 
audioFormat: () => targetSabrAudio,
enabledTrackTypes:enabledTrack,
});

const durationSec = info.basic_info.duration || 0;

createDownloaderStatus();
createDownloaderIndicator();

var downloaderDiv=document.querySelector("#ytProDownloaderDiv");

function createProgreses(streamName){
var elProgressBar=document.createElement("div");
var elProgress=document.createElement("div");
var elDetails=document.createElement("span");

elDetails.className="ytproDetails";
elProgressBar.className="ytproProgressBar";
elProgress.className="ytproProgress";
elProgressBar.appendChild(elProgress);

elDetails.innerHTML=`${streamName}: <span><span>`
downloaderDiv.appendChild(elDetails)
downloaderDiv.appendChild(elProgressBar);

return {elDetails,elProgress};
}

if(enabledTrack==EnabledTrackTypes.VIDEO_ONLY){
const estVideoBytes = targetSabrVideo.contentLength || (targetSabrVideo.bitrate ? Math.floor((targetSabrVideo.bitrate * durationSec) / 8) : 0);
downloaderDiv.insertAdjacentHTML("beforeend",`<br><br><b>Title: ${safeTitle}</b><br>`)

var fileName=`${safeTitle}_video${new Date().getTime()}.${containerExt}`;
var {elDetails,elProgress} = createProgreses("Video Stream");
await pipeToDisk(videoStream,fileName, estVideoBytes,elDetails,elProgress);

}else if(enabledTrack==EnabledTrackTypes.AUDIO_ONLY){
const estAudioBytes = targetSabrAudio.contentLength || 
(targetSabrAudio.bitrate ? Math.floor((targetSabrAudio.bitrate * durationSec) / 8) : 0);
downloaderDiv.insertAdjacentHTML("beforeend",`<br><br><b>Title: ${safeTitle}</b><br>`)

var {elDetails,elProgress} = createProgreses("Audio Stream");
var fileName=`${safeTitle}_audio${new Date().getTime()}.${containerExt}`;
await pipeToDisk(audioStream,fileName, estAudioBytes,elDetails,elProgress);

}else if(enabledTrack==EnabledTrackTypes.VIDEO_AND_AUDIO){
const estVideoBytes = targetSabrVideo.contentLength || (targetSabrVideo.bitrate ? Math.floor((targetSabrVideo.bitrate * durationSec) / 8) : 0);
const estAudioBytes = targetSabrAudio.contentLength || 
(targetSabrAudio.bitrate ? Math.floor((targetSabrAudio.bitrate * durationSec) / 8) : 0);

downloaderDiv.insertAdjacentHTML("beforeend",`<br><br><b>Title: ${safeTitle}</b><br>`)

var videoEl= createProgreses("Video Stream");
var audioEl= createProgreses("Audio Stream");

var videoFileName=`${safeTitle}_video${new Date().getTime()}.${containerExt}`;
var audioFileName=`${safeTitle}_audio${new Date().getTime()}.${containerExt}`;

const downloadTasks = [];
if (videoStream) {
downloadTasks.push(pipeToDisk(videoStream, videoFileName, estVideoBytes,videoEl.elDetails,videoEl.elProgress));
}
if (audioStream) {
downloadTasks.push(pipeToDisk(audioStream, audioFileName, estAudioBytes,audioEl.elDetails,audioEl.elProgress));
}

await Promise.all(downloadTasks);
window.Android?.showToast?.('Muxing formats...');
window.Android?.muxVideoAudio?.(videoFileName,audioFileName,`${safeTitle}_${new Date().getTime()}.${containerExt}`);
}
}
}

function getDownloadElement() {
const isExisting = (id) => document.getElementById(id);

const ytproDown = isExisting("outerdownytprodiv") || document.createElement("div");
const ytproDownDiv = isExisting("downytprodiv") || document.createElement("div");

ytproDown.id = "outerdownytprodiv";
ytproDownDiv.id = "downytprodiv";

Object.assign(ytproDown.style, {
height: "100%", width: "100%", position: "fixed",
top: "0", left: "0", display: "flex",
justifyContent: "center", background: "rgba(0,0,0,0.4)", zIndex: "9"
});

Object.assign(ytproDownDiv.style, {
height: "65%", width: "85%", overflow: "auto",
background: isD ? "#212121" : "#f1f1f1",
position: "absolute", bottom: "20px", zIndex: "99",
padding: "20px", borderRadius: "25px", textAlign: "center"
});

ytproDown.addEventListener("click", (ev) => {
if (!ytproDownDiv.contains(ev.target)) history.back();
});

const TABS = [
{ label: "Formats",    viewId: "videoViewDiv"    },
{ label: "Thumbnails", viewId: "thumbViewDiv"    },
{ label: "Captions",   viewId: "captionsViewDiv" },
];

const tabStyle = {
height: "100%",
width: "calc((100% - 10px) / 3)",
borderRadius: "25px",
lineHeight: "30px"
};

const tabs = document.createElement("div");
Object.assign(tabs.style, {
height: "30px", width: "95%", display: "flex",
gap: "5px", position: "absolute", top: "10px", left: "2.5%"
});

const views = [];

TABS.forEach(({ label, viewId }) => {
const tab = document.createElement("div");
Object.assign(tab.style, tabStyle);
tab.textContent = label;
tab.dataset.view = `#${viewId}`;
tabs.appendChild(tab);

const view = document.createElement("div");
view.id = viewId;
view.style.paddingTop="40px";
view.style.display = "none";
ytproDownDiv.appendChild(view);
views.push(view);
});

tabs.addEventListener("click", (e) => {
const el = e.target.closest("[data-view]");
if (!el) return;

[...tabs.children].forEach(child => child.style.background = "transparent");
views.forEach(v => v.style.display = "none");

document.querySelector(el.dataset.view).style.display = "block";
el.style.background = d;
});

document.body.appendChild(ytproDown);
ytproDown.appendChild(ytproDownDiv);
ytproDownDiv.prepend(tabs);

tabs.children[0].style.background=d;
document.querySelector("#videoViewDiv").style.display = "block"

return ytproDownDiv;
}

const pendingStreams = {};

window.addEventListener("message", (event) => {
if (typeof event.data === "string" && event.data.startsWith("PORT_FOR:") && event.ports.length > 0) {
const fileName = event.data.substring(9);
if (pendingStreams[fileName]) {
pendingStreams[fileName](event.ports[0]);
delete pendingStreams[fileName];
}
}
});

function createDedicatedPipe(fileName) {
return new Promise((resolve) => {
pendingStreams[fileName] = resolve;
window.Android?.requestBinaryPort?.(fileName);
});
}

function arrayBufferToBase64(buffer) {
    var binary = '';
    var bytes = new Uint8Array(buffer);
    var len = bytes.byteLength;
    for (var i = 0; i < len; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return window.btoa(binary);
}

async function pipeToDisk(stream, fileName, expectedTotalBytesStr, elDetails, elProgress) {
const expectedBytes = parseInt(expectedTotalBytesStr || "0", 10);
const totalMB = expectedBytes > 0 ? (expectedBytes / (1024 * 1024)).toFixed(2) : '?';

const filePort = await createDedicatedPipe(fileName);
if (!filePort) {
console.error(`[YTPRO] Failed to get port for ${fileName}`);
return 0;
}

const reader = stream.getReader();
let total = 0;
let lastLogMB = -1;

try {
const CHUNK_SIZE = 1024 * 512; 

while (true) {
const { done, value } = await reader.read();
if (done) break;

if (value?.length > 0) {
let offset = 0;
while (offset < value.length) {
    const chunkBuffer = value.slice(offset, offset + CHUNK_SIZE).buffer;
    filePort.postMessage(arrayBufferToBase64(chunkBuffer));

    const bytesWritten = chunkBuffer.byteLength;
    offset += bytesWritten;
    total += bytesWritten;

    const currentMBFloor = Math.floor(total / (1024 * 1024));
    if (currentMBFloor > lastLogMB) {
        const downloadedMB = (total / (1024 * 1024)).toFixed(2);
        const percent = expectedBytes > 0 ? Math.round((total / expectedBytes) * 100) : -1;

        elDetails.children[0].innerHTML = ` ${downloadedMB} MB / ${totalMB} MB`;
        elProgress.style.width = percent + "%";
        elProgress.innerHTML = percent + "%";

        window.Android?.onDownloadProgress?.(percent, total);
        lastLogMB = currentMBFloor;
    }
    await new Promise(r => setTimeout(r, 5)); 
}
}
}
} finally {
filePort.postMessage("END");
}

const finalMB = (total / (1024 * 1024)).toFixed(2);
elDetails.children[0].innerHTML = ` ${finalMB} MB / ${totalMB} MB`;
elProgress.style.width = "100%";
elProgress.innerHTML = "100%";

return total;
}

function createDownloaderStatus(){
if(document.querySelector("#ytProDownloaderDiv")) return;
var div=document.createElement("div");
div.id="ytProDownloaderDiv";

Object.assign(div.style,{
height:"50%",
overflow:"auto",
width:"calc(95% - 20px)",
zIndex:999999,
position:"fixed",
padding:"10px",
bottom:"10px",
display:"none",
left:"2.5%",
background:isD ? "#212121" : "#f1f1f1",
borderRadius:"25px",
textAlign:"center",
boxShadow:"1px 1px 2px black"
});

div.innerHTML=`
<style>
.ytproDetails{
display:block;
width:95%;
margin:auto;
margin-top:5px;
text-align:left;
}
.ytproProgressBar{
display:flex;
position:relative;
width:95%;
height:20px;
padding:5px;
margin:auto;
margin-top:5px;
background:${d};
border-radius:20px;
}
.ytproProgress{
display:grid;
place-items:center;
position:relative;
width:0%;
height:20px;
background:${c};
border-radius:20px;
text-align:center;
color:${dc};
line-height:20px;
transition:0.25s;
}
</style>
<br>
<span style="opacity:0.8;"> INFO: Do NOT close YTPRO while we are downloading the files<br>
(SABR streams are limited with 1-2 MBps speed by youtube servers)</span>
<br>
`;
document.body.appendChild(div);
}

function createDownloaderIndicator(){
if(document.querySelector("#ytproDownloadIndicator") ) return;
var div=document.createElement("div");
div.id="ytproDownloadIndicator";

Object.assign(div.style,{
height:"50px",
width:"50px",
zIndex:999999,
position:"fixed",
bottom:"calc(40px)",
right:"20px",
background:isD ? "#212121" : "#f1f1f1",
borderRadius:"50%",
border:`1px solid ${c}`,
display:"grid",
placeItems:"center"
});

div.innerHTML=`<svg xmlns="http://www.w3.org/2000/svg" height="36" width="36" viewBox="0 0 24 24" fill="none"><style> .arrow { animation: drop 1.5s infinite ease-in-out; } @keyframes drop{  0% {transform: translateY(-8px);opacity: 0;}20% {opacity: 1;}80% {opacity: 1;}100% {transform: translateY(2px);opacity: 0;}}</style><path class="arrow" d="M16.59 9H15V4a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5H7.41a1 1 0 0 0-.7 1.7l4.59 4.59a1 1 0 0 0 1.42 0l4.59-4.59a1 1 0 0 0-.72-1.7Z" stroke="${c}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /><rect x="5" y="17.2" width="14" height="1.8" rx="0.9" fill="${c}" /></svg>`;

document.body.appendChild(div)

div.addEventListener("click",()=>{
var el=document.querySelector("#ytProDownloaderDiv");

if(el.style.display=="block"){
el.style.display="none";
div.style.bottom="70px";
}else{
el.style.display="block";
div.style.bottom="calc(50% + 40px)";
}
})
}

/*****YTPRO GENERAL INITS & UTILITIES****** */

if(window.eruda == null && localStorage.getItem("devMode") == "true"){
var script = document.createElement('script'); script.src="//youtube.com/ytpro_cdn/npm/eruda"; document.body.appendChild(script); script.onload=()=>{eruda.init();}
}

if(!window.YTProVerVal){
window.YTProVerVal="3.98";
var ytoldV="";
var isF=false;
var isAp=false;
const originalPause = HTMLMediaElement.prototype.pause;
window.PIPause = false;
window.isPIP=false;
window.pauseAllowed = true;
var sTime=[];
var webUrls=["m.youtube.com","youtube.com","yout.be","accounts.google.com"];
var GeminiAT="";
var GeminiModels = {
    "3.0 Pro": '[1,null,null,null,"9d8ca3786ebdfbea",null,null,0,[4],null,null,1]',
    "3.0 Flash": '[1,null,null,null,"fbb127bbb056c959",null,null,0,[4],null,null,1]',
    "3.0 Flash Thinking": '[1,null,null,null,"5bf011840784117a",null,null,0,[4],null,null,1]',
    "3.0 Pro Plus": '[1,null,null,null,"e6fa609c3fa255c0",null,null,0,[4],null,null,4]',
    "3.0 Flash Plus": '[1,null,null,null,"56fdd199312815e2",null,null,0,[4],null,null,4]',
    "3.0 Flash Thinking Plus": '[1,null,null,null,"e051ce1aa80aa576",null,null,0,[4],null,null,4]',
    "3.0 Pro Advanced": '[1,null,null,null,"e6fa609c3fa255c0",null,null,0,[4],null,null,2]',
    "3.0 Flash Advanced": '[1,null,null,null,"56fdd199312815e2",null,null,0,[4],null,null,2]',
    "3.0 Flash Thinking Advanced": '[1,null,null,null,"e051ce1aa80aa576",null,null,0,[4],null,null,2]'
};

var YTPROCodecs={
video:["AV1","VP8","VP9","H264"],
audio:["Opus","Mp4a"]
}

let touchstartY = 0;
let touchendY = 0;
let initialDistance=null;

var sens=0.005;
var vol=Android.getVolume?.() ?? 0.5;
var brt = (Android.getBrightness?.() ?? 50)/100;

if(localStorage.getItem("saveCInfo") == null  || localStorage.getItem("gesC") == null || localStorage.getItem("gesM") == null || localStorage.getItem("bgplay") == null){
localStorage.setItem("autoSpn","true");
localStorage.setItem("bgplay","true");
localStorage.setItem("gesC","true");
localStorage.setItem("gesM","false");
localStorage.setItem("fzoom","false");
localStorage.setItem("saveCInfo","true");
localStorage.setItem("geminiModel","3.0 Flash");
localStorage.setItem("prompt","Give me details about this YouTube video Id: {videoId} , a detailed summary of timestamps with facts , resources and reviews of the main content");
localStorage.setItem("devMode","false");
localStorage.setItem("block_60fps","false");

YTPROCodecs.video.forEach((x)=>{
localStorage.setItem(x,"true");
});

YTPROCodecs.audio.forEach((x)=>{
localStorage.setItem(x,"true");
});
}

if(localStorage.getItem("fzoom") == "true"){
document.getElementsByName("viewport")[0].setAttribute("content","");
}

if (["2.0 Flash", "2.0 Flash Thinking", "2.5 Flash", "2.5 Pro"].includes(localStorage.getItem('geminiModel'))) {
localStorage.setItem('geminiModel', "3.0 Flash");
}

if(window.location.pathname.indexOf("shorts") > -1){
ytoldV=window.location.pathname;
}
else{
ytoldV=(new URLSearchParams(window.location.search)).get('v') ;
}

var c="#000";
var d="#f2f2f2";
var dc="#fff";
var isD=false;
var dislikes="...";

if(document.cookie.indexOf("f6=40000") > -1){
dc ="#000";c ="#fff";d="rgba(255,255,255,0.1)";
isD=true;
}else{
dc ="#fff";c="#000";d="rgba(0,0,0,0.05)";
isD=false;
}

var downBtn=`<svg xmlns="http://www.w3.org/2000/svg" height="24" width="24" viewBox="0 0 24 24" fill="none">
<path
d="M16.59 9H15V4a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5H7.41a1 1 0 0 0-.7 1.7l4.59 4.59a1 1 0 0 0 1.42 0l4.59-4.59a1 1 0 0 0-.72-1.7Z"
stroke="${c}"
stroke-width="1.8"
stroke-linecap="round"
stroke-linejoin="round"
/>
<rect x="5" y="17.2" width="14" height="1.8" rx="0.9" fill="${c}" />
</svg>`;

function override() {
var videoElem = document.createElement('video');
var origCanPlayType = videoElem.canPlayType.bind(videoElem);
videoElem.__proto__.canPlayType = makeModifiedTypeChecker(origCanPlayType);

var mse = window.MediaSource;
if (mse === undefined) return;
var origIsTypeSupported = mse.isTypeSupported.bind(mse);
mse.isTypeSupported = makeModifiedTypeChecker(origIsTypeSupported);
}

function makeModifiedTypeChecker(origChecker) {
return function (type) {
if (type === undefined) return '';
var disallowed_types = [];
if (localStorage['H264'] === 'false') disallowed_types.push('avc');
if (localStorage['VP8'] === 'false') disallowed_types.push('vp8');
if (localStorage['VP9'] === 'false') disallowed_types.push('vp9', 'vp09');
if (localStorage['AV1'] === 'true') disallowed_types.push('av01', 'av99');
if (localStorage['Opus'] === 'false') disallowed_types.push('opus');
if (localStorage['Mp4a'] === 'false') disallowed_types.push('mp4a');

for (var i = 0; i < disallowed_types.length; i++) {
if (type.indexOf(disallowed_types[i]) !== -1) return '';
}

if (localStorage['block_60fps'] === 'true') {
var match = /framerate=(\d+)/.exec(type);
if (match && match[1] > 30) return '';
}
return origChecker(type);
};
}
override();

function insertAfter(referenceNode, newNode) {
try{
referenceNode.parentNode.insertBefore(newNode, referenceNode.nextSibling);
}catch{}
}

async function waitForElement(selector,vid) {
return new Promise((resolve) => {
const element = document.querySelector(selector);
if(element){
if(vid && element.src != "") return resolve(element);
if(!vid) return resolve(element);
}
const observer = new MutationObserver(() => {
const el = document.querySelector(selector);
if (el){
if(vid && el.src) resolve(el),observer.disconnect();
if(!vid) resolve(el),observer.disconnect();
}
});
observer.observe(document.body, { childList: true, subtree: true });
});
}

var addSettingsTab=()=>{
if(document.getElementById("setDiv") == null){
var setDiv=document.createElement("div");
setDiv.setAttribute("style",`z-index:9999999999;font-size:22px;text-align:center;line-height:35px;pointer-events:auto;`);
setDiv.setAttribute("id","setDiv");
var svg=document.createElement("ytm-pivot-bar-item-renderer");
svg.innerHTML=`<svg fill="${ window.location.href.indexOf("watch") < 0 ? c : "#fff" }" xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 0 24 24" width="24"  id="hSett"><path d="M12.844 1h-1.687a2 2 0 00-1.962 1.616 3 3 0 01-3.92 2.263 2 2 0 00-2.38.891l-.842 1.46a2 2 0 00.417 2.507 3 3 0 010 4.525 2 2 0 00-.417 2.507l.843 1.46a2 2 0 002.38.892 3.001 3.001 0 013.918 2.263A2 2 0 0011.157 23h1.686a2 2 0 001.963-1.615 3.002 3.002 0 013.92-2.263 2 2 0 002.38-.892l.842-1.46a2 2 0 00-.418-2.507 3 3 0 010-4.526 2 2 0 00.418-2.508l-.843-1.46a2 2 0 00-2.38-.891 3 3 0 01-3.919-2.263A2 2 0 0012.844 1Zm-1.767 2.347a6 6 0 00.08-.347h1.687a4.98 4.98 0 002.407 3.37 4.98 4.98 0 004.122.4l.843 1.46A4.98 4.98 0 0018.5 12a4.98 4.98 0 001.716 3.77l-.843 1.46a4.98 4.98 0 00-4.123.4A4.979 4.979 0 0012.843 21h-1.686a4.98 4.98 0 00-2.408-3.371 4.999 4.999 0 00-4.12-.399l-.844-1.46A4.979 4.979 0 005.5 12a4.98 4.98 0 00-1.715-3.77l.842-1.459a4.98 4.98 0 004.123-.399 4.981 4.981 0 002.327-3.025ZM16 12a4 4 0 11-7.999 0 4 4 0 018 0Zm-4 2a2 2 0 100-4 2 2 0 000 4Z"></path></svg>`;
setDiv.appendChild(svg);
var homeLogo = document.getElementsByTagName("ytm-home-logo")[0];
if(homeLogo) insertAfter(homeLogo,setDiv);
if(document.getElementById("hSett") != null){
document.getElementById("hSett").addEventListener("click", function(ev){ window.location.hash="settings"; });
}
}
};

function getDislikesInLocale(num){
var nn=num;
if (num < 1000) nn = num;
else{
const int = Math.floor(Math.log10(num) - 2);
const decimal = int + (int % 3 ? 1 : 0);
const value = Math.floor(num / 10 ** decimal);
nn = value * 10 ** decimal;
}
let userLocales = document.documentElement.lang || navigator.language || "en";
return Intl.NumberFormat(userLocales, { notation: "compact", compactDisplay: "short" }).format(nn);
}

async function skipSponsor(){
var sDiv=document.createElement("div");
sDiv.setAttribute("style",`height:3px;pointer-events:none;width:100%;position:absolute;z-index:99;`)
sDiv.setAttribute("id","sDiv");
var player = document.getElementsByClassName("video-stream")[0];
var dur=player?.duration;
if(!player || isNaN(dur)) return;

for(var x in sTime){
var s1=document.createElement("div");
var s2=sTime[x];
s1.setAttribute("style",`height:3px;width:${(100/dur) * (s2[1]-s2[0])}%;background:#0f8;position:absolute;z-index:9;left:${(100/dur) * s2[0]}%;`)
sDiv.appendChild(s1);
}

var e=await waitForElement("yt-progress-bar",false);
if(document.getElementById("sDiv") == null){
if(document.getElementsByClassName('ytPlayerProgressBarHost')[0] != null){
document.getElementsByClassName('ytPlayerProgressBarHost')[0].appendChild(sDiv);
}else{
try{document.getElementsByClassName('ytProgressBarLineProgressBarLine')[0].appendChild(sDiv);}catch{}
}
}
}

async function fDislikes(url){ 
var Url=new URL(url);
var vID="";
if(Url.pathname.indexOf("shorts") > -1){
vID=Url.pathname.substr(8,Url.pathname.length);
}
else if(Url.pathname.indexOf("watch") > -1){
vID=Url.searchParams.get("v");
}

fetch("https://returnyoutubedislikeapi.com/votes?videoId="+vID)
.then(response => response.json()).then(jsonObject => {
if('dislikes' in jsonObject){
dislikes=getDislikesInLocale(parseInt(jsonObject.dislikes));
}
}).catch(error => {});
}

async function checkSponsors(Url){
if(Url.indexOf("watch") > -1){
sTime=[];
await fetch("https://sponsor.ajay.app/api/skipSegments?videoID="+new URL(Url).searchParams.get("v"))
.then(response => response.json()).then(jsonObject => {
for(var x in jsonObject){
var time=jsonObject[x].segment;
sTime.push(time);
}
}).catch(error => {});

var player = await waitForElement(".video-stream",true);
player.ontimeupdate=()=>{
skipSponsor();
var cur=player.currentTime;
for(var x in sTime){
var s2=sTime[x];
if(Math.floor(cur) == Math.floor(s2[0])){
if(localStorage.getItem("autoSpn") == "true"){
player.currentTime=s2[1];
addSkipper(s2[0]);
}
}
}
};
}
}

function addSkipper(sT){
var sSDiv=document.createElement("div");
sSDiv.setAttribute("style",`
height:50px;${(screen.width > screen.height) ? "width:50%;" : "width:80%;"}overflow:auto;background:rgba(130,130,130,.3);
backdrop-filter:blur(6px);
position:absolute;bottom:40px;
line-height:50px;
left:calc(15% / 2 );padding-left:10px;padding-right:10px;
z-index:99999999999999;text-align:center;border-radius:25px;
color:white;text-align:center;
`);
sSDiv.innerHTML=`<span style="height:30px;line-height:30px;margin-top:10px;display:block;font-family:monospace;font-size:16px;float:left;">Skipped Sponsor</span>
<span style="height:30px;line-height:44px;float:right;padding-right:30px;margin-top:10px;display:block;padding-left:30px;border-left:1px solid white;">
<svg data-action="rewind" xmlns="http://www.w3.org/2000/svg" width="23" height="23" style="margin-top:0px;" fill="currentColor" viewBox="0 0 16 16">
<path fill-rule="evenodd" d="M8 3a5 5 0 1 1-4.546 2.914.5.5 0 0 0-.908-.417A6 6 0 1 0 8 2v1z"/>
<path d="M8 4.466V.534a.25.25 0 0 0-.41-.192L5.23 2.308a.25.25 0 0 0 0 .384l2.36 1.966A.25.25 0 0 0 8 4.466z"/>
</svg>
<svg data-action="close" xmlns="http://www.w3.org/2000/svg" width="20" height="20" style="margin-left:30px;" fill="#f24" class="bi bi-x-circle-fill" viewBox="0 0 16 16">
<path d="M16 8A8 8 0 1 1 0 8a8 8 0 0 1 16 0zM5.354 4.646a.5.5 0 1 0-.708.708L7.293 8l-2.647 2.646a.5.5 0 0 0 .708.708L8 8.707l2.646 2.647a.5.5 0 0 0 .708-.708L8.707 8l2.647-2.646a.5.5 0 0 0-.708-.708L8 7.293 5.354 4.646z"/>
</svg>
</span>`;
document.getElementById("player-control-container")?.appendChild(sSDiv);

sSDiv.addEventListener("click",(e)=>{
  var el=e.target.closest("[data-action]");
  if(!el) return;
  var action=el.dataset.action;
  if(action == "close"){ el.parentElement.parentElement.remove(); }
  else if(action == "rewind"){
    el.parentElement.parentElement.remove();
    document.getElementsByClassName('video-stream')[0].currentTime=sT+1; 
  }
});
setTimeout(()=>{sSDiv.remove();},5000);
}

fDislikes(window.location.href);
checkSponsors(window.location.href);

if((window.location.pathname.indexOf("watch") > -1) || (window.location.pathname.indexOf("shorts") > -1)){
var unV=setInterval(() => {
if (document.getElementsByClassName('video-stream')[0]) {
document.getElementsByClassName('video-stream')[0].muted=false;
if(!document.getElementsByClassName('video-stream')[0].muted) clearInterval(unV);
}
}, 5);
}

function sty(e,v){
var s={
display:"flex",
alignItems:"center",
justifyContent:"center",
fontWeight:"550",
height:"65%",
minWidth:"80px",
width:"auto",
borderRadius:"20px",
background:d,
fontSize:"12px",
marginRight:"5px",
textAlign:"center",
};
for(x in s){ e.style[x]=s[x]; }
}

function getGeminiModels(){
var t="";
for(var x in GeminiModels){
t+=`<br>
<button data-action="saveModel" data-value="${x}" ${(x == localStorage.getItem('geminiModel')) ? `style="background:${c};color:${dc};"` : "" } >${x}
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="${c}"  viewBox="0 0 16 16">
<path fill-rule="evenodd" d="M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708"/>
</svg>
</button>`;
}
return t;
}

function getYTPROCodecs(){
var t=`<p style="text-align:center;font-size:14px;">This feature is experimental. Tap on the buttons below to switch them.</p><br> <vc  style="font-size:14px;">Video Codecs</vc><br>`;

for(var y in YTPROCodecs.video){
var x=YTPROCodecs.video[y];
t+=`<button data-action="setRemoveCodec" data-value="${x}" ${("true" == localStorage.getItem(x)) ? `style="background:${c};color:${dc};"` : "" } >${x}
<svg  ${("true" != localStorage.getItem(x)) ? `style="display:none"` : "" } xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="${dc}"  viewBox="0 0 16 16">
<path d="M10.97 4.97a.75.75 0 0 1 1.07 1.05l-3.99 4.99a.75.75 0 0 1-1.08.02L4.324 8.384a.75.75 0 1 1 1.06-1.06l2.094 2.093 3.473-4.425z"/>
</svg>
</button>`;
}

t+=`<br><br><vc  style="font-size:14px">Audio Codecs</vc><br>`
for(var y in YTPROCodecs.audio){
var x=YTPROCodecs.audio[y];
t+=`<button data-action="setRemoveCodec" data-value="${x}" ${("true" == localStorage.getItem(x)) ? `style="background:${c};color:${dc};"` : "" } >${x}
<svg ${("true" != localStorage.getItem(x)) ? `style="display:none"` : "" } xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="${dc}"  viewBox="0 0 16 16">
<path d="M10.97 4.97a.75.75 0 0 1 1.07 1.05l-3.99 4.99a.75.75 0 0 1-1.08.02L4.324 8.384a.75.75 0 1 1 1.06-1.06l2.094 2.093 3.473-4.425z"/>
</svg>
</button>`;
}

t+=`<br><br><div>Block 60FPS <span data-action="block_60fps" style="${sttCnf(0,0,"block_60fps")}" ><b style="${sttCnf(0,1,"block_60fps")}" ></b></span></div> `;
t+=`<br><br><button data-action="done" style="margin-top:10px;width:25%;float:right;text-align:center;background:${c};color:${dc};" >Done</button>`;
return t;
}

function setRemoveCodec(x,y){
if(localStorage[x] == "true"){
localStorage.setItem(x,"false");
y.style.background=isD ? "rgba(255,255,255,.1)" : "rgba(0,0,0,.1)";
y.style.color=c;
y.children[0].style.display="none";
}else{
localStorage.setItem(x,"true");
y.style.background=c;
y.style.color=dc;
y.children[0].style.display="block";
}
}

async function ytproSettings(){
var ytpSet=document.createElement("div");
var ytpSetI=document.createElement("div");
ytpSet.setAttribute("id","settingsprodiv");
ytpSetI.setAttribute("id","ssprodivI");
ytpSet.setAttribute("style",`height:100%;width:100%;position:fixed;top:0;left:0;display:flex;justify-content:center;background:rgba(0,0,0,0.7);z-index:9999;`);
ytpSet.addEventListener("click", function(ev){ if(!(ev.target == ytpSetI || ytpSetI.contains(ev.target))){ history.back(); } });

ytpSetI.setAttribute("style",`height:65%;width:calc(95% - 20px);overflow:auto;background:${isD ? "#212121" : "#f1f1f1"};position:fixed;bottom:20px;z-index:99999999999999;padding:10px;text-align:center;border-radius:25px;color:${c};`);

ytpSetI.innerHTML=`<style>
@import url('https://fonts.googleapis.com/css2?family=Delius&display=swap');
#settingsprodiv a{text-decoration:underline;} #settingsprodiv li{list-style:none; display:flex;align-items:center;justify-content:center;color:#fff;border-radius:25px;padding:10px;background:#000;margin:5px;}
#ssprodivI div{
height:10px;
width:calc(100% - 20px);
padding:10px;
font-size:1.45rem;
text-align:left;
display:flex;
align-items:center;
position:relative;
margin-top:3px;
}
#ssprodivI div span{
display:block;
height:23px;
width:40px;
border-radius:40px;
right:10px;
position:absolute;
background:#151515;
}
#ssprodivI div span b{
display:block;
height:19px;
width:19px;
position:absolute;
right:2px;
top:2px;
border-radius:50px;
background:#fff;
}
#ssprodivI div input::placeholder{color:${ isD ? "white" : "#000"};}
#ssprodivI div input,#ssprodivI div button{
height:35px;
background:${isD ? "rgba(255,255,255,.1)" : "rgba(0,0,0,.1)"};
width:100%;
border:0;
border-radius:20px;
padding:10px;
font-size:1.25rem;
}
#ssprodivI button{
background:transparent;
font-size:1.45rem;
width:calc(100% - 20px);
height:40px;
color:${isD ? "#ccc" : "#444"};
margin-top:3px;
text-align:left;
}
#ssprodivI button svg{
float:right;
}
#ssprodivI .credit{
display:none;
}
#ssprodivI .geminiModels,#ssprodivI .disableCodecs,#ssprodivI .geminiPrompt{
height:auto;
min-height:100px;
padding-bottom:12px;
background:${isD ? "#212121" : "#f1f1f1"};
position:fixed;
display:block;
width:calc(95% - 20px);
left:calc(2.5% + 0px);
bottom:20px;
z-index:999999;
box-shadow:0px 0px 5px black;
border-radius:25px;
display:none;
}
#ssprodivI .geminiPrompt textarea{
height:300px;
width:95%;
border-radius:20px;
padding:15px;
background:${d};
}
#ssprodivI .disableCodecs button{
width:48%;
margin-right:2%;
color:${c};
}
</style>
<br><b style='font-size:18px' >YT PRO Settings</b>
<span style="font-size:10px">v${window.YTProVerVal}</span>
<br><br>
<div><input type="url" placeholder="Enter Youtube URL" id="ytproUrlInput" ></div>
<br>
<button data-action="hearts">Liked Videos
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="${isD ? "#ccc" : "#444"}" viewBox="0 0 16 16">
<path fill-rule="evenodd" d="M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708"/>
</svg>
</button>
<br>
<button data-action="checkUpdate">Check for Updates
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="${isD ? "#ccc" : "#444"}"  viewBox="0 0 16 16">
<path fill-rule="evenodd" d="M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708"/>
</svg>
</button>
<br>
<div>Autoskip Sponsors <span data-action="sttCnf" data-value="autoSpn" style="${sttCnf(0,0,"autoSpn")}" ><b style="${sttCnf(0,1,"autoSpn")}"></b></span></div>
<br>
<div>Gesture Controls <span data-action="sttCnf" data-value="gesC" style="${sttCnf(0,0,"gesC")}" ><b style="${sttCnf(0,1,"gesC")}"></b></span></div>
<br>
<div>Miniplayer Gesture <span data-action="sttCnf" data-value="gesM" style="${sttCnf(0,0,"gesM")}" ><b style="${sttCnf(0,1,"gesM")}"></b></span></div>
<br>
<div>Force Zoom <span data-action="sttCnf" data-value="fzoom"  style="${sttCnf(0,0,"fzoom")}" ><b style="${sttCnf(0,1,"fzoom")}" ></b></span></div> 
<br>
<div>Background Play <span data-action="sttCnf" data-value="bgplay" style="${sttCnf(0,0,"bgplay")}" ><b style="${sttCnf(0,1,"bgplay")}" ></b></span></div> 
<br>
<div>Hide Shorts <span data-action="sttCnf" data-value="shorts" style="${sttCnf(0,0,"shorts")}" ><b style="${sttCnf(0,1,"shorts")}" ></b></span></div> 
<br>
<div>Use single Gemini chat <span data-action="sttCnf" data-value="saveCInfo" style="${sttCnf(0,0,"saveCInfo")}" ><b style="${sttCnf(0,1,"saveCInfo")}"></b></span></div>
<br>
<button data-action="geminiModels">Select Gemini Model
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="${isD ? "#ccc" : "#444"}" viewBox="0 0 16 16">
<path fill-rule="evenodd" d="M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708"/>
</svg>
</button>
<br>
<button data-action="geminiPrompt">Edit Gemini Prompt
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="${isD ? "#ccc" : "#444"}" viewBox="0 0 16 16">
<path fill-rule="evenodd" d="M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708"/>
</svg>
</button>
<br>
<button data-action="disableCodecs">Disable Codecs
<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="${isD ? "#ccc" : "#444"}" viewBox="0 0 16 16">
<path fill-rule="evenodd" d="M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708"/>
</svg>
</button>
<br>
<div>Developer Mode <span data-action="sttCnf" data-value="devMode" style="${sttCnf(0,0,"devMode")}" ><b style="${sttCnf(0,1,"devMode")}"></b></span></div>
<br><br>

<div class="geminiModels"></div>
<div class="geminiPrompt" style="text-align:center;">
<textarea>${localStorage.getItem("prompt")}</textarea>
<button data-action="savePrompt" style="margin-top:10px;width:25%;float:right;text-align:center;background:${c};color:${dc};" >Save</button>
<br><br>
</div>
<div class="disableCodecs"></div>
`;

document.body.appendChild(ytpSet);
ytpSet.appendChild(ytpSetI);

document.getElementById("ytproUrlInput")?.addEventListener("keyup",searchUrl);

var actionsList={
  hearts:()=>{ window.location.hash='#hearts'; },
  checkUpdate:()=>{ Android.showToast("Your app is up to date"); },
  sttCnf:(button,action)=>{ sttCnf(button,action); },
  geminiModels:()=>{
    document.getElementsByClassName('geminiModels')[0].style.display='block';
    document.getElementsByClassName('geminiModels')[0].innerHTML=getGeminiModels();
  },
  geminiPrompt:()=>{ document.getElementsByClassName('geminiPrompt')[0].style.display='block'; },
  disableCodecs:()=>{
    document.getElementsByClassName('disableCodecs')[0].style.display='block';
    document.getElementsByClassName('disableCodecs')[0].innerHTML=getYTPROCodecs();
  },
  savePrompt:(el)=>{ localStorage.setItem('prompt',el.previousElementSibling.value); el.parentElement.style.display='none'; },
  done:(el)=>{ el.parentElement.style.display='none'; },
  setRemoveCodec:(el,value)=>{ setRemoveCodec(value,el); },
  block_60fps:(el)=>{ sttCnf(el,"block_60fps"); },
  saveModel:(el,value)=>{
    localStorage.removeItem('geminiChatInfo');
    localStorage.setItem('geminiModel',value);
    el.parentElement.style.display='none';
  }
}

ytpSetI.querySelectorAll("[data-action]").forEach(button =>{
  button.addEventListener("click",()=>{
    if(button.dataset.action== "sttCnf"){
      actionsList[button.dataset.action](button,button.dataset.value);
    }else{
      actionsList[button.dataset.action](button);
    }
  })
});

ytpSetI.querySelector(".disableCodecs").addEventListener("click",(e)=>{
  var el = e.target.closest("[data-action]");
  if(!el) return;
  actionsList[el.dataset.action](el,el.dataset.value);
});

ytpSetI.querySelector(".geminiModels").addEventListener("click",(e)=>{
  var el = e.target.closest("[data-action]");
  if(!el) return;
  actionsList[el.dataset.action](el,el.dataset.value);
});
}

function searchUrl(e){
if(e.keyCode === 13 || e === "Enter"){
var url=e.target.value;
const regex = /(?:https?:\/\/)?(?:www\.|m\.)?(?:youtu\.be\/|youtube(?:-nocookie)?\.com\/(?:(?:watch)?\?(?:.*&)?v(?:i)?=|(?:embed|v|vi|shorts|live)\/))([a-zA-Z0-9_-]{11})/;
const match = url.match(regex);
var id=match ? match[1] : null;
if(id){ return navigateInternalYtMweb(id); }

var a=document.createElement("a");
a.href=url;
document.body.appendChild(a);
try{document.getElementById("settingsprodiv").remove();}catch{}
a.click();
}
}

function sttCnf(x,z,y) {
var s = isD ? ["#000","#717171","#fff"] : ["#fff","#909090","#151515"];
if(typeof y == "string"){
if(localStorage.getItem(y) != "true"){
return z == 1 ? `background:${s[0]};left:2px;` : `background:${s[1]};`;
}else{
return z == 1 ? `background:${s[0]};` : `background:${s[2]};`;
}
}
if(localStorage.getItem(z) == "true"){
localStorage.setItem(z,"false");
x.style.background=s[1];
x.children[0].style.left="2px";
x.children[0].style.background=s[0];
}else{
localStorage.setItem(z,"true");
x.style.background=s[2];
x.children[0].style.left="auto";
x.children[0].style.right="2px";
x.children[0].style.background=s[0];
}

if(localStorage.getItem("fzoom") == "false"){
document.getElementsByName("viewport")[0]?.setAttribute("content","width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=no,");
}else{
document.getElementsByName("viewport")[0]?.setAttribute("content","");
}

if(localStorage.getItem("bgplay") == "true"){ Android.setBgPlay?.(true); }
else { Android.setBgPlay?.(false); }

if(localStorage.getItem("gesC") != "true"){
try{
document.getElementById("brtS")?.remove();
document.getElementById("volS")?.remove();
}catch{}
}
}

function formatFileSize(bytes){
var s=parseInt(bytes);
let ss = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
for (var i=0; s > 1024; i++) s /= 1024;
return `${s.toFixed(1)} ${ss[i]}`;
}

async function ytproDownVid(){
window.ytproSabrDownload();
}

function downCap(x,t){ Android.downvid(t,x,"plain/text"); }

var stopProp = false;
var zoomIn=false;
var scale=1;

function checkDirection(e) {
if ((touchendY > touchstartY) && (touchendY - touchstartY > 20)) { minimize(true); }
else if ((touchendY < touchstartY) && (touchstartY - touchendY > 20)) { minimize(false); }
}

function getDistance(touches) {
const [a, b] = touches;
return Math.hypot(b.pageX - a.pageX, b.pageY - a.pageY);
}

document.body.addEventListener('touchstart', e => {
touchstartY = e.changedTouches[0].screenY;
if (e.touches.length === 2) { initialDistance = getDistance(e.touches); }
}, { capture: true });

document.body.addEventListener('touchmove', (e) => {
if(stopProp) e.stopPropagation();
if (e.touches.length === 2 && initialDistance !== null) {
const currentDistance = getDistance(e.touches);
const z = currentDistance / initialDistance;
stopProp=true;

if((e.target.className?.toString().includes("video-stream") || e.target.className?.toString().includes("player-controls-background")) && document.fullscreenElement){
if (z > 1.05) {
var Vv=document.getElementsByClassName('video-stream')[0];
zoomIn=true;
scale=Math.max((screen.height / Vv.offsetHeight) , (screen.width / Vv.offsetWidth)); 
addMaxButton();
} else if (z < 0.95) {
zoomIn=false;
scale=1;
addMaxButton();
}
}
}
},{capture:true});

document.body.addEventListener('touchend', e => {
touchendY = e.changedTouches[0].screenY;

if((e.target.className?.toString().includes("video-stream") || e.target.className?.toString().includes("player-controls-background")) && !document.fullscreenElement && localStorage.getItem("gesM") == "true"){
checkDirection();
}

if (e.touches.length < 2) {
initialDistance = null;
setTimeout(()=>{ stopProp=false; },500)
}
}, { capture: true });

navigation.addEventListener("navigate", e => {
if(e.destination.url.indexOf("watch") > -1 || e.destination.url.indexOf("shorts") > -1){
  dislikes="...";
  fDislikes(e.destination.url);
  checkSponsors(e.destination.url);
}
});

function minimize(yes){
const createIframe=()=>{
var iframe=document.createElement("iframe");
iframe.setAttribute("id",`miniIframe`);
iframe.setAttribute("style",`height:99.999%;width:100%;background:${c};top:0px;line-height:50px;position:fixed;left:0;z-index:999;border:0;`);

iframe.src="https://m.youtube.com/";
document.body.appendChild(iframe);

var iwindow = iframe.contentWindow || iframe.contentDocument.defaultView;
var doc = iwindow.document;

if (doc.readyState  == 'complete' ) {
if (iwindow.trustedTypes && iwindow.trustedTypes.createPolicy && !iwindow.trustedTypes.defaultPolicy) {
iwindow.trustedTypes.createPolicy('default', {createHTML: (string) => string,createScriptURL: string => string, createScript: string => string, });
}
}

iwindow.navigation.addEventListener("navigate", e => {
if(e.destination.url.indexOf("youtube.com") > -1){
if(e.destination.url.indexOf("/watch") > -1 || e.destination.url.indexOf("/shorts") > -1){
window.location.href=e.destination.url;
}
}else{
window.location.href=e.destination.url;
}
});
return iframe;
}

var iframe = document.getElementById("miniIframe") || createIframe();
var player=document.getElementById("player-container-id");

if(yes){
iframe.style.display="block";
player.setAttribute("ogTop",getComputedStyle(player).top)
player.style.transform="scale(0.65)";
player.style.top=(window.screen.height-(player.getBoundingClientRect().height*2.5))+"px";
player.style.zIndex="9999";
}else{
iframe.style.display="none";
player.style.transform="scale(1)";
player.style.top=player.getAttribute("ogTop");
player.style.zIndex="normal";
player.removeAttribute("ogTop");
}
}

function callbackSNlM0e(){ return new Promise(resolve => { callbackSNlM0e.resolve = resolve; }); }
function callbackGeminiClient(){ return new Promise(resolve => { callbackGeminiClient.resolve = resolve; }); }

function handleGeminiResponse(res){
const getBody=(x)=>{
for(var i in x){
try{
var json=JSON.parse(x[i][2]);
if(json[4]?.[0]?.[0].indexOf("rc_") > -1) return json;
}catch(e){}}
}

const modifyTimestamps=(x)=>{
var html=x;
var hrefs=html.match(/href="([^"]*)"/g) || [];
var urls= [...hrefs].map(url => url.replace(/href="|"/g, ""));
hrefs.forEach((x,i)=>{
var time=new URL(urls[i]).searchParams.get("t");
if(time != null){
html=html.replace(x,`href="javascript:void(0);" onclick="document.getElementsByClassName('video-stream')[0].currentTime='${time}'"`)
}else if(urls[i].indexOf("youtube.com") < 0 && urls[i].indexOf("youtu.be") < 0){
html=html.replace(x,`href="javascript:void(0);" onclick="try{document.getElementsByClassName('video-stream')[0].pause();}catch{}Android.oplink('${urls[i]}')"`)
}
})
return html;
}

var response=res.stream;
if (response == undefined) return document.getElementById("GeminiResponse").innerHTML=`<center style="margin-top:15px" > An error Occurred while connecting to Gemini`;

var lines=response.split("\n");
var responseJson=JSON.parse(lines[2])
var body=getBody(responseJson) || [];

var chat=[];
chat.push(body?.[1]?.[0]);
chat.push(body?.[1]?.[1]);
chat.push(body?.[4]?.[0]?.[0]);

localStorage.setItem("geminiChatInfo",chat.toString());
body=body?.[4]?.[0];

var text=body?.[1]?.[0] || "";
text=text.replace(/http:\/\/googleusercontent\.com\/\S+/g,'');
var thoughts = body?.[37]?.[0]?.[0] || null;

for(var i in body?.[12]?.[1]){
var img=body?.[12]?.[1]?.[i]
text+=`<center><img alt="${img[0][4]}" src="${img[0][0][0]}"></center>`;
}

let converter = new showdown.Converter();
converter.setFlavor('github');
let html = modifyTimestamps(converter.makeHtml(text));

let thoughtsHtml=(thoughts != null) ? `<button onclick="(this.nextElementSibling.style.height=='auto') ? (this.children[0].style.transform='rotate(-90deg)',this.nextElementSibling.style.height='0') : (this.children[0].style.transform='rotate(90deg)',this.nextElementSibling.style.height='auto');" class="think" >Show Thinking 
<svg xmlns="http://www.w3.org/2000/svg" style="transform:rotate(-90deg);margin-left:10px" width="16" height="16" fill="${isD ? "#ccc" : "#444"}" viewBox="0 0 16 16">
<path fill-rule="evenodd" d="M4.646 1.646a.5.5 0 0 1 .708 0l6 6a.5.5 0 0 1 0 .708l-6 6a.5.5 0 0 1-.708-.708L10.293 8 4.646 2.354a.5.5 0 0 1 0-.708"/>
</svg></button>
<div class="geminiThoughts">
<br>
${converter.makeHtml(thoughts)}
</div><br>` : "";

document.getElementById("GeminiResponse").innerHTML=`<a href="https://gemini.google.com/chat/${chat[0].replace("c_","")}" >Go to the chat</a><br><br>
${thoughtsHtml}
<div class="geminiAnswer">${html}</div>`;
}

async function geminiInfo(){
if(document.getElementById("GeminiResponse") == null){
var GeminiRes=document.createElement("div");
GeminiRes.setAttribute("style",`min-height:80px;max-height:400px;display:block;height:auto;overflow:scroll;font-weight:400;width:calc(92% - 20px);font-size:14px;padding:10px;position:relative;margin:auto;background:${d};border-radius:15px;margin-bottom:8px;`);
GeminiRes.setAttribute("id","GeminiResponse");
insertAfter(document.getElementById('ytproMainDivE'),GeminiRes);
}else{
var GeminiRes=document.getElementById("GeminiResponse");
}

document.getElementById("GeminiResponse").innerHTML=`<div class="geminiLoader"></div>`;
var cookies=Android.getAllCookies(window.location.href);

if(cookies.indexOf("__Secure-1PSID=") < 0){
GeminiRes.innerHTML=`<center style="margin-top:15px"><span>Sign in to use Gemini<span><br><br><a href="https://accounts.google.com/ServiceLogin?service=youtube" ><button style="background:${c};color:${isD ? "#000" : "#fff"};font-weight:500;height:35px;width:90px;border-radius:25px;text-align:center;line-height:35px;">Sign In</button></a><br><br></center>`;
return;
}

cookies=cookies.split(";");
var secured="";
cookies.forEach((x)=>{
if(x.indexOf("__Secure-1PSID=") > -1 || x.indexOf("__Secure-1PSIDTS=") > -1) secured+=x+";";
})

var endpoint="https://gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate";
var headers=JSON.stringify({
"accept": "*/*",
"accept-language": "en",
"content-type":"application/x-www-form-urlencoded;charset=UTF-8",
"x-goog-ext-525001261-jspb": GeminiModels[localStorage.getItem('geminiModel')], 
"x-same-domain": "1",
"cookie": secured,
"Referer": "https://gemini.google.com/",
"Referrer-Policy": "origin"
});

if(GeminiAT == ""){
Android.getSNlM0e(secured);
GeminiAT=await callbackSNlM0e();
var sd = document.createElement('script');
sd.src="//youtube.com/ytpro_cdn/npm/showdown/dist/showdown.min.js";
document.body.appendChild(sd);
}

var prompt=localStorage.getItem('prompt').replaceAll("{url}",window.location.href).replaceAll("{videoId}",new URL(window.location.href).searchParams.get("v")).replaceAll("{title}",document.getElementsByClassName('slim-video-metadata-header')[0]?.textContent.replaceAll("|","") || "YouTube Video"); 

var chat = null;
if(localStorage.getItem("saveCInfo") == "true" && localStorage.getItem("geminiChatInfo") != null){
chat = localStorage.getItem("geminiChatInfo").split(",");
}

const formData = new URLSearchParams();
formData.append("f.req", JSON.stringify([null, JSON.stringify([[prompt],null,chat])]));
formData.append("at", GeminiAT);

Android.GeminiClient(endpoint,headers,formData.toString());
var response=await callbackGeminiClient();
handleGeminiResponse(response);
}

var volSvg=`<svg xmlns="http://www.w3.org/2000/svg" height="16" viewBox="0 0 24 24" width="16" focusable="false" aria-hidden="true" style="pointer-events: none;filter:drop-shadow(0px 0px 1px black);position:absolute;top:10%"><path fill="#fff" d="M11.485 2.143 3.913 6.687A6 6 0 001 11.832v.338a6 6 0 002.913 5.144l7.572 4.543A1 1 0 0013 21V3a1.001 1.001 0 00-1.515-.857Zm6.88 2.079a1 1 0 00-.001 1.414 9 9 0 010 12.728 1 1 0 001.414 1.414 11 11 0 000-15.556 1 1 0 00-1.413 0Zm-2.83 2.828a1 1 0 000 1.415 5 5 0 010 7.07 1 1 0 001.415 1.415 6.999 6.999 0 000-9.9 1 1 0 00-1.415 0Z"></path></svg>`;
var brtSvg=`<svg xmlns="http://www.w3.org/2000/svg" enable-background="new 0 0 24 24" height="16" viewBox="0 0 24 24" width="16" style="filter:drop-shadow(0px 0px 1px black);position:absolute;top:10%;"><rect fill="none" height="24" width="24"/><path fill="#fff" d="M12,7c-2.76,0-5,2.24-5,5s2.24,5,5,5s5-2.24,5-5S14.76,7,12,7L12,7z M2,13l2,0c0.55,0,1-0.45,1-1s-0.45-1-1-1l-2,0 c-0.55,0-1,0.45-1,1S1.45,13,2,13z M20,13l2,0c0.55,0,1-0.45,1-1s-0.45-1-1-1l-2,0c-0.55,0-1,0.45-1,1S19.45,13,20,13z M11,2v2 c0,0.55,0.45,1,1,1s1-0.45,1-1V2c0-0.55-0.45-1-1-1S11,1.45,11,2z M11,20v2c0,0.55,0.45,1,1,1s1-0.45,1-1v-2c0-0.55-0.45-1-1-1 C11.45,19,11,19.45,11,20z M5.99,4.58c-0.39-0.39-1.03-0.39-1.41,0c-0.39,0.39-0.39,1.03,0,1.41l1.06,1.06 c0.39,0.39,1.03,0.39,1.41,0s0.39-1.03,0-1.41L5.99,4.58z M18.36,16.95c-0.39-0.39-1.03-0.39-1.41,0c-0.39,0.39-0.39,1.03,0,1.41 l1.06,1.06c0.39,0.39,1.03,0.39,1.41,0c0.39-0.39,0.39-1.03,0-1.41L18.36,16.95z M19.42,5.99c0.39-0.39,0.39-1.03,0-1.41 c-0.39-0.39-1.03-0.39-1.41,0l-1.06,1.06c-0.39,0.39-0.39,1.03,0,1.41s1.03,0.39,1.41,0L19.42,5.99z M7.05,18.36 c0.39-0.39,0.39-1.03,0-1.41c-0.39-0.39-1.03-0.39-1.41,0l-1.06,1.06c-0.39,0.39-0.39,1.03,0,1.41s1.03,0.39,1.41,0L7.05,18.36z"/></svg>`;

async function pkc(){
if(window.location.href.indexOf("youtube.com/watch") > -1){
try{
var elm=document.getElementsByTagName("dislike-button-view-model")[0]?.children[0]; 
if (elm) {
elm.children[0].children[0].style.width="auto";
elm.children[0].children[0].style.paddingRight="15px";

if(!document.getElementById("diskl")){
  var diskl=document.createElement("span");
  diskl.setAttribute("id","diskl");
  diskl.innerHTML=dislikes;
  diskl.style.marginLeft="5px";
  insertAfter(elm.getElementsByClassName("yt-spec-button-shape-next__icon")[0],diskl);
}else{
document.getElementById("diskl").innerHTML=dislikes;
}
}
}catch(e){}

try{
if(localStorage.getItem("gesC") == "true"){
var v= document.getElementById("player-container-id");
var rect=v?.getBoundingClientRect();

if (rect) {
var elStyle={
height:"70%",
width:rect.width*0.14+"px",
display:"flex",
"flex-direction":"column",
"align-items":"center",
"justify-content":"center",
position:"absolute",
top:"16%", 
right:"0px",
opacity:"0",
};  

var el=document.createElement("div");
var elB=document.createElement("div");
elB.setAttribute("id","brtS");
el.setAttribute("id","volS");

Object.assign(el.style,elStyle);
Object.assign(elB.style,elStyle);
elB.style.left="0";

el.innerHTML=`${volSvg}<div style="position:absolute;bottom:5%;left:calc(50% - 1.5px);background:rgba(255,255,255,0.5); height:70%;width:3px;border-radius:3px;color:red;box-shadow:0px 0px 2px black;pointer-events:none" ><div style="background:white;width:100%;height:${vol * 100}%;border-radius:3px;position:absolute;bottom:0;box-shadow:0px 0px 2px black;" id="volIS"></div></div>`;
elB.innerHTML=`${brtSvg}<div style="position:absolute;bottom:5%;left:calc(50% - 1.5px);background:rgba(255,255,255,0.5); height:70%;width:3px;border-radius:3px;color:red;box-shadow:0px 0px 2px black;pointer-events:none" ><div style="background:white;width:100%;height:${brt * 100}%;border-radius:3px;position:absolute;bottom:0;box-shadow:0px 0px 1px black;" id="brtIS"></div></div>`;

if(!document.getElementById("brtS")){
document.getElementById("player-container-id")?.appendChild(elB);

elB.addEventListener("touchmove",(e)=>{
e.preventDefault();
elB.style.opacity="1";
var diff= touchstartY - e.touches[0].pageY;
if(diff > 0){ brt +=sens; }else{ brt -=sens; }
brt = Math.max(0, Math.min(1, brt));
touchstartY=e.touches[0].pageY;
Android.setBrightness?.(brt);
var bIS = document.getElementById("brtIS");
if(bIS) bIS.style.height=brt*100+"%";
},{ passive: false })

elB.addEventListener("touchend",(e)=>{ elB.style.opacity="0"; },{ passive: false });
}

if(!document.getElementById("volS")){
document.getElementById("player-container-id")?.appendChild(el);

el.addEventListener("touchmove",(e)=>{
e.preventDefault();
el.style.opacity="1";
var diff= touchstartY - e.touches[0].pageY;
if(diff > 0){ vol +=sens; }else{ vol -=sens; }
vol = Math.max(0, Math.min(1, vol));
touchstartY=e.touches[0].pageY;
Android.setVolume?.(vol);
var vIS = document.getElementById("volIS");
if(vIS) vIS.style.height=vol * 100 +"%";
},{ passive: false })

el.addEventListener("touchend",(e)=>{ el.style.opacity="0"; },{ passive: false });
}
}
}
}catch(e){}

if(document.getElementById("ytproMainDivE") == null){
var ytproMainDivA=document.createElement("div");
ytproMainDivA.setAttribute("id","ytproMainDivE");
ytproMainDivA.setAttribute("style",`height:50px;width:100%;display:block;overflow:auto;`);

var actionBar = document.getElementsByClassName('slim-video-action-bar-actions')[0];
if (actionBar) {
insertAfter(actionBar, ytproMainDivA);

var ytproMainDiv=document.createElement("div");
ytproMainDiv.setAttribute("style",`height:50px;width:100%;display:flex;overflow:auto;align-items:center;justify-content:center;padding-left:20px;padding-right:10px;`);
ytproMainDivA.appendChild(ytproMainDiv);

var ytproGemini=document.createElement("div");
sty(ytproGemini);
ytproGemini.style.width="115px";
ytproGemini.style.height="calc(65% - 4.5px)";
ytproGemini.style.position="relative";
ytproGemini.style.background=`linear-gradient(${isD ? "#272727,#272727" : "#f2f2f2,#f2f2f2"}) padding-box , linear-gradient(16deg ,#4285f4 ,#9b72cb ,#d96570) border-box`;
ytproGemini.style.border="2px solid transparent";
ytproGemini.innerHTML=`
<svg style="height:16px;width:16px" fill="none" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"><path d="M16 8.016A8.522 8.522 0 008.016 16h-.032A8.521 8.521 0 000 8.016v-.032A8.521 8.521 0 007.984 0h.032A8.522 8.522 0 0016 7.984v.032z" fill="url(#prefix__paint0_radial_980_20147)"/><defs><radialGradient id="prefix__paint0_radial_980_20147" cx="0" cy="0" r="1" gradientUnits="userSpaceOnUse" gradientTransform="matrix(16.1326 5.4553 -43.70045 129.2322 1.588 6.503)"><stop offset=".067" stop-color="#9168C0"/><stop offset=".343" stop-color="#5684D1"/><stop offset=".672" stop-color="#1BA1E3"/></radialGradient></defs></svg>
<span style="margin-left:4px">Gemini</span>
<style type="text/css">
#GeminiResponse img{ max-width:90%; height:auto; border-radius:10px; margin-top:5px; }
#GeminiResponse a{ color:rgb(62,166,255); }
.geminiLoader,.geminiLoader:before,.geminiLoader:after{
content:'';
height:10px;
width:70%;
position:absolute;
top:15px;
border-radius:5px;
left:10px;
background:${d};
animation: geminiLoad 1s linear infinite alternate;
}
.geminiLoader:before{ top:27px; left:0; }
.geminiLoader:after{ top:54px; left:0; width:90%; }
@keyframes geminiLoad{ 0% { opacity:1; } 100% { opacity:.4; } }
.geminiThoughts{ height:0; width:calc(100% - 30px); transition:5s; overflow:hidden; padding-left:5px; font-style:italic; border-left:3px solid ${d}; display:block; float:none; clear:both; }
.geminiAnswer{ height:auto; width:100%; display:block; float:none; clear:both; }
#GeminiResponse .think{ background:transparent; font-size:1.45rem; width:calc(100% - 20px); height:20px; color:${isD ? "#ccc" : "#444"}; margin-top:3px; text-align:left; display:flex; padding-left:5px; border-left:3px solid ${d}; }
</style>`;

ytproMainDiv.appendChild(ytproGemini);
ytproGemini.addEventListener("click", function(){ geminiInfo(); });

var ytproFavElem=document.createElement("div");
sty(ytproFavElem);
ytproFavElem.innerHTML=`<svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 0 24 24" width="24"><path d="M0 0h24v24H0V0z" fill="none"/><path fill="${c}" d="M19.66 3.99c-2.64-1.8-5.9-.96-7.66 1.1-1.76-2.06-5.02-2.91-7.66-1.1-1.4.96-2.28 2.58-2.34 4.29-.14 3.88 3.3 6.99 8.55 11.76l.1.09c.76.69 1.93.69 2.69-.01l.11-.1c5.25-4.76 8.68-7.87 8.55-11.75-.06-1.7-.94-3.32-2.34-4.28zM12.1 18.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"/></svg><span style="margin-left:8px">Heart<span>`;
ytproMainDiv.appendChild(ytproFavElem);

var ytproDownVidElem=document.createElement("div");
sty(ytproDownVidElem);
ytproDownVidElem.style.width="140px";
ytproDownVidElem.innerHTML=`${downBtn.replace('width="18"','width="24"').replace('height="18"','height="24"')}<span style="margin-left:2px">Download<span>`;
ytproMainDiv.appendChild(ytproDownVidElem);
ytproDownVidElem.addEventListener("click", function(){ window.location.hash="download"; });

var ytproPIPVidElem=document.createElement("div");
sty(ytproPIPVidElem);
ytproPIPVidElem.style.width="140px";
ytproPIPVidElem.innerHTML=`<svg xmlns="http://www.w3.org/2000/svg" height="22" viewBox="0 0 24 24" width="22"><path fill="${c}" d="M18 7h-6c-.55 0-1 .45-1 1v4c0 .55.45 1 1 1h6c.55 0 1-.45 1-1V8c0-.55-.45-1-1-1zm3-4H3c-1.1 0-2 .9-2 2v14c0 1.1.9 1.98 2 1.98h18c1.1 0 2-.88 2-1.98V5c0-1.1-.9-2-2-2zm-1 16.01H4c-.55 0-1-.45-1-1V5.98c0-.55.45-1 1-1h16c.55 0 1 .45 1 1v12.03c0 .55-.45 1-1 1z"/></svg><span style="margin-left:8px">PIP Mode<span>`;
ytproMainDiv.appendChild(ytproPIPVidElem);
ytproPIPVidElem.addEventListener("click", function(){ PIPlayer(true); });
}
}
}else if(window.location.href.indexOf("youtube.com/shorts") > -1){
var b = document.getElementById("brtS");
var v = document.getElementById("volS");
if (b) b.remove();
if (v) v.remove();

if(document.getElementById("ytproMainSDivE") == null){
var ys=document.createElement("div");
ys.setAttribute("id","ytproMainSDivE");
ys.setAttribute("style",`width:50px;height:auto;position:relative;display:block;`);

ysDown=document.createElement("div");
ysDown.setAttribute("style",`height:48px;width:48px;display:flex;align-items:center;justify-content:center;filter:drop-shadow(0 0 1px #0009);border-radius:50%;`);
ysDown.innerHTML=downBtn.replaceAll(`${c}`,`#fff`).replace(`width="24"`,`width="30"`).replace(`height="24"`,`height="30"`);

ysDown.addEventListener("click", function(){ window.location.hash="download"; });

ysHeart=document.createElement("div");
ysHeart.setAttribute("style",`height:48px;width:48px;display:flex;align-items:center;justify-content:center;filter:drop-shadow(0 0 1px #0009);border-radius:50%;margin-bottom:0px;`);
ysHeart.innerHTML=`<svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 0 24 24" width="24"><path d="M0 0h24v24H0V0z" fill="none"/><path fill="#fff" d="M19.66 3.99c-2.64-1.8-5.9-.96-7.66 1.1-1.76-2.06-5.02-2.91-7.66-1.1-1.4.96-2.28 2.58-2.34 4.29-.14 3.88 3.3 6.99 8.55 11.76l.1.09c.76.69 1.93.69 2.69-.01l.11-.1c5.25-4.76 8.68-7.87 8.55-11.75-.06-1.7-.94-3.32-2.34-4.28zM12.1 18.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"/></svg>`;

try{
  var actions = document.getElementsByClassName("reel-player-overlay-actions")[0];
  if(actions){
    actions.insertBefore(ys, actions.children[1]);
    ys.appendChild(ysDown);
    ys.appendChild(ysHeart);
  }
}catch{}
}
try{document.querySelectorAll('dislike-button-view-model')[0].children[0].children[0].children[0].children[1].children[0].innerHTML=dislikes;}catch{}
}
}
setInterval(pkc,1000);

function navigateInternalYtMweb(videoId) {
    window.location.hash="";
    const link = document.createElement('a');
    link.href = `/watch?v=${videoId}`;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    link.remove();
}

function PIPlayer(pip = false){
var v=document.getElementsByClassName('video-stream')[0];
if(!v) return;
if(pip){
  if(v.getBoundingClientRect().height > v.getBoundingClientRect().width) Android.pipvid?.("portrait");
  else Android.pipvid?.("landscape");
  return;
}
v.requestFullscreen();
v.play();
pauseAllowed = false;
isPIP=true;
}

HTMLMediaElement.prototype.pause = function(){
if (pauseAllowed || PIPause) { return originalPause.apply(this, arguments); }
if (this.paused) { this.play().catch(() => {}); }
};

const originalExitFullscreen = document.exitFullscreen;
const originalRequestFullscreen = Element.prototype.requestFullscreen;

document.exitFullscreen = function (...args) {
 if(!isPIP){ return originalExitFullscreen.apply(this, args);}
};

Element.prototype.requestFullscreen = function (...args) {
var video = document.getElementsByClassName('video-stream')[0];
if(video){
  if(video.getBoundingClientRect().height > video.getBoundingClientRect().width) Android.fullScreen?.(true);
  else Android.fullScreen?.(false);
}
return originalRequestFullscreen.apply(this, args);
};

window.onhashchange=()=>{
try{document.getElementById("outerdownytprodiv")?.remove();}catch{}
try{document.getElementById("settingsprodiv")?.remove();}catch{}
if(window.location.hash == "#download"){ ytproDownVid(); }
else if(window.location.hash == "#settings"){ ytproSettings(); }
}

(() => {
const _origFetch = window.fetch;
window.fetch = async function(input, init) {
try {
const url = (typeof input === 'string') ? input : input.url;
if(url.includes("googleads.g.doubleclick.net") || url.includes("youtube.com/youtubei/v1/player/ad_break") || url.includes("youtube.com/pagead/adview") || url.includes("youtube.com/api/stats/ads")){
return "";
}else if(url.includes("youtube.com/youtubei/")){
const response = await _origFetch.apply(this, arguments);
try {
const clone = response.clone();
let data = await clone.json();

if(data?.responseContext?.webResponseContextExtensionData?.webResponseContextPreloadData?.preloadMessageNames?.[0] == "adSlotRenderer" || data?.responseContext?.webResponseContextExtensionData?.webResponseContextPreloadData?.preloadMessageNames?.[0] == "shortsAdsRenderer"){
data={};
}
delete data?.adSlots;
delete data?.playerAds;
delete data?.adPlacements;
delete data?.adBreakHeartbeatParams;
delete data?.[0]?.playerResponse?.adSlots;
delete data?.[0]?.playerResponse?.playerAds;
delete data?.[0]?.playerResponse?.adPlacements;
delete data?.[0]?.playerResponse?.adBreakHeartbeatParams;

const newBody = JSON.stringify(data);
const newHeaders = new Headers(response.headers);
newHeaders.set("content-length", String(newBody.length));
newHeaders.set("content-type", "application/json");

return new Response(newBody, {
status: response.status,
statusText: response.statusText,
headers: newHeaders
});
} catch (e) { return response; }
}
return _origFetch.apply(this, arguments);
} catch (e) { }
return _origFetch.apply(this, arguments);
};
})();

const XHR = window.XMLHttpRequest;
const origOpen = XHR.prototype.open;
const origSend = XHR.prototype.send;

XHR.prototype.open = function(method, url, ...rest) {
this._interceptedMethod = method;
this._interceptedUrl = url;
return origOpen.apply(this, [method, url, ...rest]);
};

XHR.prototype.send = function(body) {
if (this._interceptedUrl.includes("googleads.g.doubleclick.net") || this._interceptedUrl.includes("youtube.com/youtubei/v1/player/ad_break") || this._interceptedUrl.includes("youtube.com/pagead/adview") || this._interceptedUrl.includes("youtube.com/api/stats/ads")) {
return;
}
return origSend.apply(this, arguments);
};

function adsBlock(){
try{ document.getElementsByClassName('video-stream')[0]?.removeAttribute('disablepictureinpicture'); }catch{}

var ads=document.getElementsByTagName("ad-slot-renderer");
for(var x in ads){ try{ads[x].remove();}catch{} }
try{
document.getElementsByClassName("ad-interrupting")[0]?.getElementsByTagName("video")[0]?.setCurrentTime(9999);
document.getElementsByClassName("ytp-ad-skip-button-modern")[0]?.click();
}catch{}

try{ document.getElementsByTagName("ytm-promoted-sparkles-web-renderer")[0]?.remove(); }catch{}
try{ document.getElementsByTagName("ytm-companion-ad-renderer")[0]?.remove(); }catch{}

try{
document.querySelectorAll('a').forEach(a => {
if (a.href.indexOf("intent://") > -1) { a.style.display = 'none'; }
});
}catch{}
try{document.getElementsByTagName("ytm-paid-content-overlay-renderer")[0].style.display="none";}catch{}

if(localStorage.getItem("shorts") == "true"){
for( x in document.getElementsByClassName("big-shorts-singleton")){ try{document.getElementsByClassName("big-shorts-singleton")[x].remove(); }catch{} }
for( x in document.getElementsByTagName("ytm-reel-shelf-renderer")){ try{document.getElementsByTagName("ytm-reel-shelf-renderer")[x].remove(); }catch{} }
for( x in document.getElementsByTagName("ytm-shorts-lockup-view-model")){ try{document.getElementsByTagName("ytm-shorts-lockup-view-model")[x].remove(); }catch{} }
}
}
}

function addMaxButton(){
var Ve=document.getElementById('player');
var Vv=document.getElementsByClassName('video-stream')[0];
if(Ve && Vv && document.fullscreenElement){
try{ Ve.style.transform = zoomIn ? `scale(${scale})` : "scale(1)"; }catch{}
}else if(Ve){
try{ Ve.style.transform="scale(1)"; }catch{}
}
}

async function extraSpeed(){
const slider = document.getElementById("slider");
if(slider && slider.max != 10) {
slider.max = 10;
slider.ariaValueMax = "10";
slider.addEventListener("input", () => {
  const video = document.querySelector('.video-stream');
  if (video) video.playbackRate = parseFloat(slider.value);
});
}
}

const targetNode = document.body;
const config = { childList: true, subtree: true };

const observer = new MutationObserver(() => {
extraSpeed(); 
adsBlock();
addMaxButton();
addSettingsTab();

try{
var video = document.getElementsByClassName('video-stream')[0];
if(video && video.getBoundingClientRect().height > video.getBoundingClientRect().width){
Android.fullScreen?.(true);
}else{
Android.fullScreen?.(false);
}}catch{}
});

observer.observe(targetNode, config);

document.addEventListener('click',(event) => {
let anchor = event.target.closest('a');
if (anchor){
if(anchor.href.includes("www.youtube.com/redirect")){
try{ document.getElementsByClassName('video-stream')[0]?.pause(); }catch{}
const url=new URL(anchor.href).searchParams.get("q");
setTimeout(()=>{Android.oplink?.(url)},50);
event.preventDefault();
event.stopPropagation(); 
}
}
}, true);
}
    