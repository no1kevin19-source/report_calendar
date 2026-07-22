const STORAGE_KEY = "assessmentPlannerMvp.assignments";
const REMINDER_STORAGE_KEY = "assessmentPlannerMvp.reminderSettings";
const DISCORD_SENT_STORAGE_KEY = "assessmentPlannerMvp.discordSentReminders";
const MS_PER_DAY = 1000 * 60 * 60 * 24;
const MAX_PHOTO_SIZE = 900;
const DISCORD_CHECK_INTERVAL = 30 * 1000;
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

const $ = (selector) => document.querySelector(selector);
const form = $("#assignmentForm");
const assignmentId = $("#assignmentId");
const titleInput = $("#titleInput");
const subjectInput = $("#subjectInput");
const customSubjectLabel = $("#customSubjectLabel");
const customSubjectInput = $("#customSubjectInput");
const dueInput = $("#dueInput");
const periodInput = $("#periodInput");
const detailInput = $("#detailInput");
const photoInput = $("#photoInput");
const photoPreview = $("#photoPreview");
const cancelEditButton = $("#cancelEditButton");
const weekRange = $("#weekRange");
const weekList = $("#weekList");
const weekEmpty = $("#weekEmpty");
const calendarGrid = $("#calendarGrid");
const monthLabel = $("#monthLabel");
const prevMonthButton = $("#prevMonthButton");
const nextMonthButton = $("#nextMonthButton");
const detailDialog = $("#detailDialog");
const closeDetailButton = $("#closeDetailButton");
const detailSubject = $("#detailSubject");
const detailDday = $("#detailDday");
const detailTitle = $("#detailTitle");
const detailDate = $("#detailDate");
const detailText = $("#detailText");
const detailPhotos = $("#detailPhotos");
const detailDeleteButton = $("#detailDeleteButton");
const detailEditButton = $("#detailEditButton");
const detailDoneButton = $("#detailDoneButton");
const photoDialog = $("#photoDialog");
const closePhotoButton = $("#closePhotoButton");
const largePhoto = $("#largePhoto");
const largePhotoCaption = $("#largePhotoCaption");
const photoZoomStage = $("#photoZoomStage");
const reminderDaysInput = $("#reminderDaysInput");
const reminderTimeInput = $("#reminderTimeInput");
const saveReminderButton = $("#saveReminderButton");
const cancelReminderButton = $("#cancelReminderButton");
const reminderStatus = $("#reminderStatus");

let assignments = loadAssignments();
let selectedPhotos = [];
let activeDetailId = "";
let photoZoom = 1;
let isPhotoDragging = false;
let photoDragStartX = 0;
let photoDragStartY = 0;
let photoDragScrollLeft = 0;
let photoDragScrollTop = 0;
let calendarDate = new Date();
calendarDate.setDate(1);
calendarDate.setHours(0, 0, 0, 0);

setDefaultDueDate();
render();
updateCustomSubjectVisibility();
disableSavedInputSuggestions();
loadReminderSettingsIntoForm();
updateReminderStatus();
checkDiscordDueNotifications();
setInterval(checkDiscordDueNotifications, DISCORD_CHECK_INTERVAL);

subjectInput.addEventListener("change", () => {
  updateCustomSubjectVisibility();
  if (subjectInput.value === "직접 입력") customSubjectInput.focus();
});

saveReminderButton.addEventListener("click", () => {
  saveReminderSettings(getReminderSettingsFromForm());
  updateReminderStatus("알림 설정이 저장되었어요.");
  checkDiscordDueNotifications();
  window.alert("저장되었습니다.");
});

cancelReminderButton.addEventListener("click", () => {
  cancelReminderSettings();
  updateReminderStatus("알림 설정이 취소되었어요.");
  window.alert("취소되었습니다.");
});

reminderDaysInput.addEventListener("change", () => updateReminderStatus("변경한 알림 설정을 저장해 주세요."));
reminderTimeInput.addEventListener("input", () => updateReminderStatus("변경한 알림 설정을 저장해 주세요."));

form.addEventListener("submit", (event) => {
  event.preventDefault();
  const existing = assignments.find((item) => item.id === assignmentId.value);
  const subject = getSelectedSubject();
  if (!subject) {
    customSubjectInput.focus();
    return;
  }

  const payload = {
    id: assignmentId.value || crypto.randomUUID(),
    title: titleInput.value.trim(),
    subject,
    dueDate: dueInput.value,
    period: periodInput.value,
    detail: detailInput.value.trim(),
    photos: selectedPhotos.length ? selectedPhotos : existing?.photos ?? [],
    done: Boolean(existing?.done),
    createdAt: existing?.createdAt ?? new Date().toISOString()
  };

  assignments = assignmentId.value
    ? assignments.map((item) => item.id === payload.id ? payload : item)
    : [payload, ...assignments];

  calendarDate = parseDateKey(payload.dueDate);
  calendarDate.setDate(1);
  saveAssignments();
  resetForm();
  render();
});

photoInput.addEventListener("change", async () => {
  const files = [...photoInput.files].slice(0, 4);
  selectedPhotos = await Promise.all(files.map(toCompressedDataUrl));
  renderPhotoPreview(selectedPhotos);
});

cancelEditButton.addEventListener("click", resetForm);
prevMonthButton.addEventListener("click", () => { calendarDate.setMonth(calendarDate.getMonth() - 1); renderCalendar(); });
nextMonthButton.addEventListener("click", () => { calendarDate.setMonth(calendarDate.getMonth() + 1); renderCalendar(); });

calendarGrid.addEventListener("click", (event) => {
  const eventButton = event.target.closest(".calendar-event");
  if (!eventButton) return;
  const assignment = assignments.find((item) => item.id === eventButton.dataset.id);
  if (assignment) openDetail(assignment);
});

weekList.addEventListener("click", (event) => {
  const weekItem = event.target.closest(".week-item");
  if (!weekItem) return;
  const assignment = assignments.find((item) => item.id === weekItem.dataset.id);
  if (assignment) openDetail(assignment);
});

closeDetailButton.addEventListener("click", () => detailDialog.close());
detailDialog.addEventListener("close", () => { activeDetailId = ""; });
detailDialog.addEventListener("click", (event) => { if (event.target === detailDialog) detailDialog.close(); });

detailDeleteButton.addEventListener("click", () => {
  const target = getActiveDetailAssignment();
  if (!target) return;
  const confirmed = window.confirm(`'${target.title}' 수행평가를 정말로 삭제하시겠습니까?`);
  if (!confirmed) return;
  assignments = assignments.filter((item) => item.id !== target.id);
  saveAssignments();
  detailDialog.close();
  activeDetailId = "";
  render();
});

detailEditButton.addEventListener("click", () => {
  const target = getActiveDetailAssignment();
  if (!target) return;
  detailDialog.close();
  startEdit(target);
});

detailDoneButton.addEventListener("click", () => {
  const target = getActiveDetailAssignment();
  if (!target) return;
  target.done = !target.done;
  saveAssignments();
  render();
  openDetail(target);
});

closePhotoButton.addEventListener("click", () => photoDialog.close());
photoDialog.addEventListener("click", (event) => { if (event.target === photoDialog) photoDialog.close(); });
photoDialog.addEventListener("close", () => { setPhotoZoom(1); stopPhotoDrag(); });
photoZoomStage.addEventListener("wheel", (event) => {
  event.preventDefault();
  setPhotoZoom(photoZoom + (event.deltaY < 0 ? 0.15 : -0.15));
}, { passive: false });
photoZoomStage.addEventListener("pointerdown", (event) => {
  if (event.button !== 0) return;
  isPhotoDragging = true;
  photoDragStartX = event.clientX;
  photoDragStartY = event.clientY;
  photoDragScrollLeft = photoZoomStage.scrollLeft;
  photoDragScrollTop = photoZoomStage.scrollTop;
  photoZoomStage.classList.add("dragging");
  photoZoomStage.setPointerCapture(event.pointerId);
});
photoZoomStage.addEventListener("pointermove", (event) => {
  if (!isPhotoDragging) return;
  photoZoomStage.scrollLeft = photoDragScrollLeft - (event.clientX - photoDragStartX);
  photoZoomStage.scrollTop = photoDragScrollTop - (event.clientY - photoDragStartY);
});
["pointerup", "pointercancel", "pointerleave"].forEach((name) => photoZoomStage.addEventListener(name, stopPhotoDrag));

function render() { renderCalendar(); renderWeekPanel(); }

function renderCalendar() {
  calendarGrid.innerHTML = "";
  monthLabel.textContent = new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long" }).format(calendarDate);
  WEEKDAYS.forEach((weekday) => {
    const header = document.createElement("div");
    header.className = "weekday";
    header.textContent = weekday;
    calendarGrid.append(header);
  });

  const firstDate = new Date(calendarDate.getFullYear(), calendarDate.getMonth(), 1);
  const startDate = new Date(firstDate);
  startDate.setDate(firstDate.getDate() - firstDate.getDay());

  for (let index = 0; index < 42; index += 1) {
    const date = new Date(startDate);
    date.setDate(startDate.getDate() + index);
    calendarGrid.append(createCalendarDay(date));
  }
}

function createCalendarDay(date) {
  const cell = document.createElement("div");
  const dateKey = toDateKey(date);
  const todayKey = toDateKey(new Date());
  const isCurrentMonth = date.getMonth() === calendarDate.getMonth();
  const events = assignments
    .filter((item) => item.dueDate === dateKey)
    .sort((a, b) => Number(a.done) - Number(b.done));

  cell.className = "calendar-day";
  cell.classList.toggle("is-muted", !isCurrentMonth);
  cell.classList.toggle("is-today", dateKey === todayKey);

  const dayNumber = document.createElement("div");
  dayNumber.className = "day-number";
  dayNumber.innerHTML = `<span>${date.getDate()}</span>`;
  cell.append(dayNumber);

  events.forEach((assignment) => {
    const days = getDayDiff(assignment.dueDate);
    const button = document.createElement("button");
    button.className = "calendar-event";
    button.classList.toggle("done", assignment.done);
    button.type = "button";
    button.dataset.id = assignment.id;
    button.innerHTML = `${escapeHtml(assignment.title)}<small>${escapeHtml(getAssignmentMeta(assignment))} · ${formatDday(days, assignment.done).label}</small>`;
    cell.append(button);
  });
  return cell;
}

function renderWeekPanel() {
  const { start, end } = getThisWeekRange();
  const weekAssignments = [...assignments]
    .filter((assignment) => {
      const due = parseDateKey(assignment.dueDate);
      return due >= start && due <= end;
    })
    .sort((a, b) => Number(a.done) - Number(b.done) || getDayDiff(a.dueDate) - getDayDiff(b.dueDate));

  weekRange.textContent = `${formatMonthDay(start)} ~ ${formatMonthDay(end)}`;
  weekList.innerHTML = "";
  weekEmpty.classList.toggle("show", weekAssignments.length === 0);

  weekAssignments.forEach((assignment) => {
    const days = getDayDiff(assignment.dueDate);
    const button = document.createElement("button");
    button.className = "week-item";
    button.classList.toggle("done", assignment.done);
    button.type = "button";
    button.dataset.id = assignment.id;
    button.innerHTML = `
      <span>
        <span class="week-item-title">${escapeHtml(assignment.title)}</span>
        <span class="week-item-meta">${escapeHtml(getAssignmentMeta(assignment))} · ${formatDueDate(assignment.dueDate)}</span>
      </span>
      <span class="week-item-dday">${formatDday(days, assignment.done).label}</span>
    `;
    weekList.append(button);
  });
}

function openDetail(assignment) {
  const days = getDayDiff(assignment.dueDate);
  activeDetailId = assignment.id;
  detailSubject.textContent = assignment.subject;
  detailDday.textContent = formatDday(days, assignment.done).label;
  detailTitle.textContent = assignment.title;
  detailDate.textContent = `마감일: ${formatDueDate(assignment.dueDate)}${assignment.period ? ` · ${assignment.period}` : ""}`;
  detailText.textContent = assignment.detail || "세부내용이 없습니다.";
  detailDoneButton.textContent = assignment.done ? "완료 취소" : "완료";
  detailPhotos.innerHTML = "";

  if (!assignment.photos?.length) {
    const empty = document.createElement("p");
    empty.className = "detail-text";
    empty.textContent = "첨부 사진이 없습니다.";
    detailPhotos.append(empty);
  } else {
    assignment.photos.forEach((photo, index) => {
      const button = document.createElement("button");
      const image = document.createElement("img");
      button.className = "photo-thumb-button";
      button.type = "button";
      button.setAttribute("aria-label", `${assignment.title} 첨부 사진 ${index + 1} 크게 보기`);
      image.src = photo;
      image.alt = `${assignment.title} 첨부 사진 ${index + 1}`;
      button.append(image);
      button.addEventListener("click", () => openLargePhoto(photo, image.alt));
      detailPhotos.append(button);
    });
  }

  if (!detailDialog.open) detailDialog.showModal?.() ?? detailDialog.setAttribute("open", "");
}

function getActiveDetailAssignment() { return assignments.find((item) => item.id === activeDetailId); }

function openLargePhoto(src, alt) {
  setPhotoZoom(1);
  largePhoto.src = src;
  largePhoto.alt = alt;
  largePhotoCaption.textContent = alt;
  if (!photoDialog.open) photoDialog.showModal?.() ?? photoDialog.setAttribute("open", "");
}

function setPhotoZoom(nextZoom) {
  photoZoom = Math.min(3, Math.max(0.5, nextZoom));
  largePhoto.style.width = `${photoZoom * 100}%`;
  largePhoto.style.maxWidth = photoZoom > 1 ? "none" : "100%";
  largePhoto.style.transform = "none";
}

function stopPhotoDrag() {
  isPhotoDragging = false;
  photoZoomStage.classList.remove("dragging");
}

function startEdit(assignment) {
  assignmentId.value = assignment.id;
  titleInput.value = assignment.title;
  setSubjectFields(assignment.subject);
  dueInput.value = assignment.dueDate;
  periodInput.value = assignment.period || "";
  detailInput.value = assignment.detail;
  selectedPhotos = assignment.photos || [];
  renderPhotoPreview(selectedPhotos);
  cancelEditButton.hidden = false;
  titleInput.focus();
}

function resetForm() {
  form.reset();
  assignmentId.value = "";
  selectedPhotos = [];
  photoPreview.innerHTML = "";
  cancelEditButton.hidden = true;
  setDefaultDueDate();
  updateCustomSubjectVisibility();
}

function renderPhotoPreview(photos) {
  photoPreview.innerHTML = "";
  photos.forEach((photo, index) => {
    const image = document.createElement("img");
    image.src = photo;
    image.alt = `선택한 사진 ${index + 1}`;
    photoPreview.append(image);
  });
}

function getAssignmentMeta(assignment) { return `${assignment.subject} · ${assignment.period || "교시 미정"}`; }

function getThisWeekRange() {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const start = new Date(today);
  start.setDate(today.getDate() - today.getDay());
  const end = new Date(start);
  end.setDate(start.getDate() + 6);
  end.setHours(23, 59, 59, 999);
  return { start, end };
}

function getSelectedSubject() { return subjectInput.value === "직접 입력" ? customSubjectInput.value.trim() : subjectInput.value; }

function setSubjectFields(subject) {
  const optionValues = [...subjectInput.options].map((option) => option.value || option.textContent);
  if (optionValues.includes(subject)) {
    subjectInput.value = subject;
    customSubjectInput.value = "";
  } else {
    subjectInput.value = "직접 입력";
    customSubjectInput.value = subject;
  }
  updateCustomSubjectVisibility();
}

function updateCustomSubjectVisibility() {
  const isCustom = subjectInput.value === "직접 입력";
  customSubjectLabel.hidden = !isCustom;
  customSubjectInput.required = isCustom;
  if (!isCustom) customSubjectInput.value = "";
}

function disableSavedInputSuggestions() {
  [titleInput, customSubjectInput, detailInput].forEach((field, index) => {
    field.setAttribute("autocomplete", index === 2 ? "off" : "new-password");
    field.setAttribute("name", `planner-field-${index}-${crypto.randomUUID()}`);
  });
}

function getDayDiff(dueDate) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const due = parseDateKey(dueDate);
  return Math.ceil((due - today) / MS_PER_DAY);
}

function formatDday(days, done = false) {
  if (done) return { label: "완료" };
  if (days === 0) return { label: "D-Day" };
  if (days > 0) return { label: `D-${days}` };
  return { label: `D+${Math.abs(days)}` };
}

function formatDueDate(date) {
  return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long", day: "numeric", weekday: "short" }).format(parseDateKey(date));
}

function formatMonthDay(date) {
  return new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric" }).format(date);
}

function setDefaultDueDate() {
  const nextWeek = new Date();
  nextWeek.setDate(nextWeek.getDate() + 7);
  dueInput.value = toDateKey(nextWeek);
}

function toDateKey(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function parseDateKey(dateKey) {
  const [year, month, day] = dateKey.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  date.setHours(0, 0, 0, 0);
  return date;
}

function loadReminderSettings() {
  const fallback = { daysBefore: 1, time: "08:00", enabled: false };
  try {
    const saved = JSON.parse(localStorage.getItem(REMINDER_STORAGE_KEY));
    if (!saved || typeof saved !== "object") return fallback;
    const daysBefore = Number(saved.daysBefore);
    return { daysBefore: Number.isFinite(daysBefore) ? daysBefore : fallback.daysBefore, time: saved.time || fallback.time, enabled: saved.enabled !== false };
  } catch { return fallback; }
}

function saveReminderSettings(settings) { localStorage.setItem(REMINDER_STORAGE_KEY, JSON.stringify({ ...settings, enabled: true })); }
function cancelReminderSettings() { localStorage.setItem(REMINDER_STORAGE_KEY, JSON.stringify({ ...getReminderSettingsFromForm(), enabled: false })); }
function loadReminderSettingsIntoForm() { const settings = loadReminderSettings(); reminderDaysInput.value = String(settings.daysBefore); reminderTimeInput.value = settings.time; }
function getReminderSettingsFromForm() { return { daysBefore: Number(reminderDaysInput.value), time: reminderTimeInput.value || "08:00" }; }

function updateReminderStatus(message = "") {
  const settings = getReminderSettingsFromForm();
  const savedSettings = loadReminderSettings();
  const dayText = getReminderDayText(settings.daysBefore);
  const enabledText = savedSettings.enabled ? "알림 켜짐" : "알림 꺼짐";
  reminderStatus.textContent = message ? `${message} ${dayText} ${settings.time} Discord 알림 · ${enabledText}` : `${dayText} ${settings.time} 기준으로 Discord에 알림이 가요. ${enabledText}`;
}

function getReminderDayText(daysBefore) {
  if (daysBefore === 0) return "마감일";
  if (daysBefore === 1) return "마감 1일 전";
  return `마감 ${daysBefore}일 전부터 1일 전까지 매일`;
}

async function checkDiscordDueNotifications() {
  const settings = loadReminderSettings();
  if (!settings.enabled || !isAfterReminderTime(new Date(), settings.time)) return;
  const todayKey = toDateKey(new Date());
  const sentReminders = loadDiscordSentReminders();
  let changed = false;

  for (const assignment of assignments.filter((item) => !item.done)) {
    const daysUntilDue = getDayDiff(assignment.dueDate);
    if (!shouldSendDiscordReminder(daysUntilDue, settings.daysBefore)) continue;
    const reminderKey = `${assignment.id}:${todayKey}:D-${daysUntilDue}:${settings.daysBefore}:${settings.time}`;
    if (sentReminders[reminderKey]) continue;
    const sent = await sendDiscordReminder(createDiscordReminderPayload(assignment, daysUntilDue));
    if (sent) {
      sentReminders[reminderKey] = new Date().toISOString();
      changed = true;
    }
  }
  if (changed) saveDiscordSentReminders(sentReminders);
}

function isAfterReminderTime(now, time) {
  const [hour = 8, minute = 0] = time.split(":").map(Number);
  const reminderTime = new Date(now);
  reminderTime.setHours(hour, minute, 0, 0);
  return now >= reminderTime;
}

function shouldSendDiscordReminder(daysUntilDue, daysBefore) {
  if (daysBefore === 0) return daysUntilDue === 0;
  return daysUntilDue >= 1 && daysUntilDue <= daysBefore;
}

function createDiscordReminderPayload(assignment, daysUntilDue) {
  const dday = formatDday(daysUntilDue, assignment.done).label;
  return { content: `${assignment.title} ${dday}야! 잊지 마.` };
}

async function sendDiscordReminder(payload) {
  try {
    const response = await fetch("/api/discord-reminder", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    return response.ok;
  } catch { return false; }
}

function loadDiscordSentReminders() {
  try {
    const saved = JSON.parse(localStorage.getItem(DISCORD_SENT_STORAGE_KEY));
    return saved && typeof saved === "object" && !Array.isArray(saved) ? saved : {};
  } catch { return {}; }
}
function saveDiscordSentReminders(sentReminders) { localStorage.setItem(DISCORD_SENT_STORAGE_KEY, JSON.stringify(sentReminders)); }

function loadAssignments() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return Array.isArray(saved) ? saved : [];
  } catch { return []; }
}
function saveAssignments() { localStorage.setItem(STORAGE_KEY, JSON.stringify(assignments)); }

async function toCompressedDataUrl(file) {
  const image = await loadImage(file);
  const canvas = document.createElement("canvas");
  const scale = Math.min(1, MAX_PHOTO_SIZE / Math.max(image.width, image.height));
  canvas.width = Math.round(image.width * scale);
  canvas.height = Math.round(image.height * scale);
  canvas.getContext("2d").drawImage(image, 0, 0, canvas.width, canvas.height);
  return canvas.toDataURL("image/jpeg", 0.74);
}

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.addEventListener("load", () => {
      const image = new Image();
      image.addEventListener("load", () => resolve(image));
      image.addEventListener("error", reject);
      image.src = reader.result;
    });
    reader.addEventListener("error", reject);
    reader.readAsDataURL(file);
  });
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
