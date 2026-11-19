(() => {
  const WINDOW_SECONDS = 5.0;
  const GAP_SECONDS = 0.08;
  const LEFT_MARGIN = 72;
  const ACCEPTED_FILE = /\.(csv|txt)$/i;
  const CONDITION_ORDER = ["Normal Sinus Rhythm", "Bradycardia", "Tachycardia", "Myocardial Infarction"];
  const SEVERITY_RANK = { NORMAL: 0, WARNING: 1, CRITICAL: 2 };
  const MONITOR_COLORS = { NORMAL: "#7ED3A4", WARNING: "#F2B65A", CRITICAL: "#FF7A7A" };

  const PAPER = {
    background: "#FFFCF7",
    minor: "#F1DDD6",
    major: "#DDB2A8",
    ink: "#2A1E1B",
    muted: "#6F625A",
    rPeak: "#6E1E31",
    stElevation: "#B4232F"
  };

  const state = {
    scenarios: [],
    data: null,
    rPeakSet: new Set(),
    stElevatedSet: new Set(),
    running: true,
    currentTime: 0,
    lastFrame: performance.now(),
    source: null,      // { type: "demo", scenario } or { type: "file", index } into state.batch
    batch: [],         // { name, path, file, text, result, error }
    batchRun: 0,       // bumped on every new load so an older, still-running batch stops
    hoverMouse: null
  };

  // DOM elements
  const $ = (id) => document.getElementById(id);
  const canvas = $("ecgCanvas");
  const ctx = canvas.getContext("2d");
  const hoverReadout = $("hoverReadout");

  const scenarioSelect = $("scenarioSelect");
  const patientIdEl = $("patientId");
  const patientNameEl = $("patientName");
  const patientAgeEl = $("patientAge");
  const patientSexEl = $("patientSex");
  const samplingRateEl = $("samplingRate");
  const errorBox = $("errorBox");

  const dropZone = $("dropZone");
  const folderInput = $("folderInput");
  const filesInput = $("filesInput");

  const batchPanel = $("batchPanel");
  const batchBody = $("batchBody");

  const pauseBtn = $("pauseBtn");
  const livePill = $("livePill");
  const liveLabel = $("liveLabel");
  const heartIcon = $("heartIcon");

  // ------------------------------------------------------------------ clock

  function updateClock() {
    const now = new Date();
    $("clockTime").textContent = now.toTimeString().slice(0, 8);
    $("clockDate").textContent = now.toLocaleDateString("en-GB", {
      weekday: "short", day: "2-digit", month: "short", year: "numeric"
    });
  }
  updateClock();
  setInterval(updateClock, 1000);

  // ------------------------------------------------------------------ backend API

  async function init() {
    const res = await fetch("/api/config");
    const cfg = await res.json();
    state.scenarios = cfg.scenarios;

    scenarioSelect.innerHTML = "";
    cfg.scenarios.forEach((s) => {
      const opt = document.createElement("option");
      opt.value = s.id;
      opt.textContent = `${s.label} (${s.heartRate} bpm)`;
      scenarioSelect.appendChild(opt);
    });

    $("rulesList").innerHTML = cfg.rules.map((r) =>
      `<div class="rule-item"><strong>${escapeHtml(r.name)}</strong><span>${escapeHtml(r.description)}</span></div>`
    ).join("");

    await showDemo(cfg.scenarios[0].id);
  }

  /** POSTs to /api/analyze and returns the report JSON, or throws with the server's message. */
  async function analyze(source, includePatient) {
    const body = new URLSearchParams();
    if (source.type === "demo") {
      body.set("scenario", source.scenario);
    } else {
      const entry = state.batch[source.index];
      body.set("csvText", entry.text);
      body.set("fileName", entry.name);
      body.set("samplingRate", samplingRateEl.value);
    }
    if (includePatient) {
      body.set("patientId", patientIdEl.value);
      body.set("name", patientNameEl.value);
      body.set("age", patientAgeEl.value);
      body.set("sex", patientSexEl.value);
    }

    const res = await fetch("/api/analyze", { method: "POST", body });
    const json = await res.json().catch(() => ({ error: `Server returned ${res.status}` }));
    if (!res.ok || json.error) {
      throw new Error(json.error || "Analysis failed");
    }
    return json;
  }

  async function showDemo(scenarioId) {
    state.source = { type: "demo", scenario: scenarioId };
    scenarioSelect.value = scenarioId;
    markActiveRow();
    try {
      hideError();
      showReport(await analyze(state.source, false));
    } catch (err) {
      showError(err.message);
    }
  }

  /** Re-analyses the current recording using the patient details typed in the form. */
  async function rerun() {
    if (!state.source) return false;
    try {
      hideError();
      const json = await analyze(state.source, true);
      if (state.source.type === "file") {
        state.batch[state.source.index].result = json;
        renderBatchRow(state.source.index);
        renderBatchSummary();
      }
      showReport(json);
      return true;
    } catch (err) {
      showError(err.message);
      return false;
    }
  }

  function showReport(json) {
    state.data = json;
    state.rPeakSet = new Set(json.features.rPeaks);
    state.stElevatedSet = new Set(json.features.stElevatedPeaks);
    state.currentTime = 0;

    renderPatient(json.patient);
    renderHero(json);
    renderVitals(json);
    renderFindings(json);
  }

  // ------------------------------------------------------------------ loading files & folders

  /** items: [{ file, path }]. Analyses every .csv/.txt file one after another. */
  async function loadRecordings(items, label) {
    const accepted = items
      .filter(({ file }) => ACCEPTED_FILE.test(file.name) && !file.name.startsWith("."))
      .sort((a, b) => a.path.localeCompare(b.path, undefined, { numeric: true }));

    if (!accepted.length) {
      showError(`No .csv or .txt recordings found in “${label}”.`);
      return;
    }
    hideError();

    const run = ++state.batchRun;
    state.batch = accepted.map(({ file, path }) =>
      ({ name: file.name, path, file, text: null, result: null, error: null }));
    if (state.source && state.source.type === "file") state.source = null;

    $("batchTitle").textContent = label;
    batchBody.innerHTML = state.batch.map(() => '<tr tabindex="0"></tr>').join("");
    state.batch.forEach((_, i) => renderBatchRow(i));
    renderBatchSummary();
    batchPanel.hidden = false;

    let shownFirst = false;
    for (let i = 0; i < state.batch.length; i++) {
      const entry = state.batch[i];
      $("batchProgress").textContent = `Analysing ${i + 1} of ${state.batch.length}…`;
      try {
        entry.text = await entry.file.text();
        entry.result = await analyze({ type: "file", index: i }, false);
      } catch (err) {
        entry.error = err.message;
      }
      if (run !== state.batchRun) return;

      renderBatchRow(i);
      renderBatchSummary();
      if (!shownFirst && entry.result) {
        shownFirst = true;
        selectBatchRow(i);
      }
    }
    const n = state.batch.length;
    $("batchProgress").textContent = `${n} recording${n === 1 ? "" : "s"} analysed`;
  }

  function selectBatchRow(index) {
    const entry = state.batch[index];
    if (!entry || (!entry.result && !entry.error)) return;
    if (entry.error) {
      showError(`${entry.name}: ${entry.error}`);
      return;
    }
    hideError();
    state.source = { type: "file", index };
    markActiveRow();
    showReport(entry.result);
  }

  function markActiveRow() {
    const active = state.source && state.source.type === "file" ? state.source.index : -1;
    Array.from(batchBody.children).forEach((tr, i) => tr.classList.toggle("active", i === active));
  }

  /** Collects files from a drag-and-drop, walking into dropped folders. */
  function itemsFromDrop(dataTransfer) {
    // webkitGetAsEntry() must run synchronously inside the drop event.
    const entries = Array.from(dataTransfer.items || [])
      .map((item) => (item.webkitGetAsEntry ? item.webkitGetAsEntry() : null))
      .filter(Boolean);

    if (!entries.length) {
      const files = Array.from(dataTransfer.files);
      return Promise.resolve({ items: files.map((f) => ({ file: f, path: f.name })), label: describe(files) });
    }

    const label = entries.length === 1 ? entries[0].name : `${entries.length} dropped items`;
    const items = [];
    return Promise.all(entries.map((e) => walkEntry(e, items))).then(() => ({ items, label }));
  }

  async function walkEntry(entry, out) {
    if (entry.isFile) {
      const file = await new Promise((resolve, reject) => entry.file(resolve, reject));
      out.push({ file, path: entry.fullPath.replace(/^\//, "") });
    } else if (entry.isDirectory) {
      const reader = entry.createReader();
      let chunk;
      do {
        chunk = await new Promise((resolve, reject) => reader.readEntries(resolve, reject));
        for (const child of chunk) await walkEntry(child, out);
      } while (chunk.length);
    }
  }

  function describe(files) {
    return files.length === 1 ? files[0].name : `${files.length} selected files`;
  }

  // ------------------------------------------------------------------ rendering

  function sevClass(sev) {
    return "sev-" + String(sev || "error").toLowerCase();
  }

  function renderPatient(p) {
    patientIdEl.value = p.patientId || "";
    patientNameEl.value = p.name || "";
    patientAgeEl.value = p.age > 0 ? p.age : "";
    const known = Array.from(patientSexEl.options).some((o) => o.value === p.sex);
    patientSexEl.value = known ? p.sex : "Unknown";
  }

  function renderHero(data) {
    const r = data.report;
    const p = data.patient;
    $("resultHero").className = "result-hero " + sevClass(r.overallSeverity);
    $("heroCondition").textContent = r.detectedCondition;
    $("heroDiagnosis").textContent = r.overallSeverity === "NORMAL"
      ? "No anomalies detected — heart rate and ST segment within normal limits"
      : r.summary;

    const badge = $("heroBadge");
    badge.textContent = r.overallSeverity;
    badge.className = "sev-badge " + sevClass(r.overallSeverity);

    const parts = [p.name, p.patientId];
    if (p.age > 0) parts.push(`${p.age} y`);
    if (p.sex && p.sex !== "Unknown") parts.push(p.sex);
    $("heroPatient").textContent = parts.filter(Boolean).join(" · ");

    const s = data.signal;
    $("heroSource").textContent = `${s.sourceName} · ${s.durationSeconds.toFixed(1)} s · ${s.samplingRate} Hz`;
    $("heroSource").title = s.sourceName;
  }

  function renderVitals(data) {
    const f = data.features;
    const color = MONITOR_COLORS[data.report.overallSeverity] || MONITOR_COLORS.NORMAL;

    const hrEl = $("hrValue");
    hrEl.textContent = f.beatCount >= 3 ? Math.round(f.heartRate) : "--";
    hrEl.style.color = color;

    const rhythmEl = $("rhythmLabel");
    rhythmEl.textContent = data.report.primaryDiagnosis;
    rhythmEl.style.color = color;

    $("vRr").textContent = `${Math.round(f.meanRrMs)} ms`;
    $("vHrRange").textContent = `${Math.round(f.minHeartRate)} / ${Math.round(f.maxHeartRate)} bpm`;
    $("vHrv").textContent = `${Math.round(f.sdnnMs)} ms`;

    const stEl = $("vSt");
    stEl.textContent = `${signed(f.meanStDeviation)} mV`;
    stEl.style.color = f.meanStDeviation >= 0.10 ? MONITOR_COLORS.CRITICAL : "";

    const qrEl = $("vQr");
    qrEl.textContent = `${Math.round(f.meanQToRRatio * 100)} %`;
    qrEl.style.color = f.meanQToRRatio > 0.25 ? MONITOR_COLORS.WARNING : "";

    $("vRAmp").textContent = `${f.meanRAmplitude.toFixed(2)} mV`;
    $("vBeats").textContent = f.beatCount;
  }

  function renderFindings(data) {
    $("findingsGrid").innerHTML = data.report.findings.map((f) => {
      const cls = f.detected ? sevClass(f.severity) : "sev-normal";
      const evidence = f.evidence.map((e) => `<li>${escapeHtml(e)}</li>`).join("");
      const rec = f.detected
        ? `<div class="recommendation"><strong>Recommendation:</strong> ${escapeHtml(f.recommendation)}</div>`
        : "";
      return `
        <article class="finding-card ${cls}${f.detected ? " positive" : ""}">
          <div class="finding-head">
            <span class="finding-rule">${escapeHtml(f.ruleName)}</span>
            <span class="sev-badge">${f.detected ? escapeHtml(f.severity) : "NEGATIVE"}</span>
          </div>
          <div class="finding-diag">${escapeHtml(f.detected ? f.diagnosis : "Not detected")}</div>
          <ul class="evidence-list">${evidence}</ul>
          ${rec}
        </article>`;
    }).join("");
  }

  function renderBatchRow(i) {
    const e = state.batch[i];
    const tr = batchBody.children[i];
    if (!tr) return;
    const fileCell = `<td class="file-cell" title="${escapeHtml(e.path)}">${escapeHtml(e.name)}</td>`;

    if (e.error) {
      tr.innerHTML = `${fileCell}<td class="muted">—</td><td class="num muted">—</td><td class="num muted">—</td>
        <td><span class="cond-pill sev-error" title="${escapeHtml(e.error)}">Unreadable</span></td>
        <td><span class="sev-badge sev-error">ERROR</span></td>`;
    } else if (!e.result) {
      tr.innerHTML = `${fileCell}<td class="muted" colspan="5">Waiting…</td>`;
    } else {
      const f = e.result.features;
      const r = e.result.report;
      const cls = sevClass(r.overallSeverity);
      const hr = f.beatCount >= 3 ? `${Math.round(f.heartRate)} bpm` : "—";
      tr.innerHTML = `${fileCell}
        <td>${escapeHtml(e.result.patient.name)}</td>
        <td class="num">${hr}</td>
        <td class="num">${signed(f.meanStDeviation)} mV</td>
        <td><span class="cond-pill ${cls}">${escapeHtml(r.detectedCondition)}</span></td>
        <td><span class="sev-badge ${cls}">${escapeHtml(r.overallSeverity)}</span></td>`;
    }
    tr.classList.toggle("active", !!state.source && state.source.type === "file" && state.source.index === i);
  }

  function renderBatchSummary() {
    const groups = new Map();
    for (const e of state.batch) {
      if (!e.result && !e.error) continue;
      const key = e.error ? "Unreadable" : e.result.report.detectedCondition;
      const sev = e.error ? "error" : e.result.report.overallSeverity;
      const g = groups.get(key) || { count: 0, sev };
      g.count++;
      if ((SEVERITY_RANK[sev] ?? -1) > (SEVERITY_RANK[g.sev] ?? -1)) g.sev = sev;
      groups.set(key, g);
    }
    const rank = (k) => (CONDITION_ORDER.includes(k) ? CONDITION_ORDER.indexOf(k) : CONDITION_ORDER.length);
    const keys = Array.from(groups.keys()).sort((a, b) => rank(a) - rank(b));

    $("batchSummary").innerHTML = keys.map((k) => {
      const g = groups.get(k);
      return `<span class="summary-chip ${sevClass(g.sev)}" style="--chip: var(--sev)"><i></i>${escapeHtml(k)} <b>${g.count}</b></span>`;
    }).join("");
  }

  // ------------------------------------------------------------------ ECG strip canvas

  function resizeCanvas() {
    const parent = canvas.parentElement;
    const dpr = window.devicePixelRatio || 1;
    const w = parent.clientWidth;
    const h = parent.clientHeight;
    if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return { w, h };
  }

  function pulseHeart() {
    heartIcon.classList.add("pulse");
    setTimeout(() => heartIcon.classList.remove("pulse"), 140);
  }

  function drawFrame(now) {
    const dt = Math.min(0.1, (now - state.lastFrame) / 1000);
    state.lastFrame = now;

    if (state.running && state.data) {
      const prev = state.currentTime;
      state.currentTime += dt;
      const fs = state.data.signal.samplingRate;
      const len = state.data.signal.samples.length;
      const s0 = Math.floor(prev * fs) + 1;
      const s1 = Math.floor(state.currentTime * fs);
      for (let s = s0; s <= s1; s++) {
        if (state.rPeakSet.has(s % len)) {
          pulseHeart();
        }
      }
    }

    paintStrip();
    requestAnimationFrame(drawFrame);
  }

  function paintStrip() {
    const { w, h } = resizeCanvas();
    const plotWidth = w - LEFT_MARGIN - 8;
    const pxPerSecond = plotWidth / WINDOW_SECONDS;
    const smallBox = pxPerSecond * 0.04;
    const pxPerMv = smallBox * 10;
    const baselineY = h * 0.62;

    ctx.fillStyle = PAPER.background;
    ctx.fillRect(0, 0, w, h);

    // 1. Millimetre ECG grid
    for (let x = LEFT_MARGIN, i = 0; x < w; x += smallBox, i++) {
      ctx.strokeStyle = i % 5 === 0 ? PAPER.major : PAPER.minor;
      ctx.lineWidth = i % 5 === 0 ? 1 : 0.6;
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();
    }
    for (const dir of [-1, 1]) {
      for (let y = baselineY, i = 0; y >= 0 && y <= h; y += dir * smallBox, i++) {
        ctx.strokeStyle = i % 5 === 0 ? PAPER.major : PAPER.minor;
        ctx.lineWidth = i % 5 === 0 ? 1 : 0.6;
        ctx.beginPath();
        ctx.moveTo(LEFT_MARGIN, y);
        ctx.lineTo(w, y);
        ctx.stroke();
      }
    }
    ctx.strokeStyle = PAPER.major;
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.moveTo(LEFT_MARGIN, 0);
    ctx.lineTo(LEFT_MARGIN, h);
    ctx.stroke();

    // 2. 1 mV calibration pulse
    const x0 = 12;
    const calW = 0.2 * pxPerSecond;
    ctx.strokeStyle = PAPER.ink;
    ctx.lineWidth = 1.6;
    ctx.beginPath();
    ctx.moveTo(x0, baselineY);
    ctx.lineTo(x0 + 8, baselineY);
    ctx.lineTo(x0 + 8, baselineY - pxPerMv);
    ctx.lineTo(x0 + 8 + calW, baselineY - pxPerMv);
    ctx.lineTo(x0 + 8 + calW, baselineY);
    ctx.lineTo(LEFT_MARGIN - 4, baselineY);
    ctx.stroke();

    ctx.fillStyle = PAPER.muted;
    ctx.font = "10px Inter, sans-serif";
    ctx.fillText("1 mV", x0 + 6, baselineY - pxPerMv - 6);

    // 3. Labels
    ctx.fillStyle = PAPER.ink;
    ctx.font = "600 18px 'Source Serif 4', Georgia, serif";
    ctx.fillText("II", 14, 28);

    ctx.fillStyle = PAPER.muted;
    ctx.font = "11px Inter, sans-serif";
    const scaleTxt = "25 mm/s    10 mm/mV    Lead II";
    ctx.fillText(scaleTxt, w - ctx.measureText(scaleTxt).width - 12, 20);

    if (state.data) {
      ctx.fillText(state.data.signal.sourceName, LEFT_MARGIN + 8, h - 10);
    }

    const rTxt = "▼ R-peak";
    const stTxt = "▼ ST elevation";
    const stW = ctx.measureText(stTxt).width;
    const rW = ctx.measureText(rTxt + "    ").width;
    ctx.fillStyle = PAPER.rPeak;
    ctx.fillText(rTxt, w - stW - rW - 12, h - 10);
    ctx.fillStyle = PAPER.stElevation;
    ctx.fillText(stTxt, w - stW - 12, h - 10);

    // 4. Signal trace
    if (!state.data) return;
    const samples = state.data.signal.samples;
    const fs = state.data.signal.samplingRate;
    const len = samples.length;
    const sweepPos = state.currentTime % WINDOW_SECONDS;
    const cycleStart = state.currentTime - sweepPos;
    const samplesOnScreen = Math.floor(WINDOW_SECONDS * fs);

    ctx.strokeStyle = PAPER.ink;
    ctx.lineWidth = 1.8;
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.beginPath();

    let penDown = false;
    const markers = [];

    for (let k = 0; k <= samplesOnScreen; k++) {
      const screenTime = k / fs;
      const inGap = screenTime > sweepPos && screenTime < sweepPos + GAP_SECONDS;
      const t = screenTime <= sweepPos ? cycleStart + screenTime : cycleStart - WINDOW_SECONDS + screenTime;
      if (inGap || t < 0) {
        penDown = false;
        continue;
      }
      const rawIdx = Math.round(t * fs);
      const index = ((rawIdx % len) + len) % len;
      const x = LEFT_MARGIN + screenTime * pxPerSecond;
      const y = baselineY - samples[index] * pxPerMv;

      if (penDown) ctx.lineTo(x, y);
      else ctx.moveTo(x, y);
      penDown = true;

      if (state.rPeakSet.has(index)) {
        markers.push({ x, y, st: state.stElevatedSet.has(index) });
      }
    }
    ctx.stroke();

    // R-peak / ST markers
    ctx.font = "11px Inter, sans-serif";
    for (const m of markers) {
      ctx.fillStyle = m.st ? PAPER.stElevation : PAPER.rPeak;
      ctx.fillText("▼", m.x - 5, m.y - 8);
    }

    // Sweep cursor
    const sx = LEFT_MARGIN + sweepPos * pxPerSecond;
    ctx.fillStyle = "rgba(110, 30, 49, 0.28)";
    ctx.fillRect(sx, 0, 3, h);

    // Hover crosshair
    if (state.hoverMouse && state.hoverMouse.x >= LEFT_MARGIN) {
      const mx = state.hoverMouse.x;
      const screenTime = (mx - LEFT_MARGIN) / pxPerSecond;
      const t = screenTime <= sweepPos ? cycleStart + screenTime : cycleStart - WINDOW_SECONDS + screenTime;
      if (t >= 0) {
        const rawIdx = Math.round(t * fs);
        const index = ((rawIdx % len) + len) % len;
        const mv = samples[index];
        ctx.strokeStyle = "rgba(46, 11, 20, 0.45)";
        ctx.lineWidth = 1;
        ctx.setLineDash([4, 4]);
        ctx.beginPath();
        ctx.moveTo(mx, 0);
        ctx.lineTo(mx, h);
        ctx.stroke();
        ctx.setLineDash([]);
        hoverReadout.textContent = `t = ${t.toFixed(2)} s   |   ${signed(mv, 3)} mV`;
        hoverReadout.hidden = false;
      } else {
        hoverReadout.hidden = true;
      }
    } else {
      hoverReadout.hidden = true;
    }
  }

  // ------------------------------------------------------------------ UI events

  scenarioSelect.addEventListener("change", () => showDemo(scenarioSelect.value));

  $("runBtn").addEventListener("click", rerun);

  pauseBtn.addEventListener("click", () => {
    state.running = !state.running;
    pauseBtn.textContent = state.running ? "Pause monitor" : "Resume monitor";
    livePill.classList.toggle("paused", !state.running);
    liveLabel.textContent = state.running ? "Monitor live" : "Paused";
  });

  $("chooseFolderBtn").addEventListener("click", () => folderInput.click());
  $("chooseFilesBtn").addEventListener("click", () => filesInput.click());
  dropZone.addEventListener("click", () => folderInput.click());
  dropZone.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      folderInput.click();
    }
  });

  folderInput.addEventListener("change", () => {
    const files = Array.from(folderInput.files);
    folderInput.value = "";
    if (!files.length) return;
    const first = files[0].webkitRelativePath || files[0].name;
    const label = first.includes("/") ? first.split("/")[0] : describe(files);
    loadRecordings(files.map((f) => ({ file: f, path: f.webkitRelativePath || f.name })), label);
  });

  filesInput.addEventListener("change", () => {
    const files = Array.from(filesInput.files);
    filesInput.value = "";
    if (!files.length) return;
    loadRecordings(files.map((f) => ({ file: f, path: f.name })), describe(files));
  });

  // Drop anywhere on the page; the drop zone highlights while dragging.
  let dragDepth = 0;
  window.addEventListener("dragenter", (e) => {
    e.preventDefault();
    dragDepth++;
    dropZone.classList.add("dragging");
  });
  window.addEventListener("dragleave", () => {
    dragDepth = Math.max(0, dragDepth - 1);
    if (dragDepth === 0) dropZone.classList.remove("dragging");
  });
  window.addEventListener("dragover", (e) => e.preventDefault());
  window.addEventListener("drop", (e) => {
    e.preventDefault();
    dragDepth = 0;
    dropZone.classList.remove("dragging");
    itemsFromDrop(e.dataTransfer)
      .then(({ items, label }) => loadRecordings(items, label))
      .catch((err) => showError(`Could not read the dropped items: ${err.message}`));
  });

  batchBody.addEventListener("click", (e) => {
    const tr = e.target.closest("tr");
    if (tr) selectBatchRow(Array.prototype.indexOf.call(batchBody.children, tr));
  });
  batchBody.addEventListener("keydown", (e) => {
    if (e.key !== "Enter" && e.key !== " ") return;
    const tr = e.target.closest("tr");
    if (!tr) return;
    e.preventDefault();
    selectBatchRow(Array.prototype.indexOf.call(batchBody.children, tr));
  });

  $("downloadSampleBtn").addEventListener("click", () => {
    window.location.href = `/api/sample-csv?scenario=${encodeURIComponent(scenarioSelect.value)}`;
  });

  $("exportBtn").addEventListener("click", async () => {
    if (!(await rerun()) || !state.data) return;
    const blob = new Blob([state.data.report.plainText], { type: "text/plain;charset=utf-8" });
    const a = document.createElement("a");
    const safeId = (patientIdEl.value || "patient").replace(/[^A-Za-z0-9-]/g, "");
    a.href = URL.createObjectURL(blob);
    a.download = `Ventria_Report_${safeId}.txt`;
    a.click();
    URL.revokeObjectURL(a.href);
  });

  canvas.addEventListener("mousemove", (e) => {
    const rect = canvas.getBoundingClientRect();
    state.hoverMouse = { x: e.clientX - rect.left, y: e.clientY - rect.top };
  });
  canvas.addEventListener("mouseleave", () => {
    state.hoverMouse = null;
  });

  // ------------------------------------------------------------------ helpers

  function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
  }

  function hideError() {
    errorBox.hidden = true;
  }

  function signed(value, decimals = 2) {
    return `${value >= 0 ? "+" : ""}${value.toFixed(decimals)}`;
  }

  function escapeHtml(text) {
    return String(text)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  init()
    .catch((err) => showError(`Could not reach the Ventria server: ${err.message}`))
    .finally(() => requestAnimationFrame(drawFrame));
})();
