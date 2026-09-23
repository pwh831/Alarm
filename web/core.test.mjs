import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  ArmRaiseCounter, Difficulty, Hint, MissionSession, MotionGuard, PushupCounter, SquatCounter, UpperBodySquatCounter,
  angleDeg, matchesEmergency, streak, summary,
} from './core.js';

// ── 합성 포즈 (좌표 0~1, y 아래로 증가) ──
const P = (x, y, confidence = 1) => ({ x, y, confidence });
const rad = (d) => (d * Math.PI) / 180;

function legs(shoulder, hip, knee, ankle) {
  const m = {};
  for (const s of ['left', 'right']) Object.assign(m, { [`${s}Shoulder`]: shoulder, [`${s}Hip`]: hip, [`${s}Knee`]: knee, [`${s}Ankle`]: ankle });
  return m;
}
const squat = (deg, c = 0.9) => {
  const knee = P(0.5, 0.7, c);
  const hip = P(0.5 + 0.2 * Math.sin(rad(deg)), 0.7 + 0.2 * Math.cos(rad(deg)), c);
  return legs(P(hip.x, hip.y - 0.3, c), hip, knee, P(0.5, 0.9, c));
};
const kneeOnly = (deg) => legs(P(0.5, 0.2), P(0.5, 0.5), P(0.5, 0.7), P(0.5 - 0.2 * Math.sin(rad(deg)), 0.7 - 0.2 * Math.cos(rad(deg))));
function pushup(deg, knees = false) {
  const wrist = P(0.3, 0.8);
  const half = rad(deg / 2);
  const shoulder = P(0.3, wrist.y - 0.2 * Math.sin(half));
  const elbow = P(0.3 - 0.1 * Math.cos(half), (shoulder.y + wrist.y) / 2);
  const [knee, ankle] = knees ? [P(0.65, 0.8), P(0.9, 0.6)] : [P(0.65, shoulder.y + (0.8 - shoulder.y) * 0.58), P(0.9, 0.8)];
  const base = knees ? knee : ankle;
  const hip = P(0.5, shoulder.y + (base.y - shoulder.y) * (0.2 / (base.x - 0.3)));
  return { leftShoulder: shoulder, leftElbow: elbow, leftWrist: wrist, leftHip: hip, leftKnee: knee, leftAnkle: ankle };
}
const curl = (deg) => ({
  leftShoulder: P(0.5, 0.3), leftElbow: P(0.5, 0.45), leftWrist: P(0.5 + 0.15 * Math.sin(rad(deg)), 0.45 - 0.15 * Math.cos(rad(deg))),
  leftHip: P(0.5, 0.6), leftKnee: P(0.5, 0.75), leftAnkle: P(0.5, 0.9),
});
function arms(l, r = l) {
  const arm = (s, d, dir) => P(s.x + dir * 0.25 * Math.sin(rad(d)), s.y + 0.25 * Math.cos(rad(d)));
  const ls = P(0.4, 0.3), rs = P(0.6, 0.3);
  return { leftShoulder: ls, rightShoulder: rs, leftHip: P(0.4, 0.6), rightHip: P(0.6, 0.6), leftWrist: arm(ls, l, -1), rightWrist: arm(rs, r, 1) };
}

/** 30fps로 각도를 움직이며 프레임을 흘려 넣는다. */
function driver(counter, pose) {
  let t = 0;
  let last;
  const step = (from, to, ms) => {
    const n = Math.max(1, Math.floor(ms / 33));
    for (let i = 1; i <= n; i++) {
      t += 33;
      last = counter.update(t, pose(from + ((to - from) * i) / n));
    }
  };
  return {
    step,
    hold: (d, ms) => step(d, d, ms),
    rep: (top, bottom, ms = 1500) => { step(top, bottom, ms / 2); step(bottom, top, ms / 2); },
    get last() { return last; },
  };
}

test('angles', () => {
  assert.ok(Math.abs(angleDeg(P(0, 1), P(0, 0), P(1, 0)) - 90) < 1e-9);
  assert.ok(Math.abs(angleDeg(P(1, 0), P(0, 0), P(1, -1)) - 45) < 1e-9);
  const s = squat(95);
  assert.ok(Math.abs(angleDeg(s.leftHip, s.leftKnee, s.leftAnkle) - 95) < 1e-6);
});

test('squat counts full reps and respects difficulty', () => {
  const d = driver(new SquatCounter(Difficulty.NORMAL), (x) => squat(x));
  d.hold(175, 500);
  for (let i = 0; i < 5; i++) d.rep(175, 85);
  assert.equal(d.last.reps, 5);
  for (const [diff, expected] of [[Difficulty.EASY, 3], [Difficulty.NORMAL, 0]]) {
    const s = driver(new SquatCounter(diff), (x) => squat(x));
    s.hold(175, 500);
    for (let i = 0; i < 3; i++) s.rep(175, 110);
    assert.equal(s.last.reps, expected);
  }
});

test('squat anti-cheat: too fast, knee-only, low confidence', () => {
  const fast = driver(new SquatCounter(Difficulty.NORMAL), (x) => squat(x));
  fast.hold(175, 500);
  fast.step(175, 85, 100); fast.hold(85, 100); fast.step(85, 175, 100); fast.hold(175, 33);
  assert.equal(fast.last.reps, 0);
  assert.equal(fast.last.hint, Hint.TOO_FAST);

  const k = driver(new SquatCounter(Difficulty.NORMAL), kneeOnly);
  k.hold(178, 500);
  for (let i = 0; i < 3; i++) k.rep(178, 70);
  assert.equal(k.last.reps, 0);

  const low = driver(new SquatCounter(Difficulty.NORMAL), (x) => squat(x, 0.2));
  for (let i = 0; i < 3; i++) low.rep(175, 85);
  assert.equal(low.last.reps, 0);
  assert.equal(low.last.hint, Hint.BODY_NOT_VISIBLE);
});

test('pushup: plank counts, standing curl and knee pushup on normal do not', () => {
  const d = driver(new PushupCounter(Difficulty.NORMAL), (x) => pushup(x));
  d.hold(175, 500);
  for (let i = 0; i < 4; i++) d.rep(175, 80);
  assert.equal(d.last.reps, 4);

  const c = driver(new PushupCounter(Difficulty.EASY), curl);
  c.hold(175, 500);
  for (let i = 0; i < 3; i++) c.rep(175, 60);
  assert.equal(c.last.reps, 0);
  assert.equal(c.last.hint, Hint.GET_HORIZONTAL);

  const easy = driver(new PushupCounter(Difficulty.EASY), (x) => pushup(x, true));
  easy.hold(175, 500);
  for (let i = 0; i < 2; i++) easy.rep(175, 90);
  assert.equal(easy.last.reps, 2);
  const normal = driver(new PushupCounter(Difficulty.NORMAL), (x) => pushup(x, true));
  normal.hold(175, 500);
  for (let i = 0; i < 2; i++) normal.rep(175, 80);
  assert.equal(normal.last.reps, 0);
});

test('arm raise needs both arms', () => {
  const both = driver(new ArmRaiseCounter(Difficulty.NORMAL), (m) => arms(180 - m));
  both.hold(170, 500);
  for (let i = 0; i < 3; i++) both.rep(170, 10);
  assert.equal(both.last.reps, 3);
  const one = driver(new ArmRaiseCounter(Difficulty.NORMAL), (m) => arms(180 - m, 10));
  one.hold(170, 500);
  for (let i = 0; i < 3; i++) one.rep(170, 10);
  assert.equal(one.last.reps, 0);
});

test('mission session: countdown, then counts; phone movement pauses', () => {
  const s = new MissionSession(new SquatCounter(Difficulty.NORMAL));
  let t = 0;
  let st;
  const feed = (deg, ms, moving = false) => {
    for (let i = 0; i < Math.max(1, Math.floor(ms / 33)); i++) { t += 33; st = s.update(t, squat(deg), moving); }
    return st;
  };
  assert.equal(feed(175, 33).countdownSec, 3);
  assert.equal(feed(175, 100, true).rep.hint, Hint.PHONE_MOVING);
  assert.equal(feed(175, 33).countdownSec, 3);
  assert.equal(feed(175, 3100).started, true);
  for (const deg of [120, 80, 80, 120, 175]) feed(deg, 150, true);
  assert.equal(feed(175, 100).rep.reps, 0);
  for (const deg of [150, 120, 90, 80, 80, 80, 100, 130, 160, 175, 175, 175]) feed(deg, 100);
  assert.equal(feed(175, 100).rep.reps, 1);
});

test('motion guard', () => {
  const g = new MotionGuard();
  let t = 0;
  for (let i = 0; i < 250; i++) { t += 20; g.onAcceleration(t, (Math.random() - 0.5) * 0.6, 9.81, 0); }
  assert.equal(g.isMoving(t), false);
  for (let i = 0; i < 25; i++) { t += 20; g.onAcceleration(t, 4 * Math.sin(t / 30), 9.81, 0); }
  assert.equal(g.isMoving(t), true);
  for (let i = 0; i < 75; i++) { t += 20; g.onAcceleration(t, 0, 9.81, 0); }
  assert.equal(g.isMoving(t), false);
});

test('emergency phrase', () => {
  assert.ok(matchesEmergency('  나는 지금  완전히 깨어 있고 운동 대신 알람을 끄기로 선택합니다 '));
  assert.ok(!matchesEmergency('나는 지금 완전히 깨어 있고'));
});

test('streak and summary', () => {
  const now = new Date(2026, 8, 23, 12).getTime(); // 2026-09-23 수요일
  const rec = (daysAgo, method = 'MISSION', exercise = 'SQUAT', reps = 15) => {
    const d = new Date(2026, 8, 23 - daysAgo, 7);
    return { startedAt: d.getTime(), finishedAt: d.getTime() + 90_000, method, exercise, reps };
  };
  assert.equal(streak([rec(0), rec(1), rec(2), rec(4)], now), 3);
  assert.equal(streak([rec(1), rec(2)], now), 2);
  assert.equal(streak([rec(0), rec(1, 'EMERGENCY', null, 0), rec(2)], now), 1);
  const s = summary([rec(0), rec(1, 'MISSION', 'PUSHUP', 10), rec(3, 'MISSION', 'SQUAT', 20), rec(23, 'MISSION', 'SQUAT', 99), rec(2, 'EMERGENCY', null, 0)], now);
  assert.deepEqual(s.weekReps, { SQUAT: 15, PUSHUP: 10 });
  assert.deepEqual(s.monthReps, { SQUAT: 35, PUSHUP: 10 });
  assert.equal(s.monthEmergencyCount, 1);
});

// 상체만 보이는 스쿼트: 선 자세 어깨 (0.5, 0.3), 엉덩이 (0.5, 0.6) → 몸통 0.3.
// 값은 판정값(100 = 선 자세, 100 - 하강비율×100)으로 넣는다.
const upperSquat = (value) => {
  const drop = ((100 - value) / 100) * 0.3;
  return {
    leftShoulder: P(0.48, 0.3 + drop), rightShoulder: P(0.52, 0.3 + drop),
    leftHip: P(0.48, 0.6 + drop), rightHip: P(0.52, 0.6 + drop),
  };
};
// 허리만 숙이기: 엉덩이는 그대로, 어깨만 앞으로 내려간다.
const bowOnly = (value) => {
  const bend = rad(((100 - value) / 100) * 150);
  const hip = P(0.5, 0.6);
  const sh = P(0.5 + 0.3 * Math.sin(bend), 0.6 - 0.3 * Math.cos(bend));
  return { leftShoulder: sh, rightShoulder: sh, leftHip: hip, rightHip: hip };
};

test('upper-body squat counts hip drops by difficulty', () => {
  for (const [diff, depth, expected] of [
    [Difficulty.NORMAL, 30, 3], // 하강 0.7 ≥ 0.5
    [Difficulty.NORMAL, 60, 0], // 하강 0.4 < 0.5
    [Difficulty.EASY, 60, 3], // 하강 0.4 ≥ 0.3
    [Difficulty.HARD, 40, 0], // 하강 0.6 < 0.7
  ]) {
    const d = driver(new UpperBodySquatCounter(diff), upperSquat);
    d.hold(100, 500);
    for (let i = 0; i < 3; i++) d.rep(100, depth);
    assert.equal(d.last.reps, expected, `${JSON.stringify(diff)} depth ${depth}`);
  }
});

test('upper-body squat ignores bowing forward', () => {
  const d = driver(new UpperBodySquatCounter(Difficulty.EASY), bowOnly);
  d.hold(100, 500);
  for (let i = 0; i < 3; i++) d.rep(100, 20);
  assert.equal(d.last.reps, 0);
});

test('upper-body squat needs only shoulders and hips', () => {
  const c = new UpperBodySquatCounter(Difficulty.NORMAL);
  assert.equal(c.sees(upperSquat(100)), true);
  assert.equal(c.sees({ leftShoulder: P(0.5, 0.3) }), false);
});
