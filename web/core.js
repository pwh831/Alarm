// 플랫폼 독립 로직: 반복 카운터, 미션 진행, 폰 움직임 감지, 기록 통계.
// Android(core/pose, core/alarm)·iOS(FitWakeCore)와 같은 규칙을 JavaScript로 옮긴 것이다.

export const Exercise = { SQUAT: 'SQUAT', SQUAT_UPPER: 'SQUAT_UPPER', PUSHUP: 'PUSHUP', ARM_RAISE: 'ARM_RAISE' };
export const RANDOM_EXERCISES = [Exercise.SQUAT, Exercise.PUSHUP];

/** PRD 5.3 난이도별 기준. */
export const Difficulty = {
  // upperSquatDrop: 상체만 보는 스쿼트에서 엉덩이가 몸통 길이의 몇 배만큼 내려가야 하는지
  EASY: { squatBottomKnee: 120, pushupBottomElbow: 110, allowKneePushup: true, armRaiseMinShoulder: 90, upperSquatDrop: 0.3 },
  NORMAL: { squatBottomKnee: 100, pushupBottomElbow: 90, allowKneePushup: false, armRaiseMinShoulder: 140, upperSquatDrop: 0.5 },
  HARD: { squatBottomKnee: 85, pushupBottomElbow: 75, allowKneePushup: false, armRaiseMinShoulder: 160, upperSquatDrop: 0.7 },
};

export const Hint = {
  NONE: 'NONE',
  BODY_NOT_VISIBLE: 'BODY_NOT_VISIBLE',
  TOO_FAST: 'TOO_FAST',
  LOWER_HIPS: 'LOWER_HIPS',
  GET_HORIZONTAL: 'GET_HORIZONTAL',
  KEEP_BODY_STRAIGHT: 'KEEP_BODY_STRAIGHT',
  PHONE_MOVING: 'PHONE_MOVING',
  /** 상체 스쿼트: 허리를 숙이지 말고 상체를 세운 채 앉아야 함. */
  STAND_UPRIGHT: 'STAND_UPRIGHT',
};

export const Phase = { WAITING: 'WAITING', TOP: 'TOP', BOTTOM: 'BOTTOM' };

/** 관절 이름. 좌표는 이미지 기준(y가 아래로 증가) {x, y, confidence}. */
const SIDES = [
  { shoulder: 'leftShoulder', elbow: 'leftElbow', wrist: 'leftWrist', hip: 'leftHip', knee: 'leftKnee', ankle: 'leftAnkle' },
  { shoulder: 'rightShoulder', elbow: 'rightElbow', wrist: 'rightWrist', hip: 'rightHip', knee: 'rightKnee', ankle: 'rightAnkle' },
];

/** b를 꼭짓점으로 하는 각 abc (0~180도). */
export function angleDeg(a, b, c) {
  const v1 = Math.atan2(a.y - b.y, a.x - b.x);
  const v2 = Math.atan2(c.y - b.y, c.x - b.x);
  let deg = Math.abs(((v1 - v2) * 180) / Math.PI);
  if (deg > 180) deg = 360 - deg;
  return deg;
}

const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
const avg = (xs) => xs.reduce((s, x) => s + x, 0) / xs.length;

/** 주어진 관절이 모두 minConfidence 이상이면 좌표 배열, 아니면 null. */
function visible(points, minConfidence, ...names) {
  const out = [];
  for (const n of names) {
    const p = points[n];
    if (!p || p.confidence < minConfidence) return null;
    out.push(p);
  }
  return out;
}

export const defaultConfig = { minConfidence: 0.5, minRepMs: 400, lostVisibilityMs: 1000, smoothing: 0.5 };

/**
 * 관절 각도 기반 반복 카운터 (PRD 5.2).
 * 각도가 top 이상이면 TOP, bottom 이하이면 BOTTOM. TOP → BOTTOM → TOP을 한 번 거치면 1회.
 */
class RepCounter {
  constructor(config) {
    this.config = { ...defaultConfig, ...config };
    this.reps = 0;
    this.phase = Phase.WAITING;
    this.smoothed = null;
    this.lastTopMs = 0;
    this.lastSeenMs = null;
    this.lastHint = Hint.NONE;
  }

  /** 하위 클래스: {angle, formHint} 또는 null(필요한 관절이 안 보임). */
  measure(_points, _smooth) { throw new Error('override'); }
  onTop(_points) {}
  reset() {}

  /** 판정에 필요한 관절이 모두 보이는지. 상태는 바꾸지 않는다. */
  sees(points) { return this.measure(points, (x) => x) !== null; }

  update(timestampMs, points) {
    const now = timestampMs;
    const m = this.measure(points, (raw) => {
      this.smoothed = this.smoothed === null ? raw : this.smoothed + this.config.smoothing * (raw - this.smoothed);
      return this.smoothed;
    });
    if (m === null) {
      if (this.lastSeenMs === null) this.lastSeenMs = now;
      if (now - this.lastSeenMs >= this.config.lostVisibilityMs) {
        this.phase = Phase.WAITING;
        this.smoothed = null;
        this.reset();
        this.lastHint = Hint.BODY_NOT_VISIBLE;
      }
      return this.state(null);
    }
    this.lastSeenMs = now;
    if (this.lastHint === Hint.BODY_NOT_VISIBLE) this.lastHint = Hint.NONE;

    const angle = m.angle;
    const formHint = m.formHint ?? Hint.NONE;
    if (this.phase === Phase.BOTTOM) {
      if (angle >= this.top) {
        if (now - this.lastTopMs >= this.config.minRepMs) {
          this.reps++;
          this.lastHint = Hint.NONE;
        } else {
          this.lastHint = Hint.TOO_FAST;
        }
        this.phase = Phase.TOP;
        this.lastTopMs = now;
        this.onTop(points);
      }
    } else if (angle >= this.top) {
      this.phase = Phase.TOP;
      this.lastTopMs = now;
      this.onTop(points);
      if (this.lastHint !== Hint.TOO_FAST) this.lastHint = formHint;
    } else if (this.phase === Phase.TOP && angle <= this.bottom) {
      if (formHint === Hint.NONE) {
        this.phase = Phase.BOTTOM;
        this.lastHint = Hint.NONE;
      } else {
        this.lastHint = formHint;
      }
    } else if (formHint !== Hint.NONE) {
      this.lastHint = formHint;
    }
    return this.state(angle);
  }

  state(angle) {
    return { reps: this.reps, phase: this.phase, hint: this.lastHint, angle };
  }
}

/** 스쿼트: 무릎 각도 + 선 자세 대비 엉덩이 하강(허벅지 길이의 30% 이상). */
export class SquatCounter extends RepCounter {
  constructor(difficulty, config) {
    super(config);
    this.top = 160;
    this.bottom = difficulty.squatBottomKnee;
    this.standingHipY = null;
    this.standingThigh = null;
  }

  measure(points, smooth) {
    const legs = SIDES.map((s) => visible(points, this.config.minConfidence, s.hip, s.knee, s.ankle)).filter(Boolean);
    if (legs.length === 0) return null;
    const angle = smooth(avg(legs.map(([h, k, a]) => angleDeg(h, k, a))));
    const hipY = avg(legs.map(([h]) => h.y));
    let formHint = Hint.NONE;
    if (angle <= this.bottom && this.standingHipY !== null && hipY - this.standingHipY < this.standingThigh * 0.3) {
      formHint = Hint.LOWER_HIPS;
    }
    return { angle, formHint };
  }

  onTop(points) {
    const legs = SIDES.map((s) => visible(points, this.config.minConfidence, s.hip, s.knee)).filter(Boolean);
    if (legs.length === 0) return;
    this.standingHipY = avg(legs.map(([h]) => h.y));
    this.standingThigh = avg(legs.map(([h, k]) => dist(h, k)));
  }

  reset() {
    this.standingHipY = null;
    this.standingThigh = null;
  }
}

/** 푸시업: 팔꿈치 각도 + 몸 수평(45° 이내)·일직선(150° 이상). 측면 촬영, 잘 보이는 쪽 팔. */
export class PushupCounter extends RepCounter {
  constructor(difficulty, config) {
    super(config);
    this.top = 150;
    this.bottom = difficulty.pushupBottomElbow;
    this.allowKnee = difficulty.allowKneePushup;
  }

  measure(points, smooth) {
    const minC = this.config.minConfidence;
    const conf = (s) => Math.min(...[s.shoulder, s.elbow, s.wrist, s.hip].map((n) => points[n]?.confidence ?? 0));
    const side = SIDES.filter((s) => visible(points, minC, s.shoulder, s.elbow, s.wrist, s.hip))
      .sort((a, b) => conf(b) - conf(a))[0];
    if (!side) return null;
    const [shoulder, elbow, wrist, hip] = visible(points, 0, side.shoulder, side.elbow, side.wrist, side.hip);
    const ankle = visible(points, minC, side.ankle)?.[0];
    const knee = visible(points, minC, side.knee)?.[0];
    const fullPlank = ankle && angleDeg(shoulder, hip, ankle) >= 150 ? ankle : null;
    const kneePlank = knee && this.allowKnee && angleDeg(shoulder, hip, knee) >= 150 ? knee : null;
    const end = fullPlank ?? kneePlank ?? ankle ?? knee;
    if (!end) return null;
    const angle = smooth(angleDeg(shoulder, elbow, wrist));
    const tilt = (Math.atan2(Math.abs(end.y - shoulder.y), Math.abs(end.x - shoulder.x)) * 180) / Math.PI;
    let formHint = Hint.NONE;
    if (tilt > 45) formHint = Hint.GET_HORIZONTAL;
    else if (!fullPlank && !kneePlank) formHint = Hint.KEEP_BODY_STRAIGHT;
    return { angle, formHint };
  }
}

/** 팔 올리기: 180 - (엉덩이-어깨-손목 각도). 양팔 중 덜 올린 팔 기준. */
export class ArmRaiseCounter extends RepCounter {
  constructor(difficulty, config) {
    super(config);
    this.top = 150;
    this.bottom = 180 - difficulty.armRaiseMinShoulder;
  }

  measure(points, smooth) {
    const arms = SIDES.map((s) => visible(points, this.config.minConfidence, s.hip, s.shoulder, s.wrist)).filter(Boolean);
    if (arms.length === 0) return null;
    const lowest = Math.min(...arms.map(([h, s, w]) => angleDeg(h, s, w)));
    return { angle: smooth(180 - lowest), formHint: Hint.NONE };
  }
}

/**
 * 좁은 공간용 스쿼트: 어깨와 엉덩이만 보이면 된다.
 * 최근 몇 초 동안 가장 높았던 엉덩이 위치(선 자세)를 기준으로, 엉덩이가 몸통 길이의 일정 배수 이상
 * 내려갔다가 돌아오면 1회. 허리만 숙이면 엉덩이가 내려가지 않으므로 세지 않는다.
 * 판정값은 `100 - 하강비율×100`이라 선 자세가 100 근처, 깊이 앉을수록 작아진다.
 */
export class UpperBodySquatCounter extends RepCounter {
  constructor(difficulty, config) {
    super(config);
    this.top = 90;
    this.bottom = 100 - difficulty.upperSquatDrop * 100;
    this.windowMs = 6000;
    this.samples = [];
    this.recording = false;
  }

  update(timestampMs, points) {
    this.now = timestampMs;
    this.recording = true;
    try {
      return super.update(timestampMs, points);
    } finally {
      this.recording = false;
    }
  }

  measure(points, smooth) {
    const torsos = SIDES.map((s) => visible(points, this.config.minConfidence, s.shoulder, s.hip)).filter(Boolean);
    if (torsos.length === 0) return null;
    const shoulderY = avg(torsos.map(([s]) => s.y));
    const hipY = avg(torsos.map(([, h]) => h.y));
    const torso = avg(torsos.map(([s, h]) => dist(s, h)));
    const lean = (Math.atan2(Math.abs(avg(torsos.map(([s, h]) => s.x - h.x))), hipY - shoulderY) * 180) / Math.PI;

    // 판정 여부 확인(sees)만 할 때는 기준 자세 기록을 건드리지 않는다.
    if (this.recording) {
      this.samples.push({ t: this.now, hipY, torso });
      while (this.samples.length && this.now - this.samples[0].t > this.windowMs) this.samples.shift();
    }
    const window = this.samples.length ? this.samples : [{ hipY, torso }];
    const standingHipY = Math.min(...window.map((x) => x.hipY));
    const standingTorso = Math.max(...window.map((x) => x.torso));
    const drop = Math.max(0, (hipY - standingHipY) / standingTorso);
    const formHint = lean > 50 ? Hint.STAND_UPRIGHT : Hint.NONE;
    return { angle: smooth(100 - drop * 100), formHint };
  }

  reset() {
    this.samples = [];
  }
}

export function createCounter(exercise, difficultyName, config) {
  const d = Difficulty[difficultyName] ?? Difficulty.NORMAL;
  if (exercise === Exercise.SQUAT_UPPER) return new UpperBodySquatCounter(d, config);
  if (exercise === Exercise.PUSHUP) return new PushupCounter(d, config);
  if (exercise === Exercise.ARM_RAISE) return new ArmRaiseCounter(d, config);
  return new SquatCounter(d, config);
}

/** 몸이 계속 보이면 카운트다운 후 시작하고, 폰이 움직이는 동안은 세지 않는다. */
export class MissionSession {
  constructor(counter, countdownMs = 3000) {
    this.counter = counter;
    this.countdownMs = countdownMs;
    this.visibleSince = null;
    this.started = false;
    this.last = { reps: 0, phase: Phase.WAITING, hint: Hint.NONE, angle: null };
  }

  update(timestampMs, points, phoneMoving = false) {
    if (!this.started) {
      if (phoneMoving) {
        this.visibleSince = null;
        return { countdownSec: null, started: false, rep: { ...this.last, hint: Hint.PHONE_MOVING } };
      }
      if (!this.counter.sees(points)) {
        this.visibleSince = null;
        return { countdownSec: null, started: false, rep: this.last };
      }
      if (this.visibleSince === null) this.visibleSince = timestampMs;
      const left = this.countdownMs - (timestampMs - this.visibleSince);
      if (left > 0) return { countdownSec: Math.ceil(left / 1000), started: false, rep: this.last };
      this.started = true;
    }
    if (phoneMoving) return { countdownSec: null, started: true, rep: { ...this.last, hint: Hint.PHONE_MOVING } };
    this.last = this.counter.update(timestampMs, points);
    return { countdownSec: null, started: true, rep: this.last };
  }
}

/** 가속도(m/s², 중력 포함)로 폰이 움직이는지 판단한다 (PRD AC-06). */
export class MotionGuard {
  constructor(threshold = 1.0, holdMs = 700, alpha = 0.1) {
    Object.assign(this, { threshold, holdMs, alpha, gravity: null, lastMovement: null });
  }

  onAcceleration(timestampMs, x, y, z) {
    if (!this.gravity) {
      this.gravity = [x, y, z];
      return;
    }
    const g = this.gravity;
    g[0] += this.alpha * (x - g[0]);
    g[1] += this.alpha * (y - g[1]);
    g[2] += this.alpha * (z - g[2]);
    if (Math.hypot(x - g[0], y - g[1], z - g[2]) > this.threshold) this.lastMovement = timestampMs;
  }

  isMoving(nowMs) {
    return this.lastMovement !== null && nowMs - this.lastMovement < this.holdMs;
  }
}

/** 긴급 해제 문장 (PRD AC-07). 띄어쓰기 차이는 봐준다. */
export const EMERGENCY_PHRASE = '나는 지금 완전히 깨어 있고 운동 대신 알람을 끄기로 선택합니다';
export const matchesEmergency = (input, phrase = EMERGENCY_PHRASE) =>
  input.trim().split(/\s+/).join(' ') === phrase.trim().split(/\s+/).join(' ');

// ── 기록 통계 (PRD ST-03, ST-04) ─────────────────────────────
// 기록: { startedAt: epoch ms, finishedAt: epoch ms, method: 'MISSION' | 'EMERGENCY', exercise, reps }

/** 현지 날짜 키 'YYYY-MM-DD'. */
export function dayKey(ms) {
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function addDays(ms, n) {
  const d = new Date(ms);
  d.setDate(d.getDate() + n);
  return d.getTime();
}

export function successDays(records) {
  return new Set(records.filter((r) => r.method === 'MISSION').map((r) => dayKey(r.startedAt)));
}

/** 연속 기상 일수. 오늘 아직 기록이 없어도 어제까지 이어졌으면 유지된다. */
export function streak(records, nowMs) {
  const days = successDays(records);
  let day = days.has(dayKey(nowMs)) ? nowMs : addDays(nowMs, -1);
  let count = 0;
  while (days.has(dayKey(day))) {
    count++;
    day = addDays(day, -1);
  }
  return count;
}

export function summary(records, nowMs) {
  const today = new Date(nowMs);
  today.setHours(0, 0, 0, 0);
  const weekStart = addDays(today.getTime(), -((today.getDay() + 6) % 7)); // 월요일 시작
  const monthStart = new Date(today.getFullYear(), today.getMonth(), 1).getTime();
  const repsSince = (from) => {
    const out = {};
    for (const r of records) {
      if (r.method !== 'MISSION' || !r.exercise || r.startedAt < from) continue;
      out[r.exercise] = (out[r.exercise] ?? 0) + r.reps;
    }
    return out;
  };
  return {
    streak: streak(records, nowMs),
    weekReps: repsSince(weekStart),
    monthReps: repsSince(monthStart),
    monthEmergencyCount: records.filter((r) => r.method === 'EMERGENCY' && r.startedAt >= monthStart).length,
  };
}
