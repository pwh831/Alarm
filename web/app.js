import {
  EMERGENCY_PHRASE, Hint, MissionSession, MotionGuard, Phase, RANDOM_EXERCISES,
  createCounter, dayKey, matchesEmergency, successDays, summary,
} from './core.js';

// ── 설정·기록 저장 (이 브라우저의 localStorage) ────────────────
const SETTINGS_KEY = 'fitwake.settings';
const RECORDS_KEY = 'fitwake.records';
const defaults = { exercise: 'SQUAT', difficulty: 'NORMAL', reps: 15, shortcutName: 'FitWake 완료' };

function load(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key)) ?? fallback;
  } catch {
    return fallback;
  }
}
const save = (key, value) => {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* 사생활 보호 모드 등 */ }
};
const settings = { ...defaults, ...load(SETTINGS_KEY, {}) };
const records = () => load(RECORDS_KEY, []);

const LABEL = { SQUAT: '스쿼트', PUSHUP: '푸시업', ARM_RAISE: '팔 올리기', RANDOM: '랜덤' };
const DEFAULT_REPS = { SQUAT: 15, PUSHUP: 10, ARM_RAISE: 20, RANDOM: 15 };
const $ = (id) => document.getElementById(id);

// ── 화면 전환 ─────────────────────────────────────────────
function show(id) {
  for (const v of document.querySelectorAll('.view')) v.classList.toggle('hidden', v.id !== id);
  if (id === 'stats') renderStats();
  if (id !== 'mission') stopMission();
  window.scrollTo(0, 0);
}
for (const b of document.querySelectorAll('[data-go]')) b.addEventListener('click', () => show(b.dataset.go));

// ── 홈: 미션 설정 ─────────────────────────────────────────
const missionUrl = `${location.origin}${location.pathname}?alarm`;
function renderSettings() {
  $('exercise').value = settings.exercise;
  $('difficulty').value = settings.difficulty;
  $('reps').value = settings.reps;
  $('shortcut-name').value = settings.shortcutName;
  $('shortcut-name-label').textContent = settings.shortcutName;
  $('mission-url').textContent = missionUrl;
  $('exercise-note').textContent = {
    PUSHUP: '폰을 몸 옆쪽 바닥에 세워 측면에서 찍히게 해 주세요.',
    RANDOM: '매번 스쿼트와 푸시업 중 하나가 무작위로 정해져요.',
    ARM_RAISE: '부상이 있거나 공간이 좁을 때 쓰는 가벼운 미션이에요. 양팔을 머리 위로 올렸다 내리면 1회예요.',
  }[settings.exercise] ?? '폰을 세워 두고 전신이 보이게 2~3m 떨어져 주세요.';
}
$('exercise').addEventListener('change', (e) => {
  settings.exercise = e.target.value;
  settings.reps = DEFAULT_REPS[settings.exercise];
  save(SETTINGS_KEY, settings);
  renderSettings();
});
$('difficulty').addEventListener('change', (e) => { settings.difficulty = e.target.value; save(SETTINGS_KEY, settings); });
$('reps').addEventListener('change', (e) => {
  settings.reps = Math.min(50, Math.max(5, Math.round(Number(e.target.value) || 15)));
  save(SETTINGS_KEY, settings);
  renderSettings();
});
$('shortcut-name').addEventListener('change', (e) => {
  settings.shortcutName = e.target.value.trim() || defaults.shortcutName;
  save(SETTINGS_KEY, settings);
  renderSettings();
});

// ── 미션 ─────────────────────────────────────────────────
const VISION_URL = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@1.0.1';
const MODEL_URL = 'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task';
// MediaPipe Pose 33개 랜드마크 중 판정에 쓰는 것
const LANDMARKS = {
  leftShoulder: 11, rightShoulder: 12, leftElbow: 13, rightElbow: 14, leftWrist: 15, rightWrist: 16,
  leftHip: 23, rightHip: 24, leftKnee: 25, rightKnee: 26, leftAnkle: 27, rightAnkle: 28,
};
const BONES = [[11, 12], [23, 24], [11, 13], [13, 15], [12, 14], [14, 16], [11, 23], [12, 24], [23, 25], [25, 27], [24, 26], [26, 28]];

let landmarker = null;
let mission = null; // { alarm, exercise, target, session, motion, stream, facing, openedAt, startedAt, running, wakeLock }

async function getLandmarker() {
  if (landmarker) return landmarker;
  const { FilesetResolver, PoseLandmarker } = await import(`${VISION_URL}/vision_bundle.mjs`);
  const fileset = await FilesetResolver.forVisionTasks(`${VISION_URL}/wasm`);
  const options = (delegate) => ({ baseOptions: { modelAssetPath: MODEL_URL, delegate }, runningMode: 'VIDEO', numPoses: 1 });
  try {
    landmarker = await PoseLandmarker.createFromOptions(fileset, options('GPU'));
  } catch {
    landmarker = await PoseLandmarker.createFromOptions(fileset, options('CPU'));
  }
  return landmarker;
}

function openMission(alarm) {
  const exercise = settings.exercise === 'RANDOM'
    ? RANDOM_EXERCISES[Math.floor(Math.random() * RANDOM_EXERCISES.length)]
    : settings.exercise;
  mission = {
    alarm,
    exercise,
    target: settings.reps,
    session: new MissionSession(createCounter(exercise, settings.difficulty)),
    motion: new MotionGuard(),
    facing: 'user',
    openedAt: Date.now(),
    running: false,
    lastVideoTime: -1,
    lastReps: 0,
  };
  show('mission');
  $('tap-title').textContent = alarm ? '일어날 시간이에요!' : '연습';
  $('tap-mission').textContent = `${LABEL[exercise]} ${mission.target}회 · 끝내야 백업 알람이 꺼져요`;
  if (!alarm) $('tap-mission').textContent = `${LABEL[exercise]} ${mission.target}회`;
  $('tap-to-start').classList.remove('hidden');
  $('emergency').classList.toggle('hidden', !alarm);
  $('quit').classList.toggle('hidden', alarm);
  updateHud({ countdownSec: null, started: false, rep: { reps: 0, phase: Phase.WAITING, hint: Hint.NONE } });
}

$('practice').addEventListener('click', () => openMission(false));
$('quit').addEventListener('click', () => show('home'));

$('start').addEventListener('click', async () => {
  // iOS는 동작 센서 권한을 사용자 탭 안에서만 요청할 수 있다.
  if (typeof DeviceMotionEvent !== 'undefined' && typeof DeviceMotionEvent.requestPermission === 'function') {
    try { await DeviceMotionEvent.requestPermission(); } catch { /* 거부해도 미션은 진행 */ }
  }
  speechSynthesis.speak(new SpeechSynthesisUtterance('')); // iOS 음성 잠금 해제
  $('tap-to-start').classList.add('hidden');
  $('loading').classList.remove('hidden');
  try {
    await startCamera();
    await getLandmarker();
  } catch (e) {
    $('loading').classList.add('hidden');
    $('hint').textContent = `카메라나 인식 엔진을 시작하지 못했어요: ${e.message ?? e}`;
    return;
  }
  $('loading').classList.add('hidden');
  window.addEventListener('devicemotion', onMotion);
  try { mission.wakeLock = await navigator.wakeLock?.request('screen'); } catch { /* 지원 안 함 */ }
  mission.startedAt = Date.now();
  mission.running = true;
  requestAnimationFrame(loop);
});

async function startCamera() {
  mission.stream?.getTracks().forEach((t) => t.stop());
  mission.stream = await navigator.mediaDevices.getUserMedia({
    video: { facingMode: mission.facing, width: { ideal: 720 }, height: { ideal: 1280 } },
    audio: false,
  });
  const video = $('video');
  video.srcObject = mission.stream;
  await video.play();
  document.querySelector('.stage').classList.toggle('mirror', mission.facing === 'user');
}

$('switch-camera').addEventListener('click', async () => {
  if (!mission) return;
  mission.facing = mission.facing === 'user' ? 'environment' : 'user';
  try { await startCamera(); } catch { /* 카메라가 하나뿐인 기기 */ }
});

function onMotion(e) {
  const a = e.accelerationIncludingGravity;
  if (mission && a && a.x !== null) mission.motion.onAcceleration(performance.now(), a.x, a.y, a.z);
}

function loop() {
  if (!mission?.running) return;
  const video = $('video');
  if (video.readyState >= 2 && video.currentTime !== mission.lastVideoTime) {
    mission.lastVideoTime = video.currentTime;
    const now = performance.now();
    const result = landmarker.detectForVideo(video, now);
    const lm = result.landmarks?.[0];
    const points = {};
    if (lm) {
      for (const [name, i] of Object.entries(LANDMARKS)) {
        const p = lm[i];
        points[name] = { x: p.x * video.videoWidth, y: p.y * video.videoHeight, confidence: p.visibility ?? 1 };
      }
    }
    const state = mission.session.update(now, points, mission.motion.isMoving(now));
    drawSkeleton(lm);
    updateHud(state);
    if (state.rep.reps > mission.lastReps) {
      mission.lastReps = state.rep.reps;
      speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(String(state.rep.reps));
      u.lang = 'ko-KR';
      speechSynthesis.speak(u);
      if (state.rep.reps >= mission.target) {
        finish('MISSION', state.rep.reps);
        return;
      }
    }
  }
  requestAnimationFrame(loop);
}

function updateHud(state) {
  $('count').textContent = `${state.rep.reps} / ${mission.target}`;
  $('countdown').classList.toggle('hidden', state.countdownSec === null);
  $('countdown').textContent = state.countdownSec ?? '';
  $('hint').textContent = hintText(state);
}

function hintText(state) {
  switch (state.rep.hint) {
    case Hint.BODY_NOT_VISIBLE: return '몸이 잘 안 보여요. 조금 더 뒤로 가거나 불을 켜주세요';
    case Hint.TOO_FAST: return '너무 빨라요. 천천히 끝까지 해주세요';
    case Hint.LOWER_HIPS: return '엉덩이를 더 내려주세요';
    case Hint.GET_HORIZONTAL: return '몸을 바닥과 수평으로 만들어주세요';
    case Hint.KEEP_BODY_STRAIGHT: return '어깨부터 발끝까지 일직선을 유지하세요';
    case Hint.PHONE_MOVING: return '폰이 움직이고 있어요. 바닥이나 선반에 세워 두세요';
    default:
      if (state.countdownSec !== null) return '좋아요, 그대로! 곧 시작해요';
      if (state.started && state.rep.phase !== Phase.WAITING) return '좋아요! 계속하세요';
      return {
        SQUAT: '전신이 보이도록 2~3m 떨어져서 똑바로 서세요',
        PUSHUP: '폰을 옆에 두고 팔을 편 플랭크 자세를 잡으세요',
        ARM_RAISE: '상체가 다 보이게 서서 양팔을 내리세요',
      }[mission.exercise];
  }
}

/** 미리보기와 같은 object-fit: cover 변환으로 관절을 그린다. */
function drawSkeleton(lm) {
  const canvas = $('overlay');
  const video = $('video');
  const dpr = window.devicePixelRatio || 1;
  const w = canvas.clientWidth;
  const h = canvas.clientHeight;
  if (canvas.width !== w * dpr || canvas.height !== h * dpr) {
    canvas.width = w * dpr;
    canvas.height = h * dpr;
  }
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);
  if (!lm || !video.videoWidth) return;
  const scale = Math.max(w / video.videoWidth, h / video.videoHeight);
  const dx = (w - video.videoWidth * scale) / 2;
  const dy = (h - video.videoHeight * scale) / 2;
  const at = (i) => [lm[i].x * video.videoWidth * scale + dx, lm[i].y * video.videoHeight * scale + dy];
  const seen = (i) => (lm[i].visibility ?? 1) >= 0.5;
  ctx.strokeStyle = '#ff8a3d';
  ctx.lineWidth = 5;
  ctx.lineCap = 'round';
  for (const [a, b] of BONES) {
    if (!seen(a) || !seen(b)) continue;
    ctx.beginPath();
    ctx.moveTo(...at(a));
    ctx.lineTo(...at(b));
    ctx.stroke();
  }
  ctx.fillStyle = '#fff';
  for (const i of Object.values(LANDMARKS)) {
    if (!seen(i)) continue;
    ctx.beginPath();
    ctx.arc(...at(i), 6, 0, Math.PI * 2);
    ctx.fill();
  }
}

function stopMission() {
  if (!mission) return;
  mission.running = false;
  mission.stream?.getTracks().forEach((t) => t.stop());
  mission.wakeLock?.release?.();
  window.removeEventListener('devicemotion', onMotion);
}

function finish(method, reps) {
  const m = mission;
  stopMission();
  const seconds = Math.round((Date.now() - (m.startedAt ?? m.openedAt)) / 1000);
  if (m.alarm) {
    const list = records();
    list.push({ startedAt: m.openedAt, finishedAt: Date.now(), method, exercise: method === 'MISSION' ? m.exercise : null, reps });
    save(RECORDS_KEY, list);
  }
  $('done-text').textContent = method === 'MISSION'
    ? `${LABEL[m.exercise]} ${reps}회를 ${seconds}초 만에 끝냈어요.`
    : '긴급 해제했어요.';
  $('run-shortcut').classList.toggle('hidden', !m.alarm);
  $('done-note').classList.toggle('hidden', !m.alarm);
  mission = null;
  show('done');
  if (m.alarm) runShortcut();
}

function runShortcut() {
  location.href = `shortcuts://run-shortcut?name=${encodeURIComponent(settings.shortcutName)}`;
}
$('run-shortcut').addEventListener('click', runShortcut);

$('emergency').addEventListener('click', () => {
  const input = prompt(`정말 급할 때만 쓰세요. 아래 문장을 그대로 입력하면 꺼져요.\n\n${EMERGENCY_PHRASE}`);
  if (input !== null && matchesEmergency(input)) finish('EMERGENCY', 0);
});

// ── 기록 ─────────────────────────────────────────────────
let calendarMonth = new Date();
calendarMonth.setDate(1);

function renderStats() {
  const list = records();
  const s = summary(list, Date.now());
  $('streak').textContent = `${s.streak}일`;
  const line = (reps) => Object.entries(reps).map(([e, n]) => `${LABEL[e]} ${n}회`).join(', ') || '-';
  $('reps-summary').innerHTML = '';
  for (const [title, text] of [['이번 주', line(s.weekReps)], ['이번 달', line(s.monthReps)], ['이번 달 긴급 해제', `${s.monthEmergencyCount}번`]]) {
    const p = document.createElement('p');
    p.innerHTML = `<span class="muted"></span> `;
    p.firstChild.textContent = title;
    p.append(text);
    $('reps-summary').append(p);
  }

  const ok = successDays(list);
  const y = calendarMonth.getFullYear();
  const mo = calendarMonth.getMonth();
  $('month-title').textContent = `${y}년 ${mo + 1}월`;
  const cal = $('calendar');
  cal.innerHTML = '';
  for (const d of ['월', '화', '수', '목', '금', '토', '일']) {
    const el = document.createElement('div');
    el.className = 'dow';
    el.textContent = d;
    cal.append(el);
  }
  const leading = (new Date(y, mo, 1).getDay() + 6) % 7;
  for (let i = 0; i < leading; i++) cal.append(document.createElement('div'));
  const todayKey = dayKey(Date.now());
  for (let day = 1; day <= new Date(y, mo + 1, 0).getDate(); day++) {
    const key = dayKey(new Date(y, mo, day).getTime());
    const el = document.createElement('div');
    el.className = `day${ok.has(key) ? ' ok' : ''}${key === todayKey ? ' today' : ''}`;
    el.textContent = day;
    cal.append(el);
  }

  const recent = $('recent');
  recent.innerHTML = '';
  if (list.length === 0) {
    const li = document.createElement('li');
    li.className = 'muted';
    li.textContent = '아직 기록이 없어요. 알람을 끄고 미션을 마치면 여기에 쌓여요.';
    recent.append(li);
  }
  for (const r of [...list].sort((x, y) => y.startedAt - x.startedAt).slice(0, 30)) {
    const li = document.createElement('li');
    const when = new Date(r.startedAt).toLocaleString('ko-KR', { month: 'short', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit' });
    const secs = Math.round((r.finishedAt - r.startedAt) / 1000);
    const detail = document.createElement('div');
    detail.className = r.method === 'MISSION' ? 'ok small' : 'bad small';
    detail.textContent = r.method === 'MISSION' ? `${LABEL[r.exercise]} ${r.reps}회 · ${Math.floor(secs / 60)}분 ${secs % 60}초` : '긴급 해제';
    li.append(when, detail);
    recent.append(li);
  }
}
$('prev-month').addEventListener('click', () => { calendarMonth.setMonth(calendarMonth.getMonth() - 1); renderStats(); });
$('next-month').addEventListener('click', () => { calendarMonth.setMonth(calendarMonth.getMonth() + 1); renderStats(); });

// ── 시작 ─────────────────────────────────────────────────
renderSettings();
if (new URLSearchParams(location.search).has('alarm')) {
  // 알람을 끈 뒤 단축어 자동화가 연 경우. 새로고침해도 다시 미션이 열린다.
  openMission(true);
} else {
  show('home');
}
